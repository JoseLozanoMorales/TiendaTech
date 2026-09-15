# Decisión sobre la rama huérfana `origin/Matster_Repositories`

## Contexto

Al revisar el estado de las ramas remotas en la fase de cierre (punto #31 de
la Guía de Cierre PFC AGLS: *"Decidir sobre ese commit integrando por
solicitud de incorporación si su contenido aporta, o eliminando la rama y
dejando constancia de la decisión, de modo que ninguna rama quede por
delante al cierre"*), se encontró una rama remota que nunca se integró a
`main`:

- **Rama:** `origin/Matster_Repositories`
- **Último commit:** `e52933e0470eac0414190f6304db14d472eb6c6f`
- **Fecha del commit:** 2026-08-09
- **Estado respecto a `main`:** 1 commit adelante, 334 commits detrás
  (verificado con `git rev-list --left-right --count origin/main...origin/Matster_Repositories`)

## Qué contiene el commit

Se revisó el diff completo (`git show e52933e0...`, 1018 líneas). El
contenido es real, no un experimento vacío:

1. `docker-compose.yml` — cambio menor de nombre de base de datos
   (`TiendaTechV21` → `TiendaTechV21.5`).
2. `frontend/.../application.yml` — nuevas rutas de gateway para
   categorías/galería.
3. Una sección de administración **"Órdenes y Proveedores"**: modal para
   crear/editar proveedores, listado y creación de órdenes de compra con
   detalle de líneas, y registro de recepción (total o parcial) de
   mercancía — implementada en
   `frontend/.../js/admin/proveedores-compras.js` (1408 líneas).
4. Cambios de backend reales en `ordenes-proveedores-service`:
   nuevos campos en `OrdenCompra` (`subtotalPedido`, `ivaPedido`,
   `totalPedido`, `detalle`) y un método `listarDetalle` en
   `OrdenCompraRepository`.
5. Un archivo nuevo, `ventas-service/.../static/factura.html`, con una
   vista de factura imprimible/descargable en PDF.

## Por qué no se integra tal cual

El commit es de **hace más de un mes** respecto al cierre y arrastra
supuestos de una etapa anterior de la arquitectura del proyecto que ya no
son válidos:

- Toca `src/main/resources/static/admin.html` **sin** el prefijo
  `frontend/` — es decir, la ruta del monolito viejo, no la estructura
  actual `Apps/web/frontend/`.
- `proveedores-compras.js` llama a la API con una URL **hardcodeada**
  (`http://localhost:8084`) en vez de pasar por el enrutamiento real del
  API Gateway que el sistema usa hoy.
- El propio autor original dejó un comentario en `factura.html`
  reconociendo la migración a medias: *"en el monolito esto regresaba al
  index.html de la tienda. Aquí ventas-service ya no sirve esa página;
  ajusta el href..."*.
- Los cambios de `OrdenCompra`/`OrdenCompraRepository` no se verificaron
  contra el esquema y las migraciones Flyway actuales (posteriores al
  cierre del punto 5, "Esquema desde cero solo con migraciones").

Integrarlo correctamente no sería un simple merge: exigiría reescribir las
rutas de API del módulo de proveedores/compras, reubicar los archivos
estáticos en la estructura vigente, y validar (con pruebas propias) los
cambios de backend contra el esquema actual — trabajo de desarrollo nuevo,
no una integración mecánica, y no cabe con seguridad en el tiempo restante
del periodo de cierre sin arriesgar la estabilidad de lo que ya está verde
en CI.

## Decisión

**Se documenta el contenido de la rama en esta acta y se elimina la rama
remota `origin/Matster_Repositories`.** No se pierde información: el
commit queda accesible por su SHA (`e52933e0470eac0414190f6304db14d472eb6c6f`)
en el historial de Git aunque la referencia de rama se borre, y este
documento deja constancia de qué había ahí y por qué se decidió no
incorporarlo en este cierre.

Si en el futuro el equipo quiere retomar el módulo de "Proveedores y
Compras" (gestión de proveedores, órdenes de compra con recepción parcial,
y facturación imprimible), este documento y el hash del commit son el
punto de partida para reimplementarlo sobre la estructura actual del
proyecto, en vez de partir de cero.

## Cómo se eliminó

```
git push origin --delete Matster_Repositories
```

---

*Decisión registrada el 15 de septiembre de 2026 por Jhinson Stalyn
Aucatoma Celorio durante el cierre de los puntos pendientes de la Guía de
Cierre PFC AGLS (periodo de supletorio).*
