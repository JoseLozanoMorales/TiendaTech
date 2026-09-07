package com.tiendatech.inventario.application.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrdbTransactionRetryExecutorTest {

    @Test
    void reintentaLaTransaccionCompletaAnteSqlState40001() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(ignored -> mock(TransactionStatus.class));
        var executor = new CrdbTransactionRetryExecutor(manager, 4, 0, 0);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException(new SQLException("retry", "40001"));
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void noReintentaErroresQueNoSonDeSerializacion() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(ignored -> mock(TransactionStatus.class));
        var executor = new CrdbTransactionRetryExecutor(manager, 4, 0, 0);
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(RuntimeException.class, () -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException(new SQLException("otro", "23505"));
        }));
        assertEquals(1, attempts.get());
    }
}
