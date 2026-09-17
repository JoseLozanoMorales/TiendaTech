# Pruebas de contrato Pact

Contratos *consumer-driven* de las interfaces HTTP consumidas por la web y la
aplicación móvil. Las pruebas generan contratos Pact V4 en `pacts/`.

```bash
cd tests/contract
npm ci
npm test
```

Los estados del proveedor documentan las precondiciones que deben implementar
los verificadores de `productos-service` y `usuarios-service`
(`services/*/src/test/pact-java/.../*ProviderVerificationTest.java`, perfil
Maven `pact`), enganchados a CI en los jobs `provider-verification-usuarios`
y `provider-verification-productos`. Ambos servicios envuelven toda
respuesta 2xx en `{status, data, message, timestamp}` vía `ApiResponseAdvice`;
los contratos aquí definidos reflejan ese envoltorio real, no el cuerpo sin
envolver del controlador.

CI conserva los Pact generados como evidencia auditable y, en el mismo job,
falla con `git diff --exit-code -- pacts/` si el contrato recién regenerado
difiere del que está commiteado — evita que un cambio en un test de
consumidor quede sin propagarse al `.json` versionado y a los verificadores
de proveedor. Si tocas un test de este directorio, corre `npm test` y
comitea el `.json` regenerado en el mismo cambio.
