# Unit tests and translation checks (MOB-1203)

Two separate PR checks cover this work. The `check_translations` job validates
translation resources on a pinned Python. The `test_kotlin_modules` job runs
`:preference-impl-android-lib:testDebugUnitTest`, including the
encrypted-preference recovery/quarantine and preference-cache suites, and
publishes Gradle JUnit XML alongside the existing reports. The jobs are
separate so translation drift on the merge base never stops the unit tests
from reporting their own signal.

## Translation completeness (MOB-1263)

Run from the repository root:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_translations.py
```

The checker reads first-party app modules, respecting the explicit main resource
directories in `build.gradle.kts` (`setOf` or `listOf`) and standard source-set
resource overlays. Additive `res.srcDir`/`res.srcDirs` declarations fail closed;
convert them to the explicit replacing form. Test-only modules and vendored
dependencies are excluded. Missing Spanish resources, empty values, mismatched
formatting arguments, missing plural defaults and inconsistent array lengths are
defects. Explicit `translatable="false"` entries are excluded. Lint's
`tools:ignore="MissingTranslation"` marker is not honored: the baseline is the
only deferral mechanism this check recognizes. Overlays inherit main resources;
they do not need duplicate copies of translations that Android already inherits.
Singular plural forms may spell out their unique integer count while retaining
the other format arguments. Regional or qualified Spanish directories, including
`values-es-rMX` and `values-b+es+419`, fail with an explicit unsupported-configuration
error until locale fallback support is added. They cannot silently bypass validation.

The same script serves both app repositories. The canonical copy is
`scripts/check_translations.py` here; `Scripts/check_translations.py` in
`zodl-ios-private` stays byte-identical, as does `test_translations.py`. Sync
both repositories when changing either copy.

`scripts/translation-baseline.json` records existing debt by resource, key,
locale and variant. CI fails for additional defects and stale baseline entries.
It never generates or expands the baseline. After repairing existing resources:

```sh
python3 scripts/check_translations.py --prune-baseline
```

This command only removes entries and still fails on new defects. Review baseline
changes with the resource changes. CI also compares the baseline with the base
branch and fails when a pull request adds a `(key, locale, defect, variant)`
entry for an additional resource. The baseline only shrinks. Deferring new debt
requires an explicit change to the CI rule.

Baseline identity includes the resource file path. When a strings file moves,
the checker reports `NEW` entries at the new path and `RESOLVED` entries at the
old path. Re-key by hand: update the `resource` field of the affected entries to
the new path in the same commit. The growth guard counts resources separately
and permits each re-key only when a matching entry is removed from the old path.
Adding the same defect in a second module still fails. These checks do not judge
linguistic accuracy.

## Focused regression suites (MOB-1264 and MOB-1268)

Use JDK 21+, matching `.github/actions/setup-java`:

```sh
./gradlew :ui-lib:testZcashmainnetStoreDebugUnitTest \
  --tests '*SyncProgressPercentageTest' \
  --tests '*Zip321ParseUriValidationUseCaseTest' \
  --tests '*PrefillSendZip321AmountTest' \
  --tests '*MetadataRepositorySelectedAccountTest' \
  :preference-impl-android-lib:testDebugUnitTest
python3 scripts/verify-unit-test-results.py
```

The verifier rejects missing result XML, empty/all-skipped suites and failures.
It requires all eight suites, including the lossy-amount characterization and
the selected-account metadata regression.
The parser suite uses the shipped ZIP321 parser with only SDK address validation
mocked. It covers validation and complete multi-payment URI forwarding to the
software, Keystone and Ledger proposal pipelines. It performs no native signing,
network requests, or funded sends. The progress suite checks percentage boundaries
and restoring/resyncing/non-syncing behavior independently of banner thresholds.

For the full unit stack, run the two Gradle commands from the workflow's Test
step. Keep app tests here and device/E2E smoke tests in `zodl-qa`.

See [the follow-up roadmap](unit-test-roadmap.md) for proposed additional suites.

Repository merge rules are unchanged. These checks run in CI; this change does
not add a required-status-check rule. Verified on 2026-11-10: the `main` branch
ruleset has no required status checks, so neither `check_translations` nor
`test_kotlin_modules` blocks merge on its own. Revisit that ruleset decision
when promoting these checks to required.
