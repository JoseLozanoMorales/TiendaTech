# Cierre — Punto 12 (Pruebas de contrato)

## Situación original (resuelta)

El directorio de contratos solo tenía lado consumidor: dos pruebas
(`tests/contract/tests/*.pact.test.js`) que generaban dos pacts
(`tiendatech-mobile-usuarios-service.json`,
`tiendatech-webapp-productos-service.json`), cada uno con una sola
interacción y su estado de proveedor declarado. No existía ninguna
verificación de proveedor sobre Java, JavaScript ni configuración: cero
apariciones en todo el repositorio de los términos con los que se escribe
una verificación de este tipo. El job `contract-tests` solo regeneraba el
contrato del consumidor y lo archivaba como artefacto. El propio índice del
directorio admitía que los estados «documentan las precondiciones que deben
implementar los verificadores», verificadores que no existían. La
consecuencia práctica: un cambio en la forma de una respuesta no rompía
nada en el flujo, que es justo lo que un contrato debe detectar.

## Qué se implementó

- Perfil Maven `pact` en `services/usuarios/pom.xml` y
  `services/productos-service/pom.xml` — mismo patrón que el perfil
  `openapi` del punto #4 (dependencia solo en el perfil,
  `build-helper-maven-plugin` para sumar un directorio de test-sources
  extra), de modo que `au.com.dius.pact.provider:junit5:4.6.15` no se
  agrega al build ni al `mvn test` normales.
- Una prueba de verificación de proveedor por servicio
  (`UsuariosProviderVerificationTest.java`,
  `ProductosProviderVerificationTest.java`), en un directorio propio
  (`src/test/pact-java`) que solo se compila bajo el perfil `pact`. Ambas
  montan el **controlador real** (`LoginController` / `ProductoController`)
  con el mismo patrón mínimo de `OpenApiExportTest.java` del punto #4:
  escaneo de `@Controller` por reflexión, registro como bean real,
  colaboradores mockeados genéricamente. A diferencia de `OpenApiExportTest`
  (que usa un `MockServletContext` sin puerto), aquí se levanta un
  **Tomcat embebido real** en un puerto aleatorio, porque `pact-jvm` 4.6.x
  no publica módulo `MockMvcTestTarget` para Spring Framework 7 (Boot 4,
  usado por `usuarios`) — solo para Spring 6.
- Los dos `@State` pedidos, implementados mockeando solo la capa de
  servicio (`UsuarioService.login(...)`, `ProductoService.listar(0, 12)`)
  con datos de entrada controlados, dejando que el controlador real
  produzca la respuesta JSON — que es exactamente la lógica que el
  contrato debe verificar.
- Dos jobs nuevos en `.github/workflows/ci.yml`
  (`provider-verification-usuarios`, `provider-verification-productos`),
  con `needs: contract-tests`, insertados sobre el `ci.yml` vigente sin
  tocar ningún job existente (verificado por diff).

## Demostración pedida por la rúbrica

Se renombró temporalmente `@JsonProperty("producto_id")` a `"productoId"`
en `ProductoResumenResponse.java`, se corrió la verificación de proveedor
de `productos-service`, y falló exactamente como se esperaba:

```
1.1) body: $[0] Actual map is missing the following keys: producto_id
```

El cambio fue revertido de inmediato; el repositorio nunca quedó con esa
ruptura (`productos-DELIBERADO-producto_id-renombrado-ROJO.log`,
`productos-REVERTIDO-VERDE.log`).

## Hallazgo real encontrado y corregido en el camino

Al ejecutar la verificación de `usuarios-service` contra el pact **tal
cual estaba en el repositorio**, la verificación falló de verdad, sin
ningún cambio deliberado:

```
1.1) body: $.user Actual map is missing the following keys: idRol
```

`LoginController.java` arma la respuesta de `/api/login` con la clave
literal `"id_rol"`, pero el pact de consumidor (generado por
`mobile-login.pact.test.js`) esperaba `"idRol"`. Este no es un hallazgo
fabricado para la demostración: la prueba monta el controlador real, no un
doble, y reveló una divergencia preexistente entre lo que el móvil asumía
y lo que el backend realmente enviaba. Confirmado además en el lado móvil:
`AuthApi.kt` ya declaraba un campo `idRol` separado (sin `@SerialName`,
esperando literalmente esa clave) que, al nunca recibirla, quedaba
siempre `null` en producción.

**Corrección aplicada** (`LoginController.java`, aditiva, sin quitar nada):

```java
Map<String, Object> userPayload = Map.of(
        "usuarioId", u.getUsuarioId(),
        "usuario",   u.getUsuario(),
        "nombre",    u.getNombre(),
        "cedula",    u.getCedula(),
        "correo",    u.getCorreo(),
        "telefono",  u.getTelefono(),
        "id_rol",    u.getIdRol(),
        "idRol",     u.getIdRol()
);
```

Se verificó antes de aplicar que esto no reintroduce el riesgo de
`NullPointerException` de `Map.of()` documentado en el cierre del #13: la
clave `"id_rol"` ya usa el mismo valor `u.getIdRol()` hoy, así que si ese
valor fuera `null` el `Map.of()` ya fallaría en esa entrada antes de
llegar a la nueva — la corrección no añade ninguna superficie de riesgo
que no existiera ya.

## Verificación final (cierre original, commit 883cb4b)

- Run citado en su momento: `.../actions/runs/266` — **este enlace ya no
  resuelve** (404) y no debe usarse como evidencia; ver la corrección de
  abajo.
- Commit: `883cb4b` ("verificación de proveedor en usuarios y productos;
  corrige drift real idRol/id_rol")
- Job **"Pact provider verification (usuarios-service)"**: succeeded.
- Job **"Pact provider verification (productos-service)"**: succeeded.
- Los 12 jobs del flujo completo terminaron en verde.
- Contenido de `LoginController.java` y ambos `pom.xml` en `main`
  verificado directamente contra el repositorio remoto.

## Corrección posterior (16 de septiembre de 2026): el arnés excluía el envoltorio real

La revisión externa del docente reprodujo el defecto de fondo que el cierre
original no detectó: `scanner.addIncludeFilter(new
AnnotationTypeFilter(Controller.class))` en ambos arneses solo registra
clases `@Controller`. `ApiResponseAdvice` es `@RestControllerAdvice` (meta
anota `@ControllerAdvice`, no `@Controller`), así que nunca se registraba
como bean, Spring nunca armaba la cadena `ResponseBodyAdvice`, y la
verificación comparaba contra el cuerpo crudo del controlador en vez del
envoltorio `{status,data,message,timestamp}` que el sistema real emite en
producción. Además, los dos pacts de consumidor (`tiendatech-mobile-usuarios-
service.json`, `tiendatech-webapp-productos-service.json`) pedían la forma
sin envolver, contradiciendo a los clientes reales (móvil: `Response<ApiEnvelope<LoginResponse>>`;
web: desenvuelve `data` en `services/api.ts`).

**Corrección aplicada:**

- `UsuariosProviderVerificationTest.java` y
  `ProductosProviderVerificationTest.java`: se añadió
  `scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class))`
  junto al filtro de `@Controller` ya existente, sin tocar código de
  producción.
- `tests/contract/tests/mobile-login.pact.test.js` y
  `web-catalog.pact.test.js`: el cuerpo esperado ahora exige el envoltorio
  real (`data.user`, `data.access` / `data` como arreglo), regenerando los
  dos `.json` versionados con `npm test`.
- De paso, se corrigió un bug preexistente de portabilidad en ambos
  `.pact.test.js`: `new URL('../pacts', import.meta.url).pathname` producía
  una ruta inválida en Windows (`/C:/Users/...`, rechazada por el núcleo
  nativo de Pact con "os error 123"); se reemplazó por
  `fileURLToPath(new URL('../pacts', import.meta.url))`.
- `.github/workflows/ci.yml`, job `contract-tests`: se añadió el paso
  `git diff --exit-code -- pacts/` inmediatamente después de `npm test`,
  para que el job falle si el contrato regenerado difiere del commiteado
  (la brecha de control de deriva que señaló la revisión externa).

**Verificación local tras el fix** (perfil `pact`, `mvn -Ppact
-Dtest=...ProviderVerificationTest test`):

```
Verifying a pact between tiendatech-mobile and usuarios-service
  ... has a matching body (OK)
Verifying a pact between tiendatech-webapp and productos-service
  ... has a matching body (OK)
```

**Prueba de mutación repetida con el arnés ya corregido** (esta vez sobre el
envoltorio real, no sobre el cuerpo crudo): se renombró temporalmente
`"access"` → `"tokenRenombrado"` en la respuesta de `LoginController.login`.
La verificación falló exactamente como se esperaba:

```
1.1) body: $.data Actual map is missing the following keys: access

    {
    -  "access": "pact-access-token",
    +  "success": true,
    +  "token": "pact-access-token",
    +  "tokenRenombrado": "pact-access-token",
      "user": { ... }
    }
```

El cambio se revirtió de inmediato; `LoginController.java` en el árbol de
trabajo quedó verificado byte a byte contra su versión anterior a la
mutación antes de continuar.

## Verificación final (esta corrección)

- Run: `https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35166009044`
- Commit: `a266253` ("fijar el timestamp de ejemplo en los pacts para que la
  compuerta git diff sea reproducible") — segundo commit de esta corrección;
  el primero (arnés + pacts enganchados al envoltorio real + esta
  documentación + la compuerta de deriva) se subió inmediatamente antes,
  sobre el mismo `main`.
- Job **"Pact consumer contracts"**: succeeded — incluye el paso nuevo
  `git diff --exit-code -- pacts/` sin diferencias.
- Job **"Pact provider verification (usuarios-service)"**: succeeded.
- Job **"Pact provider verification (productos-service)"**: succeeded.
- Los 12 jobs del flujo completo terminaron en verde.

Nota sobre el commit intermedio: la primera subida de esta corrección hizo
fallar el job `contract-tests` en CI (`git diff --exit-code` detectó que el
`timestamp` de ejemplo cambiaba en cada regeneración porque el test usaba
`new Date().toISOString()`). Se corrigió fijando ese valor de ejemplo a una
constante (`2026-01-01T00:00:00.000Z`); el matcher `regex` que valida el
formato ISO 8601 real durante la verificación no cambió, así que esto no
relaja ninguna comprobación — ver la nota en
`tests/contract/tests/*.pact.test.js` junto a `TIMESTAMP_EJEMPLO`. El run
citado arriba (`35166009044`) es el que ya incluye esa corrección y quedó
en verde de punta a punta.

## Conclusión

Ya existe verificación de proveedor real sobre los dos servicios con
contratos versionados, enganchada al flujo de CI, y se demostró
explícitamente — dos veces, con el arnés original y de nuevo con el
arnés corregido — que la verificación falla ante un cambio deliberado de
forma en una respuesta. El enlace de evidencia del cierre original (`.../runs/266`)
quedó roto y se reemplaza aquí por el run real y vigente citado arriba. La
compuerta de deriva (`git diff --exit-code`) añadida en esta corrección
cierra además el hallazgo de que un cambio en el consumidor podía no
propagarse nunca al `.json` versionado sin que CI lo notara, y su propia
puesta en marcha demostró que funciona: detectó una fuente de no-determinismo
real (el timestamp) en el primer intento.
