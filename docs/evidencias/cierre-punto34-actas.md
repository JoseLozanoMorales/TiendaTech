# Cierre del punto #34 — actas de reunión

## Alcance

Esta evidencia responde a la observación sobre las actas del periodo de
septiembre de 2026. No intenta convertir actividad Git en reuniones. Separa
los hechos técnicos comprobables de la información reconstruida de memoria y
establece una ratificación posterior mediante cuentas individuales de los
participantes.

## Inventario

| Fecha | Documento | Naturaleza | Estado de ratificación |
|---|---|---|---|
| 2026-09-01 | `docs/actas/acta-2026-09-01.md` | Retrospectiva, redactada 2026-09-15 | Ratificada por Andy en PR #93 |
| 2026-09-03 | `docs/actas/acta-2026-09-03.md` | Retrospectiva, redactada 2026-09-15 | Ratificada por Jhinson y Jeremy |
| 2026-09-04 | `docs/actas/acta-2026-09-04.md` | Contemporánea | Ratificada por Jhinson |
| 2026-09-07 | `docs/actas/acta-2026-09-07.md` | Retrospectiva, redactada 2026-09-15 | Ratificada por Jhinson y Jeremy |
| 2026-09-10 | `docs/actas/acta-2026-09-10.md` | Retrospectiva, redactada 2026-09-15 | Ratificada por Jeremy |
| 2026-09-11 | `docs/actas/acta-2026-09-11.md` | Retrospectiva, redactada 2026-09-15 | Ratificada por Jhinson y Jeremy |
| 2026-09-12 | `docs/actas/acta-2026-09-12.md` | Retrospectiva, redactada 2026-09-15 | Ratificada por Jhinson |
| 2026-09-13 | `docs/actas/acta-2026-09-13.md` | Retrospectiva, redactada 2026-09-15 | Ratificada por Jhinson y Jeremy |

Las sesiones fueron presenciales en la universidad cuando coincidía la
disponibilidad del equipo. Esta modalidad fue declarada retrospectivamente
por Jhinson Aucatoma el 18 de septiembre de 2026 y queda sujeta a ratificación de
los demás participantes. No se conservó una duración fiable; por ello las
horas de los commits se presentan únicamente como ventanas de actividad.

## Correcciones comprobables aplicadas

1. El acta del 1 de septiembre ahora reconoce actividad Git desde las 00:36,
   distingue a los cuatro autores con actividad y atribuye correctamente las
   PR #81–#83 a las cuentas de Andy. También corrige el orden: `c766872`
   precede a `a1296c4`.
2. La ventana del 10 de septiembre termina a las 23:44, hora de `d3dfce3`,
   no a las 23:47.
3. La ventana del 11 de septiembre comienza a las 15:00 con `e4027c0`.
   `3d0c1ef` y `a406fd4`, ambos del 12 de septiembre, se retiraron de esa
   acta y se ubicaron en la fecha correcta.
4. Se eliminó la afirmación no demostrable de que el 7 de septiembre fue el
   día de mayor coordinación del mes.
5. Cada acta identifica responsables, estado, evidencia técnica, pendientes
   conocidos y ratificación pendiente.

## Controles realizados antes del pull request

- Los 60 identificadores abreviados de commit citados en `docs/actas/`
  resuelven a objetos `commit` existentes.
- `git diff --check` no informa errores.
- `scripts/validate_evidence_refs.py` concluye con 72 referencias activas
  válidas y cero fallos.
- El cambio se prepara en la rama `fix/punto34-actas-ratificadas`, basada en
  el merge `6892ae16861cdd1c0151fef41c3053716060737e` de `main`.

## Alcance de los días 14 y 15

No hubo reuniones los días 14 y 15 de septiembre. Jhinson Aucatoma lo confirmó
retrospectivamente el 18 de septiembre de 2026. El historial registra
actividad técnica esos días, principalmente de un solo autor, pero esos
commits corresponden a continuidad de trabajo individual o asincrónico. Por
ello no se fabrican actas para esas fechas. Los demás integrantes podrán
ratificar o corregir esta declaración durante la revisión del pull request.

## Integración y ratificación verificadas

- **Pull request:** [#92](https://github.com/JoseLozanoMorales/TiendaTech/pull/92).
- **Commit de la rama:** `98d8024636934df3be3e4b8c070dd7cd0b51e1f5`.
- **Merge commit:** `604a9b67155326874e06d75f244a413cb17290d2`.
- **Revisor y responsable del merge:** Jeremy Gaibor (`JeremyGaibor`).
- **Ratificación:** [revisión formal APPROVED](https://github.com/JoseLozanoMorales/TiendaTech/pull/92#pullrequestreview-5250914408).
- **Checks del commit de rama:** 30 completados correctamente.
- **Checks posteriores al merge en `main`:** 30 completados correctamente.

Jeremy confirmó expresamente su participación presencial los días 3, 7, 10,
11 y 13, las responsabilidades que se le atribuyen, el significado limitado
de las ventanas Git y la ausencia de reuniones los días 14 y 15.

La corrección de identidad y el registro de esa auditoría se integraron
posteriormente mediante:

- **Pull request correctivo:** [#93](https://github.com/JoseLozanoMorales/TiendaTech/pull/93).
- **Commit de la rama:** `c8961579cf26e55dc14c51afda27d0496de2de41`.
- **Merge commit:** `b7efceffab34e15dd8e845b7e540586adfb14aa3`.
- **Responsable del merge:** Jeremy Gaibor (`JeremyGaibor`).
- **Checks del commit de rama:** 30 completados correctamente.
- **Checks posteriores al merge en `main`:** 30 completados correctamente.

Andy Sánchez ratificó desde su cuenta la reunión del 1 de septiembre: confirmó
su participación presencial, las decisiones y responsabilidades atribuidas y
el significado limitado de la ventana Git. La evidencia permanente es su
[comentario en el PR #93](https://github.com/JoseLozanoMorales/TiendaTech/pull/93#issuecomment-5734750054).

Con las declaraciones de Jhinson, la revisión formal de Jeremy y el comentario
de Andy, cada una de las ocho actas cuenta con al menos una ratificación de una
persona declarada como participante. Las personas sin ratificación individual
continúan identificadas como tales; no se presenta su silencio como aprobación.

## Condición de cierre

El punto queda cerrado documentalmente porque las correcciones históricas son
verificables, cada acta contiene responsables y pendientes, las ocho actas
tienen cobertura de ratificación de al menos un participante, los días 14 y 15
se documentan honestamente como trabajo sin reunión y los dos ciclos de
integración concluyeron en verde. Esta conclusión no convierte en ratificación
el silencio de participantes adicionales ni transforma las ventanas Git en
duraciones de reunión.
