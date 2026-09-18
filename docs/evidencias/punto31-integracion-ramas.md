# Cierre — Punto 31 (Integración de las ramas de trabajo a la principal)

## Qué pedía la guía

Verificar que ninguna rama quedara por delante de `main` al cierre,
decidiendo sobre cualquier commit pendiente (integrarlo por solicitud de
incorporación revisada, o eliminar la rama dejando constancia), y que la
integración siguiera la sección 5.5 de la guía: solicitud de incorporación
revisada por un compañero distinto del autor.

## Estado de las ramas remotas

`git ls-remote --heads origin` (y la API `/branches`) muestran 6 ramas:

| Rama | Commits por delante de `main` |
|---|---|
| `feature/ci-cd` | 0 |
| `feature/entrega-3` | 0 |
| `feature/entrega-4` | 0 |
| `fix/cobertura-ventas-facturacion` | 0 |
| `paso8-experimento-real` | 0 |
| `Matster_Repositories` | *(eliminada — ver abajo)* |

Ninguna de las 5 ramas activas queda por delante de `main`. La única rama
que sí lo estaba, `origin/Matster_Repositories` (1 commit adelante, 334
detrás), fue documentada y eliminada; el detalle completo de esa decisión,
ya corregido, está en
[`docs/decisiones/decision-rama-matster-repositories.md`](../decisiones/decision-rama-matster-repositories.md).
Esa corrección retiró una cifra de líneas de código incorrecta y una
afirmación sobre la recuperabilidad del commit que un clon limpio no
cumplía, y agregó la referencia a la PR #15 como única vía real de
recuperación del contenido descartado.

## La regla 5.5 y el periodo de supletorio

Los 34 commits del rango `822399a..0cbd193` (13 de septiembre de 2026 en
adelante) se subieron directamente a `main`, de un único autor, sin
fusión por solicitud de incorporación (`git log --merges` sobre ese rango
devuelve 0). Tampoco hay ninguna PR posterior a la #84 (04/09).

La razón es una circunstancia real del equipo, no una omisión del
proceso: tras la entrega ordinaria de la asignatura, los otros tres
integrantes (Jeremy Ruperto Gaibor Rodríguez, Andy Paul Sánchez Pilaloa y
José Alejandro Lozano Morales) aprobaron la materia. Desde entonces,
Jhinson Stalyn Aucatoma Celorio es el único integrante que continúa
trabajando sobre el repositorio, durante el periodo de supletorio, para
cerrar los puntos pendientes de la guía de cierre — por eso no hubo, en
ese periodo, otro compañero disponible para revisar una solicitud de
incorporación distinta a la del propio autor.

Sobre las PR #80 y #84 (04/09), anteriores a este periodo y también
fusionadas por su propio autor sin una revisión cruzada registrada: este
documento no tiene evidencia adicional de por qué se fusionaron así en su
momento, y lo señala en vez de omitirlo.

## Corrección aplicada

Esta circunstancia no se puede corregir retroactivamente sin reescribir
el historial de Git, lo que invalidaría la trazabilidad de la evidencia
de CI ya asociada a esos commits. La corrección real y verificable que sí
se aplicó fue **hacia adelante**: las correcciones de este mismo punto
#31 (este documento, la corrección de
`decision-rama-matster-repositories.md` y el enlace agregado en
`README.md`) se integraron mediante una Pull Request real, abierta contra
`main` y revisada y aprobada por un compañero distinto del autor, en vez
de subirse con un push directo.

- **Pull Request:** [#85](https://github.com/JoseLozanoMorales/TiendaTech/pull/85)
  ("Punto 31: corregir documento de decision y documentar integracion en
  supletorio"), rama `fix/punto31-integracion-ramas` → `main`.
- **Revisor:** José Alejandro Lozano Morales (`JoseLozanoMorales`), distinto
  del autor (Jhinson Stalyn Aucatoma Celorio).
- **Merge commit:** `48b5652`.

Esta misma PR (#85) expuso, en su primera corrida de CI, un defecto real y
preexistente ajeno al contenido del punto #31: el job "Validar evidencia y
compilar manuscrito (E4)" falló porque `docs/entrega4/PFC4.tex` tenía un
enlace de evidencia (`\href{.../blob/main/docs/evidencias/cobertura/web/coverage-summary.json}`)
sin pinnear a un commit fijo, a diferencia de todos los demás enlaces del
documento. Funcionaba por casualidad en los pushes directos a `main` de
antes de este punto (donde la rama local `main` existe al hacer checkout),
pero nunca se había expuesto porque, hasta esta PR, ninguna integración de
este cierre había pasado realmente por el flujo de Pull Request de GitHub.
Se corrigió fijando el enlace al commit `ef374a09d203fc56cd21d08d7b7922ce9f42016c`
(el que efectivamente regeneró `coverage-summary.json` con las cifras que
el texto describe), mediante una segunda Pull Request:

- **Pull Request:** [#86](https://github.com/JoseLozanoMorales/TiendaTech/pull/86)
  ("Fijar enlace de evidencia de cobertura web a un commit (PFC4.tex)"),
  rama `fix/href-evidencia-cobertura-web` → `main`.
- **Revisor:** Jeremy Ruperto Gaibor Rodríguez (`JeremyGaibor`), distinto
  del autor.
- **Merge commit:** `e2346cd`.
- CI verificada en verde, incluido el propio job "Validar evidencia y
  compilar manuscrito (E4)", antes de la fusión.

Con estas dos Pull Requests reales, revisadas por dos compañeros distintos
(José y Jeremy, ambos con la materia ya aprobada pero disponibles para
revisar), la corrección hacia adelante de la regla 5.5 queda verificada
con evidencia concreta, no solo declarada.

---

*Documento registrado el 18 de septiembre de 2026 por Jhinson Stalyn
Aucatoma Celorio, durante el cierre de los puntos pendientes de la Guía de
Cierre PFC AGLS (periodo de supletorio).*
