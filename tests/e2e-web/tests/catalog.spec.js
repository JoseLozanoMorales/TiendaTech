import { expect, test } from '@playwright/test'

// Antes de esta prueba, un interceptor global (page.route('**/api/**', ...))
// respondía a toda llamada al servidor con datos constantes escritos en el
// propio archivo de prueba, así que la petición real nunca salía. Ahora la
// prueba navega contra el sistema real: el trabajo del flujo de CI levanta
// CockroachDB y los microservicios (ver .github/workflows/ci.yml) y siembra
// el producto "Procesador Ryzen 7" con docs/db/seed-e2e.sql antes de correr
// esta suite, así que /api/productos responde desde la base real.
test('un visitante consulta y filtra el catalogo', async ({ page }) => {
  await page.goto('./#/', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Encuentra tu componente' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Procesador Ryzen 7' })).toBeVisible()
  await page.getByRole('searchbox', { name: 'Buscar producto' }).fill('producto inexistente')
  await expect(page.getByText('No encontramos productos')).toBeVisible()
})
