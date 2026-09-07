# Evidencia principal de C2, C3 y C6

La campaña canónica es
`resultados-reales/correctiva-20260905-final-v2/`. Fue ejecutada mediante
`run_real_experiment.py` contra el API Gateway, los microservicios de Pedidos,
Ventas e Inventario y CockroachDB real.

La carpeta `resultados-reales/oficial-v4-20260904/` se conserva solo como
antecedente invalidado: produjo tres checkouts confirmados de 401 y ya no
sustenta las conclusiones de la entrega.

## Estado comprobable de la campaña correctiva

- 120 corridas y 120 claves únicas.
- 24 condiciones con cinco repeticiones cada una.
- Estrategias `2pc` y `saga`; concurrencias 50, 100, 200 y 400; modos `none`,
  `omission` y `timing`.
- 60 segundos de calentamiento descartado y 300 segundos de medición.
- 208 003 solicitudes; 49 786 checkouts; 30 275 confirmados y 19 511 fallidos.
- Piloto basal y rampa previa sin fallos completados antes de la matriz.

`analisis/validacion.json` acredita la integridad estructural. El análisis
estadístico y los boxplots están en `analisis/`, junto con el informe final.

## Oráculo retrospectivo de CockroachDB

`reconstruct_real_oracle.py` consultó las tablas reales de Pedidos, Ventas e
Inventario usando una instantánea MVCC fija de CockroachDB. Para las ventanas
oficiales encontró 43 168 órdenes persistidas, de las cuales 6 290 no tenían
factura y 13 210 no tenían exactamente el movimiento de stock esperado. No
encontró importes de factura distintos, descuentos duplicados ni stock
negativo. El detalle por corrida se conserva en
`analisis/oracle_por_corrida_crdb.csv`; los metadatos, límites y hashes están en
`analisis/oracle_resumen_crdb.json`.

La verificación es deliberadamente parcial: el sistema no persiste un ledger
independiente de cobros y los resultados fallidos/cancelados solo existían en
un buffer de memoria de 200 entradas que se perdió con los reinicios. Por eso
el estado de compensación de intentos cancelados queda `no_verificado`, nunca
se rellena como éxito. La convergencia Saga publicada corresponde al outbox
durable factura–inventario, no a una compensación de checkout cancelado.

`coordination_lab.py` y `run_paso8.py` son pilotos locales en SQLite. Se
conservan para reproducibilidad, pero no se usan como evidencia de C2, C3 o C6.
