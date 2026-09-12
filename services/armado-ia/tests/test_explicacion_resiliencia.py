"""Regresiones de recuperación del proveedor; no requieren Bedrock ni red."""
import unittest
from unittest.mock import Mock, patch

from app.explicacion.client import ContextoExplicacion
from app.explicacion.fallback_client import DeterministicExplicacionClient
from app.explicacion.service import ExplicacionService


class ExplicacionResilienciaTest(unittest.TestCase):
    def setUp(self):
        self.contexto = ContextoExplicacion(
            porcentaje_bottleneck=8.0,
            nivel="bajo",
            componente_limitante="GPU",
            advertencias=["Revisar ventilacion."],
            componentes_nombres={"gpu": "NVIDIA RTX 4060"},
        )
        self.proveedor = Mock()
        self.respaldo = Mock()
        self.respaldo.explicar.return_value = "Explicacion de respaldo"

    # CAPTURA A: fallo del proveedor y contadores mutuamente excluyentes.
    @patch("app.explicacion.service.metrics.registrar_explicacion_bedrock")
    @patch("app.explicacion.service.metrics.registrar_explicacion_fallback")
    def test_timeout_devuelve_respaldo_y_registra_una_sola_estrategia(self, fallback, bedrock):
        self.proveedor.explicar.side_effect = TimeoutError("proveedor agotó el tiempo")
        servicio = ExplicacionService(self.proveedor, self.respaldo)

        self.assertEqual("Explicacion de respaldo", servicio.generar(self.contexto))
        self.respaldo.explicar.assert_called_once_with(self.contexto)
        fallback.assert_called_once_with()
        bedrock.assert_not_called()

    def test_sin_proveedor_conserva_datos_en_respaldo_real(self):
        texto = ExplicacionService(None).generar(self.contexto)

        self.assertIn("8.0%", texto)
        self.assertIn("GPU", texto)
        self.assertIn("Revisar ventilacion.", texto)
        self.assertNotIn("RTX 4090", texto)

    # CAPTURA B: recuperación entre solicitudes; no queda pegado al respaldo.
    @patch("app.explicacion.service.metrics.registrar_explicacion_bedrock")
    @patch("app.explicacion.service.metrics.registrar_explicacion_fallback")
    def test_proveedor_recuperado_se_utiliza_en_la_siguiente_solicitud(self, fallback, bedrock):
        self.proveedor.explicar.side_effect = [
            ConnectionError("desconectado"), "  La RTX 4060 tiene un cuello bajo.  "
        ]
        servicio = ExplicacionService(self.proveedor, self.respaldo)

        self.assertEqual("Explicacion de respaldo", servicio.generar(self.contexto))
        self.assertEqual("La RTX 4060 tiene un cuello bajo.", servicio.generar(self.contexto))
        self.assertEqual(2, self.proveedor.explicar.call_count)
        self.respaldo.explicar.assert_called_once_with(self.contexto)
        fallback.assert_called_once_with()
        bedrock.assert_called_once_with()

    def test_respuesta_vacia_activa_respaldo_real(self):
        self.proveedor.explicar.return_value = " \n "
        esperado = DeterministicExplicacionClient().explicar(self.contexto)

        self.assertEqual(esperado, ExplicacionService(self.proveedor).generar(self.contexto))

    def test_fallo_del_validador_tambien_activa_respaldo(self):
        self.proveedor.explicar.return_value = "La RTX 4060 tiene un cuello bajo."
        validador = Mock()
        validador.validate.side_effect = RuntimeError("validador no disponible")

        texto = ExplicacionService(self.proveedor, self.respaldo, validador).generar(self.contexto)

        self.assertEqual("Explicacion de respaldo", texto)
        validador.validate.assert_called_once_with(self.proveedor.explicar.return_value, self.contexto)

    # Límite explícito: el respaldo tampoco es infalible.
    def test_fallo_del_respaldo_sin_proveedor_se_propaga(self):
        self.respaldo.explicar.side_effect = RuntimeError("respaldo no disponible")

        with self.assertRaisesRegex(RuntimeError, "respaldo no disponible"):
            ExplicacionService(None, self.respaldo).generar(self.contexto)


if __name__ == "__main__":
    unittest.main(verbosity=2)
