# Experimento real (Paso 8) contra los microservicios — guía de arranque

Complementa a `run_paso8.py` (simulación local en SQLite, ya corrida). Esta
versión pega contra el stack real vía el API Gateway, con Locust como
generador de carga, para validar los mismos 24 condiciones con datos de un
sistema real en vez de una simulación.

## Alcance de la campaña correctiva

- 24 condiciones = 2 estrategias (`COORD=2pc|saga`) x 4 concurrencias
  (50/100/200/400) x 3 modos de pasarela (`none`/`omission`/`timing`).
- 5 repeticiones por condición = **120 corridas**.
- Cada corrida: 60s de calentamiento descartado + **300s de medición** = 360s.
- Tiempo mínimo de carga: **12 horas** (120 × 360s), más reinicios,
  autenticación, pilotos y cambios de `COORD`.
- Antes de la matriz se ejecuta automáticamente un piloto de 120s con un
  usuario para cada estrategia. Debe producir al menos un checkout confirmado
  y cero errores; de lo contrario el orquestador se detiene.
- Después se ejecuta una rampa sin fallos de 1 → 5 → 10 → 25 → 50 usuarios
  para cada estrategia. Cada escalón debe confirmar compras con cero errores.
  Sus archivos quedan fuera de `experimento_real_crudo.csv`.

## Qué cambia realmente entre E-2PC y Saga

- **E-2PC (`COORD=2pc`)**: Pedidos exige que Ventas confirme la estrategia y
  Ventas mantiene una barrera síncrona; no responde al checkout hasta que
  Inventario acepta el movimiento. Después marca procesado el outbox.
- **Saga (`COORD=saga`)**: Ventas confirma su transacción local de
  factura/outbox y el descuento de Inventario se realiza asíncronamente desde
  el procesador del outbox.
- Ambas rutas usan `Idempotency-Key: factura-inventario-<facturaId>`, por lo
  que una carrera o un reintento no puede descontar dos veces el stock.

La etiqueta **E-2PC** significa *2PC experimental*: es una barrera síncrona de
coordinación entre servicios, no un XA/`PREPARE TRANSACTION` distribuido. Esta
precisión debe conservarse en el informe para no atribuirle garantías que la
implementación no ofrece.

## Orden de ejecución (fijo, no configurable desde CLI)

`repeticion` (1..5) > `coord` (2pc, saga) > `fallo` (none, omission, timing) >
`concurrencia` (50,100,200,400). Repetición es el nivel más externo a
propósito: si el proceso se corta a mitad, el resultado es N repeticiones
**completas** de las 24 condiciones (n más chico pero analizable), nunca
condiciones enteras en cero — en particular `timing` (el modo que nunca se
midió antes por el bug del delay) ya queda cubierto desde la primera
repetición, no se deja para el final.

## Piezas nuevas

| Archivo | Qué hace |
|---|---|
| `generate_request_bank.py` | Registra hasta N usuarios sintéticos reales (login, dirección, método de pago) y escribe el JSON que consume el resto. No pre-llena el carrito. |
| `checkout_locustfile.py` | Tarea de Locust: agrega ítem al carrito + checkout, en bucle, con el modo de fallo sorteado en el cliente (p=0.10) y re-login automático si el JWT expira (10 min). |
| `reset_ambiente.py` | Repone el stock de los productos y vacía exclusivamente los carritos/reservas del banco sintético entre fases, vía SQL directo. No borra órdenes ni facturas. |
| `run_real_experiment.py` | Orquestador: preflight directo (salud de seis contenedores + `SELECT 1` a la BD de campaña), piloto, rampa 1/5/10/25/50 separada de la matriz, orden fijo, reanudación desde el CSV crudo, caché JWT segura por expiración, aislamiento de estado entre calentamiento/medición, monitoreo de CPU/mem de Locust y captura de saturación. |
| `analyze_real_results.py` | Valida las 120 corridas y genera medianas por cada una de las 24 condiciones. |

## Antes de arrancar de verdad

1. **Instalar dependencias en un venv del repo** (no reutilizar otro
   proyecto — regla ya establecida en `spark/PLAN-PASO6.md`):
   ```powershell
   python -m venv experiments/paso8/.venv-real
   experiments/paso8/.venv-real/Scripts/pip install -r experiments/paso8/requirements-real.txt
   ```
2. **Verificar que el cluster de AWS esté encendido** y que
   `CRDB_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` apunten a él en el entorno
   del shell (las mismas variables que usan los microservicios).
3. **Levantar el stack con las dos desviaciones deliberadas activas**
   (ninguna es el default de `docker-compose.yml`):
   ```powershell
   $env:EXPERIMENT_FAULT_INJECTION_ENABLED = "true"
   $env:GATEWAY_RATE_LIMIT_REQUESTS = "20000"
   $env:COORD = "2pc"
   docker compose build --no-cache tiendatech-usuarios tiendatech-productos `
     tiendatech-inventario tiendatech-ventas tiendatech-pedidos tiendatech-gateway
   docker compose up -d
   ```
   La reconstrucción sin caché es parte del protocolo: evita mezclar binarios
   viejos con contratos actuales. En el piloto del 5 de septiembre una imagen
   desactualizada de Inventario interpretó como ceros el nuevo sobre TCP de
   Pedidos y produjo HTTP 409 aun con identificadores válidos.
   `run_real_experiment.py` se niega a arrancar si no detecta estas dos
   variables en los contenedores corriendo (`verificar_fault_injection_habilitado`,
   `verificar_rate_limit_elevado`) — no es un chequeo cosmético, evita medir
   horas de resultados inválidos.
4. **Elegir los productos del experimento** (necesitan stock alto y estable).
   Usar solo 3 productos concentra 400 usuarios sobre 3 filas: a partir de
   c=25 esa fila caliente satura CockroachDB y produce esperas de cliente
   (HTTP 0) que no miden 2PC vs. Saga, sino contención artificial. Por eso se
   usan los **29 productos habilitados** en la intersección de `productos.producto`
   e `inventario.inventario_producto`:
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/query_productos_habilitados.py
   ```
   repóneles stock alto a todos:
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/reset_ambiente.py --producto-ids 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 --valor 1000000
   ```
   Si el banco de usuarios ya fue generado con menos productos, redistribuir
   sin re-registrar cuentas (conserva login/token/dirección/método de pago):
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/redistribuir_banco.py --banco experiments/paso8/resultados-reales/banco.json --producto-ids 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29
   ```
5. **Generar el banco de usuarios sintéticos** (400 para cubrir la
   concurrencia más alta; tarda varios minutos por el pacing del rate-limit):
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/generate_request_bank.py `
     --gateway http://localhost:8180 `
     --output experiments/paso8/resultados-reales/banco.json `
     --count 400 --min-stock-producto 200000
   ```
   Revisar `banco.fallidos.json` si el conteo final es menor a 400.
6. **Ejecutar toda la preparación antes de comprometer más de 12 horas.** El
   modo `--readiness-only` hace el preflight, los pilotos de 120s y la rampa
   1/5/10/25/50 para E-2PC y Saga, pero tiene un candado que impide iniciar la
   matriz:
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/run_real_experiment.py `
     --request-bank experiments/paso8/resultados-reales/banco.json `
     --output experiments/paso8/resultados-reales/correctiva-20260905-final-v2 `
     --readiness-only `
     --pilot-warmup-seconds 10 --pilot-seconds 120 `
     --producto-ids 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29
   ```
   No use `--skip-pilot` en el primer arranque. Los artefactos quedan en
   `piloto-basal/runs/`, con un resumen auditable por estrategia en
   `piloto-basal/piloto-2pc.json` y `piloto-basal/piloto-saga.json`. Si el
   piloto falla, la matriz no comienza. La rampa queda en
   `rampa-readiness/` y tampoco escribe el CSV oficial. Al terminar,
   `--readiness-only` finaliza sin ejecutar ninguna de las 120 corridas.
   Si se dispone de un JWT administrativo legítimo, se puede añadir
   `--admin-token $env:ADMIN_JWT` para validar también `/api/admin/system`.
   Sin él, el preflight no se omite: usa las sondas Docker y SQL directas y
   guarda el resultado en `preflight.json`.
7. **Lanzar el experimento completo** (mismo comando, sin recortar
   `--concurrencias`/`--repeticiones`/tiempos):
   ```powershell
   experiments/paso8/.venv-real/Scripts/python experiments/paso8/run_real_experiment.py `
     --request-bank experiments/paso8/resultados-reales/banco.json `
     --output experiments/paso8/resultados-reales/correctiva-20260905-final-v2 `
     --skip-pilot `
     --producto-ids 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29
   ```
   Si se corta, correr **exactamente el mismo comando**: lee
   `experimento_real_crudo.csv`, salta lo ya hecho y sigue en el mismo orden.
   `--skip-pilot` no es un bypass libre: antes de tocar el stock o iniciar la
   matriz, el orquestador verifica que existan los dos pilotos de 120 s, los 10
   escalones de readiness aprobados y que la huella estable de los 400 casos
   coincida con el banco que superó esas pruebas. Los JWT y contraseñas se
   excluyen de la huella para permitir renovar credenciales sin invalidarla.

## Decisiones tomadas que no estaban explícitas en el pedido (revisar)

- **`wait_time` entre iteraciones de cada usuario virtual**: `between(0.2, 1.0)` s,
  elegido para sostener throughput sin ser una f ráfaga sin pausa. No viene de
  la guía ni de la conversación — es un supuesto metodológico a documentar en
  amenazas a la validez si no se ajusta.
- **`spawn_rate` limitado a 20 usuarios/s** para evitar que la máquina de carga
  falsee el resultado con una estampida instantánea.
- **Reinicio real entre corridas**: se reinician usuarios, productos,
  inventario, ventas, pedidos y gateway; después se espera estado saludable y
  se repone el stock. CockroachDB en AWS no se reinicia ni destruye.
- **Aislamiento de carritos**: después del reinicio y nuevamente al terminar
  el calentamiento se borran solo los detalles y reservas activas de los
  usuarios sintéticos seleccionados. Esto evita heredar cantidades y estados
  Lamport; cada corrida guarda los conteos en `reset-fases.json`. Las órdenes,
  facturas y movimientos históricos se conservan. Locust usa una parada
  graciosa de hasta 65s para no abandonar una transacción en vuelo; la limpieza
  repite su transacción completa si CockroachDB devuelve SQLSTATE `40001`.
- **Caché JWT**: como los tokens duran 10 minutos, solo se reutilizan cuando su
  `exp` cubre calentamiento + medición + 60s de margen; los demás se renuevan
  mediante el login real antes de medir.
- **No se verificó** si `cedula`/`telefono` tienen validación de formato más
  allá de longitud — `generate_request_bank.py` seguirá con los fallidos
  registrados en `banco.fallidos.json` si algún patrón es rechazado; revisar
  ese archivo tras el paso 5.
- **Redistribución de productos (2026-09-05)**: el banco original (`banco.json`)
  asignaba solo 3 productos a 400 usuarios (round-robin de
  `generate_request_bank.py`). En la rampa de readiness, c=25 con esos 3
  productos produjo ~8 escrituras concurrentes por fila y varias esperas de
  cliente (`HTTP 0`) por encima de 60s — contención artificial, no una
  diferencia real entre 2PC y Saga. Se redistribuyó `productoId` en el mismo
  `banco.json` (mismas cuentas/tokens/direcciones, sin re-registrar usuarios)
  sobre los 29 productos habilitados en la intersección de
  `productos.producto` e `inventario.inventario_producto`
  (`redistribuir_banco.py`), y se topó el stock de los 29 a 1,000,000
  (`reset_ambiente.py`). El original de 3 productos queda como
  `banco.json.bak-3productos` para trazabilidad.
- **Timeout de cliente de carga insuficiente en c=50 (2026-09-05)**: con los
  dos candados anteriores resueltos, la rampa de readiness volvió a fallar en
  el último escalón (c=50, E-2PC) por 2 "checkout HTTP 0". Se verificó en los
  logs de `pedidos-service` que esos mismos checkouts sí completaron con
  `201` en el servidor, tardando 62-65s bajo la barrera síncrona de E-2PC con
  50 usuarios concurrentes — el cliente (Locust) los daba por caídos a los
  60s. Se subió el timeout HTTP del locustfile de 60s a 90s
  (`checkout_locustfile.py`) y el `--stop-timeout` del orquestador de 65s a
  95s para dejarles margen a completar antes de que Locust cierre. La
  latencia real (60-65s en cola bajo E-2PC a c=50) se conserva en las
  métricas de la corrida — es evidencia real de la diferencia 2PC vs. Saga
  bajo carga, no se está ocultando.
