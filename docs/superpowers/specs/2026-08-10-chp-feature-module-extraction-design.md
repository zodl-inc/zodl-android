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

## Revision note (2026-08-10, post Fable adversarial review)

The first version of this design was reviewed adversarially before implementation and found to
have real problems, not just polish gaps: the SDK-side 3-layer mirror of `MigrationSdk` glossed
over a genuine lifecycle mismatch (voting is stateful, migration isn't), the planned
`VotingCryptoClient` deletion leaked app concerns into the SDK's public API, "lock down
`VotingRustBackend` visibility" is not expressible with Kotlin `internal` across Gradle modules,
and the file inventory undercounted real coupling. This revision folds in the fixes. Two
decisions below were confirmed with the user directly rather than inferred.

## App-side design: `feature-voting` module

- New module `feature-voting`, same shape as `feature-migration`'s `build.gradle.kts`
  (`implementation(projects.uiLib)` + the SDK artifact + shared infra libs it needs).
- Owns everything currently under `ui-lib/screen/voting/*`, `common/model/voting/*` (except any
  DTO genuinely shared with non-voting code — none identified so far),
  `common/usecase/*Voting*UseCase`, `common/repository/Voting*Repository`, plus the following
  found during the Fable review that the original file-glob inventory missed:
  - `SubmitVotesUseCase` and `SkipRemainingKeystoneBundlesUseCase` (voting use cases whose names
    don't match the `*Voting*UseCase` glob).
  - `co.electriccoin.zcash.work.VotingShareTrackingWorker`/`Scheduler` — a WorkManager worker.
    **Caveat**: WorkManager persists a worker's fully-qualified class name in its own DB to
    resume/cancel already-enqueued work across app restarts. If this class's package changes as
    part of the move, already-scheduled work on devices upgrading through this change breaks
    silently. Keep it under `co.electriccoin.zcash.work` (moving modules, not packages), the same
    way `MigrationWorker`/`MigrationLiveDriver` did.
  - `UseCaseModule` has ~20 voting Koin registrations beyond the three DI modules originally
    listed (`ViewModelModule`, `ProviderModule`, `RepositoryModule`).
  - `VotingApiProvider`, `VotingHotkeySeedProvider`, `VotingServerFailoverException`,
    `PirSnapshotResolver`.
  - Shared-file seams that need a contract instead of a wholesale move: `HttpClientProvider`
    (voting-path redaction logic lives inline in this shared class), `ConfigurationEntries`
    (`VOTING_CONFIG_URL`/`VOTING_SERVER_URL` keys in a shared config file), and
    `SynchronizerProvider.getVotingWalletDbPath()` / `Synchronizer.getWalletDbPathForVoting()` (a
    voting-specific hook implemented inside the core `SdkSynchronizer`/`SlipstreamSynchronizer`,
    not something that can move into feature-voting — it needs a contract the SDK still owns).
  - `VotingCryptoClient` is **split**, not deleted outright — see "Splitting `VotingCryptoClient`"
    below.
- New `ui-lib/common/voting/VotingContracts.kt`, styled like `MigrationContracts.kt`:
  - `VotingNavContributor` — installs voting destinations into `WalletNavGraph` via
    `getAll<VotingNavContributor>()`, identical mechanism to migration.
  - The `HomeVM` seam is the hardest contract in this design and should not be hand-waved as "a
    `VotingHomeEntrySource`, or similar": `HomeVM` injects five voting dependencies today and does
    pending-voting-route recovery with explicit ordering against the restore-success dialog,
    typed Keystone scan/sign routing (`ScanKeystoneVotingPCZTRequest`, `SignKeystoneVotingArgs`),
    and `VotingShareTrackingScheduler` calls. This needs its own small design pass during
    implementation planning (likely more than one contract interface, mirroring how migration
    needed `MigrationGate` + `MigrationSyncedHook` + `MigrationAppHooks` + `MigrationNavigator`
    rather than a single catch-all), not a single interface decided in advance here.
- `feature-voting` registers its contract implementations via a new `featureVotingModule` Koin
  module, wired into `ZcashApplication.kt` next to `featureMigrationModule`, the screenshot-test
  runner, and a new `VotingKoinGraphSmokeTest` (mirrors `MigrationKoinGraphSmokeTest`).
- `VOTING_ENABLED` moves inside `feature-voting` and gates the contract implementations' runtime
  behavior (no-op `contribute()`, hidden home entry) — the same role
  `MigrationGate.isMigrationActive()` plays for migration.
- **Module scoping (confirmed with user)**: voting is either present or absent for *all* build
  variants together — no per-variant (e.g. FOSS) exclusion. The app depends on `feature-voting`
  unconditionally, exactly like `feature-migration`. `VOTING_ENABLED` is the only toggle; there is
  no build-graph-level one.

## SDK-side design: `VotingSdk` closes the layering gap, with a real handle model

Voting is **not** a stateless-per-call backend like migration
(`MigrationContext::new` is cheap, every Rust call opens its own connection). `VotingRustBackend`
holds handle/registry state: today's `VotingCryptoClient` owns an `AtomicLong` handle allocator, a
`dbs` map, a mutex-guarded lazy singleton, and a two-step `openVotingDb(dbPath): Long` (allocates,
doesn't open) → `setWalletId(handle, walletId, networkId)` (actually opens, silently
closes/reopens any prior DB on that handle) protocol used across ~6 call sites
(`SubmitVotesUseCase`, `VotingKeystoneRepository`, etc., each in try/finally). `MigrationSdk`'s
shape never had to solve this, so it doesn't transplant as-is.

Revised layering:

1. **`VotingSdk`** (new) — public interface in `cash.z.ecc.android.sdk`. Instead of the
   Long-handle two-step protocol, exposes an object-handle session, matching what
   `TypesafeVotingBackend.openVotingDb(...): TypesafeVotingDb` already does one level down:
   `VotingSdk.openDb(dbPath, walletId, networkId): VotingDbSession` (name TBD during
   implementation), a single call that both allocates and opens, returned as a closeable session
   object rather than a `Long` the caller re-looks-up. The ~6 existing call sites are rewritten
   onto this shape, not mechanically ported.
2. **`VotingSdkImpl`** (new) — implements `VotingSdk`, delegates to `TypesafeVotingBackend`/`Impl`.
   **`TypesafeVotingBackend` is not unchanged** — `delegationPhases` and `resetVotingSessionState`
   currently exist only on the raw JNI `VotingRustBackend.VotingDb` and must be added to the
   typesafe layer (with corresponding test coverage — see Testing).
3. **`TypesafeVotingBackend`/`Impl`** (existing, extended per above) — stays `internal`,
   JNI-facing.

### Splitting `VotingCryptoClient` (not deleting it into `VotingSdkImpl`)

The original plan to absorb all of `VotingCryptoClient`'s JNI-DTO → app-model mapping into
`VotingSdkImpl` was wrong: that mapping carries real app concerns that don't belong in the SDK's
public API —
- the app's own persistence schema (`notesJson`/`witnessesJson`/`encSharesJson`, app-chosen JSON
  keys like `"van_nullifier"`, hex encodings) — enshrining this in the SDK means a storage-format
  tweak needs an SDK release;
- `RoundPhase` UI-facing collapsing/renaming (e.g. `HOTKEY_GENERATED` → `HOTKEY`);
- `BALLOT_DIVISOR_ZATOSHI` — a governance/product constant, never touches Rust;
- `toVoteCommitmentBundle()`, an app-model parser embedded in the JNI mapping.

Split instead:
- **SDK side** (`VotingSdk`): exposes typed public SDK models — new types under
  `cash.z.ecc.android.sdk`, distinct from the app's `ui-lib` model types (not the same classes
  moving to two owners, which was the original doc's self-contradiction). The one piece of
  `VotingCryptoClient` that genuinely belongs here is the `runExpectedMissingRowLookup`
  ("Query returned no rows" compat shim).
- **App side** (thin adapter in `feature-voting`): converts `VotingSdk`'s typed models to/from the
  app's existing `ui-lib` model types, and keeps the JSON storage conventions, `RoundPhase`
  collapsing, and `BALLOT_DIVISOR_ZATOSHI`.
- **Naming collision to resolve during implementation**: sdk-lib already has an internal
  `VotingTxHashLookup` (`Missing`/`Found`) and the app has its own, differently-shaped
  `VotingTxHashLookup` (`NotFound`/`Present`). Rename the app-side one when writing the adapter
  (exact name TBD) — do not let both stand under the same name in different layers.

### Enforcement (confirmed with user): dependency-graph, not visibility modifier

Kotlin `internal` cannot restrict access across Gradle modules — `TypesafeVotingBackendImpl` lives
in `sdk-lib`, `VotingRustBackend` in `backend-lib`, and Kotlin has no friend-modules mechanism.
`VotingRustBackend` and the `Jni*` voting DTOs necessarily stay public classes in the published
backend AAR; "lock down visibility" was not an implementable instruction. Real enforcement:
**`feature-voting` (and, once migrated, `ui-lib`) drop the direct `libs.zcash.sdk.backend`
(JNI artifact) dependency** they'd otherwise inherit — without it on the compile classpath,
importing `VotingRustBackend`/`Jni*` outside `sdk-lib` is a compile error, not a convention. This
can only happen *after* the app is fully migrated off `VotingCryptoClient`'s direct backend-lib
usage (see Phasing) — dropping the dependency early would break the still-unmigrated app.

No new Gradle module on the SDK side (per explicit decision below), and `backend-lib`'s shared
`internal`-visibility helpers (dispatchers, `catch_unwind`, DB-handle plumbing) are untouched.

### The two-toggle coupling (Rust build flag vs. app runtime flag)

`backend-lib/build.gradle.kts` sets `RUSTFLAGS="--cfg zcash_voting"` to gate `mod voting` and the
`VotingRustBackend_*` JNI exports at compile time — separate from the app's runtime
`VOTING_ENABLED`. If these two disagree (SDK artifact built without the cfg, app runtime flag on),
any code path that reaches `VotingRustBackend.new()` gets an `UnsatisfiedLinkError` crash, not a
graceful no-op. Mitigation: `VotingSdk` exposes a cheap `isAvailable(): Boolean` probe (e.g.
attempting the lazy backend init and catching `UnsatisfiedLinkError` once, or checking for the
expected JNI symbol), and `feature-voting`'s contract implementations consult it in addition to
`VOTING_ENABLED` before touching anything voting-related. Document the invariant explicitly at
both flag sites so it can't drift silently.

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
    churn in non-voting code. The dependency-drop enforcement above gives most of the practical
    isolation benefit the module split would have without that churn.
  - **Module scoping**: all-variants-or-none, confirmed with user — no FOSS-specific exclusion.
  - **Enforcement mechanism**: drop the `backend-lib` JNI dependency from `feature-voting`'s
    classpath (confirmed with user over the lint/Konsist-rule alternative) — simpler, no new
    tooling, though it only becomes possible once the app fully migrates off direct
    `VotingRustBackend` usage.

## Testing

- New `VotingKoinGraphSmokeTest` in `feature-voting` (mirrors `MigrationKoinGraphSmokeTest`).
- New unit tests for `VotingSdkImpl` mirroring `OrchardMigrationSdkImplTest`.
- New test coverage for `TypesafeVotingBackend`'s extended surface (`delegationPhases`,
  `resetVotingSessionState`) — the "existing tests untouched" claim from the first draft no longer
  holds once this layer gains methods.
- **Mapping-parity test** (new requirement, not in the original draft): the riskiest code in this
  whole refactor is the JSON/DTO mapping and handle lifecycle behavior moving from
  `VotingCryptoClient` into the SDK/adapter split. Add a test that exercises the same inputs
  through the old and new paths during the transition and asserts identical output, rather than
  trusting the mechanical move by inspection alone.
- Screenshot-test coverage check for the 15+ moved voting screens — confirm existing
  `ui-screenshot-test` coverage survives the module move (path/package changes can silently drop
  screens from screenshot suites).
- `VotingRustBackendTest` (backend-lib) stays untouched — that layer isn't moving.

## Error handling

No new conventions introduced. Each layer keeps its existing contract: `TypesafeVotingBackend`
already documents its `RuntimeException` surface; `VotingSdk` follows whatever pattern
`MigrationSdk` uses at the equivalent layer.

## Phasing (for the implementation plan)

This is a large, mechanical, multi-file move across 3 modules in 2 repos, with one real
cross-repo dependency: **the app consumes the SDK as a published artifact** (`libs.zcash.sdk`),
so app-side work that depends on `VotingSdk` needs either a released SDK version containing it,
or a local SDK checkout override (`ORG_GRADLE_PROJECT_SDK_INCLUDED_BUILD_PATH`, the same mechanism
used for the CHP bugfix deploy) during development. Revised order:

1. SDK: add `VotingSdk`/`VotingSdkImpl`/`VotingDbSession` and the `TypesafeVotingBackend`
   extensions, additive and non-breaking (existing `VotingCryptoClient` keeps working against
   `VotingRustBackend` unchanged in the interim). Cut an SDK release (or wire a local checkout
   override for development).
2. App: scaffold the `feature-voting` module skeleton + `VotingContracts.kt` in `ui-lib`, still
   depending on `libs.zcash.sdk.backend` at this point (enforcement isn't possible yet).
3. Move the 67+ voting files (full inventory above) into `feature-voting`; rewrite the ~6
   `VotingCryptoClient` call sites onto the new `VotingSdk`/adapter split; run the mapping-parity
   test against the old code before deleting it; delete the old scattered locations and
   `VotingCryptoClient` itself.
4. Wire Koin (`featureVotingModule`), add the smoke test, run the full test suite in both repos.
5. **Enforcement, last**: only once step 3 is fully landed, drop the `libs.zcash.sdk.backend`
   dependency from `feature-voting`/`ui-lib` to make the boundary a compile error going forward.

## Where this lands

Both repos currently have `bugfix/MOB-1678` branches with open PRs (`zodl-android#2406` →
`maint/v3.9.x`, `zcash-android-wallet-sdk#2157` → `maint/v3.0.x`) scoped to the multi-bundle
delegation phase-model bugfix. This extraction builds on top of that already-landed work, so it
branches off the tip of `bugfix/MOB-1678` in each repo (not off `maint` directly) and gets its own
separate branch/PR pair — kept distinct from the existing bugfix PRs so that fix stays reviewable
on its own. **Acknowledged risk**: until #2406/#2157 actually merge, this extraction's diff
includes the bugfix commits too, and any review churn on MOB-1678 means rebasing a large
multi-file move on top of it.
