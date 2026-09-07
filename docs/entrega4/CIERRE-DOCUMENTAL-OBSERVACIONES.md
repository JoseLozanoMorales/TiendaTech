# Cierre documental de las observaciones

Estado vigente al 7 de septiembre de 2026. Este registro diferencia el cierre actual de los cortes históricos conservados en `CIERRE-PASO13.md`, `cierre/issues-corte.json` y `cierre/actualizacion-20260903/`. No certifica aprobación docente, firmas personales ni similitud institucional.

## Correcciones documentales cerradas

| Asunto | Evidencia vigente |
|---|---|
| Denominación TiendaTech | `docs/nombre/README.md` y tres capturas históricas. Se documentan coincidencias; no se afirma exclusividad. |
| Registro por entrega, C1 | `registro-cambios.tex` y el registro explícito E1 de `PFC4.tex`. |
| Trazabilidad, C5 | `trazabilidad-temas.tex` y `cierre/trazabilidad-temas.csv`: 27 localizaciones revalidadas contra el corte de cierre `d7a00ccf6d8ac5def08ff52f7d2880f869667b0b`. |
| Preguntas de investigación | Cinco preguntas y respuestas actualizadas con la campaña desplegada; admiten resultados negativos e insuficiencia de evidencia. |
| Amenazas | Cuatro categorías con mitigaciones y separación entre piloto SQLite y campaña desplegada. |
| Bibliografía, C9 | Veinte fuentes académicas y tres normativas o éticas; cinco fuentes específicas de 2PC/Saga con metadatos en `cierre/bibliografia-2pc-saga.json`. |
| Duplicados y deuda, C8 | Los issues 40, 46 y 48 fueron respondidos y cerrados como duplicados de 41, 47 y 49. `tab:deuda` conserva únicamente deuda técnica vigente. |
| Residuo `commits.txt` | Eliminado y publicado en Git. |
| Observabilidad | Panel bajo carga, trazas OpenTelemetry y propagación por TCP documentados en `docs/evidencias/paso10-*`. |
| Calidad | Carga, disponibilidad de una hora, cobertura, complejidad y seguridad consolidadas en `docs/experimentos/resultados/iso25010.csv`. |
| Campaña 2PC/Saga | Campaña correctiva con 120 corridas, preflight, pilotos, rampa y cinco minutos de medición en `experiments/paso8/resultados-reales/correctiva-20260905-final-v2/`. Se confirmaron 30 275 checkouts; el oráculo retrospectivo detectó 13 210 inconsistencias de inventario entre 43 168 órdenes persistidas, y ninguna comparación conserva significación tras Bonferroni. |
| Contratos y E2E | Suites Pact y Playwright versionadas en `tests/contract/` y `tests/e2e-web/`, incluidas en CI. |
| Arranque, P3 | Evidencia de volúmenes limpios y estado del fix publicado en `docs/evidencias/arranque-limpio-paso15.md`. |

## Resultados que no deben mezclarse

- Las 120 corridas de `experiments/paso8/resultados/` pertenecen al banco SQLite histórico y no sustentan una garantía distribuida.
- El piloto de `cierre/actualizacion-20260903/` contiene 240 operaciones y detecta una discordancia de stock en E-Saga local.
- La campaña correctiva registra 208 003 solicitudes, 49 786 intentos de checkout, 19 511 fallos y 30 275 confirmaciones. Acredita flujo basal y degradación con carga; el oráculo retrospectivo acredita parcialmente invariantes persistentes y descubre inconsistencias de inventario, sin reconstruir cobro ni compensación cancelada.
- La carga ISO estable registra 2112 solicitudes, cero fallos y p95 de 610 ms. Es una corrida distinta con cincuenta usuarios y un límite operativo ajustado.
- La disponibilidad registra 3588/3588 sondeos exitosos durante una ventana de una hora. No se extrapola a producción.

## Pendientes reales del cierre documental

1. Obtener la lectura y aprobación de los autores restantes para sus conclusiones individuales y declaraciones de IA; Andy actualizó ambas entradas en los commits `8b0093d` y `161bf2c`.
2. Ejecutar la comprobación institucional de similitud; no declarar un porcentaje antes de recibirla.
3. Verificar acceso al repositorio, comprobar el PDF exacto y cargarlo en el SGA conservando el comprobante.

Los enlaces permanentes de las respuestas a duplicados están en `cierre/respuestas-issues-duplicados.md`. Los controles que dependen de integrantes o de sistemas externos están en `cierre/lista-cierre-externo.md`. La revisión de deuda, la trazabilidad vigente y la publicación de imágenes ya disponen de evidencia; el PDF debe regenerarse una vez después de integrar las últimas revisiones personales.
Cada afirmación final debe corresponder a un archivo versionado y a la misma unidad experimental. Implementación, ejecución y conclusión se registran por separado. El documentalista puede cerrar la integración y la reproducción del PDF; las firmas, la similitud institucional y cualquier repetición técnica adicional requieren la intervención indicada.

La comprobación final queda registrada en `cierre/verificacion-final-20260904.json`: 54 páginas, 23 referencias, 27 rangos históricos válidos, 120 filas de campaña y cero errores LaTeX, referencias indefinidas o desbordamientos. El PDF tiene SHA-256 `1A3612183EE1549E8D64D25A91E17EE2A25EA11693516A099A8D9D6B6ECD5A7A`.
