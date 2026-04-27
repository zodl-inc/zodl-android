# MOB-1108 — Coinholder Polling: Review Overview

## What this is

Android implementation of the Coinholder Polling feature — a guided in-app flow
that allows ZEC holders to participate in Zcash governance votes without moving funds.

---

## Specs & References

| Resource | Link |
|----------|------|
| Linear ticket | https://linear.app/zodl/issue/MOB-1108/implement-coinholder-polling |
| Figma | https://www.figma.com/design/Z7xL37dxz1Ii1T0liRhOHD/ZODL-External?node-id=15-9405 |
| iOS APP PR | https://github.com/zodl-inc/zodl-ios/pull/1728 |
| iOS SDK PR | https://github.com/zcash/zcash-swift-wallet-sdk/pull/1687 |
| ZIP-1200 | https://github.com/zcash/zips/pull/1200 |
| ZIP-1218 | https://github.com/zcash/zips/pull/1218 |
| Local dev workspace | https://github.com/valargroup/shielded-Vote-workspace |

---

## Strategy

**Mirror iOS implementation.** The external team delivered a complete iOS implementation
(`zodl-ios` PR #1728). Android follows iOS screen-by-screen, using iOS source as
the primary reference rather than Figma (iOS and Figma have minor divergences,
documented in `navigation-graph.md`).

**Reference code locations (local clones):**

| Codebase | Local path |
|----------|-----------|
| Android app (this repo) | `zashi-android` |
| Android SDK | `zcash-android-wallet-sdk` |
| iOS app (voting branch) | `zodl-ios-voting` |
| iOS SDK | `zcash-swift-walled-sdk` |

**iOS voting source files:** `zodl-ios-voting/secant/Sources/Features/Voting/`

---

## Approach

- Template/example screen: `ui-lib/.../screen/disconnect/` — patterns for VM, State, View, LCE
- One screen = one package under `ui-lib/.../screen/voting/`
- LCE pattern: `mutableLce + withLce + ErrorMapperUseCase` for all async data loading
- Shared session state: `VotingSessionRepository` (Koin singleton) for draft votes across screens
- Commits per logical group, branch: `feature/MOB-1108`
