package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

enum class VotingRecoveryPhase {
    INITIALIZED,
    BUNDLES_PREPARED,
    HOTKEY_READY,
    DELEGATION_PROVED,
    DELEGATION_SUBMITTED,
    VOTES_SUBMITTED,
    SHARES_SUBMITTED
}

data class VotingProposalSelection(
    val choiceId: Int,
    val numOptions: Int
)

data class VotingKeystoneBundleSignature(
    val spendAuthSigBase64: String,
    val sighashBase64: String,
    val rkBase64: String? = null
) {
    fun decodeSpendAuthSig(): ByteArray = Base64.getDecoder().decode(spendAuthSigBase64)

    fun decodeSighash(): ByteArray = Base64.getDecoder().decode(sighashBase64)

    fun decodeRk(): ByteArray? = rkBase64?.let(Base64.getDecoder()::decode)
}

enum class VotingKeystoneRouteStage {
    SIGN,
    SCAN
}

data class VotingPendingKeystoneRequest(
    val bundleIndex: Int,
    val actionIndex: Int,
    val redactedPcztBase64: String,
    val expectedSighashBase64: String,
    val expectedRkBase64: String? = null,
    val routeStage: VotingKeystoneRouteStage = VotingKeystoneRouteStage.SIGN
) {
    fun decodeRedactedPczt(): ByteArray = Base64.getDecoder().decode(redactedPcztBase64)

    fun decodeExpectedSighash(): ByteArray = Base64.getDecoder().decode(expectedSighashBase64)

    fun decodeExpectedRk(): ByteArray? = expectedRkBase64?.let(Base64.getDecoder()::decode)
}

data class VotingRecoverySnapshot(
    val accountUuid: String,
    val roundId: String,
    val phase: VotingRecoveryPhase = VotingRecoveryPhase.INITIALIZED,
    val bundleCount: Int? = null,
    val eligibleWeight: Long? = null,
    val bundleWeights: List<Long> = emptyList(),
    val skippedBundleCount: Int = 0,
    val submittedAtEpochSeconds: Long? = null,
    val voteEndEpochSeconds: Long? = null,
    // Legacy recovery snapshots stored the voting hotkey seed inline here.
    // New writes keep the seed in VotingHotkeySeedProvider and leave this for migration.
    val hotkeySeedBase64: String? = null,
    val hotkeyAddress: String? = null,
    val voteServerUrls: List<String> = emptyList(),
    val singleShareMode: Boolean? = null,
    val draftChoices: Map<Int, Int> = emptyMap(),
    val proposalSelections: Map<Int, VotingProposalSelection> = emptyMap(),
    val keystoneBundleSignatures: Map<Int, VotingKeystoneBundleSignature> = emptyMap(),
    val pendingKeystoneRequest: VotingPendingKeystoneRequest? = null,
    val submittedProposalIds: Set<Int> = emptySet(),
    val updatedAt: Instant = Instant.now()
) {
    fun decodeHotkeySeed(): ByteArray? =
        hotkeySeedBase64?.let { encoded -> Base64.getDecoder().decode(encoded) }
}

interface VotingRecoveryRepository {
    fun observe(
        accountUuid: String,
        roundId: String
    ): Flow<VotingRecoverySnapshot?>

    suspend fun get(
        accountUuid: String,
        roundId: String
    ): VotingRecoverySnapshot?

    suspend fun store(snapshot: VotingRecoverySnapshot)

    suspend fun setPhase(
        accountUuid: String,
        roundId: String,
        phase: VotingRecoveryPhase
    )

    suspend fun storeBundleSetup(
        accountUuid: String,
        roundId: String,
        bundleCount: Int,
        eligibleWeight: Long,
        bundleWeights: List<Long>
    )

    suspend fun setEligibleWeight(
        accountUuid: String,
        roundId: String,
        eligibleWeight: Long
    )

    suspend fun storeVoteEndEpochSeconds(
        accountUuid: String,
        roundId: String,
        voteEndEpochSeconds: Long
    )

    suspend fun storeSubmittedAt(
        accountUuid: String,
        roundId: String,
        submittedAtEpochSeconds: Long
    )

    suspend fun storeHotkey(
        accountUuid: String,
        roundId: String,
        hotkeyAddress: String
    )

    suspend fun storeVoteServerUrls(
        accountUuid: String,
        roundId: String,
        voteServerUrls: List<String>
    )

    suspend fun storeDraftChoices(
        accountUuid: String,
        roundId: String,
        draftChoices: Map<Int, Int>
    )

    suspend fun storeProposalSelections(
        accountUuid: String,
        roundId: String,
        proposalSelections: Map<Int, VotingProposalSelection>
    )

    suspend fun storeKeystoneBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        spendAuthSig: ByteArray,
        sighash: ByteArray,
        rk: ByteArray? = null
    )

    suspend fun storePendingKeystoneRequest(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        redactedPczt: ByteArray,
        expectedSighash: ByteArray,
        expectedRk: ByteArray? = null
    )

    suspend fun setPendingKeystoneRouteStage(
        accountUuid: String,
        roundId: String,
        routeStage: VotingKeystoneRouteStage
    )

    suspend fun clearPendingKeystoneRequest(
        accountUuid: String,
        roundId: String
    )

    suspend fun skipRemainingKeystoneBundles(
        accountUuid: String,
        roundId: String,
        keepCount: Int
    ): VotingRecoverySnapshot

    suspend fun storeSingleShareMode(
        accountUuid: String,
        roundId: String,
        singleShareMode: Boolean
    )

    suspend fun markProposalSubmitted(
        accountUuid: String,
        roundId: String,
        proposalId: Int
    )

    suspend fun clearRound(
        accountUuid: String,
        roundId: String
    )

    /**
     * Enumerates round ids for the given account that have a recorded submission
     * (`submittedAtEpochSeconds != null` and at least one entry in `submittedProposalIds`)
     * and may therefore have outstanding share-tracking work. The recovery snapshot does not
     * track when share confirmation finishes, so the resulting list is conservative: callers
     * should pass each round id to the share-tracking worker, which is idempotent and
     * short-circuits when there are no unconfirmed shares.
     */
    suspend fun getRoundIdsRequiringShareTracking(accountUuid: String): List<String>
}

class VotingRecoveryRepositoryImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : VotingRecoveryRepository {
    override fun observe(
        accountUuid: String,
        roundId: String
    ): Flow<VotingRecoverySnapshot?> =
        flow {
            emit(get(accountUuid, roundId))
            emitAll(
                encryptedPreferenceProvider()
                    .observe(key = key(accountUuid, roundId))
                    .map { encoded -> encoded?.toVotingRecoverySnapshot() }
            )
        }

    override suspend fun get(
        accountUuid: String,
        roundId: String
    ): VotingRecoverySnapshot? {
        val scopedKey = key(accountUuid, roundId)
        val preferenceProvider = encryptedPreferenceProvider()

        preferenceProvider
            .getString(scopedKey)
            ?.toVotingRecoverySnapshot()
            ?.let { return it }

        val legacySnapshot =
            preferenceProvider
                .getString(legacyKey(roundId))
                ?.toVotingRecoverySnapshot()
                ?: return null

        val migratedSnapshot =
            legacySnapshot.copy(
                accountUuid = accountUuid,
                roundId = roundId,
                updatedAt = Instant.now()
            )

        preferenceProvider.putString(
            key = scopedKey,
            value = migratedSnapshot.encode()
        )
        preferenceProvider.remove(legacyKey(roundId))

        return migratedSnapshot
    }

    override suspend fun store(snapshot: VotingRecoverySnapshot) {
        val preferenceProvider = encryptedPreferenceProvider()
        preferenceProvider.putString(
            key = key(snapshot.accountUuid, snapshot.roundId),
            value = snapshot.encode()
        )
        addToIndex(preferenceProvider, snapshot.accountUuid, snapshot.roundId)
    }

    override suspend fun setPhase(
        accountUuid: String,
        roundId: String,
        phase: VotingRecoveryPhase
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                phase = phase,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeBundleSetup(
        accountUuid: String,
        roundId: String,
        bundleCount: Int,
        eligibleWeight: Long,
        bundleWeights: List<Long>
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                phase = VotingRecoveryPhase.BUNDLES_PREPARED,
                bundleCount = bundleCount,
                eligibleWeight = eligibleWeight,
                bundleWeights = bundleWeights,
                skippedBundleCount = 0,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun setEligibleWeight(
        accountUuid: String,
        roundId: String,
        eligibleWeight: Long
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                eligibleWeight = eligibleWeight,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeVoteEndEpochSeconds(
        accountUuid: String,
        roundId: String,
        voteEndEpochSeconds: Long
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                voteEndEpochSeconds = voteEndEpochSeconds,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeSubmittedAt(
        accountUuid: String,
        roundId: String,
        submittedAtEpochSeconds: Long
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                submittedAtEpochSeconds = submittedAtEpochSeconds,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeHotkey(
        accountUuid: String,
        roundId: String,
        hotkeyAddress: String
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                phase = VotingRecoveryPhase.HOTKEY_READY,
                hotkeyAddress = hotkeyAddress,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeVoteServerUrls(
        accountUuid: String,
        roundId: String,
        voteServerUrls: List<String>
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                voteServerUrls =
                    voteServerUrls
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .map { url -> if (url.endsWith('/')) url.dropLast(1) else url }
                        .distinct(),
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeDraftChoices(
        accountUuid: String,
        roundId: String,
        draftChoices: Map<Int, Int>
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                draftChoices = draftChoices.toMap(),
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeProposalSelections(
        accountUuid: String,
        roundId: String,
        proposalSelections: Map<Int, VotingProposalSelection>
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                proposalSelections = current.proposalSelections + proposalSelections,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storeKeystoneBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        spendAuthSig: ByteArray,
        sighash: ByteArray,
        rk: ByteArray?
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                keystoneBundleSignatures =
                    current.keystoneBundleSignatures + (
                        bundleIndex to
                            VotingKeystoneBundleSignature(
                                spendAuthSigBase64 = Base64.getEncoder().encodeToString(spendAuthSig),
                                sighashBase64 = Base64.getEncoder().encodeToString(sighash),
                                rkBase64 = rk?.let(Base64.getEncoder()::encodeToString)
                            )
                    ),
                pendingKeystoneRequest =
                    current.pendingKeystoneRequest
                        ?.takeUnless { request -> request.bundleIndex == bundleIndex },
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun storePendingKeystoneRequest(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        redactedPczt: ByteArray,
        expectedSighash: ByteArray,
        expectedRk: ByteArray?
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                pendingKeystoneRequest =
                    VotingPendingKeystoneRequest(
                        bundleIndex = bundleIndex,
                        actionIndex = actionIndex,
                        redactedPcztBase64 = Base64.getEncoder().encodeToString(redactedPczt),
                        expectedSighashBase64 = Base64.getEncoder().encodeToString(expectedSighash),
                        expectedRkBase64 = expectedRk?.let(Base64.getEncoder()::encodeToString),
                        routeStage = VotingKeystoneRouteStage.SIGN
                    ),
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun setPendingKeystoneRouteStage(
        accountUuid: String,
        roundId: String,
        routeStage: VotingKeystoneRouteStage
    ) {
        val current = get(accountUuid, roundId) ?: return
        val pendingRequest = current.pendingKeystoneRequest ?: return
        if (pendingRequest.routeStage == routeStage) {
            return
        }
        store(
            current.copy(
                pendingKeystoneRequest = pendingRequest.copy(routeStage = routeStage),
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun clearPendingKeystoneRequest(
        accountUuid: String,
        roundId: String
    ) {
        val current = get(accountUuid, roundId) ?: return
        if (current.pendingKeystoneRequest == null) {
            return
        }
        store(
            current.copy(
                pendingKeystoneRequest = null,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun skipRemainingKeystoneBundles(
        accountUuid: String,
        roundId: String,
        keepCount: Int
    ): VotingRecoverySnapshot {
        val current =
            requireNotNull(get(accountUuid, roundId)) {
                "Voting round $roundId has not been prepared"
            }
        return current.withRemainingKeystoneBundlesSkipped(keepCount).also { updated ->
            store(updated)
        }
    }

    override suspend fun storeSingleShareMode(
        accountUuid: String,
        roundId: String,
        singleShareMode: Boolean
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.copy(
                singleShareMode = singleShareMode,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun markProposalSubmitted(
        accountUuid: String,
        roundId: String,
        proposalId: Int
    ) {
        val current =
            get(accountUuid, roundId) ?: VotingRecoverySnapshot(
                accountUuid = accountUuid,
                roundId = roundId
            )
        store(
            current.withProposalSubmitted(proposalId)
        )
    }

    override suspend fun clearRound(
        accountUuid: String,
        roundId: String
    ) {
        val preferenceProvider = encryptedPreferenceProvider()
        preferenceProvider.remove(key(accountUuid, roundId))
        preferenceProvider.remove(legacyKey(roundId))
        removeFromIndex(preferenceProvider, accountUuid, roundId)
    }

    override suspend fun getRoundIdsRequiringShareTracking(accountUuid: String): List<String> {
        val preferenceProvider = encryptedPreferenceProvider()
        val scopedAccount = accountUuid.lowercase()
        val pairs = readIndex(preferenceProvider)
        if (pairs.isEmpty()) {
            return emptyList()
        }
        return pairs
            .filter { (indexedAccount, _) -> indexedAccount == scopedAccount }
            .mapNotNull { (_, indexedRoundId) ->
                val snapshot =
                    get(scopedAccount, indexedRoundId) ?: run {
                        // Stale index entry — drop it lazily.
                        removeFromIndex(preferenceProvider, scopedAccount, indexedRoundId)
                        return@mapNotNull null
                    }
                if (snapshot.submittedProposalIds.isNotEmpty()) {
                    snapshot.roundId
                } else {
                    null
                }
            }
    }

    private fun key(
        accountUuid: String,
        roundId: String
    ) = PreferenceKey(
        "voting_recovery_${accountUuid.lowercase()}_${roundId.lowercase()}"
    )

    private fun legacyKey(roundId: String) =
        PreferenceKey(
            "voting_recovery_${roundId.lowercase()}"
        )

    private suspend fun readIndex(
        preferenceProvider: PreferenceProvider
    ): List<Pair<String, String>> {
        val encoded = preferenceProvider.getString(INDEX_KEY) ?: return emptyList()
        return decodeIndex(encoded)
    }

    private suspend fun addToIndex(
        preferenceProvider: PreferenceProvider,
        accountUuid: String,
        roundId: String
    ) {
        val scopedAccount = accountUuid.lowercase()
        val scopedRound = roundId.lowercase()
        val current = readIndex(preferenceProvider)
        if (current.any { (a, r) -> a == scopedAccount && r == scopedRound }) {
            return
        }
        preferenceProvider.putString(
            key = INDEX_KEY,
            value = encodeIndex(current + (scopedAccount to scopedRound))
        )
    }

    private suspend fun removeFromIndex(
        preferenceProvider: PreferenceProvider,
        accountUuid: String,
        roundId: String
    ) {
        val scopedAccount = accountUuid.lowercase()
        val scopedRound = roundId.lowercase()
        val current = readIndex(preferenceProvider)
        val updated = current.filterNot { (a, r) -> a == scopedAccount && r == scopedRound }
        if (updated.size == current.size) {
            return
        }
        if (updated.isEmpty()) {
            preferenceProvider.remove(INDEX_KEY)
        } else {
            preferenceProvider.putString(
                key = INDEX_KEY,
                value = encodeIndex(updated)
            )
        }
    }

    private fun encodeIndex(pairs: List<Pair<String, String>>): String {
        val array = JSONArray()
        pairs.forEach { (accountUuid, roundId) ->
            array.put(
                JSONObject()
                    .put("account_uuid", accountUuid)
                    .put("round_id", roundId)
            )
        }
        return array.toString()
    }

    private fun decodeIndex(encoded: String): List<Pair<String, String>> =
        runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val accountUuid = entry.optString("account_uuid").takeIf(String::isNotEmpty) ?: continue
                    val roundId = entry.optString("round_id").takeIf(String::isNotEmpty) ?: continue
                    add(accountUuid to roundId)
                }
            }
        }.getOrDefault(emptyList())

    private companion object {
        val INDEX_KEY = PreferenceKey("voting_recovery_index")
    }
}

private fun VotingRecoverySnapshot.encode(): String =
    JSONObject()
        .put("account_uuid", accountUuid)
        .put("round_id", roundId)
        .put("phase", phase.name)
        .put("bundle_count", bundleCount)
        .put("eligible_weight", eligibleWeight)
        .put("bundle_weights", JSONArray(bundleWeights))
        .put("skipped_bundle_count", skippedBundleCount)
        .put("submitted_at_epoch_seconds", submittedAtEpochSeconds)
        .put("vote_end_epoch_seconds", voteEndEpochSeconds)
        .put("hotkey_seed", hotkeySeedBase64)
        .put("hotkey_address", hotkeyAddress)
        .put("vote_server_urls", JSONArray(voteServerUrls))
        .put("single_share_mode", singleShareMode)
        .put(
            "draft_choices",
            JSONObject().apply {
                draftChoices.toSortedMap().forEach { (proposalId, choiceId) ->
                    put(proposalId.toString(), choiceId)
                }
            }
        ).put(
            "proposal_selections",
            JSONObject().apply {
                proposalSelections.toSortedMap().forEach { (proposalId, selection) ->
                    put(
                        proposalId.toString(),
                        JSONObject()
                            .put("choice_id", selection.choiceId)
                            .put("num_options", selection.numOptions)
                    )
                }
            }
        ).put(
            "keystone_bundle_signatures",
            JSONObject().apply {
                keystoneBundleSignatures.toSortedMap().forEach { (bundleIndex, signature) ->
                    put(
                        bundleIndex.toString(),
                        JSONObject()
                            .put("spend_auth_sig", signature.spendAuthSigBase64)
                            .put("sighash", signature.sighashBase64)
                            .put("rk", signature.rkBase64)
                    )
                }
            }
        ).put(
            "pending_keystone_request",
            pendingKeystoneRequest?.let { request ->
                JSONObject()
                    .put("bundle_index", request.bundleIndex)
                    .put("action_index", request.actionIndex)
                    .put("redacted_pczt", request.redactedPcztBase64)
                    .put("expected_sighash", request.expectedSighashBase64)
                    .put("expected_rk", request.expectedRkBase64)
                    .put("route_stage", request.routeStage.name)
            }
        ).put("submitted_proposal_ids", JSONArray(submittedProposalIds.sorted()))
        .put("updated_at", updatedAt.toEpochMilli())
        .toString()

private fun String.toVotingRecoverySnapshot(): VotingRecoverySnapshot {
    val json = JSONObject(this)

    return VotingRecoverySnapshot(
        accountUuid = json.optString("account_uuid"),
        roundId = json.getString("round_id"),
        phase =
            json
                .optString("phase")
                .takeIf { it.isNotEmpty() }
                ?.let(VotingRecoveryPhase::valueOf)
                ?: VotingRecoveryPhase.INITIALIZED,
        bundleCount =
            json
                .optInt("bundle_count")
                .takeIf { json.has("bundle_count") && !json.isNull("bundle_count") },
        eligibleWeight =
            json
                .optLong("eligible_weight")
                .takeIf { json.has("eligible_weight") && !json.isNull("eligible_weight") },
        bundleWeights =
            buildList {
                val weightsJson = json.optJSONArray("bundle_weights") ?: JSONArray()
                for (index in 0 until weightsJson.length()) {
                    add(weightsJson.getLong(index))
                }
            },
        skippedBundleCount =
            json
                .optInt("skipped_bundle_count")
                .takeIf { json.has("skipped_bundle_count") && !json.isNull("skipped_bundle_count") }
                ?: 0,
        submittedAtEpochSeconds =
            json
                .optLong("submitted_at_epoch_seconds")
                .takeIf { json.has("submitted_at_epoch_seconds") && !json.isNull("submitted_at_epoch_seconds") },
        voteEndEpochSeconds =
            json
                .optLong("vote_end_epoch_seconds")
                .takeIf { json.has("vote_end_epoch_seconds") && !json.isNull("vote_end_epoch_seconds") },
        hotkeySeedBase64 = json.optString("hotkey_seed").takeIf { it.isNotEmpty() },
        hotkeyAddress = json.optString("hotkey_address").takeIf { it.isNotEmpty() },
        voteServerUrls =
            buildList {
                val voteServersJson = json.optJSONArray("vote_server_urls") ?: JSONArray()
                for (index in 0 until voteServersJson.length()) {
                    add(voteServersJson.getString(index))
                }
            },
        singleShareMode =
            json
                .optBoolean("single_share_mode")
                .takeIf { json.has("single_share_mode") && !json.isNull("single_share_mode") },
        draftChoices =
            buildMap {
                val draftChoicesJson = json.optJSONObject("draft_choices") ?: JSONObject()
                draftChoicesJson.keys().forEach { proposalId ->
                    if (!draftChoicesJson.isNull(proposalId)) {
                        put(proposalId.toInt(), draftChoicesJson.getInt(proposalId))
                    }
                }
            },
        proposalSelections =
            buildMap {
                val selectionsJson = json.optJSONObject("proposal_selections") ?: JSONObject()
                selectionsJson.keys().forEach { proposalId ->
                    val selection = selectionsJson.optJSONObject(proposalId) ?: return@forEach
                    put(
                        proposalId.toInt(),
                        VotingProposalSelection(
                            choiceId = selection.getInt("choice_id"),
                            numOptions = selection.getInt("num_options")
                        )
                    )
                }
            },
        keystoneBundleSignatures =
            buildMap {
                val signaturesJson = json.optJSONObject("keystone_bundle_signatures") ?: JSONObject()
                signaturesJson.keys().forEach { bundleIndex ->
                    val signature = signaturesJson.optJSONObject(bundleIndex) ?: return@forEach
                    put(
                        bundleIndex.toInt(),
                        VotingKeystoneBundleSignature(
                            spendAuthSigBase64 = signature.getString("spend_auth_sig"),
                            sighashBase64 = signature.getString("sighash"),
                            rkBase64 = signature.optString("rk").takeIf(String::isNotEmpty)
                        )
                    )
                }
            },
        pendingKeystoneRequest =
            json
                .optJSONObject("pending_keystone_request")
                ?.let { request ->
                    VotingPendingKeystoneRequest(
                        bundleIndex = request.getInt("bundle_index"),
                        actionIndex = request.getInt("action_index"),
                        redactedPcztBase64 = request.getString("redacted_pczt"),
                        expectedSighashBase64 = request.getString("expected_sighash"),
                        expectedRkBase64 = request.optString("expected_rk").takeIf(String::isNotEmpty),
                        routeStage =
                            request
                                .optString("route_stage")
                                .takeIf(String::isNotEmpty)
                                ?.let(VotingKeystoneRouteStage::valueOf)
                                ?: VotingKeystoneRouteStage.SIGN
                    )
                },
        submittedProposalIds =
            buildSet {
                val submittedIds = json.optJSONArray("submitted_proposal_ids") ?: JSONArray()
                for (index in 0 until submittedIds.length()) {
                    add(submittedIds.getInt(index))
                }
            },
        updatedAt =
            json
                .optLong("updated_at")
                .takeIf { json.has("updated_at") }
                ?.let(Instant::ofEpochMilli)
                ?: Instant.now()
    )
}
