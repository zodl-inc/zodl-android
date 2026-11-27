package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRepository
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSigningBundle
import com.sparrowwallet.hummingbird.UR

class CreateVotingKeystonePcztEncoderUseCase(
    private val votingKeystoneRepository: VotingKeystoneRepository
) {
    suspend operator fun invoke(
        accountUuid: String,
        roundId: String
    ): VotingKeystoneSigningBundle =
        votingKeystoneRepository.createPcztEncoder(accountUuid, roundId)
}

class ParseVotingKeystonePCZTUseCase(
    private val votingKeystoneRepository: VotingKeystoneRepository,
    keystoneSDKProvider: KeystoneSDKProvider,
) : BaseKeystoneScanner(keystoneSDKProvider) {
    suspend operator fun invoke(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        result: String
    ): ParseKeystoneQrResult {
        this.accountUuid = accountUuid
        this.roundId = roundId
        this.bundleIndex = bundleIndex
        this.actionIndex = actionIndex
        return super.invoke(
            result = result,
            scanSessionId = votingScanSessionId(accountUuid, roundId, bundleIndex, actionIndex)
        )
    }

    private var accountUuid: String? = null
    private var roundId: String? = null
    private var bundleIndex: Int? = null
    private var actionIndex: Int? = null

    override suspend fun onSuccess(ur: UR) {
        votingKeystoneRepository.storeBundleSignature(
            accountUuid = requireNotNull(accountUuid),
            roundId = requireNotNull(roundId),
            bundleIndex = requireNotNull(bundleIndex),
            actionIndex = requireNotNull(actionIndex),
            signedPcztUr = ur
        )
    }

    private fun votingScanSessionId(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int
    ) = listOf(accountUuid, roundId, bundleIndex, actionIndex).joinToString(separator = ":")
}
