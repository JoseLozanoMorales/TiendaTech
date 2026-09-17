"""Manifiestos de datos de campaña, con CSV/TSV/JSON/SVG/MD canónicos LF (sin alterar archivos).

Incluye los archivos estructurados (JSON) de toda la carpeta de campaña, no solo los
tabulares: ahí viven los veredictos del experimento (por ejemplo validacion.json) y la
metodología con sus propias sumas de referencia, que antes quedaban sin verificar.

También incluye los SVG (por ejemplo los boxplot_*.svg de analyze_corrective_results.py):
antes no estaban en ningún manifiesto y una alteración de su contenido (por ejemplo un
color) pasaba desapercibida para todos los verificadores.

Y los .md: informe_final.md de cada campaña contiene el veredicto narrativo con las
cifras clave (órdenes persistidas, inconsistencias, resultado y limitación principal) --
es evidencia igual de crítica que validacion.json, solo que en prosa en vez de JSON."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re


def inventory(directory: Path) -> dict[str, str]:
    entries = {}
    # Toda la carpeta de campaña, incluidas subcarpetas como piloto-basal/ y
    # rampa-readiness/; excluye reproducciones locales auxiliares fuera del árbol.
    paths = directory.rglob('*')
    for path in sorted(paths):
        if path.suffix.lower() not in {'.csv', '.tsv', '.json', '.svg', '.md'} or not path.is_file():
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
