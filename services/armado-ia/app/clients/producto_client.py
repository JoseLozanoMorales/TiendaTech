"""
Cliente HTTP hacia productos-service. Circuit breaker + reintento en las 3
lecturas (GET, idempotentes por naturaleza) -- mismo patron que la version
Java: retry ENVUELVE al circuit breaker (si el circuito esta abierto,
CircuitBreakerError no es reintentable y se propaga de inmediato). Solo se
reintentan fallas transitorias (timeout/conexion/5xx); un 404 nunca se
reintenta y se traduce a BadRequestError.
"""
import json
import logging
from decimal import Decimal

import httpx
import pybreaker
from tenacity import retry, retry_if_exception, stop_after_attempt, wait_exponential

from app.config import settings
from app.domain.models import CategoriaInfo, ProductoCatalogo
from app.errors import BadRequestError

log = logging.getLogger("armado_ia.producto_client")

class _EstadoBreakerListener(pybreaker.CircuitBreakerListener):
    """
    Aprovecha la API de listener de pybreaker para exponer el estado del
    circuit breaker (equivalente a /actuator/circuitbreakers de la version
    Java) sin tener que inspeccionar/mutar el breaker desde afuera.
    """

    def __init__(self):
        self.llamadas_totales = 0
        self.llamadas_fallidas = 0
        self.ultima_transicion: str | None = None

    def before_call(self, cb, func, *args, **kwargs):
        self.llamadas_totales += 1

    def failure(self, cb, exc):
        self.llamadas_fallidas += 1

    def state_change(self, cb, old_state, new_state):
        self.ultima_transicion = f"{old_state.name} -> {new_state.name}"


# Un unico circuit breaker compartido por las 3 lecturas, igual que la
# version Java (un solo CircuitBreaker "productoClient" para
# obtenerPorId/listarPorCategoria/listarCategorias). No se excluye ninguna
# excepcion del conteo de fallos -- tampoco se configuro record-exceptions en
# la version Java, asi que se replica el mismo comportamiento por defecto
# (incluso un 404 cuenta como fallo del circuito, no solo los 5xx).
_estado_listener = _EstadoBreakerListener()
breaker = pybreaker.CircuitBreaker(fail_max=5, reset_timeout=10, listeners=[_estado_listener])


def estado_circuit_breaker() -> dict:
    return {
        "circuitBreakers": {
            "productoClient": {
                "state": breaker.current_state.upper(),
                "totalCalls": _estado_listener.llamadas_totales,
                "failedCalls": _estado_listener.llamadas_fallidas,
                "failureThreshold": breaker.fail_max,
                "resetTimeoutSeconds": breaker.reset_timeout,
                "lastStateChange": _estado_listener.ultima_transicion,
            }
        }
    }


class _NotFound(Exception):
    pass


class _ServerError(Exception):
    pass


def _es_reintentable(exc: BaseException) -> bool:
    if isinstance(exc, (httpx.TimeoutException, httpx.ConnectError, httpx.RemoteProtocolError)):
        return True
    return isinstance(exc, _ServerError)


class ProductoClient:
    def __init__(self):
        timeout = httpx.Timeout(
            settings.http_client.read_timeout_ms / 1000,
            connect=settings.http_client.connect_timeout_ms / 1000,
        )
        self._client = httpx.Client(base_url=settings.productos_service_base_url, timeout=timeout)

    def obtener_por_id(self, producto_id: int) -> ProductoCatalogo:
        try:
            raw = self._lectura(f"/api/productos/{producto_id}")
        except _NotFound:
            raise BadRequestError(f"Producto {producto_id} no encontrado en productos-service")
        return self._mapear(raw)

    def listar_por_categoria(self, categoria_id: int) -> list[ProductoCatalogo]:
        raw = self._lectura("/api/productos/por-categoria", params={"categoriaId": categoria_id})
        return [self._mapear(fila) for fila in (raw or [])]

    def listar_categorias(self) -> list[CategoriaInfo]:
        raw = self._lectura("/api/categorias")
        return [CategoriaInfo(id=int(fila["id_categoria"]), nombre=str(fila["nombre"])) for fila in (raw or [])]

    @retry(retry=retry_if_exception(_es_reintentable), stop=stop_after_attempt(3),
           wait=wait_exponential(multiplier=0.2, min=0.2, max=2), reraise=True)
    def _lectura(self, path: str, params: dict | None = None):
        return breaker.call(self._get, path, params)

    def _get(self, path: str, params: dict | None):
        response = self._client.get(path, params=params)
        if response.status_code == 404:
            raise _NotFound()
        if response.status_code >= 500:
            raise _ServerError(f"{path} -> {response.status_code}")
        response.raise_for_status()
        return self._desenvolver_respuesta(response.json())

    @staticmethod
    def _desenvolver_respuesta(payload):
        """Acepta tanto el contrato comun del servicio como el payload legado.

        productos-service envuelve sus respuestas JSON en
        ``{status, data, message, timestamp}``. El cliente anterior entregaba
        ese objeto completo a ``_mapear`` y terminaba evaluando
        ``int(None)`` porque los campos del producto viven dentro de ``data``.
        """
        if isinstance(payload, dict) and {"status", "data", "message", "timestamp"} <= payload.keys():
            return payload["data"]
        return payload

    def _mapear(self, fila: dict) -> ProductoCatalogo:
        precio_raw = fila.get("precio", fila.get("preciounitario"))
        precio = Decimal(str(precio_raw)) if precio_raw is not None else Decimal(0)
        return ProductoCatalogo(
            id=int(fila.get("producto_id", fila.get("id"))),
            nombre=str(fila.get("nombre")),
            precio=precio,
            categoria_id=fila.get("categoria_id"),
            categoria_nombre=fila.get("categoria"),
            habilitado=bool(fila.get("habilitado", True)),
            atributos=self._extraer_atributos(fila.get("atributos")),
        )

    @staticmethod
    def _extraer_atributos(atributos_raw) -> dict:
        # productos-service devuelve la columna JSONB "atributos" tal cual la
        # serializa Jackson sobre el PGobject del driver JDBC, NO como un
        # objeto JSON anidado limpio: {"type":"jsonb","value":"{...}","null":false}.
        # Verificado en vivo contra productos-service sobre CockroachDB.
        if not atributos_raw:
            return {}
        if isinstance(atributos_raw, dict):
            atributos = atributos_raw
            # En catalogos que ya fueron migrados mas de una vez puede llegar
            # un PGobject JSONB dentro de otro PGobject JSONB. Se desenvuelven
            # todas las capas conocidas para recuperar el objeto tecnico real.
            for _ in range(8):
                valor = atributos.get("value")
                if not isinstance(valor, str) or atributos.get("type") != "jsonb":
                    return atributos
                try:
                    decodificado = json.loads(valor)
                except (TypeError, ValueError):
                    return {}
                if not isinstance(decodificado, dict):
                    return {}
                atributos = decodificado
            return atributos
        return {}


producto_client = ProductoClient()
