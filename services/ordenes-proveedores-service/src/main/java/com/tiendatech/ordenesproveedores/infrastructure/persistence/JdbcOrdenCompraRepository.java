package com.tiendatech.ordenesproveedores.infrastructure.persistence;

import com.tiendatech.ordenesproveedores.domain.DetalleOrdenCompra;
import com.tiendatech.ordenesproveedores.domain.EstadoOrdenCompra;
import com.tiendatech.ordenesproveedores.domain.OrdenCompra;
import com.tiendatech.ordenesproveedores.domain.OrdenCompraRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


@Repository
public class JdbcOrdenCompraRepository implements OrdenCompraRepository {
    private static final BigDecimal IVA = new BigDecimal("15.00");
    private final JdbcTemplate jdbcTemplate;

    public JdbcOrdenCompraRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    @Transactional
    public Integer crear(Integer proveedorId, Integer usuarioId, LocalDate fechaEsperada,
                         List<DetalleOrdenCompra> detalle) {
        validarDetalle(detalle);
        Boolean activo = jdbcTemplate.query("SELECT activo FROM ordenes_proveedores.proveedor WHERE proveedor_id=?",
                (rs, n) -> rs.getBoolean(1), proveedorId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Proveedor " + proveedorId + " no existe"));
        if (!activo) throw new IllegalStateException("El proveedor " + proveedorId + " esta inactivo");
        Integer id = jdbcTemplate.queryForObject("""
                INSERT INTO ordenes_proveedores.orden_compra
                    (proveedor_id, usuario_id, fecha_emision, fecha_esperada, estado)
                VALUES (?, ?, current_date, ?, 'PENDIENTE') RETURNING orden_compra_id
                """, Integer.class, proveedorId, usuarioId, fechaEsperada);
        jdbcTemplate.update("UPDATE ordenes_proveedores.orden_compra SET numero_orden=? WHERE orden_compra_id=?",
                "OC-%06d".formatted(id), id);
        insertarDetalle(id, detalle);
        recalcular(id);
        return id;
    }

    @Override
    @Transactional
    public void actualizar(Integer id, Integer proveedorId, LocalDate fechaEsperada,
                           List<DetalleOrdenCompra> detalle) {
        validarDetalle(detalle);
        exigirEstado(id, EstadoOrdenCompra.PENDIENTE);
        jdbcTemplate.update("UPDATE ordenes_proveedores.orden_compra SET proveedor_id=?, fecha_esperada=? WHERE orden_compra_id=?",
                proveedorId, fechaEsperada, id);
        jdbcTemplate.update("DELETE FROM ordenes_proveedores.detalle_orden_compra WHERE orden_compra_id=?", id);
        insertarDetalle(id, detalle);
        recalcular(id);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void enviar(Integer id) {
        exigirEstado(id, EstadoOrdenCompra.PENDIENTE);
        jdbcTemplate.update("UPDATE ordenes_proveedores.orden_compra SET estado='ENVIADA' WHERE orden_compra_id=?", id);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cancelar(Integer id) {
        EstadoOrdenCompra estado = estado(id);
        if (estado != EstadoOrdenCompra.PENDIENTE && estado != EstadoOrdenCompra.ENVIADA)
            throw new IllegalStateException("No se puede cancelar una orden en estado " + estado);
        jdbcTemplate.update("UPDATE ordenes_proveedores.orden_compra SET estado='CANCELADA' WHERE orden_compra_id=?", id);
    }

    @Override
    @Transactional
    public Map<Integer, BigDecimal> registrarRecepcion(Integer id, Map<Integer, Integer> recepcion) {
        EstadoOrdenCompra estado = estado(id);
        if (estado != EstadoOrdenCompra.ENVIADA && estado != EstadoOrdenCompra.RECIBIDA_PARCIAL)
            throw new IllegalStateException("Solo se recibe una orden ENVIADA o RECIBIDA_PARCIAL");
        if (recepcion == null || recepcion.isEmpty()) throw new IllegalArgumentException("La recepcion esta vacia");
        recepcion.forEach((producto, cantidad) -> {
            if (cantidad == null || cantidad <= 0) throw new IllegalArgumentException("Cantidad recibida invalida");
            int changed = jdbcTemplate.update("""
                    UPDATE ordenes_proveedores.detalle_orden_compra
                       SET cantidad_recibida=cantidad_recibida+?,
                           subtotal_linea=(cantidad_recibida+?)*costo_unitario
                     WHERE orden_compra_id=? AND producto_id=?
                       AND cantidad_recibida+? <= cantidad_pedida
                    """, cantidad, cantidad, id, producto, cantidad);
            if (changed != 1) throw new IllegalArgumentException("Producto ausente o cantidad excedida: " + producto);
        });
        Boolean completo = jdbcTemplate.queryForObject("""
                SELECT bool_and(cantidad_recibida=cantidad_pedida)
                  FROM ordenes_proveedores.detalle_orden_compra WHERE orden_compra_id=?
                """, Boolean.class, id);
        jdbcTemplate.update("""
                UPDATE ordenes_proveedores.orden_compra
                   SET estado=?, fecha_recepcion=CASE WHEN ? THEN current_date ELSE fecha_recepcion END
                 WHERE orden_compra_id=?
                """, Boolean.TRUE.equals(completo) ? "RECIBIDA" : "RECIBIDA_PARCIAL",
                Boolean.TRUE.equals(completo), id);
        recalcular(id);
        return costosPorProducto(id, recepcion.keySet());
    }

    // El costo_unitario negociado ya vive en detalle_orden_compra (se fija al crear la
    // orden). Antes se perdia aca: registrarEntradasPorRecepcion solo mandaba producto_id
    // y cantidad a inventario-service, asi que el kardex nunca recalculaba el costo
    // promedio ponderado con el costo real de compra. Este metodo cierra ese hueco.
    private Map<Integer, BigDecimal> costosPorProducto(Integer ordenCompraId, java.util.Set<Integer> productoIds) {
        Map<Integer, BigDecimal> costos = new java.util.HashMap<>();
        jdbcTemplate.query("""
                SELECT producto_id, costo_unitario FROM ordenes_proveedores.detalle_orden_compra
                 WHERE orden_compra_id=?
                """, rs -> {
            Integer productoId = rs.getInt("producto_id");
            if (productoIds.contains(productoId)) {
                costos.put(productoId, rs.getBigDecimal("costo_unitario"));
            }
        }, ordenCompraId);
        return costos;
    }

    @Override
    public List<OrdenCompra> listarPorEstado(EstadoOrdenCompra estado) {
        String sql = "SELECT * FROM ordenes_proveedores.orden_compra" +
                (estado == null ? "" : " WHERE estado=?") + " ORDER BY fecha_emision DESC, orden_compra_id DESC";
        return estado == null ? jdbcTemplate.query(sql, this::mapOrden)
                : jdbcTemplate.query(sql, this::mapOrden, estado.name());
    }

    @Override
    public OrdenCompra obtenerPorId(Integer id) {
        return jdbcTemplate.query("SELECT * FROM ordenes_proveedores.orden_compra WHERE orden_compra_id=?",
                this::mapOrden, id).stream().findFirst().orElse(null);
    }

    @Override
    public List<DetalleOrdenCompra> listarDetalle(Integer ordenCompraId) {
        return jdbcTemplate.query("""
                SELECT * FROM ordenes_proveedores.detalle_orden_compra
                 WHERE orden_compra_id=? ORDER BY detalle_id
                """, this::mapDetalle, ordenCompraId);
    }

    private DetalleOrdenCompra mapDetalle(ResultSet rs, int row) throws SQLException {
        DetalleOrdenCompra d = new DetalleOrdenCompra();
        d.setDetalleId(rs.getInt("detalle_id"));
        d.setOrdenCompraId(rs.getInt("orden_compra_id"));
        d.setProductoId(rs.getInt("producto_id"));
        d.setCantidadPedida(rs.getInt("cantidad_pedida"));
        d.setCantidadRecibida(rs.getInt("cantidad_recibida"));
        d.setCostoUnitario(rs.getBigDecimal("costo_unitario"));
        d.setSubtotalLinea(rs.getBigDecimal("subtotal_linea"));
        return d;
    }

    private void insertarDetalle(Integer id, List<DetalleOrdenCompra> detalle) {
        for (DetalleOrdenCompra d : detalle) jdbcTemplate.update("""
                INSERT INTO ordenes_proveedores.detalle_orden_compra
                    (orden_compra_id, producto_id, cantidad_pedida, cantidad_recibida, costo_unitario, subtotal_linea)
                VALUES (?, ?, ?, 0, ?, 0)
                """, id, d.getProductoId(), d.getCantidadPedida(), d.getCostoUnitario());
    }

    private void recalcular(Integer id) {
        BigDecimal subtotal = jdbcTemplate.queryForObject("""
                SELECT coalesce(sum(subtotal_linea),0) FROM ordenes_proveedores.detalle_orden_compra
                 WHERE orden_compra_id=?
                """, BigDecimal.class, id);
        BigDecimal iva = subtotal.multiply(IVA).divide(new BigDecimal("100")).setScale(2);
        jdbcTemplate.update("UPDATE ordenes_proveedores.orden_compra SET subtotal=?, iva=?, total=? WHERE orden_compra_id=?",
                subtotal, iva, subtotal.add(iva), id);
    }

    private EstadoOrdenCompra estado(Integer id) {
        return jdbcTemplate.query("SELECT estado FROM ordenes_proveedores.orden_compra WHERE orden_compra_id=?",
                (rs, n) -> EstadoOrdenCompra.valueOf(rs.getString(1)), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Orden de compra " + id + " no existe"));
    }

    private void exigirEstado(Integer id, EstadoOrdenCompra esperado) {
        EstadoOrdenCompra actual = estado(id);
        if (actual != esperado) throw new IllegalStateException("Estado esperado " + esperado + ", actual " + actual);
    }

    private void validarDetalle(List<DetalleOrdenCompra> detalle) {
        if (detalle == null || detalle.isEmpty()) throw new IllegalArgumentException("La orden requiere detalle");
        for (DetalleOrdenCompra d : detalle)
            if (d.getProductoId() == null || d.getCantidadPedida() == null || d.getCantidadPedida() <= 0 ||
                    d.getCostoUnitario() == null || d.getCostoUnitario().signum() < 0)
                throw new IllegalArgumentException("Linea de detalle invalida");
    }

    private OrdenCompra mapOrden(ResultSet rs, int row) throws SQLException {
        OrdenCompra o = new OrdenCompra();
        o.setOrdenCompraId(rs.getInt("orden_compra_id")); o.setProveedorId(rs.getInt("proveedor_id"));
        o.setUsuarioId(rs.getInt("usuario_id")); o.setNumeroOrden(rs.getString("numero_orden"));
        o.setFechaEmision(rs.getObject("fecha_emision", LocalDate.class));
        o.setFechaEsperada(rs.getObject("fecha_esperada", LocalDate.class));
        o.setFechaRecepcion(rs.getObject("fecha_recepcion", LocalDate.class));
        o.setEstado(EstadoOrdenCompra.valueOf(rs.getString("estado")));
        o.setSubtotal(rs.getBigDecimal("subtotal")); o.setIva(rs.getBigDecimal("iva")); o.setTotal(rs.getBigDecimal("total"));
        return o;
    }
}
