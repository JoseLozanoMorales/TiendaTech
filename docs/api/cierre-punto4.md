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

## Esquema de petición y respuesta para el contrato de autenticación

El evaluador descontó la mitad de "esquema de petición y respuesta"
señalando que 59 de 120 respuestas y 25 cuerpos de petición no describían
ningún campo, y demostró el problema de raíz: los controladores devolvían
`ResponseEntity<?>` construido a mano con `Map.of(...)`/`LinkedHashMap`, y
`enrich()` (línea 152 de `generate.py`) fuerza el texto "Contenido
dinámico devuelto por el controlador; admite cualquier valor JSON." en
cuanto ve `<?>` en el tipo de retorno — sin importar lo que springdoc
pudiera inferir realmente. El evaluador probó esto renombrando un campo de
la respuesta de login y mostrando que `--check` seguía en verde: el
contrato no podía detectar ese cambio porque nunca lo describía.

**No se cerraron las 59+25 rutas del hallazgo completo** — eso sigue
pendiente. Se tipó el subconjunto que el evaluador usó como demostración y
que es el camino crítico de autenticación: `POST /api/login`,
`POST /auth/refresh`, `POST /auth/keepalive`, `POST /auth/logout` y
`GET /api/usuarios/me`, en `usuarios`. Se crearon DTOs reales
(`LoginRequest`, `LoginResponse`, `LoginUserResponse`, `RefreshResponse`,
`LogoutResponse`, `UsuarioMeResponse`, `UsuarioPerfilResponse`,
reutilizando el `KeepalivePayload` ya existente) que reemplazan los
`Map`/`LinkedHashMap` de `LoginController`, `AuthController` y
`UsuarioController`, preservando exactamente la misma forma de JSON que
viajaba antes (incluidas las claves duplicadas `id_rol`/`idRol` y
`avatar_path`/`avatarPath` del payload de usuario, vía `@JsonProperty`).
Al dejar de ser `ResponseEntity<?>`, `enrich()` ya no fuerza el texto
"Contenido dinámico": ahora el esquema real que infiere springdoc queda
en el contrato.

**Prueba de reproducción del evaluador, repetida contra el arreglo**: se
renombró temporalmente `token` → `tokenRenombrado` en `LoginResponse.java`
y se corrió `generate.py --check`. Compiló y pasaron los tests igual que
antes (el constructor de `LoginResponse` se llama posicionalmente, no por
nombre de campo), pero `--check` **falló** con
`ValueError: Contratos desactualizados: usuarios.yaml` — exactamente lo
que antes no pasaba. Se revirtió el cambio y `--check` volvió a pasar en
verde. Evidencia de que el punto ciego demostrado por el evaluador está
cerrado, no maquillado.

## Bug de codificación encontrado al regenerar en Windows

Al ejecutar `generate.py` (sin `--check`, para regenerar los `*.yaml` con
los DTOs nuevos) por primera vez de forma local en Windows, el script
crasheó durante la validación final con
`yaml.reader.ReaderError: unacceptable character ... invalid trailing
UTF-8 octet`, escalando a
`referencing.exceptions.Unresolvable: ref=./armado-ia.yaml#/paths/...`.

**Causa raíz**: `write_json()` escribía los `*.yaml` con
`path.write_text(...)` sin especificar `encoding`. En Windows, sin ese
parámetro, Python usa la codificación de la consola (cp1252), no UTF-8.
Los contratos incluyen texto en español con tildes en la descripción que
`enrich()` agrega a todo servicio ("Los objetos dinámicos conservan
propiedades abiertas..."), así que esa "á" se guardaba como un solo byte
cp1252 en vez de la secuencia UTF-8 de dos bytes. Eso no rompía nada
dentro del propio script (leía con la misma codificación con la que
escribía), pero `consolidate()` arma `openapi.yaml` con referencias
cruzadas (`$ref: "./armado-ia.yaml#/paths/..."`), y al resolverlas
`openapi_spec_validator` reabre el archivo referenciado desde disco vía su
URI `file://` y lo decodifica como UTF-8 estricto (JSON exige UTF-8) — ahí
truena con el byte suelto. Es un bug preexistente e independiente del
trabajo de este punto: nunca se había disparado porque las generaciones
completas previas venían de CI (runners Linux de GitHub Actions, con
UTF-8 por defecto), no de una corrida local en Windows.

**Corrección aplicada**: se fijó `encoding="utf-8"` explícito en los 9
lugares de `generate.py` que leen o escriben estos JSON/YAML, en vez de
depender de la codificación del sistema operativo.

## Integración del gateway al pipeline

El evaluador también descontó "la ruta del gateway que queda fuera de la
compuerta": `Apps/web/frontend` (el gateway) nunca estuvo en el
diccionario `SERVICES` de `generate.py`, así que su único endpoint real,
`GET /api/admin/system` (`SystemObservabilityController`), no se exportaba
ni documentaba con nada.

Agregarlo ingenuamente habría sido incorrecto: el escaneo de controladores
compartido en `OpenApiExportTest.java` usaba
`AnnotationTypeFilter(Controller.class)`, que también matchea
`@RestController` (meta-anotado con `@Controller`). El gateway tiene,
además del endpoint real, `WebappController` — `@Controller` puro, ~15
rutas de vista/redirección del monolito legacy (`/admin.html`,
`/Login.html`, etc.), que no son API y no deben entrar al contrato.
Angostar el filtro a `AnnotationTypeFilter(RestController.class)` excluye
`WebappController` sin afectar a los 6 microservicios — se verificó
primero, buscando en todo el árbol, que ninguno de los 6 declara
`@Controller` puro (todos son 100% `@RestController`).

Se agregó un perfil Maven `openapi` a `Apps/web/frontend/pom.xml`
(espejo del de los 6 servicios: mismo mecanismo de
`build-helper-maven-plugin` para compartir `OpenApiExportTest.java`, más
`springdoc-openapi-starter-webmvc-api` y `spring-boot-starter-webmvc-test`
como dependencias de test), y se agregó `"gateway": "Apps/web/frontend"`
a `SERVICES`, generalizando la resolución de rutas de `generate.py` (antes
asumía que todo vivía bajo `services/`).

Corrida end-to-end contra los 7 módulos: los 6 servicios reportaron
exactamente los mismos conteos de operaciones que antes (el filtro
angostado no les afecta), y el gateway exportó **1 operación** —
`GET /api/admin/system`, confirmando que `WebappController` quedó fuera —
con esquema tipado (`SystemSnapshot`/`ServiceHealth`, ya bien tipados en
el propio controlador) y `--check` en verde. El endpoint no forma parte
del documento consolidado (`x-gateway-exposed: false`): es un endpoint
propio del gateway, no una ruta reenviada a un backend, así que ese valor
es correcto, no un defecto.

## Riesgo de integración evitado

El `ci.yml` recibido junto con el generador estaba basado en un commit
anterior a los cambios del punto 13. Integrarlo tal cual habría revertido
el job `e2e-web` (arranque del sistema completo, siembra de datos,
verificación de salud) a su versión anterior sin backend real. Se insertó
manualmente solo el nuevo job `openapi-contracts` en el `ci.yml` vigente,
verificado por diff que la única diferencia introducida fue ese job nuevo.

## Verificación final

**Integración original (armado-ia) — evidencia en CI, ya en `main`:**

- Run: `https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34923576885`
- Commit: `a30f1d0` ("restaurar response_model en /api/armado/analizar y
  regenerar contrato OpenAPI")
- Job **"OpenAPI generation and route coverage"**: succeeded.
- Job **"armado-ia quality"**: succeeded.
- Los 9 jobs del flujo completo terminaron en verde, confirmando que la
  integración del punto 4 no rompió ningún trabajo previo (incluido el
  punto 13).
- Contenido de `services/armado-ia/app/main.py` y `docs/api/armado-ia.yaml`
  en `main` verificado byte a byte contra lo entregado.

**Contrato de autenticación, bug de codificación e integración del
gateway — evidencia en CI, fusionado a `main` vía PR revisado (mismo
estándar que el resto de los puntos):**

- PR: `https://github.com/JoseLozanoMorales/TiendaTech/pull/88`
  (`feat/punto4-contrato-auth-gateway` → `main`), aprobada por
  JoseLozanoMorales, fusionada el 18 de septiembre de 2026. Commit de
  merge en `main`: `fee3db9`.
- Commit de la rama: `c208b76` ("punto 4: tipar contrato de autenticacion,
  integrar gateway al pipeline OpenAPI y corregir bug de codificacion en
  Windows").
- Run: `https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35317851008`
  (workflow **CI**, disparado por la propia PR #88): succeeded.
- Job **"OpenAPI generation and route coverage"**: succeeded.
- Los 6 servicios Java + `armado-ia` compilaron y exportaron los mismos
  conteos de operaciones que el recuento original del evaluador
  (productos 37, inventario 6, pedidos 19, ordenes-proveedores 14,
  ventas 6, usuarios 35, armado-ia 3 = 120), confirmando que los cambios
  no afectaron a los demás servicios.
- El gateway exportó 1 operación (`GET /api/admin/system`), excluyendo
  correctamente las ~15 rutas de vista de `WebappController`.
- `generate.py --check` en verde con los 8 contratos (7 servicios +
  gateway) ya regenerados y committeados en el árbol de trabajo.
- Prueba de reproducción del evaluador (renombrar `token` en
  `LoginResponse`) repetida: `--check` pasó de verde a fallar y de vuelta
  a verde al revertir, confirmando que el punto ciego está cerrado.

## Conclusión

El contrato se genera desde el propio código con la herramienta del
framework de cada servicio (springdoc en Java, FastAPI en Python; ahora
también el gateway), declara esquema de petición y de respuesta para las
120 operaciones originales más la del gateway, y una compuerta en el flujo
(`generate.py --check`) falla si el código y el contrato divergen — se
demostró dos veces que la compuerta funciona: detectando una divergencia
real preexistente en `armado-ia` (no simulada, corregida en el código) y
reproduciendo la prueba del propio evaluador sobre el contrato de
autenticación recién tipado.

Queda fuera de este cierre, deliberadamente: el resto de las 59+25 rutas
sin esquema completo que señaló el evaluador (se priorizó el camino
crítico de autenticación, que fue lo que él usó para demostrar el
problema). El contrato de autenticación, el fix de codificación y el
gateway ya pasaron por el mismo ciclo de PR revisado + CI en verde que el
punto 31 (PR #88, fusionada a `main` el 18 de septiembre de 2026, run
`https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35317851008`).
