# Integridad de campañas (E5)

Se complementa el manifiesto general de 60 CSV/TSV con manifiestos `checksums.txt`
propios de cada sesión. El alcance actual es:

| Sesión | Datos cubiertos |
| --- | --- |
| Paso 8, correctiva-20260905-final-v2 | Crudo y nueve CSV de analisis/ |
| Paso 8, oficial-v4-20260904 | Crudo y resumen de analisis/ |
| tests/load/results | Cuatro CSV históricos de Locust |

`experiments/paso8/campaign_checksums.py` incluye los CSV/TSV directamente en la
carpeta de sesión y en su subárbol oficial `analisis/`. No incluye reproducciones
auxiliares como `analisis-reproducido/`, gráficos, HTML, JSON ni archivos secretos.
Usa nombres relativos ordenados y SHA-256 del contenido con finales CRLF convertidos
a LF, sin modificar los datos. Así coincide con los CSV publicados mediante Git.
Para archivos con CRLF se debe usar el verificador Python; una herramienta que
calcule el hash literal de esos bytes produciría un resultado diferente.

La generación es el último paso de escritura de datos del guion de ejecución real,
los análisis estadístico, comparativo y general, y la reconstrucción del oráculo.
Los análisis con salida `analisis/` actualizan el manifiesto de su sesión; una
salida alternativa recibe un manifiesto propio. La ejecución de Locust genera
sumas al terminar y conserva su código de error si la carga falló. Tener sumas no
significa que el experimento haya sido exitoso ni que sus métricas cumplan objetivos.

Los manifiestos históricos ampliados se generaron el 13 de septiembre de 2026
sobre los datos conservados. No se volvieron a ejecutar campañas de carga ni se
atribuyen retrospectivamente estas sumas a sus ejecuciones originales.

Verificación (no regenera manifiestos):

```console
python experiments/paso8/campaign_checksums.py
python -m unittest discover -s experiments/paso8 -p test_campaign_checksums.py -v
```

Generación explícita para datos históricos o salidas producidas fuera de los guiones:

```console
python experiments/paso8/campaign_checksums.py --write
```

El workflow `Integridad de datos` verifica los manifiestos sobre el checkout de
Actions, sin regenerarlos, además de comprobar el manifiesto general. Detecta
cambios de contenido, datos nuevos sin suma, archivos ausentes y manifiestos
ausentes o duplicados. La validación remota de esta ampliación requiere publicar
los cambios; no se declara aprobada anticipadamente.

Validación local realizada: 16 sumas correctas y seis pruebas aprobadas tanto en
el árbol de trabajo como en una exportación aislada de HEAD con esta ampliación.
Se ejecutaron los análisis general y comparativo sobre copias de los datos y se
comprobó que actualizaron automáticamente sus manifiestos. El análisis general
de la sesión oficial conserva su resultado `valido: false`; la integridad no cambia
esa evaluación. No se ejecutaron pruebas de carga ni consultas al oráculo remoto.
El manifiesto general de 60 archivos sigue verificándose sin cambios en los CSV.
