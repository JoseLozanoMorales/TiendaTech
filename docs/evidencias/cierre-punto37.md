# Cierre del punto #37 — higiene del repositorio

## Resultado

Los artefactos pesados exigidos por la guía están fuera del árbol Git y
publicados como assets de `v4.0.0`: video, dos APK, agente OpenTelemetry y las
360 bases SQLite comprimidas. El único `.jar` conservado es
`Apps/mobile/gradle/wrapper/gradle-wrapper.jar`, dependencia legítima de Gradle.

El APK local histórico `9da16724...`, usado en la primera validación física,
también quedó adjunto con checksum y procedencia. No se confunde con el APK
canónico `b1e622a2...` generado por CI.

Después de retirar los cuatro archivos locales, el contenido versionado del
checkout ocupa 43.042.412 bytes (aprox. 41,0 MiB); los dos APK eliminados
sumaban 76.830.898 bytes.

## Controles reproducibles

- `.gitignore` bloquea `*.apk`, `*.mp4`, `*.jar`, `*.db` y `*.db-journal`;
  solo exceptúa `gradle-wrapper.jar`.
- `release/release-assets-v4.0.0.json` fija nombre, bytes y SHA-256 de ocho
  assets vinculados al requisito.
- `scripts/verify_release_assets.py` consulta la API pública de GitHub y falla
  ante ausencia, cambio de tamaño o digest. Sus pruebas negativas se ejecutan
  en `data-integrity.yml`.
- `scripts/check_repository_hygiene.py` inspecciona el índice Git en CI e
  impide incluso una reintroducción forzada que eluda `.gitignore`.
- Con `--download-dir` el verificador descarga y recalcula el SHA-256.
- Los README enlazan cada artefacto y no afirman que los APK estén versionados.

## APK local histórico

La release incluye el APK de 35.849.002 bytes y SHA-256
`9da16724b311ac53e2a670fedd08dfaabe7c3a28ce7589fe80199a2999a04e59`, su
`.sha256` y un archivo de procedencia que declara su carácter histórico. Así se
conserva el entregable original sin mantenerlo en Git ni atribuirle la
procedencia del APK canónico.

## Integración verificada

- Pull request: [#97](https://github.com/JoseLozanoMorales/TiendaTech/pull/97).
- Commit original: `667c7e3b44fbe187e7b10e33d00baab4e235f200`.
- Commit de fusión: `dc98fab83afb8e89e588b235646dee49b300ae44`.
- Ejecución verificada:
  [35410651107](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35410651107).
- Resultado del commit fusionado: 50 checks exitosos, 0 fallidos y 0
  pendientes.
- Check que ejecutó los nuevos controles: `Integridad de todos los datos CSV y
  TSV`, exitoso
  ([job 105809472355](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35410651107/job/105809472355)).
- Fecha de fusión: 19 de septiembre de 2026, 00:49:25 UTC.

Con la integración en `main` quedaron activos tanto el control de los ocho
assets publicados como el guardián que impide reintroducir artefactos pesados.
El punto 37 queda cerrado con evidencia reproducible y verificada en CI.
