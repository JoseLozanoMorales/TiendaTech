-- Puebla inventario.inventario_producto a partir de productos.producto.
--
-- Hallazgo real (documentado en docs/evidencias/punto17-observabilidad-gateway.md
-- y reconfirmado durante la preparación del punto 14): tras el cambio a
-- migraciones puras por servicio (punto 5), un clúster nuevo nunca queda con
-- filas en inventario.inventario_producto -- ningún script de docs/db/ ni
-- docker-compose.yml lo carga. La reserva de stock real (StockReservationService,
-- inventario-service) valida contra ESTA tabla, no contra productos.producto.stock,
-- así que sin este seed el primer "agregar al carrito" de cualquier flujo
-- autenticado (checkout, camino crítico del punto 14) falla con 409 "Stock
-- insuficiente" aunque productos.producto.stock muestre unidades disponibles.
--
-- Idempotente (ON CONFLICT DO NOTHING): no pisa cantidades que ya hayan sido
-- reservadas o ajustadas manualmente en una corrida previa.
--
-- Uso:
--   docker compose exec -T tiendatech-crdb-1 \
--     cockroach sql --insecure --host=localhost:26257 -d tiendatech --file=/dev/stdin \
--     < docs/db/seed-inventario-stock.sql
--
-- Requiere que productos.producto ya tenga filas (aplicar antes
-- product-management-reference.sql + seed-catalogo-sintetico.sql, o el
-- catálogo real del equipo).

USE tiendatech;

INSERT INTO inventario.inventario_producto
    (producto_id, nombre, stock, stock_minimo, costo, precio_referencia, habilitado, valor_inventario, actualizado_en)
SELECT
    p.producto_id,
    p.nombre,
    500,                          -- stock generoso: pensado para pruebas de carga, no para producción
    10,
    p.costo,
    p.preciounitario,
    p.habilitado,
    500 * p.costo,
    now()
FROM productos.producto p
ON CONFLICT (producto_id) DO NOTHING;

SELECT count(*) AS productos_con_stock_sembrado FROM inventario.inventario_producto;
