# Pruebas de carga (`tests/load/`)

Dos escenarios, dos scripts, no mezclados a propósito (ver el docstring de
`critical_path_locustfile.py`):

- **`locustfile.py`** — 4 lecturas públicas del catálogo (`/api/productos`,
  `/api/categorias`, `/api/marcas`, `/api/provincias`), sin autenticación.
  Es el escenario histórico usado en `paso10-item5-grafana-carga.md` y
  `punto17-observabilidad-gateway.md`.
- **`critical_path_locustfile.py`** — camino crítico autenticado de punta a
  punta por usuario virtual: registro → login → dirección propia → método de
  pago propio → (carrito → agregar → checkout → factura → asistente de
  armado) en bucle, con `POST /auth/refresh` forzado cada
  `REFRESH_EVERY_N_ITERATIONS` iteraciones. Es el escenario del punto 14.

## Prerrequisitos de datos (obligatorios antes de correr `critical_path_locustfile.py`)

Un clúster recién levantado con migraciones puras por servicio (punto 5) no
trae poblado ningún catálogo de referencia — nada en `docker-compose.yml` ni
en Flyway lo hace. Sin estos dos scripts, el camino crítico falla desde el
primer usuario virtual:

1. **`docs/db/seed-ecuador-mobile-checkout.sql`** — provincias, ciudades y
   tipos de método de pago. Sin esto, `_crear_direccion()` y
   `_crear_metodo_pago()` fallan porque los catálogos que consultan
   (`GET /api/ciudades`, `GET /api/metodopago/tipos`) están vacíos.

   ```
   docker compose exec -T tiendatech-crdb-1 \
     cockroach sql --insecure --host=localhost:26257 -d tiendatech --file=/dev/stdin \
     < docs/db/seed-ecuador-mobile-checkout.sql
   ```

   Ojo: este script no trae `USE tiendatech;` al principio — sin `-d
   tiendatech` explícito corre contra `defaultdb` y falla con
   `relation "usuarios.provincia" does not exist` aunque la tabla sí exista
   (en otra base).

2. **`docs/db/seed-inventario-stock.sql`** — puebla
   `inventario.inventario_producto` a partir de `productos.producto`. La
   reserva de stock real (`StockReservationService`, inventario-service)
   valida contra esta tabla, no contra `productos.producto.stock`: sin este
   seed, el primer `POST /api/carrito/[carritoId]/agregar` de cualquier
   usuario virtual falla con `409` ("Stock insuficiente") aunque el catálogo
   de productos muestre unidades disponibles.

   ```
   docker compose exec -T tiendatech-crdb-1 \
     cockroach sql --insecure --host=localhost:26257 -d tiendatech --file=/dev/stdin \
     < docs/db/seed-inventario-stock.sql
   ```

   Requiere que `productos.producto` ya tenga filas — aplicar antes
   `docs/db/product-management-reference.sql` + un seed de catálogo
   (`seed-catalogo-sintetico.sql` o `seed-e2e.sql`), o el catálogo real del
   equipo.

Si el catálogo de productos está vacío, aplicar también
`docs/db/seed-e2e.sql` (deja un producto habilitado de prueba, categoría
`CPU`). El escenario no depende de ningún `producto_id` fijo: descubre
productos por `GET /api/productos` y la categoría de CPU por
`GET /api/categorias` + `GET /api/productos/por-categoria`, comparando el
nombre de categoría en minúsculas contra `"cpu"` (el nombre real de la
semilla, no `"procesador"`).

## Rate limiter del Gateway

Todos los usuarios virtuales de Locust comparten una sola IP de origen
(`localhost`), así que el límite por IP del Gateway
(`GATEWAY_RATE_LIMIT_REQUESTS`, 300 por defecto) se agota entre todos ellos
casi de inmediato y el resto de la ventana recibe `429` sin relación con el
comportamiento real del sistema. Para una campaña que no busca ejercitar el
rate limiter, subir el límite antes de correr, por ejemplo:

```
GATEWAY_RATE_LIMIT_REQUESTS=20000
```

## Cómo correr una campaña

```
./tests/load/run-load-test.ps1 -Users 5 -SpawnRate 2 -RunTime 90s `
  -LocustFile critical_path_locustfile.py -OutputPrefix tiendatech-critical-path-5-users

./tests/load/run-load-test.ps1 -Users 10 -SpawnRate 2 -RunTime 90s `
  -LocustFile critical_path_locustfile.py -OutputPrefix tiendatech-critical-path-10-users

./tests/load/run-load-test.ps1 -Users 20 -SpawnRate 4 -RunTime 90s `
  -LocustFile critical_path_locustfile.py -OutputPrefix tiendatech-critical-path-20-users
```

`-OutputPrefix` es obligatorio cuando se corre más de un nivel o más de un
escenario: sin él, `run-load-test.ps1` siempre escribe con el prefijo fijo
`tiendatech-<Users>-users`, y una segunda corrida con el mismo número de
usuarios pisa los CSV de la primera sin dejar rastro de cuál era cuál. Usar
siempre un prefijo explícito y distinto por corrida cuando el mismo nombre
se vaya a citar en más de un documento de evidencia.

## `tiendatech-50-users`: un nombre, varias corridas distintas

`-OutputPrefix` por defecto es `"tiendatech-$Users-users"`, así que **toda**
invocación con `-Users 50` (el valor por defecto de `run-load-test.ps1`)
escribe con el mismo nombre fijo, sin importar cuántos días o corridas
distintas hayan pasado — cada una pisa la anterior en
`tests/load/results/`. Esto ya generó confusión real: el `tiendatech-50-users_*.csv`
que hoy está versionado en `tests/load/results/` corresponde a una corrida
del 2026-09-15 13:49 UTC con concurrencia real de solo 10 usuarios (columna
`User Count` de `_stats_history.csv`), 790 peticiones, 253 fallos `429` — no
a la campaña de `paso10-item5-grafana-carga.md` (2026-09-04, 2194
peticiones, 1894 fallos) ni a la de `punto17-observabilidad-gateway.md`
(2026-09-15, 400 usuarios reales contra el Gateway, 28,787 peticiones,
28,187 fallos). Esas dos campañas sí quedan preservadas intactas, cada una
en su propia copia versionada dentro de su carpeta de evidencia
(`docs/evidencias/paso10-item5-grafana-carga/` y
`docs/evidencias/punto17-observabilidad-gateway/`) — el problema es
exclusivamente la copia genérica en `tests/load/results/`, que no es
evidencia de ningún cierre específico y no debe citarse como tal.

**Regla a partir de ahora: nunca dejar `-OutputPrefix` en su valor por
defecto si el resultado se va a citar como evidencia.** Pasar siempre un
prefijo explícito con fecha o propósito (por ejemplo
`tiendatech-50-users-20260915-ratelimit-check`), y copiar el resultado a la
carpeta de evidencia del punto correspondiente antes de correr cualquier
otra campaña que pueda reusar el mismo nombre por defecto.

## Después de la corrida: crudos y manifiesto

Los crudos que Locust escribe (`_stats.csv`, `_stats_history.csv`,
`_failures.csv`, `_exceptions.csv`, `.html`) van a `tests/load/results/`,
que está en `.gitignore` salvo los CSV que se versionan explícitamente. El
`.html` **no se versiona ni se somete a checksum** — no citarlo como
"verificado en checksums.txt".

**No editar los CSV crudos después de la corrida** (ni para quitar `\r`
sueltos, ni BOM, ni ningún otro "limpiado"): el manifiesto debe reflejar
exactamente los bytes que Locust escribió. Si un CSV necesita revisarse por
algún problema de formato, se vuelve a correr la campaña — no se reescribe
el archivo ya versionado y se regenera el checksum sobre la versión
alterada, porque entonces el manifiesto certifica bytes que la herramienta
nunca produjo.

```
python experiments/paso8/campaign_checksums.py --directory tests/load/results --write
```
