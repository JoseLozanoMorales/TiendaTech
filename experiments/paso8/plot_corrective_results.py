#!/usr/bin/env python3
"""Genera figuras del resumen correctivo; nunca modifica el CSV crudo."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("analysis", type=Path)
    args = parser.parse_args()
    with (args.analysis / "resumen_coord_concurrencia.csv").open(
        newline="", encoding="utf-8"
    ) as handle:
        rows = list(csv.DictReader(handle))
    colors = {"2pc": "#315b9a", "saga": "#d27828"}
    labels = {"2pc": "2PC", "saga": "Saga"}

    fig, axes = plt.subplots(1, 2, figsize=(10, 4.2))
    for coord in ("2pc", "saga"):
        sample = [row for row in rows if row["coord"] == coord]
        x = [int(row["concurrencia"]) for row in sample]
        axes[0].plot(
            x,
            [float(row["confirmacion_pct"]) for row in sample],
            marker="o",
            label=labels[coord],
            color=colors[coord],
        )
        axes[1].plot(
            x,
            [float(row["p95_mediana_corridas_ms"]) / 1000 for row in sample],
            marker="o",
            label=labels[coord],
            color=colors[coord],
        )
    axes[0].set(title="Checkouts confirmados", xlabel="Usuarios concurrentes", ylabel="Confirmación (%)")
    axes[1].set(title="Latencia p95", xlabel="Usuarios concurrentes", ylabel="Mediana entre corridas (s)")
    for axis in axes:
        axis.grid(alpha=0.25)
        axis.legend()
        axis.set_xticks([50, 100, 200, 400])
    fig.suptitle("Campaña correctiva: resultado agregado por concurrencia")
    fig.tight_layout()
    fig.savefig(args.analysis / "campana-correctiva-resumen.png", dpi=180, bbox_inches="tight")
    plt.close(fig)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
