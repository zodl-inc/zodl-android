# MOB-1108 — Screen Map: iOS → Android

iOS reference: `zodl-ios-voting/secant/Sources/Features/Voting/`
Android screens: `ui-lib/.../screen/voting/`

---

## Screen-by-Screen Mapping

| iOS View | iOS File | Android Package | Status | Notes |
|----------|----------|-----------------|--------|-------|
| `HowToVoteView` | `HowToVoteView.swift` | `howtovote/` | ✅ | Dynamic wallet name (Keystone/Zodl) |
| `PollsListView` | `PollsListView.swift` | `coinholderpolling/` | ✅ | Active / Voted / Closed card states |
| `ProposalListView (mode:.voting)` | `ProposalListView.swift` | `proposallist/` | ✅ | Progress bar, meta line, description sheet |
| `ProposalDetailView` | `ProposalDetailView.swift` | `proposaldetail/` | ✅ | N/N counter, Support/Oppose/Abstain, Back+Next |
| `ProposalListView (mode:.review)` | `ProposalListView.swift` | `proposallist/` | ✅ | Same screen, `isReviewMode=true` arg |
| `ConfirmSubmissionView` | `ConfirmSubmissionView.swift` | `confirmsubmission/` | ✅ | Full state machine: idle→authorizing→submitting→completed→failed |
| `DelegationSigningView` | `DelegationSigningView.swift` | `delegationsigning/` | ⚠️ | Keystone QR signing — stub, Zodl flow skips this |
| `TallyingView` | `TallyingView.swift` | `tallying/` | ✅ | "Votes Closed" read-only screen |
| `ResultsView` | `ResultsView.swift` | `results/` | ✅ | Result bars with iOS color logic |
| `IneligibleView` | `IneligibleView.swift` | `ineligible/` | ✅ | 0 voting weight case |
| `WalletSyncingView` | `WalletSyncingView.swift` | `walletsyncing/` | ✅ | Wallet not synced case |
| `VotingErrorView` | `VotingErrorView.swift` | `votingerror/` | ✅ | Generic error |

---

## Screens NOT in iOS (removed from Android)

| Android Screen | Reason removed |
|---------------|----------------|
| `EligibleScreen` | Not in iOS flow — eligibility is checked silently |
| `VoteSubmissionScreen` | Replaced by per-question `ProposalDetailScreen` |

---

## iOS Screens NOT in Figma (but kept)

| iOS Screen | Why kept |
|-----------|----------|
| `DelegationSigningView` | Keystone-only QR signing — needed for Keystone flow later |
| `TallyingView` | "Votes Closed" state when round is in tallying/finalized status |

---

## Navigation Flow (simplified)

```
Settings → HowToVoteScreen (first time)
         → CoinholderPollingScreen (returning)

CoinholderPollingScreen
  → ProposalListScreen (voting mode)
    → ProposalDetailScreen [Q1 … QN]
      → ProposalListScreen (review mode)
        → ConfirmSubmissionScreen
            idle → authorizing → submitting → completed → Done
          → CoinholderPollingScreen

Error paths:
  → IneligibleScreen    (0 voting weight)
  → WalletSyncingScreen (wallet not synced)
  → VotingErrorScreen   (generic error via LCE)
```

Full nav graph: `docs/voting/navigation-graph.md`

---

## Key Divergences: iOS vs Figma vs Android

| Topic | iOS | Figma | Android |
|-------|-----|-------|---------|
| Back on Q1 | Always shown | Only from Q2 | Follows iOS |
| Poll description sheet | Inside ProposalListView (View more) | From PollsList card tap | Follows iOS |
| Submission progress | Single screen (ConfirmSubmission, multi-state) | Separate "Submitting vote..." screen | Follows iOS |
| DelegationSigning | Keystone QR screen | Not in Figma | Kept for Keystone |
| TallyingView | Read-only "Votes Closed" | Not in Figma | Kept for closed rounds |
