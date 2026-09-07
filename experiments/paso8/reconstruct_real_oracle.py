#!/usr/bin/env python3
"""Reconstruye evidencia de consistencia del Paso 8 desde CockroachDB real.

El script es deliberadamente de solo lectura. Asigna los registros persistidos a
las ventanas temporales de ``experimento_real_crudo.csv`` y no usa el laboratorio
SQLite de Paso 7/Paso 8.

Limitacion intencional: los intentos de checkout fallidos se almacenaron en un
buffer acotado en memoria del servicio Pedidos y se perdieron al reiniciar los
contenedores. Por ello el invariante de cancelacion/compensacion se marca siempre
``no_verificado`` y nunca se infiere como aprobado.
"""

from __future__ import annotations

import argparse
import bisect
import csv
import hashlib
import json
import statistics
import sys
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterable

from psycopg import sql


ROOT = Path(__file__).resolve().parents[2]
PASO8 = Path(__file__).resolve().parent
sys.path.insert(0, str(PASO8))

from reset_ambiente import conectar  # noqa: E402


NO_VERIFICADO = "no_verificado"
NO_APLICA = "no_aplica"


@dataclass(frozen=True)
class RunWindow:
    index: int
    fallo: str
    coord: str
    concurrencia: int
    repeticion: int
    inicio_epoch: float
    fin_epoch: float

    @property
    def key(self) -> tuple[str, str, int, int]:
        return self.fallo, self.coord, self.concurrencia, self.repeticion


def parse_args() -> argparse.Namespace:
    base = PASO8 / "resultados-reales" / "correctiva-20260905-final-v2"
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw-csv", type=Path, default=base / "experimento_real_crudo.csv")
    ap.add_argument("--request-bank", type=Path, default=base / "runs" / "none-2pc-c400-r1" / "banco_autenticado.json")
    ap.add_argument(
        "--user-ids-csv",
        type=Path,
        default=base / "analisis" / "usuarios_sinteticos_ids.csv",
        help="lista publicable de IDs sintéticos; se usa si el banco privado no está disponible",
    )
    ap.add_argument(
        "--as-of",
        help=("timestamp UTC para una lectura historica consistente de CockroachDB; "
              "permite fijar el estado anterior al reprocesamiento del outbox"),
    )
    ap.add_argument("--output", type=Path, default=base / "analisis" / "oracle_por_corrida_crdb.csv")
    ap.add_argument("--summary-output", type=Path, default=base / "analisis" / "oracle_resumen_crdb.json")
    ap.add_argument("--enriched-output", type=Path, default=base / "analisis" / "experimento_real_crudo_enriquecido.csv")
    return ap.parse_args()


def load_windows(path: Path) -> tuple[list[dict[str, str]], list[RunWindow]]:
    with path.open(newline="", encoding="utf-8-sig") as fh:
        raw_rows = list(csv.DictReader(fh))
    windows = []
    for index, row in enumerate(raw_rows):
        inicio = float(row["inicio_epoch"])
        windows.append(RunWindow(
            index=index,
            fallo=row["fallo"],
            coord=row["coord"],
            concurrencia=int(row["concurrencia"]),
            repeticion=int(row["repeticion"]),
            inicio_epoch=inicio,
            fin_epoch=inicio + float(row["duracion_segundos"]),
        ))
    windows.sort(key=lambda item: item.inicio_epoch)
    if len(windows) != 120 or len({window.key for window in windows}) != 120:
        raise SystemExit("el CSV real no contiene exactamente 120 ventanas unicas")
    for anterior, siguiente in zip(windows, windows[1:]):
        if anterior.fin_epoch >= siguiente.inicio_epoch:
            raise SystemExit(f"ventanas solapadas: {anterior.key} y {siguiente.key}")
    return raw_rows, windows


def load_user_ids(request_bank: Path, safe_csv: Path) -> list[int]:
    if request_bank.exists():
        rows = json.loads(request_bank.read_text(encoding="utf-8"))
        ids = sorted({int(row["usuarioId"]) for row in rows if row.get("usuarioId") is not None})
    elif safe_csv.exists():
        with safe_csv.open(newline="", encoding="utf-8-sig") as fh:
            ids = sorted({int(row["usuario_id"]) for row in csv.DictReader(fh)})
    else:
        raise SystemExit(
            "no existe el banco privado ni la lista publicable de IDs sintéticos"
        )
    if not ids:
        raise SystemExit("la fuente de usuarios no contiene IDs")
    return ids


def dt_epoch(value: datetime | None) -> float | None:
    if value is None:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.timestamp()


def run_for_epoch(windows: list[RunWindow], starts: list[float], epoch: float | None) -> RunWindow | None:
    if epoch is None:
        return None
    pos = bisect.bisect_right(starts, epoch) - 1
    if pos < 0:
        return None
    window = windows[pos]
    return window if epoch <= window.fin_epoch else None


def rows_as_dicts(cursor: Any) -> list[dict[str, Any]]:
    columns = [item.name for item in cursor.description]
    return [dict(zip(columns, row)) for row in cursor.fetchall()]


def decimal_equal(left: Any, right: Any) -> bool:
    if left is None or right is None:
        return False
    return Decimal(left).quantize(Decimal("0.01")) == Decimal(right).quantize(Decimal("0.01"))


def median_ms(values: Iterable[float]) -> str | float:
    materialized = list(values)
    return round(float(statistics.median(materialized)), 3) if materialized else NO_VERIFICADO


def iso_utc(epoch: float) -> str:
    return datetime.fromtimestamp(epoch, tz=timezone.utc).isoformat()


def status(violations: int) -> str:
    return "verificado_sin_violaciones" if violations == 0 else "verificado_con_violaciones"


def reconstruct(args: argparse.Namespace) -> tuple[list[dict[str, str]], list[dict[str, Any]], dict[str, Any]]:
    raw_rows, windows = load_windows(args.raw_csv)
    user_ids = load_user_ids(args.request_bank, args.user_ids_csv)
    starts = [window.inicio_epoch for window in windows]
    campaign_start = datetime.fromtimestamp(windows[0].inicio_epoch, tz=timezone.utc)
    campaign_end = datetime.fromtimestamp(windows[-1].fin_epoch, tz=timezone.utc)

    with conectar() as conn:
        # Una sola lectura MVCC evita mezclar estados si el procesador del
        # outbox esta activo mientras se ejecuta la auditoria.
        conn.autocommit = True
        with conn.cursor() as cur:
            cur.execute("BEGIN")
            if args.as_of:
                cur.execute(sql.SQL("SET TRANSACTION AS OF SYSTEM TIME {}").format(sql.Literal(args.as_of)))
            cur.execute("SELECT current_database(), now(), version()")
            database_name, audit_timestamp, database_version = cur.fetchone()

            cur.execute("""
                SELECT o.orden_id, o.usuario_id, o.creado_en, o.estado, o.total AS orden_total,
                       f.factura_id, f.total AS factura_total,
                       ob.estado AS outbox_estado, ob.creado_en AS outbox_creado_en,
                       ob.procesado_en AS outbox_procesado_en
                FROM pedidos.orden o
                LEFT JOIN ventas.factura_encabezado f ON f.orden_id = o.orden_id
                LEFT JOIN ventas.factura_outbox ob ON ob.factura_id = f.factura_id
                WHERE o.usuario_id = ANY(%s)
                  AND o.creado_en >= %s AND o.creado_en <= %s
                ORDER BY o.creado_en, o.orden_id
                """, (user_ids, campaign_start, campaign_end))
            orders = rows_as_dicts(cur)

            cur.execute("""
                SELECT o.orden_id, d.producto_id, d.cantidad
                FROM pedidos.orden o
                JOIN pedidos.detalle_orden d
                  ON d.orden_id = o.orden_id AND d.fecha = o.fecha
                WHERE o.usuario_id = ANY(%s)
                  AND o.creado_en >= %s AND o.creado_en <= %s
                ORDER BY o.orden_id, d.producto_id
                """, (user_ids, campaign_start, campaign_end))
            order_lines = rows_as_dicts(cur)

            cur.execute("""
                SELECT f.factura_id, fc.producto_id, fc.cantidad,
                       fc.total AS linea_total
                FROM pedidos.orden o
                JOIN ventas.factura_encabezado f ON f.orden_id = o.orden_id
                JOIN ventas.factura_cuerpo fc ON fc.factura_id = f.factura_id
                WHERE o.usuario_id = ANY(%s)
                  AND o.creado_en >= %s AND o.creado_en <= %s
                ORDER BY f.factura_id, fc.producto_id
                """, (user_ids, campaign_start, campaign_end))
            invoice_lines = rows_as_dicts(cur)

            # Se consultan movimientos por referencia de factura, no solo por
            # fecha, para incluir convergencias del outbox posteriores a la
            # ventana de medicion. La asignacion a corrida se hace por la orden.
            cur.execute("""
                SELECT f.factura_id, m.movimiento_id, m.producto_id,
                       m.cantidad, m.fecha, m.referencia
                FROM pedidos.orden o
                JOIN ventas.factura_encabezado f ON f.orden_id = o.orden_id
                JOIN inventario.movimiento_inventario m
                  ON m.referencia = concat('FAC-', CAST(f.factura_id AS STRING))
                WHERE o.usuario_id = ANY(%s)
                  AND o.creado_en >= %s AND o.creado_en <= %s
                ORDER BY f.factura_id, m.producto_id, m.movimiento_id
                """, (user_ids, campaign_start, campaign_end))
            movements = rows_as_dicts(cur)

            cur.execute("""
                SELECT fecha, producto_id, saldo_cantidad
                FROM inventario.kardex_inventario
                WHERE fecha >= %s AND fecha <= %s AND saldo_cantidad < 0
                ORDER BY fecha
                """, (campaign_start, campaign_end))
            negative_kardex = rows_as_dicts(cur)

            cur.execute("""
                SELECT creado_en, producto_id, stock_disponible
                FROM inventario.operacion_reserva
                WHERE creado_en >= %s AND creado_en <= %s AND stock_disponible < 0
                ORDER BY creado_en
                """, (campaign_start, campaign_end))
            negative_reservations = rows_as_dicts(cur)

            cur.execute("""
                SELECT count(*)
                FROM inventario.inventario_producto
                WHERE stock < 0
                """)
            current_negative_stock = int(cur.fetchone()[0])
            cur.execute("COMMIT")

    orders_by_run: dict[int, list[dict[str, Any]]] = defaultdict(list)
    unassigned_orders = 0
    for order in orders:
        window = run_for_epoch(windows, starts, dt_epoch(order["creado_en"]))
        if window is None:
            unassigned_orders += 1
        else:
            orders_by_run[window.index].append(order)

    details_by_order: dict[int, dict[int, int]] = defaultdict(dict)
    for line in order_lines:
        details_by_order[int(line["orden_id"])][int(line["producto_id"])] = int(line["cantidad"])

    invoice_lines_by_invoice: dict[int, dict[int, int]] = defaultdict(dict)
    for line in invoice_lines:
        invoice_lines_by_invoice[int(line["factura_id"])][int(line["producto_id"])] = int(line["cantidad"])

    movements_by_invoice: dict[int, dict[int, list[dict[str, Any]]]] = defaultdict(lambda: defaultdict(list))
    for movement in movements:
        movements_by_invoice[int(movement["factura_id"])][int(movement["producto_id"])].append(movement)

    negative_by_run: dict[int, int] = defaultdict(int)
    for event in negative_kardex:
        window = run_for_epoch(windows, starts, dt_epoch(event["fecha"]))
        if window is not None:
            negative_by_run[window.index] += 1
    for event in negative_reservations:
        window = run_for_epoch(windows, starts, dt_epoch(event["creado_en"]))
        if window is not None:
            negative_by_run[window.index] += 1

    oracle_rows: list[dict[str, Any]] = []
    for window in windows:
        run_orders = orders_by_run.get(window.index, [])
        missing_invoice = 0
        invoice_amount_mismatch = 0
        order_invoice_line_mismatch = 0
        stock_missing_or_mismatch = 0
        duplicate_stock_movements = 0
        inconsistent_order_ids: set[int] = set()
        outbox_durations: list[float] = []
        outbox_processed = 0
        outbox_unresolved = 0

        for order in run_orders:
            order_id = int(order["orden_id"])
            factura_id = order["factura_id"]
            if factura_id is None:
                missing_invoice += 1
                stock_missing_or_mismatch += 1
                inconsistent_order_ids.add(order_id)
                continue
            factura_id = int(factura_id)
            if not decimal_equal(order["orden_total"], order["factura_total"]):
                invoice_amount_mismatch += 1
                inconsistent_order_ids.add(order_id)

            expected = details_by_order.get(order_id, {})
            invoiced = invoice_lines_by_invoice.get(factura_id, {})
            if expected != invoiced:
                order_invoice_line_mismatch += 1
                inconsistent_order_ids.add(order_id)

            actual_by_product = movements_by_invoice.get(factura_id, {})
            actual_products = set(actual_by_product)
            expected_products = set(expected)
            movement_problem = actual_products != expected_products
            duplicate = False
            for product_id, expected_quantity in expected.items():
                rows = actual_by_product.get(product_id, [])
                actual_quantity = sum(int(item["cantidad"]) for item in rows)
                if len(rows) != 1 or actual_quantity != expected_quantity:
                    movement_problem = True
                if len(rows) > 1:
                    duplicate = True
            if movement_problem:
                stock_missing_or_mismatch += 1
                inconsistent_order_ids.add(order_id)
            if duplicate:
                duplicate_stock_movements += 1
                inconsistent_order_ids.add(order_id)

            if window.coord == "saga":
                created = dt_epoch(order["outbox_creado_en"])
                processed = dt_epoch(order["outbox_procesado_en"])
                if order["outbox_estado"] == "PROCESADO" and created is not None and processed is not None:
                    outbox_processed += 1
                    outbox_durations.append(max(0.0, (processed - created) * 1000.0))
                else:
                    outbox_unresolved += 1

        invoice_violations = missing_invoice + invoice_amount_mismatch + order_invoice_line_mismatch
        inventory_violations = stock_missing_or_mismatch + duplicate_stock_movements
        negative_events = negative_by_run.get(window.index, 0)
        # Una orden puede incumplir mas de un invariante. Se cuentan ordenes
        # unicas mas eventos historicos de stock negativo, sin duplicar la orden.
        verified_violations = len(inconsistent_order_ids) + negative_events
        operations = len(run_orders)
        rate: str | float = (round(verified_violations / operations, 6)
                             if operations > 0 else NO_VERIFICADO)

        convergence: str | float = NO_APLICA
        convergence_status = NO_APLICA
        if window.coord == "saga":
            convergence = median_ms(outbox_durations)
            convergence_status = (
                "verificado" if outbox_durations and outbox_unresolved == 0
                else "verificado_parcial_con_pendientes" if outbox_durations
                else NO_VERIFICADO
            )

        oracle_rows.append({
            "fallo": window.fallo,
            "coord": window.coord,
            "concurrencia": window.concurrencia,
            "repeticion": window.repeticion,
            "inicio_utc": iso_utc(window.inicio_epoch),
            "fin_utc": iso_utc(window.fin_epoch),
            "ordenes_persistidas": operations,
            "ordenes_sin_factura": missing_invoice,
            "facturas_importe_distinto": invoice_amount_mismatch,
            "orden_factura_lineas_distintas": order_invoice_line_mismatch,
            "orden_factura_estado": status(invoice_violations),
            "ordenes_stock_ausente_o_distinto": stock_missing_or_mismatch,
            "ordenes_descuento_duplicado": duplicate_stock_movements,
            "inventario_exactamente_una_vez_estado": status(inventory_violations),
            "eventos_stock_negativo": negative_events,
            "stock_no_negativo_estado": status(negative_events),
            "outbox_procesados_saga": outbox_processed if window.coord == "saga" else NO_APLICA,
            "outbox_no_resueltos_saga": outbox_unresolved if window.coord == "saga" else NO_APLICA,
            "convergencia_compensacion_ms": convergence,
            "convergencia_compensacion_estado": convergence_status,
            "cancelados_compensados_estado": NO_VERIFICADO,
            "inconsistencias": verified_violations,
            "ordenes_con_inconsistencia": len(inconsistent_order_ids),
            "tasa_inconsistencia": rate,
            "oracle_pass": "false" if verified_violations > 0 else NO_VERIFICADO,
        })

    totals = {
        "corridas": len(oracle_rows),
        "corridas_con_ordenes": sum(int(row["ordenes_persistidas"]) > 0 for row in oracle_rows),
        "corridas_con_violaciones_verificadas": sum(int(row["inconsistencias"]) > 0 for row in oracle_rows),
        "ordenes_persistidas": sum(int(row["ordenes_persistidas"]) for row in oracle_rows),
        "ordenes_sin_factura": sum(int(row["ordenes_sin_factura"]) for row in oracle_rows),
        "facturas_importe_distinto": sum(int(row["facturas_importe_distinto"]) for row in oracle_rows),
        "orden_factura_lineas_distintas": sum(int(row["orden_factura_lineas_distintas"]) for row in oracle_rows),
        "ordenes_stock_ausente_o_distinto": sum(int(row["ordenes_stock_ausente_o_distinto"]) for row in oracle_rows),
        "ordenes_descuento_duplicado": sum(int(row["ordenes_descuento_duplicado"]) for row in oracle_rows),
        "eventos_stock_negativo": sum(int(row["eventos_stock_negativo"]) for row in oracle_rows),
        "current_negative_stock": current_negative_stock,
    }
    summary = {
        "fuente": "CockroachDB real configurado por CRDB_DATASOURCE_URL",
        "database": database_name,
        "database_version": database_version,
        "audit_timestamp_utc": audit_timestamp.isoformat(),
        "as_of_system_time": args.as_of or "transaction_snapshot_at_start",
        "campaign_start_utc": campaign_start.isoformat(),
        "campaign_end_utc": campaign_end.isoformat(),
        "synthetic_users_count": len(user_ids),
        "assignment": "pedidos.orden.creado_en dentro de [inicio_epoch, inicio_epoch + duracion_segundos]",
        "confirmed_order_operationalization": (
            "orden persistida en pedidos.orden; OrdenService confirma el commit antes de invocar facturacion"
        ),
        "charge_limitation": (
            "no existe ledger de cobros; se verifico factura e importe, no una captura bancaria independiente"
        ),
        "cancel_compensation_limitation": (
            "no_verificado: los estados COMPLETADA/FALLIDA vivieron en TransactionObservationStore, "
            "buffer en memoria de 200 entradas, y se perdieron en los reinicios entre corridas"
        ),
        "saga_convergence_definition": (
            "ventas.factura_outbox.procesado_en - creado_en; mide convergencia factura-inventario del outbox, "
            "no reconstruye una compensacion de checkout cancelado"
        ),
        "raw_counts": {
            "orders": len(orders),
            "order_lines": len(order_lines),
            "invoice_lines": len(invoice_lines),
            "inventory_movements": len(movements),
            "negative_kardex_rows": len(negative_kardex),
            "negative_reservation_rows": len(negative_reservations),
            "unassigned_orders": unassigned_orders,
        },
        "totals": totals,
    }
    return raw_rows, oracle_rows, summary


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    args = parse_args()
    raw_rows, oracle_rows, summary = reconstruct(args)
    user_ids = load_user_ids(args.request_bank, args.user_ids_csv)
    write_csv(args.user_ids_csv, [{"usuario_id": user_id} for user_id in user_ids])
    write_csv(args.output, oracle_rows)

    oracle_by_key = {
        (str(row["fallo"]), str(row["coord"]), int(row["concurrencia"]), int(row["repeticion"])): row
        for row in oracle_rows
    }
    enriched = []
    oracle_extra_fields = [field for field in oracle_rows[0] if field not in {"fallo", "coord", "concurrencia", "repeticion"}]
    for raw in raw_rows:
        key = (raw["fallo"], raw["coord"], int(raw["concurrencia"]), int(raw["repeticion"]))
        merged: dict[str, Any] = dict(raw)
        source = oracle_by_key[key]
        for field in oracle_extra_fields:
            merged[field] = source[field]
        enriched.append(merged)
    write_csv(args.enriched_output, enriched)

    summary["outputs"] = {
        "usuarios_sinteticos_ids": str(args.user_ids_csv.relative_to(ROOT)).replace("\\", "/"),
        "oracle_por_corrida": str(args.output.relative_to(ROOT)).replace("\\", "/"),
        "experimento_enriquecido": str(args.enriched_output.relative_to(ROOT)).replace("\\", "/"),
    }
    summary["sha256"] = {
        args.raw_csv.name: hashlib.sha256(args.raw_csv.read_bytes()).hexdigest(),
        args.user_ids_csv.name: hashlib.sha256(args.user_ids_csv.read_bytes()).hexdigest(),
        args.output.name: hashlib.sha256(args.output.read_bytes()).hexdigest(),
        args.enriched_output.name: hashlib.sha256(args.enriched_output.read_bytes()).hexdigest(),
    }
    args.summary_output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
