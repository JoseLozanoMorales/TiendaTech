# Evidencia — la suite E2E falla con el sistema apagado (punto 13)

La rúbrica exige demostrar, con una corrida real, que la suite de Playwright
falla cuando el sistema real no está disponible — la contraparte de que hoy
pasaba precisamente porque nada dependía de él.

## Cómo se generó

Sobre un clon limpio de este repositorio, después de quitar el interceptor
global (`tests/e2e-web/tests/fixtures.js`) de `catalog.spec.js` y
`auth-admin.spec.js`, se ejecutó la suite **sin levantar `docker compose`**,
es decir exactamente en las condiciones que tenía el flujo de CI antes de
este cambio: solo el frontend arriba (el `webServer` de
`playwright.config.js`), sin CockroachDB ni microservicios.

```
cd tests/e2e-web
npx playwright test --project=chromium
```

## Resultado real obtenido

```
1) [chromium] › tests/auth-admin.spec.js:15:1 › un administrador inicia sesion y gestiona productos

   Error: expect(page).toHaveURL(expected) failed
   Expected pattern: /#\/admin$/
   Received string:  "http://127.0.0.1:4173/app/#/login?next=%2Fadmin"

2) [chromium] › tests/catalog.spec.js:10:1 › un visitante consulta y filtra el catalogo

   Error: expect(locator).toBeVisible() failed
   Locator: getByRole('heading', { name: 'Procesador Ryzen 7' })
   Expected: visible
   Error: element(s) not found

2 failed
  tests/auth-admin.spec.js:15:1 › un administrador inicia sesion y gestiona productos
  tests/catalog.spec.js:10:1 › un visitante consulta y filtra el catalogo
1 passed (17.5s)
```

Las dos pruebas que dependen del servidor (consultar el catálogo real e
iniciar sesión contra el backend real) fallan tal como se espera cuando el
sistema está apagado: el login nunca llega a `/#/admin` porque `POST
/api/login` no tiene backend que lo responda, y el catálogo nunca muestra
"Procesador Ryzen 7" porque `/api/productos` tampoco responde. La única
prueba que sigue pasando es la que **no depende del servidor** (el
redireccionamiento de un visitante anónimo hacia `/login`, que es lógica
puramente del cliente) — es justamente la única de las tres que la propia
rúbrica identificaba como no dependiente del interceptor.

Esto es la contraprueba de lo que documentaba la situación original: antes,
la suite pasaba con el sistema completo ausente porque el interceptor
respondía por él; ahora, sin el interceptor y sin el sistema, la suite
falla — que es la definición de una prueba de extremo a extremo real.

Las capturas de pantalla y el `trace.zip` de esta corrida (generados por
Playwright en `test-results/`) se adjuntaron como evidencia junto a este
documento.
