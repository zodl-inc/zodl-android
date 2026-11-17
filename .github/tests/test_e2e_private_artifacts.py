"""Execute the workflow wrappers with secret-bearing fixtures, without a device."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows/e2e-smoke.yml"
SENTINEL = "fixture-secret-material-do-not-publish"


def preparation(name):
    lines = WORKFLOW.read_text().splitlines()
    start = lines.index("      - name: " + name)
    run = lines.index("        run: |", start)
    block = []
    for line in lines[run + 1:]:
        if line and not line.startswith("          "):
            break
        block.append(line)
    return textwrap.dedent("\n".join(block))


class PrivateArtifactsTests(unittest.TestCase):
    def test_success_and_failure_preserve_status_without_publishing_secrets(self):
        suites = [
            ("Speedrun", "run-speedrun-private.sh", "run-smoke-android.sh", (1, 2)),
            ("Keystone", "run-keystone-private.sh", "run-keystone-android.sh", (None,)),
        ]
        for suite, runner, script, phases in suites:
            for phase in phases:
                for status in (0, 23):
                    with self.subTest(suite=suite, phase=phase, status=status):
                        self.check_fixture(suite, runner, script, phase, status)

    def check_fixture(self, suite, runner, script, phase, status):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            scripts = root / "zodl-qa/scripts"
            scripts.mkdir(parents=True)
            binary = root / "bin"
            binary.mkdir()
            xvfb = binary / "xvfb-run"
            xvfb.write_text('#!/bin/bash\n[ "$1" = -a ] && shift\nexec "$@"\n')
            xvfb.chmod(0o700)
            stub = scripts / script
            stub.write_text('''#!/bin/bash
set -euo pipefail
"$FIXTURE_PYTHON" -c 'import os,stat; assert stat.S_IMODE(os.stat(os.environ["TMPDIR"]).st_mode) == 0o700'
runtime="${KEYSTONE_RUNTIME_DIR:-${ZODL_SPEEDRUN_RESULTS_DIR:-}}"
mkdir -p "$runtime/assets"
printf '%s' "$FIXTURE_SECRET" > "$runtime/assets/user1_secret.json"
# The in-run QR consumer must retain access until the suite exits.
test "$(cat "$runtime/assets/user1_secret.json")" = "$FIXTURE_SECRET"
printf '%s' "$TMPDIR" > "$FIXTURE_PROBE"
printf '%s\\n' "$FIXTURE_SECRET"
printf '%s\\n' "$FIXTURE_SECRET" >&2
if [ -z "${KEYSTONE_RUNTIME_DIR:-}" ] && [ "${ZODL_TEST_PHASE:-}" = 2 ]; then
  mkdir -p "$ZODL_E2E_METRICS_DIR"
  for name in report.json report.md report.html; do
    printf '{"flow_duration_s":12.5}' > "$ZODL_E2E_METRICS_DIR/$name"
  done
fi
exit "$FIXTURE_STATUS"
''')
            stub.chmod(0o700)
            published = root / "reports"
            env = dict(os.environ)
            for key in ("KEYSTONE_RUNTIME_DIR", "ZODL_SPEEDRUN_RESULTS_DIR"):
                env.pop(key, None)
            env.update(
                PATH=str(binary) + os.pathsep + env["PATH"],
                RUNNER_TEMP=str(root),
                ZODL_TEST_RESULTS_DIR=str(published),
                ZODL_E2E_METRICS_DIR=str(published / "metrics"),
                ZODL_TEST_PHASE=str(phase or ""),
                FIXTURE_PYTHON=sys.executable,
                FIXTURE_STATUS=str(status),
                FIXTURE_SECRET=SENTINEL,
                FIXTURE_PROBE=str(root / "private-path"),
            )
            subprocess.run(["bash", "-euo", "pipefail", "-c", preparation(
                "Prepare private " + suite + " runner"
            )], cwd=root, env=env, check=True, capture_output=True, timeout=10)
            result = subprocess.run(["bash", str(root / runner)], cwd=root,
                                    env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, status)
            self.assertNotIn(SENTINEL, result.stdout + result.stderr)
            private = Path((root / "private-path").read_text())
            self.assertTrue(private.is_relative_to(root))
            self.assertFalse(private.exists(), "owned raw data must be removed on exit")
            files = [p for p in published.rglob("*") if p.is_file()]
            self.assertTrue(files)
            self.assertTrue(all(SENTINEL not in p.read_text() for p in files))
            summary = (published / "summary.txt").read_text()
            self.assertIn("Exit code: " + str(status), summary)
            self.assertIn("Suite duration (s):", summary)
            if suite == "Speedrun" and phase == 2:
                self.assertEqual(len(list((published / "metrics").glob("report.*"))), 3)

    def test_keystone_upload_excludes_raw_simulator_and_maestro_directories(self):
        job = WORKFLOW.read_text().split("  e2e-keystone:\n", 1)[1]
        self.assertIn("path: ${{ runner.temp }}/keystone-safe-results/summary.txt", job)
        self.assertNotIn("~/.maestro/tests/", job)
        self.assertNotIn("/tmp/zodl-keystone-sim.", job)


if __name__ == "__main__":
    unittest.main()
