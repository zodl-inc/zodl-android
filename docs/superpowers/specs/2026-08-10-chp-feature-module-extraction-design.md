# CHP (Coinholder Polling / shielded voting) feature-module extraction — design

Repos: `zodl-android` (this doc, app-side) and `zcash-android-wallet-sdk` (SDK-side; see the
pointer doc at `docs/superpowers/specs/2026-08-10-chp-feature-module-extraction-design.md` there,
which links back here — this is the single source of truth for both sides).

## Context

CHP/voting code today is scattered across both repos with no enforced boundary:

- **zodl-android**: 67 files under `ui-lib` — `screen/voting/*` (UI), `common/model/voting/*`,
  `common/usecase/*Voting*UseCase`, `common/repository/Voting*Repository`,
  `common/provider/VotingCryptoClient`. `HomeVM`/`MoreVM`/`WalletNavGraph`/the Koin DI modules
  (`ViewModelModule`, `ProviderModule`, `RepositoryModule`) all import voting classes directly, and
  a hardcoded `internal const val VOTING_ENABLED = true` in `MoreVM.kt` is the only toggle.
- **zcash-android-wallet-sdk**: the Rust crate at `backend-lib/src/main/rust/voting/` (144
  matches) plus its JNI-facing Kotlin (`internal.jni.VotingRustBackend`,
  `internal.model.voting.*`), and a partial Kotlin typesafe layer in `sdk-lib`
  (`TypesafeVotingBackend`/`Impl`, already `internal interface` — good) that nothing outside the
  SDK actually uses today. The app's `VotingCryptoClient` bypasses it entirely and talks straight
  to the raw JNI class `VotingRustBackend`.

Goal: extract CHP into a real, isolated feature boundary in both repos — following the
`feature-migration` module's already-proven pattern on the app side, and closing a genuine
layering violation on the SDK side — so that CHP becomes a single addable/removable unit instead
of code scattered through the shared core.

**Explicitly out of scope**: the Rust `voting/` crate itself (its internal structure, the
`zcash_voting`/`unstable-voting-circuits` Cargo feature gating) is not touched. It's already
conditionally compiled and stays exactly where it is.

## Precedent: `feature-migration`

`feature-migration` is a sibling Gradle module (`app` → `implementation(projects.featureMigration)`,
`feature-migration` → `implementation(projects.uiLib)` + SDK). `ui-lib` never imports a single
`feature-migration` class. Instead `ui-lib/common/migration/MigrationContracts.kt` defines the
seam — `MigrationNavContributor`, `MigrationHomeMessageSource`, `MigrationGate`,
`MigrationSyncedHook`, `MigrationAppHooks`, `MigrationNavigator`, `MigrationDebugActions` — and
`feature-migration` implements them, registering the implementations via a `featureMigrationModule`
Koin module wired into `ZcashApplication.kt`. `WalletNavGraph` discovers migration destinations
generically: `KoinPlatform.getKoin().getAll<MigrationNavContributor>().forEach { it.contribute(this) }`.

On the SDK side, migration has three layers, not two:

1. **`MigrationSdk`** — a genuinely public interface in `cash.z.ecc.android.sdk`, obtained via a
   `MigrationSdk.new(...)` companion factory. The *only* thing `feature-migration` depends on.
2. **`OrchardMigrationSdkImpl`** — implements `MigrationSdk`, delegates down to...
3. **`TypesafeMigrationBackend`/`Impl`** — `internal`, JNI-facing.

Voting today only has layer 3. This design adds the missing layers on both sides.

## App-side design: `feature-voting` module

- New module `feature-voting`, same shape as `feature-migration`'s `build.gradle.kts`
  (`implementation(projects.uiLib)` + the SDK artifact + shared infra libs it needs).
- Owns everything currently under `ui-lib/screen/voting/*`, `common/model/voting/*` (except any
  DTO genuinely shared with non-voting code — none identified so far),
  `common/usecase/*Voting*UseCase`, `common/repository/Voting*Repository`. `VotingCryptoClient` is
  **deleted** (see SDK-side design — its job moves into the SDK's `VotingSdkImpl`).
- New `ui-lib/common/voting/VotingContracts.kt`, styled exactly like `MigrationContracts.kt`:
  - `VotingNavContributor` — installs voting destinations into `WalletNavGraph` via
    `getAll<VotingNavContributor>()`, identical mechanism to migration.
  - `VotingHomeEntrySource` (or similar name, finalized during implementation) — replaces the
    direct `VOTING_ENABLED`/route imports in `HomeVM`/`MoreVM`, mirroring
    `MigrationHomeMessageSource`.
  - Any other seam interface implementation surfaces once the file-by-file move identifies them
    (mirrors migration's `MigrationGate`/`MigrationSyncedHook`/`MigrationAppHooks` pattern where
    needed).
- `feature-voting` registers its contract implementations via a new `featureVotingModule` Koin
  module, wired into `ZcashApplication.kt` next to `featureMigrationModule`, the screenshot-test
  runner, and a new `VotingKoinGraphSmokeTest` (mirrors `MigrationKoinGraphSmokeTest` — resolves
  every voting ViewModel through `featureVotingModule` without a
  `NoDefinitionFoundException`).
- `VOTING_ENABLED` moves inside `feature-voting` and gates the contract implementations' runtime
  behavior (no-op `contribute()`, hidden home entry) — the same role `MigrationGate.isMigrationActive()`
  plays for migration. The app keeps depending on `feature-voting` unconditionally, exactly like
  `feature-migration` — this is a runtime gate, not a build-graph toggle.

## SDK-side design: `VotingSdk` closes the layering gap

Mirrors `MigrationSdk`'s three-layer shape exactly:

1. **`VotingSdk`** (new) — public interface in `cash.z.ecc.android.sdk`, `VotingSdk.new(...)`
   companion factory. The only thing `feature-voting` depends on.
2. **`VotingSdkImpl`** (new) — implements `VotingSdk`, delegates to the existing
   `TypesafeVotingBackend`/`Impl` (unchanged). Absorbs the JNI-DTO → app-model mapping logic that
   `VotingCryptoClient` used to do.
3. **`TypesafeVotingBackend`/`Impl`** (existing, unchanged) — stays `internal`, JNI-facing.

`backend-lib`'s `VotingRustBackend` becomes reachable from exactly one place —
`TypesafeVotingBackendImpl` — nothing else, including the app, may see it. This is the one real
behavior-preserving refactor in the whole design: closing a genuine layering violation (the app
currently bypasses the SDK's public API and reaches into JNI internals directly), not just a
cosmetic file move.

No new Gradle module on the SDK side (per explicit decision below), and `backend-lib`'s shared
`internal`-visibility helpers (dispatchers, `catch_unwind`, DB-handle plumbing) are untouched.

## Decisions made during brainstorming (recorded for context)

- **Scope**: one shared design across both repos, since the goal and shape turned out symmetric
  once the SDK side was narrowed to "Kotlin/JNI layer only, Rust crate untouched."
- **CHP's status on `main`/`maint`**: left as-is for this design — this extraction restructures
  CHP wherever it lives, it does not itself decide whether/when CHP ships in a release. That's a
  separate, already-in-flight conversation (see the `#wallet-team` Slack thread from 2026-08-10 re:
  `maint` vs `main` basing).
  - **App**: real new Gradle module (`feature-voting`), mirroring `feature-migration` — the
    precedent fits with no friction.
  - **SDK**: package-level boundary (`VotingSdk` public interface + existing `internal` layers),
    *not* a new Gradle module. Considered the symmetric alternative (new `voting-lib` module) but
    rejected it: it would force widening `backend-lib`'s shared internal helpers (dispatchers,
    `catch_unwind`, DB-handle plumbing) to be visible outside their module, which is unrelated
    churn in non-voting code for no isolation benefit the interface boundary doesn't already give.

## Testing

- New `VotingKoinGraphSmokeTest` in `feature-voting` (mirrors `MigrationKoinGraphSmokeTest`).
- New unit tests for `VotingSdkImpl` mirroring `OrchardMigrationSdkImplTest`.
- Existing `VotingRustBackendTest` (backend-lib) and `TypesafeVotingBackendImplTest` (sdk-lib) are
  untouched — they test layers that aren't moving.

## Error handling

No new conventions introduced. Each layer keeps its existing contract: `TypesafeVotingBackend`
already documents its `RuntimeException` surface; `VotingSdk` follows whatever pattern
`MigrationSdk` uses at the equivalent layer.

## Phasing (for the implementation plan)

This is a large, mechanical, multi-file move across 3 modules in 2 repos. Natural order:

1. SDK: add `VotingSdk`/`VotingSdkImpl`, lock down `VotingRustBackend` visibility to
   `TypesafeVotingBackendImpl` only.
2. App: scaffold the `feature-voting` module skeleton + `VotingContracts.kt` in `ui-lib`.
3. Move the 67 voting files into `feature-voting`; rewire the deleted `VotingCryptoClient`'s
   call sites onto `VotingSdk`; delete the old scattered locations.
4. Wire Koin (`featureVotingModule`), add the smoke test, run the full test suite in both repos.

## Where this lands

Both repos currently have `bugfix/MOB-1678` branches with open PRs (`zodl-android#2406` →
`maint/v3.9.x`, `zcash-android-wallet-sdk#2157` → `maint/v3.0.x`) scoped to the multi-bundle
delegation phase-model bugfix. This extraction builds on top of that already-landed work, so it
branches off the tip of `bugfix/MOB-1678` in each repo (not off `maint` directly) and gets its own
separate branch/PR pair — kept distinct from the existing bugfix PRs so that fix stays reviewable
on its own.
