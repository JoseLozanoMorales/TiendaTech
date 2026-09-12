"""Regresiones de coincidencias parciales y doble ejecución del respaldo."""
import unittest
from unittest.mock import Mock, patch

from app.explicacion.client import ContextoExplicacion
from app.explicacion.service import ExplicacionService
from app.explicacion.validation import build_explanation_validation_chain


class ExplicacionRegresionesTest(unittest.TestCase):
    def contexto(self, nombre):
        return ContextoExplicacion(8.0, "bajo", "GPU", [], {"componente": nombre})

    def test_no_confunde_500w_con_1500w(self):
        issue = build_explanation_validation_chain().validate(
            "La fuente es de 500 W", self.contexto("Fuente de 1500 W")
        )
        self.assertIsNotNone(issue)
        self.assertEqual("unverified_hardware", issue.code)

    def test_no_confunde_modelo_base_con_variante_ti(self):
        issue = build_explanation_validation_chain().validate(
            "La RTX 4060 es adecuada", self.contexto("NVIDIA RTX 4060 Ti")
        )
        self.assertIsNotNone(issue)

    def test_acepta_mismo_modelo_con_espacios_y_mayusculas_distintos(self):
        issue = build_explanation_validation_chain().validate(
            "La rtx4060ti es adecuada", self.contexto("NVIDIA RTX 4060 Ti")
        )
        self.assertIsNone(issue)

    @patch("app.explicacion.service.metrics.registrar_explicacion_fallback")
    def test_respaldo_fallido_no_se_ejecuta_dos_veces(self, registrar_fallback):
        proveedor = Mock()
        proveedor.explicar.return_value = " "
        respaldo = Mock()
        respaldo.explicar.side_effect = RuntimeError("fallo del respaldo")
        servicio = ExplicacionService(proveedor, respaldo)

        with self.assertRaisesRegex(RuntimeError, "fallo del respaldo"):
            servicio.generar(self.contexto("RTX 4060"))

        respaldo.explicar.assert_called_once()
        registrar_fallback.assert_called_once_with()


if __name__ == "__main__":
    unittest.main(verbosity=2)
