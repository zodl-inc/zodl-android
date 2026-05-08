# Changelog
All notable changes to this library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this library adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

# Unreleased

## Added
- `SDKSynchronizer.rescanFrom(height:)`: Rescans the chain from the given BlockHeight.
- `SynchronizerState.fullyScannedHeight`: Contiguous-from-birthday scan high-water mark published on `stateStream`/`latestState`. Callers that need an authoritative view of the wallet's note and nullifier state at a specific height (for example, balance anchored at a poll snapshot) should gate on this rather than `latestBlockHeight` (chain tip) or `maxScannedHeight` (head-first scan progress, which can race ahead under Spend-before-Sync).
- `Synchronizer.getTreeState(height:)`: Fetches the commitment tree state at the given block height from lightwalletd and returns the protobuf-serialized `TreeState` bytes, for app-layer consumers that need to hand tree state to an external component (for example, witness generation via FFI). A throwing default implementation keeps the addition source-compatible for downstream `Synchronizer` conformers.
- `zcash_voting` dependency foundation: SDK Rust crate now depends on `zcash_voting 0.5.6` (`default-features = false`, `client-pir`, `client-tree-sync`) and exposes pure-function FFI symbols for share-nullifier computation and PIR proof validation.
- `PirSnapshotResolver`, `PirSnapshotResolverError`, `PirSnapshotProbeOutcome`, `PirSnapshotProbing`, and `HTTPPirSnapshotProbe`: Select a vote-nullifier PIR endpoint whose `/root` metadata exactly matches a voting round's expected snapshot height.
- `Voting*` Swift types: public type contract for the shielded voting FFI boundary, in `Sources/ZcashLightClientKit/Rust/Voting/VotingTypes.swift`.
- `VotingRustBackend`: Swift wrapper for the voting `libzcashlc` surface. A `final class` that owns an opaque `VotingDatabaseHandle` for the duration of a voting session. Exposes:
  - Database lifecycle: `init()`, `open(path:)`, `close()` (idempotent), and `deinit` cleanup, over `zcashlc_voting_db_open` / `zcashlc_voting_db_free`.
  - `setWalletId(_:)` over `zcashlc_voting_set_wallet_id`.
  - `precomputeDelegationPir(roundId:bundleIndex:notes:pirEndpoints:expectedSnapshotHeight:networkId:pirResolver:)` over `zcashlc_voting_precompute_delegation_pir`, with `PirSnapshotResolver`-driven endpoint selection.
  - Vote-tree sync: `syncVoteTree(roundId:nodeUrl:)`, `generateVanWitness(roundId:bundleIndex:anchorHeight:)`, and `resetTreeClient(roundId:)` over the corresponding `zcashlc_voting_sync_vote_tree` / `_generate_van_witness` / `_reset_tree_client` FFI.
  - The static `computeShareNullifier(voteCommitment:shareIndex:primaryBlind:)` over `zcashlc_voting_compute_share_nullifier`, with 32-byte input validation.
  - `VotingRustBackendError` cases `databaseAlreadyOpen` / `databaseNotOpen` for handle-state errors, alongside the existing `rustError` and `invalidData`.
- `Broadcaster` protocol — separates transaction creation from submission, enabling custom broadcast strategies (e.g. submitting to multiple lightwalletd servers in parallel).
  - `Broadcaster.createProposedTransactions(proposal:spendingKey:)` — creates transactions locally without broadcasting, returning `[ZcashTransaction.Overview]` with raw bytes.
  - `Broadcaster.createTransactionFromPCZT(pcztWithProofs:pcztWithSigs:)` — extracts and stores a transaction from PCZT data without submitting.
  - `Broadcaster.submit(_:to:)` — submits raw transaction bytes to a specific `LightWalletEndpoint`. Respects Tor configuration.
- `Synchronizer.broadcaster` property, also exposed through the closure and Combine synchronizer facades, to access the `Broadcaster` from SDK synchronizer instances.
- `libzcashlc` voting tree-sync FFI: `zcashlc_voting_sync_vote_tree`, `zcashlc_voting_generate_van_witness`, and `zcashlc_voting_reset_tree_client`. Operate on the existing `VotingDatabaseHandle`, which also carries a `zcash_voting::tree_sync::VoteTreeSync` constructed in `zcashlc_voting_db_open`.
- `libzcashlc` voting wallet-notes FFI: `zcashlc_voting_get_wallet_notes`. Loads unspent Orchard notes for a wallet account at a snapshot height and returns them as a JSON-encoded `Vec<NoteInfo>`, suitable as the `notes` input to `zcashlc_voting_precompute_delegation_pir`. `account_uuid` must be a non-null pointer to exactly 16 bytes; otherwise the call fails (returns null).
- `libzcashlc` voting key-utility FFI: `zcashlc_voting_extract_orchard_fvk_from_ufvk`. Decodes a UFVK string and returns the raw 96-byte Orchard FVK. Returns null on missing Orchard component, malformed UFVK, or invalid `network_id`.
- `libzcashlc` voting utility FFI: `zcashlc_voting_warm_proving_caches`, `zcashlc_voting_decompose_weight`, `zcashlc_voting_generate_delegation_inputs`, `zcashlc_voting_generate_delegation_inputs_with_fvk`, `zcashlc_voting_extract_pczt_sighash`, `zcashlc_voting_extract_spend_auth_sig`, `zcashlc_voting_extract_nc_root`, and `zcashlc_voting_verify_witness`. These cover voting proof setup, PCZT/signature extraction, note-commitment root extraction, and witness verification.
- `libzcashlc` voting witness FFI: `zcashlc_voting_generate_note_witnesses`. Generates Orchard Merkle inclusion witnesses for a bundle's notes anchored at the round's snapshot height. Adds `incrementalmerkletree 0.8` as a direct Rust dependency.
- `libzcashlc` voting round and recovery FFI: adds C-compatible return structs and free helpers, persisted round-state APIs, crash-recovery metadata helpers, and share-delegation tracking.

## Changed
- Bumped Rust dependencies to current crates.io releases (`zcash_address` 0.10→0.11, `zcash_client_backend` 0.21→0.22, `zcash_client_sqlite` 0.19→0.20, `zcash_primitives`/`zcash_proofs` 0.26→0.27, `zcash_protocol` 0.7→0.8, `zcash_transparent` 0.6→0.7, `sapling-crypto` 0.6→0.7, `orchard` 0.12→0.13, `pczt` 0.5→0.6) and removed the `[patch.crates-io]` git-rev overrides. No public Swift API changes.
- Pinned `orchard` to `=0.13.1` and enabled its `unstable-voting-circuits` feature, required transitively by `zcash_voting`. No public Swift API changes.
- Enabled the `client-tree-sync` feature on `zcash_voting`, required by the new voting tree-sync FFI listed above.
- Added `zcash_keys 0.13` (`orchard` feature) as a Rust dependency, used by the new voting wallet-notes, key-utility, and utility FFI to decode UFVKs and derive Orchard FVKs. No public Swift API changes.
- Bumped `zcash_voting` to `0.5.6` so `network_id` matches the SDK (`0` = testnet, `1` = mainnet) end-to-end for wallet-notes JSON consumed by delegation PIR, and to pick up upstream voting submission validation fixes. No public Swift API changes.

# 2.4.9 - 2026-04-04

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3285000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3295000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3920000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3930000.json
````

# 2.4.8 - 2026-03-25

## Fixed
- Networking connections are closed properly, resetting the state and letting the next re-run to properly initialize. This fixes the issues with restore after reset and also server switch issues. 

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3280000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3282500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3910000.json
````

# 2.4.7 - 2026-03-20

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3265000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3277500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3890000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3900000.json
````

# 2.4.6 - 2026-03-12

## Fixed
- `switchTo` server updates `TransactionEncoder`. It was missing and submission of the transactions went through the previous server instead of a current one. 

# 2.4.5 - 2026-03-06

## Fixed
- Fix for a long-standing note commitment tree corruption error.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3252500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3262500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3870000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3880000.json
````

# 2.4.4 - 2026-02-24

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3220000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3250000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3810000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3860000.json
````

# 2.4.3 - 2026-02-02

## Fixed
- LightWalletGRPCServiceOverTor.submit now throws ZcashError.serviceSubmitFailed instead of a generic error. This fix mirrors LightWalletGRPCService’s error reporting and resolves the discrepancy between the Tor and non-Tor submit APIs.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3172500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3217500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3740000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3800000.json
````

# 2.4.2 - 2025-12-16

## Added
- `SDKSynchronizer.deleteAccount(AccountUUID)`: Deletes the specified account, and all transactions that exclusively involve it, from the wallet database.

## Changed
- `downloadParamsIfnotPresent` now retries up to three times on sync pipeline failure.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3157500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3170000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3720000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3730000.json
````

# 2.4.1 - 2025-12-03

## Added
- Custom SQL functions to `Synchronizer.debugDatabase`:
  - `txid(Blob) -> String`: converts a transaction ID from its byte form to a user-facing string.
  - `memo(Blob?) -> String?`: prints the given blob as a string if it is a text memo, and as hex-encoded bytes otherwise.

- `SDKSynchronizer.estimateTimestamp(for height: Blockheight)`: Get an estimated timestamp for a given block height.

- `SDKSynchronizer.enhanceTransactionBy(txId)` Calls an enhance action for a given txId.

## Fixed
- The Sapling parameter files download logic replaces the files atomically rather than moving them into the final destination. This prevents errors caused by partially downloaded files.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3130000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3155000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3680000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3710000.json
````

# 2.4.0 - 2025-11-10

## Added
- `SDKSynchronizer.getSingleUseTransparentAddress` Get an ephemeral single use transparent address.
- `SDKSynchronizer.checkSingleUseTransparentAddresses` Checks to find any single-use ephemeral addresses exposed in the past day that have not yet received funds, excluding any whose next check time is in the future. This will then choose the address that is most overdue for checking, retrieve any UTXOs for that address over Tor, and add them to the wallet database. 
- `SDKSynchronizer.updateTransparentAddressTransactions` Finds all transactions associated with the given transparent address.
- `SDKSynchronizer.fetchUTXOsBy(address)` Checks to find any UTXOs associated with the given transparent address. This check will cover the block range starting at the exposure height for that address, if known, or otherwise at the birthday height of the specified account.

## Fixed
- [2.3.6 change] Transparent funds are now reported after `UpdateChainTipAction` is processed. Attempt to shield before this action has been failing otherwise. Update: the solution handled only cold start of a client, now it resets the logic with each stop() call of the SDK.
- Updated to zcash_client_sqlite-0.18.9 to fix problems in transparent UTXO selection for shielding, including incorrect handling of outputs received at ephemeral addresses and selection of dust transparent outputs for shielding.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3107500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3127500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3640000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3670000.json
````

# 2.3.7 - 2025-10-20

## Added
- New public API `func debugDatabase(sql: String) -> String` for querying the database from the client. Usa cautiously, ideally for debugging purposes only. A However note, the connection to the database is created in a read-only mode.

## Fixed
- Updated FFI 0.18.4 with fixes for the transaction states alongside changes in the enhancement logic for handling not found transactions.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3095000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3105000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3620000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3630000.json
````

# 2.3.6 - 2025-10-10

## Changed
- Transparent funds are now reported after `UpdateChainTipAction` is processed. Attempt to shield before this action has been failing otherwise. 

## Fixed
- FFI bumped to 0.18.3 with sqp fixes for balances.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3090000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3092500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3610000.json
````

# 2.3.5 - 2025-10-06

## Fixed
- Zero confirmation shielding error. With mempool detection a new scenario appeared - clients could make an attempt to shield while the transparents funds haven't been confirmed (it's associated receiving transaction). [2nd fix for this issue alongside 2.3.4]

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3082500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3087500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3600000.json
````

# 2.3.4 - 2025-09-29

## Fixed
- Zero confirmation shielding error. With mempool detection a new scenario appeared - clients could make an attempt to shield while the transparents funds haven't been confirmed (it's associated receiving transaction).

# 2.3.3 - 2025-09-28

## Added
- Mempool detection support: see
  `CompactBlockProcessor.{watchMempool,consumeMempoolStream,resolveMempools}`.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3052500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3080000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3570000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3590000.json
````

# 2.3.2 - 2025-09-03

## Fixed
- This release fixes a potential false-positive in the `expired_unmined` column of the `v_transactions` view.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3040000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3050000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3560000.json
````

# 2.3.1 - 2025-08-22

## Added
- `SDKSynchronizer.httpRequestOverTor(for request: URLRequest, retryLimit: UInt8)` New public API for http requests done via Tor.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3020000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3037500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3530000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3550000.json
````

# 2.3 - 2025-08-05

## Removed
- `latencyThresholdMillis` parameter was removed from `Synchronizer.evaluateBestOf()` method. The algorithm of servers evaluation was changed to always require `kServers` to be returned.

## Updated
- `Initializer.init(..., isTorEnabled: Bool, isExchangeRateEnabled: Bool)` The initializer has been updated to include flags that control Tor setup.

## Added
- `func tor(enabled: Bool)` A function that allows clients to configure Tor usage for lwd and http calls.
- `func exchangeRateOverTor(enabled: Bool)` A function that allows clients to configure Tor usage for exchange rate.
- `func isTorSuccessfullyInitialized() async -> Bool?` A function that returns the result of the TorClient initialization. A nil value indicates that initialization has not been initiated. True/false represents success or failure, respectively.
- `ZcashTransaction.Overview state` that holds information whether the transaction has been confirmed or expired or is still pending.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2962500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/3017500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3440000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3520000.json
````

# 2.2.17 - 2025-06-16

## Fixed
- FFI 0.17.0 introduces retry logic for Tor, significantly improving the reliability of currency conversion fetches.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2925000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2960000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3400000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3430000.json
````

# 2.2.16 - 2025-05-21

## Fixed
- BlockEnhancer has got stuck in a while loop due to a missing break (retry = false)

# 2.2.15 - 2025-05-15

## Added
- `SDKSynchronizer.getCustomUnifiedAddress`: Obtain a newly-generated Unified Address
  with the specified receiver types.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2907500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2922500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3380000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3390000.json
````

# 2.2.14 - 2025-04-30

## Fixed

### [#1482] Fix the wipe function
- An occasional error occurred after the wipe function was called due to a missing termination of the timer. The next trigger caused the compact block processor to run again, but without any database in place, resulting in a “no such table: accounts” error.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2902500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2905000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3370000.json
````

# 2.2.13 - 2025-04-25

## Changed
- The base sapling params download URL has been changed to `https://download.z.cash/downloads/`

# 2.2.12 - 2025-04-24

## Added
- `SDKSynchronizer.estimateBirthdayHeight(for date: Date)`: Get an estimated height for a given date, typically used for estimating birthday.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2877500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2900000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3330000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3360000.json
````

# 2.2.11 - 2025-04-03

## Fixed
- `transparent_gap_limit_handling` migration, whereby wallets having received transparent outputs at child indices below the index of the default address could cause the migration to fail.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2870000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2875000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3320000.json
````

# 2.2.10 - 2025-03-27

## Fixed
- Adopted `zcashlc_fix_witnesses` for the note commitment tree fix.
- Transparent gap limit handling. SDK can find all transparent funds and shield them. This has been tested to successfully recover Ledger funds.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2842500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2867500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3280000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3310000.json
````

# 2.2.9 - 2025-03-06

## Added
- `SDKSynchronizer.redactPCZTForSigner`: Decrease the size of a PCZT for sending to a signer.
- `SDKSynchronizer.PCZTRequiresSaplingProofs`: Check whether the Sapling parameters are required for a given PCZT.

## Updated
- Methods returning an array of `ZcashTransaction.Overview` try to evaluate transaction's missing blockTime. This typically applies to an expired transaction.  

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2782500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2840000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3180000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3270000.json
````

# 2.2.8 - 2025-01-10

## Added
- `SDKSynchronizer.listAccounts` Returns a list of the accounts in the wallet.
- `SDKSynchronizer.importAccount` Imports a new account for unified full viewing key.
- `SDKSynchronizer.createPCZTFromProposal` Creates a partially-created (unsigned without proofs) transaction from the given proposal.
- `SDKSynchronizer.addProofsToPCZT` Adds proofs to the given PCZT
- `SDKSynchronizer.createTransactionFromPCZT` Takes a PCZT that has been separately proven and signed, finalizes it, and stores it in the wallet. Internally, this logic also submits and checks the newly stored and encoded transaction.

## Changed
- `zcashlc_propose_transfer`, `zcashlc_propose_transfer_from_uri` and `zcashlc_propose_shielding` no longer accpt a `use_zip317_fees` parameter; ZIP 317 standard fees are now always used and are not configurable.
- The SDK no longer assumes a default account. All business logic with instances of Zip32AccountIndex(<index>) has been refactored.
- `SDKSynchronizer.getAccountBalance -> AccountBalance?` into `SDKSynchronizer.getAccountsBalances -> [AccountUUID: AccountBalance]`

## Removed
- `SDKSynchronizer.sendToAddress`, deprecated in 2.1
- `SDKSynchronizer.shieldFunds`, deprecated in 2.1

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2675000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2780000.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3010000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3170000.json
````

# 2.2.7 - 2024-11-17

## Added
- `DerivationTool.deriveArbitraryWalletKey`
- `DerivationTool.deriveArbitraryAccountKey`
- `DerivationTool.deriveUnifiedAddressFrom(ufvk)`

# 2.2.6 - 2024-10-22

## Fixed
- This release fixes a bug in wallet reorg handling that could result in a crash
  under certain circumstances.

# 2.2.5 - 2024-10-10

## Fixed
- This release fixes a bug in scan progress calculation that could result in
  occasionally reporting scan progress values greater than 100%.

# 2.2.4 - 2024-10-07

## Fixed
- This release fixes a potential source of corruption in wallet note commitment
  trees related to incorrect handling of chain reorgs. It includes a database
  migration that will repair the corrupted database state of any wallet
  affected by this corner case.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2650000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2672500.json
````

Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2800000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/3000000.json
````

# 2.2.3 - 2024-09-17

## Changed

### [#1488] Resolve build issues with SQLight
- SQLight's `Expression` is no longer a unique identifier, namespace needed to be added as a prefix to it. Buildability solved with `SQLight.Expression` instead.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2637500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2647500.json
````

# 2.2.2 - 2024-09-06

## Added

### [#1466] Choose the best server by testing responses from multiple hosts
- Synchronizer's `evaluateBestOf(endpoints: [], ...) async -> [LightWalletEndpoint]` method takes a list of endpoints and evaluates top k best performant servers. 

- `TransactionEntity` extended to access `is_shielding` from the DB and provides the value to the clients. 

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2620000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2635000.json
````

# 2.2.1 - 2024-08-21

## Fixed
- This release fixes an error in database migration logic that could cause problems
  when upgrading certain wallets from versions in the 2.1.x range.

# 2.2.0 - 2024-08-20

## Added
- `Synchronizer.exchangeRateUSDStream: AnyPublisher<FiatCurrencyResult?, Never>`,
  which returns the currently-cached USD/ZEC exchange rate, or `nil` if it has not yet been
  fetched.
- `Synchronizer.refreshExchangeRateUSD()`, which refreshes the rate returned by
  `Synchronizer.exchangeRateUSDStream`. Prices are queried over Tor (to hide the wallet's
  IP address).

## Changed

### [#1475] Adopt transaction data requests
- The transaction history is now processed using `transaction data requests`, which are fetched every 1,000 blocks during longer syncs or with each sync loop when a new block is mined.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2562500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2617500.json
````

# 2.1.12 - 2024-07-04

## Fixed

### [#1462] Syncing is broken
The CompactBlockProcessor's state machine got stuck in some cases at the updateChainTip action.

# 2.1.11 - 2024-07-03

## Added

### [#452] TX Resubmission-the wallet has to periodically resubmit unmined transactions
The Compact block processor's state machine has been extended to check whether there are any unmined and unexpired transactions, and it attempts to resubmit such transactions every 5 minutes.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2542500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2560000.json
````

# 2.1.10 - 2024-06-14

## Fixed
- Further changes for compatibility with Xcode 15.3 and above. 

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2532500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2540000.json
````

# 2.1.9 - 2024-06-05

## Fixed
- Synchronizer's' `prepare()` method passes even if server is down and not providing chan tip. 

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2522500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2530000.json
````

# 2.1.8 - 2024-05-30

## Added
- New API `getMemos(for rawID: Data) -> [Memos]` to load memos for a certain transaction (ZcashTransaction.Overview) defined by its rawID. 

## Fixed
- Swiftlint issues have been addressed.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2475000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2520000.json
````

# 2.1.7 - 2024-05-16

## Changed
- `minimumShieldingConfirmations` set to 1 instead of 10. This should fasten the time it takes to swap transparent funds to shielded ones.

# 2.1.6 - 2024-05-15

## Fixed
- The backend method proposeShielding checks the pointer for a null value before attempting to construct the Data with it. Without this check, proposeShielding would crash when there were either zero funds to shield or when the amount was less than the threshold defined by the client.

# 2.1.5 - 2024-04-18

## Changed
- Updated to `zcash-light-client-ffi` version 0.8.0. This includes a migration to
  ensure that the default Unified Address for existing wallets contains an Orchard
  receiver.
- This release includes a workaround for build and deployment issues related to
  a bug in XCode 15.3.

# 2.1.4 - 2024-04-17

## Changed
- The database locking mechanism has been changed to use async/await concurrency approach - the DBActor.

## Fixed
- Call of wipe() resets local (in memory) values.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2450000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2472500.json
````


Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2780000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2790000.json
````

# 2.1.3 - 2024-03-28

## Fixed
- Orchard subtree roots are now fetched alongside Sapling subtree roots.

# 2.1.2 - 2024-03-27

## Fixed
- Bug in note selection when sending to a transparent recipient.

# 2.1.1 - 2024-03-27

## Fixed
- Bug in an SQL query that prevented shielding of transparent funds.

# 2.1.0 - 2024-03-26

### [#1379] Fulfill Payment from a valid ZIP-321 request
New API implemented that allows clients to use a ZIP-321 Payment URI to create transaction.
```
func fulfillPaymentURI(
        _ uri: String,
        spendingKey: UnifiedSpendingKey
    ) async throws -> ZcashTransaction.Overview
```

Possible errors:
- `ZcashError.rustProposeTransferFromURI`
- Other errors that `sentToAddress` can throw

## Removed

- `SDKSynchronizer.latestUTXOs`

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2430000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2447500.json
````


Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2750000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2770000.json
````

# 2.0.11 - 2024-03-08

## Changed
- Migrated to `zcash-light-client-ffi 0.6.0`.

### [#1186] Enable ZIP 317 fees
- The SDK now generates transactions using [ZIP 317](https://zips.z.cash/zip-0317) fees,
  instead of a fixed fee of 10,000 Zatoshi. Use `Proposal.totalFeeRequired` to check the
  total fee for a transfer before creating it.

## Added

### [#1204] Expose APIs for working with transaction proposals
New `Synchronizer` APIs that enable constructing a proposal for transferring or
shielding funds, and then creating transactions from a proposal. The intermediate
proposal can be used to determine the required fee, before committing to producing
transactions.

The old `Synchronizer.sendToAddress` and `Synchronizer.shieldFunds` APIs have been
deprecated, and will be removed in 2.1.0 (which will create multiple transactions
at once for some recipients).

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2402500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2427500.json
````


Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2690000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2740000.json
````

# 2.0.10 - 2024-02-12

## Added

### [#1153] Allow runtime switch of lightwalletd servers
New API implemented that allows clients to change the `mainnet` endpoint. Use `func switchTo(endpoint: LightWalletEndpoint) async throws`.
Possible errors:
- `ZcashError.synchronizerServerSwitch` will perform a check to ensure that it's possible to communicate with the specified lightwalletd server, which may result in an an error. If this check fails, the user should be prompted to check the address, port and verify that the `address:port` format is respected.
- Switching endpoints causes a call to `synchronizer.Start()`, which may throw a `ZcashError`.

## Changed

### [#1369] SynchronizerState refactor and balances cleanup
`SynchronizerState` cleaned up and changed to provide only `AccountBalance`. This struct holds `saplingBalance: PoolBalance` which represents shielded balance for both total and spendable. Also holds `unshielded: Zatoshi` which represents transparent balance.

## Removed

### [#1369] SynchronizerState refactor and balances cleanup
- `WalletBalance` has been removed from the SDK, replaced with `AccountBalance`.
- `getTransparentBalance(accountIndex: Int)`, use `getAccountBalance(accountIndex: Int = 0)` instead
- `getShieldedBalance(accountIndex: Int)`, use `getAccountBalance(accountIndex: Int = 0)` instead
- `getShieldedVerifiedBalance(accountIndex: Int)`, use `getAccountBalance(accountIndex: Int = 0)` instead

# 2.0.9 - 2024-01-31

## Changed

### [#1363] Account balances in the SynchronizerState
`shieldedBalance: WalletBalance` has been replaced with `accountBalances: AccountBalance`. `AccountBalance` provides the same values as `shieldedBalance` but adds up a pending changes. Under the hood this calls rust's `getWalletSummary` which improved also the syncing initial values of % and balances.

## Added

### [#1153] Allow runtime switch of lightwalletd servers
New API implemented that allows clients to change the `mainnet` endpoint. Use `func switchTo(endpoint: LightWalletEndpoint) async throws`.
Possible errors:
- `ZcashError.synchronizerServerSwitch`: endpoint fails, check the address, port and format address:port,
- Some `ZcashError` related to `synchronizer.Start()`: the switch calls `start()` at the end and that is the only throwing function except the validation.

# 2.0.8 - 2024-01-30

Adopt `zcash-light-client-ffi 0.5.1`. This fixes a serialization problem
broke shielding.

# 2.0.7 - 2024-01-29

## Added
- `Model.ScanSummary`
- `Model.WalletSummary.{PoolBalance, AccountBalance, WalletSummary}`

## Changed
- The `ZcashError` type has changed.
  - Added variant `rustGetWalletSummary`
  - Removed variants:
    - `rustGetVerifiedBalance` (expect `rustGetWalletSummary` instead)
    - `rustGetScanProgress` (expect `rustGetWalletSummary` instead)
    - `rustGetBalance` (expect `rustGetWalletSummary` instead)
- The performance of `getWalletSummary` and `scanBlocks` have been improved.

# 2.0.6 - 2024-01-28

## Changed

### [#1346] Troubleshooting synchronization
We focused on performance of the synchronization and found out a root cause in progress reporting. Simple change reduced the synchronization significantly by reporting less frequently. This affect the UX a bit because the % of the sync is updated only every 500 scanned blocks instead of every 100. Proper solution is going to be handled in #1353.

### [#1351] Recover from block stream issues
Async block stream grpc calls sometimes fail with unknown error 14, most of the times represented as `Transport became inactive` or `NIOHTTP2.StreamClosed`. Unless the service is truly down, these errors are usually false positive ones. The SDK was able to recover from this error with the next sync triggered but it takes 10-30s to happen. This delay is unnecessary so we made 2 changes. When these errors are caught the next sync is triggered immediately (at most 3 times) + the error state is not passed to the clients.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2332500.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2382500.json
````


Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2640000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2690000.json
````

# 2.0.5 - 2023-12-15

## Added

### [#1336] Tweaks for sdk metrics
Shielded verified and total balances are logged for every sync of `SDKMetrics`.

## Checkpoints

Mainnet
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2330000.json

Testnet
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2630000.json

# 2.0.4 - 2023-12-12

## Changed
The `SDKMetrics` logs data using os_log. The public API `enableMetrics()` and `disableMetrics()` no longer exist. All metrics are automatically logged for every sync run. Extraction of the metrics is up to the client/dev - done by using `OSLogStore`.

## Added

### [#1325] Log metrics
The sync process is measured and detailed metrics are logged for every sync run. The data are logged using os_log so any client can export it. Verbose logs are under `sdkLogs_default` category, `default` level. Sync specific logs use `error` level.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2270000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2327500.json
````


Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2560000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2620000.json
````

# 2.0.3 - 2023-10-20

## Fixed

### [#1308] Enhancing seems to not process all ranges
The enhancing of the transactions now processes all the blocks suggested by scan ranges. The issue was that when new scan ranges were suggested the value that drives the enhancing range computation wasn't reset, so when higher ranges were processed, the lower ranges were skipped. This fix ensures all transaction data are properly set, as well as fixing eventStream `.foundTransaction` reporting.

### Fix incorrect note deduplication in v_transactions (librustzcash)
This is a fix in the rust layer. The amount sent in the transaction was incorrectly reported even though the actual amount was sent properly. Now clients should see the amount they expect to see in the UI.

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2250000.json
...
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2267500.json
````


Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2540000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2550000.json
````


# 2.0.2 - 2023-10-12

## Changed

### [#1303] Don't invalidate the timer with the error
The SDK has some simple logic of retrying when some erros occurs. There were 5 attempts of retry until the SDK stopped the synchronization process completely. (The timer is not restarted after those). That approach led to some annoying UX issue of manually starting the SDKSynchronizer from the client, shifting the responsibility to the devs/clients. This has been changed, the SDK never stops the timer unless `synchronizer.stop()` is called.

## Fixed

### [#1301] foundTransactions don't emit after rewind
The `.foundTransactions` observed on eventStream worked well during the sync until the rewind was called. That API missed reset of the ActionContext in the CompactBlockProcesser and that led to never observing the same transactions again. This ticket fixed the problem, reset is called in the rewind and new sync passes the transactions to the stream.

# 2.0.1 - 2023-10-03

## Changed

### [#1294] Remove all uses of the incorrect 1000-ZAT fee
The 1000 Zatoshi fee proposed in ZIP-313 is deprecated now and so the minimum is 10k Zatoshi, defined in ZIP-317.
The SDK has been cleaned up from deprecated fee but note, real fee is handled in a rust layer.
The public API `NetworkConstants.defaultFee(for: BlockHeight)` has been refactored to `NetworkConstants.defaultFee()`.

# 2.0.0 - 2023-09-25

## Notable Changes

This release updates `ZcashLightClientKit` to implement the Spend-Before-Sync fast
synchronization algorithm.

## Changed

Updated dependencies:
- `zcash-light-client-ffi 0.4.0`

`CompactBlockProcessor` now processes compact blocks from the lightwalletd server with Spend-before-Sync algorithm (i.e. non-linear order). This feature shortens the time after which a wallet's spendable balance can be used.

### [#1196] Check logging level priorities
The levels for logging have been updated according to Log Levels in Swift. (https://www.swift.org/server/guides/libraries/log-levels.html).
There's one naming difference, instead of `notice` we use `event`. So the order is debug, info, event, warning, error.

### [#1111] Change how the sync progress is stored inside the SDK

`Initializer` has now a new parameter called `generalStorageURL`. This URL is the location of the directory
where the SDK can store any information it needs. A directory doesn't have to exist. But the SDK must
be able to write to this location after it creates this directory. It is suggested that this directory is
a subdirectory of the `Documents` directory. If this information is stored in `Documents` then the
system itself won't remove these data.

Synchronizer's prepare(...) public API changed: `viewingKeys:
[UnifiedFullViewingKey]` has been removed and `for walletMode: WalletInitMode`
added. `WalletInitMode` is an enum with 3 cases: .newWallet, .restoreWallet and
.existingWallet. Use `.newWallet` when preparing the SDKSynchronizer for a
brand new wallet that has been generated. Use `.restoreWallet` when wallet is
about to be restored from a seed and `.existingWallet` for all other scenarios.

## Removed

### [#1181] Correct computation of progress for Spend before Sync
`latestScannedHeight` and `latestScannedTime` have been removed from `SynchronizerState`. With multiple algorithms
of syncing the amount of data provided is reduced so it's consistent. Spend before Sync is done in non-linear order
so both Height and Time don't make sense anymore.

### [#1230] Remove linear sync from the SDK

- `latestScannedHeight` and `latestScannedTime` have been removed from the
  SynchronizerState.
- The concept of pending transaction has changed: `func allPendingTransactions()`
  is no longer available. Use `public func allTransactions()` instead.

# 0.22.0-beta

## Checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2057500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2060000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2062500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2065000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2067500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2070000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2072500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2075000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2077500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2080000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2082500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2085000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2087500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2090000.json
````


Testnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2320000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2330000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2340000.json
````

## Fixed

- [#1037] Empty string memo throws `ZcashError.memoTextInputEndsWithNullBytes`
- [#1016] Rebuild download stream periodically while downloading
This fixes a memory consumption issue coming from GRPC-Swift.
- [#1019] Memo has trailing garbled text

### [#1111] Change how the sync progress is stored inside the SDK

`Initializer` has now a new parameter called `generalStorageURL`. This URL is the location of the directory
where the SDK can store any information it needs. A directory doesn't have to exist. But the SDK must
be able to write to this location after it creates this directory. It is suggested that this directory is
a subdirectory of the `Documents` directory. If this information is stored in `Documents` then the
system itself won't remove these data.

### [#1019] Memo has trailing garbled text

Changes the way unpadded bytes are turned into a UTF-8 Swift String
without using cString assuming APIs that would overflow memory and
add garbled trailing bytes.

- [#781] This fixes test `testMaxAmountMinusOneSend` by creating two separate tests:
  - testMaxAmountMinusOneSendFails
  - testMaxAmountSend

Also includes new functionality that tracks sent transactions so
that users can be notified specifically when they are mined and uses "idea B" of
issue #1033.

closes #1033
closes #781

### [#1001] Remove PendingDb in favor of `v_transactions` and `v_tx_output` Views

## Changed

- `WalletTransactionEncoder` now uses a `LightWalletService` to submit the
encoded transactions.

- Functions returning or receiving `ZcashTransaction.Sent` or `ZcashTransaction.Received` now
will be simplified by returning `ZcashTransaction.Overview` or be replaced by their Overview
counterparts

## Added

- `ZcashTransaction.Overview` can be checked for "pending-ness" by calling`.isPending(latestHeight:)` latest height must be provided so that minedHeight
  can be compared with the lastest and the `defaultStaleTolerance` constant.

- `TransactionRecipient` is now a public type.

- `ZcashTransaction.Output` can be queried to know the inner details of a
  `ZcashTransaction.Overview`. It will return an array with all the tracked
  outputs for that transaction so that they can be shown to users who request them

- `ZcashTransaction.Overview.State` is introduced to represent `confirmed`,
  `pending` or `expired` states. This State is relative to the current height
  of the chain that is passed to the function `getState(for currentHeight: BlockHeight)`.

State should be a transient value and it's not adviced to store it unless
transactions have stale values such as `confirmed` or `expired`.

### Synchronizer Changes

- `public func getTransactionOutputs(transaction) async -> [ZcashTransaction.Output]` is added to
get the outputs related to the given transaction. You can use this to know every detail of the
transaction Overview and show it in a more fine-grained UI.

- `TransactionRecipient` is returned on `getRecipients(for:)`.

## Renamed

- `AccountEntity` called `Account` is now `DbAccount`

## Removed

- `ZcashTransaction.Received` and `ZcashTransaction.Sent` are removed
  and replaced by `Overview` since the notion of Sent and received is
  not entirely applicable to Zcash transactions where value can be
  sent and received at the same time. Transactions with negative value
  will be considered as "sent" but that won't be enforced with a type
  anymore
- `cancelSpend()`: support for cancel spend was removed since its
  completion was not guaranteed
- `PendingTransactionEntity` and all of its related components.
  Pending items are still tracked and visualized by the existing APIs
  but they are retrieved from the `TransactionRepository` instead by
  returning `ZcashTransaction.Overview` instead.
- `pendingDbURL` is removed from every place it was required. Its
  deletion is responsibility of wallet developers.
- `ClearedTransactions` are now just `transactions`.`MigrationManager`
  is deleted. Now all migrations are in charge of the rust welding layer.
- `PendingTransactionDao.swift` is removed.
- `PendingTransactionRepository` protocol is removed.
- `TransactionManagerError`
- `PersistentTransactionManager`
- `OutboundTransactionManager` is deleted and replaced by `TransactionEncoder`
  which now incorporates `submit(encoded:)` functionality
- `DatabaseMigrationManager` is remove since it's no longer needed all Database
  migrations shall be hanlded by the rust layer.
- `ZcashSDK.defaultPendingDbName` along with any sibling members
- `TransactionRepository`
    - `findMemos(for receivedTransaction: ZcashTransaction.Received)`
    - `findMemos(for sentTransaction: ZcashTransaction.Sent)`

### [#1013] Enable more granular control over logging behavior

Now the SDK allows for more fine-tuning of its logging behavior. The `LoggingPolicy` enum
provides for three options: `.default(OSLogger.LogLevel)` wherein the SDK will use its own logger, with the option
to customize the log level by passing an `OSLogger.LogLevel` to the enum case.
`custom` allows one to pass a custom `Logger` implementation for completely customized logging.
Lastly, `noLogging` disables logging entirely.

To utilize this new configuration option, pass a `loggingPolicy` into the `Initializer`. If unspecified, the SDK
will utilize an internal `Logger` implementation with an `OSLogger.LogLevel` of `.debug`

### [#442] Implement parallel downloading and scanning

The SDK now parallelizes the download and scanning of blocks. If the network connection of the client device is fast enough then the scanning
process doesn't have to wait for blocks to be downloaded. This makes the whole sync process faster.

`Synchronizer.stop()` method is not async anymore.

### [#361] Redesign errors inside the SDK

Now the SDK uses only one error type - `ZcashError`. Each method that throws now throws only `ZcashError`.
Each publisher (or stream) that can emit error now emitts only `ZcashError`.

Each symbol in `ZcashError` enum represents one error. Each error is used only in one place
inside the SDK. Each error has assigned unique error code (`ZcashErrorCode`) which can be used in logs.

# 0.21.0-beta

## Checkpoints

Mainnet:

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2032500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2035000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2037500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2040000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2042500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2045000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2047500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2050000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2052500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2055000.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2290000.json
````

### [#969] Clear cache on error to avoid discontinuities when verifying
This change drops the file-system cache whenever an error occurs when storing blocks
so that there is not a discontinuity in the cached block range that could cause a
discontinuity error on libzcashlc when calling `scan_blocks`. This will have a setback of
at most 100 blocks that would have to be re-downloaded when resuming sync.

### [#959] and [#914] Value of outbound transactions does not match user intended tx input

This change switches to a new (future) version of the rust crates that will get
rid of the sent and received transactions Views in favor of a v_transaction
view that will do better accounting of outgoing and incoming funds.
Additionally it will support an outputs view for seeing the inner details of
transactions enabling the SDKs tell the users the precise movement of value
that a tx causes in its multiple possible ways according to the protocol.

the v_tx_outputs view is not yet implemented.

Sent and Received transaction sub-types are kept for compatibility purposes but
they are generated from Overviews instead of queried from a specific view.

In the transaction Overview the value represents the whole value transfer for
the transaction from the point of view of a given account including fees. This
means that the value for a single transaction Overview struct represents the
addition or subtraction of ZEC value to the account's balance.

Future updates will give clients the possibility to drill into the inner
workings of those value changes in a per-output basis for each transaction.

Also, the field pending_unmined field was added to v_transactions so that
wallets can query DataDb for pending but yet unmined txs

This will prepare the field for removing the notion of a "PendingDb" and its nuances.

### [#888] Updates to layer between Swift and Rust

This is mostly internal change. But it also touches the public API.

`KeyDeriving` protocol is changed. And therefore `DerivationTool` is changed. `deriveUnifiedSpendingKey(seed:accountIndex:)` and
`deriveUnifiedFullViewingKey(from:)` methods are now async. `DerivationTool` offers alternatives for these methods. Alternatives are using either
closures or Combine.

### [#469] ZcashRustBackendWelding to Async

This is mostly internal change. But it also touches the public API.

These methods previously returned Optional and now those methods return non-optional value and those methods can an throw error:
- `getSaplingAddress(accountIndex: Int) async throws -> SaplingAddress`
- `func getUnifiedAddress(accountIndex: Int) async throws -> UnifiedAddress`
- `func getTransparentAddress(accountIndex: Int) async throws -> TransparentAddress`

These methods are now async:
- `func getShieldedBalance(accountIndex: Int) async throws -> Zatoshi`
- `func getShieldedVerifiedBalance(accountIndex: Int) async throws -> Zatoshi`

`Initializer` no longer have methods to get balance. Use `SDKSynchronizer` (or it's alternative APIs) to get balance.


# 0.20.0-beta

## Checkpoints:

Mainnet:

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2012500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2015000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2017500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2020000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2022500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2025000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2027500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2030000.json
````

Testnet:

````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2260000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2270000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2280000.json
````

## Deprecations made effective:

Synchronizer:
- `func getShieldedBalance(accountIndex: Int) -> Int64`
- `func getShieldedVerifiedBalance(accountIndex: Int) -> Int64`
  use the API retuning Zatoshi instead. If needed `zatoshi.amount` would return an
  Int64 value.

Initializer:
- `func getBalance(account index: Int = 0) -> Int64`
  use the API retuning Zatoshi instead. If needed `zatoshi.amount` would return an
  Int64 value.
- `func getVerifiedBalance(account index: Int = 0) -> Int64`
  use the API retuning Zatoshi instead. If needed `zatoshi.amount` would return an
  Int64 value.

ZcashSDK.NetworkConstants:
- `func defaultFee(for height: BlockHeight) -> Int64`
  use the API retuning Zatoshi instead. If needed `zatoshi.amount` would return an
  Int64 value.

ZcashRustBackendWelding:
- `func getReceivedMemoAsUTF8(dbData:idNote:networkType:) -> String?`
  Use `getReceivedMemo(dbData:idNote:networkType)` instead
- `func getSentMemoAsUTF8(dbData:idNote:networkType:) -> String?`
  Use `getSentMemo(dbData:idNote:networkType)` instead

## Changed

### [#209] Support Initializer Aliases

Added `ZcashSynchronizerAlias` enum which is used to identify an instance of the `SDKSynchronizer`. All the paths
to all resources (databases, filesystem block storage...) are updated automatically inside the SDK according to the
alias. So it's safe to create multiple instances of the `SDKSynchronizer`. Each instance must have unique Alias. If
the `default` alias is used then the SDK works the same as before this change was introduced.

The SDK now also checks which aliases are used and it prevents situations when two instances of the `SDKSynchronizer`
has the same alias. Methods `prepare()` and `wipe()` do checks for used alias. And those methods fail
with `InitializerError.aliasAlreadyInUse` if the alias is already used.

If the alias check fails in the `prepare()` method then the status of the `SDKSynchronizer` isn't switched from `unprepared`.
These methods newly throw `SynchronizerError.notPrepared` error when the status is `unprepared`:
- `sendToAddress(spendingKey:zatoshi:toAddress:memo:) async throws -> PendingTransactionEntity`
- `shieldFundsspendingKey:memo:shieldingThreshold:) async throws -> PendingTransactionEntity`
- `latestUTXOs(address:) async throws -> [UnspentTransactionOutputEntity]`
- `refreshUTXOs(address:from:) async throws -> RefreshedUTXOs`
- `rewind(policy:) -> AnyPublisher<Void, Error>`

Provided file URLs to resources (databases, filesystem block storage...) are now parsed inside the SDK and updated
according to the alias. If some error during this happens then `SDKSynchronzer.prepare()` method throws
`InitializerError.cantUpdateURLWithAlias` error.

### [#831] Add support for alternative APIs

There are two new protocols (`ClosureSynchronizer` and `CombineSynchronizer`). And there are two new
objects which conform to respective protocols (`ClosureSDKSynchronizer` and `CombineSDKSynchronizer`). These
new objects offer alternative API for the `SDKSynchronizer`. Now the client app can choose which technology
it wants to use to communicate with Zcash SDK and it isn't forced to use async.

These methods in the `SDKSynchronizer` are now async:
- `prepare(with:viewingKeys:walletBirthday:)`
- `start(retry:)`
- `stop()`
- `cancelSpend(transaction:)`
- All the variants of the `getMemos(for:)` method.
- All the variants fo the `getRecipients(for:)` method.
- `allConfirmedTransactions(from:limit:)`

These properties in the `SDKSynchronizer` are now async:
- `pendingTransactions`
- `clearedTransactions`
- `sentTransactions`
- `receivedTransactions`

Non async `SDKsynchronizer.latestHeight(result:)` were moved to `ClosureSDKSynchronizer`.

### [#724] Switch from event based notifications to state based notifications

The `SDKSynchronizer` no longer uses `NotificationCenter` to send notifications.
Notifications are replaced with `Combine` publishers. Check the migrating document and
documentation in the code to get more information.

### [#826] Change how the SDK is initialized

- `viewingKeys` and `walletBirthday` are removed from `Initializer` constuctor. These parameters moved to
  `SDKSynchronizer.prepare` function.
- Constructor of the `SDKSynchronizer` no longer throws exception.
- Any value emitted from `lastState` stream before `SDKSynchronizer.prepare` is called has `latestScannedHeight` set to 0.
- `Initializer.initialize` function isn't public anymore. To initialize SDK call `SDKSynchronizer.prepare` instead.


# 0.19.1-beta

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2002500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2005000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2007500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2010000.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2250000.json
````

## Fixed

### [#821] `failedToWriteMetadata` at sync startup

No public API changes.

Adds `func shouldClearBlockCacheAndUpdateInternalState() -> BlockHeight?` to `SyncRanges`
so that the compact block processor can advert internal states that are not consistent and
recover from such state.

For concrete examples check out the tests in:
`Tests/NetworkTests/CompactBlockProcessorTests.swift`

## Deleted
Removed linter binary from repository

# 0.19.0-beta

### [#816] Improve how rewind call can be used

`SDKSynchronizer.rewind(policy:)` function can be now called anytime. It returns `AnyPublisher` which
completes or fails when the rewind is done. For more details read the documentation for this method
in the code.

### [#801] Improve how wipe call can be used

`SDKSynchronizer.wipe()` function can be now called anytime. It returns `AnyPublisher` which
completes or fails when the wipe is done. For more details read the documentation for this method
in the code.

### [#793] Send synchronizerStopped notification only when sync process stops

`synchronizerStopped` notification is now sent after the sync process stops. It's
not sent right when `stop()` method is called.

### [#795] Include sapling-spend file into bundle for tests

This is only an internal change and doesn't change the behavior of the SDK. `Initializer`'s
constructor has a new parameter `saplingParamsSourceURL`. Use `SaplingParamsSourceURL.default`
value for this parameter.

### [#764] Refactor communication between components inside th SDK

This is mostly an internal change. A consequence of this change is that all the notifications
delivered via `NotificationCenter` with the prefix `blockProcessor` are now gone. If affected
notifications were used in your code use notifications with the prefix `synchronizer` now.
These notifications are defined in `SDKSynchronizer.swift`.

### [#759] Remove Jazz-generated HTML docs

We remove these documents since they are outdated and we rely on the docs in the code itself.

### [#726] Modularize GRPC layer

This is mostly internal change. `LightWalletService` is no longer public. If it
is used in your code replace it by using `SDKSynchronizer` API.

### [#770] Update GRPC swift library
This updates to GRPC-Swift 1.14.0.

## Checkpoints added:

Mainnet:
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1965000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1967500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1970000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1972500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1975000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1977500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1980000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1982500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1985000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1987500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1990000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1992500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1995000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1997500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/2000000.json
````

Testnet:
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2210000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2220000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2230000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2240000.json
````
## File system backed block cache

File system based block cache. Compact blocks will now be stored
on the file system. Caller must provide a `URL` pointing to the
filesystem root directory where the fsBlock cache is. this directory
is expected to contain a `/blocks` sub-directory with the blocks stored
in the convened filename format `{height}-{hash}-compactblock`. This directory
must be granted both read and write permissions.

the file system cache will have a "bookkeeping" database that the rust
welding layer will use to know the state of the cache and locate the
cached compact blocks. This directory can be deleted provided that the
Compactblock processor or Synchronizer are not running. Upon deletion
caller is responsible for initializing these objects for the cache to
be created.

Implementation notes: Users of the SDK will know the path they will
provide but must assume no behavior whatsoever or rely on the cached
information in any way, since it is an internal state of the SDK.
Maintainers might provide no support for problems related to speculative
use of the file system cache. If you consider your application needs any
other information than the one available through public APIs, please
file the corresponding feature request.

### Added

- `Synchronizer.shieldFunds(spendingKey:memo:shieldingThreshold)` shieldingThreshold
was added allowing wallets to manage their own shielding policies.

### Removed
- `InitializerError.cacheDbMigrationFailed`

### Deprecations
CacheDb references that were deprecated instead of **deleted** are pointing out
that they should be useful for you to migrate from using cacheDb.

- `ResourceProvider.cacheDbURL` deprecated but left for one release cycle for clients
to move away from cacheDb.

- `NetworkConstants.defaultCacheDbName` deprecated but left for one release cycle for clients
to move away from cacheDb.

## Other Issues Fixed by this PR:

### [#587] ShieldFundsTests:
 - https://github.com/zcash/ZcashLightClientKit/issues/720
 - https://github.com/zcash/ZcashLightClientKit/issues/587
 - https://github.com/zcash/ZcashLightClientKit/issues/667

### [#443] Delete blocks from cache after processing them
    Closes https://github.com/zcash/ZcashLightClientKit/issues/443
### [#754] adopt name change in libzashlc package that fixes a deprecation in SPM
    Closes https://github.com/zcash/ZcashLightClientKit/issues/754

# 0.18.1-beta
### [#767] implement getRecipients() for Synchronizer.

This implements `getRecipients()` function which retrieves the possible
recipients from a sent transaction. These can either be addresses or
internal accounts depending on the transaction being a shielding tx
or a regular outgoing transaction.

Other changes:
- Fix version of zcash-light-client-ffi to 0.1.1
- Enhance error reporting on a test make Mock comply with protocol

# 0.18.0-beta

## Farewell Cocoapods.
### [#612] Remove Support for Cocoapods (#706)

It wouldn't have been possible to release an SDK without you, pal.

We are moving away from Cocoapods since our main dependencies, SwiftGRPC
and SWIFT-NIO are. We don't have much of a choice.

We've been communicating this for a long time. Although, if you really need Cocoapods,
please let us know by opening an issue in our repo and we'll talk about it.


### Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1937500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1940000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1942500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1945000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1947500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1950000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1952500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1955000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1957500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1960000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1962500.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2180000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2190000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2200000.json
````

## Bugfixes

### [#645] Default rewind after ReOrg is 20 blocks when it should be 10
This fixes an issue where the default reorg was 20 blocks rewind instead of 10. The
reorg count was incremented before calling the rewind height computing function.

## Use Librustzcash database views to query and represent transactions

### [#556] Change data structures which represent transactions.

These data types are gone: `Transaction`, `TransactionEntity`, `ConfirmedTransaction`,
`ConfirmedTransactionEntity`. And these data types were added: `ZcashTransaction.Overview`,
`ZcashTransaction.Received`, `ZcashTransaction.Sent`.

New data structures are very similar to the old ones. Although there many breaking changes.
The APIs of the `SDKSynchronizer` remain unchanged in their behavior. They return different
data types. **When adopting this change, you should check which data types are used by methods
of the `SDKSynchronizer` in your code and change them accordingly.**

New transaction structures no longer have a `memo` property. This responds to the fact that
Zcash transactions can have either none or multiple memos. To get memos for the transaction
the `SDKSynchronizer` has now new methods to fetch those:
- `func getMemos(for transaction: ZcashTransaction.Overview) throws -> [Memo]`,
- `func getMemos(for receivedTransaction: ZcashTransaction.Received) throws -> [Memo]`
- `func getMemos(for sentTransaction: ZcashTransaction.Sent) throws -> [Memo]`

## CompactBlockProcessor is now internal
### [#671] Make CompactBlockProcessor Internal.


The CompactBlockProcessor is no longer a public class/API. Any direct access will
end up as a compiler error. Recommended way how to handle things is via `SDKSynchronizer`
from now on. The Demo app has been updated accordingly as well.

## We've changed how we download and scan blocks. Status reporting has changed.

### [#657] Change how blocks are downloaded and scanned.

In previous versions, the SDK first downloaded all the blocks and then it
scanned all the blocks. This approach requires a lot of disk space. The SDK now
behaves differently. It downloads a batch of blocks (100 by default), scans those, and
removes those blocks from the disk. And repeats this until all the blocks are processed.

`SyncStatus` was changed. `.downloading`, `.validating`, and `.scanning` symbols
were removed. And the `.scanning` symbol was added. The removed phases of the sync
process are now reported as one phase.

Notifications were also changed similarly. These notifications were
removed: `SDKSynchronizerDownloading`, `SDKSyncronizerValidating`, and `SDKSyncronizerScanning`.
And the `SDKSynchronizerSyncing` notification was added. The added notification replaces
the removed notifications.

## New Wipe Method to delete wallet information. Use with care.

### [#677] Add support for wallet wipe into SDK. Add new method `Synchronizer.wipe()`.

## Benchmarking APIs: A primer

### [#663] Foundations for the benchmarking/performance testing in the SDK.

This change presents 2 building blocks for the future automated tests, consisting
of a new SDKMetrics interface to control flow of the data in the SDK and
new performance (unit) test measuring synchronization of 100 mainnet blocks.

# 0.17.6-beta

### [#756] 0.17.5-beta updates to libzcashlc 0.2.0 when it shouldn't

Updated checkpoints to the ones present in 0.18.0-beta

# 0.17.5-beta

Update checkpoints

Mainnet

````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1912500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1915000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1917500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1920000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1922500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1925000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1927500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1930000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1932500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1935000.json
````

Tesnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2150000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2160000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2170000.json
````

# 0.17.4-beta

### [#665] Fix testShieldFunds() `get_transparent_balance` error
updates `libzcashlc` to `0.1.1` to fix an error where getting a
transparent balance on an empty database would fail.

# 0.17.3-beta

### [#646] SDK sync process resumes to previously saved block height
This change adds an internal storage test on UserDefaults that tells the
SDK where sync was left off when cancelled whatever the reason for it
to restart on a later attempt. This fixes some issues around syncing
long block ranges in several attempts not enhancing the right transactions
because the enhancing phase would only consider the last range scanned.
This only fixes the situation where rewinding the SDK would cause the
whole database to be cleared instead and syncing to be restarted from
scratch (issue [#660]).

- commit `3b7202c` Fix `testShieldFunds()` dataset loading issue. (#659)

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1897500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1900000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1902500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1905000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1907500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1910000.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2140000.json
````

New Checkpoint for `testShieldFunds()`
```
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1631000.json
```

# 0.17.2-beta

- [#660] Fix the situation when any rewind causes full rescan

# 0.17.1-beta

- [#651] Change the rewind behavior. Now if the rewind is used while the sync process is in progress then an exception is thrown.
- [#616] Download Stream generates too many updates on the main thread
  **WARNING**: Notifications from SDK are no longer delivered on main thread.
- [#585] Fix RewindRescanTests (#656)
- Cleanup warnings (#655)
- [#637] Make sapling parameter download part of processing blocks (#650)
- [#631] Verify SHA1 correctness of Sapling files after downloading (#643)
- Add benchmarking info to SyncBlocksViewController (#649)
- [#639] Provide an API to estimate TextMemo length limit correctly (#640)
- [#597] Bump up SQLite Swift to 0.14.1 (#638)
- [#488] Delete cache db when sync ends

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1882500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1885000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1887500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1890000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1892500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1895000.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2120000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2130000.json
````

# 0.17.0-beta

- [#321] Validate UA
- [#384] Adopt Type Safe Memos in the FFI and SDK
- [#355] Update lib.rs to lastest librustzcash master
- [#373] Demo App shows ZEC balances in scientific notation
- [#380] One of the initAccountsTable() is dead code (except for tests)
- [#374] XCTest don't load Resources from the module's bundle
- [#375] User can't go twice in a row to SendFundsViewController
- [#490] Rebase long dated PRs on top of the feature branches
- [#510] Change references of Shielded address to Sapling Address
- [#511] Derivation functions should only return a single resul
- [#512] Remove derivation of t-address from pubkey
- [#520] Use UA Test Vector for Recipient Test
- [#544] Change Demo App to use USK and new rolling addresses
- [#602] Improve error logging for InitializerError and RustWeldingError
- [#579] Fix database lock
- [#595] Update Travis to use Xcode 14
- [#592] Fix various tests and deleted some that are not useful anymore
- [#523] Make a CompactBlockProcessor an Actor
- [#593] Fix testSmallDownloadAsync test
- [#577] Fix: reduce batch size when reaching increased load part of the chain
- [#575] make Memo and MemoBytes parameters nullable so they can be omitted  when sending to transparent receivers.
- commit `1979e41` Fix pre populated Db to have transactions from darksidewalletd seed
- commit `a483537` Ensure that the persisted test database has had migrations applied.
- commit `1273d30` Clarify & make regular how migrations are applied.
- commit `78856c6` Fix: successive launches of the application fail because the closed range of the migrations to apply would be invalid (lower range > that upper range)
- commit `7847a71` Fix incorrect encoding of optional strings in PendingTransaction.
- commit `789cf01` Add Fee field to Transaction, ConfirmedTransaction, ReceivedTransactions and Pen dingTransactions. Update Notes DAOs with new fields
- commit `849083f` Fix UInt32 conversions to SQL in PendingTransactionDao
- commit `fae15ce` Fix sent_notes.to_address column reference.
- commit `23f1f5d` Merge pull request #562 from zcash/fix_UnifiedTypecodesTests
- commit `30a9c06` Replace `db.run` with `db.execute` to fix migration issues
- commit `0fbf90d` Add migration to re-create pending_transactions table with nullable columns.
- commit `36932a2` Use PendingTransactionEntity.internalAccount for shielding.
- commit `f5d7aa0` Modify PendingTransactionEntity to be able to represent internal shielding tx.
- [#561] Fix unified typecodes tests
- [#530] Implement ability to extract available typecodes from UA

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1872500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1875000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1877500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1880000.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2110000.json
````

# 0.17.0-beta.rc1

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1852500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1855000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1857500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1860000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1862500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1865000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1867500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1870000.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2020000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2030000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2040000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2050000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2060000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2070000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2080000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2090000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2100000.json
````

# 0.17.0-alpha.5

- update to libzcashlc 0.1.0-beta.3. This fixes an issue spending change notes

# 0.17.0-alpha.4

- update to libzcashlc 0.1.0-beta.2

# 0.17.0-alpha.3

- [#602] Improve error logging for InitializerError and RustWeldingError

# 0.17.0-alpha.2

- [#579] Fix database lock
- [#592] Fix various tests and deleted some that are not useful anymore
- [#581] getTransparentBalanceForAccount error not handled

# 0.17.0-alpha.1

See MIGRATING.md

# 0.16-13-beta

- [#597] SDK does not build with SQLite 0.14

# 0.16.12-beta

## Checkpoints added:

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1832500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1835000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1837500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1840000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1842500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1845000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1847500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1850000.json
````

# 0.16.11-beta

## Checkpoints added:

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1812500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1815000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1817500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1820000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1822500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1825000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1827500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1830000.json
````

# 0.16.10-beta

- [#532] [0.16.x-beta] Download does not stop correctly

  Issue Reported:

  When the synchronizer is stopped, the processor does not cancel
  the download correctly. Then when attempting to resume sync, the
  synchronizer is not on `.stopped` and can't be resumed

  this doesn't appear to happen in `master` branch that uses
  structured concurrency for operations.

  Fix:

  This commit makes sure that the download streamer checks cancelation
  before processing any block, or getting called back to report progress

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1807500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1810000.json
````

# 0.16.9-beta

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1787500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1790000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1792500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1795000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1797500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1800000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1802500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1805000.json
````

# 0.16.8-beta

## Checkpoints added

Mainnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1775000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1777500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1780000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1782500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1785000.json
````

Testnet
````
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2000000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/2010000.json
````

# 0.16.7-beta

- [#455] revert queue priority downgrade changes from [#435] (#456)
  This reverts queue priority changes from commit `a5d0e447748257d2af5c9101391dd05a5ce929a2` since we detected it might prevent downloads to be scheduled in a timely fashion

## Checkpoints added

Mainnet
```
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1757500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1760000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1762500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1765000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1767500.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1770000.json
Sources/ZcashLightClientKit/Resources/checkpoints/mainnet/1772500.json
```

Testnet
```
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/1980000.json
Sources/ZcashLightClientKit/Resources/checkpoints/testnet/1990000.json
```

# 0.16.6-beta

- There's a problem with 0.16.5-beta hash. Re-releasing

# 0.16.5-beta

- [#449] Use CompactBlock Streamer download instead of batch downloads (#451)
  This increases the speed of downloads significantly while reducing the memory footprint.

- [#435] thread sanitizer issues (#448)
  Issues related to Thread Sanitizer warnings

- Adds new Checkpoint for 1755000 on mainnet

# 0.16.4-beta

- [#444] Syncing Restarts to zero when the wallet is wiped and synced from zero in one go. (#445)
- [#440] Split constants for Download Batches and Scanning Batches (#441)
  This change was done to aleviate memory load when downloading large blocks.
  Default download batch constant is deprecated in favor of `DefaultDownloadBatch` and
  `DefaultScanningBatch`

# 0.16.3-beta

- [#436] Add checkpoint with a lower interval on mainnet (#437)
  This adds checkpoint at a 2500 block interval to help reduce scan times
  on new wallets

# 0.16.2-beta

- [#418] "Swift.EncodingError.InvalidValue Encoding an Int64 is not supported" (#430)
  This fixes Cocoapods clients pointing to SQLite 0.12.2 instead of using 0.13
  the former does not support custom encoding of Int64 and makes Zatoshi break

- [#428] make some helpers publicly accessible (#429)

# 0.16.1-beta

- [#422] Make Zatoshi extensions of `NSDecimalNumber` public (#423)
- [#419] Fix Unavailable Transport 14 when attempting to sync (#426)
  this changes timeout settings and Keepalive changes.
  Recommended settings for lightwalletd.com are 60000ms singleCallTimeout
  on `LightWalletEndpoint`

- [#416] Update GRPC to 1.8.2 (#421)

# 0.16.0-beta

This version changes the way wallet birthdays are handled. `WalletBirthday`
struct is not longer public and has been renamed to `Checkpoint`.

`SynchronizerError` has a default `LocalizedError` compliance to
help debug errors and display them to the user. This is a workaround
to get rid of cryptic errors that are being reported to maintainers and
are subject to change in future versions.

- [#392] Synchronizer error 8. when syncing. (#413)
- [#398] Make WalletBirthday an internal type (#414)
- [#411] add Fresh checkpoints for release 0.16.0-beta (#412)
- [#406] some BirthdayTests fail for MacOS target (#410)
- [#404] Configure GRPC KeepAlive according to docs (#409)

# 0.15.1-beta (hotfix)

- [#432] create 0.15.1-beta with SQLite 0.13
  this build is a hotfix for cocoapods. which has the wront SQLite dependency
  It moves it from 0.12.2 to 0.13

# 0.15.0-beta

** IMPORTANT ** This version no longer supports iOS 12
We've made a decision to make iOS 13 the minimum deployment target
in order to adopt and support Structured Concurrency and other important features
of the Swift language like Combine.

- [#363] bump iOS minimum deployment target to iOS 13.0 (#407)
- [#381] Move Zatoshi and Amount Types to the SDK (#396)
  This deprecates many methods on `SDKSynchronizer` using Zatoshi for amounts
  instead of `Int64`. This exposes number formatters that conveniently provide
  decimal conversion from `Zatoshi` to "human-readable" ZEC decimal numbers.

- [#397] Checkpoint format that supports NU5 TreeStates (#399)
  `WalletBirthday` now have both `saplingTree` and `orchardTree` values. The
  latter being Optional for checkpoints prior to Orchard activation height.

- [#401] DecodingError when refreshing pending transactions (#402)
- [#394] Update swift-grpc to 1.8.0 (#395)

# 0.14.0-beta

- [#388] Integrate libzcashlc 0.0.3 to support v5 transaction parsing on NU5 activation

# 0.13.1-beta

- [#326] Load Checkpoint files from bundle.
  This is great news! now checkpoints are loaded from files on the bundle instead of
  hardcoding them on source files. This will make updates easier, less error prone,
  and mostly automatable.

- PR #356 Adds a caveat to SPM / Xcode integration in Readme
- [#367] Darksidewalletd for testing `shield_funds`
- [#351] Write a Commit message Section for CONTRIBUTING.md

# 0.13.0-beta.2

- [Enhancement] Fix: make BlockProgress `.nullProgress` static property public for ECC Reference Wallet CombineSynchonizer

# 0.13.0-beta.1

- [Enhancement] PR #338. Rust-less build. Check for new documentation on how to benefit from this huge change
- [Enhancement] Swift Package Manager Support!

# 0.12.0-beta.6

- [Enhancement] Fresh checkpoints

# 0.12.0-beta.5

- FIX fixes to Apple Silicon M1 builds

# 0.12.0-beta.4

- Fix: add parameter to ensure 10 confs when shielding.

# 0.12.0-beta.2

- [Fix] Issue #293 MaxAttemptsReached error surfaces when it's actually dismissable and the wallet is working fine
- [Enhancement] Add test to verify that a checksum invalid t-address fails to validate.

# 0.12.0-alpha.11

- [Enhancement] Network Agnostic build

#  0.12.0-alpha.10

- Fix: UNIQUE Constraint bug when coming from background. fixed and logged warning to keep investigating
- [New] latestScannedHeight added to SDKSynchronizer

# 0.12.0-alpha.9

- CompactBlockProcessor states don't propagate correctly

# 0.12.0-alpha.8

- target height reporting enhancements

# 0.12.0-alpha.7

- improve status publishing for SDKSynchronizer
- [FIX] missingStartHeight error when scanning from sapling activation

# 0.12.0-alpha.6

- Make sapling parameters default url public

# 0.12.0-alpha.5

- add output files to build phase to avoid CI failures
- fix lint warnings

# 0.12.0-alpha.4

- Tests
- [Fix] Issue #289 main thread lock when validating the server
- [Fix] info single call times out all the time
- make sure operations cancel in a timely manner
- FigureNextBatchOperation.swift tests
- make range function static

# tag: 0.12.0-alpha.3

- getInfo service times out too soon

# 0.12.0-alpha.2

- FIX: processor stalls on reconnection
- Fix warnings

# 0.12.0-alpha.1

- Replace Status for SyncStatus
- fix tests
- Fix Demo App
- fetch operation does not cancel when the previous operations do
- Fix: operations start when they have been canceled already
- fix progress being > 1
- Synchronizing by phases, preview
- Add fetch UTXO operation to compact block processor
- CompactBlock batch download and stream download operation tests pass.

# 0.11.2

- [FIX] Fix build for Apple Silicon (M1) #285 by @ealymbaev

# 0.11.1

- [Enhancement] Rewind API has a `.quick` option

# 0.11.0

- [New] Shield Funds Feature
- [New] Get Transparent Balance for account
- [New] Z -> T Restore: transactions to transparent addresses are now restored when the user restores from seed or re-scans the wallet
- [New] [Preview] Unified Viewing Key Structure
- [New] Abstractions over Transparent Address and ShieldedAddress
- [FIX] `CompactBlockProcessor` validates LightdInfo from Lightwalletd
- [Enhancement] Add BlockTime to SDKSynchronizer updates
- [New] Db Migration for UVKs
- [FIX] Rewind API breaks on quick re-scan
- [Update] 37f2232: Update to gRPC-Swift 1.0.0

# 0.10.2

- Adds Mainnet and Testnet Checkpoints

# 0.10.1

- Adds Mainnet Checkpoints

# 0.10.0

- [critical] Fix #255 #261 outgoing no-change transactions not reported as mined
- [NEW] Rewind API. Allow Wallet developers to rewind synchronizer and (eventually) rescan
- [NEW] Rust Welding 0.0.6 - using rust crates 0.5 and Data Access API
- [NEW] updated Logger API to use StaticString on line and function as many logging libraries do
- [FIX] Mac OS BIG SUR build fixed

# 0.9.4

- New: added viewing key derivation to Derivation Tool
- Issue #252 - blockheight progress is latest height instead of upperbound of last scanned range

# 0.9.3

- added new checkpoints for mainnet

# 0.9.2

- Fix: memo string handling

# 0.9.1

- Fix: issue #240 reorg not catched because of ARC dealloc

# 0.9.0

- implement ZIP-313 reducing fees to 1000 zatoshi

# 0.8.0

- [IMPORTANT] Issue #237 Untie SDKSynchronizer from UIApplication Events
- Fix #236 fix CI problem
- Issue #176 operation gets cancelled when backgrounding
- Issue #136 on https://github.com/zcash/zcash-ios-wallet
- Issue #123 on https://github.com/zcash/zcash-ios-wallet
- PR from @ant013: Forcibly change the state to stopped when the handle cancels any task in OperationQueue

# 0.7.2

- Checkpoint for Mainnet

# 0.7.1

- Issue 208 - Improve API method to request transaction history
- Added Found transaction notification to SDK Synchronizer
- Add darksidewalletd tests for foundTransactions notifications
- [CRITICAL] Fix sqlite crate canopy issue. Add a new checkpoint to aid testing
- FIX - UNIQUE constraint violation when an operation failed

# 0.7.0

## Improvements

- #22 Sapling parameter downloader
- #201 Throw exception when seed can't be provided
- #204 Add DerivationTool to Initializer
- #205 Add IVK initialization capabilities to Initializer
- #206 [community request @esengulov] add extension function to identify inbound v. outbound txs on a client side
- #207 [community request @esengulov] Add extension function for timestamps on transactions

# 0.6.4

- FIX: transaction details listing duplicate transactions on certain transactions with several outputs and inputs
- added checkpoints

# 0.6.3

- updated to gRPC-Swift 1.0.0-alpha19
- readme warning on issues with rustc 1.46.0
- improvement on build system to help switch network environment faster

# 0.6.2

- added new checkpoints for testnet and mainnet

# 0.6.1

- Updated librustzcash to support Canopy on testnet

# 0.6.0

- Error handling improvements (breaks API)

# 0.5.3

- Fixes #158 #132 #134 #135 #133

# 0.5.2

- update Librustzcash to point to master repo!
- enhance pending transaction handling (#160)
- Added memo demo!
- automation!

# 0.5.1

- remove MnemonicKit dependency from tests

# 0.5.0

- Enable heartwood. (#138)
- Update LICENSE
- Switch to MnemonicSwift (#142)
- Issue 136 Null bytes in strings effectively truncate the string from … (#140)
- Fixes issue 136 - expiry height -1 on pending transactions (#139)
- Advanced Re Org tests + Balance tests (#137)
- CI doc mods (#116)
- Update issue templates
- Replace the threat model with the one on readthedocs (#131)
- Add bug report and feature request issue templates
- remove commit lock from podfile
- Canonical empty memo test (#112)
- Memo tests (#111)
- Decrypt transactions. Full wallet restore (#110)

# 0.4.0

- Updated GRPC dependency to Swift GRPC NIO. this change does not break any public interfaces

# 0.3.2

-  reorg testing (#104)
-  Docs - Fix typos and cleanup (#103)
-  ZcashRustBackend.decryptAndStoreTransaction()
-  Enhance logging on compact block processor
-  parameterize helper method with constant

# 0.3.1

- Reverted  -> update librustzcash to commit 52d8b436300724bc4b83aa4a0c659ab34c3dbce7

# 0.3.0

- testing: fix test crash
- fix: updated sample code where interface changed
- ENHANCEMENT: Retry support + error management
- FIX: processor crashes when lightwalletd has not caught up with latest height
- Better error handing when scanning fails
- [IMPORTANT] update librustzcash to commit 52d8b436300724bc4b83aa4a0c659ab34c3dbce7
- improved docs Move read.me up a directory
- NEW: Integrate logging capabilities
- FIX: account initialization error
- ENHANCEMENT: Mainnet checkpoints (#88)

# 0.2.1

**IMPORTANT: this version contains a critical fix, upgrade to it as soon as possible**

- [CRITICAL] Fixed a hardcoded COIN_TYPE on lib.rs
- added mainnet checkpoints

# 0.2.0

**Warning: These changes might break interfaces in your project.**

- upgraded to note-spending-v7
- fixed memory leak and blockrange error
- fixed memory cycles and leaks
- Fixed capture blocks retaining references
- fixed bug where compact block processor wouldn't reschedule
- add address validation functionality to Initializer
- Fixes to initializer, added v7 methods, documented API. Fixed compact block processor not initializing correctly upon new wallets.
- use "zip32 compliant" seed on demo app

# 0.1.3

Changes to createToAddress function to fix issues with paths that have spaces

Synchronizer:

change from computed variables to functions to allow throwing errors to clients

https://github.com/zcash/ZcashLightClientKit/pull/84
