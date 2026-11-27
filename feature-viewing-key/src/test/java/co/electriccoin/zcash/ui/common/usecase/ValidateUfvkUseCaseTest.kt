package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.UnifiedFullViewingKeyComponents
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.ufvk.UfvkFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Only a full viewing key for the wallet's network that the SDK reads as carrying both an Orchard
 * and a transparent P2PKH component is importable. Text that is mixed case or does not start with
 * the network's key prefix is rejected without reaching the SDK, and no outcome prints the key. A key
 * the SDK fails to read is a fault, reported as such without the SDK's exception, never as invalid.
 */
class ValidateUfvkUseCaseTest {
    private val describedKeys = mutableListOf<Pair<String, ZcashNetwork>>()

    private var sdkComponents: UnifiedFullViewingKeyComponents? = FULL_COMPONENTS

    private var sdkFailure: Exception? = null

    private val useCase =
        ValidateUfvkUseCase(
            object : UfvkDescriber {
                override suspend fun describe(
                    ufvk: String,
                    network: ZcashNetwork,
                ): UnifiedFullViewingKeyComponents? {
                    describedKeys += ufvk to network
                    sdkFailure?.let { throw it }
                    return sdkComponents
                }
            }
        )

    @Test
    fun aMainnetKeyWithEveryComponentIsValidOnMainnet() =
        runTest {
            val result = useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Mainnet)

            assertEquals(UfvkFixtures.MAINNET_FULL, assertIs<UfvkValidation.Valid>(result).ufvk)
            assertEquals(listOf(UfvkFixtures.MAINNET_FULL to ZcashNetwork.Mainnet), describedKeys)
        }

    @Test
    fun aKeyWithTransparentAndOrchardButNoSaplingIsValid() =
        runTest {
            sdkComponents = FULL_COMPONENTS.copy(hasSapling = false)

            val result = useCase(UfvkFixtures.MAINNET_TRANSPARENT_AND_ORCHARD, ZcashNetwork.Mainnet)

            assertEquals(UfvkFixtures.MAINNET_TRANSPARENT_AND_ORCHARD, assertIs<UfvkValidation.Valid>(result).ufvk)
        }

    @Test
    fun surroundingWhitespaceAndUpperCaseAreAccepted() =
        runTest {
            val result = useCase("  \n" + UfvkFixtures.MAINNET_FULL.uppercase() + "\t ", ZcashNetwork.Mainnet)

            assertEquals(UfvkFixtures.MAINNET_FULL, assertIs<UfvkValidation.Valid>(result).ufvk)
            assertEquals(listOf(UfvkFixtures.MAINNET_FULL to ZcashNetwork.Mainnet), describedKeys)
        }

    @Test
    fun aMixedCaseKeyIsRejected() =
        runTest {
            val mixed = UfvkFixtures.MAINNET_FULL.take(10).uppercase() + UfvkFixtures.MAINNET_FULL.drop(10)

            assertInvalid(useCase(mixed, ZcashNetwork.Mainnet))
            assertInvalid(useCase("U" + UfvkFixtures.MAINNET_FULL.drop(1), ZcashNetwork.Mainnet))
            assertTrue(describedKeys.isEmpty())
        }

    @Test
    fun whitespaceAndZeroWidthCharactersInsideTheKeyAreDropped() =
        runTest {
            val separators = listOf("\n", " ", "\r\n", "\u200B", "\u200C", "\u200D", "\uFEFF", "\t", "\u2060")
            val broken =
                UfvkFixtures.MAINNET_FULL
                    .chunked(40)
                    .mapIndexed { index, chunk -> chunk + separators[index % separators.size] }
                    .joinToString("")

            val result = useCase("\uFEFF" + broken, ZcashNetwork.Mainnet)

            assertEquals(UfvkFixtures.MAINNET_FULL, assertIs<UfvkValidation.Valid>(result).ufvk)
            assertEquals(listOf(UfvkFixtures.MAINNET_FULL to ZcashNetwork.Mainnet), describedKeys)
        }

    @Test
    fun anUpperCaseKeyWithLineBreaksIsStoredInCanonicalLowerCase() =
        runTest {
            val wrapped =
                UfvkFixtures.MAINNET_FULL
                    .uppercase()
                    .chunked(60)
                    .joinToString("\n")

            val result = useCase(wrapped, ZcashNetwork.Mainnet)

            assertEquals(UfvkFixtures.MAINNET_FULL, assertIs<UfvkValidation.Valid>(result).ufvk)
        }

    @Test
    fun aKeyForTheOtherNetworkIsRejectedWithoutReachingTheSdk() =
        runTest {
            assertInvalid(useCase(TESTNET_KEY, ZcashNetwork.Mainnet))
            assertInvalid(useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Testnet))
            assertTrue(describedKeys.isEmpty())
        }

    @Test
    fun aTestnetKeyIsDescribedForTestnet() =
        runTest {
            val result = useCase(TESTNET_KEY, ZcashNetwork.Testnet)

            assertEquals(TESTNET_KEY, assertIs<UfvkValidation.Valid>(result).ufvk)
            assertEquals(listOf(TESTNET_KEY to ZcashNetwork.Testnet), describedKeys)
        }

    @Test
    fun aKeyWithoutATransparentP2pkhComponentIsRejected() =
        runTest {
            sdkComponents = FULL_COMPONENTS.copy(hasP2pkh = false)

            assertInvalid(useCase(UfvkFixtures.MAINNET_WITHOUT_TRANSPARENT, ZcashNetwork.Mainnet))
        }

    @Test
    fun aKeyWhoseOnlyTransparentComponentIsP2shIsRejected() =
        runTest {
            sdkComponents = FULL_COMPONENTS.copy(hasP2pkh = false, hasP2sh = true)

            assertInvalid(useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Mainnet))
        }

    @Test
    fun aKeyWithoutAnOrchardComponentIsRejected() =
        runTest {
            sdkComponents = FULL_COMPONENTS.copy(hasOrchard = false)

            assertInvalid(useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Mainnet))
        }

    @Test
    fun aSaplingOnlyKeyIsRejected() =
        runTest {
            sdkComponents = FULL_COMPONENTS.copy(hasP2pkh = false, hasOrchard = false)

            assertInvalid(useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Mainnet))
        }

    @Test
    fun anIncomingViewingKeyIsRejectedWithoutReachingTheSdk() =
        runTest {
            assertInvalid(useCase(UfvkFixtures.MAINNET_UIVK, ZcashNetwork.Mainnet))
            assertTrue(describedKeys.isEmpty())
        }

    @Test
    fun garbageWithoutTheKeyPrefixIsRejectedWithoutReachingTheSdk() =
        runTest {
            listOf(
                "",
                "   ",
                "hello",
                "u1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq",
            ).forEach { input ->
                assertInvalid(useCase(input, ZcashNetwork.Mainnet))
            }
            assertTrue(describedKeys.isEmpty())
        }

    @Test
    fun aKeyTheSdkCannotReadIsRejected() =
        runTest {
            sdkComponents = null

            listOf(
                "uview1",
                UfvkFixtures.MAINNET_FULL.dropLast(1),
                UfvkFixtures.MAINNET_FULL + "q",
                UfvkFixtures.MAINNET_FULL,
            ).forEach { input ->
                assertInvalid(useCase(input, ZcashNetwork.Mainnet))
            }
        }

    @Test
    fun aKeyTheSdkFailsToReadIsAFaultWithoutTheKeyInItsStackTrace() =
        runTest {
            sdkFailure = IllegalStateException("describe(${UfvkFixtures.MAINNET_FULL})", RuntimeException("uview"))

            val thrown =
                assertFailsWith<ViewOnlyKeyCheckException> { useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Mainnet) }

            assertTrue(generateSequence<Throwable>(thrown) { it.cause }.all { it is ViewOnlyKeyCheckException })
            assertFalse(thrown.stackTraceToString().contains("uview"))
        }

    @Test
    fun aCancelledSdkReadIsRethrownNotReportedAsAFault() =
        runTest {
            sdkFailure = CancellationException("cancelled")

            assertFailsWith<CancellationException> { useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Mainnet) }
        }

    @Test
    fun theValidOutcomeNeverPrintsTheKey() =
        runTest {
            val result = useCase(UfvkFixtures.MAINNET_FULL, ZcashNetwork.Mainnet)

            assertFalse(result.toString().contains("uview"))
        }

    @Test
    fun theKeyPrefixFollowsTheNetwork() {
        assertEquals("uview1", ValidateUfvkUseCase.keyPrefixOf(ZcashNetwork.Mainnet))
        assertEquals("uviewtest1", ValidateUfvkUseCase.keyPrefixOf(ZcashNetwork.Testnet))
    }

    private fun assertInvalid(result: UfvkValidation) = assertEquals(UfvkValidation.Invalid, result)

    private companion object {
        val FULL_COMPONENTS =
            UnifiedFullViewingKeyComponents(
                hasP2pkh = true,
                hasP2sh = false,
                hasSapling = true,
                hasOrchard = true,
                hasUnknownItems = false,
            )

        /**
         * Text with the testnet key prefix. The SDK is faked here, so it only has to look like a
         * testnet key to the use case; the SDK's own decoding is covered on a device.
         */
        val TESTNET_KEY = "uviewtest1" + UfvkFixtures.MAINNET_FULL.removePrefix("uview1")
    }
}
