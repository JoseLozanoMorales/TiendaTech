-- Traslado de docs/db/migrations/V005__ventas_propietario_facturas.sql
-- (ya escrita) al historial Flyway de su dueño real: ventas. Contenido
-- identico al original; es enteramente propia de este esquema, sin
-- dependencias cruzadas. No-op contra el estado actual (docs/db/schema.sql
-- ya no tiene esta FK en su V1__esquema_base.sql), conservada tal como fue
-- escrita para que el historial de Flyway sea fiel a la migracion real.

ALTER TABLE ventas.factura_encabezado
    DROP CONSTRAINT IF EXISTS fk_factura_orden;

-- Verificación: debe devolver cero filas.
SELECT constraint_name
FROM information_schema.table_constraints
WHERE table_schema = 'ventas'
  AND table_name = 'factura_encabezado'
  AND constraint_name = 'fk_factura_orden';
