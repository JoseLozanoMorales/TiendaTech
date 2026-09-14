# Evidencia numérica web — E1/E3

Medición local del 14 de septiembre de 2026, sobre el código de `main` tras
retirar la lista de doce vistas de `coverage.include` en `vitest.config.ts`.
Vitest 4.1.11 / V8. **62 pruebas aprobadas en 14 archivos; ESLint sin
advertencias y TypeScript aprobado.**

El alcance anterior (11 de septiembre de 2026) medía solo tres vistas
(CartView, CheckoutView, AdminView) y reportaba 19 pruebas con 53,39 % de
líneas. Esa medición quedó desactualizada frente al trabajo ya realizado por
el equipo (62 pruebas en 14 archivos) y frente al alcance real del producto
(todo `src/**/*.{ts,tsx}`, no solo tres vistas).

| Alcance | Líneas | Sentencias | Ramas | Funciones |
|---|---:|---:|---:|---:|
| Total, árbol completo (`src/**/*.{ts,tsx}`) | 71,96 % (267/371) | 56,26 % | 45,10 % | 44,47 % |
| `App.tsx` (punto de entrada) | 0 % (0/38) | 0 % | 0 % | 0 % |
| `src/services/cart.ts` (lógica de carrito) | 0 % (0/16) | 0 % | 0 % | 0 % |
| `src/services/api.ts` | 100 % (48/48) | 98,27 % | 95,55 % | 100 % |
| `src/services/session.ts` | 88,88 % (32/36) | 87,50 % | 40,90 % | 81,81 % |
| 12 vistas (`src/views/*`) | ver `coverage-summary.json` | — | — | — |

Reportes versionados: [JSON](coverage-summary.json) y [LCOV](lcov.info).
Las rutas del JSON se normalizaron respecto de la aplicación.

## Alcance del criterio E1/E3

La cobertura ya no se restringe a un subconjunto de vistas elegido a mano:
se mide todo `src/**/*.{ts,tsx}` (excepto `main.tsx`, tipos `.d.ts` y los
propios archivos de prueba). Según el criterio compartido para este cierre,
E1 exige esa evidencia y no fija un porcentaje mínimo; por tanto, no se aplica
aquí un umbral hipotético del 70 %. Se documentan los valores reales sin
confundirlos con la nota de evaluación.

Dos huecos quedaron visibles con la ampliación de alcance y no estaban
documentados antes: `App.tsx` (el punto de entrada de la aplicación) y
`src/services/cart.ts` (lógica de carrito) están en 0 % de cobertura. Ninguno
de los dos estaba dentro del alcance medido anteriormente, así que su 0 % no
es una regresión: es la primera vez que se mide.

El análisis estático web de E3 tiene su reporte y recuentos junto al backend en
[la evidencia de análisis estático](../../../experimentos/resultados/iso25010/complejidad/README.md)
y se referencia en `docs/entrega4/PFC4.tex`. La calificación definitiva corresponde
al evaluador; no se deduce automáticamente del estado verde de CI.

Los servicios HTTP están simulados; las pruebas verifican la interfaz y sus
controles, no las transiciones del backend. Estos porcentajes no son la
cobertura de toda la SPA (falta cubrir `App.tsx` y `cart.ts`), los
microservicios o Android. La cobertura backend se documenta por separado en
el directorio superior.

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
