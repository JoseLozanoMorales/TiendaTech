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

## Corrección de una segunda ronda de hallazgos del evaluador

Una revisión posterior, más profunda, señaló que el cierre anterior era
incompleto en dos puntos concretos (además de otros que se detallan al
final de esta sección y que **siguen sin resolver**):

1. **La `V1` de `pedidos` no era una extracción fiel del monolito.**
   `docs/db/schema.sql` (líneas 468-489) tenía cinco sentencias de
   fragmentación física — dos `ALTER TABLE ... SPLIT AT VALUES`
   (`pedidos.orden`, `pedidos.detalle_orden`), dos `ALTER TABLE ...
   SCATTER` y un `ALTER RANGE default CONFIGURE ZONE USING num_replicas =
   3` — que nunca se trasladaron a `services/pedidos-service/.../V1__esquema_base.sql`
   cuando se dividió el monolito por servicio. Un clúster levantado desde
   cero solo con migraciones perdía la fragmentación trimestral que
   sostiene la parte de "Datos y consistencia" del sistema distribuido.
   **Corrección**: las cinco sentencias se agregaron al final de la `V1`
   de `pedidos-service`, en el mismo corte que las creó originalmente.
2. **Dos tablas y un índice de `inventario` se creaban por código, no por
   Flyway.** `InventarioSchemaInitializer.java` (`@PostConstruct`) creaba
   `inventario.reserva_stock`, `inventario.operacion_reserva` y el índice
   `idx_reserva_stock_producto` con `CREATE ... IF NOT EXISTS`, al margen
   de cualquier migración versionada — contradiciendo la instrucción de
   que el esquema se forme "solo con migraciones". (Una tercera tabla que
   el mismo método creaba, `inventario.solicitud_idempotente`, no tenía
   este problema: ya estaba en `V1` desde el primer cierre.)
   **Corrección**: se creó `inventario-service/.../V3__reserva_stock_lamport.sql`
   con las dos tablas y el índice, y se eliminó
   `InventarioSchemaInitializer.java` por completo — ya no hace falta,
   Flyway cubre las tres tablas que antes dependían de código.

Como consecuencia directa de corregir (2), el job de CI
`db-migrations-clean-start` (`.github/workflows/ci.yml`) se actualizó: el
comentario que justificaba por qué se esperaban 35 tablas en vez de 33
(atribuyéndolo a un bypass de código) ya no aplica — ahora las 35 salen
solo de Flyway — y se corrigió además una inconsistencia menor que tenía
el propio job (el mensaje de diff seguía diciendo "33 esperadas" aunque la
lista ya comparaba contra 35).

### Verificación real (local, pendiente de PR y CI en verde)

Ambas correcciones se probaron contra CockroachDB real, no solo
compilación:

- **Inventario**: se vació el esquema (`DROP SCHEMA inventario CASCADE`)
  y se reconstruyó el contenedor. El log de Flyway muestra las tres
  migraciones aplicándose en orden desde cero —
  `Migrating schema "inventario" to version "1 - esquema base"`,
  `"2 - inventario propietario stock"`, `"3 - reserva stock lamport"` —
  y `Successfully applied 3 migrations to schema "inventario", now at
  version v3`. El contenedor quedó `healthy` (confirma que
  `IdempotencyGuard`, que depende de estas tablas, arrancó bien sin el
  `@PostConstruct` eliminado). Consulta directa a
  `information_schema.tables` confirmó las 8 tablas esperadas del esquema
  `inventario` (incluidas `reserva_stock` y `operacion_reserva`), ni una
  de más ni de menos.
- **Pedidos**: mismo procedimiento (`DROP SCHEMA pedidos CASCADE` +
  reconstrucción) para evitar un conflicto de checksum de Flyway, ya que
  el contenido de la `V1` cambió. El log muestra `V1` y `V2` aplicándose
  limpio desde cero (`Successfully applied 2 migrations to schema
  "pedidos", now at version v2`), incluyendo la salida real de los
  `SPLIT AT`/`SCATTER` durante la ejecución. `SHOW RANGES FROM TABLE
  pedidos.orden` confirmó el resultado físico: la tabla quedó partida en
  6 rangos (los 5 límites trimestrales fijados por `SPLIT AT`, más el
  rango inicial), cada uno con `replicas: {1,2,3}` — tres réplicas
  activas, confirmando que `CONFIGURE ZONE USING num_replicas = 3` sí se
  aplicó.

### Corrección de dos hallazgos adicionales (enlace roto y `baseline-on-migrate`)

En la misma iteración se corrigieron, además, dos hallazgos más de esa
segunda revisión:

3. **Enlace roto en el comentario del `V2` de `inventario`.** El
   comentario citaba `docs/db/migraciones-historicas/README.md`, una ruta
   que no existe — el archivo real es `docs/db/migraciones-historicas.md`
   (sin subcarpeta `README.md`). **Corrección**: se corrigió la ruta en el
   comentario de
   `services/inventario-service/.../V2__inventario_propietario_stock.sql`.
5. **`spring.flyway.baseline-on-migrate=true` activo en `usuarios`.** Esta
   bandera quedó fija en el archivo committeado desde una corrección
   puntual de un entorno local (documentada en el propio
   `application.properties`), pero al estar activa en todo lugar —CI,
   producción, cualquier clon del repo— le dice a Flyway "trata V1 como ya
   aplicado" sin verificar que el esquema realmente coincide con V1: si
   algún esquema queda con columnas o cambios que no vinieron de Flyway,
   la bandera los acepta en silencio en vez de que Flyway detecte la
   divergencia real (`Found non-empty schema(s) but no schema history
   table`), que es justamente la función de este punto. **Corrección**: se
   eliminaron `spring.flyway.baseline-on-migrate=true` y
   `spring.flyway.baseline-version=1` de
   `services/usuarios/src/main/resources/application.properties`,
   reemplazando el comentario por la explicación del riesgo y el
   procedimiento de recuperación seguro y ya verificado (vaciar el esquema
   con `DROP SCHEMA usuarios CASCADE` y dejar que Flyway lo reconstruya
   desde cero, en vez de baselinearlo a ciegas).

**Verificación real de ambas correcciones**: se reconstruyó
`tiendatech-usuarios` sin la bandera. El log de Flyway muestra
`Successfully validated 2 migrations (execution time 00:00.099s)`,
`Current version of schema "usuarios": 1`, `Schema "usuarios" is up to
date. No migration necesary.` — es decir, Flyway validó de verdad el
esquema existente contra las migraciones reales (no un baseline ciego) y
no encontró deriva. El servicio arrancó limpio (`Started UsuariosApplication
in 11.583 seconds`) y respondió `200` en `/health` de forma estable durante
los minutos siguientes: sin regresión de arranque al quitar la bandera. El
enlace corregido en el `V2` de `inventario` se verificó por lectura directa
del archivo tras el cambio.

### Corrección de dos hallazgos más (documentación falsa y falta de control de equivalencia)

6. **`docs/db/README.md` afirmaba que el job de CI verificaba "las 33
   tablas, sus columnas clave" y los historiales Flyway "exactamente como
   se espera".** Al leer el job real (`db-migrations-clean-start` en
   `.github/workflows/ci.yml`) se confirmó que eso era falso en tres
   puntos: el número correcto ya era 35, no 33; la comparación cubre
   **solo nombres de tabla** (un `diff` contra una lista fija), sin
   ninguna aserción sobre columnas ni tipos; y la consulta a
   `flyway_schema_history` se imprime en el log para inspección manual,
   pero no se compara contra nada ni hace fallar el job. **Corrección**:
   se reescribió la sección "Verificación" de `docs/db/README.md` para
   describir con exactitud lo que el job comprueba y lo que no.
7. **`docs/db/schema.sql` seguía siendo la fuente de
   `docker-compose.db.yml` (el entorno de evidencias de planes de consulta
   de la Entrega 4) sin ningún control automático que confirmara que
   equivale a las migraciones.** Al investigar este punto se encontró que
   ya no era solo un riesgo teórico: al eliminarse
   `InventarioSchemaInitializer.java` en la corrección del hallazgo (2),
   `schema.sql` se quedó sin `inventario.reserva_stock` ni
   `inventario.operacion_reserva` — esa clase las creaba por código y
   `schema.sql` nunca las tuvo. Es decir, `docker-compose.db.yml` pasó a
   producir 33 tablas de `inventario` mientras el sistema real (migrado)
   ya tenía 35 — una divergencia real, no hipotética, entre las dos
   definiciones del esquema. **Corrección**: se agregaron ambas tablas y
   su índice a `docs/db/schema.sql` (contenido idéntico al de la `V3` de
   `inventario-service`); se creó `docs/db/tablas-esperadas.txt` como
   fuente única de las 35 tablas esperadas; el job existente
   `db-migrations-clean-start` se modificó para leer esa lista en vez de
   tenerla incrustada; y se agregó un job nuevo,
   `schema-sql-equivalencia`, que levanta `docker-compose.db.yml` (solo
   `schema.sql`, sin microservicios ni Flyway) y compara sus tablas contra
   el mismo `docs/db/tablas-esperadas.txt`. Que ambos jobs lean el mismo
   archivo es lo que hace automática la detección de que las migraciones y
   `schema.sql` vuelvan a divergir.

**Verificación de ambas correcciones**: la nueva sección de
`docs/db/README.md` se verificó por lectura directa contra el job real de
`ci.yml` línea por línea. La corrección de `schema.sql` se probó contra
CockroachDB real, no solo por lectura: se reconstruyó
`docker-compose.db.yml` desde cero (`down -v` + `up -d --wait`), el log de
`tiendatech-crdb-init` muestra `schema.sql` aplicándose completo sin
errores (todas las `CREATE SCHEMA`/`CREATE TABLE`/`ALTER TABLE`/`CREATE
INDEX` en orden, terminando en `CONFIGURE ZONE 1`), y la consulta directa a
`information_schema.tables` devolvió exactamente las 35 tablas de
`docs/db/tablas-esperadas.txt` — mismo conjunto exacto, incluidas
`inventario.reserva_stock` e `inventario.operacion_reserva` — confirmando
que `docker-compose.db.yml` ya no diverge del sistema migrado. Falta correr
el job `schema-sql-equivalencia` dentro de un CI real (pendiente del PR)
para tener esa misma evidencia también en GitHub Actions, no solo local.

### Corrección del hallazgo (8): `docs/db/seed-inventario-stock.sql` sin invocar

8. **`docs/db/seed-inventario-stock.sql` no estaba referenciado desde
   ningún flujo, composición o script real** — quedaba huérfano dentro de
   `docs/db/`, sin que nada en el repositorio dijera cuándo aplicarlo.
   Verificado línea por línea contra el código real de
   `StockReservationService.reconcileOnce()`
   (`inventario-service/.../application/reservation/StockReservationService.java:48-50`):
   la primera sentencia de la reserva de stock es
   `SELECT stock FROM inventario.inventario_producto WHERE producto_id = ? ...`
   — no valida contra `productos.producto.stock`. Ninguno de los seeds de
   catálogo existentes (`seed-e2e.sql`, `seed-catalogo-sintetico.sql`,
   `product-management-reference.sql`) puebla esa tabla, y desde el punto 5
   (migraciones puras) ningún script ni `docker-compose.yml` lo hace
   tampoco: un clúster nuevo nunca queda con filas ahí. El flujo real que
   necesita este seed es el camino crítico autenticado del punto 14
   (`POST /api/carrito/{carritoId}/agregar`, ejercitado por
   `tests/load/critical_path_locustfile.py`), y su propio
   `INSTRUCCIONES.md` documentaba el prerrequisito de
   `seed-ecuador-mobile-checkout.sql` pero **no** el de
   `seed-inventario-stock.sql` — un vacío real en la documentación del
   único flujo que de verdad lo necesita, no solo un script sin usar.
   **Corrección**: se agregó `docs/db/seed-inventario-stock.sql` como paso
   obligatorio (no opcional) en
   `docs/evidencias/pruebas-carga-camino-critico/INSTRUCCIONES.md`, con el
   comando exacto y la razón verificada contra el código.

   **Verificación real de punta a punta** (no solo lectura de código): se
   reconstruyó el sistema completo desde cero (`docker compose down -v` +
   `up --build`) y, con `seed-e2e.sql` aplicado pero **sin**
   `seed-inventario-stock.sql`, un usuario nuevo real recibió `HTTP 409`
   ("Conflicto") al intentar `POST /api/carrito/{carritoId}/agregar` —
   confirmando el hallazgo contra el sistema real, no solo contra el
   comentario del script. Tras aplicar `seed-inventario-stock.sql`, la
   misma petición contra el mismo carrito devolvió `HTTP 200` con la
   reserva real: `{"accepted":true,"message":"Reserva reconciliada",
   "reservedQuantity":1,"availableStock":498,...}`, y una segunda petición
   decrementó el stock disponible correctamente (498 → 497), confirmando
   que la reserva de stock queda funcionando de extremo a extremo, no solo
   que deja de dar error. Script de prueba usado:
   `probar-item8-seed-inventario.ps1` (raíz del repo).

   **Nota honesta**: la corrida real ya documentada en
   `docs/evidencias/pruebas-carga-camino-critico.md` (209/209 peticiones de
   "agregar al carrito" exitosas, 0 fallos, confirmado en
   `tests/load/results/tiendatech-critical-path-20-users_stats.csv` y
   `..._failures.csv`) **no aplicó este seed** y aun así no encontró el
   problema — su entorno no era un clúster recién migrado desde cero, ya
   traía filas de pruebas manuales previas en la misma máquina. Esto no
   invalida el hallazgo: un clon nuevo del repositorio, o el entorno que
   usa `db-migrations-clean-start` en CI, sí arranca sin esas filas. Queda
   documentado así, sin inflar la evidencia existente ni fingir que la
   corrida ya cubrió este caso.

### Corrección del hallazgo (9): evidencia del run de CI citado en el cierre anterior

9. **El cierre anterior citaba el run de CI `35007492980` (commit
   `0cbd193`) como prueba de que `db-migrations-clean-start` pasaba, y el
   evaluador reportó no poder leer su registro.** Verificado de forma
   independiente en esta ronda: el run existe, es público (el repositorio
   de GitHub es público, confirmado con el equipo), y está accesible sin
   necesitar ser colaborador — `https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35007492980`
   muestra el run #284 ("recompilar manuscrito con cifras de cobertura
   corregidas"), disparado por el push de `0cbd193` a `main`, estado
   `Success`, duración `5m 43s`, con el job **"Esquema desde cero solo con
   migraciones (Punto 5)"** en verde junto a los demás 12 jobs del
   pipeline. No se pudo determinar por qué el evaluador no logró leerlo en
   su momento (pudo ser un problema transitorio de su lado, o que lo
   intentó antes de que el run terminara) — lo que sí queda confirmado es
   que la evidencia citada era real y no estaba inventada.

   **Pero hay un problema más importante que la sola accesibilidad**: ese
   run es del 15 de septiembre, y para hoy (18 de septiembre) el propio
   punto 5 acumuló ocho correcciones más en esta misma ronda de cierre
   (items 1-8 de esta sección) que ese run nunca vio — no existían
   todavía. Seguir citando `35007492980` como "la prueba" del estado
   actual del punto 5 sería engañoso, así el run sea genuino: ya no
   representa el código real del repositorio. **Corrección**: no se puede
   fabricar un run nuevo sin pushear los cambios (fuera del alcance de
   este documento, que solo trabaja en local hasta que el usuario decida
   pushear); queda como pendiente explícito para el cierre real del punto
   5 correr un PR con todas las correcciones de items 1-8, esperar CI en
   verde de ese PR, y citar **ese** run — no `35007492980` — como la
   evidencia final. Ver "Pendiente" más abajo.

### Lo que sigue sin resolver de la lista completa del evaluador

Con esto, los nueve hallazgos de la segunda revisión del evaluador quedan
corregidos y verificados. Lo único que falta para el cierre real y
definitivo del punto 5 no es un hallazgo nuevo, sino el paso final
pendiente en todo momento: abrir el PR con los cambios de items 1-8,
obtener revisión y CI en verde, fusionar, y actualizar este documento con
esa evidencia — reemplazando toda referencia a runs anteriores a esta
ronda (`b2a6942`, `35007492980`) por el run real y actual del PR fusionado.

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

Una segunda revisión, más profunda, encontró ocho problemas más: que esa
primera corrección seguía sin ser fiel al monolito original en la
fragmentación física de `pedidos`; que dos tablas de `inventario` se
seguían creando por fuera de Flyway; que un comentario citaba una ruta de
documentación inexistente; que `usuarios` seguía con
`baseline-on-migrate=true` activo pudiendo ocultar deriva real; que
`docs/db/README.md` afirmaba falsamente que el job de CI verificaba
columnas y versiones de Flyway cuando solo compara nombres de tabla; que
`docs/db/schema.sql` había quedado genuinamente desactualizado (perdió dos
tablas reales al eliminarse el código que las creaba) sin ningún control
automático que lo detectara; que el seed de stock de `inventario` no
estaba conectado al único flujo real que lo necesita; y que el run de CI
citado como evidencia en el cierre anterior, aunque real y público, ya
había quedado desactualizado frente al propio código que debía probar. Los
ocho quedaron corregidos y verificados con evidencia real (contra
CockroachDB donde aplicaba, por lectura directa del código, contra el
sistema real de punta a punta, y de los resultados de carga existentes
donde no). No queda ningún hallazgo del evaluador sin abordar en esta
ronda; lo único pendiente es el paso final de siempre — abrir el PR,
obtener CI en verde y fusionar — para que la evidencia de CI del punto 5
deje de depender de un run anterior a estas correcciones.
