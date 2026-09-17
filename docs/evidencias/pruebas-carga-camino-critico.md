# Cierre — Punto 14 (Pruebas de carga: camino crítico autenticado)

- Fecha: 2026-09-15
- Objetivo: extender las pruebas de carga existentes (que solo cubrían
  lecturas públicas, sin autenticación) a un **camino crítico autenticado
  de punta a punta**: registro → login → creación de dirección y método de
  pago → carrito → checkout → factura → armado, incluyendo renovación real
  de sesión (`POST /auth/refresh`) bajo carga — en varios niveles de
  concurrencia, con resultados versionados y verificados por checksum.
- Aporte inicial: paquete de un integrante del equipo
  (`tests/load/critical_path_locustfile.py`, modificaciones a
  `run-load-test.ps1`, e `INSTRUCCIONES.md` con el diseño del escenario),
  con el diseño ya verificado línea por línea contra los controladores y
  DTOs reales, pero **sin ninguna corrida en vivo** — el entorno de quien lo
  preparó quedó bloqueado por una falla intermitente de Docker
  Desktop/red local al levantar el stack (`Connect timed out` de Flyway
  contra CockroachDB en el arranque de 6 servicios), no atribuible al
  código ni a CockroachDB. Ese `INSTRUCCIONES.md` original queda preservado
  en [`pruebas-carga-camino-critico/INSTRUCCIONES.md`](./pruebas-carga-camino-critico/INSTRUCCIONES.md)
  como registro de ese intento.
- Máquina: equipo local de Jhinson Aucatoma (Windows, Docker Desktop).
- Herramienta: Locust, vía `tests/load/run-load-test.ps1 -LocustFile
  critical_path_locustfile.py`.

## Qué se hizo

Se tomó el escenario ya diseñado y se completó lo que faltaba para
convertirlo en evidencia real:

1. Se aplicó el prerrequisito de datos (`docs/db/seed-ecuador-mobile-checkout.sql`
   contra la base `tiendatech`, no `defaultdb`) para poblar el catálogo de
   provincias/ciudades y tipos de método de pago que el camino crítico
   necesita para crear direcciones y métodos de pago reales.
2. Se subió el límite del rate limiter del Gateway
   (`GATEWAY_RATE_LIMIT_REQUESTS=20000`, mismo mecanismo documentado en
   `punto17-observabilidad-gateway.md` y en
   `experiments/paso8/run_real_experiment.py`) porque todos los usuarios
   virtuales de Locust comparten una sola IP (`localhost`), así que la
   cuota de 300 req/60s por defecto se agota entre todos ellos en segundos
   y produce `429` que no tienen nada que ver con el comportamiento real
   del sistema bajo carga.
3. Se corrió la campaña en tres niveles de concurrencia (5, 10 y 20 usuarios
   virtuales, 90s cada una, `SpawnRate` proporcional), deliberadamente más
   bajos que el escenario público de solo lectura: cada usuario virtual del
   camino crítico hace ~9 peticiones HTTP secuenciales con escrituras reales
   en 4 microservicios distintos por iteración (no es comparable en volumen
   a las 4 lecturas públicas del escenario original de 50 usuarios).
4. Se regeneró el manifiesto de checksums
   (`experiments/paso8/campaign_checksums.py --directory tests/load/results
   --write`) tras cada corrida.

## Dos hallazgos reales encontrados y corregidos durante las corridas

Ninguno de los dos apareció en la revisión de diseño del escenario —ambos
salieron a la luz solo al ejecutarlo contra el backend real bajo carga—:

### 1. Tokens de acceso inválidos (`username=null`) emitidos por `/auth/refresh`

`RefreshTokenService.rotateSession()` generaba el nuevo access token con
`username=null`, porque el refresh JWT nunca trae ese claim (solo
`subject`/`role`/`family_id`/`jti`) y nadie iba a buscarlo. `JwtUtil.parseAccess`
exige `username` no nulo para aceptar un token como access token — con
`username=null`, **todo** access token emitido por `/auth/refresh` o
`/auth/keepalive` nacía estructuralmente inválido: no expiraba antes de
tiempo, nacía roto, y la siguiente petición autenticada fallaba con `401`
("JWT invalido o expirado") en cualquier servicio, sin importar qué tan
reciente fuera el refresh.

Confirmado end-to-end con el propio load test: en cuanto el escenario
empezaba a forzar renovaciones (`POST /auth/refresh` cada 3 iteraciones, tal
como está diseñado el `critical_path_locustfile.py` para ejercitar de
verdad el camino de renovación), el checkout empezaba a fallar en cascada
para esos usuarios virtuales.

**Corrección**: `rotateSession()` ahora busca el `username` real del
usuario (`usuarios.findById(userId).map(Usuario::getUsuario)`) antes de
generar el nuevo access token, igual que hace `issueOnLogin()` en el login
inicial.

### 2. `SQLSTATE 40001` (conflicto de serialización de CockroachDB) en `/auth/refresh` bajo 20 usuarios concurrentes

`RefreshTokenService.refresh()` hace una lectura y dos escrituras (revocar
la sesión vieja + crear la nueva) dentro de una misma transacción contra
`refresh_sessions`. Bajo aislamiento serializable, CockroachDB aborta una
de dos transacciones concurrentes que chocan sobre las mismas filas y exige
que el cliente reintente la transacción completa
(`TransactionRetryWithProtoRefreshError` / `ABORT_REASON_NEW_LEASE_PREVENTS_TXN`,
`SQLSTATE 40001`) — no es un bug de la aplicación, es el contrato normal de
una base de datos distribuida con control de concurrencia optimista.

Con 5 y 10 usuarios concurrentes nunca se vio (poco choque de
transacciones); con 20 usuarios apareció en **4 de 2209 peticiones (0.18%)**,
todas `POST /auth/refresh` con `HTTP 500`.

Este mismo patrón ya existía en `pedidos-service` (`CrdbRetryExecutor`,
para el checkout) y en `inventario-service` (`CrdbTransactionRetryExecutor`,
para reservas) — `usuarios-service` era el único servicio con una
transacción multi-escritura sin ese tratamiento.

**Corrección**: se replicó el diseño de `inventario-service` en
`usuarios-service` — nuevo `CrdbTransactionRetryExecutor` (Spring
`TransactionTemplate` envolviendo la operación completa, backoff
exponencial con jitter, `crdb.retry.max-attempts/initial-delay-ms/max-delay-ms`
configurables, mismos valores por defecto que `inventario-service` para
consistencia entre servicios). `RefreshTokenService.rotateSession()` dejó
de llevar `@Transactional` propio (hubiera sido ignorado de todos modos por
auto-invocación dentro de la misma clase) y ahora se invoca como
`crdbRetry.execute(() -> rotateSession(...))`, de modo que un `40001`
reintenta la transacción completa —lectura + ambas escrituras— en vez de
propagarse como `500` al cliente.

Cobertura nueva: `CrdbTransactionRetryExecutorTest` (reintenta ante
`40001`, no reintenta otros `SQLSTATE`, se rinde al agotar los intentos) y
ajuste de `RefreshTokenServiceTest` para mockear el nuevo colaborador como
passthrough.

## Otros hallazgos reales de esta misma campaña (documentados previamente, no repetidos aquí)

Durante la preparación del entorno para esta campaña también se
encontraron y corrigieron, y quedan documentados con su propio detalle en
los comentarios del código y de `application.properties` de
`usuarios-service`:

- **Bug del cookiejar de Python**: con `Domain=localhost` en la cookie
  `refresh`, el navegador y `curl` la reenvían sin problema, pero el
  cookiejar estándar de Python (`http.cookiejar`, usado por
  `requests`/Locust) la descarta en silencio porque su política exige un
  punto en el `Domain` (o el literal `.local`). Con `Domain` vacío la
  cookie queda "host-only" (RFC 6265 lo permite) y esa política de Python
  sí la acepta — `auth.cookie.domain` quedó parametrizable
  (`AUTH_COOKIE_DOMAIN`, vacío por defecto) por si un despliegue real
  necesita fijar un dominio con punto.
- **Crash-loop de Flyway en `usuarios-service`**: el esquema `usuarios`
  tenía sus tablas creadas pero sin `flyway_schema_history`, y Flyway se
  negaba a arrancar ("Found non-empty schema(s) but no schema history
  table"). Corregido con `baselineOnMigrate=true` +
  `baselineVersion=1` (la única migración existente).

## Resultados por nivel de concurrencia (corrida del 15/09, antes de la corrección de abajo)

Los tres niveles quedaron con **0 fallos** en el estado versionado en su
momento:

| Usuarios concurrentes | Peticiones totales | Fallos | RPS | Mediana | p95 | Máximo |
|---|---|---|---|---|---|---|
| 5  | 625  | 0 | 7.1  | 96ms  | 1300ms | 3.56s |
| 10 | 1208 | 0 | 13.9 | 120ms | 1200ms | 4.04s |
| 20 | 2041 | 0 | 23.0 | 140ms | 2300ms | 4.34s |

El nivel de 20 usuarios reprodujo en su momento el `SQLSTATE 40001` descrito
arriba (fallos observados en consola durante la corrida previa a la
corrección de `CrdbTransactionRetryExecutor`, no preservados en ningún
archivo porque esa corrida se sobrescribió al aplicar el fix y volver a
correr) y, tras el fix, la corrida final sí quedó versionada con 0 fallos.
**Esta tabla y estos archivos ya no son la evidencia vigente de este
cierre** — ver la corrección de abajo: la campaña se repitió por completo
el 17/09 porque estos tres archivos tenían dos problemas de fondo no
detectados en su momento.

## Corrección (17 de septiembre de 2026): el asistente de armado nunca se ejecutaba, abandonos sin marcar, y crudos alterados a posteriori

La revisión externa del docente encontró tres problemas reales en el cierre
anterior, ninguno relacionado con los dos bugs de `usuarios-service` de
arriba (esos siguen corregidos y se reverifican más abajo):

**1. El asistente de armado nunca se ejercitó.** Ninguna de las tres
corridas de la tabla anterior tiene una sola fila
`POST /api/armado/analizar` — 0 peticiones en los tres niveles, pese a que
`_analizar_armado()` es parte del flujo que la evidencia afirmaba cubrir.
Causa raíz: `_CatalogoCompartido.cpu_producto_id()`
(`critical_path_locustfile.py`) buscaba una categoría llamada
`"procesador"`, pero la semilla real la llama `'CPU'`
(`docs/db/product-management-reference.sql:9`, `seed-e2e.sql:23`). El
lookup nunca encontraba nada, `cpu_producto_id` quedaba siempre en `None`,
y `_analizar_armado()` se saltaba en silencio en las tres corridas.
**Corrección**: comparar contra `"cpu"` en vez de `"procesador"`.

**2. Abandonos de iteración sin marcar como fallo.** `flujo_completo()`
hacía `return` en silencio (ni éxito ni fallo registrado) cuando el
registro/login de `on_start` no se completaba, o cuando el usuario virtual
no tenía dirección/método de pago propios — un hueco invisible en la tasa
de error real de la campaña. **Corrección**: se agregó
`_registrar_abandono()`, que dispara el mismo evento interno que usa
Locust para cada petición HTTP real (`environment.events.request.fire(...)`
con una excepción como marca de fallo), para que estos abandonos aparezcan
en `_stats.csv`/`_failures.csv` en vez de desaparecer sin dejar rastro.
Verificado en la práctica: con el stack de Docker apagado por error durante
una prueba de esta corrección, los 5 usuarios virtuales efectivamente
abandonaron su primera iteración (registro con `HTTP 0`, conexión
rechazada) y el reporte de errores de Locust mostró explícitamente `215
occurrences TASK flujo_completo: RuntimeError('registro/login de on_start
no se completo...')` — el hueco que antes no se veía ahora sí se ve.

**3. Crudos alterados después de la corrida, en dos frentes distintos**
(hallazgo compartido con #22/#42/#46, tratado aquí solo en lo que toca a
los archivos de este punto):

- El commit `62d37e4` ("eliminar CR sueltos") reescribió los CSV ya
  versionados de la campaña conservada del 04/09
  (`tiendatech-50-users-20260904-local_*.csv`), agregando BOM UTF-8 a cada
  uno y regenerando `checksums.txt` contra esos bytes alterados en vez de
  los que Locust escribió originalmente. **Corrección**: restaurados los
  cuatro archivos con `git checkout 822399a -- <archivo>` (el commit previo
  a la alteración) y regenerado el manifiesto contra los bytes reales. El
  único cambio real en cada archivo es la línea de encabezado (se quita el
  BOM; en `_stats.csv` de paso se corrigió un `90` que en la versión
  alterada había perdido su `%`) — todas las filas de datos quedaron
  intactas.
- El nombre `tiendatech-50-users_*.csv` en `tests/load/results/` (genérico,
  el que escribe `run-load-test.ps1` por defecto) había sido pisado por una
  tercera corrida sin relación con ningún cierre documentado (15/09 13:49
  UTC, concurrencia real de 10 usuarios, 790 peticiones, 253 fallos `429`),
  distinta tanto de la campaña de `paso10-item5-grafana-carga.md` (04/09,
  2194 peticiones) como de la de `punto17-observabilidad-gateway.md` (15/09,
  400 usuarios reales, 28,787 peticiones — cifra corregida ahí mismo, el
  documento tenía un desfase de 16 frente al CSV real). **Corrección**:
  renombrado a `tiendatech-ratelimit-check-10usuarios-20260915_*` para que
  el nombre ya no compita con las dos campañas documentadas, cuyas copias
  de evidencia sobreviven intactas en sus propias carpetas
  (`docs/evidencias/paso10-item5-grafana-carga/`,
  `docs/evidencias/punto17-observabilidad-gateway/`). Se documentó la regla
  a futuro en `tests/load/README.md`: nunca dejar `-OutputPrefix` en su
  valor por defecto para una corrida que se vaya a citar como evidencia.

## Resultados por nivel de concurrencia (corrida real del 17/09, con el fix aplicado)

Stack levantado localmente (Docker Desktop), semillas ya aplicadas
(`seed-ecuador-mobile-checkout.sql`, `seed-inventario-stock.sql`,
`product-management-reference.sql` + `seed-e2e.sql`), `GATEWAY_RATE_LIMIT_REQUESTS=20000`.
Cifras tomadas de la fila `Aggregated` y la fila `POST /api/armado/analizar`
de cada `_stats.csv` versionado, no del panel en vivo de Locust: en la
corrida de 20 usuarios el panel mostró 1251 peticiones agregadas y 209 en
`armado/analizar` contra 1239 y 197 en el `_stats.csv` final (12 de
diferencia en ambos) — la misma carrera
Locust/gevent al cerrar ya documentada más abajo, sin relación con
TiendaTech):

| Usuarios concurrentes | Duración | Peticiones totales | Fallos | Peticiones `armado/analizar` | Fallos en `armado/analizar` |
|---|---|---|---|---|---|
| 5  | 88s | 326  | 0 | 50  | 0 |
| 10 | 91s | 766  | 0 | 125 | 0 |
| 20 | 91s | 1239 | 0 | 197 | 0 |

Las tres corridas también ejercitaron `POST /auth/refresh` (5/10/20
usuarios → 15/40/~65 renovaciones forzadas cada
`REFRESH_EVERY_N_ITERATIONS=3` iteraciones) sin ningún fallo, confirmando
que las dos correcciones de `usuarios-service` de la sección anterior
(`username=null` en tokens renovados, `SQLSTATE 40001` sin reintento) siguen
funcionando bajo esta nueva corrida. `_failures.csv` y `_exceptions.csv`
quedaron vacíos (solo encabezado) en los tres niveles. Archivos versionados
en `tests/load/results/` (`tiendatech-critical-path-{5,10,20}-users_stats.csv`,
`_failures.csv`, `_exceptions.csv`, `_stats_history.csv`) y verificados en
`checksums.txt` — el `.html` de cada nivel existe localmente pero **no**
está versionado ni sometido a checksum (`.gitignore`), y no debe citarse
como "verificado en checksums.txt".

Nota aparte, sin relación con TiendaTech: en las corridas de 10 y 20
usuarios apareció el mismo traceback ya documentado en la corrida anterior
(`ValueError: I/O operation on closed file` en `locust/stats.py`), la
carrera interna conocida de Locust/gevent entre el hilo que escribe el CSV
y el cierre del proceso, más frecuente en Windows. No afecta los resultados
versionados: `exit code` fue `0` en los tres niveles y
`campaign_checksums.py` verificó los CSV sin problema después.

## Conclusión

El camino crítico autenticado completo —registro, login, dirección propia,
método de pago propio, carrito, checkout, factura, asistente de armado, y
renovación de sesión bajo carga— queda ejercitado de verdad en tres niveles
de concurrencia, con las cinco operaciones exigidas por la guía presentes
en el mismo `_stats.csv` (incluyendo `armado/analizar`, ausente en el
cierre anterior), sin abandonos invisibles (el mecanismo que los detecta
fue verificado en la práctica), y con evidencia versionada cuyos checksums
reflejan exactamente los bytes que escribió Locust, no una versión
"limpiada" después. El ejercicio combinado (esta corrección más el cierre
original) encontró y corrigió cuatro bugs reales: dos en `usuarios-service`
bajo carga (tokens de acceso inválidos tras renovar sesión, conflictos de
serialización de CockroachDB sin reintento) y dos en el propio guion de
carga (categoría de armado mal referenciada, abandonos de iteración sin
marcar), más la restauración de integridad de los crudos de una campaña de
otro punto que había sido alterada por error.

## Pendiente

- El paquete de aporte inicial (`INSTRUCCIONES.md`) documenta un fallo de
  arranque intermitente (`Connect timed out` de Flyway contra CockroachDB)
  atribuido a Docker Desktop/red local en esa máquina específica; en la
  máquina donde sí se corrió esta campaña no se reprodujo. Queda como nota
  para el equipo si alguien más lo encuentra en su propio entorno.
- No se repitió el escenario público de solo lectura (`locustfile.py`
  original, sin autenticación) en los mismos tres niveles de concurrencia
  — este cierre se enfocó específicamente en el camino crítico autenticado,
  que era el hallazgo pendiente. El escenario de 50 usuarios de solo
  lectura ya tiene su propia evidencia en `paso10-item5-grafana-carga.md`
  y `punto17-observabilidad-gateway.md`.
- La alteración de crudos por el commit `62d37e4` puede tener alcance más
  amplio que los archivos tocados en esta corrección (ver #22/#42/#46) —
  no se auditó aquí ningún archivo fuera de `tests/load/results/`.
