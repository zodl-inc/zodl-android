package co.electriccoin.zcash.ui.common.repository

import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder

data class VotingKeystoneSigningBundle(
    val roundId: String,
    val roundTitle: String,
    val bundleIndex: Int,
    val bundleCount: Int,
    val actionIndex: Int,
    val memoWeightZatoshi: Long,
    val encoder: UREncoder,
)

sealed class VotingKeystoneResumeSubmissionException(
    message: String
) : Exception(message)

class VotingKeystoneBundlesAlreadySignedException(
    roundId: String
) : VotingKeystoneResumeSubmissionException(
        "All Keystone voting bundles are already signed for round $roundId"
    )

sealed class VotingKeystoneSignatureRejectedException(
    message: String
) : Exception(message)

class VotingKeystoneDuplicateSignatureException(
    val signedBundleIndex: Int,
    val currentBundleIndex: Int,
    val bundleCount: Int
) : VotingKeystoneSignatureRejectedException(
        "Keystone signature for bundle $signedBundleIndex was scanned while waiting for bundle $currentBundleIndex"
    )

class VotingKeystoneWrongSignatureException(
    val currentBundleIndex: Int,
    val bundleCount: Int
) : VotingKeystoneSignatureRejectedException(
        "Signed Keystone PCZT does not match pending bundle $currentBundleIndex"
    )

/**
 * voting-5.0.0 round-driver port note: Keystone signing (Task 7 of the app-side port plan) is
 * deliberately DEFERRED for this benchmark pass — the new session-based signing flow
 * (`VotingRoundSession.getKeystoneSigningRequests`, batch, produced from a cached delegation
 * pipeline) needs an explicit session-lifetime design across the multi-screen signing UI before
 * it's re-implemented for real. Stubbed here rather than deleted so the interface's callers
 * (Keystone-only UI/use-case code) still compile; Keystone accounts are excluded from this pass's
 * benchmark runs. Do not resume this without re-reading the plan's Task 7 section.
 */
interface VotingKeystoneRepository {
    suspend fun createPcztEncoder(
        accountUuid: String,
        roundId: String
    ): VotingKeystoneSigningBundle

    suspend fun storeBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    )
}

class VotingKeystoneRepositoryImpl : VotingKeystoneRepository {
    override suspend fun createPcztEncoder(
        accountUuid: String,
        roundId: String
    ): VotingKeystoneSigningBundle =
        error(
            "Keystone voting signing is deferred for the voting-5.0.0 round-driver port " +
                "(Task 7) — not available in this build."
        )

    override suspend fun storeBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    ): Unit =
        error(
            "Keystone voting signing is deferred for the voting-5.0.0 round-driver port " +
                "(Task 7) — not available in this build."
        )
}
