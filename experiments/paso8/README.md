# Paso 8 - ejecución y análisis del experimento

> **Alcance vigente:** `resultados-reales/correctiva-20260905-final-v2/`
> contiene la campaña correctiva oficial completada y validada. La ejecución
> anterior de `resultados-reales/oficial-v4-20260904/` se conserva como
> antecedente de la observación docente, pero no sustenta las conclusiones
> actuales. `run_paso8.py` y `coordination_lab.py` usan SQLite y se conservan
> únicamente para reproducibilidad; no sustentan C2, C3 ni C6.

La repetición correctiva completó 120 corridas con 60 segundos de
calentamiento y 300 segundos de medición, después de aprobar un piloto basal
de dos minutos con un usuario para cada estrategia y una rampa previa sin
fallos de 1/5/10/25/50 usuarios. La validación, el resumen y las conclusiones
están en `resultados-reales/correctiva-20260905-final-v2/analisis/`.

El piloto local ejecuta el experimento propio de TiendaTech sobre el banco construido
en `experiments/paso7`: confirmacion en dos fases (`2pc`) frente a saga con
compensacion (`saga`), bajo fallos de pasarela.

## Matriz local histórica

- Estrategias: `2pc`, `saga`.
- Concurrencia: `50`, `100`, `200`, `400` compradores simultaneos.
- Fallos de pasarela: `none`, `omission`, `timing`.
- Repeticiones: `12` por condicion.
- Total: `2 x 4 x 3 x 12 = 288` corridas.
- Probabilidad de fallo: `0.10`.

Comando usado para la corrida local:

```powershell
py experiments/paso8/run_paso8.py `
  --output experiments/paso8/resultados `
  --repeticiones 12 `
  --concurrencias 50 100 200 400 `
  --fault-probability 0.10 `
  --delay-seconds 5 `
  --warmup-seconds 60
```

El ejecutor usa por defecto la temporización de cinco segundos y sesenta
segundos de carga real descartada. El calentamiento se ejecuta sobre una base
separada para que sus operaciones no contaminen la corrida medida.

```powershell
py experiments/paso8/run_paso8.py `
  --output experiments/paso8/resultados-rubrica `
  --repeticiones 12 `
  --concurrencias 50 100 200 400 `
  --fault-probability 0.10 `
  --delay-seconds 5 `
  --warmup-seconds 60
```

## Evidencia inicial (no sustituye la nueva corrida)

- `resultados/experimento_crudo.csv`: 120 corridas iniciales; deben regenerarse
  las 288 corridas con la configuración corregida.
- `resultados/experimento_resumen.csv`: 24 condiciones con mediana, IC95%,
  tasas e intervalos binomiales.
- `resultados/comparaciones_mann_whitney.csv`: comparacion 2PC vs Saga por
  condicion, con U de Mann-Whitney y A12 de Vargha-Delaney.
- `resultados/compatibilidad_resultados.csv`: 120 casos del asistente de
  compatibilidad con falsos positivos y falsos negativos.
- `resultados/compatibilidad_resumen.json`: aciertos e IC95% binomial.
- `resultados/boxplot_latencia_p95.svg` y `resultados/boxplot_throughput.svg`.
- `resultados/amenazas_validez.md`: cuatro categorias de amenazas.
- `resultados/db/*.db`: base SQLite auditable por corrida.

## Validación principal contra microservicios reales

`run_real_experiment.py` ejecuta compradores sintéticos contra
`Gateway -> Pedidos -> Ventas/Inventario -> CockroachDB`. Antes de medir comprueba
que los seis componentes estén disponibles, valida el checkout con un piloto
basal, ejecuta una rampa gradual separada del CSV oficial y realiza 60 segundos
de calentamiento descartado en cada corrida. Antes del calentamiento y antes de
la medición vacía únicamente los carritos y reservas de los usuarios sintéticos,
para que un reinicio no deje relojes Lamport o cantidades de otra fase. El
banco JSON no se versiona porque contiene JWT efímeros; cada
caso requiere `caseId`, `token`, `direccionId` y `metodopagoId`, y debe corresponder
a un usuario sintético con carrito preparado.

En el stack real, E-2PC y Saga ya no son solo una etiqueta de observabilidad.
E-2PC espera sincrónicamente la aceptación de Inventario antes de confirmar el
checkout; Saga confirma factura/outbox localmente y deja el movimiento de
Inventario al procesador asíncrono. Ambas rutas comparten una clave idempotente
por factura. E-2PC es una coordinación experimental, no XA distribuido.

```bash
python3 experiments/paso8/run_microservices.py \
  --admin-token "$ADMIN_JWT" \
  --request-bank /tmp/compradores-sinteticos.json \
  --concurrencia 50 --repeticion 1 --failure-mode timing
```

Para habilitar los fallos controlados, el stack experimental se levanta con
`EXPERIMENT_FAULT_INJECTION_ENABLED=true`. `timing` retrasa la respuesta cinco
segundos; `omission` espera nueve segundos y devuelve de forma determinista un
`504 Gateway Timeout`. El cliente de facturación tiene un timeout independiente
de treinta segundos para no confundir una respuesta normal lenta bajo carga con
un fallo experimental. La llamada Ventas→Inventario también espera treinta
segundos: evita reenvíos de una salida idempotente mientras CockroachDB resuelve
la contención. Los demás clientes de Pedidos conservan el límite general.
El mecanismo permanece desactivado por defecto.

## Resultados vigentes de la campaña correctiva

La matriz produjo 208 003 solicitudes y 49 786 intentos de checkout, con
30 275 confirmados. La reconstrucción retrospectiva de las ventanas oficiales
consultó CockroachDB real en una instantánea MVCC fija y encontró 43 168
órdenes persistidas: 6 290 sin factura y 13 210 sin exactamente el movimiento
de inventario esperado. No encontró importes de factura distintos, descuentos
duplicados ni stock negativo.

El sistema no conserva un ledger independiente de cobros y los intentos
cancelados/fallidos estaban en un buffer de memoria que se perdió con los
reinicios. Esos invariantes quedan `no_verificado`, no se presentan como cero ni
como éxito. La convergencia Saga recuperable mide el outbox durable entre
factura e inventario.

Archivos principales dentro de
`resultados-reales/correctiva-20260905-final-v2/analisis/`:

- `oracle_por_corrida_crdb.csv` y `experimento_real_crudo_enriquecido.csv`.
- `usuarios_sinteticos_ids.csv`, lista reproducible sin credenciales ni JWT.
- `oracle_resumen_crdb.json`, con instantánea, límites, totales y hashes.
- `resumen_estadistico_ic95.csv` y `proporciones_binomiales_ic95.csv`.
- `comparaciones_mann_whitney.csv`, con U bilateral y A12.
- `boxplot_*.svg`, diagramas de caja de las cinco repeticiones.
- `informe_final.md`, interpretación completa y límites de inferencia.

Para recalcular el oráculo se configuran las variables `CRDB_DATASOURCE_URL`,
`CRDB_DATASOURCE_USERNAME` y `CRDB_DATASOURCE_PASSWORD` del clúster real y se
ejecuta:

```powershell
python experiments/paso8/reconstruct_real_oracle.py `
  --as-of 2026-09-07T20:10:24.380904Z
python experiments/paso8/analyze_corrective_results.py
```

La instantánea histórica evita que el reprocesamiento posterior del outbox
cambie los resultados. Los CSV y sus hashes permanecen como evidencia aun
cuando CockroachDB deje de retener esa versión MVCC.
