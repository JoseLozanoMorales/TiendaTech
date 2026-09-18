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
| 2026-09-01 | `docs/actas/acta-2026-09-01.md` | Retrospectiva, redactada 2026-09-15 | Pendiente en PR |
| 2026-09-03 | `docs/actas/acta-2026-09-03.md` | Retrospectiva, redactada 2026-09-15 | Pendiente en PR |
| 2026-09-04 | `docs/actas/acta-2026-09-04.md` | Contemporánea | Pendiente de ratificación formal en PR |
| 2026-09-07 | `docs/actas/acta-2026-09-07.md` | Retrospectiva, redactada 2026-09-15 | Pendiente en PR |
| 2026-09-10 | `docs/actas/acta-2026-09-10.md` | Retrospectiva, redactada 2026-09-15 | Pendiente en PR |
| 2026-09-11 | `docs/actas/acta-2026-09-11.md` | Retrospectiva, redactada 2026-09-15 | Pendiente en PR |
| 2026-09-12 | `docs/actas/acta-2026-09-12.md` | Retrospectiva, redactada 2026-09-15 | Pendiente en PR |
| 2026-09-13 | `docs/actas/acta-2026-09-13.md` | Retrospectiva, redactada 2026-09-15 | Pendiente en PR |

Las sesiones fueron presenciales en la universidad cuando coincidía la
disponibilidad del equipo. Esta modalidad fue declarada retrospectivamente
por José Lozano el 18 de septiembre de 2026 y queda sujeta a ratificación de
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

No hubo reuniones los días 14 y 15 de septiembre. José Lozano lo confirmó
retrospectivamente el 18 de septiembre de 2026. El historial registra
actividad técnica esos días, principalmente de un solo autor, pero esos
commits corresponden a continuidad de trabajo individual o asincrónico. Por
ello no se fabrican actas para esas fechas. Los demás integrantes podrán
ratificar o corregir esta declaración durante la revisión del pull request.

## Condición de cierre

Este punto no se declara ratificado únicamente con el commit del autor. Antes
de fusionar, los integrantes mencionados deben revisar las actas que les
correspondan y confirmar o corregir asistencia, modalidad, decisiones y
responsabilidades mediante una aprobación o comentario explícito en el pull
request. En especial, Jeremy Gaibor debe revisar las fechas en las que aparece
mencionado y limitar su confirmación a aquellas en las que realmente estuvo
presente.
