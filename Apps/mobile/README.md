# TiendaTech para Android

Cliente móvil de compra para TiendaTech, construido con Kotlin, Jetpack Compose y Material 3. La raíz del proyecto Gradle es esta carpeta y el único módulo de aplicación es `app/`.

## Requisitos

- Android Studio compatible con AGP 9.2.1.
- JDK 17.
- Android SDK 37.
- Emulador o dispositivo con Android 8.0 (API 26) o superior.

## Ejecución

Para distribuir una versión release se necesita la clave del equipo y las cuatro
variables descritas en [Firma de distribución](../../release/FIRMA-DISTRIBUCION.md).
Ya se generó y verificó localmente un APK release con la clave de José; véase
`../../release/tiendatech-release.apk`. Release no se empaqueta sin la clave.
José confirmó instalación e inicio correctos en dos dispositivos tras resolver el
conflicto de firma con la versión debug. La publicación del paquete sigue pendiente.

La variante debug consume por defecto el Gateway de Docker en `http://10.0.2.2:8180/`, que corresponde al puerto `8180` del equipo anfitrión visto desde el emulador. El Gateway y los microservicios son quienes utilizan el `.env` de la raíz para conectarse a CockroachDB; la aplicación nunca recibe credenciales JDBC.

Para un dispositivo físico, una instalación remota o un puerto diferente, configurar la URL del Gateway sin editar el código:

```text
./gradlew assembleDebug -PTIENDATECH_DEBUG_API_BASE_URL=http://IP_DEL_EQUIPO:8180/
./gradlew assembleRelease -PTIENDATECH_API_BASE_URL=https://gateway.example.com/
```

La compilación `release` usa por defecto el gateway público de producción:

```text
https://18-221-94-105.sslip.io/
```

La propiedad `TIENDATECH_API_BASE_URL` permite sustituirlo cuando TiendaTech
disponga de un dominio propio.

La URL debe terminar en `/`. Para una distribución fuera de la red de desarrollo se debe utilizar HTTPS.

Desde esta carpeta:

```text
./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

El APK principal queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Capacidades y límites

La app incluye autenticación de clientes, catálogo con caché, carrito, cuenta, checkout idempotente, pedidos, facturas, tema claro/oscuro y lectura de códigos con cámara.

El escáner reconoce códigos, pero el backend aún no permite buscar productos por código. Las notificaciones solo tienen canal, payload, deep link y demostración local: FCM requiere configuración Firebase, registro de dispositivos y eventos backend reales. Estos límites no se simulan ni se ocultan.

La especificación y el estado verificado se encuentran en `../../docs/alcance-aplicacion-movil.md`.
