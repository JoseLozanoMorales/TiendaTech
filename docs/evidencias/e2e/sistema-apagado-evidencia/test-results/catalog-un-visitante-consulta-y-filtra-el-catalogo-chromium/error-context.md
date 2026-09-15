# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: catalog.spec.js >> un visitante consulta y filtra el catalogo
- Location: tests/catalog.spec.js:10:1

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByRole('heading', { name: 'Procesador Ryzen 7' })
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for getByRole('heading', { name: 'Procesador Ryzen 7' })

```

```yaml
- banner:
  - link "TT TiendaTech":
    - /url: "#/"
  - navigation "Navegación principal":
    - link "Productos":
      - /url: "#/"
    - link "Arma tu PC":
      - /url: "#/armado"
    - link "Carrito":
      - /url: "#/carrito"
  - button "Usar tema oscuro": ☾
  - link "Ingresar":
    - /url: "#/login"
- main:
  - paragraph: Tecnología a tu medida
  - heading "Todo para construir algo increíble." [level=1]:
    - text: Todo para construir
    - emphasis: algo increíble.
  - paragraph: Explora componentes, compara opciones y arma el equipo ideal.
  - paragraph: Catálogo
  - heading "Encuentra tu componente" [level=2]
  - searchbox "Buscar producto"
  - button "Todos"
  - paragraph: No encontramos productos en las páginas cargadas.
  - button "Cargando más productos…" [disabled]
- contentinfo: © 2026 TiendaTech · Arquitectura de microservicios
```

# Test source

```ts
  1  | import { expect, test } from '@playwright/test'
  2  | 
  3  | // Antes de esta prueba, un interceptor global (page.route('**/api/**', ...))
  4  | // respondía a toda llamada al servidor con datos constantes escritos en el
  5  | // propio archivo de prueba, así que la petición real nunca salía. Ahora la
  6  | // prueba navega contra el sistema real: el trabajo del flujo de CI levanta
  7  | // CockroachDB y los microservicios (ver .github/workflows/ci.yml) y siembra
  8  | // el producto "Procesador Ryzen 7" con docs/db/seed-e2e.sql antes de correr
  9  | // esta suite, así que /api/productos responde desde la base real.
  10 | test('un visitante consulta y filtra el catalogo', async ({ page }) => {
  11 |   await page.goto('./#/', { waitUntil: 'domcontentloaded' })
  12 |   await expect(page.getByRole('heading', { name: 'Encuentra tu componente' })).toBeVisible()
> 13 |   await expect(page.getByRole('heading', { name: 'Procesador Ryzen 7' })).toBeVisible()
     |                                                                           ^ Error: expect(locator).toBeVisible() failed
  14 |   await page.getByRole('searchbox', { name: 'Buscar producto' }).fill('producto inexistente')
  15 |   await expect(page.getByText('No encontramos productos')).toBeVisible()
  16 | })
  17 | 
```