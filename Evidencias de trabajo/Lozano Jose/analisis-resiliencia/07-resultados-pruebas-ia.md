# 07 / Ejecución de pruebas de resiliencia de IA

Fecha UTC: 2026-09-11T21:27:14+00:00
Python: 3.12.14
Pydantic: 2.13.5; prometheus-client: 0.26.0
Código de salida: **0**.
Archivo nuevo: `services/armado-ia/tests/test_explicacion_resiliencia.py`
SHA-256 del archivo nuevo: `161fc89802ff429878a943270558d95206ef223067e42a1b6d913f15aec38b45`

## Alcance

Seis pruebas nuevas y cinco pruebas existentes ejecutadas con unittest.
El servicio de explicación es real; el proveedor remoto se sustituye por un doble controlado.
Se comprueban también el respaldo determinista real y la cadena de validación real en varios casos.
No se contactó Bedrock ni se levantaron los microservicios. Las métricas se interceptan en
los casos que verifican sus llamadas; no se comprueba la exportación a Prometheus.

## Comando reproducible

Desde `services/armado-ia`, con las dependencias del servicio instaladas:

```text
python -m unittest tests.test_explicacion_resiliencia tests.test_explicacion_validation -v
```

## Salida real

```text
test_fallo_del_respaldo_sin_proveedor_se_propaga (tests.test_explicacion_resiliencia.ExplicacionResilienciaTest.test_fallo_del_respaldo_sin_proveedor_se_propaga) ... ok
test_fallo_del_validador_tambien_activa_respaldo (tests.test_explicacion_resiliencia.ExplicacionResilienciaTest.test_fallo_del_validador_tambien_activa_respaldo) ... Fallo la explicacion via LLM (validador no disponible), usando fallback deterministico
ok
test_proveedor_recuperado_se_utiliza_en_la_siguiente_solicitud (tests.test_explicacion_resiliencia.ExplicacionResilienciaTest.test_proveedor_recuperado_se_utiliza_en_la_siguiente_solicitud) ... Fallo la explicacion via LLM (desconectado), usando fallback deterministico
ok
test_respuesta_vacia_activa_respaldo_real (tests.test_explicacion_resiliencia.ExplicacionResilienciaTest.test_respuesta_vacia_activa_respaldo_real) ... Explicacion del LLM descartada (empty_response): Respuesta vacia del LLM. Usando fallback deterministico.
ok
test_sin_proveedor_conserva_datos_en_respaldo_real (tests.test_explicacion_resiliencia.ExplicacionResilienciaTest.test_sin_proveedor_conserva_datos_en_respaldo_real) ... ok
test_timeout_devuelve_respaldo_y_registra_una_sola_estrategia (tests.test_explicacion_resiliencia.ExplicacionResilienciaTest.test_timeout_devuelve_respaldo_y_registra_una_sola_estrategia) ... Fallo la explicacion via LLM (proveedor agotó el tiempo), usando fallback deterministico
ok
test_returns_valid_llm_response (tests.test_explicacion_validation.ExplanationServiceValidationTest.test_returns_valid_llm_response) ... ok
test_uses_fallback_when_chain_rejects_response (tests.test_explicacion_validation.ExplanationServiceValidationTest.test_uses_fallback_when_chain_rejects_response) ... Explicacion del LLM descartada (unverified_hardware): RTX 4090. Usando fallback deterministico.
ok
test_accepts_hardware_present_in_input_context (tests.test_explicacion_validation.ExplanationValidationChainTest.test_accepts_hardware_present_in_input_context) ... ok
test_rejects_empty_response_in_first_handler (tests.test_explicacion_validation.ExplanationValidationChainTest.test_rejects_empty_response_in_first_handler) ... ok
test_rejects_unverified_hardware_in_second_handler (tests.test_explicacion_validation.ExplanationValidationChainTest.test_rejects_unverified_hardware_in_second_handler) ... ok

----------------------------------------------------------------------
Ran 11 tests in 0.003s

OK
```
