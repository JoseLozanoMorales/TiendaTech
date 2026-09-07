#!/usr/bin/env python3
"""Diagnostico de solo lectura: interseccion de productos habilitados entre
productos.producto e inventario.inventario_producto, con su stock actual.

No modifica nada. Reutiliza reset_ambiente.conectar() para no reinventar el
parseo de CRDB_DATASOURCE_URL ni las credenciales.
"""
from __future__ import annotations

import json

from reset_ambiente import conectar

QUERY = """
    SELECT p.producto_id, p.stock AS stock_productos, i.stock AS stock_inventario
      FROM productos.producto p
      JOIN inventario.inventario_producto i USING (producto_id)
     WHERE p.habilitado AND i.habilitado
     ORDER BY p.producto_id
"""


def main() -> int:
    with conectar() as conn:
        with conn.cursor() as cur:
            cur.execute(QUERY)
            filas = cur.fetchall()
    resultado = [
        {"producto_id": r[0], "stock_productos": r[1], "stock_inventario": r[2]}
        for r in filas
    ]
    print(json.dumps({"total": len(resultado), "productos": resultado}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
