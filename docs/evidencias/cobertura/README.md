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
| productos-service | 15 | 138/512 | 26.95% |
| ordenes-proveedores-service | 6 | 64/546 | 11.72% |
| ventas-service | 11 | 161/493 | 32.66% |
| pedidos-service | 77 | 443/1938 | 22.86% |
| usuarios | 42 | 511/1037 | 49.28% |
| gateway | 29 | 162/207 | 78.26% |
| armado-ia | 40 | 580/931 | 62.30% |
| **Agregado (8 servicios)** | **258** | **2370/7060** | **33.57%** |

Los archivos `*-jacoco.xml` y `armado-ia-coverage.xml` son los reportes consumibles por Codecov o SonarCloud. La prueba `GatewayIntegrationTest` agrega tres flujos HTTP reales a través del API Gateway hacia productos, usuarios y pedidos.

## Comprobación publicada del umbral

El umbral exigido es 70 % de cobertura de líneas sobre el árbol completo de
cada servicio. Se comprueba directamente desde los ocho XML versionados, sin
depender de un servicio externo:

```powershell
python docs/evidencias/cobertura/verificar_umbral.py
```

El resultado auditable se conserva en `informe-umbral-70.json`, regenerado el
17 de septiembre de 2026 sobre una corrida limpia de los ocho servicios:
**solo el Gateway alcanza el 70 %** (78.26 %); los otros siete quedan por
debajo, entre 11.72 % (ordenes-proveedores-service) y 62.30 % (armado-ia),
con un agregado ponderado de 33.57 % sobre 7060 líneas instrumentables. El
script devuelve código distinto de cero si cualquier reporte falta, no
contiene líneas o queda por debajo del umbral; hoy devuelve 1 porque siete de
los ocho servicios están por debajo. El umbral se mantiene como meta
declarada en el `check` de JaCoCo de cada `pom.xml` (con
`haltOnFailure=false` para no bloquear CI mientras se amplía la suite de
pruebas), no como una condición ya cumplida.

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
servicio) sigue lejos del umbral declarado.

## Fuera del alcance

- SPA React y pruebas instrumentadas Android.
- Pruebas de contrato Pact.
- Pruebas de carga con Locust, correspondientes al Paso 3.

La carga en Codecov es complementaria. El cumplimiento del umbral puede recalcularse
desde el repositorio con el comando anterior y no depende de la disponibilidad de ese
servicio externo.
