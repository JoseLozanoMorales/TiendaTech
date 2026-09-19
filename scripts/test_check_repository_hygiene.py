import unittest
from check_repository_hygiene import violations


class RepositoryHygieneTest(unittest.TestCase):
    def test_allows_gradle_wrapper(self):
        self.assertEqual([], violations(["Apps/mobile/gradle/wrapper/gradle-wrapper.jar"]))

    def test_rejects_each_blocked_type_anywhere(self):
        paths = ["x/a.apk", "x/a.mp4", "x/a.jar", "x/a.db"]
        self.assertEqual(sorted(paths), violations(paths))

    def test_ignores_source_and_documentation(self):
        self.assertEqual([], violations(["README.md", "app.py", "data.json"]))


if __name__ == "__main__":
    unittest.main()
