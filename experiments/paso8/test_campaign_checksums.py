import tempfile
import unittest
from pathlib import Path

from campaign_checksums import finish, verify, write


class CampaignChecksumsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'analisis').mkdir()
        self.raw = self.root / 'raw.csv'
        self.raw.write_bytes(b'value\n1\n')
        (self.root / 'analisis/derived.csv').write_bytes(b'value\n2\n')
        write(self.root)

    def test_raw_and_derived(self):
        self.assertEqual(verify(self.root), 2)

    def test_changed_data(self):
        self.raw.write_bytes(b'value\n3\n')
        with self.assertRaises(ValueError):
            verify(self.root)

    def test_missing_data(self):
        self.raw.unlink()
        with self.assertRaises(ValueError):
            verify(self.root)

    def test_new_derived_requires_sum(self):
        (self.root / 'analisis/new.tsv').write_bytes(b'value\n4\n')
        with self.assertRaises(ValueError):
            verify(self.root)
        finish(self.root / 'analisis')
        self.assertEqual(verify(self.root), 3)

    def test_duplicate_or_missing_manifest(self):
        manifest = self.root / 'checksums.txt'
        manifest.write_bytes(manifest.read_bytes() * 2)
        with self.assertRaises(ValueError):
            verify(self.root)
        manifest.unlink()
        with self.assertRaises(FileNotFoundError):
            verify(self.root)

    def test_windows_line_endings(self):
        self.raw.write_bytes(b'value\r\n1\r\n')
        self.assertEqual(verify(self.root), 2)

    def test_structured_json_covered(self):
        # Los veredictos del experimento (p. ej. validacion.json) y otros archivos
        # estructurados de la campaña deben quedar cubiertos, no solo lo tabular.
        (self.root / 'validacion.json').write_bytes(b'{"valido": true}')
        subdir = self.root / 'piloto-basal'
        subdir.mkdir()
        (subdir / 'piloto-saga.json').write_bytes(b'{"ok": true}')
        with self.assertRaises(ValueError):
            verify(self.root)
        write(self.root)
        self.assertEqual(verify(self.root), 4)

    def test_json_tampering_detected(self):
        (self.root / 'validacion.json').write_bytes(b'{"valido": true}')
        write(self.root)
        (self.root / 'validacion.json').write_bytes(b'{"valido": false}')
        with self.assertRaises(ValueError):
            verify(self.root)


if __name__ == '__main__':
    unittest.main()
