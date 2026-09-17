# Evidencia del panel de Grafana con datos reales de carga (Paso 10, ítem 5)

- Fecha: 2026-09-04
- Objetivo: cerrar el requisito del ítem 5 (panel versionado como código en
  `observability/`, no una captura suelta, con datos reales de una prueba de
  carga) usando el dashboard ya versionado en
  `ops/observability/grafana-dashboard.json` (Andy, commit `72cbf81`).
- Máquina: equipo local de Jhinson Aucatoma (Windows, Docker Desktop).

## Qué se agregó

No existía ningún contenedor Grafana en `docker-compose.yml`: el panel-as-code
existía pero nunca se había renderizado con datos reales. Se agregó el
servicio `tiendatech-grafana` (imagen `grafana/grafana:11.3.1`, puerto
`127.0.0.1:3000`) con **provisioning por archivo**, no importación manual:

- `ops/observability/grafana/provisioning/datasources/datasource.yml`:
  datasource de Prometheus apuntando a `http://tiendatech-prometheus:9090`.
- `ops/observability/grafana/provisioning/dashboards/dashboards.yml`:
  proveedor de tipo `file` que carga cualquier `.json` desde
  `/etc/grafana/dashboards` (montado directo desde `ops/observability/`, que
  ya contiene `grafana-dashboard.json`).

Variables nuevas documentadas en `.env.example`: `GRAFANA_ADMIN_USER`,
`GRAFANA_ADMIN_PASSWORD` (default `admin`/`admin`, solo desarrollo).

**Nota de diagnóstico:** el primer intento de montaje falló
(`docker compose up -d tiendatech-grafana`) con
`mkdirat .../dashboards/json: read-only file system`, porque se intentaba
montar `grafana-dashboard.json` como archivo suelto *dentro* de una carpeta
que ya era, en sí misma, otro bind mount de solo lectura (`provisioning/`
completo). Docker no puede crear un punto de montaje dentro de un mount ajeno
de solo lectura. Se corrigió separando los tres volúmenes
(`datasources/`, `dashboards/` y `ops/observability/` completo) como montajes
independientes, ninguno anidado dentro de otro.

Al levantar el contenedor, el dashboard **"TiendaTech - Observabilidad
distribuida"** apareció solo en `Dashboards`, sin importación manual desde la
UI — confirma que el provisioning funciona.

## Prueba de carga real ejecutada

Se usó la prueba de carga ya definida por el equipo en `tests/load/`
(Locust, 50 usuarios, rampa de 5/s, 60s), contra rutas públicas de solo
lectura vía el Gateway (`/api/productos`, `/api/categorias`, `/api/marcas`,
`/api/provincias` — no toca el checkout, que en el frontend sigue
deshabilitado):

```powershell
python -m pip install -r tests/load/requirements.txt
./tests/load/run-load-test.ps1
```

Resultado real (copiado en esta misma carpeta el 2026-09-04 porque
`tests/load/results/` está en `.gitignore`; el archivo con este mismo
nombre que hoy vive en `tests/load/results/` corresponde a una corrida muy
posterior y distinta — ver la nota sobre nombres reutilizados en
`tests/load/README.md` — la copia válida para este cierre es únicamente la
de esta carpeta):

| Endpoint | Peticiones | Fallos | Mediana | P95 | Máx |
|---|---|---|---|---|---|
| GET /api/categorias | 435 | 381 | 4 ms | 1900 ms | 4124 ms |
| GET /api/marcas | 435 | 376 | 5 ms | 1900 ms | 4774 ms |
| GET /api/productos | 1105 | 959 | 4 ms | 1800 ms | 4157 ms |
| GET /api/provincias | 219 | 178 | 4 ms | 2000 ms | 6006 ms |
| **Agregado** | **2194** | **1894 (86.3%)** | **4 ms** | **1900 ms** | **6006 ms** |

## El 86% de fallos no es un error del sistema: es el rate limiter del Gateway funcionando

Los 1894 fallos son, sin excepción, `HTTPError('429 Client Error')`
(`tests/load/results/tiendatech-50-users_failures.csv` y
`_exceptions.csv`, copiados en esta carpeta) — ninguno es un 5xx ni un error
de conexión.

Se verificó en el código fuente real
(`Apps/web/frontend/src/main/java/com/tiendatech/frontend/security/GatewayTrafficFilter.java`,
líneas 41–64): el Gateway limita a `GATEWAY_RATE_LIMIT_REQUESTS` peticiones
(default 300) por `GATEWAY_RATE_LIMIT_WINDOW_SECONDS` (default 60s), **por
IP de origen** (`request.getRemoteAddr()`, nunca `X-Forwarded-For`, según el
propio comentario del código: "nunca confía en X-Forwarded-For enviado por
el cliente"). Los 50 usuarios simulados de Locust corren todos desde la
misma máquina, es decir comparten una sola IP de origen: agotan las 300
peticiones permitidas casi de inmediato y el resto de la ventana de 60s
recibe 429 hasta que la ventana se reinicia. Es el comportamiento esperado
de un limitador por IP bajo una prueba de carga lanzada desde un solo host,
no una falla del Gateway ni de los microservicios.

Esto también explica por qué los paneles **"Tasa de errores HTTP 4xx/5xx"**
de Grafana se mantuvieron en 0 durante toda la prueba (ver captura): esos
429 los responde el propio Gateway *antes* de reenviar la petición a
`tiendatech-productos`/`usuarios`, así que nunca llegan a incrementar
`request_count_total` en los microservicios Java que Prometheus scrapea —
el Gateway mismo no está entre los targets de `ops/observability/prometheus.yml`.

Nota aparte, ya documentada en el propio código
(`GatewayTrafficFilter`, comentario de clase): "Protección local por ventana
fija. Varias réplicas requieren un limitador compartido" — el contador vive
en memoria del proceso (`HashMap` local), así que con más de una réplica del
Gateway cada instancia tendría su propio conteo independiente. No es un
hallazgo nuevo de esta prueba, pero la prueba lo hace visible con datos
reales.

## Captura del panel (datos reales, no simulados)

![Dashboard Grafana con datos reales de la prueba de carga](./paso10-item5-grafana-carga/dashboard-carga.png)

Tomada el 2026-09-04 ~03:05 (rango `Last 15 minutes`), justo después de la
corrida de Locust. Se observa el pico real en "Solicitudes por segundo" y
"Latencia P50/P95/P99" coincidiendo con la ventana de la prueba, y el pico en
"Conexiones activas" (~03:04). El pico de latencia permanece visible varios
minutos después de terminada la prueba porque las consultas usan
`rate(...[5m])`: la ventana móvil de 5 minutos sigue incluyendo las
mediciones elevadas hasta que la prueba sale de ese rango, no porque la
latencia real se haya mantenido alta después del corte.

## Conclusión

El ítem 5 del Paso 10 queda cerrado: el panel vive como código
(`ops/observability/grafana-dashboard.json`, ya commiteado por Andy) y ahora
también se renderiza en un Grafana local cuyo datasource y dashboard se
cargan por provisioning, sin pasos manuales. La captura adjunta corresponde
a una corrida real de la prueba de carga oficial del equipo, con sus
métricas crudas conservadas en esta misma carpeta.

## Pendiente

1. ~~El servicio `tiendatech-grafana` y su provisioning viven sin commitear~~
   -- resuelto: quedaron commiteados y pusheados a `main` el 2026-09-04
   (commit `b690470`, junto con el resto de Paso 10 y el trabajo de Jeremy).
2. El rate limiter por IP del Gateway es de un solo proceso (sin estado
   compartido entre réplicas). No bloquea el cierre del Paso 10, pero es
   relevante si el equipo llega a escalar el Gateway a más de una instancia.
3. ~~La captura de este documento no es de la misma sesión de carga que la
   traza distribuida del ítem 6~~ -- resuelto: se repitió la corrida el
   2026-09-04 entre 14:09:00 y 14:10:03 (hora local), con
   `tests/load/run-load-test.ps1` corriendo en paralelo a tres invocaciones
   de `capturar-trace-agregar.ps1` (con `GATEWAY_RATE_LIMIT_REQUESTS` subido
   temporalmente para esta corrida puntual; no reemplaza la prueba de
   rate-limit ya documentada más arriba, que se mantiene con sus propios
   resultados). El panel capturado con rango absoluto 14:09:00-14:12:00
   (`dashboard-carga-conjunta.png`, esta misma carpeta) y las tres trazas
   exportadas en la misma ventana
   (`../paso10-item6-trace-distribuida/4-agregar-sesion-conjunta-con-carga.json`,
   las tres con `tiendatech-inventario` presente) quedan, ahora sí, de la
   misma sesión. Métricas crudas de esta corrida en `stats-conjunta.csv`.
4. Esta misma corrida registró 6 fallos `503 Server Error` (4 en
   `GET /api/categorias`, 2 en `GET /api/marcas`; ver `stats-conjunta.csv`),
   distintos del rate-limit ya documentado -- con el límite elevado, esta
   corrida tuvo 0 fallos `429`. No se investigó la causa de los `503`; queda
   registrado como hallazgo nuevo, no bloquea el cierre de este punto.
