# Verificación de publicación de imágenes (E4)

Se consultó el run publicado `34712657152`, con resultado success para el commit
`75aeac6d579e04f531da85e4c2d2363d549422a2`. Se inspeccionaron en GHCR los ocho
índices asociados a ese commit: todos contienen imágenes linux/amd64 y linux/arm64.
Los digests de sus manifiestos y la procedencia están en
`publicacion-ghcr-20260912.json`. Estos son resultados existentes, no una ejecución
del workflow modificado en esta revisión.

El workflow preparado conserva después de publicar un índice consultado por digest,
comprueba ambas arquitecturas y genera un artefacto por servicio con commit completo,
referencia inmutable por digest y URL del run. La comprobación local del código
aceptó un índice real y rechazó otro al retirar arm64. La verificación remota del
nuevo paso requiere publicar los cambios y ejecutar el flujo.

La evaluación marca E4 como «Modificar» pero su descripción no señala un defecto
restante concreto. Este refuerzo de trazabilidad no se presenta como corrección
aceptada por el evaluador. Sigue pendiente aclarar qué exige para el factor 1,0.
No se enviaron mensajes ni se alteró el despliegue en EC2.


La validación remota del nuevo control permanece pendiente de publicación y ejecución.
