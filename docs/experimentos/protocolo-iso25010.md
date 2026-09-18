# Protocolo de evaluación ISO/IEC 25010:2023 — Paso 4

## Alcance y reglas

Responsable: **Andy Paul Sánchez Pilaloa**. Se evalúan disponibilidad, eficiencia de desempeño,
fiabilidad, mantenibilidad y seguridad. Un valor se publica únicamente si existe
un artefacto reproducible; la ausencia de medición se registra como `PENDIENTE`,
nunca como una estimación.

## Umbrales

| Característica | Métrica | Objetivo |
|---|---|---|
| Disponibilidad | respuestas exitosas / sondeos durante 3600 s continuos | uptime >= 99.5 % |
| Rendimiento | percentil 95 agregado de Locust | P95 < 500 ms con 50 usuarios |
| Fiabilidad | respuestas HTTP 5xx / respuestas totales de la misma carga | 5xx < 1 % |
| Mantenibilidad | líneas cubiertas y máximo de complejidad por método | cobertura >= 70 % y complejidad < 10 |
| Seguridad | familias protegidas que responden 401 sin JWT | 100 % |

## Precondiciones

1. Registrar commit, fecha, zona horaria, sistema operativo, CPU, RAM, versiones
   de Docker, Java, Python, Locust y PMD en la carpeta de ejecución.
2. Ejecutar `docker compose up -d --build` y conservar `docker compose ps`.
3. Verificar que los nueve contenedores estén saludables, que el Gateway
   responda en `http://localhost:8180` y que los siete targets de Prometheus
   estén `UP`. No iniciar una medición con el stack degradado.
4. Abrir el dashboard con una ventana que incluya toda la ejecución. La captura
   debe mostrar la hora y pertenecer a la misma corrida que los CSV.

## Procedimiento oficial

1. Crear una carpeta nueva, por ejemplo
   `docs/experimentos/resultados/iso25010/AAAA-MM-DDTHH-mm-ss/`. No sobrescribir
   una corrida anterior.
2. Ejecutar la carga sin alterar sus parámetros oficiales:

   ```powershell
   ./tests/load/run-load-test.ps1 -Users 50 -SpawnRate 5 -RunTime 60s
   ```

   Conservar `*_stats.csv`, `*_failures.csv`, `*_exceptions.csv` y el HTML.
   El P95 se toma de la fila `Aggregated` de `*_stats.csv`. La tasa 5xx se
   calcula desde las respuestas 5xx; no se debe sustituir por la tasa total de
   fallos si existen fallos de transporte u otros códigos.
3. Iniciar la medición de disponibilidad inmediatamente después, manteniendo el
   mismo stack durante **3600 segundos continuos**:

   ```powershell
   ./scripts/evaluate-iso25010.ps1 -GatewayUrl http://localhost:8180 -DurationSeconds 3600
   ```

   El script rechaza para el resultado oficial cualquier duración distinta de
   3600 s, salvo que se use explícitamente `-AllowNonOfficialRun`; esas corridas
   quedan etiquetadas `NO_OFICIAL` y no alimentan el CSV final.
4. Capturar el dashboard al finalizar y guardar la imagen dentro de la carpeta
   de la misma ejecución.
5. Medir complejidad con PMD 7.17.0:

   ```powershell
   ./scripts/measure-cyclomatic-complexity.ps1
   ```

   El criterio se aplica al máximo por **método** de todo el código Java de
   producción. Se conservan los XML originales de cada módulo y el resumen CSV.
6. Regenerar cobertura con el pipeline y conservar los XML JaCoCo/coverage.py.
   Para cada servicio: `covered / (covered + missed) * 100` sobre líneas.
7. Ejecutar la seguridad:

   ```powershell
   ./Apps/web/frontend/mvnw.cmd -f Apps/web/frontend/pom.xml -Dtest=GatewayIntegrationTest test
   ```

   Conservar el XML de Surefire. Son 11 casos: tres flujos de Gateway y ocho
   familias protegidas sin token.
8. Actualizar `docs/experimentos/resultados/iso25010.csv` solo con los artefactos
   obtenidos y copiar la tabla de `docs/experimentos/resultados/iso25010-booktabs.tex`
   al documento LaTeX.

## Fórmulas y decisión

- Uptime (%) = `100 * sondeos HTTP exitosos / sondeos totales`.
- 5xx (%) = `100 * respuestas con estado 500..599 / respuestas totales`.
- Cobertura (%) = `100 * líneas cubiertas / (cubiertas + no cubiertas)`.
- `CUMPLE` solo si existe medición y satisface estrictamente el operador del
  objetivo; `NO CUMPLE` si existe y lo viola; `PENDIENTE` si falta la medición.

## Estado de la corrida oficial del 2026-09-04

La ejecución congeló entorno y topología en `2026-09-04T08-44-12/environment.txt`.
La disponibilidad se observó durante 3600.409 s, con 3588/3588 sondeos exitosos
(100 %; IC95 % Wilson 99.8931--100 %). La carga alcanzó 50 usuarios durante 60 s:
2112 solicitudes, cero fallos y P95 agregado de 610 ms. Por tanto, disponibilidad
y fiabilidad cumplen, mientras rendimiento no cumple el umbral estricto de 500 ms.

PMD 7.17.0 registra complejidad máxima 9. La cifra de cobertura consolidada
de esta corrida oficial (935/1136 líneas, 82.31 %) quedó obsoleta el 17-18 de
septiembre de 2026: medía cada microservicio Java solo sobre `application/**`
y `armado-ia` solo sobre `app.domain`+`app.explicacion`. Al retirar esos
filtros y agregar el Gateway (que no tenía JaCoCo configurado), la cobertura
real de los ocho servicios medidos es 2370/7060 líneas (33.57 %,
IC95 % Wilson descriptivo 32.4771–34.6796 %) — **NO CUMPLE** el umbral de
70 %; solo el Gateway lo alcanza (78.26 %). El detalle por servicio está en
`docs/experimentos/resultados/iso25010/cobertura-summary.csv`, regenerado en
la misma fecha desde los XML versionados en `docs/evidencias/cobertura/`.
Disponibilidad, fiabilidad, rendimiento, complejidad y seguridad no se
recalcularon en este ajuste: conservan su corrida oficial del 2026-09-04 (o
del 2026-09-11 para seguridad) sin cambios. Seguridad cubre las ocho familias
protegidas y las ocho responden 401 sin JWT.

No se calcula un intervalo para el P95 porque existe una sola corrida oficial;
hacerlo requeriría muestras por solicitud o repeticiones independientes. Esta
limitación se mantiene explícita y no cambia la decisión de `NO CUMPLE` (610 ms).
