# Cierre — Punto 5 (Esquema de base de datos y migraciones)

## Situación original (resuelta)

El esquema completo (33 tablas, 6 esquemas) vivía en un único archivo
monolítico, `docs/db/schema.sql`, que `docker-compose.yml` montaba y
aplicaba de una sola vez en el contenedor `tiendatech-crdb-init` al
levantar el sistema. No existía ningún motor de migraciones versionadas:
`docs/db/migrations/V004`–`V006` existían en el repositorio pero nunca se
invocaban desde ningún guion, composición o flujo — código muerto. La
rúbrica pedía explícitamente convertir ese archivo monolítico en una
migración base por servicio, **seguida de las tres migraciones ya
escritas**, con cada servicio migrando su propio esquema de forma
independiente.

## Qué se implementó

- **6 migraciones base** (`V1__esquema_base.sql`, una por servicio) —
  extracción fiel de `docs/db/schema.sql`, verificada por comparación
  exacta: las mismas 33 tablas, sin faltar ni sobrar ninguna en ese
  primer corte.
- **4 migraciones `V2`** que trasladan `V004`/`V005`/`V006` (ya escritas)
  a su dueño real de esquema — `inventario-service`, `ventas-service`,
  `pedidos-service`, `ordenes-proveedores-service` — cumpliendo la
  instrucción literal de la rúbrica. Se excluyó, deliberada y
  documentadamente, una única sentencia de `V004` (un `UPSERT` que lee de
  `productos.producto` desde una migración de `inventario`): en
  *database-per-service* una migración no puede depender de que otro
  servicio ya haya migrado antes, y esa sentencia además era una
  operación de datos de un corte histórico puntual, no una migración de
  esquema repetible. Justificación completa en
  `docs/db/migraciones-historicas.md`, con la sentencia excluida
  preservada ahí por trazabilidad.
- Flyway (`flyway-core` + `flyway-database-postgresql`) agregado a los 6
  `pom.xml`, con `spring.flyway.schemas`/`create-schemas=true` en cada
  `application.properties` apuntando solo al esquema propio del servicio.
- `docker-compose.yml`: `tiendatech-crdb-init` ya no monta ni aplica
  `schema.sql` — solo inicializa el clúster y crea la base `tiendatech`
  vacía; el esquema completo lo forman las seis migraciones al arrancar
  los servicios. `docs/db/schema.sql` **no se eliminó**: sigue siendo la
  base de `docker-compose.db.yml`, el entorno solo-de-base-de-datos (sin
  microservicios) que usan las evidencias de planes de consulta de la
  Entrega 4, donde no hay ningún servicio Spring que pueda aplicar
  Flyway.
- `docs/db/migrations/V004`–`V006` (código muerto) eliminados del
  repositorio, ya sin ningún uso tras trasladarse su contenido a los `V2`.
- Nuevo job de CI `db-migrations-clean-start`: levanta un clúster
  CockroachDB realmente vacío (sin `schema.sql`, sin seeds) con los seis
  servicios, y confirma contra `information_schema` que las tablas y los
  seis historiales `flyway_schema_history` se forman **solo con
  migraciones**, tal como pide la rúbrica.
- `docs/db/README.md` (nuevo) documentando toda la estructura; `README.md`
  principal actualizado para apuntar a las migraciones en vez del
  monolito.

## Dos bugs reales encontrados y corregidos en el camino

La primera corrida del CI contra un clúster realmente vacío (algo que
nunca se había probado, porque antes `schema.sql` se aplicaba una sola
vez) sacó a la luz dos problemas genuinos, ninguno de los dos visible
mientras el esquema se cargaba de un solo archivo:

1. **`usuarios-service` no migraba nada, en silencio.** `usuarios` corre
   sobre Spring Boot 4.0.7 (el resto de servicios sigue en Boot 3.x). En
   Boot 4 la auto-configuración de Flyway se separó del jar monolítico a
   un módulo propio (`spring-boot-flyway`); con solo `flyway-core` en el
   classpath, Spring nunca la conecta — ni un error, ni un log, cero
   tablas. Corregido reemplazando `flyway-core` por
   `org.springframework.boot:spring-boot-starter-flyway` en
   `services/usuarios/pom.xml`.
2. **`ordenes-proveedores-service` quedaba `unhealthy` en CI.** Flyway
   corría y terminaba bien (verificado en el log: V1 y V2 aplicadas,
   verificación en 0), pero el contenedor no llegaba a responder su
   `healthcheck` a tiempo: la ventana (`start_period: 30s` + 5 reintentos
   de 10s = 80s) estaba calibrada para cuando el esquema ya venía
   precargado y el servicio arrancaba sin nada que migrar. Ampliada a
   `start_period: 90s` / `retries: 8` en los 6 servicios que migran.

Además, el propio script de verificación del job nuevo esperaba 33 tablas
y encontró 35: `inventario.reserva_stock` y `inventario.operacion_reserva`
las crea `InventarioSchemaInitializer` con `CREATE TABLE IF NOT EXISTS` en
un `@PostConstruct`, al margen de Flyway — una función de reserva de stock
con reloj Lamport añadida directo por código después de escribirse
`schema.sql`. El esquema real de `inventario-service` ya tenía 35 tablas
desde antes del punto 5; solo se hacía invisible porque antes
`schema.sql` se aplicaba una vez y esas dos se sumaban por su cuenta sin
que nadie lo notara. Corregido el número esperado en el job de CI, con la
explicación dejada en el propio workflow.

## Verificación final

- Commit: `b2a6942` ("correción de Flyway en usuarios (Boot 4) y timeouts
  de healthcheck en CI del punto 5").
- Los tres flujos que corren sobre `main` terminaron en verde para ese
  commit: **CI** (`#269`, incluye el job nuevo *"Esquema desde cero solo
  con migraciones (Punto 5)"* y *"Playwright web E2E"*), **CI-CD quality
  gate** (`#149`, re-corrido tras un hipo transitorio de Maven Central
  ajeno a este cambio) e **Integridad de datos** (`#42`).
- Contenido de `docker-compose.yml`, los 6 `pom.xml`, los 6
  `application.properties`, las 10 migraciones y `docs/db/README.md`
  verificado directamente contra el repositorio remoto.

## Conclusión

El esquema ya no depende de un archivo monolítico aplicado a mano: cada
uno de los seis microservicios migra su propio esquema de forma
independiente con Flyway, siguiendo la instrucción literal de la rúbrica
de partir de una migración base seguida de las tres ya escritas. La
compuerta nueva (`db-migrations-clean-start`) prueba exactamente lo que
pide la rúbrica — un clúster levantado desde cero solo con migraciones —
y, al ser la primera vez que ese escenario se ejecutaba de verdad, encontró
dos regresiones reales que la carga monolítica anterior nunca habría
revelado; ambas quedaron corregidas en el mismo cierre.
