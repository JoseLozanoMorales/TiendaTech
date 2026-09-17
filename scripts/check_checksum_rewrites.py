#!/usr/bin/env python3
"""Detecta reescrituras silenciosas de entradas ya existentes en los
manifiestos de checksums, exigiendo justificacion explicita en el commit.

Motivacion (punto 42 del evaluador, "control anti-reescritura"): ningun
verificador de checksums de este repo detecta que una entrada YA EXISTENTE
cambie de hash en el mismo commit que altera el archivo que cubre -- todos
solo comprueban que el manifiesto de HOY coincida con los datos de HOY. Ya
paso una vez de verdad en este repo (commit 62d37e4: se corrompieron 4 CSV
con BOM y se reescribieron sus sumas en el mismo commit, sin que ningun
verificador lo bloqueara).

Este script compara cada manifiesto versionado contra su version en un
commit base (por defecto HEAD^, el padre). Si una entrada que ya existia
cambio de hash, exige que el mensaje del commit revisado (por defecto HEAD)
incluya una linea explicita por cada entrada reescrita:

    Checksum-Rewrite: <ruta-del-manifiesto>:<nombre-del-archivo>

Una entrada NUEVA (no existia en el commit base) no requiere nada -- esto
no bloquea agregar datos, solo reescribir en silencio los que ya estaban.
Tampoco bloquea eliminar una entrada (desaparecer no es "cambiar de hash").
"""
from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

LINE_PATTERN = re.compile(r"([0-9a-f]{64})\s+\*?(.+)")
TRAILER_PATTERN = re.compile(r"^Checksum-Rewrite:\s*(.+)$", re.MULTILINE)


def parse_manifest_text(text: str) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        match = LINE_PATTERN.fullmatch(line)
        if not match:
            continue
        checksum, name = match.groups()
        entries[name] = checksum
    return entries


def default_manifests(root: Path) -> list[Path]:
    manifests = [
        root / "docs/experimentos/resultados/checksums-datos.sha256",
        root / "docs/experimentos/resultados/checksums-evidencia-adicional.sha256",
        root / "tests/load/results/checksums.txt",
    ]
    campaigns_dir = root / "experiments/paso8/resultados-reales"
    if campaigns_dir.is_dir():
        manifests.extend(sorted(campaigns_dir.glob("*/checksums.txt")))
    return [m for m in manifests if m.is_file()]


def git_show(root: Path, ref: str, path: Path) -> str | None:
    rel = path.relative_to(root).as_posix()
    result = subprocess.run(
        ["git", "-C", str(root), "show", f"{ref}:{rel}"],
        capture_output=True,
    )
    if result.returncode != 0:
        return None  # no existia en ese commit (manifiesto nuevo)
    return result.stdout.decode("utf-8", errors="replace")


def commit_message(root: Path, ref: str) -> str:
    return subprocess.check_output(
        ["git", "-C", str(root), "log", "-1", "--format=%B", ref]
    ).decode("utf-8", errors="replace")


def find_rewrites(
    root: Path, base_ref: str, manifests: list[Path]
) -> list[tuple[str, str, str, str]]:
    """(manifiesto_relativo, nombre, hash_viejo, hash_nuevo) por cada entrada
    que existia en base_ref con un hash distinto al actual."""
    rewrites = []
    for manifest in manifests:
        rel = manifest.relative_to(root).as_posix()
        old_text = git_show(root, base_ref, manifest)
        if old_text is None:
            continue
        old_entries = parse_manifest_text(old_text)
        new_entries = parse_manifest_text(manifest.read_text(encoding="utf-8"))
        for name, old_hash in old_entries.items():
            new_hash = new_entries.get(name)
            if new_hash is not None and new_hash != old_hash:
                rewrites.append((rel, name, old_hash, new_hash))
    return rewrites


def justified_keys(message: str) -> set[str]:
    return {trailer.strip() for trailer in TRAILER_PATTERN.findall(message)}


def check(root: Path, base_ref: str, ref: str, manifests: list[Path] | None = None) -> list[tuple[str, str, str, str]]:
    """Devuelve la lista de reescrituras SIN justificar (vacia = todo bien)."""
    manifests = default_manifests(root) if manifests is None else manifests
    rewrites = find_rewrites(root, base_ref, manifests)
    if not rewrites:
        return []
    message = commit_message(root, ref)
    justified = justified_keys(message)
    return [r for r in rewrites if f"{r[0]}:{r[1]}" not in justified]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--base", default="HEAD^", help="Commit contra el que comparar (default: HEAD^)")
    parser.add_argument("--ref", default="HEAD", help="Commit cuyo mensaje se revisa (default: HEAD)")
    args = parser.parse_args()
    root = args.root.resolve()

    manifests = default_manifests(root)
    if not manifests:
        print("ERROR: no se encontro ningun manifiesto de checksums")
        return 1

    try:
        unjustified = check(root, args.base, args.ref, manifests)
    except subprocess.CalledProcessError as exc:
        print(f"ERROR: no se pudo comparar contra {args.base}: {exc}")
        return 1

    if unjustified:
        print("ERROR: entradas de checksum reescritas sin justificacion en el commit:")
        for manifest, name, old_hash, new_hash in unjustified:
            print(f"  {manifest}:{name}")
            print(f"    antes: {old_hash}")
            print(f"    ahora: {new_hash}")
        print()
        print("Si el cambio es legitimo (por ejemplo datos regenerados con una")
        print("corrida real nueva), agregar al mensaje del commit una linea por")
        print("cada entrada reescrita:")
        print("  Checksum-Rewrite: <manifiesto>:<archivo>")
        return 1

    print("Sin reescrituras de checksum sin justificar")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
