# Documento acumulativo y reproducibilidad — Pasos 13 y 14

El estado de las correcciones y los paquetes pendientes por recibir se detalla en
[Cierre documental de las observaciones](CIERRE-DOCUMENTAL-OBSERVACIONES.md),
actualizado el 7 de septiembre de 2026. `CIERRE-PASO13.md` conserva el corte histórico.

La versión de cierre es `PFC4.tex` y su salida `PFC4.pdf`. El contenido de cierre se
integró en los nombres originales. Las localizaciones de código y evidencia se
revalidaron en `d7a00ccf6d8ac5def08ff52f7d2880f869667b0b`; las incorporaciones documentales
posteriores se identifican en el registro de cambios del propio informe.

La revisión documental de Observaciones 2 (2 de septiembre de 2026) incorpora
`registro-cambios.tex` y `trazabilidad-temas.tex` mediante `\input`; ambos archivos
deben acompañar a la fuente principal. La tabla de temas fija sus 27 localizaciones
al commit evaluado `d7a00ccf6d8ac5def08ff52f7d2880f869667b0b` y conserva el inventario
en `cierre/trazabilidad-temas.csv`. La tabla de deuda distingue esfuerzo de
resolución, coste de arrastre y evidencia pendiente. La fuente vigente no sustituye
datos experimentales ni estados de issues. Las consultas
preparadas para ampliar la bibliografía están en `busquedas-2pc-saga.md` y no
constituyen por sí mismas referencias. La ampliación del 3 de septiembre incorpora
cinco fuentes sobre 2PC y Saga mediante `estado-arte-2pc-saga.tex`, que también debe
acompañar a la fuente principal. La bibliografía contiene veinte fuentes académicas
y tres referencias normativas o éticas.

## Compilación exacta

Desde la raíz del repositorio, con TeX Live y Biber instalados:

```powershell
cd docs/entrega4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
biber PFC4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
```

No usar BibTeX para esta versión. La bibliografía es `../entrega3/referenciasPFC.bib`, compartida con las entregas anteriores; se conservaron las entradas históricas no sustituidas. Se requieren `UteqLogo.png`, las imágenes relativas en `img/`, las evidencias visuales de `../../release/screenshots/` y las figuras de `cierre/`.

Alternativa con la imagen TeX Live ya disponible, desde la raíz del repositorio en PowerShell:

```powershell
docker run --rm --network none --mount "type=bind,source=$($PWD.Path),target=/work" -w /work/docs/entrega4 texlive/texlive:latest sh -c "pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex && biber PFC4 && pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex && pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex"
```

La compilación no inicia TiendaTech ni ejecuta experimentos. El cierre se compiló
con TeX Live 2026 y Biber. La comprobación del Paso 14 resolvió la imagen local
`texlive/texlive:latest` como
`sha256:8957c916b8160049f89c24d362a6d86c09d8a04095acde37e88404c4afed85b4`;
se conserva el identificador porque la etiqueta `latest` es mutable.

## Reproducción de extremo a extremo

Los siguientes comandos parten de una clonación limpia y no requieren levantar los
microservicios. Se validan con CPython 3.11.1; `run_paso8.py` y el cuaderno usan
solo la biblioteca estándar. Matplotlib 3.9.0 está fijado para las figuras PNG.

```powershell
git clone https://github.com/JoseLozanoMorales/TiendaTech.git
cd TiendaTech
python -m venv .venv-repro
.\.venv-repro\Scripts\python -m pip install --disable-pip-version-check matplotlib==3.9.0

# Banco, generador, carga, inyector y oráculo
Push-Location experiments/paso7
..\..\.venv-repro\Scripts\python -m unittest -v test_coordination_lab.py
Pop-Location

# Validación reproducible de la campaña correctiva publicada
.\.venv-repro\Scripts\python experiments/paso8/analyze_real_results.py experiments/paso8/resultados-reales/correctiva-20260905-final-v2/experimento_real_crudo.csv --output experiments/paso8/resultados-reales/correctiva-20260905-final-v2/analisis-reproducido
.\.venv-repro\Scripts\python experiments/paso8/analyze_corrective_comparison.py experiments/paso8/resultados-reales/correctiva-20260905-final-v2/experimento_real_crudo.csv --output experiments/paso8/resultados-reales/correctiva-20260905-final-v2/analisis
.\.venv-repro\Scripts\python experiments/paso8/plot_corrective_results.py experiments/paso8/resultados-reales/correctiva-20260905-final-v2/analisis

# Cuaderno de análisis y figuras del PDF
.\.venv-repro\Scripts\python experiments/paso8/execute_notebook.py
.\.venv-repro\Scripts\python docs/entrega4/cierre/generar_figuras.py

# Documento acumulativo
Set-Location docs/entrega4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
biber PFC4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
```

La campaña correctiva ya se ejecutó: 120 corridas, 24 condiciones y cinco repeticiones, con 60 segundos de calentamiento y 300 segundos de medición. Los comandos anteriores regeneran la validación, las comparaciones y la gráfica desde el CSV crudo; la ejecución completa requiere el stack y el banco descritos en `experiments/paso8/README-experimento-real.md`.

La duración se mide desde el primer `python -m unittest` hasta la última pasada de
pdfLaTeX. La descarga inicial de dependencias no se incluye porque depende de la red;
sus versiones sí quedan fijadas en los comandos. La carpeta `.repro-paso14/` es salida
temporal y puede eliminarse después de comparar su estructura y los invariantes con
`experiments/paso8/resultados/`.

### Resultado de la comprobación

El 1 de septiembre de 2026 se ejecutó el procedimiento sobre una clonación local
aislada del árbol candidato, con CPython 3.14.3 (compatible con la referencia 3.11.1)
y TeX Live 2026. Las tres pruebas unitarias aprobaron; la matriz produjo 120 corridas,
el oráculo aprobó 120/120 y no observó inconsistencias. El procesamiento completo,
incluidas las figuras y la compilación de 39 páginas, tomó **623,55 segundos
(10 min 23,55 s)**, por debajo del límite de quince minutos. La instalación o descarga
de herramientas quedó fuera del cronómetro por depender de la red.

## Figuras reproducibles

Las figuras PNG ya están incluidas. Para regenerarlas se requiere Python con Matplotlib (validado con 3.11.1):

```powershell
python docs/entrega4/cierre/generar_figuras.py
```

El script solo lee los CSV incluidos. No genera mediciones, no cambia sus valores y no consulta servicios. Los diagramas C4 se generan directamente desde TikZ en el archivo principal.

## Evidencia y límites

- `cierre/experimento_crudo.csv` y `experimento_resumen.csv`: copias del Paso 8 en el commit de corte.
- `cierre/tiempos_resumen.csv`: copia de `resultados/tiempos_resumen.csv` del mismo commit.
- `cierre/issues-corte.json`: estado y última respuesta de los issues 16–78; 63 registros, 50 cerrados y 13 abiertos. Tres abiertos son duplicados aparentes.
- `cierre/doi-verificados.json`: metadatos consultados en Crossref de las quince fuentes académicas seleccionadas. Se contrastaron título, autores y DOI; las normas y el código ético se citan mediante URL institucional.
- `cierre/bibliografia-2pc-saga.json`: metadatos y alcance de lectura de las cinco fuentes adicionales. La consulta de Daraghmi et al. se limita al resumen de autores; no se trasladan cifras del artículo.
- `experiments/paso8/resultados-reales/correctiva-20260905-final-v2/`: campaña correctiva, preflight, pilotos, rampa, CSV crudo, resúmenes, comparación y validación estructural.
- `docs/experimentos/resultados/iso25010/2026-09-04T08-44-12/`: carga estable, trazas y disponibilidad de una hora.
- Planes SQL: `docs/evidencias/resultados-planes-e4/comparativa-planes.csv` en el commit de corte.
- Tolerancia: `docs/evidencias/resultados-tolerancia-e4/mediciones.csv` y `tiempo-reintegracion.csv` en el mismo commit.
- Calidad: `docs/evidencias/cobertura/` y `docs/experimentos/resultados/iso25010/complejidad/summary.csv` del mismo commit.

Las cifras de cobertura son del alcance instrumentado, no de la totalidad de cada servicio. El banco SQLite es un piloto histórico. La campaña correctiva completó 120 corridas y confirmó 30275 checkouts. El oráculo retrospectivo sobre CockroachDB detectó 13210 órdenes con movimiento de inventario ausente o distinto; no encontró importes discordantes, descuentos duplicados ni stock negativo. La captura bancaria y la compensación cancelada siguen no verificables. La disponibilidad de una hora y la carga oficial tienen evidencia; la comparación con RAG, las firmas personales y la similitud inferior al 15 % no se acreditan.

Las conclusiones individuales proceden de los textos anteriores y se actualizaron; cada autor debe revisarlas antes de entregarlas como declaración personal. La memoria registra los pendientes, no los convierte en funcionalidades terminadas.

## Estado de publicación

El logo, las imágenes de `img/` y `release/screenshots/`, `cierre/`, los datos crudos, el banco experimental, el
cuaderno y `CITATION.cff` forman el paquete que debe quedar rastreado por Git.
Los archivos `PFC4v2.*` son respaldos locales y no son dependencias del documento
oficial.
