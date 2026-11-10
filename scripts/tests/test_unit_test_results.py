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

    def test_error_only_suites_and_a_single_bad_suite_among_healthy_ones_fail(self):
        healthy = '<testsuite tests="1"><testcase name="success"/></testsuite>'
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
            root = Path(directory)
            files = [root / path / f"TEST-{suite}.xml" for path, suites in results.SUITES.items() for suite in suites]
            for file in files:
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(healthy)
            self.assertEqual(results.verify(root), 0)
            # An errors-only suite has zero failures but still must not pass.
            bad = '<testsuite tests="1" errors="1"><testcase name="broken"><error/></testcase></testsuite>'
            for body in (bad, '<testsuite tests="1"><testcase><skipped/></testcase></testsuite>', None):
                for file in files:
                    file.write_text(body) if body else file.unlink()
                    self.assertEqual(results.verify(root), 1, f"{file} should fail alone: {body}")
                    file.write_text(healthy)
            self.assertEqual(results.verify(root), 0)


if __name__ == "__main__":
    unittest.main()
