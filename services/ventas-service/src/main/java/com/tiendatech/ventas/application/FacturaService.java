package com.tiendatech.ventas.application;

import com.tiendatech.ventas.domain.Factura;
import com.tiendatech.ventas.domain.FacturaDetalle;
import com.tiendatech.ventas.domain.FacturaStore;
import com.tiendatech.ventas.domain.FacturaDraft;
import com.tiendatech.ventas.domain.FacturaOutboxStore;
import com.tiendatech.ventas.domain.InventarioPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class FacturaService {

    private final FacturaStore facturaRepository;
    private final FacturaOutboxStore outboxRepository;
    private final InventarioPort inventarioClient;

    public FacturaService(FacturaStore facturaRepository,
                          FacturaOutboxStore outboxRepository,
                          InventarioPort inventarioClient) {
        this.facturaRepository = facturaRepository;
        this.outboxRepository = outboxRepository;
        this.inventarioClient = inventarioClient;
    }

    /**
     * Saga confirma la transaccion local de factura y deja Inventario en el
     * outbox durable. 2PC experimental conserva una barrera sincrona: el
     * checkout no se confirma hasta que Inventario acepta el segundo paso.
     * El outbox queda como recuperacion durable si esa llamada falla.
     */
    public Integer generar(FacturaDraft draft, CoordinationStrategy strategy) {
        Integer facturaId = facturaRepository.generar(draft);
        if (strategy == CoordinationStrategy.TWO_PHASE) {
            List<FacturaDetalle> detalle = facturaRepository.listarDetalle(facturaId);
            inventarioClient.registrarSalidasPorFactura(facturaId, detalle, "coord-2pc");
            outboxRepository.marcarProcesado(facturaId);
        }
        return facturaId;
    }

    public Factura obtenerPorId(Integer facturaId) {
        Factura factura = facturaRepository.obtenerPorId(facturaId);
        if (factura == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La factura " + facturaId + " no existe");
        }
        return factura;
    }

    public List<FacturaDetalle> listarDetalle(Integer facturaId) {
        obtenerPorId(facturaId); // valida que exista -> 404 si no
        return facturaRepository.listarDetalle(facturaId);
    }

    public List<Factura> listar(Integer usuarioId) {
        return facturaRepository.listar(usuarioId);
    }

    public List<Map<String, Object>> masVendidos(int limite) {
        return facturaRepository.masVendidos(Math.min(Math.max(limite, 1), 100));
    }
}
