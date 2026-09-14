# Evidencia de pruebas automatizadas - Paso 1

La medición adicional de componentes React para E1/E3 se encuentra en
[web/README.md](web/README.md), con su propio alcance y dictamen.

La cobertura se mide sobre el árbol completo de cada servicio Java (dominio,
aplicación, infraestructura y presentación). Hasta el 14 de septiembre de 2026
la medición estaba restringida artificialmente al paquete `application/**` de
cada servicio; esa restricción se retiró de los seis `pom.xml` y los números
de esta tabla reflejan la corrida sin filtro.

| Microservicio | Pruebas | Líneas cubiertas | Cobertura |
|---|---:|---:|---:|
| inventario-service | 38 | 311/1396 | 22.28% |
| productos-service | 14 | 116/512 | 22.66% |
| ordenes-proveedores-service | 6 | 64/546 | 11.72% |
| ventas-service | 11 | 161/493 | 32.66% |
| pedidos-service | 77 | 443/1938 | 22.86% |
| usuarios | 37 | 411/999 | 41.14% |
| armado-ia | 27 | 405/540 | 75.00% |
| **Agregado Java (6 servicios)** | **183** | **1506/5884** | **25.60%** |

Los archivos `*-jacoco.xml` y `armado-ia-coverage.xml` son los reportes consumibles por Codecov o SonarCloud. La prueba `GatewayIntegrationTest` agrega tres flujos HTTP reales a través del API Gateway hacia productos, usuarios y pedidos.

## Comprobación publicada del umbral

El umbral exigido es 70 % de cobertura de líneas sobre el árbol completo de
cada servicio. Se comprueba directamente desde los siete XML versionados, sin
depender de un servicio externo:

```powershell
python docs/evidencias/cobertura/verificar_umbral.py
```

El resultado auditable se conserva en `informe-umbral-70.json`, regenerado el
14 de septiembre de 2026 sobre una corrida limpia: **ninguno de los seis
servicios Java alcanza todavía el 70 %** (entre 11.72 % y 41.14 %, agregado
ponderado 25.60 % sobre 5884 líneas instrumentables). El servicio `armado-ia`
no fue modificado en este trabajo y se mantiene en 75.00 %. El script devuelve
código distinto de cero si cualquier reporte falta, no contiene líneas o queda
por debajo del umbral; hoy devuelve 1 porque los seis servicios Java están por
debajo. El umbral se mantiene como meta declarada en el `check` de JaCoCo de
cada `pom.xml` (con `haltOnFailure=false` para no bloquear CI mientras se
amplía la suite de pruebas), no como una condición ya cumplida.

## Fuera del alcance

- SPA React y pruebas instrumentadas Android.
- Pruebas de contrato Pact.
- Pruebas de carga con Locust, correspondientes al Paso 3.

La carga en Codecov es complementaria. El cumplimiento del umbral puede recalcularse
desde el repositorio con el comando anterior y no depende de la disponibilidad de ese
servicio externo.
