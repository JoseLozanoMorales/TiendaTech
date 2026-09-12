# 08 / Verificación de trazabilidad

Fecha UTC: 2026-09-11T21:58:15+00:00
Commit base: `4c0ecc6138635477e3248f7b1a0c593cb9e84a3d`. Los archivos nuevos son cambios locales sin commit.

Referencias localizadas: **22/22**.
Diagramas editables examinados: **6**.
Errores de trazabilidad o estructura: **0**.

Alcance: fuentes, anclas, hashes y estructura de los diagramas declarados en referencias.json; las copias adicionales quedan fuera de esta revisión.
No verifica comportamiento HTTP, transacciones ni una ejecución con servicios reales.

## Fuentes comprobadas

| Evidencia | Fuente y línea | SHA-256 |
|---|---|---|
| 01-recuperacion-outbox | `services/ventas-service/src/main/java/com/tiendatech/ventas/application/FacturaService.java:43` | `bca0d0c022847c636892ffc3cc18a0d1236fd04baf65b658733ac400a1effae2` |
| 01-recuperacion-outbox | `services/ventas-service/src/main/java/com/tiendatech/ventas/infrastructure/persistence/CrdbFacturaRepository.java:60` | `3a7df460425b50e58e94b5a3a8440a459347a3e5abe9477eb6045ecc2fe41065` |
| 01-recuperacion-outbox | `services/ventas-service/src/main/java/com/tiendatech/ventas/application/InventarioOutboxProcessor.java:31` | `f2dbcae223897ec3fef71d312f7610a69fbdf35e235f25262a404d1ca3a001f5` |
| 01-recuperacion-outbox | `services/ventas-service/src/main/java/com/tiendatech/ventas/infrastructure/client/InventarioClient.java:75` | `be9dac99513721a5d14baebe51b6e8711d8838be6bdb8f0cbafea592420a2bd7` |
| 02-estados-outbox | `services/ventas-service/src/main/java/com/tiendatech/ventas/infrastructure/persistence/FacturaOutboxRepository.java:24` | `6032732e421c3e938567bd8b9d7a7901760415b87cce7964e419c66748bd68a1` |
| 02-estados-outbox | `services/ventas-service/src/main/java/com/tiendatech/ventas/infrastructure/persistence/FacturaOutboxRepository.java:35` | `6032732e421c3e938567bd8b9d7a7901760415b87cce7964e419c66748bd68a1` |
| 02-estados-outbox | `services/ventas-service/src/main/java/com/tiendatech/ventas/infrastructure/persistence/FacturaOutboxRepository.java:47` | `6032732e421c3e938567bd8b9d7a7901760415b87cce7964e419c66748bd68a1` |
| 03-idempotencia-checkout | `services/pedidos-service/src/main/java/com/tiendatech/pedidos/application/OrdenService.java:184` | `6676676f2800f3dd53de8027feff547eb5d27cfa6974189bb666d67d5ae8d8eb` |
| 03-idempotencia-checkout | `services/pedidos-service/src/main/java/com/tiendatech/pedidos/application/OrdenService.java:170` | `6676676f2800f3dd53de8027feff547eb5d27cfa6974189bb666d67d5ae8d8eb` |
| 03-idempotencia-checkout | `services/pedidos-service/src/main/java/com/tiendatech/pedidos/application/OrdenService.java:110` | `6676676f2800f3dd53de8027feff547eb5d27cfa6974189bb666d67d5ae8d8eb` |
| 03-idempotencia-checkout | `services/pedidos-service/src/main/java/com/tiendatech/pedidos/infrastructure/persistence/JdbcOrdenRepository.java:135` | `8f98def35a331521e7226c99861289852b309bb96600aeead9f6ce95d519869a` |
| 04-facturacion-parcial | `services/pedidos-service/src/main/java/com/tiendatech/pedidos/application/OrdenService.java:131` | `6676676f2800f3dd53de8027feff547eb5d27cfa6974189bb666d67d5ae8d8eb` |
| 04-facturacion-parcial | `services/pedidos-service/src/main/java/com/tiendatech/pedidos/application/OrdenService.java:136` | `6676676f2800f3dd53de8027feff547eb5d27cfa6974189bb666d67d5ae8d8eb` |
| 04-facturacion-parcial | `services/pedidos-service/src/main/java/com/tiendatech/pedidos/application/OrdenService.java:140` | `6676676f2800f3dd53de8027feff547eb5d27cfa6974189bb666d67d5ae8d8eb` |
| 05-reservas-concurrentes | `services/inventario-service/src/main/java/com/tiendatech/inventario/application/reservation/StockReservationService.java:49` | `068fac96e2b868a20e3d47cf64cc142a1640ba1f5ea0c224dc0ea7376fe42565` |
| 05-reservas-concurrentes | `services/inventario-service/src/main/java/com/tiendatech/inventario/application/reservation/StockReservationService.java:67` | `068fac96e2b868a20e3d47cf64cc142a1640ba1f5ea0c224dc0ea7376fe42565` |
| 05-reservas-concurrentes | `services/inventario-service/src/main/java/com/tiendatech/inventario/application/reservation/StockReservationService.java:70` | `068fac96e2b868a20e3d47cf64cc142a1640ba1f5ea0c224dc0ea7376fe42565` |
| 05-reservas-concurrentes | `services/inventario-service/src/main/java/com/tiendatech/inventario/application/reservation/CrdbTransactionRetryExecutor.java:15` | `07103e400edcc008e6e4a05ca89fac8687b7653e98f369f1829fd7c774529352` |
| 06-respaldo-ia | `services/armado-ia/app/explicacion/service.py:32` | `e1b83377ba46a4455787b70a44c9f7fee2de84cbd9ace2796a4573e60fef906b` |
| 06-respaldo-ia | `services/armado-ia/app/explicacion/validation.py:67` | `2cf262020af272ad809fff8352f22dd1699c80213aaf37949e88a2fffa039da0` |
| 06-respaldo-ia | `services/armado-ia/app/explicacion/fallback_client.py:11` | `0e44faf45def75f801355b9127fd2a4e0a7c8db03816ddf9e6a2611b91f751ac` |
| 06-respaldo-ia | `services/armado-ia/app/armado_service.py:78` | `8f6e816a3a0d7504f036fbbf06324e9d8a8a5e8827e43e5fb04cc38addd1444d` |
