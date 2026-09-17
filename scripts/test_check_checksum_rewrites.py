import subprocess
import tempfile
import unittest
from pathlib import Path

from check_checksum_rewrites import check, default_manifests


def run(*args, cwd):
    subprocess.run(args, cwd=cwd, check=True, capture_output=True)


class ChecksumRewriteTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        run("git", "init", "-q", cwd=self.root)
        run("git", "config", "user.email", "test@example.com", cwd=self.root)
        run("git", "config", "user.name", "Test", cwd=self.root)

        manifest_dir = self.root / "docs/experimentos/resultados"
        manifest_dir.mkdir(parents=True)
        self.manifest = manifest_dir / "checksums-datos.sha256"
        self.manifest.write_text(
            "a" * 64 + "  archivo1.csv\n" + "b" * 64 + "  archivo2.csv\n",
            encoding="utf-8",
        )
        run("git", "add", ".", cwd=self.root)
        run("git", "commit", "-q", "-m", "commit base", cwd=self.root)

    def _commit(self, message: str) -> None:
        run("git", "add", ".", cwd=self.root)
        run("git", "commit", "-q", "-m", message, cwd=self.root)

    def test_no_change_passes(self):
        (self.root / "readme-extra.txt").write_text("nada que ver", encoding="utf-8")
        self._commit("cambio no relacionado")
        self.assertEqual(check(self.root, "HEAD^", "HEAD"), [])

    def test_new_entry_does_not_require_justification(self):
        self.manifest.write_text(
            self.manifest.read_text(encoding="utf-8") + "c" * 64 + "  archivo3.csv\n",
            encoding="utf-8",
        )
        self._commit("agregar archivo3 nuevo")
        self.assertEqual(check(self.root, "HEAD^", "HEAD"), [])

    def test_rewrite_without_justification_is_flagged(self):
        text = self.manifest.read_text(encoding="utf-8")
        text = text.replace("a" * 64, "d" * 64)
        self.manifest.write_text(text, encoding="utf-8")
        self._commit("cambio de suma sin decir por que")
        unjustified = check(self.root, "HEAD^", "HEAD")
        self.assertEqual(len(unjustified), 1)
        manifest_rel, name, old_hash, new_hash = unjustified[0]
        self.assertEqual(name, "archivo1.csv")
        self.assertEqual(old_hash, "a" * 64)
        self.assertEqual(new_hash, "d" * 64)

    def test_rewrite_with_justification_trailer_passes(self):
        text = self.manifest.read_text(encoding="utf-8")
        text = text.replace("a" * 64, "d" * 64)
        self.manifest.write_text(text, encoding="utf-8")
        mensaje = (
            "regenerar archivo1 con corrida real nueva\n\n"
            "Checksum-Rewrite: docs/experimentos/resultados/checksums-datos.sha256:archivo1.csv\n"
        )
        self._commit(mensaje)
        self.assertEqual(check(self.root, "HEAD^", "HEAD"), [])

    def test_justification_must_match_exact_file(self):
        # Justificar archivo2 no debe perdonar una reescritura de archivo1.
        text = self.manifest.read_text(encoding="utf-8")
        text = text.replace("a" * 64, "d" * 64)
        self.manifest.write_text(text, encoding="utf-8")
        mensaje = (
            "cambio\n\n"
            "Checksum-Rewrite: docs/experimentos/resultados/checksums-datos.sha256:archivo2.csv\n"
        )
        self._commit(mensaje)
        unjustified = check(self.root, "HEAD^", "HEAD")
        self.assertEqual(len(unjustified), 1)
        self.assertEqual(unjustified[0][1], "archivo1.csv")

    def test_deleted_entry_not_flagged_as_rewrite(self):
        text = self.manifest.read_text(encoding="utf-8")
        text = "\n".join(line for line in text.splitlines() if "archivo1" not in line) + "\n"
        self.manifest.write_text(text, encoding="utf-8")
        self._commit("quitar archivo1 del manifiesto")
        self.assertEqual(check(self.root, "HEAD^", "HEAD"), [])

    def test_new_manifest_no_history_to_compare(self):
        campaign_dir = self.root / "experiments/paso8/resultados-reales/campana-x"
        campaign_dir.mkdir(parents=True)
        (campaign_dir / "checksums.txt").write_text("e" * 64 + " *dato.csv\n", encoding="utf-8")
        self._commit("nueva campana con su propio manifiesto")
        manifests = default_manifests(self.root)
        self.assertEqual(check(self.root, "HEAD^", "HEAD", manifests), [])

    def test_multiple_rewrites_require_one_trailer_each(self):
        text = self.manifest.read_text(encoding="utf-8")
        text = text.replace("a" * 64, "d" * 64).replace("b" * 64, "e" * 64)
        self.manifest.write_text(text, encoding="utf-8")
        mensaje = (
            "reescribir dos sumas, justificar solo una\n\n"
            "Checksum-Rewrite: docs/experimentos/resultados/checksums-datos.sha256:archivo1.csv\n"
        )
        self._commit(mensaje)
        unjustified = check(self.root, "HEAD^", "HEAD")
        self.assertEqual(len(unjustified), 1)
        self.assertEqual(unjustified[0][1], "archivo2.csv")


if __name__ == "__main__":
    unittest.main()
