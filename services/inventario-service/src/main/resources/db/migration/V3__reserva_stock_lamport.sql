-- Traslada a Flyway lo que hasta ahora creaba
-- InventarioSchemaInitializer.java (@PostConstruct, fuera del control de
-- migraciones): la reserva de stock con reloj Lamport para el carrito de
-- compra concurrente. `inventario.solicitud_idempotente` (la tercera
-- tabla que creaba ese mismo @PostConstruct) no se repite aqui porque ya
-- vive en V1__esquema_base.sql desde el corte original.
--
-- Con esto, un cluster levantado desde cero solo con migraciones ya
-- refleja la forma real de inventario-service (35 tablas, no 33): estas
-- dos tablas y el indice existian en produccion desde antes del punto 5,
-- solo que se creaban por codigo en vez de por Flyway.

CREATE TABLE IF NOT EXISTS inventario.reserva_stock (
    carrito_id INT8 NOT NULL,
    producto_id INT8 NOT NULL,
    usuario_id INT8 NOT NULL,
    cantidad INT4 NOT NULL CHECK (cantidad >= 0),
    lamport INT8 NOT NULL,
    dispositivo_id VARCHAR(100) NOT NULL,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (carrito_id, producto_id)
);

-- La PK ordena por carrito_id primero, asi que el SELECT SUM(cantidad) de
-- StockReservationService.reconcileOnce (filtra por producto_id) no puede
-- usarla y hace table scan completo. Bajo escritura concurrente, ese scan
-- lee filas de CUALQUIER producto y CockroachDB (SSI) lo invalida ante el
-- UPSERT de cualquier otro producto -- no solo del mismo -- generando
-- RETRY_SERIALIZABLE incluso entre carritos que no comparten producto. El
-- indice acota el scan a las filas del producto pedido.
CREATE INDEX IF NOT EXISTS idx_reserva_stock_producto
    ON inventario.reserva_stock (producto_id);

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
);
