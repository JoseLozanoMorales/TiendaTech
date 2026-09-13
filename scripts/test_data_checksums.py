import subprocess
import tempfile
import unittest
from pathlib import Path

from check_data_checksums import generate, verify


class DataChecksumTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.git("init", "-q")
        (self.root / ".gitattributes").write_text("*.csv text eol=lf\n")
        (self.root / "dato.csv").write_bytes(b"a,b\n1,2\n")
        self.git("add", ".")
        self.manifest = self.root / "datos.sha256"
        generate(self.root, self.manifest)

    def git(self, *args):
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True)

    def test_cambio_de_valor_falla(self):
        (self.root / "dato.csv").write_text("a,b\n1,3\n")
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest)

    def test_archivo_nuevo_sin_suma_falla(self):
        (self.root / "nuevo.tsv").write_text("a\tb\n")
        self.git("add", "nuevo.tsv")
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest)

    def test_archivo_eliminado_falla(self):
        (self.root / "dato.csv").unlink()
        with self.assertRaises(OSError):
            verify(self.root, self.manifest)

    def test_entrada_duplicada_falla(self):
        self.manifest.write_text(self.manifest.read_text() * 2)
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest)

    def test_checkout_windows_equivale_a_lf(self):
        (self.root / "dato.csv").write_bytes(b"a,b\r\n1,2\r\n")
        self.assertEqual(verify(self.root, self.manifest), 1)


if __name__ == "__main__":
    unittest.main()
