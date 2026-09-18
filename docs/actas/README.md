# Actas y reconstrucciones retrospectivas del periodo

Este directorio conserva las actas de coordinación del equipo. El acta del
4 de septiembre de 2026 se registró el mismo día. Las demás actas se
redactaron retrospectivamente el 15 de septiembre de 2026 y, por tanto, no se
presentan como registros contemporáneos.

## Criterio de evidencia

En las actas retrospectivas se distinguen tres clases de información:

1. **Hecho comprobado en Git:** autor, fecha, orden y contenido observable de
   commits o pull requests. Cada afirmación de esta clase debe citar el objeto
   verificable.
2. **Recuerdo del equipo:** existencia de la coordinación, asistencia,
   modalidad y decisiones conversadas. Git no demuestra por sí solo estos
   datos; necesitan ratificación posterior de las personas mencionadas.
3. **Reconstrucción documental:** relación razonada entre decisiones y
   resultados técnicos. No debe presentarse como conversación literal ni
   atribuir acuerdos que ninguna persona haya confirmado.

Una ventana de commits no equivale a la duración de una reunión. Cuando se
incluyen horas tomadas de Git se denominan **ventana de actividad comprobada**.

## Estado de las actas

| Fecha documentada | Tipo de registro | Redacción | Ratificación posterior |
|---|---|---|---|
| 2026-09-01 | Acta retrospectiva | 2026-09-15 | Andy y José pendientes |
| 2026-09-03 | Acta retrospectiva | 2026-09-15 | Jhinson y Jeremy ratificados |
| 2026-09-04 | Acta contemporánea | 2026-09-04 | Jhinson ratificado; Andy pendiente |
| 2026-09-07 | Acta retrospectiva | 2026-09-15 | Jhinson y Jeremy ratificados; Andy y José pendientes |
| 2026-09-10 | Acta retrospectiva | 2026-09-15 | Jeremy ratificado; Andy pendiente |
| 2026-09-11 | Acta retrospectiva | 2026-09-15 | Jhinson y Jeremy ratificados; Andy y José pendientes |
| 2026-09-12 | Acta retrospectiva | 2026-09-15 | Jhinson ratificado; Andy y José pendientes |
| 2026-09-13 | Acta retrospectiva | 2026-09-15 | Jhinson y Jeremy ratificados; José pendiente |

No hubo reuniones los días 14 y 15 de septiembre. Jhinson Aucatoma lo confirmó
retrospectivamente el 18 de septiembre de 2026. Los commits de esas fechas
corresponden a continuidad de trabajo individual o asincrónico y no justifican
crear actas de reunión. Esta declaración queda disponible para ratificación o
corrección por los demás integrantes durante la revisión del pull request.

## Protocolo de ratificación

La reconstrucción se somete mediante un pull request exclusivo. Cada persona
mencionada debe revisar las actas que le correspondan y dejar una aprobación o
un comentario inequívoco desde su propia cuenta de GitHub. Una fórmula válida
es:

> Confirmo que participé en la coordinación indicada y que las decisiones y
> responsabilidades que se me atribuyen reflejan lo acordado. Las horas de Git
> se entienden como actividad comprobada y no como duración de la reunión.

Una aprobación genérica del código no sustituye esta confirmación si no queda
claro qué actas y atribuciones fueron revisadas. Las discrepancias deben
corregirse antes de fusionar el pull request.

## Requisitos mínimos de cada acta retrospectiva

- fecha real de redacción y declaración explícita de reconstrucción;
- participantes declarados, sujetos a ratificación;
- modalidad y duración solo si las confirma un participante;
- ventana de actividad Git separada de la duración;
- decisiones, responsables y estado o pendiente;
- enlaces a commits, pull requests u otras fuentes comprobables;
- sección de validación posterior con la evidencia de ratificación.
