package co.electriccoin.zcash.ui.screen.migration.review

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.MigrationSchedule
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.TransferProposal
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.migration.MigrationPlan
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransfer
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferStatus
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.MigrationPlanRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledArgs
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingArgs
import co.electriccoin.zcash.work.MigrationScheduler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class MigrationReviewVM(
    private val args: MigrationReviewArgs,
    private val sdk: OrchardMigrationSdk,
    private val migrationPlanRepository: MigrationPlanRepository,
    private val navigationRouter: NavigationRouter,
    private val migrationScheduler: MigrationScheduler,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    private val proposeLce = mutableLce<MigrationSchedule>()
    private val confirmLce = mutableLce<Unit>()

    init {
        proposeLce.execute { sdk.proposeMigrationTransfers() }
    }

    val state: StateFlow<LceState<MigrationReviewState>> =
        combine(proposeLce.state, exchangeRateRepository.state) { lce, rate ->
            lce.success?.let { sched -> createState(sched, confirmLce.state.value.loading, rate) }
        }.withLce(proposeLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(
        sched: MigrationSchedule,
        isConfirming: Boolean,
        exchangeRateState: ExchangeRateState,
    ): MigrationReviewState {
        val total = sched.transfers.sumOf { it.amountZatoshi }
        return MigrationReviewState(
            mode = args.mode,
            totalAmount = stringRes(Zatoshi(total)),
            transfers = sched.transfers.mapIndexed { i, t ->
                MigrationReviewTransferState(
                    index = i + 1,
                    totalCount = sched.transfers.size,
                    amount = stringRes(Zatoshi(t.amountZatoshi)),
                    fiatAmount = fiatAmount(Zatoshi(t.amountZatoshi), exchangeRateState),
                    scheduledLabel = scheduledLabel(t, i, args.mode),
                )
            },
            isConfirming = isConfirming,
            onConfirm = { proposeLce.guardLoading { onConfirm(sched) } },
            onBack = ::onBack,
        )
    }

    private fun fiatAmount(zatoshi: Zatoshi, exchangeRateState: ExchangeRateState): StringResource? {
        val data = exchangeRateState as? ExchangeRateState.Data ?: return null
        val conversion = data.currencyConversion ?: return null
        return stringResByDynamicCurrencyNumber(
            amount =
                zatoshi
                    .convertZatoshiToZec()
                    .multiply(BigDecimal(conversion.priceOfZec), MathContext.DECIMAL128),
            ticker = data.expectedCurrency.symbol,
        )
    }

    private fun onConfirm(sched: MigrationSchedule) =
        confirmLce.execute {
            sdk.signAndStoreMigrationSchedule(sched)
            migrationPlanRepository.save(sched.toMigrationPlan(args.mode))
            migrationScheduler.schedule(0.seconds)
            when (args.mode) {
                MigrationMode.IMMEDIATE -> navigationRouter.forward(MigrationSendingArgs)
                MigrationMode.AUTOMATIC -> navigationRouter.forward(MigrationScheduledArgs)
            }
        }

    private fun onBack() = proposeLce.guardLoading { navigationRouter.back() }

    private fun scheduledLabel(t: TransferProposal, index: Int, mode: MigrationMode): StringResource {
        if (mode == MigrationMode.IMMEDIATE) return stringRes("Send immediately")
        if (index == 0) return stringRes("Ready now")
        val nowSeconds = Clock.System.now().epochSeconds
        val secondsUntil = t.nextExecutableAfterHeight - nowSeconds
        return when {
            secondsUntil <= 0 -> stringRes("Ready now")
            secondsUntil < 3600 -> stringRes("~${(secondsUntil / 60).coerceAtLeast(1)} min")
            else -> stringRes("~${secondsUntil / 3600} hours")
        }
    }

    private fun MigrationSchedule.toMigrationPlan(mode: MigrationMode) =
        MigrationPlan(
            id = UUID.randomUUID().toString(),
            createdAtEpochSeconds = Clock.System.now().epochSeconds,
            transfers = transfers.mapIndexed { i, t ->
                MigrationTransfer(
                    index = i,
                    amountZatoshi = t.amountZatoshi,
                    scheduledAtEpochSeconds = t.nextExecutableAfterHeight,
                    status = MigrationTransferStatus.PENDING,
                )
            },
            mode = mode,
        )
}
