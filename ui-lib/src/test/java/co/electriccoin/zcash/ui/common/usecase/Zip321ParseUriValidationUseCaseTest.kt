package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.screen.scan.ScanArgs
import co.electriccoin.zcash.ui.screen.scan.ScanFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Exercises the shipped ZIP321 parser, not a parser mock. Vectors follow ZIP 321's
 * amount, memo, indexed payment and required-parameter rules: https://zips.z.cash/zip-0321.
 * Only SDK/JNI address validation is mocked, so no wallet, network or native library is needed.
 */
class Zip321ParseUriValidationUseCaseTest {
    private val address = WalletAddressFixture.UNIFIED_ADDRESS_STRING
    private val synchronizer = mockk<Synchronizer>()
    private val provider = mockk<SynchronizerProvider>()

    private fun parser(valid: Boolean = true): Zip321ParseUriValidationUseCase {
        coEvery { provider.getSynchronizer() } returns synchronizer
        coEvery { synchronizer.validateAddress(any()) } returns
            if (valid) AddressType.Unified else AddressType.Invalid("fixture rejected by SDK")
        return Zip321ParseUriValidationUseCase(provider)
    }

    @Test
    fun addressOnlyReturnsSingleAddressAndCallsSdkValidation() =
        runTest {
            assertEquals(Zip321ParseUriValidation.SingleAddress(address), parser()("zcash:$address"))
            coVerify(atLeast = 1) { synchronizer.validateAddress(address) }
        }

    @Test
    fun validRequestPreservesUriAmountMemoLabelAndMessage() =
        runTest {
            val memo = "Coffee 🎉"
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(memo.toByteArray())
            val uri = "zcash:$address?amount=1.00000001&memo=$encoded&label=Coffee%20shop&message=Thank%20you"
            val result = assertIs<Zip321ParseUriValidation.Valid>(parser()(uri))
            assertEquals(uri, result.zip321Uri)
            val payment = result.payment.payments.single()
            assertEquals(address, payment.recipientAddress.value)
            // toString exposes the stored zatoshi value. The dependency's display helper
            // toZecValueString uses an eight-significant-digit MathContext and loses precision.
            assertEquals("100000001", payment.nonNegativeAmount?.toString())
            assertEquals(memo, payment.memo?.data?.decodeToString())
            assertEquals("Coffee shop", payment.label)
            assertEquals("Thank you", payment.message)
        }

    @Test
    fun validAmountBoundariesAreNotRounded() =
        runTest {
            val parse = parser()
            for (amount in listOf("0", "0.00000001", "1.00000001", "21000000")) {
                val result = assertIs<Zip321ParseUriValidation.Valid>(parse("zcash:$address?amount=$amount"))
                assertEquals(
                    amount
                        .toBigDecimal()
                        .movePointRight(8)
                        .longValueExact()
                        .toString(),
                    result.payment.payments
                        .single()
                        .nonNegativeAmount
                        ?.toString()
                )
            }
        }

    @Test
    fun malformedAndOutOfRangeAmountsAreRejected() =
        runTest {
            val parse = parser()
            for (amount in listOf("-1", "21000001", "0.123456789", "50,000.00", "abc")) {
                assertEquals(Zip321ParseUriValidation.Invalid, parse("zcash:$address?amount=$amount"), amount)
            }
        }

    @Test
    fun duplicateParametersAndUnknownRequiredParametersAreRejected() =
        runTest {
            val parse = parser()
            for (query in listOf("amount=1&amount=2", "label=A&label=B", "req-unsupported=value")) {
                assertEquals(Zip321ParseUriValidation.Invalid, parse("zcash:$address?$query"), query)
            }
        }

    @Test
    fun malformedUrisAndMissingIndexedRecipientsAreRejected() =
        runTest {
            val parse = parser()
            for (uri in listOf("", "https://example.com", "zcash:", "zcash:?amount=1", "zcash:$address?amount.1=1")) {
                assertEquals(Zip321ParseUriValidation.Invalid, parse(uri), uri)
            }
        }

    @Test
    fun wrongNetworkUnifiedAddressIsRejectedByTheSdkValidator() =
        runTest {
            val testnetAddress = "utest1" + "q".repeat(160)
            assertEquals(Zip321ParseUriValidation.Invalid, parser(valid = false)("zcash:$testnetAddress?amount=1"))
            coVerify(atLeast = 1) { synchronizer.validateAddress(testnetAddress) }
        }

    @Test
    fun wrongNetworkTransparentAddressIsRejectedBeforeSdkValidation() =
        runTest {
            val testnetAddress = "tm" + "q".repeat(34)
            assertEquals(Zip321ParseUriValidation.Invalid, parser()("zcash:$testnetAddress?amount=1"))
            coVerify(exactly = 0) { synchronizer.validateAddress(testnetAddress) }
        }

    @Test
    fun sdkRejectedAddressInvalidatesTheWholeRequest() =
        runTest {
            assertEquals(Zip321ParseUriValidation.Invalid, parser(valid = false)("zcash:$address?amount=1"))
        }

    @Test
    fun sdkValidationExceptionIsReturnedAsInvalid() =
        runTest {
            val parse = parser()
            coEvery { synchronizer.validateAddress(any()) } throws IllegalArgumentException("invalid encoding")
            assertEquals(Zip321ParseUriValidation.Invalid, parse("zcash:$address?amount=1"))
        }

    @Test
    fun memoByteLimitAndTransparentRecipientRulesAreEnforced() =
        runTest {
            val parse = parser()

            fun encoded(size: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(size) { 65 })
            assertIs<Zip321ParseUriValidation.Valid>(parse("zcash:$address?memo=${encoded(512)}"))
            assertEquals(Zip321ParseUriValidation.Invalid, parse("zcash:$address?memo=${encoded(513)}"))
            val transparent = "t1gXqfSSQt6WfpwyuCU3Wi7sSVZ66DYQ3Po"
            assertEquals(Zip321ParseUriValidation.Invalid, parse("zcash:$transparent?memo=SGk"))
            assertEquals(Zip321ParseUriValidation.Invalid, parse("zcash:$address?memo=%%%"))
        }

    @Test
    fun multiplePaymentsKeepAllAmountsAndTheOriginalUri() =
        runTest {
            val uri = "zcash:$address?amount=1&address.2=$address&amount.2=2&label.2=Second"
            val result = assertIs<Zip321ParseUriValidation.Valid>(parser()(uri))
            assertEquals(uri, result.zip321Uri)
            assertEquals(2, result.payment.payments.size)
            assertEquals(listOf("1", "2"), result.payment.payments.map { it.nonNegativeAmount?.toZecValueString() })
            assertEquals("Second", result.payment.payments[1].label)
        }

    @Test
    fun scanningForwardsTheCompleteMultiPaymentUriToTheSelectedAccountPipeline() =
        runTest {
            val uri = "zcash:$address?amount=1&address.2=$address&amount.2=2"
            val parsed = assertIs<Zip321ParseUriValidation.Valid>(parser()(uri))
            val wallets = listOf<WalletAccount>(mockk<ZodlAccount>(), mockk<KeystoneAccount>(), mockk<LedgerAccount>())
            for (account in wallets) {
                for (flow in listOf(ScanFlow.SEND, ScanFlow.HOMEPAGE)) {
                    val accounts = mockk<AccountDataSource>()
                    val software = mockk<ZashiProposalRepository>(relaxed = true)
                    val keystone = mockk<KeystoneProposalRepository>(relaxed = true)
                    val ledger = mockk<LedgerProposalPipeline>(relaxed = true)
                    val proposal = mockk<Zip321TransactionProposal>()
                    every { proposal.amount } returns Zatoshi(300_000_000)
                    every { proposal.destination.address } returns address
                    every { proposal.memo } returns Memo("fixture")
                    coEvery { accounts.getSelectedAccount() } returns account
                    coEvery { software.createZip321Proposal(uri) } returns proposal
                    coEvery { keystone.createZip321Proposal(uri) } returns proposal
                    coEvery { ledger.createZip321Proposal(uri) } returns proposal
                    OnZip321ScannedUseCase(
                        keystone,
                        ledger,
                        software,
                        accounts,
                        mockk<NavigationRouter>(relaxed = true),
                        mockk<PrefillSendUseCase>(relaxed = true),
                        mockk(relaxed = true),
                    )(parsed, ScanArgs(flow))
                    coVerify(exactly = if (account is ZodlAccount) 1 else 0) { software.createZip321Proposal(uri) }
                    coVerify(exactly = if (account is KeystoneAccount) 1 else 0) { keystone.createZip321Proposal(uri) }
                    coVerify(exactly = if (account is LedgerAccount) 1 else 0) { ledger.createZip321Proposal(uri) }
                    coVerify(exactly = if (account is KeystoneAccount) 1 else 0) { keystone.createPCZTFromProposal() }
                    coVerify(exactly = if (account is LedgerAccount) 1 else 0) { ledger.createPCZTFromProposal() }
                }
            }
        }
}
