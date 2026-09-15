"""Camino critico autenticado (punto 14): registro -> login -> renovacion del
testigo -> carrito -> checkout -> factura -> asistente de armado.

Cada usuario virtual es un cliente nuevo, autosuficiente: se registra via el
signup publico (POST /api/usuarios/crear), inicia sesion, se crea su propia
direccion y metodo de pago, y solo entonces entra al bucle de la tarea. No
depende de datos precargados de usuario -- pero SI depende de catalogo de
referencia (ciudades/provincias y tipos de metodo de pago) que no viene
poblado por defecto en un stack recien levantado; ver tests/load/README.md
para el prerequisito de aplicar docs/db/seed-ecuador-mobile-checkout.sql.

Separado de locustfile.py (que sigue intacto con sus 4 lecturas publicas) a
proposito: mezclar ambas clases en el mismo archivo haria que
`locust -f locustfile.py` sin argumentos reparta los usuarios entre las dos
por defecto, cambiando silenciosamente el significado de las corridas
historicas de solo lectura.
"""

from __future__ import annotations

import random
import threading
import uuid
from datetime import date

from locust import HttpUser, between, task

REFRESH_EVERY_N_ITERATIONS = 3  # fuerza POST /auth/refresh aunque el access token (10 min) no haya expirado


def _unwrap(resp) -> dict:
    """Todas las respuestas JSON pasan por ApiResponseAdvice: {status,data,message,timestamp}."""
    try:
        body = resp.json()
    except ValueError:
        return {}
    return body.get("data", body) if isinstance(body, dict) else {}


def _detalle_error(resp) -> str:
    data = _unwrap(resp)
    if isinstance(data, dict):
        mensaje = data.get("message") or data.get("mensaje")
        if mensaje:
            return str(mensaje)
    return (resp.text or "").strip()[:200]


class _CatalogoCompartido:
    """Productos y categoria CPU resueltos una sola vez y reusados por todos
    los usuarios virtuales: son datos de referencia, no datos por-usuario, y
    volver a pedirlos en cada on_start solo suma carga redundante sobre
    /api/productos y /api/categorias durante una rafaga de spawns."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._productos: list[dict] | None = None
        self._cpu_producto_id: int | None = None

    def productos(self, client) -> list[dict]:
        with self._lock:
            if self._productos is not None:
                return self._productos
            with client.get("/api/productos?page=0&size=50", name="GET /api/productos (setup)",
                             catch_response=True) as resp:
                if resp.status_code != 200:
                    resp.failure(f"catalogo HTTP {resp.status_code}")
                    return []
                data = _unwrap(resp)
                candidatos = [p for p in data if p.get("habilitado") and (p.get("stock") or 0) > 0] \
                    if isinstance(data, list) else []
                self._productos = candidatos
                return candidatos

    def cpu_producto_id(self, client) -> int | None:
        with self._lock:
            if self._cpu_producto_id is not None:
                return self._cpu_producto_id
        with client.get("/api/categorias", name="GET /api/categorias (setup)", catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"categorias HTTP {resp.status_code}")
                return None
            categorias = _unwrap(resp)
            cpu = next((c for c in categorias if str(c.get("nombre", "")).strip().lower() == "procesador"), None)
            if cpu is None:
                return None
            categoria_id = cpu.get("id") or cpu.get("id_categoria")
        with client.get(f"/api/productos/por-categoria?categoriaId={categoria_id}",
                         name="GET /api/productos/por-categoria (setup)", catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"productos por categoria HTTP {resp.status_code}")
                return None
            productos = _unwrap(resp)
            if not productos:
                return None
            producto_id = productos[0].get("id") or productos[0].get("producto_id")
            with self._lock:
                self._cpu_producto_id = producto_id
            return producto_id


_catalogo = _CatalogoCompartido()


class CriticalPathUser(HttpUser):
    """Un cliente nuevo completo: se registra, inicia sesion y recorre
    catalogo -> carrito -> checkout -> factura -> asistente de armado."""

    wait_time = between(1.0, 3.0)

    def on_start(self) -> None:
        self._iteraciones = 0
        self.producto_id: int | None = None
        self.cpu_producto_id: int | None = None
        if not self._registrar_y_loguear():
            return
        self._crear_direccion()
        self._crear_metodo_pago()
        self.producto_id = self._elegir_producto()
        self.cpu_producto_id = _catalogo.cpu_producto_id(self.client)

    # --- setup por usuario -------------------------------------------------

    def _registrar_y_loguear(self) -> bool:
        sufijo = uuid.uuid4().hex[:12]
        self.usuario = f"carga{sufijo}"
        self.contrasena = "Secreto123!"
        cedula = "17" + str(random.randint(10_000_000, 99_999_999))

        with self.client.post(
                "/api/usuarios/crear",
                json={
                    "nombre": f"Carga {sufijo}",
                    "cedula": cedula,
                    "correo": f"{self.usuario}@tiendatech.load",
                    "telefono": "09" + str(random.randint(10_000_000, 99_999_999)),
                    "usuario": self.usuario,
                    "contrasena": self.contrasena,
                },
                name="POST /api/usuarios/crear",
                catch_response=True,
        ) as resp:
            if resp.status_code not in (200, 201):
                resp.failure(f"registro HTTP {resp.status_code}: {_detalle_error(resp)}")
                return False

        return self._login()

    def _login(self) -> bool:
        with self.client.post(
                "/api/login",
                json={"usuario": self.usuario, "contrasena": self.contrasena},
                name="POST /api/login",
                catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"login HTTP {resp.status_code}: {_detalle_error(resp)}")
                return False
            data = _unwrap(resp)
            token = data.get("access") or data.get("token")
            usuario_id = (data.get("user") or {}).get("usuarioId")
            if not token or not usuario_id:
                resp.failure("login 200 pero sin access/usuarioId en el body")
                return False
            self.token = token
            self.usuario_id = usuario_id
            return True

    def _auth_headers(self) -> dict:
        return {"Authorization": f"Bearer {self.token}"}

    def _crear_direccion(self) -> None:
        self.direccion_id = None
        with self.client.get("/api/ciudades", name="GET /api/ciudades (setup)", catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"ciudades HTTP {resp.status_code}")
                return
            ciudades = _unwrap(resp)
            if not ciudades:
                resp.failure("catalogo de ciudades vacio: aplicar docs/db/seed-ecuador-mobile-checkout.sql")
                return
            ciudad_id = random.choice(ciudades).get("ciudadId")

        with self.client.post(
                f"/api/usuarios/{self.usuario_id}/direcciones",
                json={"calle": "Av. de Carga 123", "referencia": "Prueba de carga (punto 14)", "ciudadId": ciudad_id},
                headers=self._auth_headers(),
                name="POST /api/usuarios/[usuarioId]/direcciones",
                catch_response=True,
        ) as resp:
            if resp.status_code not in (200, 201):
                resp.failure(f"crear direccion HTTP {resp.status_code}: {_detalle_error(resp)}")
                return
            self.direccion_id = _unwrap(resp).get("direccionId")

    def _crear_metodo_pago(self) -> None:
        self.metodopago_id = None
        with self.client.get("/api/metodopago/tipos", headers=self._auth_headers(),
                              name="GET /api/metodopago/tipos (setup)", catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"tipos de metodo de pago HTTP {resp.status_code}")
                return
            tipos = _unwrap(resp)
            if not tipos:
                resp.failure("catalogo de tipos de metodo de pago vacio: aplicar "
                              "docs/db/seed-ecuador-mobile-checkout.sql")
                return
            tipo_id = random.choice(tipos).get("tipoId")

        with self.client.post(
                "/api/metodopago",
                json={"numeroTarjeta": str(random.randint(10**15, 10**16 - 1)),
                      "fechaExpiracion": "2030-12-31", "tipoId": tipo_id},
                headers=self._auth_headers(),
                name="POST /api/metodopago",
                catch_response=True,
        ) as resp:
            if resp.status_code not in (200, 201):
                resp.failure(f"crear metodo de pago HTTP {resp.status_code}: {_detalle_error(resp)}")
                return
            location = resp.headers.get("Location", "")
            self.metodopago_id = int(location.rstrip("/").rsplit("/", 1)[-1]) if location else None

    def _elegir_producto(self) -> int | None:
        productos = _catalogo.productos(self.client)
        if not productos:
            return None
        producto = random.choice(productos)
        return producto.get("productoId") or producto.get("producto_id")

    # --- renovacion forzada del testigo ------------------------------------

    def _renovar_testigo(self) -> bool:
        # El access token vive 10 minutos (auth.access.minutes=10): en una
        # corrida corta probablemente nunca expiraria solo. Se fuerza aqui
        # cada REFRESH_EVERY_N_ITERATIONS iteraciones para demostrar que el
        # camino de renovacion realmente se ejercita, no solo que existiria
        # si hiciera falta. Usa la cookie "refresh" (httpOnly) que la sesion
        # de requests de Locust ya conserva automaticamente desde el login;
        # no se envia body.
        with self.client.post("/auth/refresh", name="POST /auth/refresh", catch_response=True) as resp:
            if resp.status_code != 200:
                resp.failure(f"refresh HTTP {resp.status_code}: {_detalle_error(resp)}")
                return False
            data = _unwrap(resp)
            token = data.get("access")
            if not token:
                resp.failure("refresh 200 pero sin access en el body")
                return False
            self.token = token
            return True

    # --- camino critico ------------------------------------------------

    @task
    def flujo_completo(self) -> None:
        if getattr(self, "token", None) is None:
            return  # el registro/login de on_start no se completo; nada que hacer esta iteracion
        if self.producto_id is None:
            self.producto_id = self._elegir_producto()
        if self.direccion_id is None or self.metodopago_id is None:
            return  # sin direccion/metodo de pago propios no hay checkout posible

        self._iteraciones += 1
        if self._iteraciones % REFRESH_EVERY_N_ITERATIONS == 0:
            self._renovar_testigo()

        carrito_id = self._obtener_carrito()
        if carrito_id is None:
            return
        if not self._agregar_al_carrito(carrito_id):
            return
        orden = self._checkout()
        if orden is None:
            return
        self._facturar(orden)
        self._analizar_armado()

    def _obtener_carrito(self) -> int | None:
        with self.client.get(
                f"/api/carrito/{self.usuario_id}",
                headers=self._auth_headers(),
                name="GET /api/carrito/[usuarioId]",
                catch_response=True,
        ) as resp:
            if resp.status_code == 401 and self._login():
                return self._obtener_carrito()
            if resp.status_code != 200:
                resp.failure(f"obtener carrito HTTP {resp.status_code}: {_detalle_error(resp)}")
                return None
            return _unwrap(resp).get("carritoId")

    def _agregar_al_carrito(self, carrito_id: int) -> bool:
        with self.client.post(
                f"/api/carrito/{carrito_id}/agregar",
                json={"productoId": self.producto_id, "cantidad": 1},
                headers=self._auth_headers(),
                name="POST /api/carrito/[carritoId]/agregar",
                catch_response=True,
        ) as resp:
            if resp.status_code == 401 and self._login():
                return self._agregar_al_carrito(carrito_id)
            if resp.status_code not in (200, 201):
                resp.failure(f"agregar al carrito HTTP {resp.status_code}: {_detalle_error(resp)}")
                return False
            return True

    def _checkout(self) -> dict | None:
        with self.client.post(
                "/api/ordenes/checkout",
                json={"direccionId": self.direccion_id, "metodopagoId": self.metodopago_id},
                headers=self._auth_headers(),
                name="POST /api/ordenes/checkout",
                catch_response=True,
        ) as resp:
            if resp.status_code == 401 and self._login():
                return self._checkout()
            if resp.status_code not in (200, 201):
                resp.failure(f"checkout HTTP {resp.status_code}: {_detalle_error(resp)}")
                return None
            return _unwrap(resp)

    def _facturar(self, orden: dict) -> None:
        producto = next((p for p in _catalogo.productos(self.client)
                          if (p.get("productoId") or p.get("producto_id")) == self.producto_id), None)
        precio = float(producto.get("preciounitario")) if producto else 0.0
        subtotal_linea = round(precio, 2)
        iva_linea = round(subtotal_linea * 0.15, 2)
        total_linea = round(subtotal_linea + iva_linea, 2)

        with self.client.post(
                "/api/facturas",
                json={
                    "ordenId": orden.get("ordenId"),
                    "fechaOrden": orden.get("fecha") or date.today().isoformat(),
                    "usuarioId": self.usuario_id,
                    "subtotal": orden.get("subtotal", subtotal_linea),
                    "total": orden.get("total", total_linea),
                    "lineas": [{
                        "productoId": self.producto_id, "cantidad": 1, "precio": precio,
                        "subtotal": subtotal_linea, "iva": iva_linea, "total": total_linea,
                    }],
                },
                headers=self._auth_headers(),
                name="POST /api/facturas",
                catch_response=True,
        ) as resp:
            if resp.status_code == 401 and self._login():
                return self._facturar(orden)
            if resp.status_code not in (200, 201):
                resp.failure(f"factura HTTP {resp.status_code}: {_detalle_error(resp)}")

    def _analizar_armado(self) -> None:
        if self.cpu_producto_id is None:
            return  # sin categoria "Procesador" resuelta no hay analisis valido posible
        with self.client.post(
                "/api/armado/analizar",
                json={
                    "componentes": {
                        "cpu": self.cpu_producto_id, "gpu": None, "ram": None,
                        "mobo": None, "storage": None, "psu": None, "case": None,
                    },
                    "presupuestoMaximo": 1200.00,
                },
                headers=self._auth_headers(),
                name="POST /api/armado/analizar",
                catch_response=True,
        ) as resp:
            if resp.status_code == 401 and self._login():
                return self._analizar_armado()
            if resp.status_code not in (200, 201):
                resp.failure(f"armado HTTP {resp.status_code}: {_detalle_error(resp)}")
