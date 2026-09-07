# Paso 8 — informe final de la campaña correctiva

## Evidencia principal

La evidencia canónica de C2, C3 y C6 es esta campaña:
`resultados-reales/correctiva-20260905-final-v2/`. Se ejecutaron 120 corridas
reales —2 estrategias × 4 concurrencias × 3 modos de fallo × 5 repeticiones—
contra `Gateway -> Pedidos -> Ventas/Inventario -> CockroachDB`, después de un
piloto basal y una rampa sin fallos. Cada corrida descartó 60 segundos de
calentamiento y midió durante 300 segundos.

El CSV de Locust registra 208 003 solicitudes y 49 786 intentos de checkout:
30 275 confirmados y 19 511 fallidos. Saga confirmó 16 220 de 25 576 intentos
(63,4 %) y 2PC confirmó 14 055 de 24 210 (58,1 %). Estas cifras describen las
respuestas HTTP; no se equiparan retrospectivamente con los registros de base
de datos.

## Reconstrucción desde CockroachDB real

El oráculo retrospectivo se ejecutó en modo de solo lectura sobre CockroachDB
real. Para evitar que el procesador de outbox reiniciado después de la campaña
cambiara la evidencia durante la consulta, todas las lecturas usan la misma
instantánea MVCC:
`AS OF SYSTEM TIME '2026-09-07T20:10:24.380904Z'`.

Las órdenes se asignaron a una corrida cuando `pedidos.orden.creado_en` cayó
dentro de su ventana oficial de medición. Los 13 321 registros de usuarios
sintéticos que quedaron fuera de esas ventanas corresponden a calentamientos
o intervalos entre corridas y no se mezclaron con la matriz medida.

| Comprobación persistente | Resultado en las 120 ventanas |
| --- | ---: |
| Órdenes persistidas | 43 168 |
| Órdenes sin factura | 6 290 |
| Facturas con importe distinto de la orden | 0 |
| Facturas cuyas líneas no coinciden con la orden | 0 |
| Órdenes con descuento de inventario ausente o distinto | 13 210 |
| Órdenes con descuento duplicado | 0 |
| Eventos de stock negativo en kardex/reservas | 0 |
| Productos con stock actual negativo | 0 |

Por tanto, 36 878 órdenes tienen factura persistida con importe y líneas
exactos, y 29 958 órdenes tienen un único movimiento de inventario con la
cantidad esperada. Se observaron 13 210 órdenes inconsistentes distintas entre
43 168 órdenes persistidas (30,6014 %). En 104 corridas hubo al menos una
violación observable. Las otras 16 se etiquetan `no_verificado`, no `true`,
porque faltan invariantes que la persistencia disponible no permite demostrar.

Para Saga, 12 159 outbox de facturas de las ventanas medidas estaban procesados
y 6 995 seguían pendientes o fallidos en la instantánea. Fue posible obtener
una mediana numérica de convergencia en 37 de las 60 corridas Saga y en al
menos una repetición de las 12 condiciones Saga. Las medianas por corrida
observadas van de 784 369,067 ms a 35 554 122,710 ms, con mediana global de
20 602 253,014 ms. Este valor es
`ventas.factura_outbox.procesado_en - creado_en`: mide la convergencia durable
factura–inventario y refleja también la acumulación del backlog; no prueba una
compensación de checkout cancelado.

## Límites explícitos del oráculo

- No existe un ledger independiente de capturas de pago. Se verificó la factura
  y su importe exacto contra la orden, pero eso no puede presentarse como prueba
  de un cobro bancario real.
- Los resultados `COMPLETADA`/`FALLIDA` de los intentos de checkout se guardaban
  en `TransactionObservationStore`, un buffer en memoria limitado a 200
  entradas. Los reinicios entre corridas eliminaron esa historia. Por ello no
  es posible reconstruir con certeza si cada intento cancelado o compensado
  devolvió el stock y no dejó cobro pendiente.
- Ese cuarto invariante queda expresamente como `no_verificado` en las 120
  filas. No se rellenó ningún cero ni `oracle_pass=true` sin evidencia.
- Una orden “confirmada” se operacionaliza aquí como una fila persistida en
  `pedidos.orden`: el servicio confirma ese commit antes de invocar
  facturación. Es una definición verificable, distinta de la respuesta HTTP
  observada por Locust.

## Análisis estadístico

Se calculó por condición la mediana y un IC95 % bootstrap percentil de 20 000
remuestreos para latencia p95, throughput, tasas de confirmación y aborto,
errores, inconsistencia y convergencia Saga. Las proporciones agregadas de
confirmación y aborto incluyen IC95 % de Wilson. Las 72 comparaciones 2PC–Saga
incluyen U de Mann–Whitney bilateral y el tamaño del efecto A12 de
Vargha–Delaney; se informa el umbral sin ajustar y la corrección de Bonferroni
por familia de 12 comparaciones (`alpha=0,0041667`).

Doce comparaciones tienen `p < 0,05` sin ajustar, pero ninguna conserva
significancia tras Bonferroni. Con solo cinco repeticiones por condición, las
medianas, los intervalos y A12 son más informativos que proclamar una estrategia
ganadora. Los diagramas publicados son boxplots construidos con las cinco
repeticiones de cada condición, no gráficos de barras.

## Archivos auditables

- `experimento_real_crudo.csv`: mediciones originales de Locust.
- `analisis/oracle_por_corrida_crdb.csv`: invariantes persistentes por corrida.
- `analisis/experimento_real_crudo_enriquecido.csv`: medición y oráculo unidos.
- `analisis/usuarios_sinteticos_ids.csv`: IDs no secretos usados para delimitar
  la carga, sin contraseñas ni JWT.
- `analisis/oracle_resumen_crdb.json`: consulta, instantánea, totales y hashes.
- `analisis/resumen_estadistico_ic95.csv`: medianas e IC95 % por condición.
- `analisis/proporciones_binomiales_ic95.csv`: tasas e IC95 % de Wilson.
- `analisis/comparaciones_mann_whitney.csv`: U, p y A12.
- `analisis/boxplot_*.svg`: cinco diagramas de caja.
- `analisis/analisis_estadistico_metodologia.json`: método y parámetros.
- `analisis/validacion.json`: integridad estructural de las 120 corridas.

## Conclusión defendible

La campaña correctiva sí constituye evidencia distribuida real y ofrece una
muestra amplia y completa por condición. La reconstrucción demuestra ausencia
de importes de factura discordantes, descuentos duplicados y stock negativo en
la persistencia disponible; también descubre pérdidas de continuidad entre
orden, factura e inventario que no deben ocultarse. No demuestra captura
bancaria ni compensación completa de intentos cancelados, porque esos datos no
fueron persistidos. Esos límites quedan declarados en vez de inventar éxito.
