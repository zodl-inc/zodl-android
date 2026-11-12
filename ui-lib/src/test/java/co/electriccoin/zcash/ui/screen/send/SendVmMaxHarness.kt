package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmount
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountDataSource
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure
import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountUnavailableException
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.SignerCapabilities
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.CreateProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMaxSendAmountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectRecipientUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveABContactPickedUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveContactByAddressUseCase
import co.electriccoin.zcash.ui.screen.send.SendVmMaxHarness.Companion.UNIFIED
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

/**
 * A [SendViewModel] for the Max tests, ported from the iOS `SendFormMaxButtonTests`. The real
 * [GetMaxSendAmountUseCase] runs on top of a mocked [MaxSendAmountDataSource], so the memo rule is proven end to end.
 * The tests run under Robolectric because the use case logs via `Twig`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SendVmMaxHarness {
    val mainDispatcher = UnconfinedTestDispatcher()
    val accounts = MutableStateFlow<WalletAccount?>(account(spendable = 1_000_000, pending = 0))
    val maxSendAmountDataSource = mockk<MaxSendAmountDataSource>()
    val createProposal = mockk<CreateProposalUseCase>(relaxed = true)
    private val recipient = mockk<RecipientAddress>()

    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkObject(RecipientAddress.Companion)
        coEvery { RecipientAddress.new(any(), any()) } returns recipient
        coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } returns MAX
    }

    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    fun newViewModel(): SendViewModel {
        val accountDataSource =
            mockk<AccountDataSource> {
                every { selectedAccount } returns accounts
                coEvery { getSelectedAccount() } coAnswers { accounts.value!! }
            }
        val synchronizerProvider =
            mockk<SynchronizerProvider> {
                coEvery { getSynchronizer() } returns mockk { every { network } returns ZcashNetwork.Mainnet }
            }
        return SendViewModel(
            exchangeRateRepository = mockk<ExchangeRateRepository>(relaxed = true),
            observeContactByAddress = mockk<ObserveContactByAddressUseCase>(relaxed = true),
            observeContactPicked = mockk<ObserveABContactPickedUseCase> { every { this@mockk() } returns emptyFlow() },
            createProposal = createProposal,
            observeWalletAccounts = mockk<GetWalletAccountsUseCase>(relaxed = true),
            getSelectedWalletAccount = GetSelectedWalletAccountUseCase(accountDataSource),
            getMaxSendAmount =
                GetMaxSendAmountUseCase(
                    accountDataSource,
                    maxSendAmountDataSource,
                    synchronizerProvider,
                    marginalFeeProvider = { Zatoshi(5_000) },
                    signerCapabilitiesProvider =
                        mockk<SignerCapabilitiesProvider> {
                            coEvery { forAccount(any()) } returns SignerCapabilities(TransactionSizeBound.CONSENSUS)
                        }
                ),
            navigateToSelectRecipient = mockk<NavigateToSelectRecipientUseCase>(relaxed = true),
            navigationRouter = mockk<NavigationRouter>(relaxed = true),
            signerCapabilitiesProvider = mockk(relaxed = true),
        )
    }

    /** The max proposal waits for [gate]; [nonCancellable] makes it outlive its cancellation, like a native call. */
    fun gatedProposal(gate: CompletableDeferred<Unit>, nonCancellable: Boolean = false) {
        coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } coAnswers {
            if (nonCancellable) withContext(NonCancellable) { gate.await() } else gate.await()
            MAX
        }
    }

    fun failWith(failure: MaxSendAmountFailure) =
        failWithUnexpected(MaxSendAmountUnavailableException(failure, RuntimeException()))

    /** The max proposal throws [exception] as is. */
    fun failWithUnexpected(exception: Throwable) {
        coEvery { maxSendAmountDataSource.proposeMaxSendAmount(any(), any(), any(), any()) } throws exception
    }

    companion object {
        const val UNIFIED = "u1recipient"
        const val OTHER = "u1other"
        const val OWN_UNIFIED = "u1own"
        val MAX = MaxSendAmount(amount = Zatoshi(123_456_789), fee = Zatoshi(10_000))

        fun account(
            spendable: Long?,
            pending: Long?,
            isShieldingAvailable: Boolean? = false,
            isShieldedPending: Boolean? = (pending ?: 0) > 0,
        ) = mockk<WalletAccount> {
            every { spendableShieldedBalance } returns spendable?.let { Zatoshi(it) }
            every { pendingShieldedBalance } returns pending?.let { Zatoshi(it) }
            every { this@mockk.isShieldingAvailable } returns isShieldingAvailable
            every { this@mockk.isShieldedPending } returns isShieldedPending
            every { transparentAddress } returns "t-own"
            every { unifiedAddress } returns OWN_UNIFIED
        }
    }
}

internal fun SendViewModel.recipient(address: String = UNIFIED, type: AddressType? = AddressType.Unified) =
    onRecipientAddressChanged(RecipientAddressState.new(address, type))
