# Evidencia final — Punto 44 (paquete móvil firmado y publicado en release)

- Etiqueta final: `v4.0.0`
- Commit de `main`: `92e66686f8bdbb79d68487ad40008b014a432aba`
- URL de la ejecución: https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34911055622
- URL de la release definitiva: https://github.com/JoseLozanoMorales/TiendaTech/releases/tag/v4.0.0
- APK SHA-256: `b1e622a201e40cc662a1a768e3832ba53e5274b6aacd2d10f5150f4eccb1d304`
- Gradle canónico SHA-256 (`Apps/mobile/app/build.gradle.kts` tal como está versionado en ese commit): `29ec862d1ca3873a1dc9652ae98fb5ed4a8885d8d65f498a2636ff54c3653233`
- Certificado SHA-256: `6ad168c152fd8090144c25c75d630116cc912fbe60b25150a81d88f354bc8bbd`
- Firma APK v2 verificada: sí (`Verified using v2 scheme (APK Signature Scheme v2): true`)
- La release aparece marcada `Latest`, no `Pre-release`

## Verificación independiente realizada

Además de la evidencia que genera el propio flujo de CI (`apksigner.txt`,
`provenance.txt`, `gradle-build-script.sha256`, adjuntos a la release), se
verificó de forma independiente, descargando los archivos publicados:

1. El SHA-256 del APK descargado coincide exactamente con
   `tiendatech-release.apk.sha256` publicado.
2. El certificado extraído directamente del APK descargado (con `openssl`,
   sobre `META-INF/CERT.RSA`) tiene huella SHA-256
   `6A:D1:68:C1:52:FD:80:90:14:4C:25:C7:5D:63:01:16:CC:91:2F:BE:60:B2:51:50:A8:1D:88:F3:54:BC:8B:BD`
   y titular `CN=Jose Alejandro Lozano Morales` — coincide con la huella
   esperada y con el certificado público versionado en
   `release/jose-lozano-certificado-publico.pem`.
3. `gradle-build-script.sha256` coincide con el hash real de
   `Apps/mobile/app/build.gradle.kts` tal como está guardado en el commit
   `92e66686f8bdbb79d68487ad40008b014a432aba` (`git show <commit>:Apps/mobile/app/build.gradle.kts | sha256sum`),
   resolviendo el defecto de reproducibilidad que cerraba el punto 44.
4. `provenance.txt` identifica el mismo commit, el mismo tag (`v4.0.0`) y la
   misma ejecución (`34911055622`) que el resto de la evidencia.

## Declaración para el manuscrito

> El paquete móvil correspondiente a la etiqueta `v4.0.0` fue compilado y
> firmado automáticamente desde el commit `92e66686f8bdbb79d68487ad40008b014a432aba`
> de la rama principal, mediante el flujo `mobile-release-final.yml`. La misma
> ejecución (run `34911055622`) publicó como versión definitiva (no
> preliminar) el APK con SHA-256 `b1e622a201e40cc662a1a768e3832ba53e5274b6aacd2d10f5150f4eccb1d304`,
> junto con la verificación de firma, la suma y la procedencia. No se
> reconstruyó ni volvió a firmar el binario después de generar su resumen.
