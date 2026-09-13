# Verificación remota de imágenes (E4)

Commit de fuentes: `e4ad304be7324a3e37c4b20d8d913a13a6cac551`.
El [run de publicación 34738998898](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34738998898)
terminó con `success` en sus ocho trabajos, incluido el nuevo paso de verificación
del índice publicado y la conservación de evidencia por imagen.

Se descargaron sus ocho artefactos `image-publication-*` mediante
`gh run download 34738998898 --repo JoseLozanoMorales/TiendaTech --pattern 'image-publication-*' --dir docs/evidencias/ghcr-e4-20260913`.
Sus registros e índices originales se conservan en [ghcr-e4-20260913](ghcr-e4-20260913/),
para que la evidencia no dependa únicamente de la retención temporal de Actions.

Se consultó directamente GHCR con un token de lectura anónimo. Para cada imagen
se obtuvo `/v2/joselozanomorales/tiendatech/manifests/<digest>` y se calculó
SHA-256 de los bytes de respuesta; el resultado coincide con el digest registrado.
El índice coincide con el descargado de Actions. La consulta por etiqueta
`<servicio>-e4ad304` devuelve el mismo digest. Los ocho registros señalan el commit
completo y la URL del run correctos.

| Servicio | Digest, etiqueta e índice coinciden | Arquitecturas presentes |
| --- | --- | --- |
| armado-ia | Sí | linux/amd64, linux/arm64 |
| frontend | Sí | linux/amd64, linux/arm64 |
| inventario | Sí | linux/amd64, linux/arm64 |
| ordenes-proveedores | Sí | linux/amd64, linux/arm64 |
| pedidos | Sí | linux/amd64, linux/arm64 |
| productos | Sí | linux/amd64, linux/arm64 |
| usuarios | Sí | linux/amd64, linux/arm64 |
| ventas | Sí | linux/amd64, linux/arm64 |

[El registro JSON](publicacion-ghcr-20260913.json) conserva la fecha UTC de consulta,
los resultados de los trabajos, las referencias completas por digest y las
comprobaciones por servicio. Las etiquetas se comprobaron en esa fecha; la referencia
inmutable es el digest, no la etiqueta.

La validación remota del refuerzo E4 queda completada. Esto acredita publicación,
trazabilidad y presencia de ambas arquitecturas en los índices; no constituye una
prueba de ejecución funcional de contenedores en ambas arquitecturas ni una
aceptación de la calificación por el evaluador. La observación original «Modificar»
sigue sin precisar un defecto adicional.

El [quality gate del mismo commit](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34738904904)
pasó. El [flujo independiente CI](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/34738904899)
falló en «Complejidad ciclomatica del backend (PMD)»; ese resultado no se declara
resuelto por esta comprobación de imágenes.

La [verificación anterior](publicacion-ghcr-20260912.md) se conserva como histórica.
