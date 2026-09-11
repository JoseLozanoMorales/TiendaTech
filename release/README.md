# Paquete Android instalable (E6)

Entregables: [tiendatech-debug.apk](tiendatech-debug.apk) y
[tiendatech-debug.apk.sha256](tiendatech-debug.apk.sha256).
Versión 1.0, aplicación `com.tiendatech.mobile`, Android 8.0 (API 26) o posterior.

Validación local del 11 de septiembre de 2026: `assembleDebug` aprobado,
checksum verificado y firma APK v2 validada con `apksigner verify`.
Es una firma **debug**, no una identidad de distribución de producción.
No se realizó una instalación en dispositivo durante esta validación.

## Verificar e instalar

Desde la raíz del repositorio, con Android Platform Tools instalado:

```bash
cd release
sha256sum -c tiendatech-debug.apk.sha256
adb install -r tiendatech-debug.apk
```

El primer comando debe devolver `tiendatech-debug.apk: OK`.
En PowerShell: `Get-FileHash release/tiendatech-debug.apk -Algorithm SHA256`,
comparando el resultado con el `.sha256`. También se puede copiar el APK al
teléfono y abrirlo permitiendo la instalación desde esa fuente.

El backend predeterminado es `http://10.0.2.2:8180/`, para el emulador Android
con el backend en el equipo anfitrión. Instalar el APK no inicia los servicios.
Para un dispositivo físico, compilar agregando
`-PTIENDATECH_DEBUG_API_BASE_URL=http://IP_DEL_SERVIDOR:8180/`.

## Regenerar

Requiere JDK 21, `ANDROID_HOME` y SDK `platforms;android-37.0` /
`build-tools;36.0.0`:

```bash
cd Apps/mobile
./gradlew assembleDebug --no-daemon
cd ../..
cp Apps/mobile/app/build/outputs/apk/debug/app-debug.apk release/tiendatech-debug.apk
(cd release && sha256sum tiendatech-debug.apk > tiendatech-debug.apk.sha256)
(cd release && sha256sum -c tiendatech-debug.apk.sha256)
```

Incluir **ambos archivos** en el commit de entrega. El APK y su suma son una
pareja: no mezclar un APK local con el checksum de otro build.

## Publicación automática y alcance de la rúbrica

`ci.yml` comprueba la suma del APK versionado antes de compilar. Luego genera
otro APK y su checksum como artefacto `mobile-release`. En los pushes a `main`,
si todos los jobs requeridos aprueban, `publish-mobile` publica ambos archivos
como una **GitHub prerelease**, con etiqueta `mobile-debug-<run_id>-<run_attempt>`
y referencia al commit exacto. Las PR no publican.

La configuración de publicación debe validarse con el primer run remoto;
la comprobación local no acredita que esa prerelease ya exista.
Las claves debug pueden variar entre equipos y runs: instalar encima de una
versión con otra clave requiere desinstalarla primero, perdiendo sus datos locales.

La presencia del APK y SHA-256 cubre la condición material de E6 para el umbral 5
descrito en la solicitud, una vez versionados y subidos. No se declara nivel 10:
queda pendiente la identidad de firma de distribución y comprobar la publicación
remota efectiva.
