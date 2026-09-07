package com.tiendatech.ordenesproveedores.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


public interface OrdenCompraRepository {

    Integer crear(Integer proveedorId, Integer usuarioId, LocalDate fechaEsperada,
                  List<DetalleOrdenCompra> detalle);

    void actualizar(Integer id, Integer proveedorId, LocalDate fechaEsperada,
                    List<DetalleOrdenCompra> detalle);

    void enviar(Integer id);

    void cancelar(Integer id);


    Map<Integer, BigDecimal> registrarRecepcion(Integer id, Map<Integer, Integer> recepcion);

    List<OrdenCompra> listarPorEstado(EstadoOrdenCompra estado);

    OrdenCompra obtenerPorId(Integer id);

    List<DetalleOrdenCompra> listarDetalle(Integer ordenCompraId);
}
