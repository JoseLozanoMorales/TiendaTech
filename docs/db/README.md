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
```

| Servicio | Esquema CockroachDB | Migraciones |
|---|---|---|
| `usuarios` | `usuarios` | V1 |
| `productos-service` | `productos` | V1 |
| `inventario-service` | `inventario` | V1, V2 |
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

`.github/workflows/ci.yml`, job `db-migrations-clean-start`: levanta un
clúster CockroachDB nuevo y vacío (sin `schema.sql` ni semillas), arranca
los seis servicios, y confirma contra `information_schema` que las 33
tablas, sus columnas clave y los seis historiales Flyway
(`flyway_schema_history` en cada esquema) quedan exactamente como se
espera — solo con migraciones, sin ningún archivo aplicado a mano.
