-- Traslado de docs/db/migrations/V004__inventario_propietario_stock.sql
-- (ya escrita) al historial Flyway de su dueño real: inventario.
--
-- Las tres sentencias ALTER de aqui abajo son identicas a las de V004
-- original y son no-op contra el estado actual (docs/db/schema.sql ya
-- incluye estas columnas y esta restriccion en su V1__esquema_base.sql),
-- pero se conservan tal como fueron escritas para que el historial de
-- Flyway sea fiel a la migracion real que documenta este paso.
--
-- Lo que NO se traslada aqui: el UPSERT INTO inventario.inventario_producto
-- ... SELECT ... FROM productos.producto de V004 original. Esa sentencia
-- lee de un esquema ajeno (productos) durante la migracion de OTRO
-- servicio (inventario), lo que rompe la independencia de arranque que
-- este mismo punto exige (un clon limpio de inventario-service no puede
-- depender de que productos-service ya haya migrado). Fue una migracion
-- de datos de un corte historico especifico -- no una migracion de
-- esquema repetible -- y queda documentada como texto de referencia en
-- docs/db/migraciones-historicas/README.md en vez de ejecutarse aqui.

ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS nombre STRING;
ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS costo DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS precio_referencia DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE inventario.inventario_producto ADD COLUMN IF NOT EXISTS habilitado BOOL NOT NULL DEFAULT true;

ALTER TABLE inventario.inventario_producto ALTER COLUMN nombre SET NOT NULL;
ALTER TABLE inventario.inventario_producto DROP CONSTRAINT IF EXISTS fk_inventario_producto;
ALTER TABLE inventario.movimiento_inventario DROP CONSTRAINT IF EXISTS fk_movimiento_producto;
ALTER TABLE inventario.kardex_inventario DROP CONSTRAINT IF EXISTS fk_kardex_producto;
