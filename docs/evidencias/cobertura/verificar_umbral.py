#!/usr/bin/env python3
"""Verifica el umbral de cobertura de líneas desde los XML versionados."""

from __future__ import annotations

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path


SERVICIOS = {
    "armado-ia": "armado-ia-coverage.xml",
    "inventario-service": "inventario-service-jacoco.xml",
    "ordenes-proveedores-service": "ordenes-proveedores-service-jacoco.xml",
    "pedidos-service": "pedidos-service-jacoco.xml",
    "productos-service": "productos-service-jacoco.xml",
    "usuarios": "usuarios-jacoco.xml",
    "ventas-service": "ventas-service-jacoco.xml",
}


def lineas(path: Path) -> tuple[int, int]:
    root = ET.parse(path).getroot()
    if root.tag == "report":  # JaCoCo
        counter = next(
            item for item in root.findall("counter") if item.get("type") == "LINE"
        )
        covered = int(counter.get("covered", "0"))
        missed = int(counter.get("missed", "0"))
        return covered, covered + missed
    # coverage.py/Cobertura
    return int(float(root.get("lines-covered", "0"))), int(
        float(root.get("lines-valid", "0"))
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--umbral", type=float, default=70.0)
    parser.add_argument("--salida", type=Path)
    args = parser.parse_args()
    base = Path(__file__).resolve().parent
    resultados = []
    for servicio, nombre in SERVICIOS.items():
        cubiertas, total = lineas(base / nombre)
        porcentaje = round(100 * cubiertas / total, 2) if total else 0.0
        resultados.append(
            {
                "servicio": servicio,
                "reporte": f"docs/evidencias/cobertura/{nombre}",
                "lineas_cubiertas": cubiertas,
                "lineas_totales": total,
                "cobertura_porcentaje": porcentaje,
                "umbral_porcentaje": args.umbral,
                "cumple": porcentaje >= args.umbral,
            }
        )
    informe = {
        "metrica": "cobertura de líneas en la capa de lógica de negocio instrumentada",
        "umbral_porcentaje": args.umbral,
        "servicios": resultados,
        "todos_cumplen": all(item["cumple"] for item in resultados),
    }
    texto = json.dumps(informe, ensure_ascii=False, indent=2) + "\n"
    if args.salida:
        args.salida.write_text(texto, encoding="utf-8")
    print(texto, end="")
    return 0 if informe["todos_cumplen"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
