# Paquete Android instalable (E6)

Desde el punto 37 (higiene del repositorio), los binarios pesados no se
versionan aquí: se publican como adjuntos de `v4.0.0`. Sus nombres, tamaños y
SHA-256 están fijados en [release-assets-v4.0.0.json](release-assets-v4.0.0.json)
y `scripts/verify_release_assets.py` los contrasta con la API de GitHub en CI.

El [APK release](https://github.com/JoseLozanoMorales/TiendaTech/releases/download/v4.0.0/tiendatech-release.apk)
está firmado por José y tiene [checksum publicado](https://github.com/JoseLozanoMorales/TiendaTech/releases/download/v4.0.0/tiendatech-release.apk.sha256)
verificado. La firma v2, el certificado público y la publicación se documentan
en [Firma de distribución](FIRMA-DISTRIBUCION.md). La instalación histórica en
dos dispositivos corresponde a un APK local anterior (`9da16724...`) y se
mantiene diferenciada. El binario distribuido exacto (`b1e622a2...`) fue
instalado e iniciado posteriormente en otros dos dispositivos; la evidencia
automatizada y visual está en
`docs/evidencias/firma-release-jose/dispositivos-apk-publicado/`.

## Paquete debug histórico

Entregable: [tiendatech-debug.apk](https://github.com/JoseLozanoMorales/TiendaTech/releases/download/v4.0.0/tiendatech-debug.apk),
inventariado por tamaño y SHA-256 en `release-assets-v4.0.0.json`.
Versión 1.0, aplicación `com.tiendatech.mobile`, Android 8.0 (API 26) o posterior.

Validación local del 11 de septiembre de 2026: `assembleDebug` aprobado,
checksum verificado y firma APK v2 validada con `apksigner verify`.
Es una firma **debug**, no una identidad de distribución de producción.
No se realizó una instalación en dispositivo durante esta validación.

## Verificar e instalar

Descargar `tiendatech-release.apk` y `tiendatech-release.apk.sha256` desde la
[versión `v4.0.0`](https://github.com/JoseLozanoMorales/TiendaTech/releases/tag/v4.0.0),
colocarlos en la misma carpeta y, con Android Platform Tools instalado:

```bash
sha256sum -c tiendatech-release.apk.sha256
adb install -r tiendatech-release.apk
```

El primer comando debe devolver `tiendatech-release.apk: OK`.
En PowerShell: `Get-FileHash tiendatech-release.apk -Algorithm SHA256`,
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
sha256sum Apps/mobile/app/build/outputs/apk/debug/app-debug.apk
```

Comparar el resultado con el SHA-256 del manifiesto remoto. El APK regenerado
no se commitea (ver `.gitignore`); si cambia por una actualización
legítima, subir el nuevo binario como adjunto de una nueva versión etiquetada y
actualizar el manifiesto versionado y el enlace de descarga.

## Publicación automática y alcance de la rúbrica

El único mecanismo de publicación es `.github/workflows/mobile-release-final.yml`
(ver punto 44): se dispara al empujar un tag `v*`, exige que ese tag apunte al
commit vigente de `main`, compila y firma un único APK release, verifica la
huella del certificado de José y publica una **versión definitiva** (no
preliminar) con `gh release create --verify-tag --latest`, adjuntando el APK,
su checksum, el informe de `apksigner`, el resumen del guion de construcción y
la procedencia (commit, tag, run). El APK debug histórico, el video de
tolerancia a fallos, el agente OpenTelemetry y las bases de datos de los
experimentos se adjuntan a esa misma versión etiquetada por separado (ver
punto 37 en el README raíz).

Los dos trabajos antiguos que compilaban y publicaban el release como
prerelease en cada push (`android-release`, `publish-mobile`) fueron retirados
de `ci.yml` al cerrar el punto 44: producían un artefacto distinto del
versionado y solo se publicaban como versión preliminar.

Para el cierre del punto 47, `v4.1.0` se publicará desde una etiqueta anotada
situada exactamente sobre el tip de `main`. El APK no vuelve al árbol: el flujo
lo compila y firma desde el código etiquetado, verifica su certificado y lo
empaqueta junto con el PDF, `CITATION.cff`, sumas, manifiesto y procedencia. Los
assets pesados históricos permanecen separados en `v4.0.0`.
