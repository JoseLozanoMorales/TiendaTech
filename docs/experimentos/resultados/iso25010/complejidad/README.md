# Análisis estático de servicios y cliente web

`summary.csv` reúne dos mediciones distintas:

- Java / PMD: máximo de complejidad por método y objetivo `<10`. Los recuentos
  de archivos, errores y advertencias se dejan vacíos porque estos informes no
  aportan esa misma medición.
- Web / ESLint: archivos analizados, errores y advertencias. La complejidad por
  método se deja vacía: **no medida**, no cero.

La ejecución web del 11 de septiembre de 2026 analizó **27 archivos**, con
**0 errores y 0 advertencias**. Incluye fuentes TypeScript/TSX, pruebas y
configuración/scripts seleccionados por `eslint.config.js`; excluye copias JS
bajo `src/`, dependencias, `dist/` y reportes de cobertura. No se infiere ausencia
universal de defectos a partir de cero incidencias de las reglas habilitadas.

Evidencia: `webapp-eslint.json` contiene los resultados por archivo con rutas
relativas al repositorio; `webapp-eslint-summary.csv` conserva los recuentos.
El manuscrito los referencia en la subsección «Cobertura y complejidad».

Para regenerar desde la raíz:

```bash
npm --prefix Apps/web/frontend/webapp ci
npm --prefix Apps/web/frontend/webapp run lint:report
```

El comando aplica las mismas reglas de ESLint que `npm run lint`, escribe los
reportes y actualiza la fila web de `summary.csv` conservando las filas PMD.
Devuelve fallo si hay errores, advertencias o ningún archivo analizado.
Después de regenerar los informes Java con `scripts/measure-cyclomatic-complexity.ps1`,
ejecutar también `lint:report` para incorporar de nuevo la fila web al resumen.

En CI se generan el JSON y CSV, se muestran los recuentos en el resumen del job
`web-quality` y se conservan como `web-static-analysis`, incluso cuando el análisis
falla. El workflow no hace commits automáticos de los informes.
