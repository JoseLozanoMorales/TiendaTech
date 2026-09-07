package com.tiendatech.inventario.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventarioSchemaInitializer {
    private final JdbcTemplate jdbc;

    public InventarioSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initializeIdempotencyLedger() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inventario.solicitud_idempotente (
                    clave VARCHAR(200) PRIMARY KEY,
                    payload_hash VARCHAR(64) NOT NULL,
                    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inventario.reserva_stock (
                    carrito_id INT8 NOT NULL,
                    producto_id INT8 NOT NULL,
                    usuario_id INT8 NOT NULL,
                    cantidad INT4 NOT NULL CHECK (cantidad >= 0),
                    lamport INT8 NOT NULL,
                    dispositivo_id VARCHAR(100) NOT NULL,
                    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
                    PRIMARY KEY (carrito_id, producto_id)
                )
                """);
        // La PK ordena por carrito_id primero, asi que el SELECT SUM(cantidad)
        // de StockReservationService.reconcileOnce (filtra por producto_id) no
        // puede usarla y hace table scan completo. Bajo escritura concurrente,
        // ese scan lee filas de CUALQUIER producto y CockroachDB (SSI) lo
        // invalida ante el UPSERT de cualquier otro producto -- no solo del
        // mismo -- generando RETRY_SERIALIZABLE incluso entre carritos que no
        // comparten producto. El indice acota el scan a las filas del
        // producto pedido.
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_reserva_stock_producto
                    ON inventario.reserva_stock (producto_id)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inventario.operacion_reserva (
                    operacion_id UUID PRIMARY KEY,
                    carrito_id INT8 NOT NULL,
                    producto_id INT8 NOT NULL,
                    aceptada BOOL NOT NULL,
                    cantidad_reservada INT4 NOT NULL,
                    stock_disponible INT4 NOT NULL,
                    lamport INT8 NOT NULL,
                    dispositivo_ganador VARCHAR(100) NOT NULL,
                    mensaje VARCHAR(300) NOT NULL,
                    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
    }
}
