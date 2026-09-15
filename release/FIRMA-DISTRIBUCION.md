# Preparación de la firma de distribución (E6)

Estado actual: **clave de José creada y APK release firmado y verificado localmente**. Certificado `CN=Jose Alejandro Lozano Morales`, RSA de 3072 bits; firma APK v2 válida y certificado del APK idéntico al exportado de su almacén. El APK no es depurable. Fecha local: 12 de septiembre de 2026.

- Instalador: [tiendatech-release.apk](https://github.com/JoseLozanoMorales/TiendaTech/releases/download/v4.0.0/tiendatech-release.apk) (ya no se versiona en `release/`; ver punto 37).
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

## Firma automática y publicación definitiva en CI

Desde el cierre del punto 44, la firma y publicación ya no ocurren en cada push
a `main`. El único mecanismo es `.github/workflows/mobile-release-final.yml`,
disparado al empujar un tag `v*`:

1. Exige que el commit del tag sea exactamente el tip de `origin/main` en ese
   momento (así el binario firmado sale del mismo código revisado).
2. Compila un único APK (`lintRelease assembleRelease`; este proyecto no
   expone `testReleaseUnitTest` — las pruebas unitarias corren en el job
   "Android mobile quality" de `ci.yml`, prerrequisito de este flujo).
3. Verifica con `apksigner` que el certificado firmante coincida exactamente
   con la huella de José (`6ad168c1...8bbd`).
4. Genera el checksum del APK, el resumen SHA-256 del guion de construcción
   (`Apps/mobile/app/build.gradle.kts` tal como está versionado en ese commit,
   ver punto 44) y la procedencia (commit, tag, run).
5. Publica con `gh release create --verify-tag --target "$GITHUB_SHA" --latest`
   (sin `--prerelease`): una versión **definitiva**, no preliminar.

Los dos trabajos antiguos que compilaban y firmaban en cada push
(`android-release`, `publish-mobile`, que publicaban como prerelease
`mobile-release-<run_id>-<run_attempt>`) fueron retirados de `ci.yml`: producían
un artefacto distinto del versionado y solo se publicaban como versión
preliminar — exactamente el defecto que cerró el punto 44.

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

## Publicación definitiva verificada

Etiqueta `v4.0.0`, commit `92e66686f8bdbb79d68487ad40008b014a432aba`
(tip de `main` en el momento de etiquetar), ejecución
[34911055622](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34911055622).
La [release `v4.0.0`](https://github.com/JoseLozanoMorales/TiendaTech/releases/tag/v4.0.0)
aparece marcada `Latest`, no `Pre-release`.

- APK SHA-256: `b1e622a201e40cc662a1a768e3832ba53e5274b6aacd2d10f5150f4eccb1d304`.
- Resumen del guion de construcción (`Apps/mobile/app/build.gradle.kts` tal
  como está versionado en ese commit): `29ec862d1ca3873a1dc9652ae98fb5ed4a8885d8d65f498a2636ff54c3653233`
  — coincide con `docs/evidencias/firma-release-jose/verificacion.json`.
- Certificado firmante extraído directamente del APK descargado (huella
  SHA-256): `6ad168c152fd8090144c25c75d630116cc912fbe60b25150a81d88f354bc8bbd`,
  `CN=Jose Alejandro Lozano Morales`, firma APK v2 verificada.
- El APK descargado de la release fue verificado de extremo a extremo:
  su SHA-256 coincide con `tiendatech-release.apk.sha256`, y `provenance.txt`
  identifica el mismo commit, tag y ejecución.

Evidencia completa en `docs/evidencias/firma-release-jose/verificacion-final-v4.0.0.md`.
