-- Porcion propia de `pedidos` en
-- docs/db/migrations/V006__eliminar_fks_entre_esquemas.sql (ya escrita),
-- trasladada al historial Flyway de su dueño real. La otra porcion (sobre
-- `ordenes_proveedores`) va en el V2 de ordenes-proveedores-service; no
-- se ejecutan como un solo guion porque cada servicio migra su propio
-- esquema de forma independiente, sin depender del orden de arranque de
-- los demas.
--
-- Los IDs externos se validan mediante las APIs propietarias. Las FK entre
-- esquemas acoplan despliegues y contradicen database/schema-per-service.
--
-- Contenido identico al original para esta porcion. No-op contra el
-- estado actual (docs/db/schema.sql ya no declara estas FK en su
-- V1__esquema_base.sql), conservada tal como fue escrita para que el
-- historial de Flyway sea fiel a la migracion real.

ALTER TABLE pedidos.orden DROP CONSTRAINT IF EXISTS fk_orden_usuario;
ALTER TABLE pedidos.carrito_de_compra DROP CONSTRAINT IF EXISTS fk_carrito_usuario;
ALTER TABLE pedidos.metodopago DROP CONSTRAINT IF EXISTS fk_metodopago_usuario;
ALTER TABLE pedidos.solicitud_idempotente
    DROP CONSTRAINT IF EXISTS fk_solicitud_idempotente_usuario;

-- Debe devolver cero: ninguna FK de `pedidos` debe apuntar a una tabla de
-- OTRO esquema (una FK intra-esquema como fk_detalle_orden, hacia
-- pedidos.orden, es legitima y no debe contarse aqui).
SELECT count(*) AS fks_entre_esquemas
FROM information_schema.referential_constraints rc
JOIN information_schema.table_constraints tc
  ON tc.constraint_catalog = rc.constraint_catalog
 AND tc.constraint_schema = rc.constraint_schema
 AND tc.constraint_name = rc.constraint_name
JOIN information_schema.constraint_column_usage ccu
  ON ccu.constraint_catalog = rc.unique_constraint_catalog
 AND ccu.constraint_schema = rc.unique_constraint_schema
 AND ccu.constraint_name = rc.unique_constraint_name
WHERE tc.table_schema = 'pedidos'
  AND tc.table_schema <> ccu.table_schema;
