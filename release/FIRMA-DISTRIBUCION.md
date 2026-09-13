# Preparación de la firma de distribución (E6)

Estado actual: **clave de José creada y APK release firmado y verificado localmente**. Certificado `CN=Jose Alejandro Lozano Morales`, RSA de 3072 bits; firma APK v2 válida y certificado del APK idéntico al exportado de su almacén. El APK no es depurable. Fecha local: 12 de septiembre de 2026.

- Instalador: [tiendatech-release.apk](tiendatech-release.apk).
- Checksum: [tiendatech-release.apk.sha256](tiendatech-release.apk.sha256).
- Certificado público: [jose-lozano-certificado-publico.pem](jose-lozano-certificado-publico.pem).
- Huella SHA-256 del certificado: `6ad168c152fd8090144c25c75d630116cc912fbe60b25150a81d88f354bc8bbd`.
- Evidencia: `docs/evidencias/firma-release-jose/verificacion.json` y `apksigner.txt`.

Custodio: José Alejandro Lozano Morales. El almacén privado está fuera del repositorio, en el directorio personal `.tiendatech-signing`, archivo `jose-lozano-release.p12`, alias `jose-lozano`. La contraseña fue introducida por José en una consola local, no se guardó en los archivos del proyecto. Conservar un respaldo privado del almacén y la contraseña en el gestor personal antes de depender de esta clave para futuras actualizaciones.

La primera generación se hizo localmente y queda conservada como evidencia histórica.
José confirmó instalación e inicio correctos en dos dispositivos después de resolver
el conflicto con la versión debug. Es validación reportada por el usuario; no se
registraron modelos ni versiones de Android y no equivale a una prueba integral de
compra. La compilación y publicación automáticas posteriores se verificaron en CI.
El APK debug anterior se conserva como artefacto histórico.

El responsable que custodie la clave deberá proporcionar al proceso Gradle estas variables de entorno, sin guardar sus valores en Git:

| Variable | Valor requerido |
|---|---|
| `TIENDATECH_KEYSTORE_PATH` | Ruta absoluta al almacén privado del equipo |
| `TIENDATECH_KEYSTORE_PASSWORD` | Contraseña del almacén |
| `TIENDATECH_KEY_ALIAS` | Alias acordado |
| `TIENDATECH_KEY_PASSWORD` | Contraseña de la clave |

Configurar los secretos fuera del repositorio. No escribirlos en órdenes que queden en el historial ni compartirlos en mensajes. La clave requiere custodia y respaldo del responsable: las actualizaciones futuras deben conservar la misma identidad de firma.

Desde `Apps/mobile`, con el entorno configurado:

```text
./gradlew requireReleaseSigning
./gradlew assembleRelease
```

La configuración `distribution` se asigna a `release` solo si están presentes las cuatro variables. Empaquetar release sin ellas falla con un mensaje explícito; debug conserva su comportamiento. Gradle comprueba el almacén y sus credenciales al firmar. La salida esperada es `app/build/outputs/apk/release/app-release.apk`.

Antes de entregar:

1. Ejecutar `apksigner verify --verbose --print-certs` sobre el APK y comprobar la huella del certificado contra la identidad del equipo. Una firma v2 por sí sola no distingue una clave debug de una de distribución.
2. Verificar versión, URL del gateway e instalación real en un dispositivo/emulador. Una instalación debug existente con firma diferente no admite actualización directa con la nueva clave.
3. Copiar el APK acordado a `release/`, generar su SHA-256 y conservar resultados de firma e instalación. No declarar E6 cerrado antes de disponer de esos archivos y comprobaciones.

Referencia de configuración: https://developer.android.com/studio/publish/app-signing

## Firma automática en CI

La ampliación de CI ejecuta `lintRelease assembleRelease`
solo en pushes a `main`, después de las comprobaciones de calidad.
Las pruebas unitarias `testDebugUnitTest` se ejecutan en el trabajo previo
`android-mobile`, del que depende la firma. Este proyecto no expone una tarea
`testReleaseUnitTest`.
Los pull requests mantienen pruebas debug y no reciben la clave de distribución. El almacén temporal
se elimina al terminar el paso; la compilación de firma no utiliza caché Gradle.
Antes de publicar se exige la huella exacta del certificado de José y se generan
el checksum del APK, el informe de `apksigner` y la procedencia (commit y run).
Se publican como prerelease `mobile-release-*`; no se actualiza el APK versionado
automáticamente ni se altera la identidad de firma de debug.

Secretos de repositorio necesarios: `TIENDATECH_KEYSTORE_BASE64`,
`TIENDATECH_KEYSTORE_PASSWORD`, `TIENDATECH_KEY_ALIAS` y `TIENDATECH_KEY_PASSWORD`.
José autorizó alojarlos en GitHub. Para cargarlos, ejecutar en PowerShell local:

```powershell
./scripts/configure-mobile-signing-secrets.ps1
```

El guion solicita la contraseña de forma oculta, valida el almacén y el certificado,
y envía los valores por entrada estándar a `gh secret set`. No guarda contraseñas
ni claves en Git. El PKCS12 de José utiliza la misma contraseña para almacén y clave.
Requiere una sesión de GitHub CLI con permisos para administrar secretos.

Estado verificado el 13 de septiembre de 2026: José cargó los cuatro secretos mediante
el guion local y el job `Firmar APK release de Jose` terminó satisfactoriamente en el
[run 34780292598](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34780292598),
para el commit completo `c3edc7affc694b2192136675bd2acb7ebb988cf9`.
La prerelease
[`mobile-release-34780292598-1`](https://github.com/JoseLozanoMorales/TiendaTech/releases/tag/mobile-release-34780292598-1)
contiene el APK, su checksum, el informe de `apksigner` y la procedencia. El digest
SHA-256 publicado del APK es
`91276296e17d3d16297e9af210736db1e1f588663d8f50490a6a2d3cc41a8126`.
La evidencia reproducible se resume en
`docs/evidencias/firma-release-jose/verificacion-ci-20260913.json`.
