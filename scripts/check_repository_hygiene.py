#!/usr/bin/env python3
"""Impide reintroducir artefactos pesados retirados por el punto 37."""
import pathlib
import subprocess

BLOCKED = {".apk", ".mp4", ".jar", ".db"}
ALLOWED = {"Apps/mobile/gradle/wrapper/gradle-wrapper.jar"}


def violations(paths):
    return sorted(
        path for path in paths
        if pathlib.PurePosixPath(path).suffix.lower() in BLOCKED and path not in ALLOWED
    )


def main():
    output = subprocess.check_output(["git", "ls-files", "-z"])
    paths = [item.decode("utf-8") for item in output.split(b"\0") if item]
    found = violations(paths)
    if found:
        raise SystemExit("ERROR: artefactos pesados versionados:\n  " + "\n  ".join(found))
    print(f"Higiene OK: sin {', '.join(sorted(BLOCKED))} fuera de la excepción de Gradle")


if __name__ == "__main__":
    main()
