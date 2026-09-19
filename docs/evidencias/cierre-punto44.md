# Cierre del punto #44 — paquete móvil firmado y publicado

## Resultado

El paquete móvil canónico de `v4.0.0` está firmado, publicado y vinculado a un
checksum verificable. El mismo binario distribuido fue instalado e iniciado en
dos dispositivos físicos el 18 de septiembre de 2026. La evidencia histórica
del APK local anterior se conserva, pero ya no se presenta como validación del
binario publicado.

## Identidad del paquete entregado

| Propiedad | Valor verificado |
|---|---|
| Release | [`v4.0.0`](https://github.com/JoseLozanoMorales/TiendaTech/releases/tag/v4.0.0) |
| Estado | definitiva, `prerelease=false`, marcada `Latest` |
| Commit etiquetado | `92e66686f8bdbb79d68487ad40008b014a432aba` |
| Ejecución de publicación | [34911055622](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34911055622) |
| Activo | `tiendatech-release.apk` |
| Tamaño | 35.849.007 bytes |
| SHA-256 | `b1e622a201e40cc662a1a768e3832ba53e5274b6aacd2d10f5150f4eccb1d304` |
| Paquete Android | `com.tiendatech.mobile` |
| Certificado SHA-256 | `6ad168c152fd8090144c25c75d630116cc912fbe60b25150a81d88f354bc8bbd` |
| Titular | `CN=Jose Alejandro Lozano Morales` |

La API pública de GitHub informa el mismo tamaño y digest para el activo. El
manifiesto `release/release-assets-v4.0.0.json` fija esos valores y
`scripts/verify_release_assets.py` los contrasta en CI sin reintroducir el APK
al árbol. La suma publicada se descarga junto al binario para `sha256sum -c`.

## Procedencia y firma

La ejecución `34911055622` generó a las 23:59:52Z el grupo canónico:

- `tiendatech-release.apk`;
- `tiendatech-release.apk.sha256`;
- `apksigner.txt`;
- `gradle-build-script.sha256`;
- `provenance.txt`.

El flujo terminó correctamente y publicó la release a las 23:59:54Z. El resumen
canónico del guion `Apps/mobile/app/build.gradle.kts` en el commit etiquetado es
`29ec862d1ca3873a1dc9652ae98fb5ed4a8885d8d65f498a2636ff54c3653233`.
La firma v2 y el certificado coinciden con la identidad fijada en el workflow.

La release contiene además activos añadidos posteriormente de forma manual
(`tiendatech-debug.apk`, video, agente OpenTelemetry, bases experimentales y
`PFC4.pdf`). Esto significa que la release completa no procede exclusivamente
de la ejecución móvil; no altera la identidad ni la procedencia del grupo
canónico anterior.

## Corrección del registro histórico

`docs/evidencias/firma-release-jose/verificacion.json` corresponde al APK local
histórico de SHA-256 `9da16724...` y 35.849.002 bytes. Se restituyó el resumen de
Gradle registrado originalmente, `3f0da127...`, y se declaró no reproducible
desde un blob versionado conocido. La explicación posterior que lo atribuía a
CRLF fue retirada: el blob LF produce `29ec862d...` y la variante CRLF comprobada
produce `d3b6d8ed...`.

La instalación histórica en dos dispositivos continúa documentada únicamente
para `9da16724...`; no se reutiliza como evidencia del APK publicado.

## Validación correctiva del APK publicado

El guion `scripts/verify-published-apk-device.ps1` falla antes de instalar si el
archivo no tiene exactamente 35.849.007 bytes y SHA-256 `b1e622a2...`. No
desinstala aplicaciones ni borra datos. Para cada dispositivo:

1. verificó tamaño y SHA-256;
2. ejecutó `adb install -r` y obtuvo `Success`;
3. confirmó que `com.tiendatech.mobile` estaba instalado;
4. inició la actividad principal;
5. guardó modelo, versión Android, salida y serial anonimizado mediante SHA-256.

| Equipo | Android | Instalación | Inicio | Evidencia |
|---|---|---|---|---|
| Xiaomi 23117RA68G | 15, SDK 35 | `Success` | confirmado | `device-7873e52cc9db.json` y dos capturas |
| INFINIX Infinix X6837 | 13, SDK 33 | `Success` | confirmado | `device-99c94a04a489.json` y tres capturas |

Los archivos y sus hashes están inventariados en
`docs/evidencias/firma-release-jose/dispositivos-apk-publicado/`. Las capturas
demuestran apertura y navegación entre pantallas públicas; no se afirma que se
hayan enviado formularios, autenticado usuarios o completado compras.

## Controles antes de integración

- Los cinco JSON de `docs/evidencias/firma-release-jose/` se analizaron
  correctamente.
- Los SHA-256 de las cinco capturas coinciden con
  `verificacion-resumen.json`.
- El guion PowerShell no tiene errores de análisis sintáctico.
- `scripts/validate_evidence_refs.py`: 72 referencias activas válidas y cero
  fallos.
- `git diff --check`: sin errores.

## Condición de cierre

El punto quedó técnicamente corregido al integrarse mediante pull request con
checks verdes: el checksum publicado corresponde al mismo APK distribuido, el
registro histórico dejó de atribuir un
valor no reproducible al guion canónico y la validación física cubre ahora el
binario distribuido exacto en dos dispositivos distintos.
