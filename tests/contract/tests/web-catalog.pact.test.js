import assert from 'node:assert/strict'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { MatchersV3, PactV4, SpecificationVersion } from '@pact-foundation/pact'

const { eachLike, integer, like, decimal, regex } = MatchersV3
const ISO_8601 = '^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z?$'
// Valor de ejemplo fijo: el matcher regex ya valida el formato ISO 8601 real
// en la verificacion; si aqui se usa new Date(), el .json regenerado nunca
// es identico al commiteado y la compuerta "git diff --exit-code -- pacts/"
// del job contract-tests siempre falla, aunque el contrato no haya cambiado.
const TIMESTAMP_EJEMPLO = '2026-01-01T00:00:00.000Z'

test('webapp obtiene el catalogo paginado', async () => {
  const pact = new PactV4({
    consumer: 'tiendatech-webapp',
    provider: 'productos-service',
    spec: SpecificationVersion.SPECIFICATION_VERSION_V4,
    // fileURLToPath normaliza la ruta en cualquier SO; .pathname deja la
    // ruta de Windows como "/C:/Users/..." y el nucleo nativo de Pact la
    // rechaza (os error 123: sintaxis de ruta invalida).
    dir: fileURLToPath(new URL('../pacts', import.meta.url)),
  })

  await pact
    .addInteraction()
    .given('existen productos habilitados')
    .uponReceiving('una solicitud de la primera pagina del catalogo')
    .withRequest('GET', '/api/productos', builder => {
      builder.query({ page: '0', size: '12' })
    })
    .willRespondWith(200, builder => {
      builder.headers({ 'Content-Type': like('application/json') })
      // El servicio envuelve toda respuesta 2xx con ApiResponseAdvice en
      // {status, data, message, timestamp}; el catalogo va dentro de "data",
      // como lo desenvuelve la webapp real en services/api.ts.
      builder.jsonBody({
        status: integer(200),
        message: like('OK'),
        timestamp: regex(ISO_8601, TIMESTAMP_EJEMPLO),
        data: eachLike({
          producto_id: integer(1),
          nombre: like('Procesador Ryzen 7'),
          preciounitario: decimal(349.99),
          stock: integer(10),
          habilitado: like(true),
        }),
      })
    })
    .executeTest(async mockServer => {
      const response = await fetch(`${mockServer.url}/api/productos?page=0&size=12`)
      assert.equal(response.status, 200)
      const envelope = await response.json()
      assert.ok(Array.isArray(envelope.data))
      assert.equal(typeof envelope.data[0].producto_id, 'number')
      assert.equal(typeof envelope.data[0].nombre, 'string')
    })
})
