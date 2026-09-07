package com.tiendatech.ordenesproveedores.application;

import com.tiendatech.ordenesproveedores.domain.DetalleOrdenCompra;
import com.tiendatech.ordenesproveedores.domain.EstadoOrdenCompra;
import com.tiendatech.ordenesproveedores.domain.OrdenCompra;
import com.tiendatech.ordenesproveedores.domain.OrdenCompraRepository;
import com.tiendatech.ordenesproveedores.domain.InventarioPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final InventarioPort inventarioClient;

    public OrdenCompraService(OrdenCompraRepository ordenCompraRepository,
                              InventarioPort inventarioClient) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.inventarioClient = inventarioClient;
    }

    public Integer crear(Integer proveedorId, Integer usuarioId, LocalDate fechaEsperada,
                         List<DetalleOrdenCompra> detalle) {
        return ordenCompraRepository.crear(proveedorId, usuarioId, fechaEsperada, detalle);
    }

    public void actualizar(Integer ordenCompraId, Integer proveedorId, LocalDate fechaEsperada,
                           List<DetalleOrdenCompra> detalle) {
        ordenCompraRepository.actualizar(ordenCompraId, proveedorId, fechaEsperada, detalle);
    }

    public void enviar(Integer ordenCompraId) {
        ordenCompraRepository.enviar(ordenCompraId);
    }

    public void cancelar(Integer ordenCompraId) {
        ordenCompraRepository.cancelar(ordenCompraId);
    }

    public void registrarRecepcion(Integer ordenCompraId, Map<Integer, Integer> recepcionPorProducto, String usuario) {
        Map<Integer, BigDecimal> costosPorProducto = ordenCompraRepository.registrarRecepcion(ordenCompraId, recepcionPorProducto);
        try {
            inventarioClient.registrarEntradasPorRecepcion(ordenCompraId, recepcionPorProducto, costosPorProducto, usuario);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "La recepcion de la orden " + ordenCompraId + " quedo confirmada, pero fallo la "
                            + "actualizacion de stock en inventario-service: " + mensajeInventario(e)
                            + " Revisar manualmente.", e);
        }
    }


    private String mensajeInventario(Exception e) {
        if (e instanceof RestClientResponseException rce) {
            String body = rce.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode node =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                    if (node.hasNonNull("error")) return node.get("error").asText();
                } catch (Exception ignored) {
                }
                return body;
            }
        }
        return e.getMessage() != null ? e.getMessage() : "error desconocido";
    }

    public List<OrdenCompra> listarPorEstado(EstadoOrdenCompra estado) {
        return ordenCompraRepository.listarPorEstado(estado);
    }

    public OrdenCompra obtenerPorId(Integer ordenCompraId) {
        OrdenCompra orden = ordenCompraRepository.obtenerPorId(ordenCompraId);
        if (orden == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de compra " + ordenCompraId + " no existe");
        }
        return orden;
    }

    public List<DetalleOrdenCompra> listarDetalle(Integer ordenCompraId) {
        obtenerPorId(ordenCompraId);
        return ordenCompraRepository.listarDetalle(ordenCompraId);
    }
}
