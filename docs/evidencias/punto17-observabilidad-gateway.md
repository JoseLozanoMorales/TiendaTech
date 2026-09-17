# Cierre — Punto 17 (Observabilidad: métricas del Gateway)

- Fecha: 2026-09-15 (implementación) / 2026-09-17 (evidencia final de los 5 paneles)
- Objetivo: completar la instrumentación de observabilidad extendiéndola al
  API Gateway (`Apps/web/frontend`), que hasta ahora quedaba fuera del
  scraping de Prometheus — cerrando además el hallazgo ya documentado en
  [`paso10-item5-grafana-carga.md`](./paso10-item5-grafana-carga.md), donde
  los paneles de Grafana "Tasa de errores HTTP 4xx/5xx" se quedaban en 0
  durante toda una prueba de carga real porque el Gateway respondía los
  `429` del rate limiter *antes* de reenviar la petición a los
  microservicios: esos rechazos nunca llegaban a incrementar
  `request_count_total` en ningún servicio que Prometheus scrapeara.
- Aporte inicial: paquete de un integrante del equipo (checksums SHA-256
  verificados contra `SHA256SUMS.txt` antes de integrar), con dos bugs
  reales encontrados y corregidos durante la integración.
- Máquina: equipo local de Jhinson Aucatoma (Windows, Docker Desktop).

## Qué se implementó

- **`Apps/web/frontend/pom.xml`**: se agrega la dependencia
  `io.micrometer:micrometer-registry-prometheus`, igual que en los 6
  microservicios Java, para que el Gateway también exponga métricas en
  formato Prometheus.
- **`Apps/web/frontend/src/main/resources/application.yml`**: se expone
  `/metrics` (vía `management.endpoints.web.path-mapping.prometheus:
  metrics` con `base-path: /`) y se habilita `health` con detalle completo.
- **`Apps/web/frontend/.../security/GatewayTrafficFilter.java`**: el filtro
  que ya interceptaba toda petición (`@Order(HIGHEST_PRECEDENCE)`) para el
  rate limiting ahora también registra tres métricas por petición, con el
  mismo formato y convención de nombres que el resto de servicios Java
  (verificado contra `HttpObservabilityFilter.java`, la implementación de
  referencia, y contra las consultas PromQL reales del propio
  `ops/observability/grafana-dashboard.json`):
  - `active_connections` (gauge, tag `service=tiendatech-gateway`)
  - `request_count_total` (counter, tags `service`, `method`, `route`, `status`)
  - `request_duration_seconds` (histograma con percentiles, mismos tags)

  **Corrección (punto 17 de la guía de cierre):** una versión anterior de
  este documento afirmaba "mismo formato" pero el `GatewayTrafficFilter`
  no emitía el tag `route` que sí lleva `HttpObservabilityFilter` — la
  frase de arriba no estaba verificada contra el código real, solo contra
  el nombre de las tres métricas. Corregido agregando `"route",
  request.getRequestURI()` al arreglo de tags, y agregada una aserción en
  `GatewayTrafficFilterTest` que verifica el tag `route` real (no solo que
  compile), para que ahora sí sea una afirmación comprobada.
- **`ops/observability/prometheus.yml`**: se agrega `tiendatech-gateway:8080`
  al job `tiendatech-java-services` ya existente.

## Dos bugs reales encontrados y corregidos en el camino

Ninguno de los dos fue detectado en la revisión de código inicial —ambos
salieron a la luz solo al intentar levantar y usar el sistema real—:

1. **Compilación rota: constructor eliminado por el patch.** El parche
   original quitaba el constructor público de 1 argumento
   `GatewayTrafficFilter(Environment)` para reemplazarlo por uno de 2
   argumentos con `MeterRegistry`. La revisión de código no lo detectó
   porque sí se verificó el constructor `(Environment, Clock)` usado en la
   mayoría de las pruebas — pero
   `GatewayTrafficFilterTest.rechazaConfiguracionInvalida()` llama
   específicamente al de 1 argumento, y `docker compose build
   tiendatech-gateway` falló en la fase de test con
   `no suitable constructor found for GatewayTrafficFilter(MockEnvironment)`.
   Corregido agregando de vuelta un constructor package-private de 1
   argumento que delega al mismo constructor base con `registry=null`;
   verificados los 6 sitios de invocación del constructor en el archivo de
   pruebas para confirmar que cada uno resuelve al overload correcto.
2. **Healthcheck del Gateway roto por el propio cambio de observabilidad.**
   Al fijar `management.endpoints.web.base-path: /` (para que `/metrics`
   quede sin el prefijo `/actuator/`), el endpoint de salud se mueve de
   `/actuator/health` a `/health` como efecto colateral — pero el
   `healthcheck` de `tiendatech-gateway` en `docker-compose.yml` seguía
   apuntando a la ruta vieja, dejando el contenedor permanentemente
   `unhealthy` aunque la aplicación arrancaba bien (confirmado en el log:
   cada intento de `curl` contra `/actuator/health` devolvía `404`).
   Corregido apuntando el healthcheck a `/health`, con un comentario en el
   propio `docker-compose.yml` explicando la causa.

## Verificación final

- **Build**: `docker compose build tiendatech-gateway` compila limpio tras
  el fix del constructor.
- **Contenedor**: `tiendatech-gateway` pasa a `healthy` tras el fix del
  healthcheck (`docker ps` confirmado).
- **Prometheus**: `Status > Target health` muestra `tiendatech-gateway:8080`
  `UP` dentro del job `tiendatech-java-services` (7/7 up), junto a los 6
  microservicios y `tiendatech-armado-ia`.
- **Prueba de carga real** contra el Gateway (`tests/load/run-load-test.ps1
  -HostUrl http://localhost:8180 -Users 400 -SpawnRate 50 -RunTime 90s`,
  deliberadamente muy por encima de los 300 req/60s del rate limiter del
  Gateway, para forzar `429` reales en vez de dejar el panel de errores
  vacío): **33,006 peticiones**, **3,812** con `429` (fila `Aggregated` de
  `tiendatech-400-users_stats.csv`), repartidas entre `GET /api/categorias`
  (774), `GET /api/marcas` (734), `GET /api/productos` (1,912) y
  `GET /api/provincias` (392); **0 excepciones internas** de Locust
  (`tiendatech-400-users_exceptions.csv` vacío). Copia versionada completa
  en `docs/evidencias/punto17-observabilidad-gateway/` con el nombre real
  del escenario (`tiendatech-400-users_stats.csv`, `_failures.csv`,
  `_exceptions.csv`, `_stats_history.csv`) — corrige el nombre engañoso
  `tiendatech-50-users_*` que citaba una versión anterior de este
  documento (el escenario siempre corrió con 400 usuarios, nunca con 50;
  los archivos originales con ese nombre quedan pendientes de archivar o
  borrar, ver Pendiente). `--csv-full-history` se agregó a
  `run-load-test.ps1` para que esta corrida sí incluya
  `_stats_history.csv` (73,802 bytes, serie temporal completa), algo que
  ninguna corrida anterior de este cierre tenía.
- **Captura real del dashboard**
  (`docs/evidencias/punto17-observabilidad-gateway/dashboard-gateway-carga.png`,
  de una corrida previa, `Last 15 minutes` tomado justo después de la
  corrida): se observa el pico real de ~380 req/s en "Solicitudes por
  segundo" coincidiendo con la ventana de la prueba, y —a diferencia de la
  captura de `paso10-item5-grafana-carga.md`— el panel **"Tasa de errores
  HTTP 4xx"** ya no se queda en 0/"No data". Esta captura por sí sola no
  cubre el panel 5xx (ver siguiente sección para la evidencia completa de
  los 5 paneles).

## Los cinco paneles con datos: generando un 5xx real

La observación del docente sobre este punto fue literal: los cinco paneles
del dashboard (`Solicitudes por segundo`, `Latencia P50/P95/P99`, `4xx`,
`5xx`, `Conexiones activas`) deben mostrar series con datos, no solo
"cobertura de instrumentación". Cerrar esto realmente tomó tres
correcciones encadenadas, cada una descubierta al intentar generar la
evidencia real (no en revisión de código):

**1) El panel 4xx parecía plano en cada intento anterior por un problema de
unidad del eje, no por falta de datos.** La consulta PromQL de los paneles
4xx/5xx devuelve una fracción entre 0 y 1 (`rate(...status=~"4.."...) /
rate(...total...)`), pero los paneles no tenían `fieldConfig.unit`
configurado — Grafana escalaba el eje Y por defecto a un rango donde
valores reales pero pequeños (máximo observado ~0.11) quedaban pegados
visualmente al cero. Esto explica por qué el panel 4xx aparecía "vacío" en
capturas anteriores de todo este proyecto (incluida la de
`paso10-item5-grafana-carga.md`) aun cuando el `429` real sí estaba
llegando al Gateway. Corregido agregando
`"fieldConfig": {"defaults": {"unit": "percentunit", "min": 0}}` a los
paneles 3 y 4 de `ops/observability/grafana-dashboard.json` (el dashboard
está provisionado como código —`allowUiUpdates: false`,
`updateIntervalSeconds: 30`— así que este archivo es la única fuente del
panel; no hay forma de que haya quedado un dashboard diferente editado a
mano en la UI).

**2) Un solo checkout con fallo inyectado no bastaba: la ventana deslizante
de `rate(...[5m])` diluye un evento aislado a casi cero.** El panel 5xx usa
una ventana de 5 minutos; un único `500`/`504` real generado y luego
capturado varios minutos después ya no aparecía en el `rate()`. Se necesitan
varios eventos repartidos dentro de la ventana de captura, no uno solo.

**3) Preparar las cuentas de prueba mientras la carga de 400 usuarios ya
está saturando el rate limiter provoca que la propia preparación (crear
cuenta, dirección, método de pago) choque contra el mismo límite que se
está forzando a propósito.** Se reestructuró `generar-5xx-real.ps1` (raíz
del repo) en dos fases separables: `-Fase Preparar` crea varias cuentas de
prueba completas (usuario, login, dirección, método de pago, producto en
carrito) **antes** de arrancar la carga, cuando el limiter está tranquilo;
`-Fase Disparar` solo dispara el checkout con `X-Failure-Mode: omission`
para cada cuenta ya preparada, y es la única parte que necesita solaparse
en el tiempo con la prueba de carga.

**4) Los CSV de Locust venían dañados desde el origen, no por una
reescritura posterior.** Revisión en detalle de los bytes del CSV
detectó `\r\r\n` (doble retorno de carro) en vez de `\r\n` al final de
cada línea — probable doble traducción: el escritor CSV propio de Locust
ya emite `\r\n`, y el archivo se abre además en modo texto de Windows,
que traduce `\n` a `\r\n` una segunda vez. `csv.reader` en modo universal
interpreta ese `\r\r\n` como una fila vacía después de cada fila real, dando
filas de longitud alternada (`[22,0,22,0,...]`). Confirmado que el archivo
ya nacía así en `tests/load/results/` en la máquina de Jhinson, antes de
cualquier copia o transferencia — no es un problema introducido al mover el
archivo. Corregido en dos frentes: se normalizaron los 4 `.csv` ya
generados (mismo contenido, solo el fin de línea) y se agregó a
`run-load-test.ps1` un paso que normaliza la salida de Locust a LF puro sin
BOM apenas termina la corrida, para que ninguna corrida futura vuelva a
quedar así.

Con esas cuatro correcciones, se usó el mecanismo de inyección de fallos
**ya existente y documentado en el propio código** del proyecto
(`ExperimentFaultInjector`, en `ventas-service`, activado vía el header
`X-Failure-Mode` y la variable `EXPERIMENT_FAULT_INJECTION_ENABLED`,
desactivada por defecto en todos los servicios). Revisando el código fuente
(`ExperimentFaultInjector.java`), el modo `omission` duerme 9 segundos y
luego lanza `new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, ...)`
— es decir, un **504** a nivel de código fuente, no un 500 codificado a
mano; corrige la afirmación de una versión anterior de este documento
("checkout devolvió un 500 genuino"), que no había leído el código del
inyector. Sea cual sea el código exacto que termina viendo cada llamador
según cómo se propague por la cadena de coordinación del checkout, cae
dentro del rango `5xx` (`status=~"5.."`) que mide el panel — es un fallo
real del sistema, generado por una ruta de código genuina pensada para
pruebas de resiliencia, no una simulación añadida para esta evidencia.

**Hallazgo aparte, real, encontrado al ejecutar este flujo por primera vez
contra un clúster migrado solo con Flyway (post punto 5):** ni el catálogo
geográfico (`usuarios.provincia`/`ciudad`), ni los tipos de método de pago
(`pedidos.tipo_metodopago`), ni el catálogo de productos
(`productos.producto`), ni el stock real de inventario
(`inventario.inventario_producto`) estaban poblados — ninguno de los
cuatro se carga por Flyway ni por ningún script que corra en
`docker-compose.yml`; existen como scripts SQL manuales en `docs/db/`
(`seed-ecuador-mobile-checkout.sql`, `product-management-reference.sql`,
`seed-catalogo-sintetico.sql`) más un `INSERT` puntual para la fila de
inventario del producto de prueba, sin ningún seed automático equivalente.
Antes del punto 5, `docs/db/schema.sql` se cargaba una sola vez y quedaba
implícito qué datos existían; con migraciones puras por servicio, un
clúster nuevo queda con el esquema correcto pero sin ningún dato de
catálogo, y nada en el repo lo avisa. Se corrieron los tres scripts de
seed existentes más el `INSERT` de inventario manualmente contra el
clúster local para destrabar la prueba; no se automatizó como parte de
este cierre (ver Pendiente).

Con las tres correcciones aplicadas (`percentunit`, fase Preparar/Disparar,
varias repeticiones dentro de la ventana de captura) se corrió
`-Fase Preparar -Repeticiones 5` antes de arrancar la carga de 400
usuarios, y luego, con la carga ya corriendo, `-Fase Disparar
-OmitirReinicio`: de las 5 cuentas preparadas, 4 dispararon un `5xx` real
del checkout con fallo inyectado y 1 chocó contra el rate limiter
(`429`) — ambos son resultados válidos para este experimento, ninguno es
un error del script.

**Capturas finales, verificadas por inspección directa de la imagen (no
solo por lo que dice este documento), rango `Last 15 minutes`, ventana
~11:37–11:51:**

- `dashboard-gateway-trafico-latencia.png`: `Solicitudes por segundo`
  muestra el pico real de ~400 req/s en doble ráfaga; `Latencia
  P50/P95/P99` muestra latencia elevada real hacia el final de la ventana
  (consistente con el rate limiter encolando/reintentando bajo saturación
  deliberada); `4xx` y `5xx` con series reales, no planas.
- `dashboard-gateway-5xx-real.png`: `4xx`, `5xx` y `Conexiones activas`
  juntos. **`5xx` muestra series reales subiendo a ~6–7% para
  `tiendatech-pedidos` y `tiendatech-ventas`** — los dos servicios que
  participan directamente en la coordinación del checkout con fallo
  inyectado. `tiendatech-gateway` se mantiene cerca de 0% en este panel:
  no porque no haya fallos pasando por él, sino porque su tráfico total
  (dominado por las ~33,000 peticiones GET públicas de la carga de 400
  usuarios) hace que los pocos `5xx` de checkout sean una fracción
  minúscula del total — a diferencia de pedidos/ventas, donde el
  denominador (solo tráfico de checkout) es mucho menor y el mismo puñado
  de fallos reales sí se nota como fracción. Esto corrige la afirmación de
  una versión anterior de este documento de que las tres series (gateway,
  pedidos, ventas) subían juntas a "~0.03" — no es así: cada servicio tiene
  una base de tráfico distinta y el gateway, al tener la mayor, diluye el
  mismo fallo real a un valor visualmente cercano a cero, lo cual es
  correcto y esperable, no un defecto. `Conexiones activas` muestra un
  pico real de ~60 para `tiendatech-gateway` alrededor de las 11:44:30,
  coincidiendo con la ventana de carga.

Entre las dos capturas quedan documentados, con datos reales y
simultáneos, los cinco paneles del dashboard.

## Conclusión

El Gateway queda instrumentado igual que el resto de los microservicios
Java: expone sus propias métricas, Prometheus lo scrapea, y sus respuestas
`429` del rate limiter —que antes eran invisibles para el dashboard— ahora
se reflejan con datos reales. Esto completa el hallazgo dejado pendiente en
`paso10-item5-grafana-carga.md` ("esto también explica por qué los paneles
Tasa de errores HTTP 4xx/5xx de Grafana se mantuvieron en 0"): la causa real
combinaba dos problemas —el `429` nunca llegaba a los microservicios
scrapeados, y por separado, el eje del panel 4xx no tenía la unidad
correcta para mostrar fracciones pequeñas— y ambos quedan corregidos y
documentados aquí. Cumpliendo la observación literal del docente, los
**cinco** paneles del dashboard muestran series con datos reales,
verificables en `dashboard-gateway-trafico-latencia.png` y
`dashboard-gateway-5xx-real.png`.

## Prueba de mutación de las métricas del Gateway

El docente señaló, aparte de los paneles, un hallazgo de mutation testing
sobre la propia instrumentación: renombrar la métrica del Gateway y fijar
el `status` a mano en `"200"` seguía dejando 24/24 pruebas en verde. La
causa: ninguna de las pruebas existentes de `GatewayTrafficFilterTest.java`
construye el filtro con un `MeterRegistry` real (todas usan el constructor
de 2 argumentos `(Environment, Clock)`, que deja `registry=null`), así que
el bloque que registra métricas en `doFilterInternal` nunca se ejercitaba.

Se agregó `registraMetricasPrometheusConNombreYStatusReales`, que usa un
`SimpleMeterRegistry` real y verifica tanto el nombre de la métrica
(`request_count`) como que el tag `status` refleje el código de respuesta
real (`401` en la prueba), no un valor fijo. Verificado con la misma
mutación exacta que describió el docente, aplicada a mano sobre
`GatewayTrafficFilter.java` línea 117 (`int status = failed ? 500 :
response.getStatus();` → `int status = 200;`):

- **Antes de la mutación**: `GatewayTrafficFilterTest` completo en verde
  (7 pruebas, incluida la nueva).
- **Con la mutación aplicada**: la nueva prueba falla exactamente donde
  debe (`Debe existir un contador request_count con status=401 real ==>
  expected: not <null>`), y además `registraSinQueryNiCredenciales` —una
  prueba ya existente que no se escribió pensando en esto— también falla
  como efecto colateral, porque el log usa la misma variable `status`
  mutada.
- **Tras revertir la mutación**: vuelve a verde.

Corrida real en IntelliJ (JUnit 6), no simulada.

## Pendiente

- El rate limiter del Gateway sigue siendo de un solo proceso, sin estado
  compartido entre réplicas (ya señalado en `paso10-item5-grafana-carga.md`,
  punto 2 de sus pendientes) — no bloquea este cierre, sigue siendo
  relevante si el equipo escala el Gateway a más de una instancia.
- No se investigó a fondo la causa exacta de la latencia máxima elevada
  observada en algunas peticiones durante el pico de 400 usuarios
  concurrentes — es consistente con la saturación deliberada del rate
  limiter (peticiones encoladas/reintentadas), no con una regresión de los
  microservicios, pero queda como posible hallazgo a revisar si el equipo
  hace una prueba de carga por debajo del límite del rate limiter (ver
  también `#14`, pruebas de carga, en curso por otro integrante).
- No se confirmó con una captura de red/log de bajo nivel qué código HTTP
  exacto ve el llamador final del checkout con fallo inyectado (el código
  fuente del inyector lanza `504`, pero la cadena de coordinación del
  checkout podría re-envolver ese error antes de que llegue al cliente).
  No cambia la validez de la evidencia del panel 5xx (que agrupa todo el
  rango `5xx`), pero queda como afirmación exacta sin verificar si se
  necesitara citar un código HTTP específico en el manuscrito.
- Los archivos `tiendatech-50-users_stats.csv`, `_failures.csv` y
  `_exceptions.csv` originales (evidencia de una corrida anterior del
  panel 4xx, con nombre engañoso — el escenario real siempre fue de 400
  usuarios) ya se movieron a
  `docs/evidencias/punto17-observabilidad-gateway/obsoletos-50-users/`
  como referencia histórica; la evidencia vigente de este cierre es
  exclusivamente la de `tiendatech-400-users_*`.
- `/metrics` y `/health` del Gateway quedan sin protección JWT (el
  `JwtGatewayFilter` solo protege `/api/**`, y `deploy/Caddyfile` reenvía
  todo el tráfico al Gateway, así que en un despliegue real quedarían
  expuestos) — pendiente de decidir si se protege o se justifica
  explícitamente, ambas alternativas aceptadas por la guía del docente.
- La corrida 5xx documentada arriba (4 reales de 5, 1 con `429`) todavía
  solo tiene como evidencia las capturas de Grafana — el docente señaló
  que "de la corrida 5xx no hay ningún dato crudo, solo el PNG".
  `generar-5xx-real.ps1` ya se corrigió para guardar un JSON con cada
  intento (`usuarioId`, código de respuesta real, `resultado`,
  duración) en `disparos-5xx-real.json`, pero ese archivo no existe
  todavía para la corrida ya documentada — falta decidir si se repite la
  corrida (ahora sí quedaría también con dato crudo) o si se acepta la
  captura de pantalla como única evidencia de esta corrida en particular.
