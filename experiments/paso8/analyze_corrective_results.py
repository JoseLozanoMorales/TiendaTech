#!/usr/bin/env python3
"""Analisis estadistico reproducible de la campana real correctiva del Paso 8."""

from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import random
import statistics
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Callable


PASO8 = Path(__file__).resolve().parent
ROOT = PASO8.parents[1]
sys.path.insert(0, str(PASO8))

from run_paso8 import mann_whitney_u, percentile, vargha_delaney_a12, wilson_interval  # noqa: E402


FAULT_ORDER = {"none": 0, "omission": 1, "timing": 2}
COORD_ORDER = {"2pc": 0, "saga": 1}
NO_VERIFICADO = "no_verificado"


def parse_args() -> argparse.Namespace:
    base = PASO8 / "resultados-reales" / "correctiva-20260905-final-v2"
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", type=Path, default=base / "analisis" / "experimento_real_crudo_enriquecido.csv")
    ap.add_argument("--output", type=Path, default=base / "analisis")
    ap.add_argument("--bootstrap-samples", type=int, default=20_000)
    return ap.parse_args()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as fh:
        return list(csv.DictReader(fh))


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def number(row: dict[str, str], field: str) -> float | None:
    value = row.get(field, "").strip()
    if not value or value in {"no_verificado", "no_aplica"}:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def bootstrap_median_ci(values: list[float], seed: int, samples: int) -> tuple[float, float]:
    if not values:
        raise ValueError("bootstrap sin valores")
    rng = random.Random(seed)
    medians = []
    for _ in range(samples):
        sample = [values[rng.randrange(len(values))] for _ in values]
        medians.append(float(statistics.median(sample)))
    return percentile(medians, 0.025), percentile(medians, 0.975)


def enrich_rates(rows: list[dict[str, str]]) -> None:
    for row in rows:
        total = int(row["checkout_total"])
        confirmed = int(row["checkout_confirmadas"])
        row["tasa_confirmacion_checkout"] = str(confirmed / total) if total else NO_VERIFICADO


def condition_key(row: dict[str, str]) -> tuple[str, int, str]:
    return row["coord"], int(row["concurrencia"]), row["fallo"]


def condition_sort(key: tuple[str, int, str]) -> tuple[int, int, int]:
    coord, concurrency, fault = key
    return FAULT_ORDER[fault], concurrency, COORD_ORDER[coord]


def summarize_conditions(rows: list[dict[str, str]], samples: int) -> list[dict[str, Any]]:
    metrics = [
        ("latencia_p95_ms", "ms"),
        ("throughput_rps", "requests/s"),
        ("tasa_confirmacion_checkout", "proporcion"),
        ("tasa_abortos_checkout", "proporcion"),
        ("inconsistencias", "ordenes_o_eventos"),
        ("tasa_inconsistencia", "proporcion"),
        ("convergencia_compensacion_ms", "ms"),
    ]
    grouped: dict[tuple[str, int, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        grouped[condition_key(row)].append(row)

    output = []
    for key in sorted(grouped, key=condition_sort):
        coord, concurrency, fault = key
        condition_rows = grouped[key]
        for metric_index, (metric, unit) in enumerate(metrics):
            values = [value for row in condition_rows if (value := number(row, metric)) is not None]
            if values:
                seed = 20260905 + metric_index * 10_000 + concurrency * 10 + FAULT_ORDER[fault] * 2 + COORD_ORDER[coord]
                low, high = bootstrap_median_ci(values, seed, samples)
                median: str | float = round(float(statistics.median(values)), 6)
                low_value: str | float = round(float(low), 6)
                high_value: str | float = round(float(high), 6)
                state = "calculado"
            else:
                median = low_value = high_value = NO_VERIFICADO
                state = NO_VERIFICADO
            output.append({
                "fallo": fault,
                "coord": coord,
                "concurrencia": concurrency,
                "repeticiones_total": len(condition_rows),
                "metrica": metric,
                "unidad": unit,
                "n_valido": len(values),
                "mediana": median,
                "ic95_inf": low_value,
                "ic95_sup": high_value,
                "metodo_ic95": f"bootstrap_percentil_{samples}" if values else NO_VERIFICADO,
                "estado": state,
            })
    return output


def proportion_intervals(rows: list[dict[str, str]]) -> list[dict[str, Any]]:
    grouped: dict[tuple[str, int, str], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        grouped[condition_key(row)].append(row)
    output = []
    for key in sorted(grouped, key=condition_sort):
        coord, concurrency, fault = key
        group = grouped[key]
        attempts = sum(int(row["checkout_total"]) for row in group)
        confirmed = sum(int(row["checkout_confirmadas"]) for row in group)
        aborted = sum(int(row["checkout_fail"]) for row in group)
        confirm_ci = wilson_interval(confirmed, attempts)
        abort_ci = wilson_interval(aborted, attempts)
        output.append({
            "fallo": fault,
            "coord": coord,
            "concurrencia": concurrency,
            "repeticiones": len(group),
            "checkout_total": attempts,
            "checkout_confirmadas": confirmed,
            "tasa_confirmacion": round(confirmed / attempts, 6) if attempts else NO_VERIFICADO,
            "tasa_confirmacion_ic95_inf": round(confirm_ci[0], 6) if attempts else NO_VERIFICADO,
            "tasa_confirmacion_ic95_sup": round(confirm_ci[1], 6) if attempts else NO_VERIFICADO,
            "checkout_abortados": aborted,
            "tasa_aborto": round(aborted / attempts, 6) if attempts else NO_VERIFICADO,
            "tasa_aborto_ic95_inf": round(abort_ci[0], 6) if attempts else NO_VERIFICADO,
            "tasa_aborto_ic95_sup": round(abort_ci[1], 6) if attempts else NO_VERIFICADO,
            "metodo": "Wilson score 95%",
        })
    return output


def comparisons(rows: list[dict[str, str]]) -> list[dict[str, Any]]:
    metrics = [
        "latencia_p95_ms",
        "throughput_rps",
        "tasa_confirmacion_checkout",
        "tasa_abortos_checkout",
        "inconsistencias",
        "tasa_inconsistencia",
    ]
    output = []
    comparisons_per_metric = 12
    threshold = 0.05 / comparisons_per_metric
    for metric in metrics:
        for fault in ("none", "omission", "timing"):
            for concurrency in (50, 100, 200, 400):
                a = [value for row in rows
                     if row["coord"] == "2pc" and row["fallo"] == fault
                     and int(row["concurrencia"]) == concurrency
                     and (value := number(row, metric)) is not None]
                b = [value for row in rows
                     if row["coord"] == "saga" and row["fallo"] == fault
                     and int(row["concurrencia"]) == concurrency
                     and (value := number(row, metric)) is not None]
                if a and b:
                    result = mann_whitney_u(a, b)
                    exact_p = result["p_exacto"]
                    p_for_decision = float(exact_p) if exact_p != "" else float(result["p_aprox"])
                    state = "calculado"
                    a12: str | float = vargha_delaney_a12(a, b)
                else:
                    result = {"u": "", "z_aprox": "", "p_aprox": "", "p_exacto": ""}
                    p_for_decision = 1.0
                    state = NO_VERIFICADO
                    a12 = NO_VERIFICADO
                output.append({
                    "fallo": fault,
                    "concurrencia": concurrency,
                    "metrica": metric,
                    "grupo_a": "2pc",
                    "grupo_b": "saga",
                    "n_a": len(a),
                    "n_b": len(b),
                    **result,
                    "p_usado_bilateral": round(p_for_decision, 9) if state == "calculado" else NO_VERIFICADO,
                    "a12_2pc_mayor_saga": a12,
                    "comparaciones_por_metrica": comparisons_per_metric,
                    "umbral_bonferroni": round(threshold, 9),
                    "significativo_0_05": state == "calculado" and p_for_decision < 0.05,
                    "significativo_bonferroni": state == "calculado" and p_for_decision < threshold,
                    "estado": state,
                })
    return output


def write_boxplot_svg(
    path: Path,
    rows: list[dict[str, str]],
    metric: str,
    title: str,
    value_format: Callable[[float], str],
) -> None:
    boxes = []
    for fault in ("none", "omission", "timing"):
        for concurrency in (50, 100, 200, 400):
            for coord in ("2pc", "saga"):
                values = [value for row in rows
                          if row["fallo"] == fault and row["coord"] == coord
                          and int(row["concurrencia"]) == concurrency
                          and (value := number(row, metric)) is not None]
                if values:
                    boxes.append({
                        "label": f"{fault}/c{concurrency}/{coord}",
                        "coord": coord,
                        "low": min(values),
                        "q1": percentile(values, 0.25),
                        "median": percentile(values, 0.5),
                        "q3": percentile(values, 0.75),
                        "high": max(values),
                    })

    width, height = 1900, 700
    left, right, top, bottom = 95, 35, 75, 190
    values = [float(box[field]) for box in boxes for field in ("low", "q1", "median", "q3", "high")]
    ymin = min(values) if values else 0.0
    ymax = max(values) if values else 1.0
    if metric in {"tasa_confirmacion_checkout", "tasa_abortos_checkout", "tasa_inconsistencia"}:
        ymin = min(0.0, ymin)
        ymax = max(1.0, ymax)
    span = ymax - ymin or 1.0

    def y(value: float) -> float:
        return top + (ymax - value) / span * (height - top - bottom)

    plot_width = width - left - right
    step = plot_width / max(1, len(boxes))
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">']
    parts.append('<rect width="100%" height="100%" fill="#ffffff"/>')
    parts.append(f'<text x="{width/2}" y="38" text-anchor="middle" font-family="Arial" font-size="24" font-weight="bold">{html.escape(title)}</text>')
    for tick in range(6):
        value = ymin + span * tick / 5
        yy = y(value)
        parts.append(f'<line x1="{left}" y1="{yy:.2f}" x2="{width-right}" y2="{yy:.2f}" stroke="#e5e7eb"/>')
        parts.append(f'<text x="{left-10}" y="{yy+4:.2f}" text-anchor="end" font-family="Arial" font-size="12" fill="#374151">{html.escape(value_format(value))}</text>')
    parts.append(f'<line x1="{left}" y1="{top}" x2="{left}" y2="{height-bottom}" stroke="#111827"/>')
    parts.append(f'<line x1="{left}" y1="{height-bottom}" x2="{width-right}" y2="{height-bottom}" stroke="#111827"/>')
    for index, box in enumerate(boxes):
        x = left + step * (index + 0.5)
        box_width = min(42.0, step * 0.62)
        color = "#4e79a7" if box["coord"] == "2pc" else "#f28e2b"
        parts.append(f'<line x1="{x:.2f}" y1="{y(float(box["low"])):.2f}" x2="{x:.2f}" y2="{y(float(box["high"])):.2f}" stroke="#374151"/>')
        parts.append(f'<line x1="{x-box_width/3:.2f}" y1="{y(float(box["low"])):.2f}" x2="{x+box_width/3:.2f}" y2="{y(float(box["low"])):.2f}" stroke="#374151"/>')
        parts.append(f'<line x1="{x-box_width/3:.2f}" y1="{y(float(box["high"])):.2f}" x2="{x+box_width/3:.2f}" y2="{y(float(box["high"])):.2f}" stroke="#374151"/>')
        parts.append(f'<rect x="{x-box_width/2:.2f}" y="{y(float(box["q3"])):.2f}" width="{box_width:.2f}" height="{max(1.0, y(float(box["q1"]))-y(float(box["q3"]))):.2f}" fill="{color}" fill-opacity="0.65" stroke="{color}"/>')
        parts.append(f'<line x1="{x-box_width/2:.2f}" y1="{y(float(box["median"])):.2f}" x2="{x+box_width/2:.2f}" y2="{y(float(box["median"])):.2f}" stroke="#111827" stroke-width="3"/>')
        parts.append(f'<text x="{x:.2f}" y="{height-bottom+18}" transform="rotate(55 {x:.2f} {height-bottom+18})" text-anchor="start" font-family="Arial" font-size="11">{html.escape(str(box["label"]))}</text>')
    parts.append(f'<rect x="{width-250}" y="18" width="14" height="14" fill="#4e79a7"/><text x="{width-230}" y="30" font-family="Arial" font-size="13">2PC</text>')
    parts.append(f'<rect x="{width-170}" y="18" width="14" height="14" fill="#f28e2b"/><text x="{width-150}" y="30" font-family="Arial" font-size="13">Saga</text>')
    parts.append('</svg>')
    path.write_text("\n".join(parts) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    rows = read_csv(args.input)
    if len(rows) != 120:
        raise SystemExit(f"se esperaban 120 corridas, se leyeron {len(rows)}")
    enrich_rates(rows)

    summary_rows = summarize_conditions(rows, args.bootstrap_samples)
    proportion_rows = proportion_intervals(rows)
    comparison_rows = comparisons(rows)
    write_csv(args.output / "resumen_estadistico_ic95.csv", summary_rows)
    write_csv(args.output / "proporciones_binomiales_ic95.csv", proportion_rows)
    write_csv(args.output / "comparaciones_mann_whitney.csv", comparison_rows)

    charts = [
        ("latencia_p95_ms", "Boxplot de latencia p95 por condición", lambda x: f"{x:,.0f} ms"),
        ("throughput_rps", "Boxplot de throughput por condición", lambda x: f"{x:.2f}"),
        ("tasa_confirmacion_checkout", "Boxplot de tasa de confirmación por condición", lambda x: f"{100*x:.0f}%"),
        ("tasa_abortos_checkout", "Boxplot de tasa de aborto por condición", lambda x: f"{100*x:.0f}%"),
        ("tasa_inconsistencia", "Boxplot de tasa de inconsistencia verificable", lambda x: f"{100*x:.0f}%"),
    ]
    chart_paths = []
    for metric, title, formatter in charts:
        path = args.output / f"boxplot_{metric}.svg"
        write_boxplot_svg(path, rows, metric, title, formatter)
        chart_paths.append(path)

    output_paths = [
        args.output / "resumen_estadistico_ic95.csv",
        args.output / "proporciones_binomiales_ic95.csv",
        args.output / "comparaciones_mann_whitney.csv",
        *chart_paths,
    ]
    metadata = {
        "input": str(args.input.relative_to(ROOT)).replace("\\", "/"),
        "rows": len(rows),
        "conditions": len({condition_key(row) for row in rows}),
        "bootstrap_samples": args.bootstrap_samples,
        "median_ci": "bootstrap percentil 95%, semilla determinista por condicion y metrica",
        "proportion_ci": "Wilson score 95% sobre conteos agregados de cinco repeticiones",
        "mann_whitney": "bilateral; p exacto sin empates y aproximacion normal cuando hay empates",
        "effect_size": "A12 de Vargha-Delaney; probabilidad de que 2PC sea mayor que Saga",
        "multiplicity": "Bonferroni por metrica: 12 comparaciones, alfa 0.05/12",
        "outputs": [str(path.relative_to(ROOT)).replace("\\", "/") for path in output_paths],
        "sha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in output_paths},
    }
    metadata_path = args.output / "analisis_estadistico_metodologia.json"
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metadata, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
