import unittest
from verify_release_assets import validate


class ReleaseAssetsTest(unittest.TestCase):
    def setUp(self):
        self.manifest = {"assets": [{"name": "a.apk", "bytes": 3, "sha256": "abc"}]}
        self.release = {"assets": [{"name": "a.apk", "size": 3, "digest": "sha256:abc"}]}

    def test_accepts_exact_metadata(self):
        self.assertEqual([], validate(self.manifest, self.release))

    def test_rejects_missing_asset(self):
        self.assertIn("ausente: a.apk", validate(self.manifest, {"assets": []}))

    def test_rejects_size_and_digest_changes(self):
        changed = {"assets": [{"name": "a.apk", "size": 4, "digest": "sha256:def"}]}
        errors = validate(self.manifest, changed)
        self.assertIn("tamano: a.apk", errors)
        self.assertIn("digest: a.apk", errors)


if __name__ == "__main__":
    unittest.main()
