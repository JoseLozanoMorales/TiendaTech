# Cierre — Punto 46 (Fijación de finales de línea para verificación reproducible)

- Fecha: 2026-09-17
- Responsable: Jhinson Aucatoma (equipo AGLS)
- Estado previo: Modificar (Logro 65%, Puntos 3,25)

## Qué pedía la guía (cita textual)

> Qué impide llegar a Hecho. Hay 7 archivos crudos de medición versionados
> en CRLF que contradicen `* text=auto eol=lf`, sin renormalizar. El patrón
> `*.tsv`, que no selecciona nada, sigue en el guardián, y el guardián no
> cubre la regla global: JSON de veredictos y código fuente con CRLF pasan
> en verde. La «normalización» del 15/09 corrompió 23 CSV crudos (LF
> duplicados y BOM), y ningún control la detecta. El generador del análisis
> estadístico sigue produciendo salidas y sumas dependientes de Windows.

Respondo cada uno de los cuatro bloqueos, en orden.

## 1. Los 7 `.headers` versionados en CRLF sin renormalizar

**Corregido en `75f2d76`.**

`docs/experimentos/resultados/iso25010/2026-09-04T08-44-12/trace/*.headers`
nunca había pasado por `git add --renormalize` desde que existe la regla
`* text=auto eol=lf`, así que sus blobs seguían con el CRLF original pese a
que el atributo ya decía `eol=lf` — exactamente lo que reportó el evaluador
con `git ls-files --eol`.

Verificación aplicada (no solo `git diff`, dado el `core.autocrlf=true`
local que ya causó falsos positivos en esta sesión — ver punto 22):

- `git ls-files --eol` antes: `i/crlf` en los 7 archivos. Después de
  `git add --renormalize`: `i/lf` en los 7.
- `git diff --cached --ignore-space-at-eol --stat` sobre los 7 archivos:
  vacío — confirma que el único cambio es el fin de línea, ningún byte de
  contenido real se movió.
- `scripts/check_raw_evidence_checksums.py` (que ya cubre estos 7 archivos
  desde `915183f`) siguió verificando las 26 sumas sin tocar el manifiesto:
  el script ya normalizaba `\r\n`→`\n` al calcular el hash, así que
  renormalizar el índice a LF no cambia el hash calculado.

## 2. El patrón `*.tsv` muerto y el guardián que no cubre la regla global

**Corregido en `9134419`.**

El job `csv-line-endings` de `ci.yml` (limitado a `'*.csv' '*.tsv'
'docs/evidencias/*-e4.txt'`, con `*.tsv` sin seleccionar nada) fue
reemplazado por completo por un job `line-endings` que usa el propio
clasificador de git (`git ls-files --eol | grep -E 'i/(crlf|mixed)'`) sobre
**todo el repo**, excluyendo solo `*.cmd` (la única excepción intencional
declarada en `.gitignore`/`.gitattributes`, `mvnw.cmd` con `eol=crlf`).

Esto no es una lista de extensiones más larga — es estructuralmente
imposible que vuelva a tener un patrón que seleccione cero archivos, porque
no depende de patrones de ruta en absoluto. Cubre por diseño los dos
ejemplos que citó el evaluador como blancos que pasaban en verde
(`validacion.json`, `src/services/cart.ts`): cualquier archivo trackeado
por git con CRLF/mixed en el índice, sin importar extensión ni carpeta,
hace fallar el job.

Verificación: antes de implementarlo, corrí en seco
`git ls-files --eol | Select-String "i/crlf|i/mixed" | Where-Object { $_ -notmatch '\.cmd$' }`
contra el estado del repo — vacío, confirmando que el repo ya estaba
100% limpio (salvo los 7 `.headers` del punto 1, resueltos en el commit
anterior) antes de activar el guardián nuevo, así que no generaría falsos
positivos al entrar en vigor.

Se descartó deliberadamente una implementación con `git grep -l $'\r'`
(búsqueda cruda de bytes) porque genera falsos positivos contra contenido
legítimo que embebe un `\r` sin ser un problema de codificación del
archivo (confirmado con una prueba real: 29 archivos con `\r` embebido que
`git ls-files --eol` clasificó correctamente como `i/lf` — scripts que
construyen texto con terminaciones CRLF a propósito, reportes que citan
encabezados HTTP crudos).

## 3. La corrupción del 15/09 (23 CSV, LF duplicado + BOM) sin control que la detecte

Esto tiene dos partes, y las trato honestamente por separado.

**La corrupción histórica en sí** ya está completamente restaurada y
verificada con evidencia de hash contra el árbol de git — ver el cierre del
punto 22 (`fd21000`), que restauró los 23 CSV afectados (commits `c10f43b`,
`3ee51b4`, `fdb16f6` para los datos crudos, `3f969f9` para el BOM adicional
encontrado en el barrido de todo el repo) y documentó los 4 bloqueos que
citó el evaluador para el punto 22 punto por punto.

**Que "ningún control lo detecte" hacia adelante** se cierra con dos
controles independientes, uno por cada mitad del patrón de corrupción:

- **BOM**: `scripts/check_no_bom.py` (`307960a`), guardián permanente en
  CI que escanea todo `git ls-files` buscando el BOM UTF-8 de 3 bytes al
  inicio de cualquier archivo — no depende de una lista de extensiones. Al
  activarlo por primera vez encontró y se corrigieron 18 archivos con BOM
  genuino que nadie había visto (`ede753b`), incluido el propio
  `.gitignore`.
- **Reescritura silenciosa de sumas**: el rasgo que definió la corrupción
  del 15/09 no fue solo el byte alterado, fue que la suma se reescribió
  *en el mismo commit*, sin que nada lo señalara. `scripts/check_checksum_rewrites.py`
  (`3d5927a`) compara cada manifiesto de checksums contra su versión en el
  commit padre; si una entrada que ya existía cambió de hash, exige un
  trailer explícito `Checksum-Rewrite: <manifiesto>:<archivo>` en el
  mensaje del commit, o CI falla. Verificado contra el escenario histórico
  real: `python scripts/check_checksum_rewrites.py --base 62d37e4^ --ref 62d37e4`
  detecta correctamente las reescrituras sin justificar de ese commit
  exacto (24 entradas, entre ellas los 4 CSV de la campaña del 04/09 que
  citó el evaluador para el punto 42) — prueba de que este control habría
  bloqueado esa corrupción si hubiera existido entonces.

**Límite honesto**: si un archivo *nuevo* (no una reescritura de una
entrada existente) tuviera líneas en blanco duplicadas pero sin BOM,
ninguno de los dos controles lo señalaría específicamente por esa forma —
solo lo detectaría si su contenido no coincide con lo que declara el
manifiesto (cualquier verificador de checksums ya lo hace), o si más
adelante alguien intenta reescribir esa suma sin justificar por qué. No
hay un tercer guardián dedicado a "detectar líneas en blanco duplicadas"
como patrón propio; no se afirma que exista.

## 4. El generador de análisis estadístico dependiente de Windows

**Corregido en `5e5c8c6`.**

`experiments/paso8/analyze_corrective_results.py`: `write_csv` usaba
`csv.DictWriter` con `lineterminator` por defecto (`\r\n`, RFC 4180), y
`write_boxplot_svg` usaba `Path.write_text` sin `newline=`, que en Windows
traduce cada `\n` escrito a `os.linesep` (`\r\n`). El archivo quedaba con
CRLF real en disco al generarse, normalizándose a LF recién al hacer
`git add` vía el atributo `eol=lf` — el mismo patrón de bug que el resto de
este punto, solo que en la propia herramienta en vez de en datos ya
congelados.

Fix: `lineterminator='\n'` explícito en el `DictWriter`, `newline='\n'` en
`write_text`.

Verificado con una regeneración real (`--bootstrap-samples 20000`, mismos
parámetros que produjeron los datos ya commiteados): `git ls-files --eol`
confirma `w/lf` en los 8 archivos de salida recién escritos (antes habría
sido `w/crlf`); `git diff --stat` de esos 8 archivos da vacío — el
contenido filtrado por git ya coincidía con lo commiteado, por eso el bug
no se había manifestado todavía en los datos versionados, pero seguía
siendo un bug real que se habría manifestado en la próxima regeneración en
Windows sin este fix. `campaign_checksums.py` sigue verificando sin
cambios.

## Verificación final

- CI en verde para cada uno de los 6 commits de este punto:
  `75f2d76`, `9134419`, `ede753b`, `307960a`, `5e5c8c6` (y `3d5927a`,
  compartido con el punto 42).
- `git ls-files --eol` sobre el repo completo, hoy: cero entradas
  `i/crlf` o `i/mixed` fuera de la excepción intencional `*.cmd`.
- Los 4 controles nuevos/ampliados de esta sesión (`line-endings`,
  `check_no_bom.py`, `check_checksum_rewrites.py`, y el fix de
  `analyze_corrective_results.py`) están todos cubiertos por pruebas
  unitarias con mutation testing (alterar el dato → confirmar que el
  verificador correspondiente pasa de verde a rojo), no solo por
  inspección manual.

## Conclusión

Los cuatro bloqueos que citó el evaluador para el punto 46 están
corregidos con evidencia verificable: los 7 blobs CRLF renormalizados, el
guardián reemplazado por uno que cubre todo el repo sin patrones muertos,
la corrupción del 15/09 con dos controles preventivos independientes
(BOM + anti-reescritura) probados contra el propio commit histórico que
motivó la observación, y el generador de análisis ya no depende de la
plataforma donde corre.

## Pendiente

- El límite honesto señalado en el punto 3 arriba (líneas en blanco
  duplicadas sin BOM en un archivo *nuevo*, no una reescritura, no tiene
  un detector de forma dedicado).
- El control anti-reescritura (`check_checksum_rewrites.py`) usa
  `github.event.before` como base en CI, que es robusto para push directos
  a una rama (el flujo real de este repo hasta ahora) pero no fue
  ejercitado contra un flujo de pull request con varios commits squasheados
  ni contra un rebase forzado; no se afirma cobertura para esos casos sin
  haberla probado.
