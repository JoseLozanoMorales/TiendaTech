import assert from 'node:assert/strict'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { MatchersV3, PactV4, SpecificationVersion } from '@pact-foundation/pact'

const { integer, like, regex } = MatchersV3
const ISO_8601 = '^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z?$'

test('mobile inicia sesion con credenciales validas', async () => {
  const pact = new PactV4({
    consumer: 'tiendatech-mobile',
    provider: 'usuarios-service',
    spec: SpecificationVersion.SPECIFICATION_VERSION_V4,
    // fileURLToPath normaliza la ruta en cualquier SO; .pathname deja la
    // ruta de Windows como "/C:/Users/..." y el nucleo nativo de Pact la
    // rechaza (os error 123: sintaxis de ruta invalida).
    dir: fileURLToPath(new URL('../pacts', import.meta.url)),
  })

  await pact
    .addInteraction()
    .given('el usuario cliente existe y esta habilitado')
    .uponReceiving('una solicitud de inicio de sesion valida')
    .withRequest('POST', '/api/login', builder => {
      builder.headers({ 'Content-Type': 'application/json' })
      builder.jsonBody({ usuario: like('cliente'), contrasena: like('Secreto123!') })
    })
    .willRespondWith(200, builder => {
      builder.headers({ 'Content-Type': like('application/json') })
      // El servicio envuelve toda respuesta 2xx con ApiResponseAdvice en
      // {status, data, message, timestamp}; el payload del login va dentro
      // de "data", como lo consume la app movil real (ApiEnvelope<LoginResponse>).
      builder.jsonBody({
        status: integer(200),
        message: like('OK'),
        timestamp: regex(ISO_8601, new Date().toISOString()),
        data: {
          user: {
            usuarioId: integer(20),
            usuario: like('cliente'),
            nombre: like('Cliente Pact'),
            idRol: integer(2),
          },
          access: regex('^[A-Za-z0-9._-]+$', 'pact-access-token'),
        },
      })
    })
    .executeTest(async mockServer => {
      const response = await fetch(`${mockServer.url}/api/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuario: 'cliente', contrasena: 'Secreto123!' }),
      })
      assert.equal(response.status, 200)
      const envelope = await response.json()
      assert.equal(envelope.status, 200)
      assert.equal(envelope.data.user.idRol, 2)
      assert.ok(envelope.data.access)
    })
})
