import argparse
import json
import tempfile
import unittest
from pathlib import Path

try:
    from .run_real_experiment import (
        condiciones_en_orden,
        huella_banco_estable,
        validar_flujo_saludable,
        validar_readiness_previa,
    )
except ImportError:  # permite ejecutar tambien: python test_real_experiment.py
    from run_real_experiment import (
        condiciones_en_orden,
        huella_banco_estable,
        validar_flujo_saludable,
        validar_readiness_previa,
    )


class ValidacionFlujoBasalTest(unittest.TestCase):
    def test_acepta_checkout_confirmado_sin_errores(self):
        validar_flujo_saludable(
            {"checkout_confirmadas": 3, "requests_fail": 0, "codigos_http_json": '{"200": 9}'},
            "piloto",
        )

    def test_rechaza_piloto_sin_checkout_confirmado(self):
        with self.assertRaisesRegex(RuntimeError, "checkouts_confirmados=0"):
            validar_flujo_saludable(
                {"checkout_confirmadas": 0, "requests_fail": 0, "codigos_http_json": '{"200": 2}'},
                "piloto",
            )

    def test_orden_de_matriz_no_incluye_la_rampa(self):
        condiciones = list(condiciones_en_orden([50, 100], 1, ["2pc"], ["none"]))
        self.assertEqual(
            [("none", "2pc", 50, 1), ("none", "2pc", 100, 1)],
            condiciones,
        )

    def test_rechaza_piloto_con_error_de_transporte_o_http(self):
        with self.assertRaisesRegex(RuntimeError, "requests_fail=1"):
            validar_flujo_saludable(
                {"checkout_confirmadas": 1, "requests_fail": 1, "codigos_http_json": '{"0": 1}'},
                "piloto",
            )


class ReadinessPreviaTest(unittest.TestCase):
    def _caso(self, case_id: int, producto_id: int) -> dict:
        return {
            "caseId": case_id,
            "usuario": f"usuario{case_id}",
            "contrasena": f"secreto{case_id}",
            "usuarioId": case_id + 100,
            "token": f"jwt-{case_id}",
            "direccionId": case_id + 200,
            "metodopagoId": case_id + 300,
            "productoId": producto_id,
        }

    def test_huella_ignora_secretos_pero_detecta_cambio_de_producto(self):
        with tempfile.TemporaryDirectory() as tmp:
            banco = Path(tmp) / "banco.json"
            banco.write_text(json.dumps([self._caso(1, 10)]), encoding="utf-8")
            original, _ = huella_banco_estable(banco)

            cambiado = self._caso(1, 10)
            cambiado["contrasena"] = "otra"
            cambiado["token"] = "jwt-renovado"
            banco.write_text(json.dumps([cambiado]), encoding="utf-8")
            solo_secretos, _ = huella_banco_estable(banco)
            self.assertEqual(original, solo_secretos)

            cambiado["productoId"] = 11
            banco.write_text(json.dumps([cambiado]), encoding="utf-8")
            otro_producto, _ = huella_banco_estable(banco)
            self.assertNotEqual(original, otro_producto)

    def test_valida_evidencia_completa_y_rechaza_banco_cambiado(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            banco = raiz / "banco.json"
            banco.write_text(json.dumps([self._caso(1, 10)]), encoding="utf-8")
            huella, cantidad = huella_banco_estable(banco)
            (raiz / "piloto-basal").mkdir()
            (raiz / "rampa-readiness").mkdir()

            filas = []
            for coord in ("2pc", "saga"):
                piloto = {
                    "fallo": "none", "coord": coord, "concurrencia": 1,
                    "warmup_seconds": 10, "measure_seconds": 120,
                    "checkout_confirmadas": 1, "requests_fail": 0,
                }
                (raiz / "piloto-basal" / f"piloto-{coord}.json").write_text(
                    json.dumps(piloto), encoding="utf-8"
                )
                for concurrencia in (1, 5, 10, 25, 50):
                    filas.append({
                        "fallo": "none", "coord": coord, "concurrencia": concurrencia,
                        "warmup_seconds": 10, "measure_seconds": 60,
                        "checkout_confirmadas": 1, "requests_fail": 0,
                    })

            resumen = {
                "estado": "APROBADA",
                "request_bank_fingerprint": huella,
                "request_bank_cases": cantidad,
                "corridas": filas,
            }
            (raiz / "rampa-readiness" / "rampa-readiness.json").write_text(
                json.dumps(resumen), encoding="utf-8"
            )
            args = argparse.Namespace(output=raiz, request_bank=banco)
            validar_readiness_previa(args)

            banco.write_text(json.dumps([self._caso(1, 11)]), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "banco actual no es el mismo"):
                validar_readiness_previa(args)


if __name__ == "__main__":
    unittest.main()
