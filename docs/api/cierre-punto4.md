# Cierre — Punto 4 (Contrato de interfaz OpenAPI)

## Situación original (resuelta)

El directorio de contratos no había sido tocado desde el 4 de septiembre:
ninguno de los treinta y dos commits correctivos posteriores trabajó sobre
él. Contando las operaciones realmente documentadas (resolviendo cada
referencia contra su archivo de servicio) sumaban 28, contra 119 rutas
expuestas en el código (113 anotaciones de ruta en clases de control, 3 de
nivel de método y 3 del servicio de inteligencia artificial): 91 quedaban
sin documentar. No existía ningún mecanismo de regeneración ni compuerta
automática, de modo que se podía añadir una ruta sin documentarla y el
flujo seguía en verde. Lo único resuelto era la estructura: los siete
contratos por servicio existían y el archivo consolidado los agregaba por
referencia sin duplicar definiciones.

## Qué se implementó

- `scripts/openapi/generate.py` genera cada contrato **desde el propio
  código**, usando la herramienta del framework de cada lado: en los seis
  servicios Java, un test (`OpenApiExportTest.java`) levanta un contexto
  Spring MVC real (controladores reales, colaboradores simulados) y
  consulta **springdoc** vía `GET /v3/api-docs`; en `armado-ia` (Python),
  se invoca directamente `app.openapi()` de FastAPI.
- El mismo test construye, de forma independiente, un segundo inventario
  de rutas leyendo directamente `RequestMappingHandlerMapping` de Spring
  (no springdoc), y `assert_coverage()` compara ambos: si springdoc y el
  registro real de rutas de Spring no coinciden exactamente, o si a
  cualquier operación le falta esquema de petición o de respuesta, la
  generación falla. Esto hace que el chequeo de cobertura no dependa de
  que springdoc esté "bien configurado": se contrasta contra la fuente de
  verdad del propio framework.
- `generate.py --check` compara la generación fresca contra lo committeado
  en `docs/api/*.yaml` byte a byte y falla si difieren — esta es la
  compuerta que faltaba: hoy no se puede añadir ni modificar una ruta sin
  que el flujo se rompa si el contrato no se regenera para reflejarla.
- Nuevo job `openapi-contracts` en `.github/workflows/ci.yml`: instala
  Java 21 y Python 3.12, corre las pruebas unitarias del generador
  (`test_generate.py`) y luego `generate.py --check`.
- Se declaran esquemas de petición y de respuesta para las 120 operaciones
  descubiertas dinámicamamente por el generador (no un número fijo escrito
  a mano), incluyendo seguridad (`bearerAuth`/`refreshCookie`),
  `operationId`, exposición real por el gateway (`x-gateway-exposed`,
  contrastado contra `application.yml`) y el sobre de respuesta uniforme
  (`{status, data, message, timestamp}`) que ya usan los servicios en
  producción.

## Bug real encontrado y corregido en el camino

La primera corrida del job `openapi-contracts` sobre el contrato integrado
falló con `ValueError: Contratos desactualizados: armado-ia.yaml`, aunque
`armado-ia: 3 operaciones verificadas` se imprimía correctamente (la
cobertura pasaba; solo fallaba la comparación de contenido con lo
committeado).

Diagnóstico reproducido localmente (entorno limpio, dependencias exactas
de `services/armado-ia/requirements.txt`): `services/armado-ia/app/main.py`
no declaraba `response_model=` en `/api/armado/analizar`, ni tipo de
retorno en `/actuator/health` ni en `/actuator/circuitbreakers`. FastAPI
solo puede describir la forma de una respuesta cuando el código la declara
explícitamente; sin ello, `app.openapi()` emite un esquema vacío (`{}`)
para esas rutas. El contrato committeado, en cambio, sí contenía los
esquemas completos (`AnalizarResponse`, `ComponenteResponse`,
`RecomendacionResponse`, y los tipos de las dos rutas de diagnóstico) —
señal de que la anotación existía cuando se generó por primera vez y se
perdió después en el código, sin que nada lo detectara: exactamente el
tipo de drift que este punto debía prevenir.

**Corrección aplicada** (`services/armado-ia/app/main.py`, solo anotaciones
de tipo, cero cambios de lógica de negocio):

```python
@app.post("/api/armado/analizar", response_model=AnalizarResponse)
def analizar(request: AnalizarRequest, identidad: IdentidadOpcional = Depends(identidad_requerida)):
    return armado_service.analizar(request, identidad, explicacion_service)

@app.get("/actuator/health")
def health() -> dict[str, str]:
    return {"status": "UP"}

@app.get("/actuator/circuitbreakers")
def circuitbreakers() -> dict[str, dict[str, dict[str, str | int | float | None]]]:
    return estado_circuit_breaker()
```

Se verificó que `armado_service.analizar()` siempre retorna una instancia
real de `AnalizarResponse` antes de aplicar el cambio, por lo que no hay
riesgo de romper validación en tiempo de ejecución. `docs/api/armado-ia.yaml`
se regeneró contra el código ya corregido y se validó como spec OpenAPI
válido (`openapi_spec_validator`).

## Riesgo de integración evitado

El `ci.yml` recibido junto con el generador estaba basado en un commit
anterior a los cambios del punto 13. Integrarlo tal cual habría revertido
el job `e2e-web` (arranque del sistema completo, siembra de datos,
verificación de salud) a su versión anterior sin backend real. Se insertó
manualmente solo el nuevo job `openapi-contracts` en el `ci.yml` vigente,
verificado por diff que la única diferencia introducida fue ese job nuevo.

## Verificación final

- Run: `https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/265`
- Commit: `a30f1d0` ("restaurar response_model en /api/armado/analizar y
  regenerar contrato OpenAPI")
- Job **"OpenAPI generation and route coverage"**: succeeded.
- Job **"armado-ia quality"**: succeeded.
- Los 9 jobs del flujo completo terminaron en verde, confirmando que la
  integración del punto 4 no rompió ningún trabajo previo (incluido el
  punto 13).
- Contenido de `services/armado-ia/app/main.py` y `docs/api/armado-ia.yaml`
  en `main` verificado byte a byte contra lo entregado.

## Conclusión

El contrato ahora se genera desde el propio código con la herramienta del
framework de cada servicio (springdoc en Java, FastAPI en Python), declara
esquema de petición y de respuesta para las 120 operaciones descubiertas
dinámicamente, y una compuerta en el flujo (`generate.py --check`) falla
si el código y el contrato divergen — se demostró que la compuerta
funciona porque detectó una divergencia real preexistente (no simulada) y
esta se corrigió en el código, no evadiéndola en el contrato.
