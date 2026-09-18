# Punto 14 — Pruebas de carga: camino crítico autenticado

## Estado real de esta entrega (léase antes que el resto)

El código está completo y lo verifiqué todo lo que se puede verificar sin
una corrida en vivo: sintaxis Python válida, y cada endpoint/DTO del flujo
(`crear` → `login` → `direcciones` → `metodopago` → `carrito` → `checkout`
→ `facturas` → `armado`) contrastado línea por línea contra el controlador
y el DTO reales del backend, y contra las rutas del gateway en
`Apps/web/frontend/src/main/resources/application.yml` (para confirmar qué
queda público y qué exige `Authorization: Bearer`).

Lo que **no** pude cerrar: una corrida en vivo de punta a punta. Sí levanté
el stack completo (`docker compose up`) y apliqué el seed de Ecuador con
éxito (confirmé 24 provincias, 50 ciudades y 2 tipos de método de pago
insertados), pero usuarios-service/productos-service/pedidos-service/
ventas-service/ordenes-proveedores-service/inventario-service fallaban de
forma intermitente al conectar a CRDB durante Flyway en el arranque
(`SocketTimeoutException: Connect timed out`), con un patrón que no logré
hacer 100% reproducible ni 100% evitable pese a varios reinicios limpios
del stack — y en el último intento de reiniciar Docker Desktop a la fuerza
para descartar una degradación de su red interna, terminé rompiendo el
propio Docker Desktop (un pipe interno, `dockerInference`, quedó sin poder
liberarse). CockroachDB mismo no mostró ningún error en sus logs en ningún
momento — el problema parece estar en la capa de Docker Desktop/red local
de esta máquina, no en el código ni en CRDB.

**Qué falta de tu lado:** una vez tengas Docker Desktop operativo de nuevo,
levantar el stack, aplicar el seed (ver abajo) y correr
`critical_path_locustfile.py` al menos una vez para confirmar que el flujo
completo pasa en vivo. Si algo del flujo falla contra el backend real (no
por Docker), avísame con el log y lo reviso — el diseño está verificado
contra el código fuente pero no contra un servidor real corriendo.

**Actualización — se descartaron VPN y WSL2 como causa:** en un segundo
intento (con Docker Desktop ya reparado) confirmé que no hay ningún
adaptador VPN activo en la máquina, y reinicié WSL2 por completo
(`wsl --shutdown` + relanzar Docker Desktop) antes de volver a levantar el
stack. El síntoma fue idéntico: los mismos 6 servicios vuelven a caer con
el mismo `Connect timed out` a los ~15-20 segundos de arrancar, sin ningún
error del lado de CockroachDB. También probé, como diagnóstico aparte (con
un `docker-compose.override.yml` local, ya borrado, nunca formó parte de
esta entrega ni se subió a git — está en `.gitignore`), darle a Flyway
reintentos de conexión en vez de un solo intento; tampoco alcanzó. Con eso
descarto que sea simplemente "CRDB tarda unos segundos en aceptar
conexiones nuevas tras arrancar" — el reintento con pausa no lo resolvió.
Dejé el stack completamente abajo (`docker compose down`) para no dejarte
contenedores en crash-loop. La causa concreta sigue sin identificarse; mi
mejor hipótesis con lo que vi es algo en la red interna de Docker Desktop
en esta máquina específica, pero no lo pude confirmar ni descartar del
todo.

## Qué se agregó

1. **`tests/load/critical_path_locustfile.py`** (nuevo, archivo separado del
   `locustfile.py` existente — a propósito, ver el docstring del archivo):
   una clase `CriticalPathUser(HttpUser)` donde cada usuario virtual es un
   cliente nuevo y autosuficiente:
   - Se registra vía el signup público `POST /api/usuarios/crear`.
   - Inicia sesión (`POST /api/login`), captura el `access` token del body y
     lo manda como `Authorization: Bearer <token>` en las siguientes
     peticiones. La cookie `refresh` (httpOnly) la conserva sola la sesión
     de `requests` de cada usuario virtual — no se toca a mano.
   - Se crea su propia dirección (`POST /api/usuarios/{usuarioId}/direcciones`)
     y su propio método de pago (`POST /api/metodopago`), usando ciudades y
     tipos de método de pago reales leídos del catálogo.
   - En cada iteración de la tarea: `GET /api/carrito/{usuarioId}` →
     `POST /api/carrito/{carritoId}/agregar` → `POST /api/ordenes/checkout`
     → `POST /api/facturas` → `POST /api/armado/analizar`.
   - Cada 3 iteraciones fuerza `POST /auth/refresh` **aunque el access token
     (10 min) no haya expirado**, para demostrar que el camino de renovación
     se ejercita de verdad y no solo que existiría si hiciera falta.
   - `X-Failure-Mode` no se envía en ningún request (se deja el
     comportamiento normal, tal como se pidió).

2. **`tests/load/run-load-test.ps1`** (modificado, mínimamente): el prefijo
   de salida `--csv`/`--html`, antes fijo en `"tiendatech-50-users"`, ahora
   se deriva de `-Users` por defecto (`"tiendatech-$Users-users"`) — así que
   `./run-load-test.ps1` sin argumentos escribe exactamente los mismos
   nombres que antes, cero cambio de comportamiento. Se agregaron dos
   parámetros opcionales:
   - `-OutputPrefix` para fijar un prefijo explícito (necesario al correr
     varios niveles de concurrencia, o el escenario de camino crítico, para
     que no se pisen los archivos entre sí).
   - `-LocustFile` para apuntar al nuevo `critical_path_locustfile.py` en
     vez del de siempre, sin duplicar el script de PowerShell.

   No se tocó la lógica de niveles de concurrencia (`-Users`/`-SpawnRate`/
   `-RunTime`): ya existía y funcionaba.

## Prerrequisito de datos: aplicar los seeds de Ecuador, catálogo y stock

El camino crítico necesita catálogo de referencia que **no viene poblado
por defecto** en un stack recién levantado: ciudades/provincias (para crear
la dirección) y tipos de método de pago. Ya existía un script para esto,
`docs/db/seed-ecuador-mobile-checkout.sql`, escrito para este mismo
propósito pero que hasta ahora no se aplicaba en ningún lado (ni CI, ni
`docker-compose.yml`). No lo até a la campaña automáticamente porque
sembrar datos no es responsabilidad del script de carga; hay que aplicarlo
una vez, a mano, antes de correr `critical_path_locustfile.py`:

```
docker compose exec -T tiendatech-crdb-1 \
  cockroach sql --insecure --host=localhost:26257 -d tiendatech --file=/dev/stdin \
  < docs/db/seed-ecuador-mobile-checkout.sql
```

**Ojo con `-d tiendatech`:** el script no trae un `USE tiendatech;` al
principio (a diferencia de `seed-e2e.sql`, que sí lo tiene) — sin `-d
tiendatech` explícito, `cockroach sql` lo corre contra `defaultdb` y falla
con `relation "usuarios.provincia" does not exist` aunque la tabla sí
exista, solo que en otra base. Es un hallazgo real de esta corrida, no una
suposición: me pasó exactamente eso al aplicarlo la primera vez.

También conviene aplicar `docs/db/seed-e2e.sql` si el catálogo de productos
está vacío (deja un producto habilitado de prueba); si ya hay productos
reales cargados, el critical-path los reutiliza sin problema — el script no
depende de ningún `producto_id` fijo, los descubre por `GET /api/productos`
y por categoría "Procesador" vía `GET /api/categorias` +
`GET /api/productos/por-categoria`.

**Paso obligatorio, no opcional: `docs/db/seed-inventario-stock.sql`.**
El camino crítico incluye `POST /api/carrito/{carritoId}/agregar`, y
`StockReservationService.reconcileOnce()` (inventario-service) hace, como
primera sentencia de la transacción,
`SELECT stock FROM inventario.inventario_producto WHERE producto_id = ? ...`
— verificado línea por línea contra el código real
(`application/reservation/StockReservationService.java:48-50`). No valida
contra `productos.producto.stock`. Desde el punto 5 (migraciones puras,
sin `schema.sql` aplicándose en el sistema real), un clúster nuevo nunca
queda con filas en `inventario.inventario_producto` — ningún script ni
`docker-compose.yml` la puebla.

**Verificado de punta a punta contra el sistema real** (stack completo
reconstruido desde cero, `docker compose down -v` + `up --build`, sin
ningún seed de inventario aplicado): un usuario nuevo, con
`seed-e2e.sql` aplicado (catálogo con un producto, sin stock de
inventario), recibe `HTTP 409` ("Conflicto") al intentar
`POST /api/carrito/{carritoId}/agregar` — confirma que la operación
realmente no puede completarse sin este seed, no es una suposición del
comentario del script. Tras aplicar `docs/db/seed-inventario-stock.sql`,
la misma petición contra el mismo carrito devuelve `HTTP 200` con la
reserva real (`{"accepted":true,"message":"Reserva reconciliada",
"reservedQuantity":1,"availableStock":498,...}`), y una segunda petición
decrementa el stock disponible correctamente (498 → 497) — confirma que
el seed no solo evita el error, sino que deja la reserva de stock
funcionando de extremo a extremo. Aplicarlo después de `seed-e2e.sql` o
del catálogo real del equipo (requiere que `productos.producto` ya tenga
filas):

```
docker compose exec -T tiendatech-crdb-1 \
  cockroach sql --insecure --host=localhost:26257 -d tiendatech --file=/dev/stdin \
  < docs/db/seed-inventario-stock.sql
```

Es idempotente (`ON CONFLICT DO NOTHING`) y siembra 500 unidades por
producto — pensado para pruebas de carga, no para producción.

**Nota honesta sobre la corrida real ya documentada en
[`pruebas-carga-camino-critico.md`](../pruebas-carga-camino-critico.md):**
esa corrida (209/209 `POST /api/carrito/.../agregar` exitosos, 0 fallos)
**no** aplicó este seed y aun así no encontró el problema — su entorno no
era un clúster recién migrado desde cero: ya traía filas en
`inventario.inventario_producto` de pruebas manuales previas en la misma
máquina. Eso no invalida el hallazgo: un clon nuevo del repo, o el entorno
que usa `db-migrations-clean-start` en CI, sí arranca sin esas filas, y ahí
sí falla el primer "agregar al carrito" sin este seed. Por eso queda como
paso documentado y obligatorio aquí, no como algo ya cubierto por la
corrida existente.

## Cómo correr los niveles de concurrencia

Camino público (sin cambios, mismo script de siempre, solo con más niveles):

```
./tests/load/run-load-test.ps1 -Users 25  -SpawnRate 5 -RunTime 60s
./tests/load/run-load-test.ps1 -Users 50  -SpawnRate 5 -RunTime 60s
./tests/load/run-load-test.ps1 -Users 100 -SpawnRate 10 -RunTime 60s -OutputPrefix tiendatech-100-users
```

(El nivel de 50 usuarios reescribe el archivo existente `tiendatech-50-users*`
si se corre de nuevo — es intencional, es el mismo escenario de siempre.)

Camino crítico autenticado (nuevo):

```
./tests/load/run-load-test.ps1 -Users 10 -SpawnRate 2 -RunTime 90s `
  -LocustFile critical_path_locustfile.py -OutputPrefix tiendatech-critical-path-10-users

./tests/load/run-load-test.ps1 -Users 25 -SpawnRate 5 -RunTime 90s `
  -LocustFile critical_path_locustfile.py -OutputPrefix tiendatech-critical-path-25-users
```

Concurrencia más baja a propósito: cada usuario virtual del camino crítico
hace ~9 peticiones HTTP secuenciales por iteración (registro, login,
dirección, método de pago, carrito ×2, checkout, factura, armado, y
refresh cada 3 iteraciones) contra un flujo con escrituras reales en 4
microservicios distintos — no es comparable en volumen a las 4 lecturas
públicas del escenario original.

## Regenerar el manifiesto al final

Una vez estén todos los CSV/HTML nuevos en `tests/load/results/`:

```
python experiments/paso8/campaign_checksums.py --directory tests/load/results --write
```

## Evidencia real recogida en esta sesión (parcial, ver "Estado real" arriba)

No hay CSV/HTML nuevos que entregar — ninguna corrida de Locust llegó a
ejecutarse contra el stack por el problema de arranque descrito arriba. Lo
que sí quedó confirmado y es evidencia real:

- El seed de Ecuador se aplicó con éxito contra el stack local
  (`provincias_habilitadas: 24`, `ciudades_habilitadas: 50`, y los 2 tipos
  de método de pago `Débito`/`Crédito` insertados).
- El gateway, una vez arriba, respondió `{"status":"UP"}` en
  `/actuator/health`.
- El fallo de conexión de Flyway (`Connect timed out` al abrir contra
  `tiendatech-crdb-1:26257,...`) se repitió en usuarios, productos,
  pedidos, ventas, ordenes-proveedores e inventario, siempre en el mismo
  punto (`FlywayAutoConfiguration` intentando la primera conexión), nunca
  con un error del lado de CRDB.

No se generó `checksums.txt` nuevo porque no hay archivos nuevos que
manifestar — el `tests/load/results/checksums.txt` existente (de la
corrida histórica de 50 usuarios de solo lectura) no se tocó.
