package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Characterization pin for the known-lossy ZIP321 prefill amount path.
 *
 * The parser stores `100000001` zatoshis exactly, but `requestFromZip321` renders the
 * amount through `NonNegativeAmount.toZecValueString()` (org.zecdev:zip321 1.0.2), whose
 * eight-significant-digit MathContext drops the ninth digit. See the P1 amount-rendering
 * row in docs/testing/unit-test-roadmap.md. This pin fails deliberately when the
 * dependency or conversion path is fixed; flip the expected value with that fix.
 */
class PrefillSendZip321AmountTest {
    @Test
    fun zip321PrefillPinsTheLossyAmountRendering() =
        runTest {
            val address = WalletAddressFixture.UNIFIED_ADDRESS_STRING
            val synchronizer = mockk<Synchronizer>()
            val provider = mockk<SynchronizerProvider>()
            coEvery { provider.getSynchronizer() } returns synchronizer
            coEvery { synchronizer.validateAddress(any()) } returns AddressType.Unified
            val parsed =
                assertIs<Zip321ParseUriValidation.Valid>(
                    Zip321ParseUriValidationUseCase(provider)("zcash:$address?amount=1.00000001")
                )
            assertEquals(
                "100000001",
                parsed.payment.payments
                    .single()
                    .nonNegativeAmount
                    ?.toString()
            )

            val prefill = PrefillSendUseCase()
            prefill.requestFromZip321(parsed.payment)
            val data = assertIs<PrefillSendData.All>(prefill().first())
            assertEquals(address, data.address)
            // 100000001 zatoshis in, 100000000 out: one zatoshi is lost today.
            assertEquals(Zatoshi(100_000_000), data.amount)
        }
}
