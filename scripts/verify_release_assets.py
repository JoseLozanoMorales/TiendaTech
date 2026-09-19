#!/usr/bin/env python3
"""Verifica contra GitHub los assets pesados retirados del árbol (punto 37)."""
import argparse
import hashlib
import json
import os
import pathlib
import urllib.request

MANIFEST = pathlib.Path("release/release-assets-v4.0.0.json")


def validate(manifest, release):
    remote = {asset["name"]: asset for asset in release.get("assets", [])}
    errors = []
    for expected in manifest["assets"]:
        actual = remote.get(expected["name"])
        if actual is None:
            errors.append(f"ausente: {expected['name']}")
            continue
        if actual.get("size") != expected["bytes"]:
            errors.append(f"tamano: {expected['name']}")
        if actual.get("digest") != f"sha256:{expected['sha256']}":
            errors.append(f"digest: {expected['name']}")
    return errors


def fetch_json(url):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "TiendaTech-release-audit"}
    if token := os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def download_and_hash(url, destination):
    digest = hashlib.sha256()
    with urllib.request.urlopen(url, timeout=60) as response, destination.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=pathlib.Path, default=MANIFEST)
    parser.add_argument("--only", action="append", default=[])
    parser.add_argument("--download-dir", type=pathlib.Path)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    release = fetch_json(manifest["api_url"])
    errors = validate(manifest, release)
    if errors:
        raise SystemExit("ERROR: " + "; ".join(errors))
    selected = [a for a in manifest["assets"] if not args.only or a["name"] in args.only]
    unknown = set(args.only) - {a["name"] for a in selected}
    if unknown:
        raise SystemExit("ERROR: asset no inventariado: " + ", ".join(sorted(unknown)))
    if args.download_dir:
        args.download_dir.mkdir(parents=True, exist_ok=True)
        urls = {a["name"]: a["browser_download_url"] for a in release["assets"]}
        for asset in selected:
            target = args.download_dir / asset["name"]
            actual = download_and_hash(urls[asset["name"]], target)
            if actual != asset["sha256"]:
                raise SystemExit(f"ERROR: contenido: {asset['name']}")
            print(f"OK contenido {asset['name']} {actual}")
    print(f"OK: {len(manifest['assets'])} assets de {manifest['tag']} coinciden en nombre, tamano y SHA-256")


if __name__ == "__main__":
    main()
