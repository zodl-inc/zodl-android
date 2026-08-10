# CHP feature-voting Extraction (App-side) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract CHP/voting out of `ui-lib` into a new `feature-voting` Gradle module, mirroring the already-proven `feature-migration` module, and close the app's layering violation by rewiring `VotingCryptoClient` onto the SDK's new public `VotingSdk` (see the SDK-side plan, already implemented and merged into `bugfix/MOB-1678`) instead of the raw JNI `VotingRustBackend`.

**Architecture:** New `feature-voting` module depends on `ui-lib` + the SDK; `ui-lib` never imports a `feature-voting` class. A new `ui-lib/common/voting/VotingContracts.kt` defines the seam (mirroring `MigrationContracts.kt`); `feature-voting` implements it and registers via a `featureVotingModule` Koin module wired into `ZcashApplication.kt` next to `featureMigrationModule`.

**Tech Stack:** Kotlin, Jetpack Compose, Koin DI, Gradle multi-module Android.

## Global Constraints

- **File inventory** (from `docs/superpowers/specs/2026-08-10-chp-feature-module-extraction-design.md`'s design, confirmed against the actual tree): everything under `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/model/voting/` and `ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/voting/` (two self-contained directories, ~23 + ~56 files), plus specific voting files living in shared directories — `common/provider/` (`VotingApiProvider.kt`, `VotingCryptoClient.kt`, `VotingHotkeySeedProvider.kt`, `VotingServerFailoverException.kt`, `PirSnapshotResolver.kt`), `common/repository/` (`VotingAccountScopeExt.kt`, `VotingApiRepository.kt`, `VotingChainConfigRepository.kt`, `VotingConfigRepository.kt`, `VotingKeystoneRepository.kt`, `VotingProofPrecomputeRepository.kt`, `VotingRecoveryRepository.kt`, `VotingRecoverySnapshotExt.kt`, `VotingSessionStore.kt`), `common/usecase/` (`AuthorizeVotingSubmissionUseCase.kt`, `GetAllVotingRoundsUseCase.kt`, `PrepareVotingRoundUseCase.kt`, `RefreshActiveVotingSessionUseCase.kt`, `RefreshVotingRoundsUseCase.kt`, `ResolveVotingRoundSessionUseCase.kt`, `TrackVotingSharesUseCase.kt`, `VotingKeystoneUseCases.kt`, `SubmitVotesUseCase.kt`, `SkipRemainingKeystoneBundlesUseCase.kt`), and `work/` (`VotingShareTrackingScheduler.kt`, `VotingShareTrackingWorker.kt` — **must stay under package `co.electriccoin.zcash.work`**, not move to a `feature-voting`-scoped package: WorkManager persists a worker's fully-qualified class name to resume/cancel already-scheduled work across app restarts, and a package change would silently break that for any device that already has CHP).
- **`VOTING_ENABLED`** moves from `ui-lib`'s `MoreVM.kt` into `feature-voting`, gating the contract implementations' runtime behavior — the app keeps depending on `feature-voting` unconditionally (all-variants-or-none, confirmed decision — no per-variant/FOSS exclusion).
- **SDK dependency during development:** the just-built `VotingSdk` (SDK repo `feature/chp-module-extraction`, merged into `bugfix/MOB-1678`, not yet released to Maven) must be consumed via a local checkout override — `export ORG_GRADLE_PROJECT_SDK_INCLUDED_BUILD_PATH=../zcash-android-wallet-sdk` before any Gradle invocation in this plan, exactly as used for the earlier Firebase deploy this session. Never edit `gradle.properties` directly for this.
- Do not touch the Rust `voting/` crate or the SDK repo at all — this plan is app-side only.
- `feature-voting`'s own `build.gradle.kts` must NOT be given latitude to keep depending on `libs.zcash.sdk.backend` once Task 8 (the `VotingCryptoClient` rewire) lands — dropping that dependency is the SDK design's chosen enforcement mechanism for "JNI internals reachable from exactly one place," and it only becomes safe once nothing in the app imports `VotingRustBackend` directly anymore.

---

## Task 1: Scaffold the `feature-voting` module

**Files:**
- Create: `feature-voting/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing yet — this is pure scaffolding.
- Produces: an empty, buildable `feature-voting` module that `app` depends on. Every later task adds real content to it.

- [ ] **Step 1: Create the module's `build.gradle.kts`**

Mirror `feature-migration/build.gradle.kts` exactly (same plugins, same flavor dimensions, same `unitTests.isReturnDefaultValues = true`), with the namespace changed and the dependency list trimmed/extended for what voting code actually needs (Ktor for the voting API client, kotlinx-serialization already present in the migration one):

```kotlin
import model.DistributionDimension
import model.NetworkDimension

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("secant.android-build-conventions")
    id("secant.jacoco-conventions")
}

android {
    namespace = "co.electriccoin.zcash.voting"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Android SDK stubs (e.g. android.util.Log, used by Twig) throw by default under plain
        // JVM unit tests instead of no-oping — needed so ViewModel logging doesn't crash tests
        // that don't otherwise touch Android framework classes.
        unitTests.isReturnDefaultValues = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.androidx.compose.compiler.get().versionConstraint.displayName
    }

    // Mirrors ui-lib's dimensions so variant matching against it is automatic.
    flavorDimensions += listOf(NetworkDimension.DIMENSION_NAME, DistributionDimension.DIMENSION_NAME)

    productFlavors {
        create(NetworkDimension.TESTNET.value) {
            dimension = NetworkDimension.DIMENSION_NAME
        }

        create(NetworkDimension.MAINNET.value) {
            dimension = NetworkDimension.DIMENSION_NAME
        }

        create(DistributionDimension.STORE.value) {
            dimension = DistributionDimension.DIMENSION_NAME
        }

        create(DistributionDimension.FOSS.value) {
            dimension = DistributionDimension.DIMENSION_NAME
        }

        create(DistributionDimension.INTERNAL.value) {
            dimension = DistributionDimension.DIMENSION_NAME
        }
    }
}

dependencies {
    implementation(projects.uiLib)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.androidx.workmanager)
    implementation(libs.bundles.androidx.compose.core)
    implementation(libs.bundles.androidx.compose.extended)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.immutable)
    implementation(libs.kotlinx.serializable.json)
    implementation(libs.zcash.sdk.backend)
    implementation(libs.zcash.sdk.incubator)

    implementation(projects.buildInfoLib)
    implementation(projects.configurationApiLib)
    implementation(projects.crashAndroidLib)
    implementation(projects.preferenceApiLib)
    implementation(projects.preferenceImplAndroidLib)
    implementation(projects.spackleAndroidLib)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
```

`libs.zcash.sdk.backend` is deliberately still present here — Task 8 removes it once nothing in this module needs `VotingRustBackend` directly anymore (see Global Constraints). If the voting API client needs Ktor (it does — `KtorVotingApiProvider` moves here in Task 3), check `ui-lib/build.gradle.kts` for the exact Ktor dependency aliases already in use there and add matching lines here; do not guess a different Ktor setup than the one already established in this codebase.

- [ ] **Step 2: Register the module in `settings.gradle.kts`**

Find the alphabetically-sorted `include(...)` list (currently `app`, `build-info-lib`, `configuration-api-lib`, `configuration-impl-android-lib`, `crash-lib`, `crash-android-lib`, `feature-migration`, `preference-api-lib`, ...) and add, in alphabetical order right after `feature-migration`:

```kotlin
include("feature-voting")
```

- [ ] **Step 3: Add the app dependency**

In `app/build.gradle.kts`, find:

```kotlin
    implementation(projects.uiLib)
    implementation(projects.featureMigration)
```

and add the new module right after it:

```kotlin
    implementation(projects.uiLib)
    implementation(projects.featureMigration)
    implementation(projects.featureVoting)
```

- [ ] **Step 4: Verify the empty module builds**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin` (mirror whichever exact task name `:feature-migration:compileZcashtestnetInternalDebugKotlin` uses if this variant name guess is wrong — check with `./gradlew :feature-migration:tasks --group verification` or similar, and use the equivalent for `feature-voting`).

Expected: BUILD SUCCESSFUL (empty module, no source files yet, nothing to compile beyond the manifest merge).

- [ ] **Step 5: Commit**

```bash
git add feature-voting/build.gradle.kts settings.gradle.kts app/build.gradle.kts
git commit -m "$(cat <<'EOF'
Scaffold feature-voting module

Empty module mirroring feature-migration's shape. Later tasks move
CHP/voting code into it.
EOF
)"
```

---

## Task 2: Move `common/model/voting/`

**Files:**
- Move (directory): `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/model/voting/` → `feature-voting/src/main/java/co/electriccoin/zcash/ui/common/model/voting/`

**Interfaces:**
- Consumes: nothing new.
- Produces: every type currently in this package, now owned by `feature-voting`, same package name (`co.electriccoin.zcash.ui.common.model.voting`) — no import changes needed anywhere that already imports from this package, only a module dependency change matters (files still in `ui-lib` that import this package will fail to resolve after the move until `ui-lib` either stops needing them or gets them back through a contract — see Task 9).

- [ ] **Step 1: Move the directory**

```bash
mkdir -p feature-voting/src/main/java/co/electriccoin/zcash/ui/common/model
git mv ui-lib/src/main/java/co/electriccoin/zcash/ui/common/model/voting \
       feature-voting/src/main/java/co/electriccoin/zcash/ui/common/model/voting
```

- [ ] **Step 2: Compile-check and record the failure set**

Run: `./gradlew :ui-lib:compileZcashtestnetInternalDebugKotlin`

Expected: FAILS with a list of "unresolved reference" errors in `ui-lib` files that imported `co.electriccoin.zcash.ui.common.model.voting.*` (this package is not yet reachable from `ui-lib` since `ui-lib` doesn't depend on `feature-voting`, and by design never will — `feature-voting` depends on `ui-lib`, not the reverse). **Do not fix these now.** Write down (in your report) the full list of `ui-lib` files that error out — Task 9 needs this exact list to know which files' voting imports must go through the new `VotingContracts.kt` seam instead of a direct import. Do not guess which files these are before running the compiler — the compiler's error list is the authoritative source, more reliable than grep, since this task's job is only to establish the mechanical baseline.

- [ ] **Step 3: Compile-check the module CHP now half-lives in**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin`

Expected: FAILS — the moved files reference other voting code (providers, repositories, screens) that hasn't moved yet. This is expected at this stage of the plan; later tasks progressively fix it. Confirm the errors are all "unresolved reference" to types that live in files this plan moves in later tasks (cross-check against the file inventory in Global Constraints), not something unrelated.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Move common/model/voting/ into feature-voting

Mechanical directory move, no code changes. ui-lib no longer
compiles (voting model imports now out of reach) — expected until
Task 9 rewires ui-lib's voting imports through VotingContracts.kt.
EOF
)"
```

---

## Task 3: Move voting files in `common/provider/`

**Files:**
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingApiProvider.kt` → `feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingApiProvider.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingCryptoClient.kt` → `feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingCryptoClient.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingHotkeySeedProvider.kt` → `feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingHotkeySeedProvider.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingServerFailoverException.kt` → `feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingServerFailoverException.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/PirSnapshotResolver.kt` → `feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/PirSnapshotResolver.kt`

**Interfaces:**
- Consumes: Task 2's moved model types.
- Produces: `VotingCryptoClient`/`VotingCryptoClientImpl` still using the OLD `VotingRustBackend`-based implementation at this point — Task 8 rewires it onto `VotingSdk` later, after everything else has moved and this module's own compile errors are otherwise resolved.

- [ ] **Step 1: Move the five files**

```bash
mkdir -p feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider
git mv ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingApiProvider.kt \
       feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingApiProvider.kt
git mv ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingCryptoClient.kt \
       feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingCryptoClient.kt
git mv ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingHotkeySeedProvider.kt \
       feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingHotkeySeedProvider.kt
git mv ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingServerFailoverException.kt \
       feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingServerFailoverException.kt
git mv ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/PirSnapshotResolver.kt \
       feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/PirSnapshotResolver.kt
```

- [ ] **Step 2: Compile-check `feature-voting`, record remaining failures**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin`

Expected: still fails (repositories, use cases, screens haven't moved yet), but the failure set should shrink relative to Task 2's — confirm no NEW category of error appeared (e.g. a missing Ktor dependency for `KtorVotingApiProvider` — if that specific error shows up, that's this task's own responsibility to fix by adding the correct Ktor dependency alias to `feature-voting/build.gradle.kts`, matching whatever `ui-lib/build.gradle.kts` already uses).

- [ ] **Step 3: Compile-check `ui-lib`**

Run: `./gradlew :ui-lib:compileZcashtestnetInternalDebugKotlin`

Expected: still fails; confirm the failure set only grew by references to these 5 newly-moved files (nothing unexpected).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Move voting provider files into feature-voting

VotingApiProvider, VotingCryptoClient, VotingHotkeySeedProvider,
VotingServerFailoverException, PirSnapshotResolver. VotingCryptoClient
still backed by VotingRustBackend at this point — Task 8 rewires it
onto VotingSdk.
EOF
)"
```

---

## Task 4: Move voting files in `common/repository/`

**Files:**
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingAccountScopeExt.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingApiRepository.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingChainConfigRepository.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingConfigRepository.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingKeystoneRepository.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingProofPrecomputeRepository.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingRecoveryRepository.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingRecoverySnapshotExt.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/VotingSessionStore.kt`
(all → the same relative path under `feature-voting/src/main/java/...`)

**Interfaces:**
- Consumes: Tasks 2-3's moved types.
- Produces: nothing new — pure relocation.

- [ ] **Step 1: Move the nine files**

```bash
mkdir -p feature-voting/src/main/java/co/electriccoin/zcash/ui/common/repository
for f in VotingAccountScopeExt VotingApiRepository VotingChainConfigRepository \
         VotingConfigRepository VotingKeystoneRepository VotingProofPrecomputeRepository \
         VotingRecoveryRepository VotingRecoverySnapshotExt VotingSessionStore; do
  git mv "ui-lib/src/main/java/co/electriccoin/zcash/ui/common/repository/${f}.kt" \
         "feature-voting/src/main/java/co/electriccoin/zcash/ui/common/repository/${f}.kt"
done
```

- [ ] **Step 2: Compile-check `feature-voting` and `ui-lib`, record remaining failures**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin :ui-lib:compileZcashtestnetInternalDebugKotlin`

Expected: both still fail (use cases and screens haven't moved). Confirm the failure sets are shrinking/growing exactly as expected from the moves so far — no surprise error category.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "Move voting repository files into feature-voting"
```

---

## Task 5: Move voting files in `common/usecase/`

**Files:**
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/AuthorizeVotingSubmissionUseCase.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/GetAllVotingRoundsUseCase.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/PrepareVotingRoundUseCase.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/RefreshActiveVotingSessionUseCase.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/RefreshVotingRoundsUseCase.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/ResolveVotingRoundSessionUseCase.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/TrackVotingSharesUseCase.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/VotingKeystoneUseCases.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/SubmitVotesUseCase.kt` (name doesn't match `*Voting*` — found via the SDK-side design review's file-inventory correction)
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/SkipRemainingKeystoneBundlesUseCase.kt` (same)
(all → the same relative path under `feature-voting/src/main/java/...`)

**Interfaces:**
- Consumes: Tasks 2-4's moved types.
- Produces: nothing new — pure relocation.

- [ ] **Step 1: Move the ten files**

```bash
mkdir -p feature-voting/src/main/java/co/electriccoin/zcash/ui/common/usecase
for f in AuthorizeVotingSubmissionUseCase GetAllVotingRoundsUseCase PrepareVotingRoundUseCase \
         RefreshActiveVotingSessionUseCase RefreshVotingRoundsUseCase ResolveVotingRoundSessionUseCase \
         TrackVotingSharesUseCase VotingKeystoneUseCases SubmitVotesUseCase SkipRemainingKeystoneBundlesUseCase; do
  git mv "ui-lib/src/main/java/co/electriccoin/zcash/ui/common/usecase/${f}.kt" \
         "feature-voting/src/main/java/co/electriccoin/zcash/ui/common/usecase/${f}.kt"
done
```

- [ ] **Step 2: Compile-check `feature-voting` and `ui-lib`, record remaining failures**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin :ui-lib:compileZcashtestnetInternalDebugKotlin`

Expected: `feature-voting` should now be much closer to compiling — only the screens (Task 6) and workers (Task 7) are still missing, plus whatever `ui-lib`-side seam Task 9 hasn't built yet. `ui-lib` still fails for the same category of reason as before (voting imports it no longer has access to).

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "Move voting use case files into feature-voting"
```

---

## Task 6: Move `screen/voting/`

**Files:**
- Move (directory): `ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/voting/` → `feature-voting/src/main/java/co/electriccoin/zcash/ui/screen/voting/`

**Interfaces:**
- Consumes: Tasks 2-5's moved types.
- Produces: every voting screen/state/view/VM, now in `feature-voting`, same package name — `WalletNavGraph`'s direct `composable<VoteXArgs>` registrations (in `ui-lib`) will fail to resolve after this move; Task 9 replaces them with the `VotingNavContributor` pattern.

- [ ] **Step 1: Move the directory**

```bash
mkdir -p feature-voting/src/main/java/co/electriccoin/zcash/ui/screen
git mv ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/voting \
       feature-voting/src/main/java/co/electriccoin/zcash/ui/screen/voting
```

- [ ] **Step 2: Compile-check `feature-voting` and `ui-lib`, record remaining failures**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin :ui-lib:compileZcashtestnetInternalDebugKotlin`

Expected: `feature-voting` should be very close to green now (Task 7's workers are the only voting code still missing). `ui-lib` fails with the accumulated set of every file that directly imported voting code — this is the exact list Task 9 must resolve. Write down this full list in your report; it becomes Task 9's starting checklist.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Move screen/voting/ into feature-voting

The largest single move (~56 files: all voting screens, states,
views, view models). ui-lib's WalletNavGraph/HomeVM/MoreVM/DI-module
voting references now unresolved — Task 9 replaces them with the
VotingContracts.kt seam.
EOF
)"
```

---

## Task 7: Move `work/Voting*`

**Files:**
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingScheduler.kt`
- Move: `ui-lib/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingWorker.kt`
(both → the same relative path under `feature-voting/src/main/java/...`)

**Interfaces:**
- Consumes: Tasks 2-6's moved types.
- Produces: nothing new.

**Package note (binding, see Global Constraints):** these two files keep the `co.electriccoin.zcash.work` package exactly as-is after the move — only the Gradle module changes, not the package. Confirm this explicitly in your self-review: `head -1` both files after moving and verify the `package` line is untouched.

- [ ] **Step 1: Move the two files**

```bash
mkdir -p feature-voting/src/main/java/co/electriccoin/zcash/work
git mv ui-lib/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingScheduler.kt \
       feature-voting/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingScheduler.kt
git mv ui-lib/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingWorker.kt \
       feature-voting/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingWorker.kt
```

- [ ] **Step 2: Verify the package declaration didn't change**

```bash
head -3 feature-voting/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingScheduler.kt
head -3 feature-voting/src/main/java/co/electriccoin/zcash/work/VotingShareTrackingWorker.kt
```

Expected: both still start with `package co.electriccoin.zcash.work`.

- [ ] **Step 3: Compile-check `feature-voting`**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin`

Expected: BUILD SUCCESSFUL — every voting file is now inside `feature-voting`, and (aside from whatever Task 8/9 still owe it) the module should compile standalone at this point since it only depends on `ui-lib` + the SDK, both already fully resolvable. If it still fails, the error list at this point is real signal that something in the file inventory was missed — cross-check against Global Constraints' file list and this plan's Tasks 2-7 before assuming it's expected.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Move VotingShareTracking worker/scheduler into feature-voting (package unchanged)"
```

---

## Task 8: Rewire `VotingCryptoClient` onto `VotingSdk`

`VotingCryptoClientImpl` currently talks directly to `VotingRustBackend` (raw JNI) using a `Long`-handle open/close protocol (`openVotingDb(dbPath): Long` → `setWalletId(handle, walletId, networkId)` → `db(handle)`). The SDK-side plan (already implemented, merged) built `cash.z.ecc.android.sdk.VotingSdk`/`VotingDbSession` specifically to replace this — `VotingSdk.openDb(dbPath, walletId, networkId): VotingDbSession` is a single call, object-handle session.

**Files:**
- Modify: `feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingCryptoClient.kt`
- Modify: `feature-voting/build.gradle.kts` (only if a new SDK entry-point dependency is needed beyond what's already there — `libs.zcash.sdk.backend` already exposes `cash.z.ecc.android.sdk.VotingSdk` since it's the same SDK artifact)

**Interfaces:**
- Consumes: `cash.z.ecc.android.sdk.VotingSdk`, `cash.z.ecc.android.sdk.VotingDbSession`, and every `cash.z.ecc.android.sdk.model.voting.*` type (full list: `VotingNoteInfo`, `VotingWitness`, `VotingVanWitness`, `VotingEncryptedShare`, `VotingCommitmentResult`, `VotingCommitResult`, `VotingSharePayload`, `VotingHotkey`, `VotingBundleSetupResult`, `VotingGovernancePczt`, `VotingRoundPhase`, `VotingRoundState`, `VotingRoundSummary`, `VotingVoteRecord`, `VotingDelegationPhase`, `VotingDelegationPirPrecomputeResult`, `VotingDelegationProofResult`, `VotingDelegationSubmissionResult`, `VotingTxHashLookup`, `VotingCommitmentBundleRecord`, `VotingCommittedVoteRecord`, `VotingShareDelegationRecord`) — see `sdk-lib/src/main/java/cash/z/ecc/android/sdk/VotingSdk.kt` and `sdk-lib/src/main/java/cash/z/ecc/android/sdk/model/voting/VotingModels.kt` in the SDK repo checkout (`../zcash-android-wallet-sdk` relative to this repo, or wherever `SDK_INCLUDED_BUILD_PATH` points) for their exact current signatures — read those files directly rather than relying on this plan's summary, since this plan was written from the SDK plan's design, not by re-reading the final merged SDK code.
- Produces: `VotingCryptoClient`'s existing PUBLIC interface (`co.electriccoin.zcash.ui.common.provider.VotingCryptoClient`) is **unchanged** — every one of its ~90+ call sites across the moved files continues to compile against the same method signatures. Only `VotingCryptoClientImpl`'s internals change.

**This task is intentionally scoped by goal, not full prescribed code** (unlike Tasks 1-7): `VotingCryptoClient.kt` is large (~1700 lines) and its exact mapping code depends on reading the actual final `VotingSdk` signatures fresh rather than trusting a summary written before that code existed — the SDK-side plan's own implementers found and fixed real type-mismatch surprises this same way (see the SDK plan's Task 5 report). Design latitude here is deliberate and will be checked by task review against the constraints below, not against a line-by-line spec.

- [ ] **Step 1: Set up the local SDK checkout**

```bash
export ORG_GRADLE_PROJECT_SDK_INCLUDED_BUILD_PATH=../zcash-android-wallet-sdk
```

Confirm the SDK checkout at that relative path is on a branch containing the merged VotingSdk work (`bugfix/MOB-1678` or later, in the `zcash-android-wallet-sdk` repo) before proceeding — `git -C ../zcash-android-wallet-sdk log --oneline -1` should show a commit that is a descendant of the VotingSdk work (look for `Fix VotingSdk.isAvailable() doc: it is not cheap` or later in its history).

- [ ] **Step 2: Read the real, current `VotingSdk` surface**

Read `sdk-lib/src/main/java/cash/z/ecc/android/sdk/VotingSdk.kt` and `sdk-lib/src/main/java/cash/z/ecc/android/sdk/model/voting/VotingModels.kt` in the SDK checkout in full. Do not proceed to Step 3 from memory or from this plan's Interfaces section alone — those are a summary, not the source of truth.

- [ ] **Step 3: Rewrite `VotingCryptoClientImpl`**

Replace the `Long`-handle/`VotingRustBackend` internals with `VotingSdk`/`VotingDbSession`-backed ones:
- A single `VotingSdk` instance (via `VotingSdk.new()`), lazily created, mutex-guarded (mirror the existing `backendMutex`/lazy-init pattern already in this file for the old `rustBackend()`).
- Replace the `nextDbHandle`/`dbPaths`/`dbs` maps' role: `VotingCryptoClient`'s own public interface still exposes `openVotingDb(dbPath): Long` / `setWalletId(dbHandle, walletId, networkId)` / `closeVotingDb(dbHandle)` as `Long`-handle methods (interface unchanged, per this task's constraint) — internally, map each issued `Long` handle to a real `VotingDbSession` obtained via `VotingSdk.openDb(...)`, called at `setWalletId` time (mirroring exactly when the old code called `rustBackend().openVotingDb(...)` — check the current `setWalletId` override's exact timing/close-prior-db-on-same-handle behavior and preserve it).
- Every other method on `VotingCryptoClientImpl` (there are dozens — this file is the full 1700-line surface) becomes a call to the corresponding `VotingDbSession`/`VotingSdk` method, with request/response types mapped between this file's own `co.electriccoin.zcash.ui.common.model.voting.*` app types (unchanged, already moved in Task 2) and the SDK's new `cash.z.ecc.android.sdk.model.voting.*` public types. Where a mapping function already exists in this file (e.g. `toAppModel()` extensions converting from the old `internal.model.voting.Jni*` types), rewrite it to convert from the new `cash.z.ecc.android.sdk.model.voting.*` types instead — same app-facing output type, different source type.
- Remove every import of `cash.z.ecc.android.sdk.internal.jni.*` and `cash.z.ecc.android.sdk.internal.model.voting.*` from this file — none should remain once the rewrite is complete.
- Preserve app-only logic that has nothing to do with the JNI/SDK boundary as-is: JSON storage-schema conventions, `RoundPhase`/`DelegationPhase` UI-facing enums (already app-owned per Task 2's move), the `BALLOT_DIVISOR_ZATOSHI`-style governance constants, `toVoteCommitmentBundle()` — these stay exactly as they are, only their upstream data source changes.

- [ ] **Step 4: Compile-check**

Run: `./gradlew :feature-voting:compileZcashtestnetInternalDebugKotlin`

Expected: BUILD SUCCESSFUL, with zero remaining references to `cash.z.ecc.android.sdk.internal.*` anywhere in `feature-voting`. Verify with:

```bash
grep -rn "cash.z.ecc.android.sdk.internal" feature-voting/src/main/java
```

Expected: no output.

- [ ] **Step 5: Drop the now-unnecessary JNI dependency**

Per Global Constraints, this is the point where the enforcement mechanism becomes safe. In `feature-voting/build.gradle.kts`, check whether `implementation(libs.zcash.sdk.backend)` is still needed — `cash.z.ecc.android.sdk.VotingSdk` itself lives in `sdk-lib`, not `backend-lib`, so if `libs.zcash.sdk.backend` was only ever pulled in transitively via `libs.zcash.sdk.incubator`/other SDK artifacts (check the SDK's own artifact structure — `libs.zcash.sdk.backend` may correspond to `sdk-lib` or to `backend-lib` depending on how the version catalog aliases are named; read `gradle/libs.versions.toml` or the equivalent catalog file to confirm which Gradle coordinate `libs.zcash.sdk.backend` actually resolves to before deciding). If it resolves to the JNI/`backend-lib` artifact and is no longer directly referenced, remove the line. If `feature-voting` genuinely doesn't need it after this task, remove it and re-run Step 4's compile-check to confirm nothing broke.

- [ ] **Step 6: Commit**

```bash
git add feature-voting/src/main/java/co/electriccoin/zcash/ui/common/provider/VotingCryptoClient.kt \
        feature-voting/build.gradle.kts
git commit -m "$(cat <<'EOF'
Rewire VotingCryptoClient onto the SDK's public VotingSdk

VotingCryptoClient's own interface is unchanged; VotingCryptoClientImpl
now delegates to VotingSdk/VotingDbSession instead of the raw JNI
VotingRustBackend, closing the layering violation the SDK-side plan
built VotingSdk to fix. Drops the direct JNI dependency once nothing
in this module needs it anymore.
EOF
)"
```

---

## Task 9: `VotingContracts.kt` seam — rewire `ui-lib`'s `WalletNavGraph`/`HomeVM`/`MoreVM`/DI modules

`ui-lib` currently reaches directly into voting code from several places, none of which go through a contract layer the way `feature-migration` does (`MigrationContracts.kt` + `getAll<MigrationNavContributor>()`):

- **`WalletNavGraph.kt`** — ~10 direct `composable<VoteXArgs> { VoteXScreen(...) }` registrations (imports at lines 204-225 in the pre-move file; exact list confirmed by Task 6's recorded compile-failure output).
- **`HomeVM.kt`** — 5 constructor-injected voting dependencies (`votingRecoveryRepository`, `votingApiRepository`, `votingSessionStore`, `refreshActiveVotingSession`, `votingShareTrackingScheduler`) plus pending-voting-route recovery logic (`recoverPendingVotingRouteIfNeeded()`, ordered against the restore-success dialog) and typed Keystone-routing navigation (`ScanKeystoneVotingPCZTRequest`, `SignKeystoneVotingArgs`).
- **`MoreVM.kt`** — direct imports of `VoteCoinholderPollingArgs`/`VoteHowToVoteArgs`, the `VOTING_ENABLED` flag (currently `internal const val VOTING_ENABLED = true` in this file — moves into `feature-voting` per Global Constraints), and the settings-menu entry point (`onVotingClick`).
- **`di/ViewModelModule.kt`, `di/ProviderModule.kt`, `di/RepositoryModule.kt`, `di/UseCaseModule.kt`** — direct Koin registrations for every voting class (full list confirmed via grep in this plan's own preparation — cross-check against what's actually still there after Tasks 2-7, since some registrations may already be dead references once their target moved).

**Files:**
- Create: `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/voting/VotingContracts.kt`
- Modify: `ui-lib/src/main/java/co/electriccoin/zcash/ui/WalletNavGraph.kt`
- Modify: `ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/home/HomeVM.kt`
- Modify: `ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/more/MoreVM.kt`
- Modify: `ui-lib/src/main/java/co/electriccoin/zcash/di/ViewModelModule.kt`, `ProviderModule.kt`, `RepositoryModule.kt`, `UseCaseModule.kt` (remove voting registrations — they move to Task 10's `featureVotingModule`)
- Create (in `feature-voting`): the `*Impl` classes implementing the new contracts, mirroring `feature-migration/src/main/java/co/electriccoin/zcash/migration/FeatureMigrationImpls.kt`'s pattern (one file, or split if it grows unwieldy — your judgment).

**Interfaces:**
- Consumes: Tasks 2-8's moved/rewired code.
- Produces: the contracts Task 10's Koin module binds implementations to. At minimum: a `VotingNavContributor` (mirrors `MigrationNavContributor` exactly — `fun contribute(navGraphBuilder: NavGraphBuilder)`, installing every voting `composable<...>` registration that used to live directly in `WalletNavGraph.kt`). Beyond that, this task has real design latitude (see below) — but whatever shape you choose, it must let `ui-lib` compile with **zero** remaining imports of any `feature-voting`-owned class, and it must preserve `HomeVM`'s existing behavior exactly (pending-route recovery ordering against the restore-success dialog, Keystone routing, share-tracking scheduling) and `MoreVM`'s existing behavior exactly (the settings entry point, gated by the same `VOTING_ENABLED` semantics, now read through the contract instead of a bare top-level `const val`).

**This task has real design latitude, by explicit decision** (this plan was authorized to proceed without a separate design pass for this specific seam — see the plan's own preparation notes). Task review will check the constraints above (zero `feature-voting` imports left in `ui-lib`, behavior preservation, contract shape mirrors `MigrationContracts.kt`'s spirit) rather than a prescribed interface list. If you find yourself needing more than one contract interface to keep `HomeVM`'s concerns cleanly separated (very likely, given migration itself needed `MigrationGate` + `MigrationSyncedHook` + `MigrationAppHooks` + `MigrationNavigator` rather than one catch-all), that is expected and correct — follow `MigrationContracts.kt`'s example of small, single-purpose interfaces over one large one.

- [ ] **Step 1: Read the real current state of every file this task touches**

Read `WalletNavGraph.kt`, `HomeVM.kt`, `MoreVM.kt`, and the four DI modules in full (in `ui-lib`, at their post-Task-6/7 state — Tasks 2-7 have already broken their voting imports, so you're reading the "what needs to be replaced" state directly, not this plan's earlier summary of it).

- [ ] **Step 2: Design and write `VotingContracts.kt`**

Style it like `MigrationContracts.kt` (`ui-lib/src/main/java/co/electriccoin/zcash/ui/common/migration/MigrationContracts.kt`) — same header comment explaining the seam's purpose ("ui-lib never imports feature-voting classes — it talks exclusively to these contracts"), same small-single-purpose-interface style.

- [ ] **Step 3: Implement the contracts in `feature-voting`**

Mirror `FeatureMigrationImpls.kt`'s pattern — concrete `*Impl` classes taking whatever dependencies they need via constructor injection (Koin resolves them in Task 10).

- [ ] **Step 4: Rewire `WalletNavGraph.kt`**

Replace the direct `composable<VoteXArgs> { ... }` block with the `getAll<VotingNavContributor>().forEach { it.contribute(this) }` pattern, exactly where migration's equivalent line lives (`ui/WalletNavGraph.kt:375-377` in the pre-Task-9 file — find the current line number since it will have shifted).

- [ ] **Step 5: Rewire `HomeVM.kt`**

Replace the 5 direct voting constructor dependencies with whatever contract(s) you designed in Step 2, preserving `recoverPendingVotingRouteIfNeeded()`'s exact behavior and its call-ordering relative to the restore-success dialog check (`hasRestoreSuccessBeenShown`), and the Keystone-routing/share-tracking-scheduling logic. This is the highest-risk step in this task — read the existing method bodies carefully before rewriting, and preserve control flow exactly, only changing where the data/actions come from (contract calls instead of direct repository/use-case calls).

- [ ] **Step 6: Rewire `MoreVM.kt`**

Replace the direct `VoteCoinholderPollingArgs`/`VoteHowToVoteArgs` imports and the bare `VOTING_ENABLED` top-level const with a contract-driven equivalent. `VOTING_ENABLED` itself moves into `feature-voting` (per Global Constraints) as the flag the contract implementations consult — `MoreVM`'s behavior (show/hide the settings entry, `onVotingClick`) must be unchanged from the outside.

- [ ] **Step 7: Clean up the four DI modules**

Remove every voting-specific registration from `ViewModelModule.kt`, `ProviderModule.kt`, `RepositoryModule.kt`, `UseCaseModule.kt` (Task 10 re-adds them all inside `featureVotingModule`). Remove the now-dead voting imports from each file's top.

- [ ] **Step 8: Compile-check `ui-lib`**

Run: `./gradlew :ui-lib:compileZcashtestnetInternalDebugKotlin`

Expected: BUILD SUCCESSFUL. Verify zero remaining `feature-voting` leakage:

```bash
grep -rln "co.electriccoin.zcash.ui.screen.voting\|co.electriccoin.zcash.ui.common.model.voting\|co.electriccoin.zcash.ui.common.provider.Voting\|co.electriccoin.zcash.ui.common.repository.Voting\|co.electriccoin.zcash.ui.common.usecase.Voting" ui-lib/src/main/java
```

Expected: no output (the one intentional exception is `VotingContracts.kt` itself, which legitimately lives in `ui-lib/src/main/java/co/electriccoin/zcash/ui/common/voting/` — a different package, won't match this grep).

- [ ] **Step 9: Commit**

```bash
git add ui-lib/src/main/java/co/electriccoin/zcash/ui/common/voting/VotingContracts.kt \
        ui-lib/src/main/java/co/electriccoin/zcash/ui/WalletNavGraph.kt \
        ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/home/HomeVM.kt \
        ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/more/MoreVM.kt \
        ui-lib/src/main/java/co/electriccoin/zcash/di/ViewModelModule.kt \
        ui-lib/src/main/java/co/electriccoin/zcash/di/ProviderModule.kt \
        ui-lib/src/main/java/co/electriccoin/zcash/di/RepositoryModule.kt \
        ui-lib/src/main/java/co/electriccoin/zcash/di/UseCaseModule.kt \
        feature-voting/src/main/java
git commit -m "$(cat <<'EOF'
Add VotingContracts.kt seam, rewire ui-lib off direct feature-voting
imports

WalletNavGraph/HomeVM/MoreVM/DI modules now talk to voting exclusively
through contracts (mirrors MigrationContracts.kt), implemented in
feature-voting. ui-lib has zero remaining feature-voting imports.
EOF
)"
```

---

## Task 10: `featureVotingModule` Koin wiring

**Files:**
- Create: `feature-voting/src/main/java/co/electriccoin/zcash/voting/di/FeatureVotingModule.kt`
- Modify: `app/src/main/java/co/electriccoin/zcash/app/ZcashApplication.kt`
- Modify: `ui-screenshot-test/src/main/java/co/electroniccoin/zcash/ui/screenshot/ZcashUiTestRunner.kt` (add `featureVotingModule` next to the existing `featureMigrationModule` registration)

**Interfaces:**
- Consumes: every contract implementation from Task 9, every provider/repository/use-case/VM from Tasks 2-8.
- Produces: `featureVotingModule` — a Koin `Module`, registered app-wide.

- [ ] **Step 1: Write `FeatureVotingModule.kt`**

Mirror `FeatureMigrationModule.kt`'s structure exactly: a header KDoc explaining what this module contributes and where it's wired, then `singleOf(...)`/`factoryOf(...)`/`viewModelOf(...)`/explicit `single { }`/`factory { }` blocks for:
- Contract implementations (Task 9's `*Impl` classes, bound to their contract interfaces).
- Providers: `VotingCryptoClientImpl` bind `VotingCryptoClient`, `VotingHotkeySeedProviderImpl` bind `VotingHotkeySeedProvider`, `KtorVotingApiProvider` bind `VotingApiProvider`, `VotingShareTrackingScheduler` (no interface — matches the current `ProviderModule.kt` registration exactly).
- Repositories: `VotingConfigRepositoryImpl` bind `VotingConfigRepository`, `VotingChainConfigRepositoryImpl` bind `VotingChainConfigRepository`, `VotingApiRepositoryImpl` bind `VotingApiRepository`, `VotingRecoveryRepositoryImpl` bind `VotingRecoveryRepository`, `VotingProofPrecomputeRepositoryImpl` bind `VotingProofPrecomputeRepository` (check the current `RepositoryModule.kt` registration for its exact constructor-injected `votingCryptoClient = get()` shape and preserve it), `VotingKeystoneRepositoryImpl` bind `VotingKeystoneRepository`, `VotingSessionStoreImpl` bind `VotingSessionStore`.
- Use cases: every voting factory registration currently in `UseCaseModule.kt` (the ~10 files from Task 5, plus `ParseVotingKeystonePCZTUseCase`/`CreateVotingKeystonePcztEncoderUseCase` if those are separate top-level classes inside `VotingKeystoneUseCases.kt` rather than requiring their own registration — check the current registrations' exact shape).
- View models: every voting VM from Task 6's move (`VoteChainConfigVM`, `VoteCoinholderPollingVM`, `VoteConfirmSubmissionVM`, `VoteHowToVoteVM`, `VoteProposalDetailVM`, `VoteProposalListVM`, `VoteResultsVM`, `ScanKeystoneVotingPCZTViewModel`, `SignKeystoneVotingVM`, `VoteTallyingVM`).

- [ ] **Step 2: Wire into `ZcashApplication.kt`**

Add the import and the module to the `startKoin` module list, right after `featureMigrationModule`:

```kotlin
import co.electriccoin.zcash.voting.di.featureVotingModule
```

```kotlin
                coreModule,
                providerModule,
                crashProviderModule,
                dataSourceModule,
                repositoryModule,
                addressBookModule,
                metadataModule,
                useCaseModule,
                mapperModule,
                viewModelModule,
                featureMigrationModule,
                featureVotingModule
```

- [ ] **Step 3: Wire into the screenshot-test runner**

Same pattern in `ui-screenshot-test/src/main/java/co/electroniccoin/zcash/ui/screenshot/ZcashUiTestRunner.kt`, next to its existing `featureMigrationModule` registration.

- [ ] **Step 4: Full app compile-check**

Run:
```bash
export ORG_GRADLE_PROJECT_SDK_INCLUDED_BUILD_PATH=../zcash-android-wallet-sdk
./gradlew :app:compileZcashtestnetInternalDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature-voting/src/main/java/co/electriccoin/zcash/voting/di/FeatureVotingModule.kt \
        app/src/main/java/co/electriccoin/zcash/app/ZcashApplication.kt \
        ui-screenshot-test/src/main/java/co/electroniccoin/zcash/ui/screenshot/ZcashUiTestRunner.kt
git commit -m "$(cat <<'EOF'
Wire featureVotingModule into Koin

Mirrors featureMigrationModule's registration point. App now compiles
end to end with voting fully isolated in feature-voting.
EOF
)"
```

---

## Task 11: `VotingKoinGraphSmokeTest` + full build/install/live verification

**Files:**
- Create: `feature-voting/src/test/java/co/electriccoin/zcash/di/VotingKoinGraphSmokeTest.kt`

**Interfaces:**
- Consumes: `featureVotingModule` (Task 10).
- Produces: nothing new — this is the terminal task for the app-side plan.

- [ ] **Step 1: Write the smoke test**

Mirror `feature-migration/src/test/java/co/electriccoin/zcash/di/MigrationKoinGraphSmokeTest.kt`'s structure and stubbing approach exactly (read that file first) — resolve every voting ViewModel through `featureVotingModule` (plus whatever other modules it needs stubs for) and assert none throws `NoDefinitionFoundException`.

- [ ] **Step 2: Run it**

Run: `./gradlew :feature-voting:testDebugUnitTest --tests "co.electriccoin.zcash.di.VotingKoinGraphSmokeTest"`

Expected: PASS.

- [ ] **Step 3: Full DIT build + install + live check**

Per this session's standing convention, build DIT and install/launch on the emulator (not just a compile check):

```bash
export ORG_GRADLE_PROJECT_SDK_INCLUDED_BUILD_PATH=../zcash-android-wallet-sdk
./gradlew assembleZcashtestnetInternalDebug
adb install -r app/build/outputs/apk/zcashtestnetInternal/debug/app-zcashtestnet-internal-debug.apk
adb shell monkey -p co.electriccoin.zcash.testnet.internal.debug -c android.intent.category.LAUNCHER 1
```

(Confirm the exact APK output path and package name against what earlier sessions used — `co.electriccoin.zcash.testnet.internal.debug` per this session's established convention — adjust if the actual build output path differs.)

Manually verify (or ask for manual verification, since this is a live device check an automated step can't fully self-certify): the More screen still shows the voting entry point, tapping it navigates to the voting flow, and nothing crashes on launch.

- [ ] **Step 4: Run the full `ui-lib` + `feature-voting` unit test suites**

Run: `./gradlew :ui-lib:testDebugUnitTest :feature-voting:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, no regressions in either module's existing test suite.

- [ ] **Step 5: Commit**

```bash
git add feature-voting/src/test/java/co/electriccoin/zcash/di/VotingKoinGraphSmokeTest.kt
git commit -m "$(cat <<'EOF'
Add VotingKoinGraphSmokeTest

Terminal task for the app-side CHP feature-voting extraction: every
voting ViewModel resolves cleanly through featureVotingModule. Full
app build/install verified live on-device.
EOF
)"
```

---

## What this plan deliberately leaves open

- **Tasks 8 and 9 are goal-scoped, not line-by-line prescribed** — an explicit, recorded decision (unlike every other task in this plan and the entire SDK-side plan). Task review for these two must check behavior-preservation and the stated constraints rather than diffing against literal plan text.
- **Mapping-parity testing** between the old `VotingCryptoClient` (JNI-backed) and the new one (SDK-backed) was flagged as a requirement in the design spec but is not written out as a task here, for the same reason Tasks 8-9 are goal-scoped — the exact old/new code shapes weren't knowable until this plan's own Task 8 actually runs. Whoever executes Task 8 should propose and add such a test as part of that task's own scope if reviewable coverage doesn't otherwise emerge from Task 11's live verification.
- **A final whole-branch review** (mirroring the SDK plan's structure) should still run after Task 11, even though it isn't written as a numbered task here — follow subagent-driven-development's standard final-review step.
