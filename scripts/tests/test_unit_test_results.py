import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("results", Path(__file__).resolve().parents[1] / "verify-unit-test-results.py")
results = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(results)


class UnitTestResultsTests(unittest.TestCase):
    def test_missing_empty_skipped_and_failing_results_fail(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
            root = Path(directory)
            self.assertEqual(results.verify(root), 1)
            for body in (
                '<testsuite tests="0"/>',
                '<testsuite tests="1"><testcase><skipped/></testcase></testsuite>',
                '<testsuite tests="1" failures="1"><testcase><failure/></testcase></testsuite>',
                '<testsuite tests="1"><testcase name="success"/></testsuite>',
            ):
                for path, suites in results.SUITES.items():
                    folder = root / path
                    folder.mkdir(parents=True, exist_ok=True)
                    for suite in suites:
                        (folder / f"TEST-{suite}.xml").write_text(body)
                self.assertEqual(results.verify(root), 0 if 'name="success"' in body else 1)


if __name__ == "__main__":
    unittest.main()
