"""Validadores encadenables para respuestas generadas por el LLM.

``ExplanationValidationHandler.validate`` define el algoritmo común y cada
subclase implementa únicamente su regla en ``_validate_current``. Además, un
handler puede delegar al siguiente mediante ``set_next``.
"""
from __future__ import annotations

import re
from abc import ABC, abstractmethod
from dataclasses import dataclass

from app.explicacion.client import ContextoExplicacion

_PATRONES_MODELO_HARDWARE = (
    re.compile(r"\bRTX\s?\d{3,4}(\s?Ti)?\b", re.IGNORECASE),
    re.compile(r"\bGTX\s?\d{3,4}(\s?Ti)?\b", re.IGNORECASE),
    re.compile(r"\bRX\s?\d{3,4}(\s?XT)?\b", re.IGNORECASE),
    re.compile(r"\bRyzen\s?\d\s?\d{3,4}[A-Z]*\b", re.IGNORECASE),
    re.compile(r"\bi[3579]-\d{4,5}[A-Z]*\b", re.IGNORECASE),
    re.compile(r"\b\d{3,4}\s?W\b"),
)


@dataclass(frozen=True)
class ValidationIssue:
    code: str
    detail: str


class ExplanationValidationHandler(ABC):
    """Handler base: Template Method del flujo y enlace de la cadena."""

    def __init__(self) -> None:
        self._next: ExplanationValidationHandler | None = None

    def set_next(self, handler: ExplanationValidationHandler) -> ExplanationValidationHandler:
        self._next = handler
        return handler

    def validate(self, text: str, context: ContextoExplicacion) -> ValidationIssue | None:
        """Template Method: regla actual; si pasa, delegación al siguiente."""
        issue = self._validate_current(text, context)
        if issue is not None:
            return issue
        if self._next is not None:
            return self._next.validate(text, context)
        return None

    @abstractmethod
    def _validate_current(
        self, text: str, context: ContextoExplicacion
    ) -> ValidationIssue | None:
        """Hook implementado por cada regla concreta."""


class NonEmptyExplanationHandler(ExplanationValidationHandler):
    def _validate_current(
        self, text: str, context: ContextoExplicacion
    ) -> ValidationIssue | None:
        del context
        if not text or not text.strip():
            return ValidationIssue("empty_response", "Respuesta vacia del LLM")
        return None


class VerifiedHardwareTokenHandler(ExplanationValidationHandler):
    def _validate_current(
        self, text: str, context: ContextoExplicacion
    ) -> ValidationIssue | None:
        # Comparar tokens completos evita aceptar 500 W dentro de 1500 W
        # o confundir un modelo base con su variante Ti.
        vocabulary = _build_allowed_vocabulary(context)
        allowed_tokens = {
            _normalize(match.group())
            for pattern in _PATRONES_MODELO_HARDWARE
            for match in pattern.finditer(vocabulary)
        }
        for pattern in _PATRONES_MODELO_HARDWARE:
            for match in pattern.finditer(text):
                if _normalize(match.group()) not in allowed_tokens:
                    return ValidationIssue("unverified_hardware", match.group())
        return None


def build_explanation_validation_chain() -> ExplanationValidationHandler:
    first = NonEmptyExplanationHandler()
    first.set_next(VerifiedHardwareTokenHandler())
    return first


def _normalize(text: str) -> str:
    return re.sub(r"\s+", "", text).upper()


def _build_allowed_vocabulary(context: ContextoExplicacion) -> str:
    parts: list[str] = list(context.componentes_nombres.values())
    parts.extend(context.advertencias)
    recommendation = context.recomendacion
    if recommendation is not None:
        parts.extend(component.nombre for component in recommendation.componentes.values())
        parts.extend(recommendation.advertencias)
    return " ".join(parts)
