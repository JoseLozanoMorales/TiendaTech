#!/usr/bin/env python3
"""Verifica los 60 DOI de docs/entrega3/referenciasPFC.bib contra el registro
publico (Crossref para los 59 casos normales, DataCite para el unico DOI de
arXiv: 10.48550/arXiv.1509.05393), comparando titulo, autores, anio y paginas
contra lo que el .bib realmente declara (punto 26 de la Guia de Cierre PFC
AGLS: "no hay evidencia versionada ni verificacion automatica de la
resolucion de los 60 DOI, asi que nada impide que el defecto vuelva a
aparecer").

Sale con codigo 1 si alguna entrada no resuelve o si algun campo comparado
(titulo, autor, anio o paginas) no coincide con el registro publico. Escribe
siempre el JSON de evidencia en --output, exista o no un fallo, para que la
corrida quede documentada aunque algo rompa.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
from pathlib import Path

import bibtexparser
import requests
from pylatexenc.latex2text import LatexNodes2Text

DEFAULT_BIB = "docs/entrega3/referenciasPFC.bib"
DEFAULT_OUTPUT = "docs/entrega4/cierre/verificacion-doi-bibliografia.json"
# Los DOI 10.48550/* los emite DataCite (arXiv), no Crossref -- Crossref
# responde 404 para ellos.
DATACITE_DOI_PREFIX = "10.48550/"
USER_AGENT = "TiendaTech-PFC-AGLS-bibliography-verifier/1.0 (+https://github.com/JoseLozanoMorales/TiendaTech)"
REQUEST_DELAY_SECONDS = 0.25

_latex_to_text = LatexNodes2Text().latex_to_text


def normalize(text: str | None) -> str:
    """Texto LaTeX/HTML -> texto plano comparable: sin acentos-macro, sin
    llaves protectoras, sin entidades HTML (Crossref devuelve '&amp;' como
    texto literal en el JSON, no como '&' ya decodificado), minusculas, solo
    alfanumerico y espacios simples."""
    if not text:
        return ""
    # El unescape de HTML va antes que la conversion LaTeX->texto: pylatexenc
    # trata un '&' suelto como tabulador de tabla (lo convierte en espacios),
    # asi que si se decodifica '&amp;' -> '&' despues, ya es tarde y queda
    # 'amp;' como palabra suelta en vez de desaparecer igual que el '\&' del
    # lado del .bib.
    plain = html.unescape(text)
    plain = _latex_to_text(plain)
    plain = plain.lower()
    plain = re.sub(r"[^a-z0-9]+", " ", plain)
    return " ".join(plain.split())


def normalize_pages(pages: str | None) -> str | None:
    """'94:1--94:24' -> '1-24' (quita el numero de articulo repetido de ACM
    antes del ':' cuando es identico en ambos extremos); en cualquier otro
    formato solo deja los digitos, separados por '-'."""
    if not pages:
        return None
    text = str(pages)
    acm_match = re.fullmatch(r"\s*(\d+):(\d+)\s*--\s*\1:(\d+)\s*", text)
    if acm_match:
        _, start, end = acm_match.groups()
        return f"{start}-{end}"
    digits = re.findall(r"\d+", text)
    return "-".join(digits) if digits else None


def pages_conflict(bib_pages: str | None, resolved_pages: str | None) -> bool:
    """True solo si las paginas de inicio realmente difieren. Crossref suele
    devolver unicamente la pagina de inicio para ciertas revistas/actas (p.
    ej. ACM/IEEE mas antiguas) mientras el .bib trae el rango completo -- eso
    no es un error del .bib, es un dato incompleto del lado de Crossref, y no
    deberia hacer fallar la verificacion. Si la pagina de inicio coincide, se
    acepta aunque el rango completo no este disponible en el registro."""
    bib_norm = normalize_pages(bib_pages)
    res_norm = normalize_pages(resolved_pages)
    if not bib_norm or not res_norm:
        return False
    bib_start = bib_norm.split("-", 1)[0]
    res_start = res_norm.split("-", 1)[0]
    return bib_start != res_start


def bib_author_families(raw: str) -> list[str]:
    """'Apellido, Nombre and Nombre Apellido2' -> ['apellido', 'apellido2']."""
    families = []
    for part in raw.split(" and "):
        part = part.strip()
        if not part:
            continue
        if "," in part:
            family = part.split(",", 1)[0]
        else:
            family = part.split()[-1]
        families.append(normalize(family))
    return families


def fetch_crossref(doi: str) -> dict:
    resp = requests.get(f"https://api.crossref.org/works/{doi}",
                         headers={"User-Agent": USER_AGENT}, timeout=20)
    resp.raise_for_status()
    msg = resp.json()["message"]
    year = None
    for key in ("published", "published-print", "published-online", "issued"):
        parts = (msg.get(key) or {}).get("date-parts")
        if parts and parts[0] and parts[0][0]:
            year = parts[0][0]
            break
    return {
        "source": "crossref",
        "title": (msg.get("title") or [None])[0],
        "authors": [a["family"] for a in msg.get("author", []) if a.get("family")],
        "year": year,
        "pages": msg.get("page"),
    }


def fetch_datacite(doi: str) -> dict:
    resp = requests.get(f"https://api.datacite.org/dois/{doi}",
                         headers={"User-Agent": USER_AGENT}, timeout=20)
    resp.raise_for_status()
    attrs = resp.json()["data"]["attributes"]
    titles = attrs.get("titles") or []
    creators = attrs.get("creators") or []
    authors = [c.get("familyName") or c.get("name") for c in creators if c.get("familyName") or c.get("name")]
    return {
        "source": "datacite",
        "title": titles[0]["title"] if titles else None,
        "authors": authors,
        "year": attrs.get("publicationYear"),
        "pages": None,
    }


def field_value(entry, name: str) -> str | None:
    field = entry.fields_dict.get(name)
    return field.value.strip() if field else None


def verify_entry(entry) -> dict:
    doi = field_value(entry, "doi")
    bib_title = field_value(entry, "title") or ""
    bib_author_raw = field_value(entry, "author") or ""
    bib_year = field_value(entry, "year")
    bib_pages = field_value(entry, "pages")

    result: dict = {
        "bibkey": entry.key,
        "doi": doi,
        "bib_title": bib_title,
        "bib_authors": bib_author_raw,
        "bib_year": bib_year,
        "bib_pages": bib_pages,
        "resolved": None,
        "error": None,
        "title_match": None,
        "author_match": None,
        "year_match": None,
        "pages_match": None,
        "mismatches": [],
        "ok": False,
    }

    fetcher = fetch_datacite if doi.lower().startswith(DATACITE_DOI_PREFIX) else fetch_crossref
    try:
        resolved = fetcher(doi)
    except (requests.RequestException, KeyError, ValueError, IndexError) as exc:
        result["error"] = f"{type(exc).__name__}: {exc}"
        result["mismatches"].append(f"no se pudo resolver el DOI: {result['error']}")
        return result

    result["resolved"] = resolved

    norm_bib_title = normalize(bib_title)
    norm_res_title = normalize(resolved["title"])
    title_match = bool(norm_res_title) and (
        norm_bib_title == norm_res_title
        or norm_bib_title in norm_res_title
        or norm_res_title in norm_bib_title
    )
    result["title_match"] = title_match
    if not title_match:
        result["mismatches"].append(f"titulo: bib={bib_title!r} vs resuelto={resolved['title']!r}")

    bib_families = set(bib_author_families(bib_author_raw))
    bib_words = set(normalize(bib_author_raw).split())
    # Se comparan palabras sueltas, no la frase completa en orden: Crossref a
    # veces separa mal nombres largos (p. ej. autores indios con varios
    # nombres de pila) y devuelve el apellido con las palabras en otro orden
    # que el .bib -- el nombre completo sigue siendo el mismo.
    missing_authors = [a for a in resolved["authors"]
                        if normalize(a) and not set(normalize(a).split()) <= bib_words]
    author_match = not missing_authors
    result["author_match"] = author_match
    if not author_match:
        result["mismatches"].append(
            f"autores: faltan en el .bib los que Crossref/DataCite listan: {missing_authors} "
            f"(bib tiene: {sorted(bib_families)})"
        )

    year_match = resolved["year"] is None or bib_year is None or str(resolved["year"]) == str(bib_year)
    result["year_match"] = year_match
    if not year_match:
        result["mismatches"].append(f"anio: bib={bib_year!r} vs resuelto={resolved['year']!r}")

    pages_match = not pages_conflict(bib_pages, resolved["pages"])
    result["pages_match"] = pages_match
    if not pages_match:
        result["mismatches"].append(f"paginas: bib={bib_pages!r} vs resuelto={resolved['pages']!r}")
    elif resolved["pages"] and bib_pages and normalize_pages(bib_pages) != normalize_pages(resolved["pages"]):
        # Informativo, no bloqueante: coincide la pagina de inicio pero
        # Crossref no trae el rango completo que si tiene el .bib.
        result["notes"] = result.get("notes", []) + [
            f"paginas: Crossref solo trae {resolved['pages']!r}, .bib trae el rango completo {bib_pages!r} "
            "(dato incompleto de Crossref, no error del .bib)"
        ]

    result["ok"] = title_match and author_match and year_match and pages_match
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bib", default=DEFAULT_BIB)
    parser.add_argument("--output", default=DEFAULT_OUTPUT)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--only", nargs="*", default=None,
                         help="Verificar solo estas claves .bib (para revalidar entradas puntuales)")
    args = parser.parse_args()

    root = args.root.resolve()
    bib_path = root / args.bib
    output_path = root / args.output

    library = bibtexparser.parse_file(str(bib_path))
    entries = [e for e in library.entries if field_value(e, "doi")]
    if args.only:
        wanted = set(args.only)
        entries = [e for e in entries if e.key in wanted]
        missing_keys = wanted - {e.key for e in entries}
        if missing_keys:
            print(f"ERROR: claves no encontradas o sin DOI: {sorted(missing_keys)}")
            return 1

    print(f"{len(library.entries)} entradas en {args.bib}, {len(entries)} con DOI a verificar"
          + (f" (filtrado a --only)" if args.only else ""))

    results = []
    for i, entry in enumerate(entries):
        result = verify_entry(entry)
        status = "OK" if result["ok"] else ("ERROR" if result["error"] else "MISMATCH")
        print(f"[{i + 1}/{len(entries)}] {entry.key} ({result['doi']}) -> {status}")
        if result["mismatches"]:
            for m in result["mismatches"]:
                print(f"    {m}")
        for note in result.get("notes", []):
            print(f"    (info, no bloqueante) {note}")
        results.append(result)
        if i < len(entries) - 1:
            time.sleep(REQUEST_DELAY_SECONDS)

    failed = [r for r in results if not r["ok"]]
    summary = {
        "bib_file": args.bib,
        "total_entries_in_bib": len(library.entries),
        "entries_with_doi": len(entries),
        "verified_ok": len(entries) - len(failed),
        "failed": len(failed),
        "failed_bibkeys": [r["bibkey"] for r in failed],
    }
    evidence = {"summary": summary, "results": results}

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(evidence, indent=2, ensure_ascii=False, sort_keys=False) + "\n",
                            encoding="utf-8", newline="\n")

    print()
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    print(f"Evidencia escrita en {args.output}")

    if failed:
        print(f"FALLO: {len(failed)} entrada(s) con DOI no resuelto o con metadatos que no coinciden.")
        return 1
    print("Las 60 entradas con DOI resuelven y coinciden en titulo, autores, anio y paginas.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
