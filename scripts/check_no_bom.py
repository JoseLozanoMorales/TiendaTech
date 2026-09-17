#!/usr/bin/env python3
"""Detecta BOM UTF-8 (EF BB BF) al inicio de archivos versionados.

Este repo ya sufrio dos veces la misma corrupcion: un CSV/JSON exportado
desde Windows con encoding 'utf-8-sig' queda con un BOM de 3 bytes al
inicio del archivo, lo que rompe el parseo con herramientas que asumen
utf-8 puro (ver docs/evidencias/punto22-datos-crudos-preservados.md).
Ninguno de los otros guardianes de CI lo detecta: el guardian de fin de
linea busca bytes \r, y un BOM no contiene ninguno.

Escanea todos los archivos versionados con git ls-files (siguiendo lo que
git realmente tiene commiteado, no el working tree) y falla si alguno
empieza con el BOM. No requiere una lista de extensiones: leer los
primeros 3 bytes es barato y EF BB BF como inicio real de un formato
binario genuino es, en la practica, inexistente.
"""
from __future__ import annotations

import argparse
import subprocess
from pathlib import Path
from typing import Iterable

BOM = b"\xef\xbb\xbf"


def find_bom_files(paths: Iterable[Path]) -> list[Path]:
    offenders = []
    for path in paths:
        if not path.is_file() or path.is_symlink():
            continue
        with open(path, "rb") as fh:
            head = fh.read(3)
        if head == BOM:
            offenders.append(path)
    return offenders


def git_tracked_files(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=root,
        capture_output=True,
        check=True,
    )
    names = result.stdout.decode("utf-8").split("\0")
    return [root / name for name in names if name]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()

    tracked = git_tracked_files(root)
    offenders = find_bom_files(tracked)

    if offenders:
        print("ERROR: BOM UTF-8 detectado al inicio de:")
        for path in sorted(offenders):
            print(f"  {path.relative_to(root).as_posix()}")
        return 1

    print(f"Sin BOM UTF-8 en {len(tracked)} archivos versionados")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
