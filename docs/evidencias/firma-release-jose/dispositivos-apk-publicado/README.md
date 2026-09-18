# Validación física del APK publicado en `v4.0.0`

Este directorio debe contener la evidencia generada al instalar **el mismo
binario** distribuido como `tiendatech-release.apk` en la release `v4.0.0`:

- tamaño: 35.849.007 bytes;
- SHA-256: `b1e622a201e40cc662a1a768e3832ba53e5274b6aacd2d10f5150f4eccb1d304`;
- paquete: `com.tiendatech.mobile`.

La prueba histórica de dos dispositivos documentada en `verificacion.json`
corresponde al APK local `9da16724...` y no se reutiliza como evidencia de este
binario.

## Procedimiento

Con Android Platform Tools instalado, depuración USB autorizada y el dispositivo
visible en `adb devices`, ejecutar desde la raíz del repositorio:

```powershell
./scripts/verify-published-apk-device.ps1 `
  -Serial "SERIAL_DE_ADB" `
  -AdbPath "C:\ruta\a\platform-tools\adb.exe"
```

El guion verifica el hash y tamaño antes de instalar, no desinstala aplicaciones
ni borra datos, instala con `adb install -r`, confirma el paquete instalado,
inicia su actividad principal y genera un JSON. El serial no se publica en claro:
solo se conserva su SHA-256.

Ejecutar una vez por dispositivo. Si existe una instalación previa firmada con
otra clave, el guion se detiene; la decisión de desinstalar y perder datos locales
queda fuera del guion y requiere intervención consciente del propietario.

Después de cada ejecución, comprobar visualmente que TiendaTech abrió y añadir
una captura con nombre `device-<id>-inicio.png`. La captura complementa el JSON,
pero no sustituye la comprobación automatizada del hash, instalación y arranque.

## Criterio de cierre

Se requieren dos JSON de dispositivos distintos y sus dos capturas de inicio.
Ambos JSON deben registrar el hash `b1e622a2...`, instalación exitosa,
`launch_confirmed: true`, modelo y versión de Android.

## Resultados registrados

| Dispositivo anonimizado | Equipo | Android | APK | Instalación | Inicio | Evidencia visual |
|---|---|---:|---|---|---|---|
| `7873e52cc9db` | Xiaomi 23117RA68G | 15 (SDK 35) | `b1e622a2...`, 35.849.007 bytes | `adb install -r`: `Success` | `launch_confirmed: true` | `device-7873e52cc9db-inicio-cuenta.jpeg`, `device-7873e52cc9db-inicio-login.jpeg` |
| `99c94a04a489` | INFINIX Infinix X6837 | 13 (SDK 33) | `b1e622a2...`, 35.849.007 bytes | `adb install -r`: `Success` | `launch_confirmed: true` | `device-99c94a04a489-inicio-login.jpeg`, `device-99c94a04a489-recuperar-contrasena.jpeg`, `device-99c94a04a489-crear-cuenta.jpeg` |

Los dos dispositivos cumplen el procedimiento completo y tienen identificadores
anonimizados distintos. Las capturas del Xiaomi muestran "Mi cuenta" e inicio
de sesión; las del Infinix muestran inicio de sesión, recuperación de contraseña
y creación de cuenta. La prueba demuestra instalación e inicio del APK exacto
publicado; no afirma haber completado autenticación, recuperación ni registro.
