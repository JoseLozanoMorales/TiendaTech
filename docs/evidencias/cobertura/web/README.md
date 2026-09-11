# Evidencia numérica web — E1/E3

Medición local del 11 de septiembre de 2026, sobre el código de `main` e4027c0,
con la configuración de cobertura añadida en esta entrega. Vitest 4.1.11 / V8.
**19 pruebas aprobadas en 3 archivos; ESLint sin advertencias y TypeScript aprobado.**

| Componente completo | Líneas | Sentencias | Ramas | Funciones |
|---|---:|---:|---:|---:|
| CartView | 100 % (8/8) | 75,32 % | 63,63 % | 64,70 % |
| CheckoutView | 100 % (1/1) | 75,00 % | 42,85 % | 54,54 % |
| AdminView, incluye órdenes | 48,93 % (46/94) | 22,28 % | 10,97 % | 10,57 % |
| Total instrumentado | 53,39 % (55/103) | 30,78 % | 15,45 % | 19,76 % |

Reportes versionados: [JSON](coverage-summary.json) y [LCOV](lcov.info).
Las rutas del JSON se normalizaron respecto de la aplicación.

## Alcance del criterio E1/E3

La cobertura de los componentes solicitados está medida y reportada. Según el
criterio compartido para este cierre, E1 exige esa evidencia y no fija un porcentaje
mínimo; por tanto, no se aplica aquí un umbral hipotético del 70 %. Se documentan
los valores reales sin confundirlos con la nota de evaluación.

El análisis estático web de E3 tiene su reporte y recuentos junto al backend en
[la evidencia de análisis estático](../../../experimentos/resultados/iso25010/complejidad/README.md)
y se referencia en `docs/entrega4/PFC4.tex`. La calificación definitiva corresponde
al evaluador; no se deduce automáticamente del estado verde de CI.

CheckoutView contiene su implementación en una sola línea ejecutable: el 100 %
de líneas no implica todas las decisiones cubiertas. La cobertura de ramas y
sentencias muestra esta limitación. AdminView se mide completo, incluidas las
funciones administrativas ajenas a órdenes.

Los servicios HTTP están simulados; las pruebas de órdenes verifican la interfaz
y sus controles, no las transiciones del backend. Estos porcentajes no son la
cobertura de toda la SPA, los microservicios o Android. La cobertura backend
histórica se documenta por separado en el directorio superior.

## Reproducir y consultar en CI

Desde `Apps/web/frontend/webapp`:

```bash
npm ci
npm run lint
npx tsc -p tsconfig.test.json
npm run test:coverage
```

Los reportes locales aparecen en `coverage/`: HTML (`index.html`), JSON y LCOV.
`web-quality` ejecuta la medición, muestra una tabla por componente en el resumen
de GitHub Actions y conserva los reportes en `web-coverage`. Esta evidencia es
local; el resultado remoto se confirma tras subir los cambios y ejecutar CI.
