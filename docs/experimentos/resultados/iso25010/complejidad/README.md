# Complejidad ciclomática después del refactor

Medición local del 13 de septiembre de 2026. `summary.csv` reúne máximos por
método/función, con el mismo objetivo estricto `<10` para Java y TypeScript/React.

| Módulo | Máximo | Estado |
| --- | ---: | --- |
| gateway | 7 | CUMPLE |
| inventario | 8 | CUMPLE |
| ordenes_proveedores | 8 | CUMPLE |
| pedidos | 9 | CUMPLE |
| productos | 8 | CUMPLE |
| usuarios | 9 | CUMPLE |
| ventas | 8 | CUMPLE |
| webapp | 9 | CUMPLE |

## Alcance y reproducción

- Java: PMD 7.17.0 y `docs/experimentos/pmd-cyclomatic-ruleset.xml`, sobre todo
  `src/main/java` de los siete módulos. Se extraen los valores por método de los
  XML, no la complejidad total por clase. La ejecución local usó JDK 21 y la CLI
  de PMD directamente, con los mismos argumentos del script PowerShell.
- Web: ESLint 10.10.0, regla `complexity`, variante `classic`, umbral de reporte 0
  y supresiones inline desactivadas. Se midieron 434 funciones en 19 archivos de
  `src/**/*.{ts,tsx}`; incluye callbacks y funciones de renderizado extraídas.
  Pruebas y herramientas están fuera de ese directorio. `webapp-complexity.json`
  conserva cada ubicación y valor. Cada herramienta cuenta las decisiones de su
  lenguaje; no se afirma identidad de todas sus convenciones sintácticas.
- `webapp-eslint*.{json,csv}` documenta el lint general por separado: 39 archivos,
  cero errores y cero advertencias. Sus recuentos no reemplazan la complejidad.
- El servicio Python y el cliente Android no forman parte de esta medición.

Desde la raíz del repositorio, con PowerShell, Java y Node disponibles:

```powershell
./scripts/measure-cyclomatic-complexity.ps1
npm --prefix Apps/web/frontend/webapp run complexity:report
npm --prefix Apps/web/frontend/webapp run lint:report
```

La regla de reporte 0 permite observar también funciones de complejidad 1.
El script web falla ante un máximo >=10. En Java, el paso posterior de CI
comprueba el umbral sobre `summary.csv`; el código 4 de PMD indica que se
produjeron mediciones, no que se incumplió necesariamente el objetivo.

`web-quality` descarga el artefacto PMD de la misma ejecución antes de añadir la
fila web, y conserva el resumen combinado. Las pruebas y la cobertura web se
siguen ejecutando si falla la complejidad; ese fallo mantiene rojo el job.

Al versionar CSV regenerados, actualizar y comprobar también el manifiesto:

```bash
python3 scripts/check_data_checksums.py --write
python3 scripts/check_data_checksums.py
```

## Validación del refactor

- Web: 62 pruebas aprobadas; TypeScript y build de producción aprobados.
- Inventario: 45 pruebas aprobadas, incluyendo costes ponderados, kardex,
  validación JWT y respuestas HTTP.
- Pedidos: 78 pruebas aprobadas; 2 de integración omitidas al no disponer de
  Docker local. Incluye validación JWT, respuestas HTTP y validaciones del
  checkout. La integración real con CockroachDB queda para CI.
- Integridad: 60 sumas de datos tabulares verificadas y 5 pruebas del verificador
  aprobadas. Referencias del manuscrito: cero fallos.

Los enlaces del manuscrito al commit `f6de760` identifican explícitamente la
medición histórica anterior (máximo web 26). La tabla actual corresponde a los
archivos regenerados que acompañan este refactor, no a aquel commit.
