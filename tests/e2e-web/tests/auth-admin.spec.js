import { expect, test } from '@playwright/test'

test('un visitante no puede abrir administracion', async ({ page }) => {
  await page.goto('./#/admin', { waitUntil: 'domcontentloaded' })
  await expect(page).toHaveURL(/#\/login\?next=%2Fadmin/)
  await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible()
})

// Antes de esta prueba, un interceptor devolvía un usuario y un token
// inventados sin que la petición de login saliera nunca hacia el servidor.
// Ahora inicia sesión contra el sistema real: docs/db/seed-e2e.sql siembra
// el usuario "admin"/"Secreto123!" con rol administrador (rol_id 1) antes de
// que corra esta suite, y POST /api/login llega de verdad al gateway y de
// ahí a usuarios-service, que valida el hash BCrypt real.
test('un administrador inicia sesion y gestiona productos', async ({ page }) => {
  await page.goto('./#/login?next=%2Fadmin', { waitUntil: 'domcontentloaded' })
  await page.getByLabel('Usuario').fill('admin')
  await page.getByLabel('Contraseña').fill('Secreto123!')
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page).toHaveURL(/#\/admin$/)
  await expect(page.getByRole('heading', { name: 'Vista general' })).toBeVisible()
  await page.getByRole('button', { name: /Productos/ }).click()
  await expect(page.getByRole('heading', { name: 'Gestión de productos' })).toBeVisible()
  await expect(page.getByText('Procesador Ryzen 7')).toBeVisible()
  await page.getByRole('button', { name: 'Crear producto' }).click()
  await expect(page.getByRole('heading', { name: 'Crear producto' })).toBeVisible()

  // Hasta aqui la prueba solo abria el dialogo, sin escribir nada (hallazgo
  // del punto 13: el nombre afirmaba "gestiona productos" sin ninguna
  // gestion real). Categoria, marca, gama e IVA ya vienen precargados con el
  // primer valor disponible (ver defaults() en AdminView.tsx); solo faltan
  // nombre y precio, que son obligatorios.
  const nombreProducto = 'Auriculares E2E administracion'
  await page.getByLabel('Nombre').fill(nombreProducto)
  await page.getByLabel('Precio').fill('199.99')
  await page.getByRole('button', { name: 'Guardar producto' }).click()

  await expect(page.getByText('Producto creado correctamente.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Crear producto' })).toBeHidden()
  await expect(page.getByText(nombreProducto)).toBeVisible()
})
