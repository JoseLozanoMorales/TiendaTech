import json
import logging
import time
from datetime import UTC, datetime

from fastapi import Depends, FastAPI
from fastapi.responses import JSONResponse
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.sdk.resources import SERVICE_NAME as OTEL_SERVICE_NAME_KEY
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from prometheus_client import Counter, Gauge, Histogram
from prometheus_fastapi_instrumentator import Instrumentator

from app import armado_service
from app.clients.producto_client import estado_circuit_breaker
from app.config import settings
from app.errors import registrar_exception_handlers
from app.explicacion.bedrock_client import BedrockExplicacionClient
from app.explicacion.client import ExplicacionClient
from app.explicacion.fallback_client import DeterministicExplicacionClient
from app.explicacion.service import ExplicacionService
from app.schemas import AnalizarRequest, AnalizarResponse
from app.security import IdentidadOpcional, identidad_requerida


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "service": "tiendatech-armado-ia",
            "logger": record.name,
            "message": record.getMessage(),
        }
        # Paso 10: mismo par trace_id/span_id que Micrometer Tracing agrega a
        # los logs JSON de los seis microservicios Java (via MDC). Aqui se lee
        # del span OTel activo, si lo hay (fuera de una peticion instrumentada
        # -- por ejemplo en el arranque -- no hay span y se omiten los campos).
        span = trace.get_current_span()
        span_context = span.get_span_context()
        if span_context.is_valid:
            payload["trace_id"] = format(span_context.trace_id, "032x")
            payload["span_id"] = format(span_context.span_id, "016x")
        for field in ("method", "route", "status", "response_time_ms"):
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value
        return json.dumps(payload, ensure_ascii=False)


handler = logging.StreamHandler()
handler.setFormatter(JsonFormatter())
logging.basicConfig(level=logging.INFO, handlers=[handler], force=True)
http_logger = logging.getLogger("tiendatech.http")
SERVICE_NAME = "tiendatech-armado-ia"
active_connections = Gauge("active_connections", "Solicitudes HTTP activas", ["service"])
request_count = Counter(
    "request_count",
    "Total de solicitudes HTTP",
    ["service", "method", "route", "status"],
)
request_duration_seconds = Histogram(
    "request_duration_seconds",
    "Duracion de solicitudes HTTP en segundos",
    ["service", "method", "route", "status"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0),
)

app = FastAPI(title="tiendatech-armado-ia")
registrar_exception_handlers(app)

# Paso 10: trazado distribuido. Mismo colector (Jaeger local via OTLP/HTTP,
# ver docker-compose.yml) que reciben los seis microservicios Java a traves
# de Micrometer Tracing -- el estandar W3C Trace Context (cabecera
# "traceparent") es el mismo de ambos lados, asi que una compra que pasa por
# Java y por este servicio Python queda en una sola traza, no en dos.
# FastAPIInstrumentor crea el span de cada peticion entrante (y lee/propaga
# "traceparent" si ya viene de otro servicio); HTTPXClientInstrumentor hace lo
# mismo en cada llamada saliente a productos-service (ver
# app/clients/producto_client.py, que usa httpx sin cambios propios).
_tracer_provider = TracerProvider(
    resource=Resource.create({OTEL_SERVICE_NAME_KEY: "tiendatech-armado-ia"})
)
_tracer_provider.add_span_processor(
    BatchSpanProcessor(OTLPSpanExporter(endpoint=settings.otlp_tracing_endpoint))
)
trace.set_tracer_provider(_tracer_provider)
FastAPIInstrumentor.instrument_app(app)
HTTPXClientInstrumentor().instrument()


@app.middleware("http")
async def respuesta_uniforme(request, call_next):
    response = await call_next(request)
    if request.url.path in {"/metrics", "/actuator/health", "/openapi.json", "/docs"}:
        return response
    content_type = response.headers.get("content-type", "")
    if "application/json" not in content_type:
        return response
    raw = b"".join([chunk async for chunk in response.body_iterator])
    try:
        data = json.loads(raw or b"null")
    except json.JSONDecodeError:
        data = None
    message = data.get("message", data.get("detail", "OK")) if isinstance(data, dict) else "OK"
    return JSONResponse(
        status_code=response.status_code,
        content={"status": response.status_code, "data": data, "message": message,
                 "timestamp": datetime.now(UTC).isoformat()},
    )

# D6.1: expone /metrics con http_requests_total y http_request_duration_seconds
# (nombres literales, sin traducir -- a diferencia del lado Java con Micrometer,
# esta libreria ya usa exactamente los nombres que pide la rubrica). Metricas
# de negocio propias en app/metrics.py, registradas en el mismo REGISTRY global.
Instrumentator().instrument(app).expose(app)


@app.middleware("http")
async def observabilidad_http(request, call_next):
    labels = active_connections.labels(service="tiendatech-armado-ia")
    labels.inc()
    started = time.perf_counter()
    status = 500
    try:
        response = await call_next(request)
        status = response.status_code
        return response
    finally:
        elapsed = time.perf_counter() - started
        elapsed_ms = round(elapsed * 1000, 3)
        labels.dec()
        route_obj = request.scope.get("route")
        route = getattr(route_obj, "path", request.url.path)
        if route != "/metrics":
            metric_labels = {
                "service": SERVICE_NAME,
                "method": request.method,
                "route": route,
                "status": str(status),
            }
            request_count.labels(**metric_labels).inc()
            request_duration_seconds.labels(**metric_labels).observe(elapsed)
        http_logger.info(
            "http_request_completed",
            extra={
                "method": request.method,
                "route": route,
                "status": status,
                "response_time_ms": elapsed_ms,
            },
        )


def _crear_explicacion_client() -> ExplicacionClient | None:
    if settings.explicacion.proveedor == "bedrock":
        return BedrockExplicacionClient()
    return None


explicacion_service = ExplicacionService(_crear_explicacion_client(), DeterministicExplicacionClient())


@app.get("/actuator/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


# Aprovecha el listener de pybreaker (ver clients/producto_client.py) para
# exponer el estado del circuit breaker -- equivalente a
# /actuator/circuitbreakers de la version Java, evidencia de resiliencia.
@app.get("/actuator/circuitbreakers")
def circuitbreakers() -> dict[str, dict[str, dict[str, str | int | float | None]]]:
    return estado_circuit_breaker()


# Identidad opcional (ver security.py): el gateway protege /api/** asi que en
# la practica siempre llega, pero este endpoint no persiste nada por usuario
# y no bloquea si faltara. Si viene, se usa en el log.
@app.post("/api/armado/analizar", response_model=AnalizarResponse)
def analizar(request: AnalizarRequest, identidad: IdentidadOpcional = Depends(identidad_requerida)):
    return armado_service.analizar(request, identidad, explicacion_service)
