import tempfile
import unittest
from pathlib import Path

from check_no_bom import find_bom_files


class BomDetectionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_detects_bom_and_ignores_clean_file(self):
        con_bom = self.root / "con_bom.csv"
        con_bom.write_bytes(b"\xef\xbb\xbfa,b\n1,2\n")
        sin_bom = self.root / "sin_bom.csv"
        sin_bom.write_bytes(b"a,b\n1,2\n")
        self.assertEqual(find_bom_files([con_bom, sin_bom]), [con_bom])

    def test_empty_file_not_flagged(self):
        vacio = self.root / "vacio.txt"
        vacio.write_bytes(b"")
        self.assertEqual(find_bom_files([vacio]), [])

    def test_short_file_not_falsely_flagged(self):
        corto = self.root / "corto.txt"
        corto.write_bytes(b"\xef\xbb")  # 2 bytes, BOM incompleto
        self.assertEqual(find_bom_files([corto]), [])

    def test_mutation_adding_bom_is_detected(self):
        mutable = self.root / "mutable.json"
        mutable.write_bytes(b'{"a":1}')
        self.assertEqual(find_bom_files([mutable]), [])
        mutable.write_bytes(b"\xef\xbb\xbf" + b'{"a":1}')
        self.assertEqual(find_bom_files([mutable]), [mutable])

    def test_missing_file_skipped_not_crashed(self):
        fantasma = self.root / "no_existe.csv"
        self.assertEqual(find_bom_files([fantasma]), [])

    def test_symlink_skipped(self):
        real = self.root / "real.txt"
        real.write_bytes(b"\xef\xbb\xbfhola")
        enlace = self.root / "enlace.txt"
        try:
            enlace.symlink_to(real)
        except OSError:
            self.skipTest("symlinks no soportados en este entorno")
        self.assertEqual(find_bom_files([enlace]), [])


if __name__ == "__main__":
    unittest.main()
