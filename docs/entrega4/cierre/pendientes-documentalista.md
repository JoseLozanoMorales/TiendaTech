# Pendientes del documentalista (José)

**Antecedente histórico del 10 de septiembre, no estado vigente.** La cita de seguridad se corrigió en `b8d3e23988bf32e739f260e21cb8b999f362e783`. Las menciones al archivo del 1 de septiembre que siguen describen el hallazgo original, no una evidencia activa. Para el alcance vigente de E2, véase [verificación del 12 de septiembre](verificacion-referencias-20260912.md).

Revisión del 10 de septiembre de 2026 sobre el árbol local, con HEAD `c59e9a2bc2e45a749a0c01f8fd257de1521447cb`. El cierre del informe y del README queda pendiente de las confirmaciones siguientes. Este registro no aprueba resultados técnicos ni sustituye evidencia de ejecución.

## 1. Evidencia de seguridad sin respaldo

Prioridad de evaluación: peso 1,6; umbral 8; estado comunicado «a modificar».

La fila Seguridad de `docs/experimentos/resultados/iso25010.csv` cita `docs/experimentos/resultados/iso25010/security-401-gatewayintegration-2026-09-01.csv`. El archivo no existe en el árbol revisado y la consulta del historial de todas las referencias Git disponibles localmente no devuelve incorporaciones de esa ruta. No se consultaron ramas remotas nuevas. La tabla `docs/experimentos/resultados/iso25010-booktabs.tex` también afirma «8/8 familias retornan 401» y «Cumple».

**Responsable de confirmación: Jeremy o Andy.** Confirmar si se ejecutó la prueba del gateway y aportar fecha, revisión del código, comando, entorno y salida original. La existencia de pruebas de código o de otras mediciones no demuestra esa ejecución.

- Si existe la evidencia original: incorporarla a Git, indicar su ruta y SHA-256 y comprobar que respalda las ocho familias y el resultado citado.
- Si se acuerda repetir la prueba: el responsable técnico debe ejecutarla y publicar evidencia con su fecha real; José actualizará las citas y cifras al nuevo resultado. No reconstruir un supuesto CSV del 1 de septiembre.
- Si se confirma que no existe respaldo recuperable y se acuerda retirar la afirmación: José actualizará conjuntamente la matriz, la tabla y las menciones derivadas en el informe, conservando el registro de la decisión.

**Estado: pendiente de decisión técnica.** La cifra y la cita se conservan durante esta revisión; no pueden considerarse verificadas ni listas para entrega. No se ha generado un archivo sustituto.

## 2. Confirmaciones para cerrar informe y README

Responsables: Arquitecto y Andy. Los hallazgos siguientes son un inventario local, no una aceptación del paquete final.

| Tema | Hallazgo verificable | Confirmación necesaria |
|---|---|---|
| Tableros | Existen `ops/observability/grafana/provisioning/` y `ops/observability/grafana-provisioning/`. `docker-compose.yml` monta la primera y `ops/observability/` como directorio de dashboards; existe `ops/observability/grafana-dashboard.json`. | Árbol definitivo, configuración activa, tableros que se entregan y tratamiento de la carpeta alternativa. |
| Paquete móvil | `Apps/mobile/README.md` describe compilación y salida debug. `Apps/mobile/releases/` contiene varios APK locales sin seguimiento Git. | APK exacto, variante, versión, URL del gateway, ubicación accesible de distribución, requisitos, pasos de instalación probados y SHA-256. No elegir un APK por su nombre o fecha. |
| Registro de imágenes | `.github/workflows/publish-images.yml` configura Docker Hub, ocho imágenes, arquitecturas amd64/arm64 y etiquetas por servicio con `latest` y SHA corto. | Registro y repositorio finales, etiquetas y digests publicados, ejecución de publicación que los respalda y procedimiento de descarga validado. La configuración del flujo no acredita por sí sola una publicación. |

## 3. Secuencia de cierre

1. Recibir las confirmaciones técnicas y conservar una referencia verificable a cada respuesta.
2. Resolver la cita de seguridad por el camino acordado con Jeremy o Andy.
3. Integrar en el informe y en los README las rutas, instalación móvil y registro confirmados.
4. Comprobar que cada evidencia citada existe, pertenece al paquete entregado y tiene el hash registrado cuando corresponda.
5. Compilar y revisar el PDF definitivo; registrar su nuevo SHA-256 y completar la lista de cierre externo. Los hashes y comprobaciones históricos no certifican un PDF posterior.

## Registro de respuestas

| Confirmación | Responsable | Decisión, fecha y enlace o archivo de respaldo |
|---|---|---|
| Prueba 401 del gateway | Jeremy o Andy | Pendiente |
| Árbol final de tableros | Arquitecto y Andy | Pendiente |
| Instalación del APK entregable | Arquitecto y Andy | Pendiente |
| Registro final de imágenes | Arquitecto y Andy | Pendiente |

No se han enviado mensajes al equipo desde esta revisión. El silencio no constituye confirmación.
