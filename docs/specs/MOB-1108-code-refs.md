# MOB-1108 — Code References

Quick reference for navigating relevant code. All paths are absolute for direct querying.

---

## Android (this repo)

**Base path:** `zashi-android`

### Voting screens
```
ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/voting/
├── coinholderpolling/      Poll list (Active/Voted/Closed cards)
├── howtovote/              Onboarding explainer (first time)
├── proposallist/           Proposal overview + review mode
├── proposaldetail/         Per-question voting (N/N, Support/Oppose/Abstain)
├── confirmsubmission/      TX preview + authorizing/submitting/completed/failed
├── delegationsigning/      Keystone QR signing (stub)
├── tallying/               "Votes Closed" read-only
├── results/                Tally results with colored bars
├── ineligible/             0 voting weight
├── walletsyncing/          Wallet not synced
└── votingerror/            Generic error
```

### Shared infrastructure
```
ui-lib/src/main/java/co/electriccoin/zcash/ui/common/
├── model/voting/           Data models (VotingRound, Proposal, VoteOption, ...)
├── repository/VotingSessionRepository.kt   Shared draft + vote records
├── provider/VotingApiProvider.kt           API client
├── provider/FakeVotingApiProvider.kt       Dev mock
├── datasource/VotingStorageDataSource.kt   Local storage (hotkey, phase, shares)
└── usecase/
    ├── GetAllVotingRoundsUseCase.kt
    ├── GetActiveVotingSessionUseCase.kt
    └── CheckVotingEligibilityUseCase.kt
```

### DI registration
```
ui-lib/src/main/java/co/electriccoin/zcash/di/
├── RepositoryModule.kt     VotingSessionRepository
├── ViewModelModule.kt      All voting VMs
└── ProviderModule.kt       HasSeenHowToVoteStorageProvider
```

### Navigation
```
ui-lib/src/main/java/co/electriccoin/zcash/ui/WalletNavGraph.kt
ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/more/MoreVM.kt   Entry point
```

### Docs
```
docs/voting/navigation-graph.md   Full nav graph (iOS vs Android comparison)
docs/voting/MOB-1108-plan.md      Implementation plan with checkboxes
docs/specs/                       This directory
```

### Template screen (reference pattern)
```
ui-lib/src/main/java/co/electriccoin/zcash/ui/screen/disconnect/
```

---

## iOS (local clone — voting branch)

**Base path:** `zodl-ios-voting`

### Voting source
```
secant/Sources/Features/Voting/
├── VotingStore.swift               State definition + Reducer entry
├── VotingStore+Navigation.swift    All navigation actions
├── VotingStore+Session.swift       Round loading, initialization
├── VotingStore+Delegation.swift    ZK proof + Keystone signing
├── VotingStore+Submission.swift    Vote submission pipeline
├── VotingStore+Helpers.swift       Shared helpers
├── VotingView.swift                Root view + screen router
├── PollsListView.swift             → CoinholderPollingView
├── HowToVoteView.swift             → HowToVoteView
├── ProposalListView.swift          → ProposalListView (voting + review modes)
├── ProposalDetailView.swift        → ProposalDetailView
├── ConfirmSubmissionView.swift     → ConfirmSubmissionScreen
├── DelegationSigningView.swift     → DelegationSigningView (Keystone only)
├── TallyingView.swift              → TallyingView
├── ResultsView.swift               → ResultsView
├── IneligibleView.swift            → IneligibleView
├── WalletSyncingView.swift         → WalletSyncingView
├── VotingErrorView.swift           → VotingErrorView
└── VotingComponents.swift          Shared: voteOptionColor, ZIPBadge, VoteBadgePill
```

### Settings entry point
```
secant/Sources/Features/Settings/SettingsCoordinator.swift   Line ~242: hasSeenHowToVote check
```

---

## iOS (main branch — for non-voting context)

**Base path:** `zodl-ios`

---

## Android SDK

**Base path:** `zcash-android-wallet-sdk`

---

## iOS SDK

**Base path:** `zcash-swift-walled-sdk`
