"""Locustfile del experimento real (Paso 8): checkout continuo contra el stack real.

Cada usuario virtual de Locust toma UN caso distinto del banco (usuario ya
registrado, con direccion y metodo de pago) y en bucle: agrega un item al
carrito y hace checkout. El checkout consume el carrito, por eso se vuelve a
agregar en cada iteracion -- no es un descuido, es necesario para sostener
throughput durante los 300s de medicion exigidos por la guia.

El modo de fallo (X-Failure-Mode) se sortea EN EL CLIENTE con la probabilidad
configurada: el servidor (ExperimentFaultInjector.java) aplica el fallo al
100% de las peticiones que traen el header, no tiene su propio sorteo de
probabilidad.

El JWT de acceso expira a los 10 minutos (auth.access.minutes=10). Como una
corrida completa del experimento dura horas, cada usuario virtual reintenta
login automaticamente ante un 401.

Variables de entorno que controla el orquestador (run_real_experiment.py):
  REQUEST_BANK_PATH   ruta al JSON de generate_request_bank.py
  FALLO_ACTIVO        none | omission | timing
  FAULT_PROBABILITY   ej. 0.10
"""

from __future__ import annotations

import itertools
import json
import os
import random
import threading
import time
import uuid
from collections import Counter

from locust import HttpUser, between, task, events

BANCO_PATH = os.environ.get("REQUEST_BANK_PATH")
FALLO_ACTIVO = os.environ.get("FALLO_ACTIVO", "none")
FAULT_PROBABILITY = float(os.environ.get("FAULT_PROBABILITY", "0.10"))
RESPONSE_CODES_PATH = os.environ.get("RESPONSE_CODES_PATH")

with open(BANCO_PATH, encoding="utf-8") as _fh:
    BANCO = json.load(_fh)

_indice_lock = threading.Lock()
_indice = itertools.count()
_codigos = Counter()
_codigos_lock = threading.Lock()


def _detalle_error(resp) -> str:
    try:
        body = resp.json()
        data = body.get("data", body) if isinstance(body, dict) else body
        if isinstance(data, dict):
            return str(data.get("message") or data.get("mensaje") or body.get("message") or "").strip()
    except (ValueError, TypeError):
        pass
    return (resp.text or "").strip()[:200]


def _siguiente_caso() -> dict:
    with _indice_lock:
        i = next(_indice) % len(BANCO)
    return BANCO[i]


class CheckoutUser(HttpUser):
    wait_time = between(0.2, 1.0)

    def on_start(self) -> None:
        self.caso = _siguiente_caso()
        self.token = self.caso["token"]
        self.device_id = f"locust-{self.caso['caseId']}-{uuid.uuid4()}"
        # Debe superar estados Lamport persistidos por ejecuciones anteriores.
        self.lamport = time.time_ns()
        self.carrito_id = self._obtener_carrito_id()

    def _obtener_carrito_id(self) -> int | None:
        with self.client.get(
                f"/api/carrito/{self.caso['usuarioId']}",
                headers={"Authorization": f"Bearer {self.token}"},
                name="GET /api/carrito/[usuarioId]",
                catch_response=True,
                timeout=15,
        ) as resp:
            if resp.status_code == 401 and self._reautenticar():
                return self._obtener_carrito_id()
            if resp.status_code != 200:
                resp.failure(f"obtener carrito HTTP {resp.status_code}")
                return None
            body = resp.json()
            data = body.get("data", body)
            return data.get("carritoId")

    def _headers(self, modo_fallo: str) -> dict:
        return {
            "Authorization": f"Bearer {self.token}",
            "X-Failure-Mode": modo_fallo,
            "X-Trace-Id": str(uuid.uuid4()),
            "Idempotency-Key": str(uuid.uuid4()),
        }

    def _reautenticar(self) -> bool:
        # Solo es un respaldo ante una expiracion inesperada. El orquestador
        # renueva todos los tokens mediante /api/login antes del warmup, fuera
        # de la ventana medida.
        with self.client.post(
                "/api/login",
                json={"usuario": self.caso["usuario"], "contrasena": self.caso["contrasena"]},
                name="POST /api/login (reauth inesperada)",
                catch_response=True,
                # 90s: a c=50 con E-2PC ya se observaron checkouts que
                # completan con 201 en el servidor a los 62-65s (contencion
                # legitima bajo la barrera sincrona, no un cuelgue). Un
                # timeout de cliente mas corto los reclasifica como "HTTP 0"
                # aunque el servidor si respondio correctamente.
                timeout=90,
        ) as resp:
            if resp.status_code == 200:
                body = resp.json()
                data = body.get("data", body)
                token = data.get("token") or data.get("access")
                if token:
                    self.token = token
                    return True
            resp.failure(f"reauth fallo: HTTP {resp.status_code}")
            return False

    def _elegir_modo_fallo(self) -> str:
        if FALLO_ACTIVO == "none":
            return "none"
        return FALLO_ACTIVO if random.random() < FAULT_PROBABILITY else "none"

    @task
    def comprar(self) -> None:
        if self.carrito_id is None:
            self.carrito_id = self._obtener_carrito_id()
            if self.carrito_id is None:
                return  # sin carrito no hay checkout posible esta iteracion; se reintenta la siguiente

        modo_fallo = self._elegir_modo_fallo()
        headers = self._headers(modo_fallo)

        with self.client.post(
                f"/api/carrito/{self.carrito_id}/agregar",
                json={"productoId": self.caso["productoId"], "cantidad": 1,
                      "deviceId": self.device_id, "lamportTimestamp": self.lamport,
                      "operationId": str(uuid.uuid4())},
                headers=headers,
                name="POST /api/carrito/[carritoId]/agregar",
                catch_response=True,
                # 90s: a c=50 con E-2PC ya se observaron checkouts que
                # completan con 201 en el servidor a los 62-65s (contencion
                # legitima bajo la barrera sincrona, no un cuelgue). Un
                # timeout de cliente mas corto los reclasifica como "HTTP 0"
                # aunque el servidor si respondio correctamente.
                timeout=90,
        ) as agregar:
            self.lamport += 1
            if agregar.status_code == 401 and self._reautenticar():
                headers = self._headers(modo_fallo)
                with self.client.post(
                        f"/api/carrito/{self.carrito_id}/agregar",
                        json={"productoId": self.caso["productoId"], "cantidad": 1,
                              "deviceId": self.device_id, "lamportTimestamp": self.lamport,
                              "operationId": str(uuid.uuid4())},
                        headers=headers,
                        name="POST /api/carrito/[carritoId]/agregar",
                        catch_response=True,
                        # 90s: a c=50 con E-2PC ya se observaron checkouts que
                # completan con 201 en el servidor a los 62-65s (contencion
                # legitima bajo la barrera sincrona, no un cuelgue). Un
                # timeout de cliente mas corto los reclasifica como "HTTP 0"
                # aunque el servidor si respondio correctamente.
                timeout=90,
                ) as agregar_retry:
                    self.lamport += 1
                    if agregar_retry.status_code not in (200, 201):
                        detalle = _detalle_error(agregar_retry)
                        agregar_retry.failure(
                            f"agregar carrito HTTP {agregar_retry.status_code}"
                            + (f": {detalle}" if detalle else "")
                        )
                        return
            elif agregar.status_code not in (200, 201):
                detalle = _detalle_error(agregar)
                agregar.failure(
                    f"agregar carrito HTTP {agregar.status_code}"
                    + (f": {detalle}" if detalle else "")
                )
                return

        with self.client.post(
                "/api/ordenes/checkout",
                json={"direccionId": self.caso["direccionId"], "metodopagoId": self.caso["metodopagoId"]},
                headers=headers,
                name=f"POST /api/ordenes/checkout [fallo={FALLO_ACTIVO}]",
                catch_response=True,
                # 90s: a c=50 con E-2PC ya se observaron checkouts que
                # completan con 201 en el servidor a los 62-65s (contencion
                # legitima bajo la barrera sincrona, no un cuelgue). Un
                # timeout de cliente mas corto los reclasifica como "HTTP 0"
                # aunque el servidor si respondio correctamente.
                timeout=90,
        ) as checkout:
            if checkout.status_code == 401 and self._reautenticar():
                headers = self._headers(modo_fallo)
                with self.client.post(
                        "/api/ordenes/checkout",
                        json={"direccionId": self.caso["direccionId"], "metodopagoId": self.caso["metodopagoId"]},
                        headers=headers,
                        name=f"POST /api/ordenes/checkout [fallo={FALLO_ACTIVO}]",
                        catch_response=True,
                        # 90s: a c=50 con E-2PC ya se observaron checkouts que
                # completan con 201 en el servidor a los 62-65s (contencion
                # legitima bajo la barrera sincrona, no un cuelgue). Un
                # timeout de cliente mas corto los reclasifica como "HTTP 0"
                # aunque el servidor si respondio correctamente.
                timeout=90,
                ) as checkout_retry:
                    if checkout_retry.status_code // 100 != 2:
                        checkout_retry.failure(
                            f"checkout HTTP {checkout_retry.status_code}: "
                            f"{_detalle_error(checkout_retry)}"
                        )
            elif checkout.status_code // 100 != 2:
                checkout.failure(
                    f"checkout HTTP {checkout.status_code}: {_detalle_error(checkout)}"
                )


@events.quitting.add_listener
def _resumen_final(environment, **kwargs) -> None:
    try:
        stats = environment.stats.total
        print(f"[locust] requests={stats.num_requests} fails={stats.num_failures} "
              f"rps={stats.total_rps:.2f} p95={stats.get_response_time_percentile(0.95):.1f}ms")
    except Exception as error:  # nunca debe impedir que Locust cierre y escriba los CSV
        print(f"[locust] no se pudo imprimir el resumen final: {error}")
    if RESPONSE_CODES_PATH:
        with _codigos_lock:
            with open(RESPONSE_CODES_PATH, "w", encoding="utf-8") as fh:
                json.dump(dict(sorted(_codigos.items())), fh, indent=2)


@events.request.add_listener
def _registrar_codigo(response=None, **kwargs) -> None:
    codigo = getattr(response, "status_code", None)
    if codigo is not None:
        with _codigos_lock:
            _codigos[str(codigo)] += 1
