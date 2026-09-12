# Integración y comprobación del candidato — 12 de septiembre de 2026

## Procedencia

Commit de fuentes publicado: `12580f84782b50510c647b8f91d5107d10fd4e4d`. Incluye tarea 1 (`3d0c1ef`), tarea 2 (`a406fd4`), tarea 3 (`c05c02a`, integrada por `aaf6d6c`) y compilación CI (`12580f8`). Se integró por avance directo sin sobrescribir los cambios locales. Se creó un clon con historial completo mediante `git clone --no-local --no-hardlinks` del repositorio actualizado en `output/integracion-final-20260912/clon`. Estado inicial vacío según `git status --porcelain`.

El PDF nuevo y este registro aún no tienen commit de entrega. Los resultados remotos de abajo pertenecen al candidato, no a un commit futuro. Los registros anteriores se conservan como históricos.

## Reproducción y compilación

```powershell
git clone https://github.com/JoseLozanoMorales/TiendaTech.git TiendaTech-validacion
cd TiendaTech-validacion
git checkout --detach 12580f84782b50510c647b8f91d5107d10fd4e4d
git status --porcelain
python scripts/validate_evidence_refs.py
cd docs/entrega4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
biber PFC4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
```

Las cuatro órdenes se ejecutaron desde `docs/entrega4` en el clon aislado mediante Docker sin red. Imagen TeX Live 2026: `sha256:8957c916b8160049f89c24d362a6d86c09d8a04095acde37e88404c4afed85b4`. Todas terminaron con código 0. Los hashes de fuentes están en `verificacion.json`; dos incluidos tienen distinta normalización CRLF/LF entre el directorio original y el clon, pero contenido idéntico al normalizar finales de línea.

Resultado: **59 páginas reales**, cero errores, citas/referencias indefinidas y cajas desbordadas. Persisten avisos no fatales de sustitución de fuente y traducción de `artno`. Se renderizaron y revisaron visualmente las 59 páginas, incluyendo índices, figuras, tablas y bibliografía; no se detectaron recortes ni solapamientos.

PDF: `docs/entrega4/PFC4.pdf`. SHA-256: **`30dd00cd67c6d3f0314949e2ee2b1a0b3f7d333f2d19f711d576704744c7b84d`**. No se atribuye la compilación al commit de entrega aún inexistente. Copia anterior conservada en el directorio local de trabajo.

## Comprobaciones documentales

- Validador en clon limpio: **69 referencias válidas, 65 excepciones explícitas, 0 fallos**, cinco fuentes. Las excepciones son discusiones/ejecuciones fuera de la comprobación Git; no se presentan como archivos validados.
- Tres controles negativos: ruta inexistente en macro, enlace blob y matriz ISO. Cada uno devolvió código 1 e identificó la ausencia. Se restauraron los originales byte a byte. Véase `controles-negativos.json`.
- Los tres enlaces web abrieron con HTTP 200 sin autenticación. Los JSON públicos coinciden byte a byte con sus blobs; el raw del CSV devolvió 503, pero su fila exacta se comprobó en el HTML público. Evidencia: `enlaces-publicos.json`. Los commits citados son antecesores de `origin/main`.
- Seguridad: la matriz cita el CSV del 11 de septiembre. Ocho rutas distintas coinciden con el test, con método GET y HTTP observado/esperado 401. No se generaron nuevos datos. El log remoto del gateway del candidato confirma 11 pruebas, cero fallos, errores u omitidas; es una ejecución posterior y distinta de la fecha original declarada en el CSV.

## Resultados remotos del candidato

Todos corresponden al SHA de fuentes indicado arriba. Consultados con `gh run list`, `gh run view` y descarga del artefacto `reports-ventas-service`.

| Flujo | Run | Resultado |
|---|---|---|
| CI-CD quality gate | [34707196595](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34707196595) | success; ventas-service y gateway aprobados |
| CI / trabajo documental | [34707196586](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34707196586) | success; validación y compilación documental aprobadas |
| Publicación GHCR | [34707282576](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34707282576) | success; las ocho imágenes aprobadas |
| Despliegue a producción | [34707546873](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34707546873) | failure: nombre de contenedor `tiendatech-tiendatech-productos-1` ya ocupado en EC2 |
| Despliegue anterior | [34707329314](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34707329314) | failure; causa de ese intento no auditada |

Artefacto remoto de ventas: JaCoCo **80/84 líneas = 95,24 %**, superior al umbral del 70 % en el alcance instrumentado. `FacturaServiceTest`: 6 pruebas; `FacturaControllerTest`: 1; ambas con cero errores/fallos. No equivale a cobertura de toda la aplicación. No se repitieron localmente esas pruebas: se inspeccionaron los XML del run identificado. La publicación de imágenes tuvo éxito independientemente del fallo posterior de despliegue; no se modificó configuración operacional.

## Estado por observación

| Observación | Archivos / evidencia | Estado |
|---|---|---|
| Tres enlaces web E2 | PFC4.tex; enlaces-publicos.json | Verificados para candidato |
| Seguridad E2 | Matriz ISO y CSV real; gateway remoto | Verificada para candidato |
| Validador E2 | scripts/validate_evidence_refs.py; validador.txt; controles-negativos.json | Aprobado dentro de los formatos cubiertos |
| Facturación | FacturaServiceTest y FacturaControllerTest; gate.json | Cobertura remota aprobada para candidato |
| PDF integrado | PFC4.pdf; compilacion.log; verificacion.json | Regenerado, pendiente de publicación |
| Imágenes | imagenes.json | Ocho jobs aprobados para candidato |
| Cierre del commit final | Nuevo PDF y registro | Pendiente |

## Pendiente de autorización y publicación

Publicar el PDF y este registro mediante el procedimiento autorizado del equipo; después consultar CI, quality gate, ventas, job documental e imágenes del **SHA final exacto**. No trasladar las aprobaciones del candidato al commit nuevo. La reserva de integración global del manuscrito se mantiene mientras falta ese paso. El fallo de despliegue se informa al responsable de operaciones. No se declara el cierre global ni una nota máxima antes de completar la comprobación remota final.
