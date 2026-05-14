package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.KeystoneSDKException
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRepository
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSigningBundle
import com.keystone.module.DecodeResult
import com.keystone.module.ZcashAccounts
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParseVotingKeystonePCZTUseCaseTest {
    @Test
    fun resetsQrDecoderWhenVotingBundleChanges() =
        runBlocking {
            val keystoneSDKProvider = FakeKeystoneSDKProvider()
            val useCase =
                ParseVotingKeystonePCZTUseCase(
                    votingKeystoneRepository = FakeVotingKeystoneRepository(),
                    keystoneSDKProvider = keystoneSDKProvider
                )

            assertFailsWith<InvalidKeystonePCZTQRException> {
                useCase(
                    accountUuid = ACCOUNT_UUID,
                    roundId = ROUND_ID,
                    bundleIndex = 0,
                    actionIndex = 10,
                    result = "first-bundle-frame-1"
                )
            }
            assertFailsWith<InvalidKeystonePCZTQRException> {
                useCase(
                    accountUuid = ACCOUNT_UUID,
                    roundId = ROUND_ID,
                    bundleIndex = 0,
                    actionIndex = 10,
                    result = "first-bundle-frame-2"
                )
            }
            assertEquals(0, keystoneSDKProvider.resetCallCount)

            assertFailsWith<InvalidKeystonePCZTQRException> {
                useCase(
                    accountUuid = ACCOUNT_UUID,
                    roundId = ROUND_ID,
                    bundleIndex = 1,
                    actionIndex = 11,
                    result = "second-bundle-frame-1"
                )
            }
            assertEquals(1, keystoneSDKProvider.resetCallCount)
        }
}

private class FakeKeystoneSDKProvider : KeystoneSDKProvider {
    var resetCallCount = 0

    override fun decodeQR(result: String): DecodeResult =
        throw KeystoneSDKException(IllegalArgumentException("invalid QR"))

    override fun resetQRDecoder() {
        resetCallCount += 1
    }

    override fun parseZcashAccounts(ur: UR): ZcashAccounts = error("Unused")

    override fun generatePczt(pczt: ByteArray): UREncoder = error("Unused")

    override fun parsePczt(ur: UR): ByteArray = error("Unused")
}

private class FakeVotingKeystoneRepository : VotingKeystoneRepository {
    override suspend fun createPcztEncoder(
        accountUuid: String,
        roundId: String
    ): VotingKeystoneSigningBundle = error("Unused")

    override suspend fun storeBundleSignature(
        accountUuid: String,
        roundId: String,
        bundleIndex: Int,
        actionIndex: Int,
        signedPcztUr: UR
    ) = error("Unused")
}

private const val ACCOUNT_UUID = "account-uuid"
private const val ROUND_ID = "round-id"
