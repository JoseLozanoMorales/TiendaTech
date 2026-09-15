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

## Verificación final

- Run: `https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/266`
- Commit: `883cb4b` ("verificación de proveedor en usuarios y productos;
  corrige drift real idRol/id_rol")
- Job **"Pact provider verification (usuarios-service)"**: succeeded.
- Job **"Pact provider verification (productos-service)"**: succeeded.
- Los 12 jobs del flujo completo terminaron en verde.
- Contenido de `LoginController.java` y ambos `pom.xml` en `main`
  verificado directamente contra el repositorio remoto.

## Conclusión

Ya existe verificación de proveedor real sobre los dos servicios con
contratos versionados, enganchada al flujo de CI, y se demostró
explícitamente que la verificación falla ante un cambio deliberado de
forma en una respuesta. Además, la compuerta demostró su valor real el
primer día que corrió: encontró una divergencia genuina y preexistente
entre el móvil y el backend (no simulada) que ningún mecanismo anterior
detectaba, y esa divergencia quedó corregida en el mismo cierre.
