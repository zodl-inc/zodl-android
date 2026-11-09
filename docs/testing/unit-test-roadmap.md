# Proposed mobile unit-test follow-ups

This roadmap follows the November 9, 2026 audit of the private iOS and Android
apps for MOB-1203. These suites are proposals, separate from the initial closure
batch. Extend existing suites rather than duplicating the substantial migration,
voting, hardware-wallet and private-connection coverage.

| Priority | Platform / owner | Scenarios |
| --- | --- | --- |
| P1 | Android: `ResetZashiUseCase` | Cancelled/failed biometrics have no destructive effects; migration workers and gift-card sessions drain before SDK deletion; an unavailable synchronizer respects the five-second bound; `keepFiles` preserves metadata while secrets are cleared. Use iOS `RootResetFlowTests` as a behavior reference. |
| P1 | iOS: `SendConfirmation` | Authentication refusal never exports seeds or submits; missing proposal fails safely; success, partial and uncertain results retain transaction IDs and produce the expected reducer/UI state. Existing submission-helper tests cover a different layer. |
| P1 | Both: payment ownership | Change accounts during suspended authentication/proposal work; prevent submission with another account's proposal. Android: repeated confirmation while submission is active invokes submission once. |
| P1 | Both: swap quote lifecycle | Return requests out of order; change account/asset/amount while waiting; dismiss before completion; exercise expiry boundaries. Stale responses must not overwrite the active quote or propose for the wrong account. |
| P1 | Android / ZIP321 dependency: amount rendering | The parser preserves `100000001` zatoshis, but `NonNegativeAmount.toZecValueString()` in ZIP321 1.0.2 returns `1` rather than `1.00000001`. Its eight-significant-digit `MathContext` loses precision. Add dependency regressions and verify `PrefillSendUseCase.requestFromZip321` preserves the amount before changing the dependency or conversion path. The MOB-1268 parser tests assert stored zatoshis directly and leave this rendering behavior unchanged. |
| P2 | Both: exchange-rate lifecycle | Switch fiat during a fetch; opt out during retries; cross refresh/staleness deadlines. Check attribution, cancellation, cached values and timer cleanup; retain existing calculation/error tests. |
| P2 | Android: restore orchestration | Persist requested Tor preference before restoration; forward seed/birthday/network unchanged; propagate failure without navigating to success. |
| P2 | iOS: background task ownership | Completion/cancellation/expiration end a task exactly once and restore the idle timer. Verify actual OS scheduling separately on a device. |

Use Swift Testing/TCA `TestStore` and existing Kotlin coroutine tools. Inject
clocks/scopes/completion signals where needed, and avoid live services, real
wallet seeds, funded wallets, hardware and wall-clock sleeps. When a regression
test exposes a production bug, pair the test with a separately reviewed fix;
do not weaken the expected invariant to accommodate the bug.
