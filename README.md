# TiendaTech — Sistema distribuido de comercio electrónico

- [APK Android, SHA-256 e instrucciones de instalación (E6)](release/README.md).
- [Cobertura medida de carrito, checkout y órdenes (E1/E3)](docs/evidencias/cobertura/web/README.md).

**Asignatura:** Aplicaciones Distribuidas (ISR-701)
**Carrera:** Ingeniería de Software, séptimo semestre
**Institución:** Universidad Técnica Estatal de Quevedo (UTEQ)
**Docente:** Prof. PhD. Gleiston C. Guerrero-Ulloa
**Período académico:** 2026-2027
**Entrega vigente:** Entrega 4 (E4) — refactor en capas, calidad de software, aplicaciones cliente y persistencia distribuida
**Denominación anterior:** este repositorio se denominó `PFC-AppsDistribuidas` hasta la adopción de `TiendaTech` en la Entrega Final TA-PFC-E4; ambos nombres corresponden al mismo proyecto y equipo.
**Comprobación del nombre:** las tres capturas incorporadas el 26 de agosto de 2026 están en [el registro de búsqueda de TiendaTech](docs/nombre/README.md). Hay coincidencias en el buscador general y GitHub; el cero de SourceForge corresponde a una búsqueda con filtro Windows. La evidencia documenta la búsqueda, no acredita exclusividad del nombre.
**Rama de trabajo:** `main` (fusionada desde `feature/entrega-4` por PR con revisión cruzada)

## Equipo

| Integrante | Rol | Usuario Git |
|---|---|---|
| Jhinson Stalyn Aucatoma Celorio | Arquitecto | `JhinsonAucatoma` |
| Jeremy Ruperto Gaibor Rodríguez | Líder de Desarrollo | `JeremyGaibor` |
| Andy Paul Sánchez Pilaloa | Responsable de Calidad | `AndySanchez2004` |
| José Alejandro Lozano Morales | Responsable de Documentación | `JoseLozanoMorales` |

---

## 1. Estado de la Entrega 4

El manuscrito (`docs/entrega4/PFC4.tex`) documenta el estado real del proyecto sección por sección, declarando explícitamente lo que se cumple, lo parcial y lo no implementado. Este resumen sigue ese mismo criterio: no se reporta nada como completo si no lo está.

| Frente | Alcance | Estado | Evidencia |
|---|---|---|---|
| Arquitectura en capas | Refactor de los 6 microservicios Java a `domain`/`application`/`infrastructure`/`presentation`, con patrones GoF (Repository, Factory Method, Strategy, Observer, Decorator) | ✅ Completo | `docs/entrega4/PFC4.tex` §"Arquitectura del sistema", código bajo `services/*/src/main/java/com/tiendatech/` |
| Persistencia distribuida | Clúster CockroachDB de 3 nodos; cada microservicio es dueño de su esquema y no consulta esquemas ajenos | ✅ Completo | `docker-compose.yml`, `.env.example`, `docs/db/schema.sql` |
| Aplicación web | SPA con 12 rutas documentadas, panel de administración completo | ✅ Completo | `docs/entrega4/PFC4.tex` §"Aplicación web", capturas en `release/screenshots/` |
| Aplicación móvil | App Android con 2 capacidades de dispositivo (caché local Room/SQLite + funcionalidad adicional documentada), pruebas unitarias e instrumentadas | ✅ Completo | `docs/entrega4/PFC4.tex` §"Aplicación móvil" |
| Contratos Pact (consumidor-proveedor) | Verificación de contratos web↔backend y móvil↔backend | ✅ Implementado | `tests/contract/` y job `contract-tests` de `.github/workflows/ci.yml` |
| Pirámide de pruebas | Los 6 microservicios Java contienen pruebas; se incluyen integración con CockroachDB mediante Testcontainers, contratos Pact y recorridos E2E web | ✅ Implementada en el alcance documentado | `services/*/src/test/`, `tests/contract/` y `tests/e2e-web/` |
| Pruebas de carga | Escenario Locust versionado | ✅ Implementado | `tests/load/` |
| CI/CD | Quality gate con pruebas Java/Python, análisis estático Java/Python/TypeScript, integración, build, cobertura, contratos Pact, E2E web y calidad móvil; publicación multi-arquitectura de 8 imágenes condicionada al gate de `main` | ✅ Implementado con evidencia rojo/verde | `.github/workflows/` y `docs/evidencias/paso9-ci-rojo-verde.md` |
| Observabilidad | Métricas Prometheus, logs JSON, recolección Alloy, dashboard Grafana, trazas Jaeger y observación estable de una hora | ✅ Evidencia versionada con alcance documentado | `ops/observability/`, `docs/evidencias/paso10-observabilidad.md` y `docs/experimentos/resultados/iso25010/2026-09-04T08-44-12/` |
| Evaluación ISO/IEC 25010 | Evaluación documentada de cinco características, con cobertura, complejidad, 3588/3588 comprobaciones durante una hora y p95 de 610 ms en el entorno medido | ✅ Ejecutada con límites declarados | `docs/experimentos/resultados/iso25010/` y `docs/entrega4/PFC4.tex` §"Evaluación de calidad" |
| Manuscrito completo | Introducción, arquitectura, apps web/móvil, persistencia, calidad/CI-CD, observabilidad, ISO 25010, discusión y amenazas a la validez, ética, reproducibilidad, trazabilidad E1-E4, conclusiones | ✅ Completo | `docs/entrega4/PFC4.tex` |

> **Nota de honestidad académica:** las secciones de observabilidad, evaluación ISO/IEC 25010 y pruebas del manuscrito distinguen lo implementado de las mediciones y recorridos todavía pendientes. Ver `docs/entrega4/PFC4.tex` para el alcance y las amenazas a la validez.

### Servicios definidos en `docker-compose.yml`

La tabla describe la configuración versionada. Para afirmar que el despliegue está
operativo debe ejecutarse la comprobación de salud indicada en el arranque rápido.

| Servicio | Puerto | Persistencia | Comunicación saliente |
|---|---:|---|---|
| `tiendatech-gateway` (API Gateway) | 8180 (host) / 8080 (interno) | — (enrutador) | Enruta a los 7 microservicios |
| `productos-service` | 8081 | CockroachDB, esquema `productos` | `ventas-service` |
| `inventario-service` | 8082 | CockroachDB, esquema `inventario` | — |
| `pedidos-service` | 8083 | CockroachDB, esquema `pedidos` | `ventas-service`, `productos-service`, `usuarios-service` |
| `ordenes-proveedores-service` | 8084 | CockroachDB, esquema `ordenes_proveedores` | `inventario-service` (síncrono, Circuit Breaker) |
| `usuarios-service` | 8085 | CockroachDB, esquema `usuarios` | — |
| `ventas-service` | 8086 | CockroachDB, esquema `ventas` | `inventario-service` (asíncrono, patrón Outbox) |
| `armado-ia` (Python/FastAPI) | 8087 | — | `productos-service`, Amazon Bedrock (Nova Lite) |
| `tiendatech-crdb-1` / `tiendatech-crdb-2` / `tiendatech-crdb-3` | Nodo 1: SQL 26257 y consola 8088; nodos 2 y 3 solo en la red interna | CockroachDB local de 3 nodos, `num_replicas = 3`, modo desarrollo sin TLS | — |

---

## 2. Requisitos previos

| Herramienta | Versión |
|---|---|
| Docker + Docker Compose v2 | Reciente, con soporte de `profiles` |
| JDK | 21 (microservicios backend) |
| Maven | 3.9 (embebido en las imágenes de build Docker) |
| Android Studio / SDK | Para compilar la app móvil desde fuente (el CI publica el APK como artefacto) |
| LaTeX | `pdflatex` + `biblatex` (backend `biber`), paquetes `tikz`, `tabularx`, `booktabs`, `subcaption` |

---

## 3. Arranque rápido

```bash
git clone https://github.com/JoseLozanoMorales/TiendaTech.git
cd TiendaTech
```

Copie `.env.example` a `.env` y cambie los valores marcados con `reemplazar_`
(`.env` está excluido por `.gitignore`). El Compose local crea e inicializa un
clúster CockroachDB de tres nodos; no necesita certificados ni una base externa.
En producción, `CRDB_DATASOURCE_URL` y `CRDB_CERTS_DIR` deben apuntar al clúster
y certificados administrados por el equipo.

Levantar el stack completo (gateway + 7 microservicios, incluido `armado-ia`):

```bash
cp .env.example .env
docker compose up -d --build
```

Comprobación:

```bash
docker compose ps
curl --fail http://localhost:8180/actuator/health
docker compose exec tiendatech-crdb-1 cockroach node status --insecure --host=localhost:26257
python3 scripts/audit_paso4.py
```

Evidencia del arranque con un solo comando en el equipo de Jeremy:
[construcción, estado de los contenedores y salud del gateway](docs/evidencias/arranque-un-comando/README.md).

---

## 4. Arquitectura

El sistema se organiza como un API Gateway (Spring Cloud Gateway) que enruta hacia 7 microservicios de dominio (6 en Java/Spring Boot, 1 en Python/FastAPI para el motor de recomendación con IA), cada uno refactorizado en capas (`domain` → `application` → `infrastructure` → `presentation`) con patrones GoF aplicados según el dominio de cada servicio. La comunicación entre servicios es REST síncrona, salvo `ventas-service`→`inventario-service`, que usa un patrón Outbox transaccional asíncrono para desacoplar la generación de facturas de la disponibilidad de inventario-service.

### Estructura del repositorio (carpetas de primer nivel)

| Carpeta | Contenido |
|---|---|
| `services/` | Los 6 microservicios Java/Spring Boot (`usuarios`, `productos`, `inventario`, `pedidos`, `ordenes-proveedores`, `ventas`) y el gateway; cada uno en capas `domain`/`application`/`infrastructure`/`presentation`. |
| `Apps/` | Aplicaciones cliente: `Apps/web/frontend` (SPA) y `Apps/mobile` (app Android). |
| `docs/` | Manuscrito (`PFC4.tex`), diagramas, evidencias de entregas y resultados de experimentos documentados. |
| `experiments/` | Guiones y resultados de los experimentos de la campaña real (Paso 7 coordinación, Paso 8 comparación 2PC/Saga). |
| `tests/` | Pruebas de contrato (Pact), E2E web (Playwright) y de carga (Locust). |
| `ops/` | Observabilidad: configuración de Prometheus, Grafana y Alloy. |
| `contracts/` | Definiciones `.proto` de gRPC compartidas entre servicios. |
| `deploy/` | Configuración de despliegue (`Caddyfile` como reverse proxy). |
| `scripts/` | Utilidades de auditoría y medición (cobertura, complejidad ciclomática, ISO/IEC 25010). |
| `spark/` | Análisis de datos con PySpark de los experimentos de rendimiento. |
| `release/` | Capturas de pantalla y artefactos de evidencia de la entrega. |
| `resultados/` | CSV y figuras del análisis de tiempos/eficiencia (Paso 6). |
| `.github/` | Workflows de CI/CD. |
| `.idea/` | Único archivo de configuración de IntelliJ mantenido (`TiendaTech.iml`); el resto de la carpeta está ignorada por `.gitignore`. |
| `Evidencias de trabajo/` | Capturas y evidencia de trabajo individual por integrante, organizadas en subcarpetas por nombre. |

### Diagramas disponibles (`docs/diagrams/`)

- `tiendatech-arquitectura-e4.drawio` / `tiendatech-arquitectura-e4.drawio.png` — Arquitectura general consolidada de la Entrega 4.
- `tiendatech-c4-l1.drawio` / `tiendatech-c4-l1.drawio.png`, `tiendatech-c4-l2.drawio` / `tiendatech-c4-l2.drawio.png`, `tiendatech-c4-l3-checkout.drawio` / `tiendatech-c4-l3-checkout.drawio.png` — Vistas C4 heredadas de E3.
- `tiendatech-despliegue.drawio` / `tiendatech-despliegue.drawio.png` — Diagrama de despliegue.
- `db-schema.drawio` / `db-schema.drawio.png` — Esquema de base de datos.

---

## 5. Pruebas y CI/CD

Ver el detalle completo, con justificación de las decisiones de priorización del equipo, en `docs/entrega4/PFC4.tex` §"Pruebas y CI/CD". En resumen:

- **Con pruebas:** los seis microservicios Java (`usuarios`, `productos-service`, `inventario-service`, `pedidos-service`, `ordenes-proveedores-service` y `ventas-service`); `pedidos-service` incluye integración contra CockroachDB mediante Testcontainers.
- **CI:** `.github/workflows/ci.yml` (lint + tests + APK móvil, tests backend CRDB, tests + lint Python de `armado-ia`) y `.github/workflows/publish-images.yml` (build y publicación multi-arquitectura de las 8 imágenes en GitHub Container Registry).
- **No implementado:** contratos Pact, pruebas E2E con Playwright y lint dedicado para todos los servicios Java y para la web. Las pruebas de carga Locust están en `tests/load/`.

---

## 6. Documentación

El manuscrito de la Entrega 4 está en `docs/entrega4/PFC4.tex`, y reutiliza la bibliografía compartida `docs/entrega3/referenciasPFC.bib`.

Compilación (desde `docs/entrega4/`):

```bash
pdflatex PFC4.tex
biber PFC4
pdflatex PFC4.tex
pdflatex PFC4.tex
```

**Advertencia:** las imágenes son relativas a `docs/entrega4/`. Para compilar desde un clon u Overleaf deben conservarse `UteqLogo.png`, las imágenes de `img/`, las figuras PNG de `cierre/` y la bibliografía `../entrega3/referenciasPFC.bib` en su estructura versionada. Los diagramas C4 del manuscrito se generan desde TikZ.

---

## 7. Declaración de uso de IA generativa

El equipo declara el uso de **Claude** por Jhinson y Andy, y de **Codex** por
Jeremy y José, como apoyo para análisis técnico, desarrollo, revisión y redacción.
Cada integrante conserva la responsabilidad sobre la comprobación de su aporte:
Jhinson revisó arquitectura y decisiones; Jeremy, implementación y banco de
pruebas; Andy, calidad, CI y seguridad; y José, trazabilidad, fuentes, declaraciones
y compilación documental. La declaración completa, con propósito y secciones
afectadas, está en `docs/entrega4/PFC4.tex`, sección "Declaraciones".

## 8. Paquete de reproducibilidad

El paquete experimental está formado por:

- `experiments/paso8/run_real_experiment.py`: experimento principal contra
  `Gateway -> microservicios -> CockroachDB`, con calentamiento, fallos y carga Locust.
- `experiments/paso8/resultados-reales/correctiva-20260905-final-v2/`: campaña correctiva principal
  auditada de 120 corridas y su validación estructural.
- `experiments/paso7/coordination_lab.py`: piloto local didáctico en SQLite; no
  constituye evidencia de concurrencia distribuida ni sustenta C2, C3 o C6.
- `experiments/paso7/evidence/`: bancos de casos y salidas auditables del piloto.
- `experiments/paso8/run_paso8.py`: ejecución del piloto local y generación de
  figuras SVG históricas.
- `experiments/paso8/resultados/experimento_crudo.csv`: 120 corridas crudas, junto
  con resúmenes, bases SQLite y metadatos.
- `experiments/paso8/analisis.ipynb`: cuaderno de inspección independiente.
- `experiments/paso8/execute_notebook.py`: ejecutor verificable del cuaderno sin
  dependencias adicionales.
- `docs/entrega4/cierre/generar_figuras.py`: regeneración de las figuras rasterizadas
  del documento desde los CSV conservados.
- `CITATION.cff`: autoría y forma de citar el software.

Versiones de referencia: CPython 3.11.1, Matplotlib 3.9.0 (fijado en
`experiments/requirements.txt`), TeX Live 2026 y Biber. El ejecutor experimental
solo usa la biblioteca estándar. Desde una clonación limpia, los comandos exactos
para comprobar el banco, regenerar resultados y figuras, ejecutar el cuaderno y
compilar el PDF están en `docs/entrega4/README.md`.

---

## 9. Trazabilidad con la rúbrica

> **Cierre acumulativo del Paso 13 (1 de septiembre de 2026):** la versión actualizada es [PFC4.tex](docs/entrega4/PFC4.tex), con [PDF](docs/entrega4/PFC4.pdf) e [instrucciones de compilación con Biber](docs/entrega4/README.md). Las tablas históricas de esta sección no sustituyen el diagnóstico actualizado de esa memoria, que incorpora las evidencias posteriores y sus límites.

Ver `docs/entrega4/PFC4.tex` §"Trazabilidad E1-E4" para la tabla completa de cierre del ciclo de las cuatro entregas y `docs/auditoria-rubrica-e4.md` para la auditoría interna de requisitos. Resumen por dimensión:

| Dimensión | Estado |
|---|---|
| D1 — Arquitectura y decisiones | ✅ Completo |
| D2 — Aplicación web | ✅ Completo |
| D3 — Aplicación móvil | ✅ Completo |
| D4.1 — Contratos Pact | ✅ Implementado para login móvil y catálogo web; ver `tests/contract/` |
| D4.2 — Persistencia distribuida | ✅ Completo (clúster de 3 nodos y propiedad por esquema) |
| D5.1 — Pirámide de pruebas | ✅ Unitarias, integración, Pact, E2E web y carga versionadas |
| D5.2 — Pipeline CI/CD | ✅ CI y quality gate incluyen contratos y E2E; la publicación se condiciona a ambos |
| D6 — Observabilidad | ✅ Métricas, logs, Grafana bajo carga y trazas distribuidas, incluido el canal TCP |
| D7 — Evaluación ISO/IEC 25010 | ✅ Cinco características medidas; rendimiento no cumple su objetivo y conserva plan técnico |
| D8 — Documentación y reproducibilidad | ✅ Completo |
| D9 — Ética, discusión y defensa oral | ✅ Completo (defensa oral pendiente de presentar) |

---

## 10. Pendientes conocidos

- Falta ampliar la cobertura de `ordenes-proveedores-service` y `ventas-service`; ambos ya contienen pruebas unitarias.
- Los contratos Pact cubren dos interacciones y los E2E web dos recorridos; ampliar casos si cambian esos contratos o rutas.
- La campaña correctiva completó 120 corridas y 30 275 checkouts confirmados. Saga mostró mejores valores descriptivos en varias condiciones, pero ninguna comparación p95 fue significativa tras Bonferroni y todavía falta un oráculo persistente de invariantes.
- La carga ISO obtuvo p95 de 610 ms frente al objetivo menor a 500 ms; requiere optimización y repeticiones para estimar un intervalo del p95.
- No se ejecutó una comparación del asistente basado en reglas frente a RAG sobre un conjunto independiente.
- Para producción deben sustituirse todos los valores de ejemplo y montarse los certificados del clúster administrado; el Compose local es autocontenido y no requiere sobrescribir `CRDB_DATASOURCE_URL`.
---

## 11. Paso 3 — TCP, gRPC y relojes de Lamport

El carrito reserva stock mediante un canal TCP persistente entre `pedidos-service`
y `inventario-service`. Cada mensaje usa un encabezado de 4 bytes, entero sin
signo en orden de red (big-endian), seguido por exactamente esa cantidad de bytes
JSON. Tanto cliente como servidor leen en un ciclo (`readFully`); nunca asumen que
un solo `recv` contiene el mensaje completo. Inventario publica la misma operación
en gRPC para el experimento comparativo.

El contrato fuente versionado es `contracts/stock_reservation.proto`. Maven genera
Java dentro de `target/generated-sources/protobuf`, que está ignorado y no debe
subirse. Para regenerar y compilar:

```bash
mvn -f services/inventario-service/pom.xml clean compile
mvn -f services/pedidos-service/pom.xml clean compile
```

Los stubs Python del experimento también son temporales:

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r experiments/requirements.txt
sh experiments/generate_proto.sh
python experiments/run_latency.py --host 127.0.0.1 \
  --user-id 47 --product-id 4 --tcp-cart-id 1001 --grpc-cart-id 1002
```

El ejecutor usa `time.perf_counter()` y realiza 100 envíos por tecnología de
forma predeterminada. Escribe cada observación en
`experiments/data/latency_sockets.csv` y `latency_grpc.csv`, muestra media,
mediana, desviación estándar y percentil 95, y genera
`experiments/figures/latency_boxplot.png` a 300 DPI. Los identificadores deben
existir en la base desplegada; los CSV incluidos solo contienen la cabecera hasta
ejecutar el experimento real y no constituyen resultados fabricados.

### Validar y compilar el documento acumulativo

Antes de compilar, `scripts/validate_evidence_refs.py` comprueba que cada
evidencia citada en el manuscrito (archivo principal
`docs/entrega4/PFC4.tex`, sus `\input` — `registro-cambios.tex`,
`estado-arte-2pc-saga.tex`, `trazabilidad-temas.tex`,
`actualizacion-evidencias.tex` — y la matriz
`docs/experimentos/resultados/iso25010.csv`) existe realmente en el árbol
versionado, contra el commit exacto al que cada cita está pinneada (no
contra el filesystem local, que podría tener un archivo sin comitear). El
CI (`manuscript-quality` en `.github/workflows/ci.yml`) ejecuta este
validador antes de compilar y falla la compilación si alguna referencia no
resuelve.

Dependencias del validador: Python 3 y `git` con el historial completo (un
clon superficial impide resolver commits históricos citados; en CI se usa
`fetch-depth: 0`). Sin argumentos usa `docs/entrega4/PFC4.tex` y la matriz
ISO como valores por defecto:

```bash
python scripts/validate_evidence_refs.py
```

Formatos verificados: la macro `\evidencia{ruta}{etiqueta}` (pinneada al
commit fijado dentro de su propia definición), enlaces
`\href{.../blob/<SHA>/<ruta>}` (pinneados al SHA del propio enlace),
enlaces `\href{.../commit/<SHA>}` (solo existencia del commit) y la columna
`evidencia` de la matriz ISO (contra `HEAD`). Enlaces a comentarios de
issues, Pull Requests o ejecuciones de GitHub Actions se reportan como
excepción documentada (no son objetos git, no se evalúan como fallo);
cualquier otro formato de enlace a `github.com` no contemplado se trata
como fallo, no se omite en silencio. Ver el docstring del script para el
detalle completo.

**Importante:** que un archivo exista no acredita por sí solo la veracidad
de lo que el texto afirma sobre su contenido — eso se revisa por separado,
manualmente, al redactar y verificar cada afirmación.

Con la validación en verde, desde la raíz del repositorio, con TeX Live y
Biber:

```powershell
cd docs/entrega4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
biber PFC4
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
pdflatex -interaction=nonstopmode -halt-on-error PFC4.tex
```

La bibliografía es `docs/entrega3/referenciasPFC.bib`. El logo y las figuras utilizadas ya forman parte de la estructura versionada; los archivos v2 no son necesarios.
