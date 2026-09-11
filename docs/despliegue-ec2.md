# Despliegue de TiendaTech en EC2

## Estado

`docker-compose.prod.yml` ejecuta imágenes previamente publicadas en GitHub Container Registry (GHCR). No compila código en EC2 y no constituye todavía despliegue continuo: la publicación de imágenes y la actualización automática del servidor requieren un workflow separado.

## Convención de imágenes

Cada imagen se publica en `ghcr.io/<owner>/<repo>` (minúsculas) con una etiqueta por servicio atada al hash corto del commit que la generó — no existe una etiqueta móvil como `latest`, así toda imagen puede rastrearse a un commit exacto:

```text
productos-38bf21e
inventario-38bf21e
ventas-38bf21e
usuarios-38bf21e
ordenes-proveedores-38bf21e
armado-ia-38bf21e
pedidos-38bf21e
frontend-38bf21e
```

`IMAGE_REPOSITORY` e `IMAGE_TAG` son obligatorias en `.env` (sin valor por defecto) y determinan exactamente qué se despliega, por ejemplo `IMAGE_TAG=38bf21e`.

### Visibilidad de los paquetes en GHCR

`GITHUB_TOKEN` publica cada paquete como privado por defecto, sin importar que el
repositorio sea público. Como el proyecto exige acceso anónimo reproducible por
terceros, después de la primera publicación de cada imagen hay que entrar a la
pestaña **Packages** del repositorio en GitHub y cambiar su visibilidad a
**público** — una vez por imagen, no requiere cambios de código ni de workflow.
Mientras algún paquete siga privado, `docker compose pull` fallará por falta de
autenticación al descargarlo.

## Archivos privados del servidor

En `/opt/tiendatech` deben existir:

- `docker-compose.prod.yml`, obtenido del repositorio.
- `.env`, creado directamente en EC2 con permisos `600`.
- `crdb-certs/`, directorio privado con `ca.crt`, el certificado del cliente y su clave PK8 cuando `CRDB_DATASOURCE_URL` usa `verify-ca`.

El `.env` debe contener la conexión de CockroachDB, credenciales de correo y el mismo `AUTH_JWT_SECRET` para Usuarios, Gateway y Armado IA. No debe copiarse al repositorio ni incluirse dentro de ninguna imagen.

La ruta anfitriona de certificados se configura con `CRDB_CERTS_DIR=./crdb-certs`. Compose la monta como `/app/crdb-certs` en modo de solo lectura, que debe coincidir con las rutas `sslrootcert`, `sslcert` y `sslkey` de la URL JDBC. Las claves deben conservar permisos restrictivos y nunca publicarse en ningún registro ni repositorio.

## Inicio manual

Desde `/opt/tiendatech`:

```text
docker compose --env-file .env -f docker-compose.prod.yml config --quiet
docker compose --env-file .env -f docker-compose.prod.yml pull
docker compose --env-file .env -f docker-compose.prod.yml up -d
docker compose --env-file .env -f docker-compose.prod.yml ps
```

El único puerto publicado por Compose es el Gateway, `8180` por defecto. Los puertos de los microservicios permanecen dentro de la red Docker.

## Verificación

```text
curl --fail http://localhost:8180/actuator/health
curl --fail "http://localhost:8180/api/productos?page=0&size=1"
```

Para uso real desde Android se necesita un dominio con HTTPS delante del Gateway. No debe configurarse el APK release con credenciales JDBC ni con direcciones internas de Docker.

## Actualización y reversión

Para desplegar una versión publicada:

```text
IMAGE_TAG=<commit> docker compose --env-file .env -f docker-compose.prod.yml pull
IMAGE_TAG=<commit> docker compose --env-file .env -f docker-compose.prod.yml up -d
```

La reversión consiste en repetir ambos comandos con la etiqueta del commit anterior. No existe una etiqueta `latest`: `IMAGE_TAG` es obligatoria, así que cada despliegue queda atado a un commit concreto.
