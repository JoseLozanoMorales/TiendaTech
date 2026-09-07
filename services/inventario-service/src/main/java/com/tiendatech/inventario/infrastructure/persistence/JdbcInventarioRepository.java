package com.tiendatech.inventario.infrastructure.persistence;

import com.tiendatech.inventario.domain.InventarioRepository;
import com.tiendatech.inventario.domain.StockProducto;
import com.tiendatech.inventario.presentation.dto.MovimientoInventarioRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcInventarioRepository implements InventarioRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcInventarioRepository.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final Validator validator;

    public JdbcInventarioRepository(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                    IdempotencyGuard idempotencyGuard, Validator validator) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.idempotencyGuard = idempotencyGuard;
        this.validator = validator;
    }

    @Override
    public List<Map<String, Object>> listarMovimientos() {
        return jdbc.queryForList("""
                SELECT m.movimiento_id, m.fecha, t.nombre AS tipo, s.nombre AS subtipo,
                       p.nombre AS producto, m.cantidad, m.costo_unitario, m.costo_total,
                       m.referencia, m.observacion, m.producto_id
                FROM inventario.movimiento_inventario m
                JOIN inventario.inventario_producto p ON p.producto_id = m.producto_id
                JOIN inventario.subtipo_movimiento s ON s.subtipo_id = m.subtipo_id
                JOIN inventario.tipo_movimiento t ON t.tipo_id = s.tipo_id
                ORDER BY m.fecha DESC, m.movimiento_id DESC
                """);
    }

    @Override
    public List<Map<String, Object>> listarSubtipos(Integer tipo) {
        return jdbc.queryForList("""
                SELECT subtipo_id, nombre, tipo_id FROM inventario.subtipo_movimiento
                WHERE (CAST(? AS INT8) IS NULL OR tipo_id = ?) ORDER BY tipo_id, nombre
                """, tipo, tipo);
    }

    @Override
    public StockProducto obtenerStock(Integer productoId) {
        if (productoId == null) {
            throw new IllegalArgumentException("Falta productoId");
        }

        List<StockProducto> rows = jdbc.query(
                "SELECT producto_id, nombre, stock FROM inventario.inventario_producto WHERE producto_id = ? AND habilitado",
                (rs, rowNum) -> new StockProducto(
                        rs.getLong("producto_id"),
                        rs.getString("nombre"),
                        rs.getInt("stock")),
                productoId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto " + productoId + " no existe");
        }
        return rows.getFirst();
    }

    @Override
    public List<StockProducto> listarStock(List<Integer> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un productoId");
        }

        List<Integer> ids = productoIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un productoId valido");
        }

        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbc.query("""
                SELECT producto_id, nombre, stock FROM inventario.inventario_producto
                WHERE habilitado AND producto_id IN (%s) ORDER BY producto_id
                """.formatted(placeholders),
                (rs, rowNum) -> new StockProducto(
                        rs.getLong("producto_id"),
                        rs.getString("nombre"),
                        rs.getInt("stock")),
                ids.toArray());
    }

    @Transactional
    @Override
    public boolean registrarMovimiento(String movimientoJson, String usuario, String idempotencyKey) {
        JsonNode body;
        try {
            body = movimientoJson == null ? null : objectMapper.readTree(movimientoJson);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("El cuerpo de la solicitud no contiene JSON valido", ex);
        }
        List<MovimientoInventarioRequest> items = normalizarItems(body);
        bloquearProductos(items);
        if (idempotencyGuard.isReplay(idempotencyKey, items)) {
            return true;
        }
        for (MovimientoInventarioRequest item : items) {
            registrarItem(item);
        }
        return false;
    }

    /**
     * Primera operacion SQL de la transaccion de movimiento. Tomar todos los
     * locks en orden de producto evita interbloqueos y hace que CockroachDB
     * resuelva la espera antes de crear la fila idempotente o el kardex.
     */
    private void bloquearProductos(List<MovimientoInventarioRequest> items) {
        List<Integer> productoIds = items.stream()
                .map(MovimientoInventarioRequest::getProductoId)
                .distinct()
                .sorted()
                .toList();
        String placeholders = String.join(",", productoIds.stream().map(id -> "?").toList());
        jdbc.queryForList("""
                SELECT producto_id
                  FROM inventario.inventario_producto
                 WHERE producto_id IN (%s)
                 ORDER BY producto_id
                 FOR UPDATE
                """.formatted(placeholders), productoIds.toArray());
    }

    private void registrarItem(MovimientoInventarioRequest item) {
        Integer productoId = item.getProductoId();
        Integer subtipoId = item.getSubtipoId();
        Integer cantidad = item.getCantidad();

        String tipo = jdbc.queryForObject("""
                SELECT t.nombre FROM inventario.subtipo_movimiento s
                JOIN inventario.tipo_movimiento t ON t.tipo_id = s.tipo_id
                WHERE s.subtipo_id = ?
                """, String.class, subtipoId);
        if (!"AJUSTE".equalsIgnoreCase(tipo) && cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }

        Map<String, Object> producto = jdbc.queryForMap("""
                SELECT stock, costo, precio_referencia FROM inventario.inventario_producto
                WHERE producto_id = ? FOR UPDATE
                """, productoId);
        int stockAnterior = ((Number) producto.get("stock")).intValue();
        BigDecimal costoAnterior = decimal(producto.get("costo"));
        BigDecimal precioActual = decimal(producto.get("precio_referencia"));
        if ("SALIDA".equalsIgnoreCase(tipo) && stockAnterior < cantidad) {
            throw new IllegalArgumentException("Stock insuficiente para el producto " + productoId);
        }

        BigDecimal costoEntrada = item.getCostoUnitario();
        BigDecimal costoEfectivo = "ENTRADA".equalsIgnoreCase(tipo) && costoEntrada != null
                ? costoEntrada : costoAnterior;
        int stockNuevo = "SALIDA".equalsIgnoreCase(tipo)
                ? stockAnterior - cantidad : stockAnterior + cantidad;
        BigDecimal costoNuevo = costoAnterior;
        if ("ENTRADA".equalsIgnoreCase(tipo) && costoEntrada != null && stockNuevo > 0) {
            costoNuevo = costoAnterior.multiply(BigDecimal.valueOf(stockAnterior))
                    .add(costoEntrada.multiply(BigDecimal.valueOf(cantidad)))
                    .divide(BigDecimal.valueOf(stockNuevo), 2, RoundingMode.HALF_UP);
        }
        BigDecimal total = costoEfectivo.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
        Timestamp fecha = timestamp(item.getFecha());

        jdbc.update("""
                INSERT INTO inventario.movimiento_inventario
                    (fecha, cantidad, costo_unitario, costo_total, referencia,
                     observacion, producto_id, subtipo_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fecha, cantidad, costoEfectivo, total, item.getReferencia(),
                item.getObservacion(), productoId, subtipoId);
        // Decision de negocio: si esta entrada sube el costo promedio ponderado por encima del
        // precio de venta actual, ya NO se bloquea la recepcion (no hay CHECK en la BD para
        // esto). En vez de eso el producto se auto-desactiva (habilitado=false): el stock y el
        // costo se actualizan igual (el kardex queda correcto), pero el producto deja de
        // aparecer en el catalogo del cliente hasta que el admin repreicie y lo reactive desde
        // productos-service. Si ya estaba deshabilitado, no lo reactivamos solos aqui.
        boolean debeDesactivar = costoNuevo.compareTo(precioActual) > 0;
        if (debeDesactivar) {
            LOG.warn("Producto {} auto-desactivado: costo recalculado ${} supera el precio actual ${}",
                    productoId, costoNuevo, precioActual);
        }
        jdbc.update("""
                UPDATE inventario.inventario_producto
                   SET stock = ?, costo = ?, valor_inventario = ?, actualizado_en = now(),
                       habilitado = CASE WHEN ? THEN false ELSE habilitado END
                 WHERE producto_id = ?
                """, stockNuevo, costoNuevo,
                costoNuevo.multiply(BigDecimal.valueOf(stockNuevo)).setScale(2, RoundingMode.HALF_UP),
                debeDesactivar, productoId);
        jdbc.update("""
                INSERT INTO inventario.kardex_inventario
                    (fecha, tipo_operacion, cantidad_entrada, costo_unitario_entrada,
                     costo_total_entrada, cantidad_salida, costo_unitario_salida,
                     costo_total_salida, saldo_cantidad, saldo_costo_unitario,
                     saldo_total, producto_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, fecha, tipo,
                "ENTRADA".equalsIgnoreCase(tipo) ? cantidad : 0,
                "ENTRADA".equalsIgnoreCase(tipo) ? costoEfectivo : null,
                "ENTRADA".equalsIgnoreCase(tipo) ? total : null,
                "SALIDA".equalsIgnoreCase(tipo) ? cantidad : 0,
                "SALIDA".equalsIgnoreCase(tipo) ? costoEfectivo : null,
                "SALIDA".equalsIgnoreCase(tipo) ? total : null,
                stockNuevo, costoNuevo,
                costoNuevo.multiply(BigDecimal.valueOf(stockNuevo)).setScale(2, RoundingMode.HALF_UP), productoId);
    }

    private List<MovimientoInventarioRequest> normalizarItems(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es obligatorio");
        }
        List<MovimientoInventarioRequest> items;
        if (body.isArray()) {
            items = objectMapper.convertValue(
                    body,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, MovimientoInventarioRequest.class));
        } else if (body.isObject()) {
            items = List.of(objectMapper.convertValue(body, MovimientoInventarioRequest.class));
        } else {
            throw new IllegalArgumentException("El cuerpo debe ser un movimiento o una lista de movimientos");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos un movimiento");
        }
        for (int index = 0; index < items.size(); index++) {
            validarItem(items.get(index), index);
        }
        return items;
    }

    private void validarItem(MovimientoInventarioRequest item, int index) {
        Set<ConstraintViolation<MovimientoInventarioRequest>> errors = validator.validate(item);
        if (!errors.isEmpty()) {
            String detail = errors.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Movimiento " + (index + 1) + ": " + detail);
        }
    }

    private static BigDecimal decimal(Object value) {
        BigDecimal result = decimalNullable(value);
        return result == null ? BigDecimal.ZERO : result;
    }

    private static BigDecimal decimalNullable(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static Timestamp timestamp(Object value) {
        if (value == null || value.toString().isBlank()) return Timestamp.valueOf(LocalDateTime.now());
        return Timestamp.valueOf(value.toString().replace('T', ' '));
    }
}
