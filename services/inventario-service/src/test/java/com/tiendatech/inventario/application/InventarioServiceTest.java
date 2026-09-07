package com.tiendatech.inventario.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiendatech.inventario.application.reservation.CrdbTransactionRetryExecutor;
import com.tiendatech.inventario.domain.InventarioRepository;
import com.tiendatech.inventario.domain.StockProducto;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventarioServiceTest {
    private final InventarioRepository repository = mock(InventarioRepository.class);
    private final InventarioService service = new InventarioService(repository);

    @Test void delegaConsultasDeInventario() {
        var movimientos = List.<Map<String,Object>>of(Map.of("tipo", "ENTRADA"));
        var subtipos = List.<Map<String,Object>>of(Map.of("id", 2));
        var stock = new StockProducto(10L, "CPU", 8);
        when(repository.listarMovimientos()).thenReturn(movimientos);
        when(repository.listarSubtipos(1)).thenReturn(subtipos);
        when(repository.obtenerStock(10)).thenReturn(stock);
        when(repository.listarStock(List.of(10))).thenReturn(List.of(stock));
        assertSame(movimientos, service.listarMovimientos());
        assertSame(subtipos, service.listarSubtipos(1));
        assertSame(stock, service.obtenerStock(10));
        assertEquals(List.of(stock), service.listarStock(List.of(10)));
    }

    @Test void serializaMovimientoYConservaIdempotencia() throws Exception {
        var body = new ObjectMapper().readTree("{\"productoId\":10,\"cantidad\":2}");
        when(repository.registrarMovimiento(body.toString(), "ana", "mov-1")).thenReturn(true);
        assertTrue(service.registrarMovimiento(body, "ana", "mov-1"));
        verify(repository).registrarMovimiento("{\"productoId\":10,\"cantidad\":2}", "ana", "mov-1");
    }

    @Test void aceptaMovimientoSinBody() {
        service.registrarMovimiento(null, "sistema", null);
        verify(repository).registrarMovimiento(null, "sistema", null);
    }

    @Test void envuelveElMovimientoCompletoEnElEjecutorDeReintentos() throws Exception {
        CrdbTransactionRetryExecutor retry = mock(CrdbTransactionRetryExecutor.class);
        InventarioService productionService = new InventarioService(repository, retry);
        var body = new ObjectMapper().readTree("{\"productoId\":10,\"cantidad\":2}");
        when(retry.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Boolean> operation = invocation.getArgument(0);
            return operation.get();
        });
        when(repository.registrarMovimiento(body.toString(), "2pc", "factura-1")).thenReturn(true);

        assertTrue(productionService.registrarMovimiento(body, "2pc", "factura-1"));

        verify(retry).execute(any());
        verify(repository).registrarMovimiento(body.toString(), "2pc", "factura-1");
    }
}
