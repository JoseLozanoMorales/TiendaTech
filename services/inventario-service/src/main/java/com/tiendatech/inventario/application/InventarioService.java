package com.tiendatech.inventario.application;

import com.tiendatech.inventario.domain.InventarioRepository;
import com.tiendatech.inventario.domain.StockProducto;
import com.tiendatech.inventario.application.reservation.CrdbTransactionRetryExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Casos de uso de inventario; depende exclusivamente del puerto del dominio. */
@Service
public class InventarioService {
    private final InventarioRepository repository;
    private final CrdbTransactionRetryExecutor retryExecutor;

    @Autowired
    public InventarioService(InventarioRepository repository,
                             CrdbTransactionRetryExecutor retryExecutor) {
        this.repository = repository;
        this.retryExecutor = retryExecutor;
    }

    // Conserva la construccion directa de las pruebas unitarias.
    public InventarioService(InventarioRepository repository) {
        this.repository = repository;
        this.retryExecutor = null;
    }

    public List<Map<String, Object>> listarMovimientos() {
        return repository.listarMovimientos();
    }

    public List<Map<String, Object>> listarSubtipos(Integer tipo) {
        return repository.listarSubtipos(tipo);
    }

    public StockProducto obtenerStock(Integer productoId) {
        return repository.obtenerStock(productoId);
    }

    public List<StockProducto> listarStock(List<Integer> productoIds) {
        return repository.listarStock(productoIds);
    }

    public boolean registrarMovimiento(JsonNode body, String usuario, String idempotencyKey) {
        String movimientoJson = body == null ? null : body.toString();
        return retryExecutor == null
                ? repository.registrarMovimiento(movimientoJson, usuario, idempotencyKey)
                : retryExecutor.execute(
                        () -> repository.registrarMovimiento(movimientoJson, usuario, idempotencyKey));
    }
}
