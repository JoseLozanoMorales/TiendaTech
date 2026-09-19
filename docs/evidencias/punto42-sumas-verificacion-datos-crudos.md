# Cierre — Punto 42 (Sumas de verificación de los datos crudos)

- Fecha: 2026-09-17
- Responsable: Jhinson Aucatoma (equipo AGLS)
- Estado previo: Modificar (Logro 60%, Puntos 3,36)

## Qué pedía la guía (cita textual)

> Qué pedía la guía. Ampliar el inventario a los archivos estructurados que
> contienen los veredictos, de modo que la evidencia que sostiene las
> conclusiones quede también cubierta.

## Qué impide llegar a Hecho (cita textual)

> Hay veredictos y datos estructurados sin suma (oráculos 2PC/Saga de paso 7,
> trazas ISO 25010, SVG con sumas incrustadas no verificadas), y su
> alteración pasa en verde. Hay manifiestos regenerados sobre 23 CSV crudos
> alterados (4 de la campaña del 04/09 con suma cambiada), sin ningún
> control que impida reescribir sumas existentes. Hay dos manifiestos
> `.apk.sha256` que fallan 0/2 en un clon limpio.

Respondo cada uno de los tres bloqueos, en orden. Los dos primeros tienen
dos partes cada uno (dato faltante / control faltante), y las trato por
separado para no mezclar "ya está cubierto" con "esto es nuevo".

## 1. Veredictos y datos estructurados sin suma (oráculos paso 7, trazas ISO, SVG)

**Corregido en `915183f` (cobertura nueva) y `9e2324a` (SVG en
`campaign_checksums.py`).**

Antes de este cierre, dos verificadores existían (`check_data_checksums.py`
para `.csv`/`.tsv` de todo el repo, `campaign_checksums.py` para
`.csv`/`.tsv`/`.json` por carpeta de campaña) y ninguno de los dos cubría
las ubicaciones que el evaluador mutó para probar el hueco:
`experiments/paso7/evidence/oracle-2pc.json` y
`iso25010/.../trace/checkout.json`, ambos con `rc=0` (verde) pese a la
alteración.

Se creó `scripts/check_raw_evidence_checksums.py` (`915183f`), con su
propio manifiesto
(`docs/experimentos/resultados/checksums-evidencia-adicional.sha256`) y su
propia suite de pruebas (`scripts/test_check_raw_evidence_checksums.py`),
cubriendo las 26 rutas que antes no tenían ninguna suma en ningún
verificador:

- `experiments/paso7/evidence/`: `oracle-2pc.json`, `oracle-saga.json`,
  `compatibility-case-bank.json`, `transaction-case-bank.json` (4
  archivos) — responde directamente a "oráculos 2PC/Saga de paso 7".
- `docs/experimentos/resultados/iso25010/2026-09-04T08-44-12/trace/`: 9
  JSON + 7 `.headers` + `environment.txt` (17 archivos) — responde a
  "trazas ISO 25010".
- `experiments/paso8/resultados/` (distinto de `resultados-reales/`, que
  cubre `campaign_checksums.py`): `metadata.json`,
  `compatibilidad_resumen.json`, `boxplot_latencia_p95.svg`,
  `boxplot_throughput.svg` (4 archivos).
- `docs/experimentos/resultados/resumen.json` (1 archivo).

Por separado, los 5 SVG del análisis estadístico (los que el evaluador
mutó cambiando un color, `boxplot_tasa_inconsistencia.svg` entre ellos) se
cubrieron agregando `.svg` al inventario de `campaign_checksums.py`
(`9e2324a`) — responde a "SVG con sumas incrustadas no verificadas".

Verificación con mutation testing, repitiendo exactamente la prueba del
evaluador contra los archivos que él mismo nombró:

- `oracle-2pc.json`: alterar un byte → `check_raw_evidence_checksums.py`
  pasa de `rc=0` a `rc=1`; restaurar → vuelve a `rc=0`.
- `iso25010/.../trace/checkout.json`: mismo resultado.
- `boxplot_tasa_inconsistencia.svg`: alterar un color → `rc=0` a `rc=1` en
  `campaign_checksums.py`; restaurar → `rc=0`. Esta prueba quedó además
  como test unitario permanente
  (`test_svg_covered_and_tampering_detected` en
  `test_campaign_checksums.py`), no solo como verificación manual de una
  vez.

**Además, más allá de lo mínimo del bloqueo citado**: la guía también
menciona en "Qué encontré" que quedaban fuera los 2 `informe_final.md` de
campaña. No forman parte de la frase de "Qué impide llegar a Hecho", pero
por la misma razón que todo lo anterior — son evidencia estructurada que
sostiene un veredicto, esta vez en prosa en vez de JSON — se agregaron a
`campaign_checksums.py` en `1603a96` (extensión `.md`, con
`test_md_covered_and_tampering_detected` como prueba de mutación), en
lugar de documentarlos como excepción aceptada.

## 2. Manifiestos regenerados sobre 23 CSV alterados, sin control anti-reescritura

Esto tiene dos partes reales y las separo: los datos ya alterados, y el
control que faltaba para que no vuelva a pasar.

**Los 23 CSV en sí, ya restaurados** — no es trabajo de esta sesión, es el
cierre del punto 22 (`docs/evidencias/punto22-datos-crudos-preservados.md`,
commits `c10f43b`, `3ee51b4`, `cf4b307`, `fdb16f6`): los 4 CSV de la
campaña del 04/09 que cita el evaluador aquí son el mismo grupo que ese
documento restauró y verificó byte a byte contra el blob de git anterior a
la corrupción de `62d37e4`. No se repite esa evidencia aquí; se referencia
para no duplicar contenido, y porque este punto 42 depende de que esa
restauración sea real — no tendría sentido un control anti-reescritura
sobre datos que siguen corruptos.

**El control que faltaba** — "sin ningún control que impida reescribir
sumas existentes", el corazón técnico de este bloqueo — se corrigió en
`3d5927a`: `scripts/check_checksum_rewrites.py`, un guardián nuevo que
compara cada manifiesto de checksums contra su versión en el commit padre;
si una entrada que **ya existía** cambió de hash, exige un trailer
explícito `Checksum-Rewrite: <manifiesto>:<archivo>` en el mensaje del
commit, o CI falla. Una entrada nueva no requiere nada (no bloquea agregar
datos), y una entrada eliminada tampoco (desaparecer no es "cambiar de
hash").

Verificado con 8 pruebas unitarias sobre un repositorio git real temporal
(`scripts/test_check_checksum_rewrites.py`: sin cambios, entrada nueva,
reescritura sin justificar, reescritura justificada, justificación que no
coincide con el archivo exacto, entrada eliminada, manifiesto nuevo sin
historial, múltiples reescrituras con justificación parcial) — y, lo más
importante, **contra el propio incidente histórico que motivó el
bloqueo**:

```
python scripts/check_checksum_rewrites.py --base 62d37e4^ --ref 62d37e4
```

detecta correctamente las 24 reescrituras sin justificar de ese commit
exacto, incluidos los 4 CSV de la campaña del 04/09 que cita el evaluador
para este punto. Es decir: este control, si hubiera existido el 15/09,
habría bloqueado la corrupción original en CI antes de que llegara a
`main`.

## 3. Los dos manifiestos `.apk.sha256` que fallan 0/2 en un clon limpio

**Corregido en `3370e29`.**

El evaluador tenía razón en las dos causas posibles y la real fue la
primera: `release/tiendatech-release.apk.sha256` tenía una suma
**desactualizada** (`9da16724...`, ya no correspondía al APK real vigente
del release `v4.0.0`), y ambos `.apk` habían salido del árbol de git en
`55f35f1` (punto 37, movidos a GitHub Release assets) mientras sus
manifiestos `.sha256` seguían versionados — así que un `git clone` limpio
nunca tiene el archivo que el manifiesto describe, y `sha256sum -c` falla
con "No such file or directory" antes incluso de comparar el hash.

La guía pide en su lista de comprobación (§8.3) que **todas** las sumas
pasen. Frente a eso, la opción de documentar una excepción ("los APK viven
en GitHub Releases, no en el clon") habría sido una justificación, no un
cumplimiento literal — y el usuario fue explícito en que priorizara
cumplir la guía tal como está escrita antes que documentar por qué no se
cumple. Por eso se optó por la opción real: recommitear los dos binarios.

- Se descargaron ambos `.apk` frescos desde el asset `v4.0.0` de GitHub
  Releases (`Invoke-WebRequest`, no `gh release download`, que no está
  instalado localmente).
- Se verificó el hash de cada descarga contra el manifiesto correspondiente
  antes de comitear cualquier cosa (`Get-FileHash -Algorithm SHA256`): el
  debug coincidió con el manifiesto existente; el release **no**
  coincidía, confirmando que el manifiesto estaba desactualizado y no el
  binario.
- Se corrigió `release/tiendatech-release.apk.sha256` con el hash real del
  binario recommiteado
  (`b1e622a2...4eccb1d304`, verificado con el mismo comando).
- Se recommitearon los dos `.apk` (34.19 MB + 39.08 MB), revirtiendo
  puntualmente para estos dos archivos la línea de `.gitignore` de punto 37
  que impedía volver a agregarlos — un cambio explícito y documentado en
  `release/README.md` y `release/FIRMA-DISTRIBUCION.md`, no una reversión
  general de la decisión de punto 37 (los demás binarios grandes de esa
  decisión, `.mp4`/`.jar`, siguen fuera del árbol).

Verificación en clon limpio, replicando exactamente el escenario que citó
el evaluador:

```
Get-FileHash release/tiendatech-release.apk -Algorithm SHA256
Get-FileHash release/tiendatech-debug.apk -Algorithm SHA256
```

ambos coinciden con sus `.sha256` respectivos — 2/2, no 0/2.

## Verificación final

- CI en verde para cada uno de los commits de este punto:
  `915183f`, `9e2324a`, `1603a96`, `3d5927a` (compartido con el punto 46),
  `3370e29`.
- Los tres bloqueos citados textualmente por el evaluador tienen, cada
  uno, una prueba de mutación que reproduce el escenario exacto que él
  probó (alterar `oracle-2pc.json`, alterar un color de SVG, `--base
  62d37e4^ --ref 62d37e4` contra la corrupción histórica real, clon con
  los `.apk` presentes) y confirma la detección o el paso en verde según
  corresponda.
- La observación del evaluador sobre CRLF/LF ("un CSV o JSON reescrito con
  otros finales de línea pasa igual", probado con `validacion.json` en
  CRLF) queda cubierta por el guardián `line-endings` del punto 46
  (`9134419`), no se duplica aquí — ver
  `docs/evidencias/punto46-fijacion-finales-linea.md`.

## Reconciliación final con el punto 37 (18/09)

La reintroducción de los dos APK en `3370e29` fue una corrección intermedia que
hizo verificables los `.sha256` locales, pero contradecía la obligación del
punto 37 de retirar ambos instalables. El estado definitivo elimina del árbol
los dos APK y sus dos manifiestos locales. En su lugar,
`release/release-assets-v4.0.0.json` fija los metadatos de los assets publicados
y `scripts/verify_release_assets.py` verifica en CI su presencia, tamaño y
digest directamente contra la API de GitHub. El modo `--download-dir` permite
además recalcular el SHA-256 de los bytes descargados.

Por ello ya no existen manifiestos locales que apunten a archivos ausentes: las
sumas de datos conservados en el árbol siguen bajo los controles de este punto,
y los binarios deliberadamente externos tienen un control remoto explícito y
fallable.

## Conclusión

Los tres bloqueos que citó el evaluador para el punto 42 están corregidos
con evidencia verificable: los oráculos de paso 7, las trazas ISO y los
SVG (más los `informe_final.md`, por la misma razón aunque no estaban en
la frase literal) tienen suma y mutation test propio; el control
anti-reescritura que faltaba existe y se probó contra el incidente
histórico real que lo motivó; y los APK externos tienen un manifiesto de
release comprobado automáticamente, sin contradecir la higiene del punto 37.

## Pendiente

- De los "214 candidatos de datos versionados fuera del código" que
  mencionó el evaluador en "Qué encontré", 126 seguían sin suma antes de
  este cierre, "en su mayoría evidencias de CI/GHCR y no mediciones" según
  su propia caracterización. Este cierre amplió la cobertura a los que el
  evaluador identificó explícitamente como bloqueo (oráculos, trazas ISO,
  SVG, informe_final.md); no se auditaron los 126 candidatos restantes uno
  por uno para decidir si cada uno necesita suma — queda fuera del alcance
  de este cierre puntual.
- El mismo límite honesto del cierre del punto 46 aplica aquí:
  `check_checksum_rewrites.py` no fue ejercitado contra un flujo de pull
  request con commits squasheados ni contra un rebase forzado, solo contra
  push directo a rama (el flujo real de este repo) y contra el escenario
  histórico de `62d37e4`.
