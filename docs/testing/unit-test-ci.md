# Unit tests and translation checks (MOB-1203)

The `test_kotlin_modules` PR check validates translation resources before
building. It also runs `:preference-impl-android-lib:testDebugUnitTest`, including
the encrypted-preference recovery/quarantine and preference-cache suites, and
publishes Gradle JUnit XML alongside the existing reports.

## Translation completeness (MOB-1263)

Run from the repository root:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_translations.py
```

The checker reads first-party app modules, respecting the explicit main resource
directories in `build.gradle.kts` (`setOf` or `listOf`) and standard source-set
resource overlays. Test-only modules and vendored dependencies are excluded.
Missing Spanish resources, empty values, mismatched formatting arguments, missing
plural defaults and inconsistent array lengths are defects. Explicit
`translatable="false"` entries are excluded. Overlays inherit main resources;
they do not need duplicate copies of translations that Android already inherits.

`scripts/translation-baseline.json` records existing debt by resource, key,
locale and variant. CI fails for additional defects and stale baseline entries.
It never generates or expands the baseline. After repairing existing resources:

```sh
python3 scripts/check_translations.py --prune-baseline
```

This command only removes entries and still fails on new defects. Review baseline
changes with the resource changes; adding debt needs an explicit decision to
defer that translation work. These checks do not judge linguistic accuracy.

## Focused regression suites (MOB-1264 and MOB-1268)

Use JDK 21+, matching `.github/actions/setup-java`:

```sh
./gradlew :ui-lib:testZcashmainnetStoreDebugUnitTest \
  --tests '*SyncProgressPercentageTest' \
  --tests '*Zip321ParseUriValidationUseCaseTest' \
  :preference-impl-android-lib:testDebugUnitTest
python3 scripts/verify-unit-test-results.py
```

The verifier rejects missing result XML, empty/all-skipped suites and failures.
The parser suite uses the shipped ZIP321 parser with only SDK address validation
mocked. It covers validation and complete multi-payment URI forwarding to the
software, Keystone and Ledger proposal pipelines. It performs no native signing,
network requests, or funded sends. The progress suite checks percentage boundaries
and restoring/resyncing/non-syncing behavior independently of banner thresholds.

For the full unit stack, run the two Gradle commands from the workflow's Test
step. Keep app tests here and device/E2E smoke tests in `zodl-qa`.

See [the follow-up roadmap](unit-test-roadmap.md) for proposed additional suites.

## Require the Android unit-test check on main

The implementation includes `.github/rulesets/require-unit-tests-main.json`, a
main-only required check for GitHub Actions' `test_kotlin_modules`. It does not
replace or widen any existing merge/release rules. The connected implementation
account has Maintain access, and GitHub denied ruleset creation (404), so the live
requirement still needs a repository administrator to apply this payload once:

```sh
gh api --method POST repos/zodl-inc/zodl-android-private/rulesets \
  --input .github/rulesets/require-unit-tests-main.json
```

Check Settings → Rules → Rulesets first to avoid creating a duplicate. After
creation, verify the active rule applies to `main` and names
`test_kotlin_modules` with GitHub Actions as its source. A failing PR check must
block merging; a successful run must satisfy the requirement.
