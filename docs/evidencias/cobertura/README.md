# Evidencia de pruebas automatizadas - Paso 1

La medición adicional de componentes React para E1/E3 se encuentra en
[web/README.md](web/README.md), con su propio alcance y dictamen.

La cobertura se mide sobre el árbol completo de cada servicio Java (dominio,
aplicación, infraestructura y presentación) y, para `armado-ia`, sobre el
paquete `app` completo (dominio, explicación, clientes HTTP, seguridad,
métricas y capa de entrada `main.py`/`errors.py`). Hasta el 14 de septiembre
de 2026 la medición Java estaba restringida artificialmente al paquete
`application/**` de cada servicio y la de `armado-ia` a `app.domain`/
`app.explicacion`; ambas restricciones se retiraron (seis `pom.xml` y el
comando `pytest --cov=app`) y los números de esta tabla reflejan la corrida
sin filtro. El 17 de septiembre de 2026 se agregó además el Gateway
(`Apps/web/frontend`), que hasta entonces no tenía JaCoCo configurado pese a
aparecer en la tabla de complejidad.

| Servicio | Pruebas | Líneas cubiertas | Cobertura |
|---|---:|---:|---:|
| inventario-service | 38 | 311/1396 | 22.28% |
| productos-service | 14 | 116/512 | 22.66% |
| ordenes-proveedores-service | 6 | 64/546 | 11.72% |
| ventas-service | 11 | 161/493 | 32.66% |
| pedidos-service | 77 | 443/1938 | 22.86% |
| usuarios | 41 | 442/1037 | 42.62% |
| gateway | 29 | 162/207 | 78.26% |
| armado-ia | 40 | 580/931 | 62.30% |
| **Agregado (8 servicios)** | **256** | **2279/7060** | **32.28%** |

Los archivos `*-jacoco.xml` y `armado-ia-coverage.xml` son los reportes consumibles por Codecov o SonarCloud. La prueba `GatewayIntegrationTest` agrega tres flujos HTTP reales a través del API Gateway hacia productos, usuarios y pedidos.

## Comprobación publicada del umbral

El umbral exigido es 70 % de cobertura de líneas sobre el árbol completo de
cada servicio. Se comprueba directamente desde los ocho XML versionados, sin
depender de un servicio externo:

```powershell
python docs/evidencias/cobertura/verificar_umbral.py
```

El resultado auditable se conserva en `informe-umbral-70.json`, regenerado el
18 de septiembre de 2026 sobre una corrida limpia de los ocho servicios:
**solo el Gateway alcanza el 70 %** (78.26 %); los otros siete quedan por
debajo, entre 11.72 % (ordenes-proveedores-service) y 62.30 % (armado-ia),
con un agregado ponderado de 32.28 % sobre 7060 líneas instrumentables. El
script devuelve código distinto de cero si cualquier reporte falta, no
contiene líneas o queda por debajo del umbral; hoy devuelve 1 porque siete de
los ocho servicios están por debajo. El umbral se mantiene como meta
declarada en el `check` de JaCoCo de cada `pom.xml` (con
`haltOnFailure=false` para no bloquear CI mientras se amplía la suite de
pruebas), no como una condición ya cumplida.

Nota de corrección (18 de septiembre de 2026): la primera regeneración del 17
de septiembre reportó 511/1037 (49.28 %) para `usuarios` y 138/512 (26.95 %)
para `productos-service`. Ambas cifras estaban infladas porque la corrida
local en IntelliJ tenía activo el perfil Maven `pact`, que agrega
`src/test/pact-java/` (los tests `UsuariosProviderVerificationTest` y
`ProductosProviderVerificationTest`, pensados para correr solo con
`-Ppact` en los jobs `provider-verification-*` de CI, no con `mvn test`
plano) al build. La primera corrida en vivo de CI (job `coverage-java`,
sin ese perfil) ya midió los valores correctos; se confirmaron localmente
corriendo `mvn clean test` con el perfil `pact` desestildado en el panel de
Maven de IntelliJ, coincidiendo exactamente con CI: 442/1037 (42.62 %) y
116/512 (22.66 %). Las cifras de esta tabla y de `informe-umbral-70.json`
ya reflejan esa corrección.

Desde el 18 de septiembre de 2026, `verificar_umbral.py` también se ejecuta
dentro de `.github/workflows/ci.yml` en cada push/PR (job
`coverage-threshold`), y ya no contra los XML estáticos versionados aquí:
los jobs `coverage-java` (matriz sobre los seis microservicios y el Gateway,
`mvn test`) y `coverage-armado-ia` (`pytest --cov=app`) regeneran los ocho
reportes en esa misma corrida de CI, y `coverage-threshold` los descarga y
verifica en vivo. El paso es intencionalmente no bloqueante
(`continue-on-error: true`): anota `::warning::` cuando algún servicio queda
por debajo del 70 % y sube `informe-umbral-70-ci.json` como artefacto,
en vez de fallar el pipeline mientras la cobertura real (11.72–78.26 % según
servicio) sigue lejos del umbral declarado. Esta corrida en vivo fue,
justamente, la que permitió detectar la inflación por el perfil `pact` en
`usuarios` y `productos-service` descrita arriba: al comparar el JSON
generado en CI contra el committeado un día antes, saltó la diferencia.

## Fuera del alcance

- SPA React y pruebas instrumentadas Android.
- Pruebas de contrato Pact.
- Pruebas de carga con Locust, correspondientes al Paso 3.

La carga en Codecov es complementaria. El cumplimiento del umbral puede recalcularse
desde el repositorio con el comando anterior y no depende de la disponibilidad de ese
servicio externo.
