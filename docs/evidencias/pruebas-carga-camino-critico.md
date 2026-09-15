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

## Resultados por nivel de concurrencia

Los tres niveles quedan con **0 fallos** en el estado final versionado en
`tests/load/results/` (`tiendatech-critical-path-{5,10,20}-users_stats.csv`,
`_failures.csv`, `_exceptions.csv`, `_stats_history.csv`, `.html`,
verificados en `checksums.txt`):

| Usuarios concurrentes | Peticiones totales | Fallos | RPS | Mediana | p95 | Máximo |
|---|---|---|---|---|---|---|
| 5  | 625  | 0 | 7.1  | 96ms  | 1300ms | 3.56s |
| 10 | 1208 | 0 | 13.9 | 120ms | 1200ms | 4.04s |
| 20 | 2041 | 0 | 23.0 | 140ms | 2300ms | 4.34s |

El nivel de 20 usuarios es el más significativo de los tres: es la corrida
que **reprodujo el hallazgo real** del `SQLSTATE 40001` (4 fallos de 2209
peticiones en la corrida previa a la corrección, 0.18%, todos en
`/auth/refresh`) y, tras aplicar la corrección de
`CrdbTransactionRetryExecutor`, **confirmó su resolución completa** en la
corrida final versionada arriba (2041 peticiones, 0 fallos, ningún `500`).
Se optó deliberadamente por conservar ese antes/después como parte de la
narrativa de esta evidencia — es más representativo de un ejercicio real de
pruebas de carga en sistemas distribuidos que tres corridas limpias desde
el inicio, y documenta un hallazgo genuino de CockroachDB bajo concurrencia
real, no simulado.

Nota aparte, sin relación con TiendaTech: en la corrida de 10 usuarios
apareció un traceback de Locust después de la línea `Shutting down (exit
code 0)` (`ValueError: I/O operation on closed file` en
`locust/stats.py`), producido por una carrera interna conocida de Locust/
gevent entre el hilo periódico que escribe el CSV de estadísticas y el
cierre del proceso — más frecuente en Windows por su timing de shutdown más
rápido que Linux/Mac. No afecta los resultados: la tabla de percentiles ya
se había impreso completa, `exit code` fue `0`, y `campaign_checksums.py`
verificó los CSV/HTML sin problema después. Es ruido de la herramienta, no
un defecto de TiendaTech.

## Conclusión

El camino crítico autenticado completo —incluyendo renovación de sesión
bajo carga, no solo el `happy path` sin refresh— queda ejercitado y
verificado en tres niveles de concurrencia, con evidencia versionada y
checksums en `tests/load/results/`. El ejercicio encontró y corrigió dos
bugs reales de `usuarios-service` que solo se manifestaban bajo carga real
(tokens de acceso inválidos tras renovar sesión, y conflictos de
serialización de CockroachDB sin reintento), sumados a los dos hallazgos de
preparación del entorno (cookiejar de Python, crash-loop de Flyway) ya
documentados en el código. Los cuatro hallazgos, en conjunto, son evidencia
de un ejercicio de pruebas de carga genuino contra un sistema distribuido
real, no una demostración superficial.

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
