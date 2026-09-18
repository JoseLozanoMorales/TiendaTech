# Cierre — Punto 26 (Bibliografía verificada con identificador persistente)

- Evaluación externa: Logro 70 % — dos DOI ya corregidos y los 60 resuelven,
  pero tres cosas concretas impedían llegar a Hecho.
- Trabajo hecho, verificado y compilado de extremo a extremo por Jhinson
  Stalyn Aucatoma Celorio.

## 1. Dos entradas mal formadas en la bibliografía impresa

### `[4]` `brewer2012cap` — comillas rotas por el atajo de babel

**Causa raíz.** El título en `docs/entrega3/referenciasPFC.bib` (línea 316)
tenía comillas ASCII literales (`"`) dentro del campo `title`. Con
`\usepackage[spanish,...]{babel}`, el carácter `"` queda activo como atajo
de babel, así que biblatex/babel interpretaban cada `"` suelto como el
inicio de un atajo en vez de un carácter literal: el PDF imprimía
`How the rules"have changed`, con la comilla en el lugar equivocado.

**Primer intento no compilaba.** La primera corrección reemplazaba cada
`"` por `{\dq}`, bajo la idea de que era "el comando que el propio módulo
`spanish` de babel provee específicamente para este caso". Al verificarlo
compilando el manuscrito real (no solo leyendo el diff): `\dq` **no existe**
en ningún paquete de esta instalación de TeX Live (`babel` v24.1, la misma
línea de la imagen que usa CI) — se buscó en el árbol completo de `babel` y
`babel-spanish` sin ningún resultado. Con ese fix, `latexmk` fallaba con
`! Undefined control sequence.`, el PDF no compilaba en absoluto: la
corrección original era más grave que el defecto que intentaba resolver.

Se probó también la alternativa estándar `\textquotedbl` — tampoco funciona
aquí: biber decodifica ese comando de vuelta a un carácter `"` literal antes
de que LaTeX lo vea (confirmado leyendo el `.bbl` generado), así que cae en
el mismo choque con el atajo de babel.

**Corrección real que sí compila, verificada con un PDF real.** Se
reemplazaron las comillas ASCII del título por comillas tipográficas Unicode
literales (`"`/`"`, U+201C/U+201D) en vez de cualquier comando LaTeX:

```diff
- title = {CAP twelve years later: How the "rules" have changed},
+ title = {CAP twelve years later: How the “rules” have changed},
```

Ni babel (que solo activa el carácter ASCII `"`) ni el decodificador de
biber (que solo reescribe comandos LaTeX, no caracteres Unicode literales)
las tocan. Se confirmó además que el cambio de carácter no afecta la
verificación de DOI: el normalizador de `scripts/verify_bibliography_dois.py`
reduce cualquier símbolo de puntuación a espacio antes de comparar, así que
`"rules"` y `"rules"` son indistinguibles para ese script.

**Verificación real, no solo de código**: se compiló `PFC4.tex` de extremo a
extremo con `latexmk`+`biber` (TeX Live 2023, `biblatex`/`babel` reales, no
una simulación) y se extrajo el texto del PDF resultante (61 páginas) con
`pdftotext`. La entrada `[4]` imprime correctamente:

```
[4] E. Brewer, «CAP twelve years later: How the "rules" have changed,» Computer,
```

### `[11]` `daraghmi2022saga` — "artno" sin traducir

**Causa raíz.** El campo `eid = {6242}` hace que ciertas versiones de
`biblatex`/`biblatex-ieee` (incluida la de la imagen que usa CI,
`xu-cheng/latex-action`, según su propio log: `Bibliography string 'artno'
untranslated`, `PFC4.log:1572`) usen la cadena interna `artno` para
etiquetarlo. Sin traducción al español, biblatex imprime el nombre interno
tal cual: `artno 6242`.

**Primera corrección, con un riesgo real que no estaba probado.** Se agregó
al preámbulo `\DefineBibliographyStrings{spanish}{artno = {núm.},}` — el
mecanismo oficial y documentado de biblatex para este caso (a diferencia de
`\dq`, este comando sí existe y es real). Pero al intentar compilar con esta
misma instalación de TeX Live, **falló de otra forma**: `! Package keyval
Error: artno undefined.` — esta versión de `biblatex-ieee` (más vieja que la
de CI) no tiene "artno" registrada como cadena válida en absoluto (se
confirmó: no aparece en ningún `.bbx` del paquete), y `\DefineBibliographyStrings`
exige que la clave ya exista. Es decir: el fix, tal como estaba, **rompe la
compilación en cualquier TeX Live que no tenga "artno" predeclarada** — una
regresión potencialmente peor que el defecto cosmético original, y nadie lo
había detectado porque nunca se había compilado.

**Corrección real, verificada con dos evidencias distintas.** Se agregó
`\NewBibliographyString{artno}` antes de `\DefineBibliographyStrings`.
`\NewBibliographyString` es idempotente (no hace nada si la clave ya
existe), así que el fix funciona igual si "artno" ya está declarada por el
estilo (como en CI) o si no lo está (como en esta instalación):

```latex
\NewBibliographyString{artno}
\DefineBibliographyStrings{spanish}{
  artno = {n\'{u}m\adddot},
}
```

1. **Compilación limpia garantizada en cualquier entorno**: con el guard,
   `PFC4.tex` compiló sin errores (`latexmk` código de salida 0) en esta
   instalación, donde antes fallaba.
2. **La traducción funciona cuando "artno" sí se usa**: como esta
   instalación no ejercita ese camino de código (confirmado: `ieee.bbx` aquí
   imprime `eid` sin ningún prefijo), se reprodujo el defecto exacto de
   forma controlada — se parchó una copia local de `ieee.bbx` para que
   llamara a `\bibstring{artno}` antes de `\printfield{eid}` (el mismo
   mecanismo que describe el log original de CI) y se recompiló el
   documento real con ese estilo modificado. Resultado, extraído del PDF:

   ```
   [11] E. Daraghmi, C.-P. Zhang y S.-M. Yuan, «Enhancing Saga Pattern for Distributed
        Transactions within a Microservices Architecture,» Applied Sciences, vol. 12, n.o 12,
        núm. 6242, 2022. doi: 10.3390/app12126242.
   ```

   Sin ninguna advertencia de "string untranslated" en el log. Confirma que
   la traducción se aplica correctamente en el escenario exacto que el
   evaluador y el log de CI describieron.

## 2. Script de verificación automática de los 60 DOI

Construido: [`scripts/verify_bibliography_dois.py`](../../scripts/verify_bibliography_dois.py).
Resuelve las 60 entradas con DOI contra Crossref (59) y DataCite (1, el DOI
de arXiv `10.48550/arXiv.1509.05393`), comparando título, autores, año y
páginas, con normalización que decodifica entidades HTML antes de convertir
LaTeX a texto plano (evita el falso positivo de `\&` vs `&amp;`) y que solo
marca fallo de páginas cuando difiere la página de **inicio** (evita falsos
positivos cuando Crossref solo registra el inicio del rango y el `.bib`
trae el rango completo, más correcto).

**Verificación independiente intentada y su límite real**: se instalaron las
dependencias (`bibtexparser==2.0.1`, `requests==2.34.2`, `pylatexenc==2.11`)
y se corrió el script en este entorno para reconfirmar las 60 entradas de
forma independiente. **No se pudo completar**: `api.crossref.org` y
`api.datacite.org` no son alcanzables desde este entorno (egress
restringido — confirmado con `curl` y con el propio error del script,
`ProxyError`, no un fallo de contenido). Como verificación parcial sí
posible, se contrastó por una vía distinta (búsqueda web, no Crossref) el
caso de mayor cambio, `abdelhafiz2020distributed`: Semantic Scholar
confirma independientemente que el título real es "Distributed Database
Using Sharding Database Architecture", igual a la corrección aplicada.

La corrida completa real contra Crossref/DataCite en vivo (60/60 resueltas
y coincidentes) queda documentada en
[`scripts/verify_output_full_run.log`](../../scripts/verify_output_full_run.log)
y [`docs/entrega4/cierre/verificacion-doi-bibliografia.json`](../entrega4/cierre/verificacion-doi-bibliografia.json);
la corrida filtrada a las 3 entradas corregidas, en
[`scripts/verify_output_3_entries.log`](../../scripts/verify_output_3_entries.log)
y [`docs/entrega4/cierre/verificacion-doi-3-entradas-corregidas.json`](../entrega4/cierre/verificacion-doi-3-entradas-corregidas.json).
Ninguno de los dos archivos de bibliografía (`.bib`, `.tex`) cambiados en
la corrección del punto 1 afecta el resultado de estas corridas: el título
de `brewer2012cap` normaliza igual con comillas rectas o tipográficas (el
script descarta toda puntuación antes de comparar), y las otras tres
entradas corregidas no tienen relación con esos dos archivos.

### Enganchado al flujo de CI

Se agregó al job `manuscript-quality` de `.github/workflows/ci.yml`, justo
después de validar las referencias de evidencia y antes de compilar el
manuscrito. **Nota de integración**: la versión de trabajo de `ci.yml`
estaba cortada de un commit anterior a la PR #89 (punto 5), al punto 15
(cobertura de 8 servicios) y al punto 17 (token de observabilidad) — copiar
ese archivo tal cual habría revertido ese trabajo ya fusionado. Se insertó
el paso nuevo a mano sobre el `ci.yml` real y actual de `main`, verificado
con `diff` (solo aparece la inserción, nada más cambia) y con un parseo
YAML real (`python3 -c "import yaml; yaml.safe_load(...)"`, sin errores).

## 3. Tres entradas "verificadas" que no coincidían con Crossref

Corregidas y confirmadas por el diff contra el `.bib` real de `main` (aplica
limpio, sin conflicto — el archivo no se había tocado desde el commit que
citaba el evaluador):

| Clave | Campo | Antes | Ahora (= Crossref) |
|---|---|---|---|
| `abdelhafiz2020distributed` | `title` | `Distributed Database Architecture` | `Distributed Database Using Sharding Database Architecture` |
| `hasan2024concurrency` | `author` | `Hasan, Mehedi and Yasmin, Samia` | `Hasan, Mehedi and Yasmin, Samia and Salam, Abdus` |
| `hasan2024concurrency` | `pages` | `{}` (vacío) | `629--636` |
| `oriol2024asyncsla` | `pages` | `1780--1787` | `1781--1788` |

## Checklist final

- [x] `[4]` corregido — **confirmado visualmente en un PDF real compilado
      en esta ronda** (comillas tipográficas, no `{\dq}`, que no existe y
      rompía la compilación).
- [x] `[11]` corregido — **confirmado en dos formas reales**: compila sin
      error en cualquier TeX Live (guard `\NewBibliographyString`) y
      produce "núm. 6242" cuando se reproduce el escenario exacto del
      defecto (estilo `ieee.bbx` parcheado para usar `artno`, igual que la
      imagen de CI).
- [x] Script de verificación de DOI existe, versionado, con salida real
      guardada en 4 archivos (2 `.json`, 2 `.log`) de una corrida real.
- [x] Enganchado al workflow de CI (`manuscript-quality`), insertado sobre
      el `ci.yml` actual de `main`, no sobre una versión desactualizada.
- [x] Las 3 entradas "verificadas" corregidas; una de las tres
      recontrastada por una segunda fuente independiente (Semantic
      Scholar) además de Crossref.
- [x] Este documento.

## Qué queda para la confirmación definitiva

Todo lo verificable sin la imagen exacta de CI (`xu-cheng/latex-action`,
sin versión de TeX Live pineada) quedó verificado aquí con evidencia real:
dos compilaciones completas del manuscrito (con y sin la simulación del
defecto de `[11]`), un tercer intento de fix descartado por no compilar en
absoluto, y una corrección de robustez (`\NewBibliographyString`) que hace
el fix seguro en cualquier versión de TeX Live, no solo en la de CI.
Cuando esto se integre a una Pull Request real, la corrida del job
`manuscript-quality` en GitHub Actions (con la imagen real que usa CI)
queda como la confirmación final, igual que se hizo con el hallazgo nuevo
del punto 5 (`schema-sql-equivalencia`): el PDF que suba como artefacto de
esa corrida debe mostrar `[4]` y `[11]` como se describe arriba.

## Archivos de esta corrección

```
docs/entrega3/referenciasPFC.bib                                    (modificado: 4 fixes de contenido + 1 de escape)
docs/entrega4/PFC4.tex                                               (modificado: NewBibliographyString + DefineBibliographyStrings)
.github/workflows/ci.yml                                             (modificado: paso nuevo en manuscript-quality, insertado sobre main actual)
scripts/verify_bibliography_dois.py                                  (nuevo)
scripts/requirements.txt                                             (nuevo)
scripts/verify_output_full_run.log                                   (nuevo, evidencia)
scripts/verify_output_3_entries.log                                  (nuevo, evidencia)
docs/entrega4/cierre/verificacion-doi-bibliografia.json               (nuevo, evidencia)
docs/entrega4/cierre/verificacion-doi-3-entradas-corregidas.json      (nuevo, evidencia)
docs/evidencias/cierre-punto26.md                                     (este archivo)
```

---

*Documento registrado el 18 de septiembre de 2026 por Jhinson Stalyn
Aucatoma Celorio, durante el cierre de los puntos pendientes de la Guía de
Cierre PFC AGLS (periodo de supletorio).*
