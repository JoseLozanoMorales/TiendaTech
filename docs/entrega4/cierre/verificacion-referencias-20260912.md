# Verificación documental de referencias — 12 de septiembre de 2026

Tarea 1. HEAD revisado: `80a5405a92c02169ea92d35181e77a885de40ad9`. Referencia remota consultada mediante fetch: `80a5405a92c02169ea92d35181e77a885de40ad9`. No se regeneró el PDF ni se ejecutaron pruebas técnicas nuevas.

## Tres enlaces corregidos

| Archivo y enlace permanente | Commit completo | Comprobación y afirmación respaldada |
|---|---|---|
| [docs/evidencias/cobertura/web/coverage-summary.json](https://github.com/JoseLozanoMorales/TiendaTech/blob/3e0738268fb51f31424d88db24fb97e04d5b0101/docs/evidencias/cobertura/web/coverage-summary.json) | `3e0738268fb51f31424d88db24fb97e04d5b0101` | Blob existente, commit alcanzable desde origin/main y contenido igual al local salvo finales de línea. 55/103 líneas = 53,39 %. El número de 19 pruebas se respalda por separado en el README de cobertura del mismo commit; no está en el JSON. |
| [docs/experimentos/resultados/iso25010/complejidad/webapp-eslint-summary.csv](https://github.com/JoseLozanoMorales/TiendaTech/blob/a9d4fbb57bfbe33262599da5b0f2649f1cae8bd1/docs/experimentos/resultados/iso25010/complejidad/webapp-eslint-summary.csv) | `a9d4fbb57bfbe33262599da5b0f2649f1cae8bd1` | Blob existente, commit alcanzable desde origin/main y contenido igual al local salvo finales de línea. 27 archivos, 0 errores y 0 advertencias; objetivo de cero incidencias. |
| [docs/experimentos/resultados/iso25010/complejidad/webapp-eslint.json](https://github.com/JoseLozanoMorales/TiendaTech/blob/a9d4fbb57bfbe33262599da5b0f2649f1cae8bd1/docs/experimentos/resultados/iso25010/complejidad/webapp-eslint.json) | `a9d4fbb57bfbe33262599da5b0f2649f1cae8bd1` | Blob existente, commit alcanzable desde origin/main y contenido igual al local salvo finales de línea. 27 entradas; suma de errorCount = 0 y warningCount = 0. No mide complejidad ciclomática. |

La comprobación de publicación se realiza contra objetos Git obtenidos del remoto y su ascendencia; no se hizo una comprobación HTTP autenticada de cada URL. El formato es el enlace estándar de GitHub a un blob inmutable.

## Macro e incluidos

La macro `\evidencia` conserva el corte histórico `c5dca588ea9662cab56732c97fe644b9a929854b`. Los tres destinos posteriores usan `\href` individual con hash completo; no se desplazaron las demás citas a una versión nueva.

Fuentes recorridas: `docs/entrega4/actualizacion-evidencias.tex`, `docs/entrega4/estado-arte-2pc-saga.tex`, `docs/entrega4/PFC4.tex`, `docs/entrega4/registro-cambios.tex`, `docs/entrega4/trazabilidad-temas.tex`.

Se comprobaron los 8 destinos restantes de la macro contra su corte histórico (existencia; no revalidación experimental):

- `docs/adr`: `tree` existente.
- `contracts/stock_reservation.proto`: `blob` existente.
- `experiments/paso7/coordination_lab.py`: `blob` existente.
- `experiments/paso8/resultados/experimento_resumen.csv`: `blob` existente.
- `experiments/paso8/resultados/comparaciones_mann_whitney.csv`: `blob` existente.
- `resultados/tiempos_resumen.csv`: `blob` existente.
- `docs/evidencias/cobertura/verificar_umbral.py`: `blob` existente.
- `docs/experimentos/resultados/iso25010/complejidad/summary.csv`: `blob` existente.

## Seguridad: correspondencia y límites

- [CSV real](https://github.com/JoseLozanoMorales/TiendaTech/blob/b8d3e23988bf32e739f260e21cb8b999f362e783/docs/experimentos/resultados/iso25010/security-401-gatewayintegration-2026-09-11.csv): ocho familias distintas, GET, HTTP observado y esperado 401; fecha declarada 2026-09-11 y revisión `24d27fccc03a20d9c685e84b2449c9cdf99cb08b`.
- [Prueba parametrizada](https://github.com/JoseLozanoMorales/TiendaTech/blob/24d27fccc03a20d9c685e84b2449c9cdf99cb08b/Apps/web/frontend/src/test/java/com/tiendatech/frontend/GatewayIntegrationTest.java): las ocho rutas coinciden con el CSV y la aserción exige HTTP 401 sin token. La clase incluye además tres pruebas de flujo; once es el total de la suite, no once familias protegidas.
- La matriz `docs/experimentos/resultados/iso25010.csv` apunta al CSV del 11 de septiembre. No se modificaron los resultados ni se fabricó evidencia.
- Reporte local inspeccionado: `Apps/web/frontend/target/surefire-reports/TEST-com.tiendatech.frontend.GatewayIntegrationTest.xml`; tests=11, failures=0, errors=0. SHA-256 `026265410388b2fb9229606c0e2be196706e14fb0cdeb174db56124bcfdf9401`. Está bajo target y no versionado; no acredita por sí solo fecha o commit ejecutado. El CSV publicado y la declaración del commit de Andy son la evidencia versionada de esta ejecución; no se presenta el XML local como evidencia remota.
- La existencia del CSV resuelve esa cita. No demuestra una auditoría integral de seguridad ni acredita el validador automático de enlaces.

## Referencias históricas y coordinación con la tarea 2

- La única mención literal al nombre ausente del 1 de septiembre en `docs/` aparece en `cierre/pendientes-documentalista.md`, ahora identificado expresamente como antecedente histórico. La matriz activa utiliza el archivo nuevo.
- Formato para integración: `\href{https://github.com/JoseLozanoMorales/TiendaTech/blob/<SHA completo>/<ruta>}{etiqueta}` para evidencias posteriores; conservar `\evidencia` para el corte histórico. Las rutas deben existir en el commit y respaldar la afirmación, no solo resolver.
- Esta convención queda documentada para la tarea 2; no se ha confirmado coordinación con su responsable ni se han enviado mensajes en su nombre. Integración debe revisar compatibilidad con su validador antes del cierre global.
- No se acredita la tarea 2 ni un validador automático persistente. Esta es una comprobación documental puntual de los destinos indicados y de la macro histórica.
- El PDF permanece sin regenerar por instrucción de la tarea. Su integración final corresponde al responsable de integración.
