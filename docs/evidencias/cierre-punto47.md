# Cierre del punto #47 — publicación etiquetada de la entrega

## Defecto que se corrige

La release histórica `v4.0.0` apunta al commit
`92e66686f8bdbb79d68487ad40008b014a432aba` y recibió después activos manuales
procedentes de estados distintos. Se conserva como evidencia histórica, pero no
representa el estado final actual de `main` como un conjunto autoconsistente.

## Primera publicación autoconsistente

La versión `v4.1.0` fue la primera publicación autoconsistente. La etiqueta se creó después de fusionar la
preparación y comprobar los checks del tip final de `main`. El workflow exige:

1. etiqueta anotada, no liviana;
2. etiqueta situada exactamente sobre el tip vigente de `origin/main`;
3. versión de la etiqueta igual a `CITATION.cff`;
4. APK compilado y firmado desde el propio árbol etiquetado;
5. certificado firmante igual al certificado público versionado;
6. PDF tomado del mismo árbol etiquetado;
7. manifiesto, sumas y procedencia generados en la misma ejecución;
8. ausencia previa de una release con ese nombre, evitando mezclar activos.

## Contenido canónico

| Activo | Procedencia |
|---|---|
| `PFC4.pdf` | `docs/entrega4/PFC4.pdf` del tag |
| `tiendatech-release.apk` | compilado y firmado en CI desde `Apps/mobile` del tag |
| `tiendatech-release.apk.sha256` | generado en la ejecución |
| `jose-lozano-certificado-publico.pem` | archivo versionado en el tag |
| `CITATION.cff` | metadatos versionados en el tag |
| `apksigner.txt` | derivado del APK recién compilado |
| `gradle-build-script.sha256` | derivado del blob etiquetado |
| `release-manifest.json` | inventario de tamaño y SHA-256 |
| `SHA256SUMS.txt` | sumas verificadas antes de publicar |
| `provenance.txt` | tag, objeto anotado, commit y ejecución |

Los activos históricos pesados permanecen en `v4.0.0`; no se mezclan con la
entrega final.

## Procedimiento de publicación ejecutado

```powershell
git switch main
git pull --ff-only origin main
$finalCommit = git rev-parse HEAD
git tag -a v4.1.0 $finalCommit -m "TiendaTech v4.1.0 - entrega final"
git cat-file -t v4.1.0
git rev-list -n 1 v4.1.0
git push origin v4.1.0
```

`git cat-file -t` debe imprimir `tag`. El push activa el workflow, que vuelve a
comprobar el tipo y destino de la etiqueta antes de compilar o publicar.

## Criterio de cierre previsto

El punto debía cerrarse después de verificar que el PR estuviera fusionado, la
etiqueta anotada apuntara al tip correspondiente de `main`, la ejecución
terminara en verde, la release fuera definitiva y `Latest`, y las sumas
descargadas aprobaran. Todas estas condiciones se verifican a continuación.

## Primera ejecución y corrección del fetch

La primera ejecución disparada por `v4.1.0`,
[35416077873](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35416077873),
se detuvo en la comprobación previa, antes de compilar o publicar. El comando
`git fetch origin main --no-tags` actualizó `FETCH_HEAD`, pero no garantizó la
actualización de `refs/remotes/origin/main` en el checkout de una etiqueta.

La corrección usa el refspec explícito
`+refs/heads/main:refs/remotes/origin/main` y muestra el commit pelado del tag y
el commit remoto de `main` antes de compararlos. La ejecución fallida no creó
una release ni activos parciales.

La segunda ejecución,
[35417585893](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35417585893),
confirmó que el ref remoto de `main` ya se obtenía correctamente, pero el
checkout del evento seguía sin materializar necesariamente el objeto anotado en
`refs/tags/v4.1.0`. También se detuvo antes de compilar o publicar.

La corrección definitiva trae explícitamente los dos refs necesarios: la rama
`main` a `refs/remotes/origin/main` y la etiqueta del evento a
`refs/tags/$GITHUB_REF_NAME`. Cada condición emite ahora un error específico
para distinguir tag ausente, tag liviano, commit distinto o versión CFF
inconsistente.

## Publicación final verificada

| Propiedad | Valor comprobado |
|---|---|
| Release | [`v4.1.0`](https://github.com/JoseLozanoMorales/TiendaTech/releases/tag/v4.1.0) |
| Ejecución | [35418812586](https://github.com/JoseLozanoMorales/TiendaTech/actions/runs/35418812586), `success` |
| Commit etiquetado | `a16412d351aeb9076368aa491d5a9fea3834a4b5` |
| Objeto de tag anotado | `85c9681cdb5b9f0521be86fd74a706bc52956723` |
| Estado | definitiva, `draft=false`, `prerelease=false`, `Latest` |
| Publicación | 19 de septiembre de 2026, 03:36:05 UTC |
| Assets | 10, todos subidos por `github-actions[bot]` |
| APK | 35.849.007 bytes; SHA-256 `9ebc8d61c4d1f8926e92ec523dbc626e85d1143abf67991a3a05678001773364` |
| PDF | SHA-256 `5ea14009a9a6cc55632a3d6f6658fcb5817d91bd9bd7176c311817039a5600c5` |

La API pública confirma que `target_commitish`, el commit pelado del tag, el
commit del manifiesto y `source_commit` de `provenance.txt` son idénticos. El
informe `apksigner.txt` registra la huella esperada
`6ad168c152fd8090144c25c75d630116cc912fbe60b25150a81d88f354bc8bbd`.

Se descargaron independientemente los diez assets. Las nueve entradas de
`SHA256SUMS.txt` se recalcularon sobre sus bytes: 9 correctas y 0 fallidas. El
manifiesto contiene los ocho archivos existentes antes de generar el propio
manifiesto y el archivo integral de sumas. Esta verificación demuestra que el
flujo de publicación funciona y conserva la trazabilidad completa de `v4.1.0`.

## Entrega vigente enlazada desde el repositorio

La corrección documental posterior forma parte de
[`v4.1.1`](https://github.com/JoseLozanoMorales/TiendaTech/releases/tag/v4.1.1).
Esa versión sustituye a `v4.1.0` como entrega vigente para que el README, la
citación y la documentación de distribución pertenezcan al mismo commit que la
etiqueta anotada y la release. El workflow vuelve a construir todos los
artefactos desde el tip etiquetado de `main`; no reutiliza ni modifica los
assets de `v4.1.0`.
