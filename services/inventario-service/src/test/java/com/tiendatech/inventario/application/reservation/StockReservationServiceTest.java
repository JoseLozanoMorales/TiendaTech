package com.tiendatech.inventario.application.reservation;

import com.tiendatech.inventario.domain.reservation.ReservationCommand;
import com.tiendatech.inventario.domain.reservation.ReservationResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockReservationServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LamportClock clock = mock(LamportClock.class);
    private final StockReservationService service = new StockReservationService(jdbc, clock);

    private ReservationCommand command(long cartId, long userId, long productId, int quantity,
                                        long lamportTimestamp, String deviceId, String operationId) {
        return new ReservationCommand(cartId, userId, productId, quantity, lamportTimestamp, deviceId, operationId);
    }

    private Object estado(int cantidad, long lamport, String deviceId) throws Exception {
        Class<?> stateClass = Class.forName(
                "com.tiendatech.inventario.application.reservation.StockReservationService$State");
        var ctor = stateClass.getDeclaredConstructor(int.class, long.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(cantidad, lamport, deviceId);
    }

    @Test
    void devuelveElResultadoCacheadoCuandoLaOperacionYaFueProcesada() {
        String opId = UUID.randomUUID().toString();
        var cmd = command(1, 1, 10, 5, 3, "dev-1", opId);
        var cached = new ReservationResult(true, "ya procesada", 5, 10, 3L, "dev-1", true);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(UUID.fromString(opId))))
                .thenReturn(List.of(cached));

        var result = service.reconcile(cmd);

        assertSame(cached, result);
        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), eq(UUID.fromString(opId)));
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(clock);
    }

    @Test
    void aceptaLaReservaCuandoHayStockYElEventoEsMasReciente() {
        String opId = UUID.randomUUID().toString();
        var cmd = command(1, 1, 10, 10, 5, "dev-1", opId);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(UUID.fromString(opId))))
                .thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), eq(10L)))
                .thenReturn(Map.of("stock", 100));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L), eq(10L)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(10L), eq(1L)))
                .thenReturn(20L);
        when(clock.receive(5L)).thenReturn(99L);

        var result = service.reconcile(cmd);

        assertTrue(result.accepted());
        assertEquals("Reserva reconciliada", result.message());
        assertEquals(10, result.reservedQuantity());
        assertEquals(70, result.availableStock());
        assertEquals(99L, result.lamportTimestamp());
        assertEquals("dev-1", result.winningDeviceId());
        assertFalse(result.replayed());
        InOrder order = inOrder(jdbc);
        order.verify(jdbc).query(anyString(), any(RowMapper.class), eq(UUID.fromString(opId)));
        order.verify(jdbc).queryForMap(contains("FOR UPDATE"), eq(10L));
        order.verify(jdbc).query(anyString(), any(RowMapper.class), eq(UUID.fromString(opId)));
    }

    @Test
    void rechazaCuandoLaCantidadSuperaElStockDisponible() {
        String opId = UUID.randomUUID().toString();
        var cmd = command(1, 1, 10, 20, 1, "dev-1", opId);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(UUID.fromString(opId))))
                .thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), eq(10L)))
                .thenReturn(Map.of("stock", 10));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L), eq(10L)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(10L), eq(1L)))
                .thenReturn(0L);
        when(clock.receive(1L)).thenReturn(2L);

        var result = service.reconcile(cmd);

        assertFalse(result.accepted());
        assertEquals("Stock insuficiente", result.message());
        assertEquals(0, result.reservedQuantity());
        assertEquals(10, result.availableStock());
        assertEquals(2L, result.lamportTimestamp());
        assertEquals("", result.winningDeviceId());
        assertFalse(result.replayed());
    }

    @Test
    void rechazaCuandoElEventoEsAnteriorAlEstadoYaReconciliado() throws Exception {
        String opId = UUID.randomUUID().toString();
        var cmd = command(1, 1, 10, 3, 5, "dev-2", opId);
        Object estadoActual = estado(7, 8, "dev-9");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(UUID.fromString(opId))))
                .thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), eq(10L)))
                .thenReturn(Map.of("stock", 50));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L), eq(10L)))
                .thenReturn(List.of(estadoActual));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(10L), eq(1L)))
                .thenReturn(0L);
        when(clock.receive(5L)).thenReturn(11L);

        var result = service.reconcile(cmd);

        assertFalse(result.accepted());
        assertEquals("Evento anterior al estado reconciliado", result.message());
        assertEquals(7, result.reservedQuantity());
        assertEquals(43, result.availableStock());
        assertEquals(11L, result.lamportTimestamp());
        assertEquals("dev-9", result.winningDeviceId());
        assertFalse(result.replayed());
    }

    @Test
    void validaLosCamposObligatoriosAntesDeConsultarLaBaseDeDatos() {
        String opId = UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(0, 1, 10, 1, 1, "dev-1", opId)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(1, 0, 10, 1, 1, "dev-1", opId)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(1, 1, 0, 1, 1, "dev-1", opId)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(1, 1, 10, -1, 1, "dev-1", opId)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(1, 1, 10, 1, -1, "dev-1", opId)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(1, 1, 10, 1, 1, null, opId)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(1, 1, 10, 1, 1, " ", opId)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reconcile(command(1, 1, 10, 1, 1, "dev-1", "no-es-un-uuid")));

        verifyNoInteractions(jdbc);
        verifyNoInteractions(clock);
    }
}
