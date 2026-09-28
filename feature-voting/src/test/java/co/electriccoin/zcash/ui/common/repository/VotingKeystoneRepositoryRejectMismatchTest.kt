package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSigningRequest
import org.junit.Test
import kotlin.test.assertFailsWith

class VotingKeystoneRepositoryRejectMismatchTest {
    @Test
    fun `a signature matching a different already-signed bundle is a duplicate, not accepted`() {
        val requests =
            listOf(
                fakeRequest(bundleIndex = 0, sighash = byteArrayOf(1), rk = byteArrayOf(1)),
                fakeRequest(bundleIndex = 1, sighash = byteArrayOf(2), rk = byteArrayOf(2))
            )
        // Scanning bundle 0's sighash while bundle 1 is the currently-pending one is a
        // duplicate/stale re-scan, not a valid signature for the pending bundle.
        assertFailsWith<VotingKeystoneDuplicateSignatureException> {
            rejectMismatchedKeystoneSighash(
                scannedSighash = byteArrayOf(1),
                pendingBundleIndex = 1,
                requests = requests
            )
        }
    }

    @Test
    fun `a signature matching neither request is a wrong-device signature`() {
        val requests = listOf(fakeRequest(bundleIndex = 0, sighash = byteArrayOf(1), rk = byteArrayOf(1)))
        assertFailsWith<VotingKeystoneWrongSignatureException> {
            rejectMismatchedKeystoneSighash(
                scannedSighash = byteArrayOf(9, 9, 9),
                pendingBundleIndex = 0,
                requests = requests
            )
        }
    }

    private fun fakeRequest(
        bundleIndex: Int,
        sighash: ByteArray,
        rk: ByteArray
    ) = VotingKeystoneSigningRequest(
        pcztBytes = ByteArray(0),
        redactedPcztBytes = ByteArray(0),
        pcztSighash = sighash,
        rk = rk,
        actionIndex = 0,
        displayMemo = "",
        eligibleWeightZatoshi = 0,
        delegatedWeightZatoshi = 0,
        bundleCount = requestsBundleCountFixture,
        bundleIndex = bundleIndex
    )

    private val requestsBundleCountFixture = 2
}
