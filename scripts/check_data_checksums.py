"""SHA-256 de todos los CSV/TSV del índice Git, sobre bytes del árbol de trabajo.

Los archivos con atributo Git eol=lf se normalizan CRLF -> LF para representar
el contenido publicado, igual en Windows y Linux. No consulta datos ignorados.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import subprocess

DEFAULT_MANIFEST = "docs/experimentos/resultados/checksums-datos.sha256"


def inventory(root: Path) -> list[str]:
    raw = subprocess.check_output(["git", "-C", str(root), "ls-files", "-z"])
    return sorted({p for p in raw.decode("utf-8").split("\0")
                   if p.lower().endswith((".csv", ".tsv"))})


def digest(root: Path, name: str) -> str:
    path = root / name
    if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
        raise ValueError(f"Ruta fuera del árbol o enlace simbólico: {name}")
    raw = path.read_bytes()
    attrs = subprocess.check_output([
        "git", "-C", str(root), "check-attr", "-z", "eol", "--", name
    ]).decode().split("\0")
    if attrs[2] == "lf":
        raw = raw.replace(b"\r\n", b"\n")
    return hashlib.sha256(raw).hexdigest()


def generate(root: Path, manifest: Path) -> int:
    names = inventory(root)
    if not names:
        raise ValueError("Inventario vacío")
    lines = [f"{digest(root, name)}  {name}\n" for name in names]
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text("".join(lines), encoding="utf-8", newline="\n")
    return len(names)


def verify(root: Path, manifest: Path) -> int:
    entries = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if not match:
            raise ValueError("Línea de manifiesto inválida")
        checksum, name = match.groups()
        if name in entries:
            raise ValueError(f"Entrada duplicada: {name}")
        entries[name] = checksum
    names = set(inventory(root))
    if not names or names != set(entries):
        raise ValueError(f"Inventario distinto: sin suma={sorted(names-set(entries))}; "
                         f"sobrantes={sorted(set(entries)-names)}")
    failures = [name for name in sorted(names) if digest(root, name) != entries[name]]
    if failures:
        raise ValueError(f"Contenido modificado: {failures}")
    return len(names)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Regeneración explícita, nunca en CI")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        count = (generate if args.write else verify)(root, root / args.manifest)
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}")
        return 1
    print(f"{'Generadas' if args.write else 'Verificadas'} {count} sumas SHA-256 de datos tabulares")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
