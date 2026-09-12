#!/usr/bin/env python3
"""Valida que las evidencias citadas en el manuscrito de Entrega 4 existan
realmente en el arbol versionado, antes de aprobar la compilacion (E2, nivel 10).

Formatos verificados (existencia real vía objetos git, nunca el filesystem
local sin versionar):

  - \\evidencia{ruta}{etiqueta}
        Pinneada al commit fijo definido dentro de la propia macro
        (\\newcommand{\\evidencia}[2]{\\href{.../blob/<SHA>/#1}{#2}}).
  - \\href{https://github.com/<owner>/<repo>/blob/<SHA>/<ruta>}{...}
        Pinneada al <SHA> del propio enlace.
  - \\href{https://github.com/<owner>/<repo>/commit/<SHA>}
        Solo se exige que el commit exista y sea alcanzable (sin ruta).
  - Columna "evidencia" de docs/experimentos/resultados/iso25010.csv
        Ruta verificada contra HEAD (no pinneada a un commit especifico).

Excepciones documentadas (formatos reconocidos pero fuera de alcance; se
reportan siempre, nunca se omiten en silencio, y no hacen fallar la validacion):

  - \\href{.../issues/N#issuecomment-...}
        Enlace a una discusion de GitHub Issues, no a un archivo versionado.
  - \\href{.../actions/runs/<id>}
        Ejecucion de un workflow de GitHub Actions; no es un objeto git y
        puede expirar o requerir autenticacion, no es verificable por este
        script.
  - \\href{.../pull/<n>} o \\href{.../pulls/<n>}
        Enlace a un Pull Request, no a un archivo versionado.

Cualquier otro enlace a github.com que no calce ninguno de los patrones
anteriores se reporta como FORMATO NO RECONOCIDO y se trata como fallo: no
hay excepcion implicita para un formato nuevo no revisado.

Uso:
    python scripts/validate_evidence_refs.py
    python scripts/validate_evidence_refs.py --main-tex ruta/a/otro.tex --iso-matrix ruta/a/otro.csv

Requiere que el repositorio tenga el historial necesario para resolver los
commits citados (un clon superficial --depth=1 hara que existencia de
commits antiguos falle incorrectamente; en CI usar fetch-depth: 0).
"""
from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MAIN_TEX = "docs/entrega4/PFC4.tex"
DEFAULT_ISO_MATRIX = "docs/experimentos/resultados/iso25010.csv"

INPUT_RE = re.compile(r"\\(?:input|include)\{([^}]+)\}")
EVIDENCIA_DEF_RE = re.compile(
    r"\\newcommand\{\\evidencia\}\[2\]\{\\href\{https://github\.com/[^/]+/[^/]+/blob/([0-9a-fA-F]{7,40})/#1\}"
)
EVIDENCIA_CALL_RE = re.compile(r"\\evidencia\{([^}]*)\}\{[^}]*\}")
GITHUB_LINK_RE = re.compile(r"https://github\.com/([^/\s\}]+)/([^/\s\}]+)/([a-zA-Z]+)/([^\s\}]+)")

EXCEPTION_KINDS = {
    "issues": "enlace a discusion de GitHub Issues, no a un archivo versionado",
    "pull": "enlace a un Pull Request, no a un archivo versionado",
    "pulls": "enlace a un Pull Request, no a un archivo versionado",
}


class Finding:
    __slots__ = ("source", "kind", "ref", "ok", "detail")

    def __init__(self, source: str, kind: str, ref: str, ok: bool | None, detail: str):
        self.source = source
        self.kind = kind
        self.ref = ref
        self.ok = ok
        self.detail = detail


def run_git(repo_root: Path, args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(["git", "-C", str(repo_root), *args], capture_output=True, text=True)


def object_exists(repo_root: Path, sha: str, path: str) -> tuple[bool, str]:
    result = run_git(repo_root, ["cat-file", "-e", f"{sha}:{path}"])
    return result.returncode == 0, result.stderr.strip()


def commit_exists(repo_root: Path, sha: str) -> tuple[bool, str]:
    result = run_git(repo_root, ["cat-file", "-e", f"{sha}^{{commit}}"])
    return result.returncode == 0, result.stderr.strip()


def discover_tex_files(repo_root: Path, main_tex: str) -> list[str]:
    """Sigue \\input/\\include desde main_tex; devuelve rutas relativas a repo_root."""
    seen: list[str] = []
    queue = [main_tex]
    base_dir = (repo_root / main_tex).parent
    while queue:
        rel = queue.pop(0)
        if rel in seen:
            continue
        seen.append(rel)
        path = repo_root / rel
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        for inc in INPUT_RE.findall(text):
            inc_path = inc if inc.endswith(".tex") else inc + ".tex"
            rel_inc = str((base_dir / inc_path).resolve().relative_to(repo_root.resolve())).replace("\\", "/")
            if rel_inc not in seen and rel_inc not in queue:
                queue.append(rel_inc)
    return seen


def evidencia_pinned_commit(main_tex_text: str) -> str | None:
    m = EVIDENCIA_DEF_RE.search(main_tex_text)
    return m.group(1) if m else None


def check_tex_file(repo_root: Path, rel_path: str, evidencia_sha: str | None) -> list[Finding]:
    findings: list[Finding] = []
    text = (repo_root / rel_path).read_text(encoding="utf-8")

    for ruta in EVIDENCIA_CALL_RE.findall(text):
        if evidencia_sha is None:
            findings.append(Finding(rel_path, "evidencia-macro", ruta, False,
                                     "se uso \\evidencia{} pero no se pudo resolver el commit fijado por la macro"))
            continue
        ok, err = object_exists(repo_root, evidencia_sha, ruta)
        findings.append(Finding(rel_path, "evidencia-macro", f"{evidencia_sha[:7]}:{ruta}", ok, err))

    for owner, repo, kind, rest in GITHUB_LINK_RE.findall(text):
        kind_lc = kind.lower()
        if kind_lc == "blob":
            if "/" not in rest:
                findings.append(Finding(rel_path, "href-blob-malformado", rest, False,
                                         "no se pudo separar el SHA de la ruta en el enlace blob"))
                continue
            sha, ruta = rest.split("/", 1)
            ruta = re.split(r"\\?#", ruta)[0]  # GitHub admite rangos de linea (#L7-L33 o \#L7-L33 escapado)
            if ruta in ("", "#1"):
                continue  # plantilla de la propia definicion de \evidencia, no una cita real
            ok, err = object_exists(repo_root, sha, ruta)
            findings.append(Finding(rel_path, "href-blob", f"{sha[:7]}:{ruta}", ok, err))
        elif kind_lc == "commit":
            sha = rest.split("#")[0]
            ok, err = commit_exists(repo_root, sha)
            findings.append(Finding(rel_path, "href-commit", sha[:7], ok, err))
        elif kind_lc in EXCEPTION_KINDS:
            findings.append(Finding(rel_path, f"excepcion-{kind_lc}", rest, None, EXCEPTION_KINDS[kind_lc]))
        elif kind_lc == "actions" and rest.startswith("runs/"):
            findings.append(Finding(rel_path, "excepcion-actions-run", rest, None,
                                     "ejecucion de GitHub Actions, no verificable via objetos git"))
        else:
            findings.append(Finding(rel_path, "FORMATO-NO-RECONOCIDO", f"{kind}/{rest}", False,
                                     "patron de enlace github.com no contemplado por este validador"))

    return findings


def check_iso_matrix(repo_root: Path, rel_path: str) -> list[Finding]:
    findings: list[Finding] = []
    path = repo_root / rel_path
    if not path.exists():
        return [Finding(rel_path, "csv-matriz", "(archivo)", False, "la matriz ISO citada no existe")]
    with path.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            ruta = (row.get("evidencia") or "").strip()
            if not ruta:
                continue
            ok, err = object_exists(repo_root, "HEAD", ruta)
            findings.append(Finding(rel_path, "csv-evidencia", ruta, ok, err))
    return findings


def run_validation(repo_root: Path, main_tex: str, iso_matrix: str) -> list[Finding]:
    tex_files = discover_tex_files(repo_root, main_tex)
    main_text = (repo_root / main_tex).read_text(encoding="utf-8")
    evidencia_sha = evidencia_pinned_commit(main_text)

    all_findings: list[Finding] = []
    for rel in tex_files:
        all_findings.extend(check_tex_file(repo_root, rel, evidencia_sha))
    if iso_matrix:
        all_findings.extend(check_iso_matrix(repo_root, iso_matrix))
    return all_findings, tex_files, evidencia_sha


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--main-tex", default=DEFAULT_MAIN_TEX,
                         help="archivo .tex principal (relativo a la raiz del repo)")
    parser.add_argument("--iso-matrix", default=DEFAULT_ISO_MATRIX,
                         help="CSV de la matriz ISO/IEC 25010 a validar (vacio para omitir)")
    parser.add_argument("--repo-root", default=str(REPO_ROOT))
    args = parser.parse_args(argv)

    repo_root = Path(args.repo_root).resolve()
    all_findings, tex_files, evidencia_sha = run_validation(repo_root, args.main_tex, args.iso_matrix)

    fails = [f for f in all_findings if f.ok is False]
    exceptions = [f for f in all_findings if f.ok is None]
    oks = [f for f in all_findings if f.ok is True]

    print(f"Archivos .tex inspeccionados ({len(tex_files)}): {', '.join(tex_files)}")
    print(f"Macro \\evidencia pinneada a commit: {evidencia_sha or '(no encontrada)'}")
    print()
    print(f"Referencias verificadas OK: {len(oks)}")
    print(f"Excepciones documentadas (fuera de alcance, no verificables via git): {len(exceptions)}")
    for f in exceptions:
        print(f"  [EXCEPCION] {f.source} :: {f.kind} :: {f.ref} -- {f.detail}")
    print(f"Fallos: {len(fails)}")
    for f in fails:
        print(f"  [FALLO] {f.source} :: {f.kind} :: {f.ref} -- {f.detail}")

    if fails:
        print(f"\nvalidate_evidence_refs: {len(fails)} referencia(s) sin resolver. Revisar detalle arriba.")
        return 1
    print("\nvalidate_evidence_refs: todas las referencias activas resuelven correctamente.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
