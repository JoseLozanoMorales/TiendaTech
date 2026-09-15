package com.tiendatech.usuarios.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Ejecuta una transaccion completa de CockroachDB de nuevo ante SQLSTATE 40001.
 *
 * Hallazgo real (punto 14, carga con 20 usuarios concurrentes sobre el camino
 * critico autenticado): RefreshTokenService.refresh() hace una lectura y dos
 * escrituras (revocar la sesion vieja + crear la nueva) dentro de una misma
 * transaccion contra refresh_sessions. Bajo aislamiento serializable,
 * CockroachDB aborta una de dos transacciones concurrentes que chocan sobre
 * las mismas filas y exige que el cliente la reintente
 * (TransactionRetryWithProtoRefreshError / ABORT_REASON_NEW_LEASE_PREVENTS_TXN,
 * SQLSTATE 40001) -- no es un bug de la aplicacion, es el contrato normal de
 * una base de datos distribuida con control de concurrencia optimista. Con 5
 * y 10 usuarios concurrentes nunca se vio (poco choque de transacciones); con
 * 20 aparecio en 4 de 2209 peticiones (0.18%), todas en /auth/refresh.
 *
 * Este mismo patron ya existia en pedidos-service (CrdbRetryExecutor, para el
 * checkout) y en inventario-service (CrdbTransactionRetryExecutor, para
 * reservas). usuarios-service era el unico servicio con transacciones
 * multi-escritura sin ese tratamiento -- se replica aqui el disenio de
 * inventario-service (envuelve la transaccion COMPLETA vía TransactionTemplate,
 * no solo el query individual, porque el reintento debe repetir la lectura
 * y ambas escrituras juntas) con backoff exponencial + jitter.
 */
@Component
public class CrdbTransactionRetryExecutor {
    private static final String SERIALIZATION_FAILURE = "40001";

    private final TransactionTemplate transactions;
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;

    public CrdbTransactionRetryExecutor(
            PlatformTransactionManager transactionManager,
            @Value("${crdb.retry.max-attempts:10}") int maxAttempts,
            @Value("${crdb.retry.initial-delay-ms:100}") long initialDelayMs,
            @Value("${crdb.retry.max-delay-ms:2000}") long maxDelayMs) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public <T> T execute(Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> operation.get());
            } catch (RuntimeException error) {
                if (!hasSqlState(error, SERIALIZATION_FAILURE) || attempt >= maxAttempts) {
                    throw error;
                }
                pause(attempt);
            }
        }
    }

    static boolean hasSqlState(Throwable error, String expectedState) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && expectedState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private void pause(int attempt) {
        int shift = Math.min(attempt - 1, 20);
        long exponential = initialDelayMs > (Long.MAX_VALUE >> shift)
                ? maxDelayMs : initialDelayMs << shift;
        long base = Math.min(maxDelayMs, exponential);
        long jitter = base > 1 ? ThreadLocalRandom.current().nextLong(base / 2 + 1) : 0;
        try {
            Thread.sleep(Math.min(maxDelayMs, base + jitter));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento CockroachDB interrumpido", interrupted);
        }
    }
}
