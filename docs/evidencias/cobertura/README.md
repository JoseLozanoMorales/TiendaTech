# Evidencia de pruebas automatizadas - Paso 1

La cobertura se mide exclusivamente sobre la capa de lógica de negocio. Los adaptadores JDBC, controladores y configuración de framework quedan fuera de esta métrica.

| Microservicio | Pruebas | Líneas cubiertas | Cobertura |
|---|---:|---:|---:|
| inventario-service | 10 | 8/8 | 100.00% |
| productos-service | 14 | 23/23 | 100.00% |
| ordenes-proveedores-service | 6 | 47/51 | 92.16% |
| ventas-service | 4 | 27/27 | 100.00% |
| pedidos-service | 48 | 104/129 | 80.62% |
| usuarios | 37 | 321/358 | 89.66% |
| armado-ia | 27 | 405/540 | 75.00% |

Los archivos `*-jacoco.xml` y `armado-ia-coverage.xml` son los reportes consumibles por Codecov o SonarCloud. La prueba `GatewayIntegrationTest` agrega tres flujos HTTP reales a través del API Gateway hacia productos, usuarios y pedidos.

## Comprobación publicada del umbral

El umbral exigido es 70 % de cobertura de líneas en la capa de lógica de negocio
instrumentada. Se comprueba directamente desde los siete XML versionados, sin depender
de un servicio externo:

```powershell
python docs/evidencias/cobertura/verificar_umbral.py
```

El resultado auditable se conserva en `informe-umbral-70.json`: los siete servicios
cumplen y el menor valor es 75,00 % para `armado-ia`. El script devuelve código distinto
de cero si cualquier reporte falta, no contiene líneas o queda por debajo del umbral.

## Fuera del alcance

- Adaptadores de persistencia y bases de datos, salvo las pruebas de integración ya existentes con CockroachDB.
- SPA React y pruebas instrumentadas Android.
- Pruebas de contrato Pact.
- Pruebas de carga con Locust, correspondientes al Paso 3.

La carga en Codecov es complementaria. El cumplimiento del umbral puede recalcularse
desde el repositorio con el comando anterior y no depende de la disponibilidad de ese
servicio externo.
