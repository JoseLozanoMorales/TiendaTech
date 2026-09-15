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

La primera corrida contra el sistema real (run
`https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/CI-260`,
commit `5362f27`) levantó el stack completo correctamente y el catálogo
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

**Esto es un defecto real y preexistente del backend**, no introducido por
este cambio: cualquier usuario real registrado sin cédula o sin teléfono
recibiría un 500 al iniciar sesión, nunca un login exitoso. La corrección
de fondo (cambiar `Map.of(...)` por una construcción que tolere valores
nulos) queda fuera del alcance de este punto y debería tratarse como una
tarea aparte del equipo. Como solución para este punto, `seed-e2e.sql`
siembra `cedula` y `telefono` con valores no nulos para el usuario admin
de prueba, evitando así depender de un bug ajeno al punto 13.

## Verificación final

- Run: `https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/CI-261`
- Commit: `f9981a6` ("corrección del seed E2E: cedula/telefono no nulos
  para evitar NPE en login")
- Job "Playwright web E2E": **succeeded**, "Run browser journeys" en 10s
  (3 pruebas pasadas) contra el sistema completo real, sin ningún
  interceptor.
