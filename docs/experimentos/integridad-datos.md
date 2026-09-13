# Integridad de los datos tabulares (E5)

El manifiesto `resultados/checksums-datos.sha256` cubre **todos los CSV y TSV del índice Git**, sin seleccionar solo la campaña favorable. Actualmente son 60 archivos. Incluye carga, sesiones ISO, campañas, resultados analíticos y tablas auxiliares. No incluye JSON, bases de datos ni archivos locales ignorados; el alcance es explícitamente tabular y no representa un inventario universal de evidencias.

Desde la raíz:

```text
python scripts/check_data_checksums.py
python -m unittest discover -s scripts -p test_data_checksums.py -v
```

El verificador compara el inventario completo con el manifiesto y después lee el contenido actual. Falla ante rutas faltantes, entradas sobrantes, duplicadas, archivos nuevos añadidos al índice sin suma o contenido alterado. Para rutas con `eol=lf`, compara los bytes normalizados a LF como se publican en Git, evitando discrepancias por checkout Windows. No modifica los datos. Los TSV sin ese atributo se verifican byte a byte.

Después de una modificación **intencional y revisada** de datos:

```text
git add -- ruta/al/nuevo-dato.csv
python scripts/check_data_checksums.py --write
python scripts/check_data_checksums.py
```

El primer paso solo es necesario para archivos nuevos: sin seguimiento no forman parte de la entrega. Revisar juntos el cambio de datos y el del manifiesto antes de publicarlos. La regeneración no prueba la veracidad de las mediciones; solo registra su integridad. No regenerar automáticamente en CI para ocultar una discrepancia.

El job `data-integrity` del workflow independiente `.github/workflows/data-integrity.yml` ejecuta los controles negativos y verifica el manifiesto sin regenerarlo. El manifiesto histórico de una sola campaña y su generación al ejecutar el experimento se conservan; no sustituyen a este inventario global.

Validación local tras integrar el remoto: 60 sumas verificadas y cinco pruebas aprobadas. El resultado remoto del nuevo workflow debe comprobarse sobre el SHA publicado; no se deduce de esta ejecución local.


## Integración con cambios del equipo

El flujo de E5 se separó de `ci.yml` para no solaparse con el nuevo job de complejidad del backend. El manifiesto debe corresponder al árbol exacto que se publique. Si se incorporan resultados nuevos, revisar primero el cambio de datos, regenerar las sumas mediante `--write` y verificar de nuevo. CI nunca regenera el manifiesto.

Se integró la actualización remota de `docs/experimentos/resultados/iso25010/complejidad/summary.csv` y se regeneró su suma de forma explícita. El manifiesto de esta revisión ya corresponde al árbol integrado. La comprobación aislada previa y el resultado posterior están en `docs/evidencias/integridad-datos-e5-verificacion.json`.
