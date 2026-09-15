# Contratos OpenAPI

Los siete archivos por servicio se generan desde Spring MVC/springdoc y FastAPI.
`openapi.yaml` referencia sus rutas publicadas por el gateway, sin duplicar
operaciones ni componentes. Los archivos `.yaml` contienen JSON, que es sintaxis
YAML válida, para mantener una serialización ordenada y reproducible.

## Regenerar

Requisitos: Java 21, Maven y Python 3.12.

```bash
python3.12 -m venv .venv-openapi
.venv-openapi/bin/pip install -r scripts/openapi/requirements.txt
.venv-openapi/bin/python scripts/openapi/generate.py
git diff -- docs/api
```

Ejecutar desde la raíz del repositorio. `MVN` permite indicar otra ruta al
ejecutable Maven; `--maven-repo /ruta` permite usar un repositorio Maven separado.
La primera ejecución descarga dependencias. La exportación no necesita base de
datos, Docker, credenciales AWS ni servicios externos.

Cada módulo Java activa el perfil Maven `openapi`, carga los controladores reales
en un contexto MVC aislado y sustituye sus colaboradores por mocks. Springdoc
describe métodos, parámetros y DTO compilados. Un inventario independiente se
obtiene de `RequestMappingHandlerMapping`; no se cuentan anotaciones con regex.
El servicio Python utiliza `app.openapi()` y el registro de rutas de FastAPI,
con el proveedor de explicaciones determinista para evitar conexiones AWS.

Springdoc se limita al perfil de exportación y al classpath de pruebas: este
cambio no publica endpoints de documentación en los servicios Java.
Las versiones corresponden a los marcos existentes: springdoc 2.6.0 para Boot
3.3.5 y springdoc 3.0.3 para Boot 4.0.7 en usuarios. Referencia:
[compatibilidad de springdoc](https://springdoc.org/).

## Validación en CI

```bash
.venv-openapi/bin/python -m unittest discover -s scripts/openapi -p 'test_*.py' -v
.venv-openapi/bin/python scripts/openapi/generate.py --check
```

El trabajo `openapi-contracts` de `.github/workflows/ci.yml`:

1. Vuelve a exportar los seis módulos Java y FastAPI.
2. Compara cada combinación de método HTTP y ruta con el registro del marco.
3. Exige esquemas para los cuerpos de petición declarados y los contenidos de respuesta.
4. Valida OpenAPI y resuelve las referencias externas del consolidado.
5. Compara los ocho archivos completos con las versiones guardadas. También
   falla cuando cambia un DTO o un parámetro sin regenerar el contrato.

Una sustitución de rutas con el mismo total también falla. `--check` no modifica
los archivos guardados. `--from-build` reutiliza las exportaciones Java de
`target/openapi` para trabajar localmente; **no debe usarse en CI**, porque no
recompila el código. Los inventarios y exportaciones originales quedan como
artefactos del trabajo de CI.

## Alcance

Inventario verificado al introducir esta automatización:

| Servicio | Operaciones HTTP explícitas | Publicadas por el gateway |
|---|---:|---:|
| productos | 37 | 37 |
| inventario | 6 | 4 |
| pedidos | 19 | 18 |
| ordenes-proveedores | 14 | 14 |
| ventas | 6 | 5 |
| usuarios | 35 | 35 |
| armado-ia | 3 | 1 |
| **Total** | **120** | **114** |

El generador no fija esos números: descubre las operaciones de cada ejecución.
`PATCH` y `POST` sobre una misma ruta cuentan por separado. No se añaden los
`HEAD`/`OPTIONS` implícitos de Spring, los endpoints de documentación del marco
ni `/metrics` de la biblioteca de instrumentación.

Cada operación indica `x-scope` (`business`, `internal` o `diagnostic`) y
`x-gateway-exposed`. La exposición se obtiene de los predicados `Path=` en
`Apps/web/frontend/src/main/resources/application.yml`. Las dos operaciones de
stock, la consulta interna de ventas, la sonda CRDB y las dos consultas de
diagnóstico de IA quedan en sus contratos de servicio, fuera del consolidado.
La consulta de reservas es de diagnóstico y está publicada por el gateway.

## Respuestas y mantenimiento

El generador incorpora el envoltorio `status`, `data`, `message`, `timestamp`
de `ApiResponseAdvice` y del middleware Python. Conserva la estructura del DTO
original dentro de `data`, las respuestas binarias y los estados sin contenido.
La consulta de salud de IA es una excepción al envoltorio; su consulta de
circuit breakers sí está envuelta, como en el middleware real.

`scripts/openapi/responses.json` declara códigos de éxito y errores que springdoc
no puede deducir de las llamadas a `ResponseEntity` dentro de un método.
Debe revisarse cuando se modifica ese comportamiento. El generador rechaza
entradas que apunten a rutas eliminadas. Las reglas de seguridad del generador
describen JWT y cookie de renovación; deben mantenerse junto a los filtros y a
`SecurityConfig`. Los requisitos de rol y propiedad del recurso siguen estando
definidos en la implementación.

**Límite de precisión:** `Map<String, Object>`, `JsonNode` y `ResponseEntity<?>`
no declaran todos los campos posibles en el código. Se documentan como objetos
abiertos o contenido JSON dinámico, sin inventar propiedades obligatorias. Para
obtener modelos de cliente estrictos para esas operaciones, habrá que tipar
esos cuerpos con DTO o aportar esquemas específicos. La generación y el control
de cobertura no sustituyen las pruebas de comportamiento de los servicios.
