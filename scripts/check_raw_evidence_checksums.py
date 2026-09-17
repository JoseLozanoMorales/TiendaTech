#!/usr/bin/env python3
"""SHA-256 de evidencia cruda estructurada que ningun otro verificador cubre.

scripts/check_data_checksums.py cubre CSV/TSV de todo el repo, y
experiments/paso8/campaign_checksums.py cubre CSV/TSV/JSON/SVG dentro de las
campanas de paso8 (resultados-reales/*) y de tests/load/results. Fuera de eso,
quedaban sin ninguna suma de verificacion: las trazas HTTP y su JSON de
docs/experimentos/resultados/iso25010/<corrida>/trace/ (confirmado por mutation
testing: alterar trace/checkout.json no lo detectaba nada), los oraculos de
experiments/paso7/evidence/ (alterar oracle-2pc.json tampoco), los JSON/SVG de
experiments/paso8/resultados/ (distinto de resultados-reales/) y el resumen
docs/experimentos/resultados/resumen.json.

No duplica CSV/TSV ya cubiertos en otro lado: solo indexa .json, .headers,
.txt y .svg dentro de los directorios objetivo.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re

EXTENSIONS = {".json", ".headers", ".txt", ".svg"}
DEFAULT_MANIFEST = "docs/experimentos/resultados/checksums-evidencia-adicional.sha256"


def default_targets(root: Path) -> list[Path]:
    return [
        root / "docs/experimentos/resultados/iso25010/2026-09-04T08-44-12",
        root / "experiments/paso7/evidence",
        root / "experiments/paso8/resultados",
    ]


def default_single_files(root: Path) -> list[Path]:
    return [root / "docs/experimentos/resultados/resumen.json"]


def inventory(root: Path, targets: list[Path], single_files: list[Path]) -> dict[str, str]:
    entries: dict[str, str] = {}
    paths = list(single_files)
    for directory in targets:
        paths.extend(sorted(directory.rglob("*")))
    for path in paths:
        if not path.is_file() or (path not in single_files and path.suffix.lower() not in EXTENSIONS):
            continue
        if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
            raise ValueError(f"Enlace no permitido o fuera del arbol: {path}")
        raw = path.read_bytes().replace(b"\r\n", b"\n")
        entries[path.relative_to(root).as_posix()] = hashlib.sha256(raw).hexdigest()
    if not entries:
        raise ValueError("Sin evidencia adicional encontrada")
    return entries


def generate(root: Path, manifest: Path, targets: list[Path], single_files: list[Path]) -> int:
    entries = inventory(root, targets, single_files)
    manifest.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"{digest}  {name}\n" for name, digest in sorted(entries.items(), key=lambda kv: kv[1])]
    manifest.write_text("".join(lines), encoding="utf-8", newline="\n")
    return len(entries)


def verify(root: Path, manifest: Path, targets: list[Path], single_files: list[Path]) -> int:
    expected = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if not match or match[2] in expected:
            raise ValueError(f"Linea de manifiesto invalida o duplicada: {manifest}")
        expected[match[2]] = match[1]
    actual = inventory(root, targets, single_files)
    if expected != actual:
        raise ValueError(
            "Evidencia adicional sin suma, ausente o alterada: "
            f"{sorted(k for k in expected.keys() | actual.keys() if expected.get(k) != actual.get(k))}"
        )
    return len(actual)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Regeneracion explicita, nunca en CI")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = root / args.manifest
    targets = default_targets(root)
    single_files = default_single_files(root)
    try:
        count = (
            generate(root, manifest, targets, single_files)
            if args.write
            else verify(root, manifest, targets, single_files)
        )
    except (OSError, ValueError) as exc:
        print(f"ERROR: {exc}")
        return 1
    print(f"{'Generadas' if args.write else 'Verificadas'} {count} sumas de evidencia adicional")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
