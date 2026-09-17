import tempfile
import unittest
from pathlib import Path

from check_raw_evidence_checksums import generate, verify


class RawEvidenceChecksumTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "trace").mkdir(parents=True)
        (self.root / "trace/checkout.json").write_bytes(b'{"status": "ok"}')
        (self.root / "trace/checkout.headers").write_bytes(b"HTTP/1.1 200 OK\n")
        (self.root / "trace/environment.txt").write_bytes(b"docker: 24.0\n")
        (self.root / "trace/chart.svg").write_bytes(b"<svg></svg>")
        (self.root / "trace/ignorar.csv").write_bytes(b"a,b\n1,2\n")  # cubierto en otro lado, no aqui
        (self.root / "trace/ignorar.md").write_bytes(b"# nota\n")
        (self.root / "trace/captura.png").write_bytes(b"\x89PNG\r\n\x1a\nfalsopng")
        self.resumen = self.root / "resumen.json"
        self.resumen.write_bytes(b'{"total": 1}')
        self.manifest = self.root / "checksums-evidencia-adicional.sha256"
        self.targets = [self.root / "trace"]
        self.single_files = [self.resumen]

    def test_covers_json_headers_txt_svg_png_but_not_csv_or_md(self):
        count = generate(self.root, self.manifest, self.targets, self.single_files)
        self.assertEqual(count, 6)  # json + headers + txt + svg + png + resumen.json
        self.assertEqual(verify(self.root, self.manifest, self.targets, self.single_files), 6)
        contents = self.manifest.read_text(encoding="utf-8")
        self.assertNotIn("ignorar.csv", contents)
        self.assertNotIn("ignorar.md", contents)
        self.assertIn("captura.png", contents)

    def test_png_tampering_detected(self):
        generate(self.root, self.manifest, self.targets, self.single_files)
        (self.root / "trace/captura.png").write_bytes(b"\x89PNG\r\n\x1a\notropng")
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest, self.targets, self.single_files)

    def test_tampering_detected(self):
        generate(self.root, self.manifest, self.targets, self.single_files)
        (self.root / "trace/checkout.json").write_bytes(b'{"status": "fallo"}')
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest, self.targets, self.single_files)

    def test_missing_file_detected(self):
        generate(self.root, self.manifest, self.targets, self.single_files)
        self.resumen.unlink()
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest, self.targets, self.single_files)

    def test_new_untracked_file_detected(self):
        generate(self.root, self.manifest, self.targets, self.single_files)
        (self.root / "trace/nuevo.json").write_bytes(b"{}")
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest, self.targets, self.single_files)

    def test_windows_line_endings_equivalent(self):
        generate(self.root, self.manifest, self.targets, self.single_files)
        (self.root / "trace/environment.txt").write_bytes(b"docker: 24.0\r\n")
        self.assertEqual(verify(self.root, self.manifest, self.targets, self.single_files), 6)

    def test_symlink_rejected(self):
        generate(self.root, self.manifest, self.targets, self.single_files)
        outside = Path(tempfile.mkdtemp()) / "afuera.json"
        outside.write_bytes(b"{}")
        self.addCleanup(lambda: outside.unlink(missing_ok=True))
        link = self.root / "trace/enlace.json"
        try:
            link.symlink_to(outside)
        except OSError:
            self.skipTest("symlinks no soportados en este entorno")
        with self.assertRaises(ValueError):
            verify(self.root, self.manifest, self.targets, self.single_files)


if __name__ == "__main__":
    unittest.main()
