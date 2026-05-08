//
//  VotingRustBackend.swift
//  ZcashLightClientKit
//

import Foundation
import libzcashlc

// MARK: - Error

/// Error type for voting Rust backend operations.
public enum VotingRustBackendError: LocalizedError, Equatable {
    /// The voting database is already open.
    case databaseAlreadyOpen
    /// The voting database is not open.
    case databaseNotOpen
    /// A Rust error occurred.
    case rustError(String)
    /// Invalid data was received.
    case invalidData(String)

    public var errorDescription: String? {
        switch self {
        case .databaseAlreadyOpen:
            return "Voting database is already open."
        case .databaseNotOpen:
            return "Voting database is not open."
        case .rustError(let message):
            return "Voting backend error: \(message)"
        case .invalidData(let message):
            return "Invalid data: \(message)"
        }
    }
}

// MARK: - Progress callback

/// Closure type for proof progress reporting.
public typealias VotingProgressHandler = @Sendable (Double) -> Void

// MARK: - VotingRustBackend

/// Wraps the voting `libzcashlc` C FFI surface.
///
/// Manages an opaque `VotingDatabaseHandle` pointer for the database-bound
/// methods. Stateless / static FFI (e.g. `computeShareNullifier`) is exposed
/// as type methods so callers do not need to open a database.
///
/// Thread safety: handle access is serialized by an `NSLock`. Database-bound
/// FFI calls hold the lock for their full duration so `close()` cannot free the
/// handle while Rust is using it.
public final class VotingRustBackend: @unchecked Sendable {
    private let lock = NSLock()
    private var handle: OpaquePointer?

    public init() {}

    deinit {
        if let handle {
            zcashlc_voting_db_free(handle)
        }
    }

    // MARK: - Database lifecycle

    /// Open the voting database at `path`.
    ///
    /// Throws `VotingRustBackendError.databaseAlreadyOpen` if the backend
    /// already holds an open handle.
    public func open(path: String) throws {
        lock.lock()
        defer { lock.unlock() }

        guard handle == nil else {
            throw VotingRustBackendError.databaseAlreadyOpen
        }

        let pathBytes = [UInt8](path.utf8)
        guard let ptr = pathBytes.withUnsafeBufferPointer({ buf in
            zcashlc_voting_db_open(buf.baseAddress, UInt(buf.count))
        }) else {
            throw VotingRustBackendError.rustError(
                Self.staticLastErrorMessage(fallback: "`voting_db_open` failed")
            )
        }
        handle = ptr
    }

    /// Close the voting database, freeing the underlying handle.
    ///
    /// Idempotent: calling `close()` on an already-closed backend is a no-op.
    public func close() {
        lock.lock()
        defer { lock.unlock() }

        if let dbh = handle {
            zcashlc_voting_db_free(dbh)
            handle = nil
        }
    }
}

// MARK: - Wallet identity

extension VotingRustBackend {
    /// Set the wallet identifier for all subsequent voting operations.
    ///
    /// Must be called after `open(path:)` and before any round operations.
    public func setWalletId(_ walletId: String) throws {
        let walletIdBytes = [UInt8](walletId.utf8)

        try withHandle { dbh in
            let result = walletIdBytes.withUnsafeBufferPointer { buf in
                zcashlc_voting_set_wallet_id(dbh, buf.baseAddress, UInt(buf.count))
            }

            guard result == 0 else {
                throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`set_wallet_id` failed"))
            }
        }
    }
}

// MARK: - Delegation (PIR precompute)

extension VotingRustBackend {
    /// Resolve the round's PIR endpoint, fetch the IMT non-membership proofs
    /// needed for the delegation ZKP, and cache them in the voting database.
    ///
    /// This performs the network PIR lookup only, proof construction happens
    //  elsewhere.
    ///
    /// `pirEndpoints` are probed in parallel via `pirResolver`. The first
    /// endpoint whose served snapshot height equals `expectedSnapshotHeight`
    /// exactly is used. See `PirSnapshotResolver` for the failure semantics.
    // swiftlint:disable:next function_parameter_count
    public func precomputeDelegationPir(
        roundId: String,
        bundleIndex: UInt32,
        notes: [VotingNoteInfo],
        pirEndpoints: [String],
        expectedSnapshotHeight: UInt64,
        networkId: UInt32,
        pirResolver: PirSnapshotResolver = PirSnapshotResolver()
    ) async throws -> VotingDelegationPirPrecomputeResult {
        try requireOpenDatabase()

        // PirSnapshotResolver expects `BlockHeight` (Int); voting snapshot
        // heights are `UInt64` everywhere else in the voting types, so convert
        // at the boundary. Snapshot heights well within Int.max in practice.
        let pirServerUrl = try await pirResolver.resolve(
            endpoints: pirEndpoints,
            expectedSnapshotHeight: BlockHeight(expectedSnapshotHeight)
        )

        let roundIdBytes = [UInt8](roundId.utf8)
        let notesJson = try JSONEncoder().encode(notes)
        let notesBytes = [UInt8](notesJson)
        let urlBytes = [UInt8](pirServerUrl.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice> = try withHandle { dbh in
            let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
                notesBytes.withUnsafeBufferPointer { notesBuf in
                    urlBytes.withUnsafeBufferPointer { urlBuf in
                        zcashlc_voting_precompute_delegation_pir(
                            dbh,
                            ridBuf.baseAddress,
                            UInt(ridBuf.count),
                            bundleIndex,
                            notesBuf.baseAddress,
                            UInt(notesBuf.count),
                            urlBuf.baseAddress,
                            UInt(urlBuf.count),
                            networkId
                        )
                    }
                }
            }

            guard let ptr else {
                throw VotingRustBackendError.rustError(
                    lastErrorMessage(fallback: "`precompute_delegation_pir` failed")
                )
            }
            return ptr
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }
}

// MARK: - Vote-tree sync

extension VotingRustBackend {
    /// Sync the vote commitment tree from a chain node.
    ///
    /// Returns the latest synced block height.
    public func syncVoteTree(roundId: String, nodeUrl: String) throws -> UInt32 {
        let roundIdBytes = [UInt8](roundId.utf8)
        let urlBytes = [UInt8](nodeUrl.utf8)

        return try withHandle { dbh in
            let result = roundIdBytes.withUnsafeBufferPointer { ridBuf in
                urlBytes.withUnsafeBufferPointer { urlBuf in
                    zcashlc_voting_sync_vote_tree(
                        dbh,
                        ridBuf.baseAddress,
                        UInt(ridBuf.count),
                        urlBuf.baseAddress,
                        UInt(urlBuf.count)
                    )
                }
            }

            guard result >= 0 else {
                throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`sync_vote_tree` failed"))
            }
            return UInt32(result)
        }
    }

    /// Generate a Vote Authority Note (VAN) Merkle witness for the given
    /// bundle at `anchorHeight`.
    public func generateVanWitness(
        roundId: String,
        bundleIndex: UInt32,
        anchorHeight: UInt32
    ) throws -> VotingVanWitness {
        let roundIdBytes = [UInt8](roundId.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice> = try withHandle { dbh in
            let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { buf in
                zcashlc_voting_generate_van_witness(
                    dbh,
                    buf.baseAddress,
                    UInt(buf.count),
                    bundleIndex,
                    anchorHeight
                )
            }

            guard let ptr else {
                throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`generate_van_witness` failed"))
            }
            return ptr
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Reset the in-memory tree client for a round, forcing the next
    /// `syncVoteTree` call to start from a fresh client.
    ///
    /// Pass an empty `roundId` to reset all rounds.
    public func resetTreeClient(roundId: String = "") throws {
        let roundIdBytes = [UInt8](roundId.utf8)

        try withHandle { dbh in
            let result = roundIdBytes.withUnsafeBufferPointer { buf in
                zcashlc_voting_reset_tree_client(dbh, buf.baseAddress, UInt(buf.count))
            }

            guard result == 0 else {
                throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`reset_tree_client` failed"))
            }
        }
    }
}

// MARK: - Round management

extension VotingRustBackend {
    /// Initialize a voting round.
    // swiftlint:disable:next function_parameter_count
    public func initRound(
        roundId: String,
        snapshotHeight: UInt64,
        eaPk: [UInt8],
        ncRoot: [UInt8],
        nullifierImtRoot: [UInt8],
        sessionJson: String?
    ) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let sessionBytes: [UInt8]? = sessionJson.map { [UInt8]($0.utf8) }

        let result = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            eaPk.withUnsafeBufferPointer { eaBuf in
                ncRoot.withUnsafeBufferPointer { ncBuf in
                    nullifierImtRoot.withUnsafeBufferPointer { nfBuf in
                        if let sessionBytes {
                            return sessionBytes.withUnsafeBufferPointer { sjBuf in
                                zcashlc_voting_init_round(
                                    dbh,
                                    ridBuf.baseAddress,
                                    UInt(ridBuf.count),
                                    snapshotHeight,
                                    eaBuf.baseAddress,
                                    UInt(eaBuf.count),
                                    ncBuf.baseAddress,
                                    UInt(ncBuf.count),
                                    nfBuf.baseAddress,
                                    UInt(nfBuf.count),
                                    sjBuf.baseAddress,
                                    UInt(sjBuf.count)
                                )
                            }
                        } else {
                            return zcashlc_voting_init_round(
                                dbh,
                                ridBuf.baseAddress,
                                UInt(ridBuf.count),
                                snapshotHeight,
                                eaBuf.baseAddress,
                                UInt(eaBuf.count),
                                ncBuf.baseAddress,
                                UInt(ncBuf.count),
                                nfBuf.baseAddress,
                                UInt(nfBuf.count),
                                nil,
                                0
                            )
                        }
                    }
                }
            }
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`init_round` failed"))
        }
    }

    /// Get the state of a voting round.
    public func getRoundState(roundId: String) throws -> VotingRoundState {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        guard let ptr = roundIdBytes.withUnsafeBufferPointer({ buf in
            zcashlc_voting_get_round_state(dbh, buf.baseAddress, UInt(buf.count))
        }) else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_round_state` failed"))
        }

        defer { zcashlc_voting_free_round_state(ptr) }

        let state = ptr.pointee
        let phase = VotingRoundPhase(rawValue: state.phase) ?? .initialized

        let roundIdStr: String
        if let rid = state.round_id {
            roundIdStr = String(cString: rid)
        } else {
            roundIdStr = roundId
        }

        let hotkeyAddr: String?
        if let addr = state.hotkey_address {
            hotkeyAddr = String(cString: addr)
        } else {
            hotkeyAddr = nil
        }

        let weight: UInt64? = state.delegated_weight >= 0 ? UInt64(state.delegated_weight) : nil

        return VotingRoundState(
            roundId: roundIdStr,
            phase: phase,
            snapshotHeight: state.snapshot_height,
            hotkeyAddress: hotkeyAddr,
            delegatedWeight: weight,
            proofGenerated: state.proof_generated
        )
    }

    /// List all voting rounds.
    public func listRounds() throws -> [VotingRoundSummary] {
        let dbh = try requireHandle()

        guard let ptr = zcashlc_voting_list_rounds(dbh) else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`list_rounds` failed"))
        }

        defer { zcashlc_voting_free_round_summaries(ptr) }

        let summaries = ptr.pointee
        var roundSummaries: [VotingRoundSummary] = []
        for i in 0..<Int(summaries.len) {
            let entry = summaries.ptr.advanced(by: i).pointee
            let rid = entry.round_id != nil ? String(cString: entry.round_id) : ""
            let phase = VotingRoundPhase(rawValue: entry.phase) ?? .initialized
            roundSummaries.append(VotingRoundSummary(
                roundId: rid,
                phase: phase,
                snapshotHeight: entry.snapshot_height,
                createdAt: entry.created_at
            ))
        }
        return roundSummaries
    }

    /// Get vote records for a round.
    public func getVotes(roundId: String) throws -> [VotingVoteRecord] {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        guard let ptr = roundIdBytes.withUnsafeBufferPointer({ buf in
            zcashlc_voting_get_votes(dbh, buf.baseAddress, UInt(buf.count))
        }) else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_votes` failed"))
        }

        defer { zcashlc_voting_free_vote_records(ptr) }

        let records = ptr.pointee
        var result: [VotingVoteRecord] = []
        for i in 0..<Int(records.len) {
            let record = records.ptr.advanced(by: i).pointee
            result.append(VotingVoteRecord(
                proposalId: record.proposal_id,
                bundleIndex: record.bundle_index,
                choice: record.choice,
                submitted: record.submitted
            ))
        }
        return result
    }

    /// Clear all data for a voting round.
    public func clearRound(roundId: String) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let result = roundIdBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_clear_round(dbh, buf.baseAddress, UInt(buf.count))
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`clear_round` failed"))
        }
    }

    /// Delete skipped bundles (bundle_index >= keepCount).
    /// Returns the number of deleted rows.
    public func deleteSkippedBundles(roundId: String, keepCount: UInt32) throws -> Int64 {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let result = roundIdBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_delete_skipped_bundles(dbh, buf.baseAddress, UInt(buf.count), keepCount)
        }

        guard result >= 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`delete_skipped_bundles` failed"))
        }
        return result
    }
}

// MARK: - Wallet notes

extension VotingRustBackend {
    /// Get wallet notes eligible for voting at the snapshot height.
    ///
    /// `accountUUID` (16 bytes) directly identifies the wallet account.
    /// Falls back to positional `accountIndex` when UUID is empty.
    public func getWalletNotes(
        walletDbPath: String,
        snapshotHeight: UInt64,
        networkId: UInt32,
        accountUUID: [UInt8]
    ) throws -> [VotingNoteInfo] {
        let dbh = try requireHandle()
        let pathBytes = [UInt8](walletDbPath.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = pathBytes.withUnsafeBufferPointer { pathBuf in
            if !accountUUID.isEmpty {
                return accountUUID.withUnsafeBufferPointer { uuidBuf in
                    zcashlc_voting_get_wallet_notes(
                        dbh,
                        pathBuf.baseAddress,
                        UInt(pathBuf.count),
                        snapshotHeight,
                        networkId,
                        uuidBuf.baseAddress,
                        UInt(uuidBuf.count)
                    )
                }
            } else {
                return zcashlc_voting_get_wallet_notes(
                    dbh,
                    pathBuf.baseAddress,
                    UInt(pathBuf.count),
                    snapshotHeight,
                    networkId,
                    nil,
                    0
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_wallet_notes` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }
}

// MARK: - Hotkey & delegation setup

extension VotingRustBackend {
    /// Generate a voting hotkey for a round.
    public func generateHotkey(roundId: String, seed: [UInt8]) throws -> VotingHotkey {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let ptr = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            seed.withUnsafeBufferPointer { seedBuf in
                zcashlc_voting_generate_hotkey(
                    dbh,
                    ridBuf.baseAddress,
                    UInt(ridBuf.count),
                    seedBuf.baseAddress,
                    UInt(seedBuf.count)
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`generate_hotkey` failed"))
        }
        defer { zcashlc_voting_free_hotkey(ptr) }
        return hotkeyFromFfi(ptr.pointee)
    }

    /// Set up note bundles for a voting round.
    public func setupBundles(roundId: String, notes: [VotingNoteInfo]) throws -> VotingBundleSetupResult {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let notesJson = try JSONEncoder().encode(notes)

        let ptr = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            notesJson.withUnsafeBytes { notesBuf in
                zcashlc_voting_setup_bundles(
                    dbh,
                    ridBuf.baseAddress,
                    UInt(ridBuf.count),
                    notesBuf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt(notesBuf.count)
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`setup_bundles` failed"))
        }
        defer { zcashlc_voting_free_bundle_setup_result(ptr) }

        return VotingBundleSetupResult(
            bundleCount: ptr.pointee.bundle_count,
            eligibleWeight: ptr.pointee.eligible_weight
        )
    }

    /// Get the number of bundles for a round.
    public func getBundleCount(roundId: String) throws -> UInt32 {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let result = roundIdBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_get_bundle_count(dbh, buf.baseAddress, UInt(buf.count))
        }

        guard result >= 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_bundle_count` failed"))
        }
        return UInt32(result)
    }

    /// Build a voting PCZT for a bundle.
    // swiftlint:disable:next function_parameter_count
    public func buildVotingPczt(
        roundId: String,
        bundleIndex: UInt32,
        notes: [VotingNoteInfo],
        fvkBytes: [UInt8],
        hotkeyRawAddress: [UInt8],
        consensusBranchId: UInt32,
        coinType: UInt32,
        seedFingerprint: [UInt8],
        accountIndex: UInt32,
        roundName: String,
        addressIndex: UInt32
    ) throws -> VotingPczt {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let notesJson = try JSONEncoder().encode(notes)
        let roundNameBytes = [UInt8](roundName.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            notesJson.withUnsafeBytes { notesBuf in
                fvkBytes.withUnsafeBufferPointer { fvkBuf in
                    hotkeyRawAddress.withUnsafeBufferPointer { hkBuf in
                        seedFingerprint.withUnsafeBufferPointer { sfBuf in
                            roundNameBytes.withUnsafeBufferPointer { rnBuf in
                                zcashlc_voting_build_pczt(
                                    dbh,
                                    ridBuf.baseAddress,
                                    UInt(ridBuf.count),
                                    bundleIndex,
                                    notesBuf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                                    UInt(notesBuf.count),
                                    fvkBuf.baseAddress,
                                    UInt(fvkBuf.count),
                                    hkBuf.baseAddress,
                                    UInt(hkBuf.count),
                                    consensusBranchId,
                                    coinType,
                                    sfBuf.baseAddress,
                                    UInt(sfBuf.count),
                                    accountIndex,
                                    rnBuf.baseAddress,
                                    UInt(rnBuf.count),
                                    addressIndex
                                )
                            }
                        }
                    }
                }
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`build_voting_pczt` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Store a tree state for witness generation.
    public func storeTreeState(roundId: String, treeStateBytes: [UInt8]) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let result = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            treeStateBytes.withUnsafeBufferPointer { tsBuf in
                zcashlc_voting_store_tree_state(
                    dbh,
                    ridBuf.baseAddress,
                    UInt(ridBuf.count),
                    tsBuf.baseAddress,
                    UInt(tsBuf.count)
                )
            }
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`store_tree_state` failed"))
        }
    }

    /// Generate Merkle inclusion witnesses for notes in a bundle.
    public func generateNoteWitnesses(
        roundId: String,
        bundleIndex: UInt32,
        walletDbPath: String,
        notes: [VotingNoteInfo]
    ) throws -> [VotingWitnessData] {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let pathBytes = [UInt8](walletDbPath.utf8)
        let notesJson = try JSONEncoder().encode(notes)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            pathBytes.withUnsafeBufferPointer { pathBuf in
                notesJson.withUnsafeBytes { notesBuf in
                    zcashlc_voting_generate_note_witnesses(
                        dbh,
                        ridBuf.baseAddress,
                        UInt(ridBuf.count),
                        bundleIndex,
                        pathBuf.baseAddress,
                        UInt(pathBuf.count),
                        notesBuf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        UInt(notesBuf.count)
                    )
                }
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`generate_note_witnesses` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }
}

// MARK: - Delegation proof

extension VotingRustBackend {
    /// Build and prove the delegation ZKP. Long-running; reports progress via callback.
    ///
    /// Pass every PIR endpoint configured for the round and the round's expected
    /// snapshot height; the SDK probes each endpoint's `GET /root` in parallel
    /// (see `PirSnapshotResolver`) and uses the first endpoint (in config order)
    /// whose served snapshot height equals `expectedSnapshotHeight` exactly.
    /// Endpoints that are behind, ahead, missing snapshot metadata, or unreachable
    /// are excluded. If no endpoint matches, the call throws
    /// `PirSnapshotResolverError.noMatchingEndpoint` (or `.noEndpointsConfigured`)
    /// — the SDK refuses to fall back to a mismatched endpoint, since proofs
    /// built against the wrong snapshot are rejected on chain.
    ///
    /// Pass a custom `pirResolver` (typically only in tests) to inject a
    /// stubbed probe; production callers should let it default.
    // swiftlint:disable:next function_parameter_count
    public func buildAndProveDelegation(
        roundId: String,
        bundleIndex: UInt32,
        notes: [VotingNoteInfo],
        hotkeyRawAddress: [UInt8],
        pirEndpoints: [String],
        expectedSnapshotHeight: UInt64,
        networkId: UInt32,
        progress: VotingProgressHandler?,
        pirResolver: PirSnapshotResolver = PirSnapshotResolver()
    ) async throws -> VotingDelegationProofResult {
        let pirServerUrl = try await pirResolver.resolve(
            endpoints: pirEndpoints,
            expectedSnapshotHeight: BlockHeight(expectedSnapshotHeight)
        )

        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let notesJson = try JSONEncoder().encode(notes)
        let notesBytes = [UInt8](notesJson)
        let urlBytes = [UInt8](pirServerUrl.utf8)

        var context = ProgressContext(handler: progress)

        // SAFETY: `ctxPtr` points at a stack-local ProgressContext. The Rust side
        // must invoke the callback only synchronously within this FFI call — if it
        // ever retains the pointer past the call boundary, this becomes a
        // use-after-free. The Rust impl in voting.rs holds this invariant today.
        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            notesBytes.withUnsafeBufferPointer { notesBuf in
                hotkeyRawAddress.withUnsafeBufferPointer { hkBuf in
                    urlBytes.withUnsafeBufferPointer { urlBuf in
                        withUnsafeMutablePointer(to: &context) { ctxPtr in
                            let callback: (@convention(c) (Double, UnsafeMutableRawPointer?) -> Void)? =
                                progress != nil ? votingProgressTrampoline : nil
                            return zcashlc_voting_build_and_prove_delegation(
                                dbh,
                                ridBuf.baseAddress,
                                UInt(ridBuf.count),
                                bundleIndex,
                                notesBuf.baseAddress,
                                UInt(notesBuf.count),
                                hkBuf.baseAddress,
                                UInt(hkBuf.count),
                                urlBuf.baseAddress,
                                UInt(urlBuf.count),
                                networkId,
                                callback,
                                UnsafeMutableRawPointer(ctxPtr)
                            )
                        }
                    }
                }
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`build_and_prove_delegation` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Get delegation submission using a seed-derived signing key.
    public func getDelegationSubmission(
        roundId: String,
        bundleIndex: UInt32,
        senderSeed: [UInt8],
        networkId: UInt32,
        accountIndex: UInt32
    ) throws -> VotingDelegationSubmission {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            senderSeed.withUnsafeBufferPointer { seedBuf in
                zcashlc_voting_get_delegation_submission(
                    dbh,
                    ridBuf.baseAddress,
                    UInt(ridBuf.count),
                    bundleIndex,
                    seedBuf.baseAddress,
                    UInt(seedBuf.count),
                    networkId,
                    accountIndex
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_delegation_submission` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Get delegation submission using a Keystone-provided signature.
    public func getDelegationSubmissionWithKeystoneSig(
        roundId: String,
        bundleIndex: UInt32,
        sig: [UInt8],
        sighash: [UInt8]
    ) throws -> VotingDelegationSubmission {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            sig.withUnsafeBufferPointer { sigBuf in
                sighash.withUnsafeBufferPointer { shBuf in
                    zcashlc_voting_get_delegation_submission_with_keystone_sig(
                        dbh,
                        ridBuf.baseAddress,
                        UInt(ridBuf.count),
                        bundleIndex,
                        sigBuf.baseAddress,
                        UInt(sigBuf.count),
                        shBuf.baseAddress,
                        UInt(shBuf.count)
                    )
                }
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(
                lastErrorMessage(fallback: "`get_delegation_submission_with_keystone_sig` failed")
            )
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Store the VAN leaf position after delegation TX is confirmed.
    public func storeVanPosition(roundId: String, bundleIndex: UInt32, position: UInt32) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let result = roundIdBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_store_van_position(dbh, buf.baseAddress, UInt(buf.count), bundleIndex, position)
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`store_van_position` failed"))
        }
    }
}

// MARK: - Vote & commitment

extension VotingRustBackend {
    /// Encrypt voting shares for a round.
    public func encryptShares(roundId: String, shares: [UInt64]) throws -> [VotingWireEncryptedShare] {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let sharesJson = try JSONEncoder().encode(shares)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            sharesJson.withUnsafeBytes { sjBuf in
                zcashlc_voting_encrypt_shares(
                    dbh,
                    ridBuf.baseAddress,
                    UInt(ridBuf.count),
                    sjBuf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt(sjBuf.count)
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`encrypt_shares` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Build a vote commitment (ZKP #2) for a proposal.
    // swiftlint:disable:next function_parameter_count
    public func buildVoteCommitment(
        roundId: String,
        bundleIndex: UInt32,
        hotkeySeed: [UInt8],
        networkId: UInt32,
        proposalId: UInt32,
        choice: UInt32,
        numOptions: UInt32,
        vanAuthPath: [[UInt8]],
        vanPosition: UInt32,
        anchorHeight: UInt32,
        singleShare: UInt8 = 0,
        progress: VotingProgressHandler?
    ) throws -> VotingVoteCommitmentBundle {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let authPathJson = try JSONEncoder().encode(vanAuthPath)

        var context = ProgressContext(handler: progress)

        // SAFETY: see the equivalent note on `buildAndProveDelegation`. `ctxPtr` is
        // stack-scoped to this call; the Rust side must not retain it past return.
        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            hotkeySeed.withUnsafeBufferPointer { seedBuf in
                authPathJson.withUnsafeBytes { apBuf in
                    withUnsafeMutablePointer(to: &context) { ctxPtr in
                        let callback: (@convention(c) (Double, UnsafeMutableRawPointer?) -> Void)? =
                            progress != nil ? votingProgressTrampoline : nil
                        return zcashlc_voting_build_vote_commitment(
                            dbh,
                            ridBuf.baseAddress,
                            UInt(ridBuf.count),
                            bundleIndex,
                            seedBuf.baseAddress,
                            UInt(seedBuf.count),
                            networkId,
                            proposalId,
                            choice,
                            numOptions,
                            apBuf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                            UInt(apBuf.count),
                            vanPosition,
                            anchorHeight,
                            callback,
                            UnsafeMutableRawPointer(ctxPtr),
                            singleShare
                        )
                    }
                }
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`build_vote_commitment` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Build share payloads for delegated share submission.
    public func buildSharePayloads(
        encShares: [VotingWireEncryptedShare],
        commitment: VotingVoteCommitmentBundle,
        voteDecision: UInt32,
        numOptions: UInt32,
        vcTreePosition: UInt64,
        singleShare: UInt8 = 0
    ) throws -> [VotingSharePayload] {
        let dbh = try requireHandle()
        let sharesJson = try JSONEncoder().encode(encShares)
        let commitmentJson = try JSONEncoder().encode(commitment)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = sharesJson.withUnsafeBytes { sjBuf in
            commitmentJson.withUnsafeBytes { cjBuf in
                zcashlc_voting_build_share_payloads(
                    dbh,
                    sjBuf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt(sjBuf.count),
                    cjBuf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    UInt(cjBuf.count),
                    voteDecision,
                    numOptions,
                    vcTreePosition,
                    singleShare
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`build_share_payloads` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Mark a vote as submitted.
    public func markVoteSubmitted(roundId: String, bundleIndex: UInt32, proposalId: UInt32) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let result = roundIdBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_mark_vote_submitted(dbh, buf.baseAddress, UInt(buf.count), bundleIndex, proposalId)
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`mark_vote_submitted` failed"))
        }
    }
}

// MARK: - Recovery state (TX hashes, bundles, keystone sigs)

extension VotingRustBackend {
    public func storeDelegationTxHash(roundId: String, bundleIndex: UInt32, txHash: String) throws {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let txBytes = [UInt8](txHash.utf8)
        let result = ridBytes.withUnsafeBufferPointer { ridBuf in
            txBytes.withUnsafeBufferPointer { txBuf in
                zcashlc_voting_store_delegation_tx_hash(dbh, ridBuf.baseAddress, UInt(ridBuf.count), bundleIndex, txBuf.baseAddress, UInt(txBuf.count))
            }
        }
        guard result == 0 else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`store_delegation_tx_hash` failed")) }
    }

    public func getDelegationTxHash(roundId: String, bundleIndex: UInt32) throws -> VotingTxHashLookup {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = ridBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_get_delegation_tx_hash(dbh, ridBuf.baseAddress, UInt(ridBuf.count), bundleIndex)
        }
        guard let ptr else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_delegation_tx_hash` failed")) }
        defer { zcashlc_free_boxed_slice(ptr) }
        let optional: String? = try decodeJSON(from: ptr)
        return optional.map { .present($0) } ?? .notFound
    }

    public func storeVoteTxHash(roundId: String, bundleIndex: UInt32, proposalId: UInt32, txHash: String) throws {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let txBytes = [UInt8](txHash.utf8)
        let result = ridBytes.withUnsafeBufferPointer { ridBuf in
            txBytes.withUnsafeBufferPointer { txBuf in
                zcashlc_voting_store_vote_tx_hash(dbh, ridBuf.baseAddress, UInt(ridBuf.count), bundleIndex, proposalId, txBuf.baseAddress, UInt(txBuf.count))
            }
        }
        guard result == 0 else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`store_vote_tx_hash` failed")) }
    }

    public func getVoteTxHash(roundId: String, bundleIndex: UInt32, proposalId: UInt32) throws -> VotingTxHashLookup {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = ridBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_get_vote_tx_hash(dbh, ridBuf.baseAddress, UInt(ridBuf.count), bundleIndex, proposalId)
        }
        guard let ptr else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_vote_tx_hash` failed")) }
        defer { zcashlc_free_boxed_slice(ptr) }
        let optional: String? = try decodeJSON(from: ptr)
        return optional.map { .present($0) } ?? .notFound
    }

    public func storeCommitmentBundle(roundId: String, bundleIndex: UInt32, proposalId: UInt32, bundleJson: String, vcTreePosition: UInt64) throws {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let jsonBytes = [UInt8](bundleJson.utf8)
        let result = ridBytes.withUnsafeBufferPointer { ridBuf in
            jsonBytes.withUnsafeBufferPointer { jsonBuf in
                zcashlc_voting_store_commitment_bundle(dbh, ridBuf.baseAddress, UInt(ridBuf.count), bundleIndex, proposalId, jsonBuf.baseAddress, UInt(jsonBuf.count), vcTreePosition)
            }
        }
        guard result == 0 else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`store_commitment_bundle` failed")) }
    }

    public struct CommitmentBundleResult {
        public let json: String
        public let vcTreePosition: UInt64
    }

    public func getCommitmentBundle(roundId: String, bundleIndex: UInt32, proposalId: UInt32) throws -> CommitmentBundleResult? {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = ridBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_get_commitment_bundle(dbh, ridBuf.baseAddress, UInt(ridBuf.count), bundleIndex, proposalId)
        }
        guard let ptr else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_commitment_bundle` failed")) }
        defer { zcashlc_free_boxed_slice(ptr) }
        // Rust serializes Option<(String, u64)> as a JSON array [string, number] or null.
        let decoded: CommitmentBundleWire? = try decodeJSON(from: ptr)
        return decoded.map { CommitmentBundleResult(json: $0.json, vcTreePosition: $0.vcTreePosition) }
    }

    private struct CommitmentBundleWire: Decodable {
        let json: String
        let vcTreePosition: UInt64

        init(from decoder: Decoder) throws {
            var container = try decoder.unkeyedContainer()
            json = try container.decode(String.self)
            vcTreePosition = try container.decode(UInt64.self)
        }
    }

    public func storeKeystoneSignature(roundId: String, bundleIndex: UInt32, sig: Data, sighash: Data, rk: Data) throws { // swiftlint:disable:this function_parameter_count
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let sigBytes = [UInt8](sig)
        let sighashBytes = [UInt8](sighash)
        let rkBytes = [UInt8](rk)
        let result = ridBytes.withUnsafeBufferPointer { ridBuf in
            sigBytes.withUnsafeBufferPointer { sigBuf in
                sighashBytes.withUnsafeBufferPointer { shBuf in
                    rkBytes.withUnsafeBufferPointer { rkBuf in
                        zcashlc_voting_store_keystone_signature(dbh, ridBuf.baseAddress, UInt(ridBuf.count), bundleIndex, sigBuf.baseAddress, UInt(sigBuf.count), shBuf.baseAddress, UInt(shBuf.count), rkBuf.baseAddress, UInt(rkBuf.count))
                    }
                }
            }
        }
        guard result == 0 else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`store_keystone_signature` failed")) }
    }

    public struct KeystoneSignatureOut: Codable {
        public let bundleIndex: UInt32
        public let sig: [UInt8]
        public let sighash: [UInt8]
        public let rk: [UInt8]

        enum CodingKeys: String, CodingKey {
            case bundleIndex = "bundle_index"
            case sig, sighash, rk
        }
    }

    public func getKeystoneSignatures(roundId: String) throws -> [KeystoneSignatureOut] {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = ridBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_get_keystone_signatures(dbh, ridBuf.baseAddress, UInt(ridBuf.count))
        }
        guard let ptr else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_keystone_signatures` failed")) }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    public func clearRecoveryState(roundId: String) throws {
        let dbh = try requireHandle()
        let ridBytes = [UInt8](roundId.utf8)
        let result = ridBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_clear_recovery_state(dbh, ridBuf.baseAddress, UInt(ridBuf.count))
        }
        guard result == 0 else { throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`clear_recovery_state` failed")) }
    }
}

// MARK: - Share delegation tracking

extension VotingRustBackend {
    /// Compute the nullifier for a vote share.
    ///
    /// This is a static/pure function — no database handle needed.
    /// `voteCommitment` and `primaryBlind` must each be exactly 32 bytes.
    /// Returns a hex-encoded nullifier string.
    public static func computeShareNullifier(
        voteCommitment: [UInt8],
        shareIndex: UInt32,
        primaryBlind: [UInt8]
    ) throws -> String {
        guard voteCommitment.count == 32, primaryBlind.count == 32 else {
            throw VotingRustBackendError.invalidData(
                "`voteCommitment` and `primaryBlind` must be exactly 32 bytes"
            )
        }

        let ptr = voteCommitment.withUnsafeBufferPointer { vcBuf in
            primaryBlind.withUnsafeBufferPointer { blindBuf in
                zcashlc_voting_compute_share_nullifier(
                    vcBuf.baseAddress,
                    blindBuf.baseAddress,
                    shareIndex
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(
                staticLastErrorMessage(fallback: "`compute_share_nullifier` failed")
            )
        }
        defer { zcashlc_string_free(ptr) }
        return String(cString: ptr)
    }

    /// Record a share delegation after sending to helper servers.
    public func recordShareDelegation(
        roundId: String,
        bundleIndex: UInt32,
        proposalId: UInt32,
        shareIndex: UInt32,
        sentToURLs: [String],
        nullifier: [UInt8],
        submitAt: UInt64
    ) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let urlsJson = try JSONEncoder().encode(sentToURLs)
        let urlsBytes = [UInt8](urlsJson)

        let result = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            urlsBytes.withUnsafeBufferPointer { urlsBuf in
                nullifier.withUnsafeBufferPointer { nfBuf in
                    zcashlc_voting_record_share_delegation(
                        dbh,
                        ridBuf.baseAddress,
                        UInt(ridBuf.count),
                        bundleIndex,
                        proposalId,
                        shareIndex,
                        urlsBuf.baseAddress,
                        UInt(urlsBuf.count),
                        nfBuf.baseAddress,
                        UInt(nfBuf.count),
                        submitAt
                    )
                }
            }
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`record_share_delegation` failed"))
        }
    }

    /// Get all share delegations for a round.
    public func getShareDelegations(roundId: String) throws -> [VotingShareDelegation] {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_get_share_delegations(dbh, ridBuf.baseAddress, UInt(ridBuf.count))
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_share_delegations` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Get unconfirmed share delegations for a round.
    public func getUnconfirmedDelegations(roundId: String) throws -> [VotingShareDelegation] {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_get_unconfirmed_delegations(dbh, ridBuf.baseAddress, UInt(ridBuf.count))
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`get_unconfirmed_delegations` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSON(from: ptr)
    }

    /// Mark a share delegation as confirmed on-chain.
    public func markShareConfirmed(
        roundId: String,
        bundleIndex: UInt32,
        proposalId: UInt32,
        shareIndex: UInt32
    ) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)

        let result = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            zcashlc_voting_mark_share_confirmed(
                dbh,
                ridBuf.baseAddress,
                UInt(ridBuf.count),
                bundleIndex,
                proposalId,
                shareIndex
            )
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`mark_share_confirmed` failed"))
        }
    }

    /// Append new server URLs to a share delegation's sent_to_urls.
    public func addSentServers(
        roundId: String,
        bundleIndex: UInt32,
        proposalId: UInt32,
        shareIndex: UInt32,
        newURLs: [String]
    ) throws {
        let dbh = try requireHandle()
        let roundIdBytes = [UInt8](roundId.utf8)
        let urlsJson = try JSONEncoder().encode(newURLs)
        let urlsBytes = [UInt8](urlsJson)

        let result = roundIdBytes.withUnsafeBufferPointer { ridBuf in
            urlsBytes.withUnsafeBufferPointer { urlsBuf in
                zcashlc_voting_add_sent_servers(
                    dbh,
                    ridBuf.baseAddress,
                    UInt(ridBuf.count),
                    bundleIndex,
                    proposalId,
                    shareIndex,
                    urlsBuf.baseAddress,
                    UInt(urlsBuf.count)
                )
            }
        }

        guard result == 0 else {
            throw VotingRustBackendError.rustError(lastErrorMessage(fallback: "`add_sent_servers` failed"))
        }
    }
}

// MARK: - Static / free functions (no database needed)

extension VotingRustBackend {
    /// Warm process-lifetime proving-key caches used by voting proofs.
    public static func warmProvingCaches() throws {
        let result = zcashlc_voting_warm_proving_caches()
        guard result == 0 else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`warm_proving_caches` failed"))
        }
    }

    /// Decompose a weight into power-of-two components.
    public static func decomposeWeight(_ weight: UInt64) throws -> [UInt64] {
        guard let ptr = zcashlc_voting_decompose_weight(weight) else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`decompose_weight` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSONStatic(from: ptr)
    }

    /// Generate delegation inputs from sender seed and hotkey seed.
    public static func generateDelegationInputs(
        senderSeed: [UInt8],
        hotkeySeed: [UInt8],
        networkId: UInt32,
        accountIndex: UInt32
    ) throws -> VotingDelegationInputs {
        let ptr = senderSeed.withUnsafeBufferPointer { sBuf in
            hotkeySeed.withUnsafeBufferPointer { hBuf in
                zcashlc_voting_generate_delegation_inputs(
                    sBuf.baseAddress,
                    UInt(sBuf.count),
                    hBuf.baseAddress,
                    UInt(hBuf.count),
                    networkId,
                    accountIndex
                )
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`generate_delegation_inputs` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSONStatic(from: ptr)
    }

    /// Generate delegation inputs using an explicit FVK.
    public static func generateDelegationInputsWithFvk(
        fvkBytes: [UInt8],
        hotkeySeed: [UInt8],
        networkId: UInt32,
        seedFingerprint: [UInt8]
    ) throws -> VotingDelegationInputs {
        let ptr = fvkBytes.withUnsafeBufferPointer { fvkBuf in
            hotkeySeed.withUnsafeBufferPointer { hBuf in
                seedFingerprint.withUnsafeBufferPointer { sfBuf in
                    zcashlc_voting_generate_delegation_inputs_with_fvk(
                        fvkBuf.baseAddress,
                        UInt(fvkBuf.count),
                        hBuf.baseAddress,
                        UInt(hBuf.count),
                        networkId,
                        sfBuf.baseAddress,
                        UInt(sfBuf.count)
                    )
                }
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(
                staticLastErrorMessage(fallback: "`generate_delegation_inputs_with_fvk` failed")
            )
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSONStatic(from: ptr)
    }

    /// Extract the sighash from PCZT bytes.
    public static func extractPcztSighash(pcztBytes: [UInt8]) throws -> [UInt8] {
        let ptr = pcztBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_extract_pczt_sighash(buf.baseAddress, UInt(buf.count))
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`extract_pczt_sighash` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return bytesFromBoxedSlice(ptr)
    }

    /// Extract a spend auth signature from a signed PCZT.
    public static func extractSpendAuthSig(signedPcztBytes: [UInt8], actionIndex: UInt32) throws -> [UInt8] {
        let ptr = signedPcztBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_extract_spend_auth_sig(buf.baseAddress, UInt(buf.count), actionIndex)
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`extract_spend_auth_sig` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return bytesFromBoxedSlice(ptr)
    }

    /// Extract the Orchard FVK from a UFVK string.
    public static func extractOrchardFvkFromUfvk(ufvkStr: String, networkId: UInt32) throws -> [UInt8] {
        let ufvkBytes = [UInt8](ufvkStr.utf8)

        let ptr = ufvkBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_extract_orchard_fvk_from_ufvk(buf.baseAddress, UInt(buf.count), networkId)
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(
                staticLastErrorMessage(fallback: "`extract_orchard_fvk_from_ufvk` failed")
            )
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return bytesFromBoxedSlice(ptr)
    }

    /// Extract the nc_root from a protobuf-encoded TreeState.
    public static func extractNcRoot(treeStateBytes: [UInt8]) throws -> [UInt8] {
        let ptr = treeStateBytes.withUnsafeBufferPointer { buf in
            zcashlc_voting_extract_nc_root(buf.baseAddress, UInt(buf.count))
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`extract_nc_root` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return bytesFromBoxedSlice(ptr)
    }

    /// Sign a cast-vote transaction.
    // swiftlint:disable:next function_parameter_count
    public static func signCastVote(
        hotkeySeed: [UInt8],
        networkId: UInt32,
        voteRoundIdHex: String,
        rVpkBytes: [UInt8],
        vanNullifier: [UInt8],
        voteAuthorityNoteNew: [UInt8],
        voteCommitment: [UInt8],
        proposalId: UInt32,
        anchorHeight: UInt32,
        alphaV: [UInt8]
    ) throws -> VotingCastVoteSignature {
        let roundIdBytes = [UInt8](voteRoundIdHex.utf8)

        let ptr: UnsafeMutablePointer<FfiBoxedSlice>? = hotkeySeed.withUnsafeBufferPointer { seedBuf in
            roundIdBytes.withUnsafeBufferPointer { ridBuf in
                rVpkBytes.withUnsafeBufferPointer { rvBuf in
                    vanNullifier.withUnsafeBufferPointer { vnBuf in
                        voteAuthorityNoteNew.withUnsafeBufferPointer { vanBuf in
                            voteCommitment.withUnsafeBufferPointer { vcBuf in
                                alphaV.withUnsafeBufferPointer { avBuf in
                                    zcashlc_voting_sign_cast_vote(
                                        seedBuf.baseAddress,
                                        UInt(seedBuf.count),
                                        networkId,
                                        ridBuf.baseAddress,
                                        UInt(ridBuf.count),
                                        rvBuf.baseAddress,
                                        UInt(rvBuf.count),
                                        vnBuf.baseAddress,
                                        UInt(vnBuf.count),
                                        vanBuf.baseAddress,
                                        UInt(vanBuf.count),
                                        vcBuf.baseAddress,
                                        UInt(vcBuf.count),
                                        proposalId,
                                        anchorHeight,
                                        avBuf.baseAddress,
                                        UInt(avBuf.count)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        guard let ptr else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`sign_cast_vote` failed"))
        }
        defer { zcashlc_free_boxed_slice(ptr) }
        return try decodeJSONStatic(from: ptr)
    }

    /// Verify a Merkle witness.
    public static func verifyWitness(_ witness: VotingWitnessData) throws -> Bool {
        let witnessJson = try JSONEncoder().encode(witness)

        let result = witnessJson.withUnsafeBytes { buf in
            zcashlc_voting_verify_witness(
                buf.baseAddress?.assumingMemoryBound(to: UInt8.self),
                UInt(buf.count)
            )
        }

        guard result >= 0 else {
            throw VotingRustBackendError.rustError(staticLastErrorMessage(fallback: "`verify_witness` failed"))
        }
        return result == 1
    }

}

// MARK: - Private helpers

private extension VotingRustBackend {
    /// Runs a database-bound operation while holding the handle lock. Keeping
    /// the lock through the FFI call prevents `close()` from freeing the handle
    /// before Rust is done using it.
    func withHandle<T>(_ operation: (OpaquePointer) throws -> T) throws -> T {
        lock.lock()
        defer { lock.unlock() }
        guard let dbh = handle else {
            throw VotingRustBackendError.databaseNotOpen
        }
        return try operation(dbh)
    }

    func requireOpenDatabase() throws {
        lock.lock()
        defer { lock.unlock() }

        guard handle != nil else {
            throw VotingRustBackendError.databaseNotOpen
        }
    }

    func requireHandle() throws -> OpaquePointer {
        lock.lock()
        defer { lock.unlock() }
        guard let dbh = handle else {
            throw VotingRustBackendError.databaseNotOpen
        }
        return dbh
    }

    func lastErrorMessage(fallback: String) -> String {
        Self.staticLastErrorMessage(fallback: fallback)
    }

    func decodeJSON<T: Decodable>(from ptr: UnsafeMutablePointer<FfiBoxedSlice>) throws -> T {
        let data = Data(bytes: ptr.pointee.ptr, count: Int(ptr.pointee.len))
        return try JSONDecoder().decode(T.self, from: data)
    }

    /// Reads the last error recorded by `libzcashlc` and clears it as a side
    /// effect, so subsequent failures do not surface a stale message.
    static func staticLastErrorMessage(fallback: String) -> String {
        let errorLen = zcashlc_last_error_length()
        defer { zcashlc_clear_last_error() }

        if errorLen > 0 {
            let error = UnsafeMutablePointer<Int8>.allocate(capacity: Int(errorLen))
            defer { error.deallocate() }
            zcashlc_error_message_utf8(error, errorLen)
            if let msg = String(validatingUTF8: error) {
                return msg
            }
        }
        return fallback
    }

    static func decodeJSONStatic<T: Decodable>(from ptr: UnsafeMutablePointer<FfiBoxedSlice>) throws -> T {
        let data = Data(bytes: ptr.pointee.ptr, count: Int(ptr.pointee.len))
        return try JSONDecoder().decode(T.self, from: data)
    }

    static func bytesFromBoxedSlice(_ ptr: UnsafeMutablePointer<FfiBoxedSlice>) -> [UInt8] {
        let len = Int(ptr.pointee.len)
        var bytes = [UInt8](repeating: 0, count: len)
        for i in 0..<len {
            bytes[i] = ptr.pointee.ptr.advanced(by: i).pointee
        }
        return bytes
    }
}

/// Convert FfiVotingHotkey to VotingHotkey.
private func hotkeyFromFfi(_ ffi: FfiVotingHotkey) -> VotingHotkey {
    var secretKey = [UInt8](repeating: 0, count: Int(ffi.secret_key_len))
    for i in 0..<Int(ffi.secret_key_len) {
        secretKey[i] = ffi.secret_key.advanced(by: i).pointee
    }

    var publicKey = [UInt8](repeating: 0, count: Int(ffi.public_key_len))
    for i in 0..<Int(ffi.public_key_len) {
        publicKey[i] = ffi.public_key.advanced(by: i).pointee
    }

    let address = ffi.address != nil ? String(cString: ffi.address) : ""

    return VotingHotkey(secretKey: secretKey, publicKey: publicKey, address: address)
}

// MARK: - Progress callback trampoline

private struct ProgressContext {
    let handler: VotingProgressHandler?
}

private func votingProgressTrampoline(progress: Double, context: UnsafeMutableRawPointer?) {
    guard let context else { return }
    let ctx = context.assumingMemoryBound(to: ProgressContext.self).pointee
    ctx.handler?(progress)
}

#if DEBUG
extension VotingRustBackend {
    func withLockedHandleForTesting(_ operation: () -> Void) throws {
        try withHandle { _ in
            operation()
        }
    }
}
#endif
