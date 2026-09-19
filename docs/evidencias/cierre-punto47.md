# Cierre del punto #47 — publicación etiquetada de la entrega

## Defecto que se corrige

La release histórica `v4.0.0` apunta al commit
`92e66686f8bdbb79d68487ad40008b014a432aba` y recibió después activos manuales
procedentes de estados distintos. Se conserva como evidencia histórica, pero no
representa el estado final actual de `main` como un conjunto autoconsistente.

## Entrega final preparada

La versión final será `v4.1.0`. La etiqueta se creará únicamente después de
fusionar esta preparación y comprobar todos los checks del tip final de `main`.
El workflow exige:

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

## Publicación posterior a la fusión

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

## Criterio de cierre

El punto se cerrará después de verificar que el PR esté fusionado, la etiqueta
anotada apunte al tip correspondiente de `main`, la ejecución termine en verde,
la release sea definitiva y `Latest`, y las sumas descargadas aprueben. Los
identificadores finales se añadirán después de la publicación.

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
