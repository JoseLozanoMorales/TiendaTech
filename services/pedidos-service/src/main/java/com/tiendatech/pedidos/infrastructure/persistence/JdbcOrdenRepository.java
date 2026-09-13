package com.tiendatech.pedidos.infrastructure.persistence;

import com.tiendatech.pedidos.domain.DetalleOrden;
import com.tiendatech.pedidos.domain.IdempotenciaRepository;
import com.tiendatech.pedidos.domain.Orden;
import com.tiendatech.pedidos.domain.OrdenRepository;
import com.tiendatech.pedidos.domain.PageResponse;
import com.tiendatech.pedidos.domain.Paginacion;
import com.tiendatech.pedidos.domain.ProductoPort;
import com.tiendatech.pedidos.domain.UsuarioPort;
import com.tiendatech.pedidos.domain.DireccionInfo;
import com.tiendatech.pedidos.domain.ProductoInfo;
import com.tiendatech.pedidos.infrastructure.config.CrdbMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adaptador JDBC (patron Repository) del puerto domain.OrdenRepository.
 * Toda la logica SQL, las llamadas externas de validacion/enriquecimiento y
 * las reglas de transaccion/aislamiento se mantienen identicas a la version
 * previa: este refactor es puramente estructural, no cambia comportamiento.
 */
@Repository
public class JdbcOrdenRepository implements OrdenRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ProductoPort productoClient;
    private final UsuarioPort usuarioClient;
    private final Optional<IdempotenciaRepository> idempotenciaRepository;
    private final ObjectProvider<CrdbMetrics> crdbMetrics;

    public JdbcOrdenRepository(JdbcTemplate jdbcTemplate, ProductoPort productoClient,
                               UsuarioPort usuarioClient,
                               Optional<IdempotenciaRepository> idempotenciaRepository,
                               ObjectProvider<CrdbMetrics> crdbMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.productoClient = productoClient;
        this.usuarioClient = usuarioClient;
        this.idempotenciaRepository = idempotenciaRepository;
        this.crdbMetrics = crdbMetrics;
    }

    private final RowMapper<Orden> ordenRowMapper = (rs, rowNum) -> new Orden(
            rs.getInt("orden_id"),
            rs.getInt("usuario_id"),
            rs.getInt("direccion_id"),
            rs.getInt("metodopago_id"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("total"),
            rs.getDate("fecha").toLocalDate()
    );

    // COUNT(*) sobre pedidos.orden: medido con EXPLAIN ANALYZE en ~271ms con
    // 600K filas (full scan, no hay forma de evitarlo con un conteo exacto).
    // Decision consciente: se acepta ese costo fijo por pagina a cambio de
    // simplicidad y totalElements/totalPages siempre correctos. Si con el
    // crecimiento de la tabla esto domina la latencia, cambiar por una
    // aproximacion basada en estadisticas de la tabla.
    @Override
    public PageResponse<Orden> listarOrdenes(Paginacion paginacion) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden ORDER BY fecha DESC, orden_id DESC LIMIT ? OFFSET ?";
        List<Orden> contenido = medirConsulta(() -> jdbcTemplate.query(
                sql, ordenRowMapper, paginacion.size(), paginacion.offset()));
        long total = medirConsulta(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.orden", Long.class));
        return PageResponse.of(contenido, paginacion, total);
    }

    @Override
    public Orden obtenerPorId(Integer ordenId) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE orden_id = ?";
        List<Orden> resultado = medirConsulta(
                () -> jdbcTemplate.query(sql, ordenRowMapper, ordenId));
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public PageResponse<Orden> listarPorUsuario(Integer usuarioId, Paginacion paginacion) {
        String sql = "SELECT orden_id, usuario_id, direccion_id, metodopago_id, subtotal, total, fecha " +
                "FROM pedidos.orden WHERE usuario_id = ? ORDER BY fecha DESC, orden_id DESC LIMIT ? OFFSET ?";
        List<Orden> contenido = medirConsulta(() -> jdbcTemplate.query(
                sql, ordenRowMapper, usuarioId, paginacion.size(), paginacion.offset()));
        long total = medirConsulta(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.orden WHERE usuario_id = ?", Long.class, usuarioId));
        return PageResponse.of(contenido, paginacion, total);
    }

    // fecha se recibe ademas de ordenId (el llamador ya tiene la Orden completa
    // por el chequeo de propiedad) porque la PK de detalle_orden es (fecha,
    // detalle_id) y el unico indice secundario es (fecha, orden_id): filtrar
    // solo por orden_id fuerza un FULL SCAN de la tabla (570ms medido con
    // EXPLAIN ANALYZE sobre 600K filas). Con fecha en el WHERE, CRDB usa
    // idx_detalle_orden y resuelve en unos pocos ms.
    @Override
    public PageResponse<DetalleOrden> obtenerDetalle(Integer ordenId, LocalDate fecha, Paginacion paginacion) {
        String sql = "SELECT orden_id, producto_id, cantidad, precio_unitario, subtotal, iva, total " +
                "FROM pedidos.detalle_orden WHERE fecha = ? AND orden_id = ? ORDER BY detalle_id LIMIT ? OFFSET ?";
        List<DetalleOrden> contenido = medirConsulta(() -> jdbcTemplate.query(sql, (rs, rowNum) -> new DetalleOrden(
                rs.getInt("orden_id"),
                rs.getInt("producto_id"),
                rs.getInt("cantidad"),
                rs.getBigDecimal("precio_unitario"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("iva"),
                rs.getBigDecimal("total")
        ), fecha, ordenId, paginacion.size(), paginacion.offset()));
        long total = medirConsulta(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pedidos.detalle_orden WHERE fecha = ? AND orden_id = ?",
                Long.class, fecha, ordenId));
        return PageResponse.of(contenido, paginacion, total);
    }

    /**
     * idempotencyKey/payloadHash son null cuando el checkout no envio
     * Idempotency-Key (o la funcionalidad esta apagada): en ese caso el
     * comportamiento es identico al de antes de esta funcionalidad.
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Orden crear(Integer usuarioId, Integer direccionId, Integer metodopagoId,
                       String idempotencyKey, String payloadHash) {

        validarCheckout(usuarioId, direccionId, metodopagoId);

        // 1) Buscar carrito activo
        String sqlCarrito = "SELECT carrito_id FROM pedidos.carrito_de_compra " +
                "WHERE usuario_id = ? AND habilitado = true";
        List<Integer> carritos = jdbcTemplate.query(sqlCarrito,
                (rs, rowNum) -> rs.getInt("carrito_id"), usuarioId);

        if (carritos.isEmpty()) {
            throw new IllegalStateException("El usuario " + usuarioId + " no tiene carrito activo");
        }
        Integer carritoId = carritos.get(0);

        // 2) Traer detalle del carrito, con precio/IVA real desde productos-service
        String sqlDetalle = "SELECT producto_id, cantidad FROM pedidos.carrito_detalle WHERE carrito_id = ?";
        List<DetalleCarritoTmp> items = jdbcTemplate.query(sqlDetalle, (rs, rowNum) -> {
            Integer productoId = rs.getInt("producto_id");
            Integer cantidad = rs.getInt("cantidad");
            ProductoInfo info = productoClient.obtenerPrecioEIva(productoId);
            return new DetalleCarritoTmp(productoId, cantidad, info.precioUnitario(), info.porcentajeIva());
        }, carritoId);

        if (items.isEmpty()) {
            throw new IllegalStateException("El carrito " + carritoId + " está vacío");
        }

        // 3) Calcular subtotal y total
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleCarritoTmp item : items) {
            BigDecimal lineaSubtotal = item.precioUnitario.multiply(BigDecimal.valueOf(item.cantidad));
            BigDecimal factorIva = BigDecimal.ONE.add(item.porcentajeIva.divide(BigDecimal.valueOf(100)));
            BigDecimal lineaTotal = lineaSubtotal.multiply(factorIva);
            subtotal = subtotal.add(lineaSubtotal);
            total = total.add(lineaTotal);
        }

        // 4) Insertar orden
        String sqlOrden = "INSERT INTO pedidos.orden (usuario_id, direccion_id, metodopago_id, subtotal, total, fecha) " +
                "VALUES (?, ?, ?, ?, ?, ?) RETURNING orden_id";
        LocalDate hoy = LocalDate.now();
        Integer ordenId = jdbcTemplate.queryForObject(sqlOrden, Integer.class,
                usuarioId, direccionId, metodopagoId, subtotal, total, hoy);

        // 5) Insertar detalle_orden por cada línea del carrito
        String sqlDetalleOrden = "INSERT INTO pedidos.detalle_orden " +
                "(cantidad, precio_unitario, total, subtotal, iva, orden_id, producto_id, fecha) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        for (DetalleCarritoTmp item : items) {
            BigDecimal lineaSubtotal = item.precioUnitario.multiply(BigDecimal.valueOf(item.cantidad));
            BigDecimal factorIva = BigDecimal.ONE.add(item.porcentajeIva.divide(BigDecimal.valueOf(100)));
            BigDecimal lineaTotal = lineaSubtotal.multiply(factorIva);
            BigDecimal lineaIva = lineaTotal.subtract(lineaSubtotal);

            jdbcTemplate.update(sqlDetalleOrden,
                    item.cantidad, item.precioUnitario, lineaTotal, lineaSubtotal, lineaIva,
                    ordenId, item.productoId, hoy);
        }

        // 6) Vaciar el carrito (el trigger recalcula el total a 0 automáticamente)
        String sqlVaciar = "DELETE FROM pedidos.carrito_detalle WHERE carrito_id = ?";
        jdbcTemplate.update(sqlVaciar, carritoId);

        // 7) Registrar la solicitud idempotente en la MISMA transaccion: si otra
        // peticion concurrente con la misma clave ya gano la carrera, esto lanza
        // ClaveIdempotenciaEnConflictoException y hace rollback de todo lo anterior
        // (orden y detalle incluidos). El llamador (application.OrdenService) atrapa esa
        // excepcion y devuelve la orden de la solicitud ganadora en su lugar.
        if (idempotencyKey != null) {
            idempotenciaRepository.ifPresent(repo -> repo.registrar(usuarioId, idempotencyKey, payloadHash, ordenId));
        }

        return new Orden(ordenId, usuarioId, direccionId, metodopagoId, subtotal, total, hoy);
    }

    private void validarCheckout(Integer usuarioId, Integer direccionId, Integer metodopagoId) {
        // 0) Validar usuario y dirección
        // Solo un 404 real de usuarios-service significa "no existe" (400). Cualquier
        // otra falla (circuit breaker abierto, timeout, 5xx) NO es lo mismo y no debe
        // camuflarse como error del cliente: se deja propagar para que el handler
        // correspondiente (503 en ambos casos) lo reporte tal cual es.
        try {
            usuarioClient.obtenerUsuario(usuarioId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Usuario " + usuarioId + " no existe en usuarios-service", e);
        }

        List<DireccionInfo> direcciones = usuarioClient.obtenerDirecciones(usuarioId);
        boolean direccionValida = direcciones.stream()
                .anyMatch(d -> direccionId.equals(d.direccionId()) && Boolean.TRUE.equals(d.habilitado()));

        if (!direccionValida) {
            throw new IllegalArgumentException(
                    "La direccion " + direccionId + " no pertenece al usuario " + usuarioId + " o está deshabilitada");
        }

        // 0.1) Validar método de pago
        String sqlMetodo = "SELECT COUNT(*) FROM pedidos.metodopago " +
                "WHERE metodopago_id = ? AND usuario_id = ? AND habilitado = true";
        Integer countMetodo = jdbcTemplate.queryForObject(sqlMetodo, Integer.class, metodopagoId, usuarioId);
        if (countMetodo == null || countMetodo == 0) {
            throw new IllegalArgumentException(
                    "El metodo de pago " + metodopagoId + " no existe, no pertenece al usuario " + usuarioId + " o esta deshabilitado");
        }

    }

    private <T> T medirConsulta(Supplier<T> consulta) {
        CrdbMetrics metrics = crdbMetrics.getIfAvailable();
        if (metrics == null) {
            return consulta.get();
        }

        Timer.Sample sample = metrics.startQuery();
        try {
            return consulta.get();
        } finally {
            metrics.stopQuery(sample);
        }
    }

    private static class DetalleCarritoTmp {
        Integer productoId;
        Integer cantidad;
        BigDecimal precioUnitario;
        BigDecimal porcentajeIva;

        DetalleCarritoTmp(Integer productoId, Integer cantidad, BigDecimal precioUnitario, BigDecimal porcentajeIva) {
            this.productoId = productoId;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
            this.porcentajeIva = porcentajeIva;
        }
    }
}
