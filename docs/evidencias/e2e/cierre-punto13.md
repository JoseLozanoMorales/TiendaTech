# Cierre — Punto 13 (Pruebas de extremo a extremo)

## Situación original (resuelta)

Los tres archivos de prueba usaban un interceptor global
(`tests/e2e-web/tests/fixtures.js`, `page.route('**/api/**', ...)`) que
respondía toda petición al servidor desde constantes escritas en el propio
archivo. El trabajo de CI (`e2e-web` en `.github/workflows/ci.yml`) solo
levantaba el frontend, sin base de datos ni microservicios, así que la
suite pasaba con el sistema completo ausente.

## Qué se cambió

- Se eliminó `fixtures.js` y el uso de `mockReadApis` en
  `catalog.spec.js` y `auth-admin.spec.js`.
- El job `e2e-web` ahora levanta el sistema completo (3 nodos de
  CockroachDB, los 7 microservicios y el gateway) con
  `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d
  --build --wait`, siembra datos deterministas (`docs/db/seed-e2e.sql`) y
  verifica que el gateway responde con datos reales antes de correr
  Playwright.
- Se documentó y evidenció (`sistema-apagado-falla.md`) que, sin el
  sistema levantado, la suite falla de verdad.

## Bug real encontrado y corregido en el camino

La primera corrida contra el sistema real (checks del commit
`https://github.com/JoseLozanoMorales/TiendaTech/commit/5362f27/checks`)
levantó el stack completo correctamente y el catálogo
pasó (`/api/productos` ya respondía con "Procesador Ryzen 7" desde la base
real), pero el login del administrador falló con un **500 real**, no con
un problema de credenciales:

```
tiendatech-usuarios | [POST /api/login] Unhandled exception
java.lang.NullPointerException
	at java.util.Map.of(...)
	at com.tiendatech.usuarios.presentation.controller.LoginController.login(LoginController.java:50)
```

`LoginController.login()` arma la respuesta con `Map.of(...)`, que en Java
lanza `NullPointerException` si cualquier valor es `null`. El usuario
administrador sembrado no tenía `cedula` ni `telefono` (ambas columnas son
opcionales en el esquema y en el formulario de registro), así que el mapa
fallaba al construirse.

**Este era un defecto real y preexistente del backend**, no introducido por
este cambio: cualquier usuario real registrado sin cédula o sin teléfono
recibía un 500 al iniciar sesión, nunca un login exitoso. En el momento de
esta corrida, la solución para este punto fue que `seed-e2e.sql` sembrara
`cedula` y `telefono` con valores no nulos para el usuario admin de prueba,
para no bloquear el punto 13 en la corrección de un bug ajeno a su alcance
original.

## Verificación final (corrida original, con el esquive documentado)

- Run: checks del commit
  `https://github.com/JoseLozanoMorales/TiendaTech/commit/f9981a6/checks`
- Commit: `f9981a6` ("corrección del seed E2E: cedula/telefono no nulos
  para evitar NPE en login")
- Job "Playwright web E2E": **succeeded**, "Run browser journeys" en 10s
  (3 pruebas pasadas) contra el sistema completo real, sin ningún
  interceptor.

## Corrección de fondo del NPE (18 de septiembre de 2026)

El bug descrito arriba se esquivaba, no se corregía: `seed-e2e.sql` seguía
sembrando `cedula`/`telefono` no nulos a propósito, y `LoginController.login()`
seguía construyendo la respuesta con `Map.of(...)`, que lanza
`NullPointerException` ante cualquier valor `null`. Cualquier usuario real
sin cédula o sin teléfono seguía recibiendo un 500 al iniciar sesión.

Se corrigió el origen: `LoginController.java` ahora arma `userPayload` con
un `LinkedHashMap` mutable (`put` en vez de `Map.of(...)`), que sí tolera
valores `null`. `seed-e2e.sql` conserva sus valores no nulos para el admin
de prueba (documentado, no es el defecto), pero el login ya no depende de
esa siembra: un usuario real sin cédula o teléfono ahora inicia sesión sin
error 500.

Además, la prueba "un administrador inicia sesion y gestiona productos"
(`auth-admin.spec.js`) solo abría el diálogo "Crear producto" sin escribir
nada — el nombre afirmaba una gestión que la prueba no realizaba. Ahora
completa nombre y precio, envía el formulario y verifica el mensaje de
éxito y que el producto nuevo aparece en la tabla: hace una escritura real
contra el sistema, no solo una lectura.
