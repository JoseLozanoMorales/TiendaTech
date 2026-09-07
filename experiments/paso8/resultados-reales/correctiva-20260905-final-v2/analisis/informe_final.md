# Paso 8 — informe de ejecución correctiva

## Protocolo ejecutado

- 24 condiciones: 2 estrategias (`2pc`, `saga`) × 4 niveles de concurrencia
  (50, 100, 200, 400) × 3 modos de fallo (`none`, `omission`, `timing`).
- 5 repeticiones por condición: 120 corridas.
- Cada corrida usó 60 segundos de calentamiento descartado y 300 segundos de
  medición, con `delay-seconds=5` y probabilidad de fallo 0,10 cuando aplicaba.
- La carga recorrió el flujo real
  `Gateway -> Pedidos -> Ventas/Inventario -> CockroachDB`.
- Se emplearon 400 compradores sintéticos y 29 productos con stock repuesto
  entre fases para evitar una fila caliente artificial.
- Antes de la matriz se aprobaron los pilotos basales de 2PC y Saga y la rampa
  sin fallos de 1, 5, 10, 25 y 50 usuarios para ambas estrategias.
- La campaña se completó en una única ejecución de 22,54 horas.

## Validación estructural

- 120 filas y 120 claves de corrida únicas.
- 24 condiciones, todas con exactamente 5 repeticiones.
- Ninguna corrida con cero solicitudes.
- Todas las corridas alcanzaron la concurrencia objetivo declarada por Locust.
- Las 120 filas registran los mismos parámetros: calentamiento de 60 segundos,
  medición de 300 segundos y temporización de 5 segundos.
- Ninguna corrida quedó con cero checkouts confirmados y no se detectaron
  advertencias de generación de carga de Locust.

El archivo `validacion.json` registra el resultado `valido: true`. El CSV
`resumen_por_condicion.csv` conserva las medianas de las métricas para cada una
de las 24 condiciones.

## Resultado global

La campaña procesó 208 003 solicitudes. De 49 786 intentos de checkout,
30 275 terminaron confirmados y 19 511 fallaron. Esto corrige la limitación de
la campaña anterior, que solo produjo tres confirmaciones y no permitía una
comparación útil.

En el agregado descriptivo, Saga confirmó 16 220 de 25 576 checkouts (63,4 %)
y 2PC confirmó 14 055 de 24 210 (58,1 %). Estas proporciones globales no
sustituyen la comparación por condición y repetición, pero muestran que ambas
estrategias produjeron una muestra abundante de compras reales.

La degradación aumenta con la concurrencia. La tasa agregada de confirmación
fue 94,1 % con 50 usuarios, 92,1 % con 100, 42,3 % con 200 y 10,8 % con 400.
Los errores HTTP 0, 500, 503, circuit breakers y timeouts observados bajo 200 y
400 usuarios forman parte del comportamiento medido durante saturación e
inyección de fallos; no representan corridas ausentes ni una falla del
generador de carga.

## Trazabilidad y seguridad de la evidencia

El repositorio publica el CSV crudo, la validación, el resumen por condición,
los resultados consolidados de piloto y rampa, y este informe. Los bancos de
usuarios, las contraseñas sintéticas, los JWT, las cachés y los logs detallados
permanecen fuera de Git. Esos archivos se conservan localmente para auditoría,
pero no son necesarios para recalcular el resumen publicado.
