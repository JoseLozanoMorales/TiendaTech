# Cierre — Punto 17 (Observabilidad: métricas del Gateway)

- Fecha: 2026-09-15
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
  - `request_count_total` (counter, tags `service`, `method`, `status`)
  - `request_duration_seconds` (histograma con percentiles, mismos tags)
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
  vacío): **28,803 peticiones**, **28,203** con `429`
  (`tests/load/results` → `docs/evidencias/punto17-observabilidad-gateway/`,
  archivos `tiendatech-50-users_stats.csv`, `_failures.csv`,
  `_exceptions.csv`). Ningún fallo `5xx`.
- **Captura real del dashboard**
  (`docs/evidencias/punto17-observabilidad-gateway/dashboard-gateway-carga.png`,
  rango `Last 15 minutes` tomado justo después de la corrida): se observa el
  pico real de ~380 req/s en "Solicitudes por segundo" coincidiendo con la
  ventana de la prueba, y —a diferencia de la captura de
  `paso10-item5-grafana-carga.md`— el panel **"Tasa de errores HTTP 4xx"**
  ya no se queda en 0/"No data": pasa a una línea sostenida cercana a 1
  durante toda la ventana de sobrecarga, cayendo a 0 en cuanto termina. En
  esta corrida el panel "5xx" quedó en "No data" — el rate limiter protege
  devolviendo `429`, nunca `500`, así que no hay ningún 5xx real que este
  tráfico en particular pueda mostrar (ver siguiente sección).

## Los cinco paneles con datos: generando un 5xx real

La observación del docente sobre este punto fue literal: los cinco paneles
del dashboard (`Solicitudes por segundo`, `Latencia P50/P95/P99`, `4xx`,
`5xx`, `Conexiones activas`) deben mostrar series con datos, no solo
"cobertura de instrumentación". Con la corrida de carga anterior, 4 de 5
paneles ya tenían datos reales, pero **"Tasa de errores HTTP 5xx" seguía en
"No data"** — el rate limiter, funcionando correctamente, nunca deja pasar
tráfico suficiente para que un backend real falle con un `5xx` genuino.

En vez de fabricar un error falso, se usó un mecanismo de inyección de
fallos **ya existente y documentado en el propio código** del proyecto
(`ExperimentFaultInjector`, en `ventas-service`, activado vía el header
`X-Failure-Mode` y la variable `EXPERIMENT_FAULT_INJECTION_ENABLED`,
desactivada por defecto en todos los servicios): con el modo `omission`, el
checkout crea la orden normalmente pero, al facturarla contra
`ventas-service` como parte del flujo real de coordinación 2PC, ese
servicio duerme 9 segundos y lanza un error que se propaga como `5xx` real
hacia el llamador. Es una ruta de código genuina del sistema, pensada
exactamente para pruebas de resiliencia/observabilidad — no una
simulación añadida para esta evidencia.

Se escribió `generar-5xx-real.ps1` (raíz del repo) para automatizar el
flujo completo contra el sistema real: habilita la bandera solo para
`ventas-service`, registra una cuenta de prueba desde cero, crea una
dirección y un método de pago reales, agrega un producto real al carrito,
y hace el checkout con `X-Failure-Mode: omission`.

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

Con los catálogos poblados, `generar-5xx-real.ps1` completó el flujo
real y el checkout devolvió un **`500` genuino**. Captura final
(`docs/evidencias/punto17-observabilidad-gateway/dashboard-gateway-5xx-real.png`,
`Last 30 minutes`): los cinco paneles muestran series con datos, incluido
**"Tasa de errores HTTP 5xx"** con una serie real subiendo a ~0.03 para
`tiendatech-gateway`, `tiendatech-pedidos` y `tiendatech-ventas` —
exactamente el fallo real propagándose por los tres servicios que
participan en el checkout.

## Conclusión

El Gateway queda instrumentado igual que el resto de los microservicios
Java: expone sus propias métricas, Prometheus lo scrapea, y sus respuestas
`429` del rate limiter —que antes eran invisibles para el dashboard— ahora
se reflejan con datos reales. Esto completa el hallazgo dejado pendiente en
`paso10-item5-grafana-carga.md` ("esto también explica por qué los paneles
Tasa de errores HTTP 4xx/5xx de Grafana se mantuvieron en 0"): ya no es un
comportamiento invisible, es visible y medible. Y, cumpliendo la
observación literal del docente, los **cinco** paneles del dashboard
—no solo los tres que ya funcionaban antes— muestran series con datos
reales, verificables en `dashboard-gateway-5xx-real.png`.

## Pendiente

- El rate limiter del Gateway sigue siendo de un solo proceso, sin estado
  compartido entre réplicas (ya señalado en `paso10-item5-grafana-carga.md`,
  punto 2 de sus pendientes) — no bloquea este cierre, sigue siendo
  relevante si el equipo escala el Gateway a más de una instancia.
- No se investigó a fondo la causa exacta de la latencia máxima elevada
  (hasta ~13s) observada en algunas peticiones durante el pico de 400
  usuarios concurrentes — es consistente con la saturación deliberada del
  rate limiter (peticiones encoladas/reintentadas), no con una regresión de
  los microservicios, pero queda como posible hallazgo a revisar si el
  equipo hace una prueba de carga por debajo del límite del rate limiter
  (ver también `#14`, pruebas de carga, en curso por otro integrante).
