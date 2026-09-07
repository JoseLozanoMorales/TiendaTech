#!/usr/bin/env python3
"""Redistribuye productoId en un banco ya generado, sin re-registrar usuarios.

Motivo: el banco original asignaba solo 3 productos (round-robin) a los 400
usuarios sinteticos. Con concurrencia >= 25, eso concentra decenas de
escrituras simultaneas sobre las mismas 3 filas de producto y satura el
generador con esperas de cliente (HTTP 0) que no reflejan una diferencia real
entre 2PC y Saga, sino un cuello de fila caliente artificial.

Este script solo reescribe el campo productoId de cada caso ya existente
(cuentas, tokens, direccion y metodo de pago se conservan intactos) usando el
mismo esquema round-robin que generate_request_bank.py, pero sobre una lista
mas ancha de productos.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--banco", type=Path, required=True)
    ap.add_argument("--producto-ids", type=int, nargs="+", required=True)
    args = ap.parse_args()

    casos = json.loads(args.banco.read_text(encoding="utf-8"))
    productos = args.producto_ids
    for i, caso in enumerate(casos):
        caso["productoId"] = productos[i % len(productos)]

    args.banco.write_text(json.dumps(casos, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    distrib: dict[int, int] = {}
    for caso in casos:
        distrib[caso["productoId"]] = distrib.get(caso["productoId"], 0) + 1
    print(f"{len(casos)} casos redistribuidos sobre {len(productos)} productos")
    print("distribucion:", dict(sorted(distrib.items())))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
