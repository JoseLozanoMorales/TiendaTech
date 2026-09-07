#!/usr/bin/env python3
"""Deriva comparaciones auditables de la campaña correctiva sin alterar el CSV crudo."""

from __future__ import annotations

import argparse
import csv
import itertools
import json
import statistics
from collections import defaultdict
from pathlib import Path


def num(row: dict[str, str], key: str) -> float:
    return float(row[key])


def u_stat(a: list[float], b: list[float]) -> float:
    return sum(x > y for x in a for y in b) + 0.5 * sum(
        x == y for x in a for y in b
    )


def permutation_p(a: list[float], b: list[float]) -> float:
    """Prueba bilateral exacta por permutación, válida también con empates."""
    pooled = a + b
    observed = abs(u_stat(a, b) - len(a) * len(b) / 2)
    extreme = total = 0
    indices = range(len(pooled))
    for left in itertools.combinations(indices, len(a)):
        left_set = set(left)
        aa = [pooled[i] for i in indices if i in left_set]
        bb = [pooled[i] for i in indices if i not in left_set]
        total += 1
        extreme += abs(u_stat(aa, bb) - len(a) * len(b) / 2) >= observed - 1e-12
    return extreme / total


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with args.csv.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    args.output.mkdir(parents=True, exist_ok=True)

    comparisons: list[dict[str, object]] = []
    alpha = 0.05 / 12
    for fault in ("none", "omission", "timing"):
        for concurrency in (50, 100, 200, 400):
            selected = [
                row
                for row in rows
                if row["fallo"] == fault and int(row["concurrencia"]) == concurrency
            ]
            by_coord = {
                coord: [row for row in selected if row["coord"] == coord]
                for coord in ("2pc", "saga")
            }
            p95 = {
                coord: [num(row, "latencia_p95_ms") for row in sample]
                for coord, sample in by_coord.items()
            }
            rates = {
                coord: [
                    num(row, "checkout_confirmadas") / num(row, "checkout_total")
                    for row in sample
                ]
                for coord, sample in by_coord.items()
            }
            u = u_stat(p95["2pc"], p95["saga"])
            p_value = permutation_p(p95["2pc"], p95["saga"])
            comparisons.append(
                {
                    "fallo": fault,
                    "concurrencia": concurrency,
                    "p95_2pc_mediana_ms": statistics.median(p95["2pc"]),
                    "p95_saga_mediana_ms": statistics.median(p95["saga"]),
                    "u_p95": u,
                    "a12_2pc_mayor_saga_p95": round(u / 25, 4),
                    "p_exacto_permutacion_p95": round(p_value, 9),
                    "significativo_bonferroni": p_value < alpha,
                    "confirmacion_2pc_mediana_pct": round(
                        100 * statistics.median(rates["2pc"]), 2
                    ),
                    "confirmacion_saga_mediana_pct": round(
                        100 * statistics.median(rates["saga"]), 2
                    ),
                }
            )
    write_csv(args.output / "comparacion_estrategias.csv", comparisons)

    grouped: dict[tuple[str, int], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        grouped[(row["coord"], int(row["concurrencia"]))].append(row)
    summaries: list[dict[str, object]] = []
    for (coord, concurrency), sample in sorted(grouped.items()):
        checkouts = sum(int(row["checkout_total"]) for row in sample)
        confirmed = sum(int(row["checkout_confirmadas"]) for row in sample)
        failed = sum(int(row["checkout_fail"]) for row in sample)
        summaries.append(
            {
                "coord": coord,
                "concurrencia": concurrency,
                "corridas": len(sample),
                "checkouts": checkouts,
                "confirmados": confirmed,
                "fallidos": failed,
                "confirmacion_pct": round(100 * confirmed / checkouts, 2),
                "p95_mediana_corridas_ms": statistics.median(
                    num(row, "latencia_p95_ms") for row in sample
                ),
            }
        )
    write_csv(args.output / "resumen_coord_concurrencia.csv", summaries)

    total_checkouts = sum(int(row["checkout_total"]) for row in rows)
    total_confirmed = sum(int(row["checkout_confirmadas"]) for row in rows)
    report = {
        "csv_fuente": args.csv.as_posix(),
        "filas": len(rows),
        "checkouts": total_checkouts,
        "confirmados": total_confirmed,
        "fallidos": sum(int(row["checkout_fail"]) for row in rows),
        "confirmacion_pct": round(100 * total_confirmed / total_checkouts, 2),
        "comparaciones_p95": 12,
        "alpha_bonferroni": alpha,
        "comparaciones_significativas": sum(
            bool(item["significativo_bonferroni"]) for item in comparisons
        ),
    }
    (args.output / "resumen_comparativo.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
