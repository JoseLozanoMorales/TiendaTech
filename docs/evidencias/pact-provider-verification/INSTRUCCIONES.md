# Punto 12 — Pruebas de contrato: verificación de proveedor Pact

Este paquete agrega la verificación de **proveedor** (lado servidor) que faltaba.
Ya existían los contratos de **consumidor** (`tests/contract/pacts/*.json`, generados
por `tests/contract/tests/*.pact.test.js` y validados en el job `contract-tests`).
Ninguno de esos tres elementos fue tocado.

## Qué se agregó

1. **Perfil Maven `pact`** en `services/productos-service/pom.xml` y
   `services/usuarios/pom.xml`, calcado del perfil `openapi` del punto #4
   (misma mecánica: dependencia solo en el perfil, `maven-surefire-plugin` con
   una system property, y `build-helper-maven-plugin` para sumar un
   directorio de test-sources extra). Con esto el jar
   `au.com.dius.pact.provider:junit5:4.6.15` **no** se agrega al build normal
   ni al `mvn test` de siempre.

2. **Un test de verificación de proveedor por servicio**, en un directorio
   propio de cada módulo para que solo se compile bajo el perfil `pact`:
   - `services/productos-service/src/test/pact-java/.../ProductosProviderVerificationTest.java`
   - `services/usuarios/src/test/pact-java/.../UsuariosProviderVerificationTest.java`

   Ambos montan el **controlador real** (`ProductoController` / `LoginController`)
   con el mismo patrón mínimo que `scripts/openapi/java/.../OpenApiExportTest.java`
   usó para el punto #4: escanean `@Controller` con reflexión, registran esa
   clase como bean real y mockean genéricamente (Mockito) cada dependencia de
   su constructor/`@Autowired`. A diferencia de `OpenApiExportTest` (que usa un
   `MockServletContext` falso sin puerto), aquí se levanta un **Tomcat embebido
   real** en un puerto aleatorio, porque `pact-jvm` 4.6.x no publica un módulo
   `MockMvcTestTarget` para Spring Framework 7 (Boot 4, usado por
   `usuarios-service`) — solo para Spring 6. Usar `HttpTestTarget` contra un
   servidor real evita ese problema y sirve igual para ambos servicios.

   Los `@ControllerAdvice`/`@RestControllerAdvice` (incluido `ApiResponseAdvice`,
   el envoltorio `{status,data,message,timestamp}`) **no** se registran, porque
   el escaneo solo busca `@Controller`. Esto es intencional: los contratos
   verifican la forma cruda que produce el controlador, igual que la ven las
   pruebas de consumidor.

   Se implementaron los dos `@State` pedidos:
   - `existen productos habilitados` → mockea `ProductoService.listar(0, 12)`
     para devolver un `ProductoResumen` con los mismos datos del pact
     (`producto_id`, `nombre`, `preciounitario`, `stock`, `habilitado`).
   - `el usuario cliente existe y esta habilitado` → mockea
     `UsuarioService.login("cliente","Secreto123!")` y
     `RefreshTokenService.issueOnLogin(...)` para que `LoginController` procese
     un login real de cliente (rol 2) sin tocar base de datos.

3. **Dos jobs nuevos en `.github/workflows/ci.yml`**, insertados justo después
   de `contract-tests` (que no se modificó):
   `provider-verification-usuarios` y `provider-verification-productos`,
   cada uno con `needs: contract-tests` y ejecutando
   `mvn -Ppact -Dtest=...ProviderVerificationTest test` en su propio módulo.

## Resultado real al ejecutar (evidencia en esta misma carpeta)

- **`productos-REVERTIDO-VERDE.log`** → `productos-service` verifica en verde
  contra `tiendatech-webapp-productos-service.json` tal cual está hoy: el
  catálogo real ya devuelve el array plano con `producto_id` que pide el pact.

- **`productos-DELIBERADO-producto_id-renombrado-ROJO.log`** → evidencia pedida
  explícitamente: se renombró temporalmente `@JsonProperty("producto_id")` a
  `"productoId"` en `ProductoResumenResponse.java`, se corrió la verificación
  (falla exactamente en `$[0]`, "Actual map is missing the following keys:
  producto_id") y **el cambio ya fue revertido** — el repo no quedó con esa
  ruptura (confirmado con `git status` después de revertir).

- **`usuarios-idRol-vs-id_rol-DRIFT-REAL.log`** → ⚠️ **hallazgo real, no
  fabricado**. Al verificar `usuarios-service` contra
  `tiendatech-mobile-usuarios-service.json` tal cual está en el repo hoy, la
  verificación **falla de verdad**:

  ```
  1.1) body: $.user Actual map is missing the following keys: idRol
  ```

  `LoginController.java` (línea 57) arma la respuesta de `/api/login` con la
  clave literal `"id_rol"`, pero el pact (generado por
  `mobile-login.pact.test.js`) espera `"idRol"`. Este test no inventa esa
  ruptura: solo la revela, porque monta el controlador real, no un doble. No
  se corrigió ni el pact ni el controlador porque:
  - Se me pidió explícitamente no tocar los archivos de pact.
  - Cambiar `LoginController` para emitir `idRol` en vez de `id_rol` afecta el
    campo funcional real que ya consume la app Android
    (`AuthApi.kt` usa `@SerialName("id_rol")`), así que no es un cambio
    seguro de hacer sin que ustedes decidan.

  **Opción segura si quieren dejar esto en verde:** agregar `"idRol", u.getIdRol()`
  como clave adicional en el `Map.of(...)` de `LoginController.java` (línea
  50-58), junto a `id_rol` que ya existe — es aditivo, no rompe nada que ya
  funcione, y satisface al pact. No lo apliqué porque es un cambio de
  código de negocio ya cerrado y prefiero que ustedes lo confirmen.

## Cómo aplicar este paquete

1. Copiar el contenido de este `.zip` sobre la raíz del repo (mismas rutas
   relativas).
2. Revisar el diff de los tres archivos modificados:
   `services/productos-service/pom.xml`, `services/usuarios/pom.xml`,
   `.github/workflows/ci.yml`.
3. Ejecutar localmente para confirmar (opcional pero recomendado):
   ```
   cd services/productos-service && mvn -Ppact -Dtest=com.tiendatech.productos.contract.ProductosProviderVerificationTest test
   cd services/usuarios && mvn -Ppact -Dtest=com.tiendatech.usuarios.contract.UsuariosProviderVerificationTest test
   ```
4. Hacer commit y push. **Importante:** al hacerlo, el job nuevo
   `provider-verification-usuarios` va a fallar en rojo en GitHub Actions,
   porque el hallazgo del punto anterior es real y sigue sin resolverse en el
   código. Si no quieren un CI rojo, hay que decidir primero si aplican el
   fix aditivo sugerido arriba en `LoginController.java`.

## Archivos de este paquete

```
.github/workflows/ci.yml                                               (modificado)
services/productos-service/pom.xml                                     (modificado)
services/usuarios/pom.xml                                              (modificado)
services/productos-service/src/test/pact-java/.../ProductosProviderVerificationTest.java   (nuevo)
services/usuarios/src/test/pact-java/.../UsuariosProviderVerificationTest.java             (nuevo)
docs/evidencias/pact-provider-verification/INSTRUCCIONES.md             (nuevo, este archivo)
docs/evidencias/pact-provider-verification/productos-REVERTIDO-VERDE.log                          (nuevo)
docs/evidencias/pact-provider-verification/productos-DELIBERADO-producto_id-renombrado-ROJO.log   (nuevo)
docs/evidencias/pact-provider-verification/usuarios-idRol-vs-id_rol-DRIFT-REAL.log                (nuevo)
```
