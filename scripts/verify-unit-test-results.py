#!/usr/bin/env python3
"""Fail when the MOB-1203 suites were missing, failed, or entirely skipped."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SUITES = {
    "ui-lib/build/test-results/testZcashmainnetStoreDebugUnitTest": [
        "co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCaseTest",
        "co.electriccoin.zcash.ui.common.usecase.SyncProgressPercentageTest",
    ],
    "preference-impl-android-lib/build/test-results/testDebugUnitTest": [
        "co.electriccoin.zcash.preference.EncryptedPreferenceRecoveryTest",
        "co.electriccoin.zcash.preference.EncryptedPreferenceQuarantineTest",
        "co.electriccoin.zcash.preference.PreferenceHolderTest",
        "co.electriccoin.zcash.preference.PreferenceProviderCacheTest",
    ],
}


def verify(root=ROOT):
    errors = []
    for directory, suites in SUITES.items():
        for suite in suites:
            path = root / directory / f"TEST-{suite}.xml"
            try:
                result = ET.parse(path).getroot()
                executed = [case for case in result.findall("testcase") if case.find("skipped") is None]
                if not executed or int(result.get("failures", 0)) or int(result.get("errors", 0)):
                    errors.append(f"No successful executed tests: {path}")
                else:
                    print(f"{suite}: {len(executed)} executed tests")
            except (OSError, ET.ParseError, ValueError) as error:
                errors.append(f"Invalid or missing result: {path}: {error}")
    for error in errors:
        print(error, file=sys.stderr)
    return int(bool(errors))


if __name__ == "__main__":
    sys.exit(verify())
