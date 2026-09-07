#!/usr/bin/env python3
"""Valida y resume el CSV oficial del experimento real del Paso 8."""

from __future__ import annotations

import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path


KEY = ("fallo", "coord", "concurrencia", "repeticion")
GROUP = ("fallo", "coord", "concurrencia")
METRICS = (
    "requests_total", "requests_fail", "tasa_error", "checkout_total",
    "checkout_fail", "checkout_confirmadas", "latencia_p50_ms",
    "latencia_p95_ms", "latencia_p99_ms", "throughput_rps",
    "locust_cpu_pct_media", "locust_cpu_pct_max", "locust_mem_mb_media",
    "locust_mem_mb_max", "duracion_segundos",
)


def number(value: str) -> float:
    return float(value) if value not in ("", None) else 0.0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", type=Path)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    with args.csv.open(newline="", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))

    keys = [tuple(row[k] for k in KEY) for row in rows]
    groups: dict[tuple[str, str, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        groups[tuple(row[k] for k in GROUP)].append(row)

    validation = {
        "filas": len(rows),
        "claves_unicas": len(set(keys)),
        "condiciones": len(groups),
        "condiciones_con_5_repeticiones": sum(len(v) == 5 for v in groups.values()),
        "requests_total_cero": sum(number(r["requests_total"]) <= 0 for r in rows),
        "concurrencia_no_alcanzada": sum(
            number(r["usuarios_spawneados"]) < number(r["usuarios_objetivo"]) for r in rows
        ),
        "parametros": sorted({
            (r["warmup_seconds"], r["measure_seconds"], r["delay_seconds"])
            for r in rows
        }),
    }
    validation["valido"] = (
        validation["filas"] == 120
        and validation["claves_unicas"] == 120
        and validation["condiciones"] == 24
        and validation["condiciones_con_5_repeticiones"] == 24
        and validation["requests_total_cero"] == 0
        and validation["concurrencia_no_alcanzada"] == 0
        and validation["parametros"] == [("60.0", "300.0", "5.0")]
    )

    args.output.mkdir(parents=True, exist_ok=True)
    summary_path = args.output / "resumen_por_condicion.csv"
    fields = [*GROUP, "repeticiones", *[f"{m}_mediana" for m in METRICS]]
    with summary_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=fields)
        writer.writeheader()
        for key in sorted(groups, key=lambda k: (k[0], k[1], int(k[2]))):
            sample = groups[key]
            result = dict(zip(GROUP, key))
            result["repeticiones"] = len(sample)
            for metric in METRICS:
                result[f"{metric}_mediana"] = round(
                    statistics.median(number(row[metric]) for row in sample), 6
                )
            writer.writerow(result)

    (args.output / "validacion.json").write_text(
        json.dumps(validation, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(validation, ensure_ascii=False, indent=2))
    return 0 if validation["valido"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
