package com.tiendatech.inventario.application.reservation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Ejecuta una transaccion completa de CockroachDB de nuevo ante SQLSTATE 40001. */
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
