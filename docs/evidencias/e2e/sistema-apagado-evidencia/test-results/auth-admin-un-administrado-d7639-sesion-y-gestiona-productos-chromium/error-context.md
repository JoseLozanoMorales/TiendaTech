# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth-admin.spec.js >> un administrador inicia sesion y gestiona productos
- Location: tests/auth-admin.spec.js:15:1

# Error details

```
Error: expect(page).toHaveURL(expected) failed

Expected pattern: /#\/admin$/
Received string:  "http://127.0.0.1:4173/app/#/login?next=%2Fadmin"
Timeout: 5000ms

Call log:
  - Expect "toHaveURL" with timeout 5000ms
    14 × locator resolved to <html lang="es">…</html>
       - unexpected value "http://127.0.0.1:4173/app/#/login?next=%2Fadmin"

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
  - paragraph: Bienvenido de vuelta
  - heading "Inicia sesión" [level=1]
  - paragraph: Accede a tu carrito, pedidos y configuraciones.
  - text: Usuario
  - textbox "Usuario":
    - /placeholder: Ingresa tu usuario
    - text: admin
  - text: Contraseña
  - textbox "Contraseña":
    - /placeholder: Ingresa tu contraseña
    - text: Secreto123!
  - alert: Error 500
  - button "Entrar"
  - link "Crear cuenta":
    - /url: "#/registro"
  - link "Olvidé mi contraseña":
    - /url: "#/recuperacion"
- contentinfo: © 2026 TiendaTech · Arquitectura de microservicios
```

# Test source

```ts
  1  | import { expect, test } from '@playwright/test'
  2  | 
  3  | test('un visitante no puede abrir administracion', async ({ page }) => {
  4  |   await page.goto('./#/admin', { waitUntil: 'domcontentloaded' })
  5  |   await expect(page).toHaveURL(/#\/login\?next=%2Fadmin/)
  6  |   await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible()
  7  | })
  8  | 
  9  | // Antes de esta prueba, un interceptor devolvía un usuario y un token
  10 | // inventados sin que la petición de login saliera nunca hacia el servidor.
  11 | // Ahora inicia sesión contra el sistema real: docs/db/seed-e2e.sql siembra
  12 | // el usuario "admin"/"Secreto123!" con rol administrador (rol_id 1) antes de
  13 | // que corra esta suite, y POST /api/login llega de verdad al gateway y de
  14 | // ahí a usuarios-service, que valida el hash BCrypt real.
  15 | test('un administrador inicia sesion y gestiona productos', async ({ page }) => {
  16 |   await page.goto('./#/login?next=%2Fadmin', { waitUntil: 'domcontentloaded' })
  17 |   await page.getByLabel('Usuario').fill('admin')
  18 |   await page.getByLabel('Contraseña').fill('Secreto123!')
  19 |   await page.getByRole('button', { name: 'Entrar' }).click()
  20 | 
> 21 |   await expect(page).toHaveURL(/#\/admin$/)
     |                      ^ Error: expect(page).toHaveURL(expected) failed
  22 |   await expect(page.getByRole('heading', { name: 'Vista general' })).toBeVisible()
  23 |   await page.getByRole('button', { name: /Productos/ }).click()
  24 |   await expect(page.getByRole('heading', { name: 'Gestión de productos' })).toBeVisible()
  25 |   await expect(page.getByText('Procesador Ryzen 7')).toBeVisible()
  26 |   await page.getByRole('button', { name: 'Crear producto' }).click()
  27 |   await expect(page.getByRole('heading', { name: 'Crear producto' })).toBeVisible()
  28 | })
  29 | 
```