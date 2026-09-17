# Cierre — Punto 22 (Datos crudos preservados)

- Fecha: 2026-09-17
- Objetivo: resolver, con causa raíz corregida y evidencia verificable, los
  cuatro bloqueos que el propio evaluador listó textualmente bajo
  "Qué impide llegar a Hecho" — no maquillar manifiestos ni regenerar sumas
  sobre datos ya alterados, que es exactamente el defecto que se está
  corrigiendo.

## Lo que dijo el evaluador (cita textual, "Qué impide llegar a Hecho")

> La comprobación de las ocho sumas de metodología no está en el flujo:
> alterar un SVG pasa en verde. El generador calcula las sumas sobre bytes
> dependientes de la plataforma (0/8 al regenerar en Windows). 23 CSV crudos
> de carga fueron modificados el 15/09 (BOM y/o líneas vacías), incluidos
> los 4 de la campaña oficial del 04/09, y sus sumas se regeneraron en el
> mismo commit: el manifiesto certifica bytes alterados y no los originales.
> Quedan datos crudos de medición (trazas ISO, oráculos de paso 7) sin suma.

Se responde a cada uno de los cuatro puntos por separado, con commits,
comandos de verificación y resultados reales.

## 1. Los 23 CSV crudos de carga alterados el 15/09

### Causa raíz

El mismo día 15/09, el mismo autor cometió el mismo error dos veces, con 35
minutos de diferencia, ambos con intención de "normalizar CRLF a LF":

- `5a995b5` (11:21, "normalizar CRLF a LF en CSV de punto17 y corregir
  healthcheck E2E"): tocó los 3 CSV de
  `docs/evidencias/punto17-observabilidad-gateway/` y regeneró
  `checksums-datos.sha256` contra el resultado ya alterado.
- `62d37e4` (11:56, "eliminar CR sueltos en CSV de tests/load/results y
  regenerar manifiestos"): tocó 20 archivos dentro de
  `tests/load/results/` (los 4 de la campaña oficial del 04/09 y 16 más:
  4 renombrados después a `tiendatech-ratelimit-check-10usuarios-20260915_*`
  y 12 de `critical-path-{5,10,20}-users`), y regeneró tanto
  `tests/load/results/checksums.txt` como `checksums-datos.sha256`.

Confirmado con `git show 62d37e4 --stat`: el commit toca exactamente 22
rutas, todas dentro de `tests/load/results/` más los dos manifiestos —
ningún archivo fuera de ese árbol. En vez de normalizar bien, ambos commits
antepusieron un BOM UTF-8 y convirtieron los `CR` sueltos en líneas en
blanco adicionales, y luego regeneraron los manifiestos **contra esos
mismos bytes ya corruptos** — por eso los flujos "CI" e "Integridad de
datos", en rojo en `6cfa4fa`, `80d7293`, `cb8cec5` y `5a995b5`, pasaron a
verde en `62d37e4`: por la reescritura, no por una corrección real.

4 (04/09) + 16 (`tests/load/results`) + 3 (`punto17`) = **23 archivos**,
exactamente el número que cita el evaluador.

### Restauración, archivo por archivo

| Grupo | Archivos | Commit limpio usado como referencia | Commit de esta sesión |
|---|---|---|---|
| Campaña oficial 04/09 | 4× `tiendatech-50-users-20260904-local_*.csv` | `822399a` | `cf4b307` (#14) |
| Renombrados (antes `tiendatech-50-users_*`) | 4× `tiendatech-ratelimit-check-10usuarios-20260915_*.csv` | `80d7293` (padre de `62d37e4`) | `c10f43b` |
| Camino crítico | 12× `tiendatech-critical-path-{5,10,20}-users_*.csv` | regenerados desde cero con 3 corridas reales de Locust (5/10/20 usuarios) | `cf4b307` / `fdb16f6` (#14) |
| Punto 17 | 3× `tiendatech-50-users_{stats,failures,exceptions}.csv` en `punto17-observabilidad-gateway/` | `cb8cec5` (padre de `5a995b5`) | `3ee51b4` |

Para los grupos restaurados desde el historial (no regenerados con Locust),
el método fue `git checkout <commit-limpio> -- <ruta>` — el propio escritor
de git, sin pasar por ninguna redirección de shell (una redirección
`git show ... > archivo` vía PowerShell o `cmd` demostró ser poco fiable
para este propósito: un `cmd /c` con `^` en la referencia de git terminó
apuntando al commit corrupto en vez de a su padre, y en otro intento el
`core.autocrlf=true` de la máquina local hizo que `git hash-object`
reportara un hash "fantasma" que no correspondía ni al contenido corrupto
ni al limpio). La verificación final y confiable en todos los casos fue
comparar bytes crudos sin ningún filtro:

```
git rev-parse <commit-limpio>:<ruta>          # hash del blob de referencia
git hash-object --no-filters <archivo-en-disco>  # hash de los bytes reales, sin autocrlf
```

Los dos coincidieron exactamente en cada uno de los 4+3=7 archivos
restaurados por `git checkout` (los 12 de camino crítico ya habían sido
reemplazados con datos reales frescos como parte del arreglo de causa raíz
de la #14, no restaurados desde el historial). Se descartó además que la
restauración reintrodujera la corrupción: se confirmó BOM ausente y
conteo de líneas coherente (sin líneas en blanco espurias) en cada archivo
antes de comitear.

Ambos manifiestos —`tests/load/results/checksums.txt` y
`docs/experimentos/resultados/checksums-datos.sha256`— se regeneraron
después de cada restauración y se revisaron línea por línea antes de cada
commit, confirmando que el diff tocaba únicamente los archivos
restaurados.

## 2. El generador de sumas dependía de la plataforma

`experiments/paso8/analyze_corrective_results.py` calculaba las 8 sumas
embebidas en `analisis_estadistico_metodologia.json` con
`hashlib.sha256(path.read_bytes())` (línea 335), sobre bytes crudos sin
normalizar. Los 3 CSV de salida llevan `\r\n` siempre (comportamiento por
defecto del módulo `csv` de Python, no depende del SO), y los 5 SVG
dependen de la plataforma (`Path.write_text` sin `newline` explícito). Git
normaliza ambos a `LF` al comitear (regla catch-all `eol=lf` de
`.gitattributes`), así que la suma embebida nunca coincidía con el
contenido realmente versionado — y regenerar el análisis en un equipo
distinto reintroducía el defecto, exactamente lo que reportó el evaluador
(0/8 al regenerar en Windows).

Corregido en `0aeff49`: se normaliza `\r\n → \n` antes de hashear, igual
que ya hacían `check_data_checksums.py` y `campaign_checksums.py`. Se
agregó además `rootrelative()` para que `--output` con una ruta fuera del
repositorio ya no reviente con `ValueError ... is not in the subpath` (el
otro defecto que reportó el evaluador sobre este mismo script).

Verificación real, no solo de código:

- `python experiments/paso8/analyze_corrective_results.py --output
  $env:TEMP\test-analisis-output --bootstrap-samples 500` → código de
  salida 0 (antes crasheaba).
- Se regeneró el análisis real (`--bootstrap-samples 20000`, la
  configuración de producción) en la misma máquina Windows donde antes
  fallaba. Los 8 archivos de salida (3 CSV + 5 SVG) dieron el mismo hash
  que la versión ya comiteada, y el hash del propio
  `analisis_estadistico_metodologia.json` en el índice de git
  (`git rev-parse :<ruta>`) resultó **idéntico** al de `HEAD`
  (`c2a441c9...` en ambos) — es decir, el contenido ya comiteado no tenía
  en realidad el desajuste (probablemente porque se generó originalmente en
  un entorno donde por las condiciones del momento ya salió en LF), pero el
  bug era real y sin este fix la próxima regeneración en cualquier otra
  máquina sí lo habría reproducido.

## 3. Las 8 sumas de metodología sin comprobación en CI, y los SVG fuera de todo inventario

El evaluador mutó un color en `boxplot_tasa_inconsistencia.svg` y ningún
verificador lo detectó (`check_data_checksums.py rc=0`,
`campaign_checksums.py rc=0`), porque `campaign_checksums.py` solo indexaba
`.csv/.tsv/.json` — el JSON de metodología se sumaba como archivo entero,
pero nadie verificaba el mapa `sha256` que lleva adentro, ni los 5 SVG
tenían ninguna suma en ningún lado.

Corregido en `9e2324a`: se agrega `.svg` al inventario de
`campaign_checksums.py`. Esto protege exactamente el escenario que probó el
evaluador —una alteración de un SVG ahora cambia su hash y no coincide con
`checksums.txt`— sin necesidad de parsear y verificar por separado el mapa
interno del JSON (que además ya queda cubierto indirectamente: el JSON
completo, incluido ese mapa, sigue teniendo su propia suma verificada como
archivo). Se agregó la prueba unitaria
`test_svg_covered_and_tampering_detected` en
`test_campaign_checksums.py`, que reproduce el mismo mutation test del
evaluador (cambiar un color) y confirma que ahora sí se detecta. Se
regeneró `experiments/paso8/resultados-reales/correctiva-20260905-final-v2/checksums.txt`
con las 5 sumas de los SVG, verificadas contra los mismos hashes que ya
había calculado el generador corregido del punto 2 (coincidencia exacta,
confirmando que "las sumas están alineadas con el manifiesto general", tal
como pedía la guía original).

## 4. Datos crudos de medición sin ninguna suma

El evaluador mutó `oracle-2pc.json` y `trace/checkout.json` y ningún
verificador existente lo detectó, porque ninguno de los dos scripts de
checksums cubría estas ubicaciones:

- `docs/experimentos/resultados/iso25010/2026-09-04T08-44-12/trace/`: 9
  JSON + 7 `.headers` de trazas HTTP reales, más `environment.txt` (17
  archivos).
- `experiments/paso7/evidence/`: `oracle-2pc.json`, `oracle-saga.json`,
  `compatibility-case-bank.json`, `transaction-case-bank.json` (4
  archivos).
- `experiments/paso8/resultados/` (distinto de `resultados-reales/`, que sí
  cubre `campaign_checksums.py`): `metadata.json`,
  `compatibilidad_resumen.json`, `boxplot_latencia_p95.svg`,
  `boxplot_throughput.svg` (4 archivos).
- `docs/experimentos/resultados/resumen.json` (1 archivo).

Total 26 archivos. Se creó `scripts/check_raw_evidence_checksums.py`
(commit `915183f`), con su propio manifiesto
(`docs/experimentos/resultados/checksums-evidencia-adicional.sha256`) y su
propia suite de pruebas
(`scripts/test_check_raw_evidence_checksums.py`): detección de alteración,
archivo faltante, archivo nuevo sin suma, symlinks rechazados, y
equivalencia CRLF/LF — reproduciendo el mismo patrón de pruebas que ya
usan `check_data_checksums.py` y `campaign_checksums.py`. Cableado a
`.github/workflows/data-integrity.yml` como dos pasos nuevos. No se tocó
ni se modificó ninguno de los dos verificadores existentes.

## Verificación final

- Los 4 comandos de integridad corren en verde de forma local:
  `check_data_checksums.py`, `campaign_checksums.py`,
  `check_raw_evidence_checksums.py`, y las 3 suites de tests unitarias
  correspondientes (24 pruebas en total entre las tres).
- CI ("Integridad de datos" y "CI") en verde tras cada uno de los 6 commits
  de esta sesión (`c10f43b`, `3ee51b4`, `3f969f9`, `0aeff49`, `9e2324a`,
  `915183f`).
- Los 9 archivos `.csv`/`.tsv` con BOM que quedaban en todo el repositorio
  (más allá de los 23 ya cubiertos en la sección 1) se identificaron con un
  barrido byte a byte (`git ls-files` + lectura de los primeros 3 bytes de
  cada archivo, no con `git grep -P`, que en este entorno no soporta de
  verdad sintaxis PCRE pese a no dar error) y se corrigieron: 3 de
  `punto17-observabilidad-gateway/` (mismo origen que la sección 1,
  `5a995b5`) y 6 que nacieron con BOM desde su único commit de creación,
  sin corrupción de contenido más allá del BOM
  (`comparativa-planes.csv`, `bitacora-tiempos.csv`,
  `mediciones.csv` ×2, `tiempo-reintegracion.csv`, `resumen.csv`;
  commit `3f969f9`). 3 + 6 = 9; sumados a los 20 de `tests/load/results`
  de la sección 1, coinciden exactamente con el "29/79 CSV con BOM" que
  reportó el evaluador para todo el repositorio.

## Conclusión

Los cuatro bloqueos explícitos del evaluador quedan resueltos con causa
raíz corregida, no con manifiestos regenerados sobre datos alterados: los
23 CSV de carga se restauraron a su contenido real verificado contra el
blob de git anterior a la corrupción (o se regeneraron con datos frescos
reales), el generador de sumas de metodología ya no depende de la
plataforma, la alteración de un SVG ahora se detecta en CI, y los 26
archivos de evidencia cruda que antes no tenían ninguna suma (trazas ISO,
oráculos de paso 7, JSON de paso8/resultados, resumen.json) ahora la
tienen, con su propia prueba de mutación que confirma la detección.

## Pendiente

- No se auditó exhaustivamente si existen más ubicaciones de datos crudos
  fuera de las cuatro identificadas por el evaluador y las tres
  adicionales encontradas en el barrido de BOM; el alcance de este cierre
  se limitó a responder punto por punto a los bloqueos explícitos.
- La cobertura de `check_raw_evidence_checksums.py` es una lista fija de
  directorios (no un descubrimiento automático de "toda evidencia cruda
  nueva que aparezca en el futuro"); si el equipo agrega una nueva
  ubicación de datos crudos fuera de `tests/load/results`,
  `experiments/paso8/resultados-reales/*` y las cuatro rutas de este
  script, quedará sin cobertura hasta que alguien la agregue
  explícitamente.
