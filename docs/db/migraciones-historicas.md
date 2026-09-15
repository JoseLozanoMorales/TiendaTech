# Migraciones históricas no trasladadas al historial Flyway

Este documento preserva, por trazabilidad, el contenido de
`docs/db/migrations/V004__inventario_propietario_stock.sql` (versión
original, ya retirada del repositorio al implementar Flyway en los seis
servicios) que **no** se trasladó a ningún historial de migración
versionado.

## Qué se excluyó y por qué

```sql
-- Única lectura cruzada permitida durante la migración de propiedad.
UPSERT INTO inventario.inventario_producto
    (producto_id, nombre, stock, stock_minimo, costo, precio_referencia,
     habilitado, valor_inventario, actualizado_en)
SELECT p.producto_id, p.nombre, p.stock, COALESCE(i.stock_minimo, 0), p.costo,
       p.preciounitario, p.habilitado, p.valor_inventario, now()
FROM productos.producto p
LEFT JOIN inventario.inventario_producto i ON i.producto_id = p.producto_id;

-- Verificación esperada antes del despliegue del nuevo inventario-service.
SELECT count(*) AS productos_sin_inventario
FROM productos.producto p
LEFT JOIN inventario.inventario_producto i ON i.producto_id = p.producto_id
WHERE i.producto_id IS NULL;
```

Esta sentencia lee de `productos.producto` (esquema ajeno) dentro de una
migración de `inventario-service`. En una arquitectura *database-per-service*
con Flyway independiente por servicio — que es justamente lo que este punto
implementa — una migración de un servicio no puede depender de que otro
servicio ya haya migrado antes: `inventario-service` puede arrancar antes,
después o sin que `productos-service` exista todavía (por ejemplo, en un job
de CI que solo levanta un servicio para probar sus propias migraciones). Si
esta sentencia se hubiera incluido en el historial Flyway de `inventario`,
un arranque en ese orden habría fallado con `relation "productos.producto"
does not exist`, reintroduciendo exactamente el acoplamiento entre esquemas
que el resto de este punto elimina.

Fue, además, una operación de **datos** de un corte histórico específico
("migración de propiedad" de un lote existente de productos hacia
inventario), no una migración de **esquema** repetible de forma segura en
cualquier entorno — el propio comentario original ("Única lectura cruzada
permitida durante la migración de propiedad") ya la señalaba como una
excepción puntual, no como parte del flujo normal.

## Si hiciera falta en un entorno real con datos existentes

Si en algún momento hay que migrar una base con datos reales de producción
que todavía no pasó por este corte, la sentencia de arriba sigue siendo
válida y puede ejecutarse manualmente, una sola vez, con ambos esquemas
(`productos` e `inventario`) ya migrados y verificados. No debe agregarse
como una versión Flyway automática de ningún servicio.
