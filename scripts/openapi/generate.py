"""Export framework contracts, check route coverage, and publish reproducible snapshots."""
import argparse
import copy
import fnmatch
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml
from openapi_spec_validator import validate

ROOT = Path(__file__).resolve().parents[2]
# Valores relativos a ROOT (antes se asumia "services/<valor>" en los dos
# lugares que los usan; el gateway vive fuera de services/, asi que ahora
# cada entrada trae su ruta completa).
SERVICES = {
    "productos": "services/productos-service", "inventario": "services/inventario-service",
    "pedidos": "services/pedidos-service", "ordenes-proveedores": "services/ordenes-proveedores-service",
    "ventas": "services/ventas-service", "usuarios": "services/usuarios", "armado-ia": "services/armado-ia",
    # Punto 4: el gateway (Apps/web/frontend) exponia /api/admin/system sin
    # documentar porque nunca estuvo en este diccionario. Su unico
    # @RestController (SystemObservabilityController) ahora se exporta con
    # el mismo mecanismo que los demas servicios; WebappController (vistas
    # legacy, @Controller puro) queda fuera por el filtro angostado en
    # OpenApiExportTest.java, no por logica de este script.
    "gateway": "Apps/web/frontend",
}
METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
GATEWAY = ROOT / "Apps/web/frontend/src/main/resources/application.yml"
POLICY = Path(__file__).with_name("responses.json")


def operations(document):
    return {f"{method.upper()} {path}" for path, item in document["paths"].items()
            for method in item if method in METHODS}


def assert_coverage(document, inventory):
    documented = operations(document)
    expected = set(inventory)
    if documented != expected:
        raise ValueError(f"Route mismatch: missing={sorted(expected - documented)}, "
                         f"unexpected={sorted(documented - expected)}")
    for route, metadata in inventory.items():
        method, path = route.split(" ", 1)
        operation = document["paths"][path][method.lower()]
        body = operation.get("requestBody")
        if metadata.get("requestBody") and not body:
            raise ValueError(f"Missing request schema: {route}")
        if body and (not body.get("content") or any("schema" not in media for media in body["content"].values())):
            raise ValueError(f"Missing request schema: {route}")
        for response in operation.get("responses", {}).values():
            if any("schema" not in media for media in response.get("content", {}).values()):
                raise ValueError(f"Missing response schema: {route}")


def schema_30(value):
    """Normalize FastAPI's JSON Schema null unions to OpenAPI 3.0 nullable."""
    if isinstance(value, list):
        return [schema_30(item) for item in value]
    if not isinstance(value, dict):
        return value
    value = {key: schema_30(item) for key, item in value.items()}
    union = value.get("anyOf", [])
    non_null = [item for item in union if item != {"type": "null"}]
    if union and len(non_null) != len(union):
        value.pop("anyOf")
        if len(non_null) == 1 and "$ref" not in non_null[0]:
            value.update(non_null[0])
            value["nullable"] = True
        else:
            # OpenAPI 3.0 ignores siblings of $ref, and nullable only applies
            # to an explicit type. A separate null-only branch preserves both.
            value["anyOf"] = non_null + [{"type": "object", "nullable": True, "enum": [None]}]
    if value.get("additionalProperties") == {"type": "object"}:
        # Java Map<String,Object> accepts scalar/array values as well as objects.
        value["additionalProperties"] = True
    return value


def envelope(data):
    return {"type": "object", "required": ["status", "data", "message", "timestamp"],
            "properties": {"status": {"type": "integer"}, "data": data,
                           "message": {"type": "string"},
                           "timestamp": {"type": "string", "format": "date-time"}}}


def public_operation(service, method, path):
    if path.startswith(("/actuator/", "/internal/")):
        return True
    if service == "usuarios":
        return (path in {"/api/login", "/auth/refresh", "/auth/keepalive", "/auth/logout",
                         "/api/usuarios/crear", "/api/usuarios/recuperar-password",
                         "/api/seguridad/cambiar-password-token"}
                or path.startswith("/api/otp")
                or (method == "get" and path.startswith(("/api/provincias", "/api/ciudades"))))
    return (method in {"get", "head"} and path.startswith(
        ("/api/productos", "/api/categorias", "/api/marcas", "/api/gamas", "/api/galeria")))


def gateway_patterns():
    config = yaml.safe_load(GATEWAY.read_text(encoding="utf-8"))
    routes = config["spring"]["cloud"]["gateway"]["server"]["webmvc"]["routes"]
    return [pattern for route in routes for predicate in route["predicates"]
            if predicate.startswith("Path=") for pattern in predicate[5:].split(",")]


def exposed(path, patterns):
    return any(path == pattern.removesuffix("/**") or fnmatch.fnmatchcase(path, pattern)
               for pattern in patterns)


def enrich(service, raw, inventory, patterns, overrides):
    document = schema_30(copy.deepcopy(raw))
    document["openapi"] = "3.0.3"
    document["info"] = {"title": f"TiendaTech {service}", "version": "1.0.0",
                        "description": "Generado desde los controladores. Los objetos dinámicos "
                        "conservan propiedades abiertas cuando el código no declara un DTO."}
    document["servers"] = [{"url": "/", "description": "URL base del servicio"}]
    components = document.setdefault("components", {})
    components["securitySchemes"] = {
        "bearerAuth": {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"},
        "refreshCookie": {"type": "apiKey", "in": "cookie", "name": "refresh"},
    }
    for path, item in document["paths"].items():
        for method, operation in item.items():
            if method not in METHODS:
                continue
            route = f"{method.upper()} {path}"
            metadata = inventory[route]
            operation["operationId"] = service.replace("-", "_") + "_" + method + "_" + re.sub(r"\W+", "_", path).strip("_")
            operation["x-gateway-exposed"] = exposed(path, patterns)
            operation["x-scope"] = ("internal" if path.startswith("/internal/") else
                                    "diagnostic" if path.startswith(("/actuator/", "/api/crdb/", "/api/reservas/"))
                                    else "business")
            operation["security"] = [] if public_operation(service, method, path) else [{"bearerAuth": []}]
            if path in {"/auth/refresh", "/auth/keepalive"}:
                operation["security"] = [{"refreshCookie": []}]
            responses = operation.setdefault("responses", {})
            rule = overrides.get(service, {}).get(route, {})
            success = str(rule.get("status", 200))
            if success != "200":
                responses[success] = responses.pop("200", {"description": "OK"})
                responses[success]["description"] = "Created" if success == "201" else "No Content"
            if success == "201" and rule.get("location", True):
                responses[success]["headers"] = {"Location": {"description": "URI del recurso creado",
                                                               "schema": {"type": "string"}}}
            # FastAPI installs a custom validation handler returning 400, not 422.
            if service == "armado-ia":
                responses.pop("422", None)
            for code, response in responses.items():
                if code == "204" or method == "head":
                    response.pop("content", None)
                    continue
                for media_type, media in list(response.get("content", {}).items()):
                    schema = media.get("schema", {})
                    binary = schema.get("format") == "binary" or media_type == "application/pdf"
                    if metadata.get("returnType", "").endswith("<?>"):
                        schema = {"description": "Contenido dinámico devuelto por el controlador; admite cualquier valor JSON."}
                    unwrapped = (path == "/actuator/health" if service == "armado-ia"
                                 else path.startswith(("/actuator/", "/internal/")))
                    if not binary and not unwrapped:
                        media["schema"] = envelope(schema)
                    if media_type == "*/*" and not binary:
                        response["content"].pop(media_type)
                        response["content"]["application/json"] = media
            errors = rule.get("errors", [])
            if operation["security"]:
                errors = sorted(set(errors) | {401, 403})
            if service == "armado-ia" and path == "/api/armado/analizar":
                errors = sorted(set(errors) | {400, 404, 500, 503})
            for code in errors:
                responses[str(code)] = {"description": {400: "Solicitud inválida", 401: "Credenciales ausentes o inválidas",
                    403: "Acceso denegado", 404: "Recurso no encontrado", 409: "Conflicto de reserva",
                    429: "Demasiadas solicitudes", 500: "Error interno", 503: "Servicio no disponible"}[code],
                    "content": {"application/json": {"schema": envelope({"nullable": True,
                        "description": "Detalles del error; su estructura depende del manejador."})}}}
            if method == "head":
                for response in responses.values():
                    response.pop("content", None)
    for route in overrides.get(service, {}):
        if route not in inventory:
            raise ValueError(f"Stale response override: {service} {route}")
    assert_coverage(document, inventory)
    return document


def export_python():
    os.environ["ARMADO_EXPLICACION__PROVEEDOR"] = "deterministic"
    os.environ["OTEL_SDK_DISABLED"] = "true"
    sys.path.insert(0, str(ROOT / "services/armado-ia"))
    from app.main import app
    from fastapi.routing import APIRoute
    inventory = {f"{method} {route.path}": {"requestBody": route.body_field is not None}
                 for route in app.routes if isinstance(route, APIRoute) and route.path != "/metrics"
                 for method in route.methods}
    # /metrics and framework documentation routes are infrastructure, not application handlers.
    document = copy.deepcopy(app.openapi())
    document["paths"].pop("/metrics", None)
    return document, inventory


def consolidate(documents):
    paths = {}
    for service, document in documents.items():
        for path, item in document["paths"].items():
            if not any(op.get("x-gateway-exposed") for method, op in item.items() if method in METHODS):
                continue
            if path in paths:
                raise ValueError(f"Gateway path collision: {path}")
            pointer = path.replace("~", "~0").replace("/", "~1")
            paths[path] = {"$ref": f"./{service}.yaml#/paths/{pointer}"}
    return {"openapi": "3.0.3", "info": {"title": "TiendaTech - API del Gateway", "version": "1.0.0",
            "description": "Rutas publicadas por el gateway; referencias a los siete contratos generados por servicio."},
            "servers": [{"url": "http://localhost:8180"}], "paths": paths}


def write_json(path, data):
    # JSON is defined to be UTF-8 (RFC 8259); pin the encoding explicitly
    # instead of relying on the OS locale. On Windows, Path.write_text()
    # without encoding= falls back to the locale codepage (e.g. cp1252),
    # which mis-encodes accented characters like the "á" in the Spanish
    # descriptions above. Those bytes round-trip fine within Python (same
    # locale reads them back), but consolidate() below builds cross-file
    # $ref entries between these *.yaml files, and openapi_spec_validator's
    # $ref resolver re-reads the referenced file straight off disk via its
    # file:// URI and decodes it as strict UTF-8 -- so a cp1252-only byte
    # there breaks with "invalid trailing UTF-8 octet".
    path.write_text(json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
                     encoding="utf-8")


def generate(args):
    documents = {}
    overrides = json.loads(POLICY.read_text(encoding="utf-8"))
    patterns = gateway_patterns()
    for service, directory in SERVICES.items():
        if service == "armado-ia":
            raw, inventory = export_python()
        else:
            if not args.from_build:
                command = [os.environ.get("MVN", "mvn"), "-B", "-ntp", "-Popenapi",
                           "-Dtest=OpenApiExportTest", "-Djacoco.skip=true", "test"]
                if args.maven_repo:
                    command.insert(1, f"-Dmaven.repo.local={args.maven_repo}")
                subprocess.run(command, cwd=ROOT / directory, check=True)
            folder = ROOT / directory / "target/openapi"
            raw = json.loads((folder / "raw.json").read_text(encoding="utf-8"))
            inventory = json.loads((folder / "routes.json").read_text(encoding="utf-8"))
        documents[service] = enrich(service, raw, inventory, patterns, overrides)
        print(f"{service}: {len(inventory)} operaciones verificadas", flush=True)
    with tempfile.TemporaryDirectory(prefix="tiendatech-openapi-") as tmp:
        folder = Path(tmp)
        for service, document in documents.items():
            write_json(folder / f"{service}.yaml", document)
        write_json(folder / "openapi.yaml", consolidate(documents))
        for file in sorted(folder.glob("*.yaml")):
            validate(json.loads(file.read_text(encoding="utf-8")), base_uri=file.as_uri())
        destination = ROOT / "docs/api"
        mismatches = []
        for file in sorted(folder.glob("*.yaml")):
            target = destination / file.name
            if args.check:
                if not target.exists() or target.read_text(encoding="utf-8") != file.read_text(encoding="utf-8"):
                    mismatches.append(file.name)
            else:
                target.write_text(file.read_text(encoding="utf-8"), encoding="utf-8")
        if mismatches:
            raise ValueError("Contratos desactualizados: " + ", ".join(mismatches)
                             + ". Ejecuta python scripts/openapi/generate.py y revisa el diff.")
    print("OpenAPI: cobertura, esquemas, referencias y snapshots verificados.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail on drift without modifying docs/api")
    parser.add_argument("--from-build", action="store_true", help="Reuse local Java exports (development only)")
    parser.add_argument("--maven-repo", help="Optional Maven repository location")
    generate(parser.parse_args())
