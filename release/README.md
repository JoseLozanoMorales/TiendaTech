# Paquete Android instalable (E6)

El [APK release](tiendatech-release.apk) está firmado por José y tiene
[checksum](tiendatech-release.apk.sha256) verificado. La firma v2, el certificado
público y la validación de instalación y publicación se documentan en
[Firma de distribución](FIRMA-DISTRIBUCION.md).

## Paquete debug histórico

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
sha256sum -c tiendatech-release.apk.sha256
adb install -r tiendatech-release.apk
```

El primer comando debe devolver `tiendatech-release.apk: OK`.
En PowerShell: `Get-FileHash release/tiendatech-release.apk -Algorithm SHA256`,
comparando el resultado con el `.sha256`. También se puede copiar el APK al
teléfono y abrirlo permitiendo la instalación desde esa fuente.

El APK release utiliza el gateway público configurado durante su compilación.
Instalar el APK no inicia los servicios.

### Conflicto con instalaciones anteriores

Android solo permite actualizar una aplicación cuando la versión instalada y el
nuevo APK usan el mismo identificador y el mismo certificado de firma. Si existe
una compilación debug u otra versión de TiendaTech firmada con una clave distinta,
la instalación de `tiendatech-release.apk` puede mostrar «conflicto de paquete».

En ese caso, desinstalar primero la versión anterior y después instalar el APK
release. La desinstalación elimina los datos locales de la aplicación, como sesión,
preferencias y caché; conviene conservar cualquier información necesaria antes de
hacerla. Una vez instalada la versión release firmada por José, las actualizaciones
futuras deberán conservar esa misma identidad de firma.

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

`ci.yml` conserva las comprobaciones de la variante debug y, en pushes a `main`
que aprueban todos los trabajos requeridos, genera un APK release firmado. Antes de
publicarlo comprueba la huella del certificado de José, genera su checksum y registra
el commit y el run de procedencia. `publish-mobile` lo publica como una **GitHub
prerelease**, con etiqueta `mobile-release-<run_id>-<run_attempt>`. Las PR no reciben
la clave privada ni publican paquetes.

La primera publicación automática release se verificó para el commit `c3edc7a`:
`mobile-release-34780292598-1`, con APK, checksum, informe de `apksigner` y
procedencia. El paquete debug se conserva únicamente como evidencia histórica.
