"""
Envoltorio que garantiza que el LLM NUNCA sea un punto de fallo duro NI una
fuente de datos falsos: sin ExplicacionClient configurado, sin credenciales,
con cualquier error de red/modelo, O si el texto generado menciona un
modelo/spec que no vino en los datos de entrada (alucinacion), se cae a una
explicacion determinista construida solo con los datos ya calculados. Mejor
una explicacion sosa que una que miente.
"""
import logging

from app import metrics
from app.explicacion.client import ContextoExplicacion, ExplicacionClient
from app.explicacion.fallback_client import DeterministicExplicacionClient
from app.explicacion.validation import (
    ExplanationValidationHandler,
    build_explanation_validation_chain,
)

log = logging.getLogger("armado_ia.explicacion")

class ExplicacionService:
    def __init__(
        self,
        cliente: ExplicacionClient | None,
        fallback: ExplicacionClient | None = None,
        validator: ExplanationValidationHandler | None = None,
    ):
        self._cliente = cliente
        self._fallback: ExplicacionClient = fallback if fallback is not None else DeterministicExplicacionClient()
        self._validator = validator if validator is not None else build_explanation_validation_chain()

    def generar(self, contexto: ContextoExplicacion) -> str:
        if self._cliente is None:
            log.info("Sin ExplicacionClient activo (ver settings.explicacion.proveedor); usando fallback deterministico")
            metrics.registrar_explicacion_fallback()
            return self._fallback.explicar(contexto)
        try:
            texto = self._cliente.explicar(contexto)
            issue = self._validator.validate(texto, contexto)
            if issue is not None:
                log.warning(
                    "Explicacion del LLM descartada (%s): %s. Usando fallback deterministico.",
                    issue.code,
                    issue.detail,
                )
            else:
                metrics.registrar_explicacion_bedrock()
                return texto.strip()
        except Exception as exc:  # noqa: BLE001 -- el LLM nunca debe tumbar la respuesta
            log.warning("Fallo la explicacion via LLM (%s), usando fallback deterministico", exc)

        # Fuera del try del proveedor: si falla el respaldo, no volver a
        # ejecutarlo ni contabilizar dos veces la misma solicitud.
        metrics.registrar_explicacion_fallback()
        return self._fallback.explicar(contexto)
