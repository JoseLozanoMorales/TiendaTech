"""Manifiestos de datos de campaña, con CSV/TSV canónicos LF (sin alterar archivos)."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re


def inventory(directory: Path) -> dict[str, str]:
    entries = {}
    # Datos de la sesión y análisis oficial; excluye reproducciones locales auxiliares.
    paths = list(directory.glob('*')) + list((directory / 'analisis').rglob('*'))
    for path in sorted(paths):
        if path.suffix.lower() not in {'.csv', '.tsv'}:
            continue
        if path.is_symlink() or not path.resolve().is_relative_to(directory.resolve()):
            raise ValueError(f'Enlace no permitido: {path}')
        raw = path.read_bytes().replace(b'\r\n', b'\n')
        entries[path.relative_to(directory).as_posix()] = hashlib.sha256(raw).hexdigest()
    if not entries:
        raise ValueError(f'Campaña sin datos: {directory}')
    return entries


def write(directory: Path) -> Path:
    entries = inventory(directory)
    target = directory / 'checksums.txt'
    target.write_text(''.join(f'{digest} *{name}\n' for name, digest in entries.items()),
                      encoding='utf-8', newline='\n')
    return target


def finish(output: Path) -> Path:
    """El análisis habitual vive en analisis/; salidas alternativas tienen manifiesto propio."""
    return write(output.parent if output.name == 'analisis' else output)


def verify(directory: Path) -> int:
    expected = {}
    for line in (directory / 'checksums.txt').read_text(encoding='utf-8').splitlines():
        match = re.fullmatch(r'([0-9a-f]{64}) [ *](.+)', line)
        if not match or match[2] in expected:
            raise ValueError(f'Manifiesto inválido o duplicado: {directory}')
        expected[match[2]] = match[1]
    actual = inventory(directory)
    if expected != actual:
        raise ValueError(f'Datos sin suma, ausentes o alterados en {directory}: '
                         f'{sorted(k for k in expected.keys() | actual.keys() if expected.get(k) != actual.get(k))}')
    return len(actual)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    campaigns = root / 'experiments/paso8/resultados-reales'
    directories = [args.directory] if args.directory else sorted(p for p in campaigns.iterdir() if p.is_dir()) + [root / 'tests/load/results']
    try:
        for directory in directories:
            if args.write:
                write(directory)
            print(f'{directory}: {verify(directory)} sumas verificadas')
    except (OSError, ValueError) as exc:
        print(f'ERROR: {exc}')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
