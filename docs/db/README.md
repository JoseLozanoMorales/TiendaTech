# Esquema de base de datos y migraciones

**El esquema ya no se aplica desde un archivo monolítico.** Hasta el
punto 5 de la guía de cierre, `docs/db/schema.sql` se montaba directo en
`docker-compose.yml` (servicio `tiendatech-crdb-init`) y las 33 tablas se
creaban de una sola vez, sin motor de migraciones ni historial versionado.

Ahora cada uno de los seis microservicios Java gestiona su propio esquema
con **Flyway**, de forma independiente:

```
services/<servicio>/src/main/resources/db/migration/
    V1__esquema_base.sql   -- todas las tablas de ese esquema
    V2__...sql             -- (solo en inventario, pedidos, ordenes-proveedores
                               y ventas) las migraciones que en su momento
                               vivían en docs/db/migrations/, trasladadas al
                               dueño real de cada cambio
    V3__...sql             -- (solo en inventario) reserva_stock,
                               operacion_reserva y su índice, que antes
                               creaba InventarioSchemaInitializer por código
                               (@PostConstruct) al margen de Flyway
```

| Servicio | Esquema CockroachDB | Migraciones |
|---|---|---|
| `usuarios` | `usuarios` | V1 |
| `productos-service` | `productos` | V1 |
| `inventario-service` | `inventario` | V1, V2, V3 |
| `pedidos-service` | `pedidos` | V1, V2 |
| `ordenes-proveedores-service` | `ordenes_proveedores` | V1, V2 |
| `ventas-service` | `ventas` | V1, V2 |

Cada servicio ejecuta sus propias migraciones al arrancar
(`spring.flyway.*` en su `application.properties`), contra su propio
esquema únicamente — ningún servicio necesita que otro haya migrado antes
para arrancar. `docker-compose.yml` (el sistema completo) ya no monta ni
aplica `schema.sql` en `tiendatech-crdb-init`: ese contenedor solo
inicializa el clúster y crea la base `tiendatech` vacía; el esquema
completo lo dejan las seis migraciones al arrancar los servicios.

`docs/db/schema.sql` **no se elimina** del repositorio: sigue siendo la
base de `docker-compose.db.yml`, un entorno solo-de-base-de-datos (sin
microservicios) que usan las evidencias de planes de consulta de la
Entrega 4 (`docs/evidencias/docker-compose.nodo-unico-e4.yml`) — ahí no
hay ningún servicio Spring que pueda aplicar Flyway, así que ese entorno
sigue necesitando cargar el esquema completo de un solo archivo. Lo único
que cambió es que el **sistema real** (`docker-compose.yml`) ya no
depende de `schema.sql` para nada.

Al eliminarse `InventarioSchemaInitializer.java` (punto 5, segunda ronda —
ver más abajo), `schema.sql` se quedó sin `inventario.reserva_stock` ni
`inventario.operacion_reserva`, que esa clase creaba por código y que
`schema.sql` nunca tuvo: una divergencia real de dos tablas entre las dos
definiciones del esquema, no solo un riesgo teórico. Se corrigió
agregando ambas tablas a `schema.sql` (contenido idéntico al de la `V3` de
`inventario-service`) y con un control automático nuevo — ver
"Verificación" más abajo — que falla si las dos definiciones vuelven a
divergir.

`V1__esquema_base.sql` de cada servicio es una extracción fiel de
`schema.sql` — mismas definiciones, sin cambios — dividida por
propietario. Los antiguos `docs/db/migrations/V004`–`V006` (que existían
en el repositorio pero nunca se ejecutaban: código muerto, sin invocación
en ningún guion, composición o flujo) se eliminaron; su contenido se
trasladó como `V2` al servicio dueño de cada cambio, salvo una sentencia
de `V004` que se excluyó deliberadamente por depender de otro esquema —
ver `docs/db/migraciones-historicas.md` para el detalle y la
justificación.

## Verificación

`docs/db/tablas-esperadas.txt` es la lista única (35 `schema.tabla`, una
por línea) que usan los dos jobs siguientes de `.github/workflows/ci.yml`
— es la fuente de verdad compartida que hace automática la detección de
que las migraciones y `schema.sql` dejen de coincidir.

- **`db-migrations-clean-start`**: levanta un clúster CockroachDB nuevo y
  vacío (sin `schema.sql` ni semillas) y arranca los seis servicios. La
  comprobación automática que hace fallar el job es una comparación exacta
  (`diff`, sin diferencias = OK) entre la lista de `schema.tabla` que
  devuelve `information_schema.tables` y `docs/db/tablas-esperadas.txt`.
  Esa comparación cubre **solo nombres de tabla**, no columnas ni tipos:
  no hay ninguna aserción sobre el detalle de cada tabla. El job también
  consulta `flyway_schema_history` (versión y `success`) de los seis
  esquemas y deja esa salida en el log del job para inspección manual,
  pero no la compara contra ningún valor esperado ni falla si difiere — es
  evidencia, no una verificación automática.
- **`schema-sql-equivalencia`**: levanta `docker-compose.db.yml` (solo
  aplica `schema.sql`, sin ningún microservicio ni Flyway) y compara sus
  tablas contra el mismo `docs/db/tablas-esperadas.txt`, con el mismo
  `diff`. Si alguien agrega o quita una tabla en un lado (una migración
  `Vn` nueva, o un cambio directo a `schema.sql`) y se olvida del otro,
  este job falla — es el control automático de equivalencia entre las dos
  definiciones del esquema que antes no existía.
