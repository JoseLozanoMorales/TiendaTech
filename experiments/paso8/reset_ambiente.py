#!/usr/bin/env python3
"""Reinicio de entorno entre corridas: repone el stock de los productos del
experimento a un piso alto, via SQL directo a CockroachDB.

No se usa la API para esto porque el stock se mueve por kardex/movimientos de
inventario (no hay un PUT directo de stock), y porque un UPDATE es una sola
llamada rapida (<1s) en vez de una secuencia de movimientos por producto.

Reutiliza las mismas variables de entorno que ya usan los microservicios
(CRDB_DATASOURCE_URL/_USERNAME/_PASSWORD en docker-compose.yml) para no
inventar credenciales nuevas. CRDB_DATASOURCE_URL es un URL JDBC con posibles
varios hosts separados por coma (targetServerType=any); este script los
intenta en orden hasta conectar.
"""

from __future__ import annotations

import argparse
import os
import random
import re
import sys
import time

import psycopg


def parsear_jdbc(url: str) -> tuple[list[tuple[str, int]], str]:
    match = re.match(r"jdbc:postgresql://([^/]+)/([^?]+)", url)
    if not match:
        raise SystemExit(f"CRDB_DATASOURCE_URL no tiene el formato esperado: {url}")
    hosts_raw, dbname = match.group(1), match.group(2)
    hosts = []
    for parte in hosts_raw.split(","):
        if ":" in parte:
            host, puerto = parte.split(":", 1)
            hosts.append((host, int(puerto)))
        else:
            hosts.append((parte, 26257))
    return hosts, dbname


def conectar() -> psycopg.Connection:
    url = os.environ.get("CRDB_DATASOURCE_URL")
    if not url:
        raise SystemExit("falta CRDB_DATASOURCE_URL en el entorno (mismo valor que usan los microservicios)")
    usuario = os.environ.get("CRDB_DATASOURCE_USERNAME", "root")
    contrasena = os.environ.get("CRDB_DATASOURCE_PASSWORD", "")
    hosts, dbname = parsear_jdbc(url)
    errores = []
    for host, puerto in hosts:
        try:
            return psycopg.connect(host=host, port=puerto, dbname=dbname, user=usuario,
                                    password=contrasena or None, sslmode="disable", connect_timeout=5)
        except psycopg.OperationalError as error:
            errores.append(f"{host}:{puerto} -> {error}")
    raise SystemExit("no se pudo conectar a ningun host de CRDB_DATASOURCE_URL:\n" + "\n".join(errores))


def topar_stock(producto_ids: list[int], valor: int) -> None:
    """Actualiza AMBAS tablas de stock: productos.producto.stock (lo que lista
    /api/productos) y inventario.inventario_producto.stock (lo que reserva
    inventario-service via TCP al agregar al carrito). No hay certeza de cual
    de las dos gatilla el rechazo real, asi que se reponen las dos.
    """
    with conectar() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE productos.producto SET stock = %s WHERE producto_id = ANY(%s)",
                (valor, producto_ids),
            )
            afectados_producto = cur.rowcount
            cur.execute(
                "UPDATE inventario.inventario_producto SET stock = %s WHERE producto_id = ANY(%s)",
                (valor, producto_ids),
            )
            afectados_inventario = cur.rowcount
        conn.commit()
    print(f"stock repuesto a {valor} para productos {producto_ids} "
          f"(productos.producto: {afectados_producto} filas, "
          f"inventario.inventario_producto: {afectados_inventario} filas)")
    if afectados_inventario == 0:
        print("ADVERTENCIA: 0 filas en inventario.inventario_producto para estos IDs -- "
              "si esa tabla es la que gatilla el rechazo de reserva, el tope no esta surtiendo efecto ahi")


def limpiar_carritos_experimento(usuario_ids: list[int], max_intentos: int = 8) -> dict[str, int]:
    """Deja vacios los carritos del banco antes de una fase de carga.

    Los relojes Lamport de los servicios viven en memoria, pero el ultimo
    estado reconciliado vive en CockroachDB. Tras reiniciar los contenedores,
    ese desfase puede rechazar como antiguo el primer evento de un carrito.
    Tambien es normal que Locust termine un calentamiento justo despues de
    reservar y antes del checkout. Limpiar *solo* los usuarios sinteticos
    elimina ambos residuos sin tocar ordenes ni facturas ya producidas.
    """
    ids = sorted({int(usuario_id) for usuario_id in usuario_ids})
    if not ids or any(usuario_id <= 0 for usuario_id in ids):
        raise ValueError("usuario_ids debe contener identificadores positivos")

    for intento in range(1, max_intentos + 1):
        try:
            with conectar() as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT carrito_id
                          FROM pedidos.carrito_de_compra
                         WHERE usuario_id = ANY(%s) AND habilitado = true
                        """,
                        (ids,),
                    )
                    carrito_ids = [int(fila[0]) for fila in cur.fetchall()]
                    if not carrito_ids:
                        raise RuntimeError("ningun usuario sintetico tiene carrito activo")

                    cur.execute(
                        "DELETE FROM inventario.reserva_stock WHERE carrito_id = ANY(%s)",
                        (carrito_ids,),
                    )
                    reservas = cur.rowcount
                    cur.execute(
                        "DELETE FROM pedidos.carrito_detalle WHERE carrito_id = ANY(%s)",
                        (carrito_ids,),
                    )
                    detalles = cur.rowcount
                conn.commit()
            break
        except psycopg.Error as error:
            if error.sqlstate != "40001" or intento >= max_intentos:
                raise
            # CockroachDB exige repetir la transaccion completa. El jitter
            # evita que la limpieza vuelva a chocar con el mismo lote.
            base = min(2.0, 0.1 * (2 ** (intento - 1)))
            time.sleep(base + random.uniform(0, base / 2))

    resultado = {
        "usuarios": len(ids),
        "carritos": len(carrito_ids),
        "reservas_eliminadas": reservas,
        "detalles_eliminados": detalles,
        "intentos": intento,
    }
    print(
        "carritos sinteticos preparados: "
        f"{resultado['usuarios']} usuarios, {resultado['carritos']} carritos, "
        f"{reservas} reservas y {detalles} detalles residuales eliminados"
    )
    return resultado


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--producto-ids", type=int, nargs="+", required=True)
    ap.add_argument("--valor", type=int, default=1_000_000)
    args = ap.parse_args()
    topar_stock(args.producto_ids, args.valor)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
