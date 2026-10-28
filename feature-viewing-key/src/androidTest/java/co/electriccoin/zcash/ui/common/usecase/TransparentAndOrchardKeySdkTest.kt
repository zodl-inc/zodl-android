package co.electriccoin.zcash.ui.common.usecase

import androidx.test.filters.SmallTest
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import co.electriccoin.zcash.ui.common.model.ufvk.UfvkFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The view-only import accepts only a key with an Orchard and a transparent component, and a key
 * exported from a hardware wallet carries no Sapling component at all. The JVM tests fake the SDK,
 * so this runs the real native [DerivationTool] on a device against such a key: the transparent and
 * Orchard items of librustzcash's public mainnet test vector, stored re-encoded without its Sapling
 * item. No one's wallet key is involved.
 */
class TransparentAndOrchardKeySdkTest {
    private val key = UfvkFixtures.MAINNET_TRANSPARENT_AND_ORCHARD

    @Test
    @SmallTest
    fun theSdkDescribesAKeyWithoutSapling() =
        runBlocking {
            val components =
                assertNotNull(
                    DerivationTool
                        .getInstance()
                        .describeUnifiedFullViewingKey(UnifiedFullViewingKey(key), ZcashNetwork.Mainnet)
                )

            assertTrue(components.hasP2pkh)
            assertTrue(components.hasOrchard)
            assertFalse(components.hasSapling)
        }

    @Test
    @SmallTest
    fun theSdkDerivesAUnifiedAddressFromAKeyWithoutSapling() =
        runBlocking {
            val address =
                DerivationTool.getInstance().deriveUnifiedAddress(viewingKey = key, network = ZcashNetwork.Mainnet)

            assertTrue(address.startsWith(MAINNET_ADDRESS_PREFIX))
        }

    @Test
    @SmallTest
    fun theValidationAcceptsAKeyWithoutSapling() =
        runBlocking<Unit> {
            val result = ValidateUfvkUseCase(SdkUfvkDescriber())(key, ZcashNetwork.Mainnet)

            assertIs<UfvkValidation.Valid>(result)
        }

    private companion object {
        const val MAINNET_ADDRESS_PREFIX = "u1"
    }
}
