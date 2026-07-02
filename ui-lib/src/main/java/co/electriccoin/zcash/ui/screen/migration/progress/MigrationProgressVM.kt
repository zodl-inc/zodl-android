package co.electriccoin.zcash.ui.screen.migration.progress

import android.content.Context
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.NetworkPrivacyOptions
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.BuildConfig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.LceState
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
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.migration.MigrationPlan
import co.electriccoin.zcash.ui.common.model.migration.formatMigrationDuration
import co.electriccoin.zcash.work.MigrationScheduler
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.MathContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MigrationProgressVM(
    private val sdk: OrchardMigrationSdk,
    private val migrationPlanRepository: MigrationPlanRepository,
    private val navigationRouter: NavigationRouter,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val errorStateMapper: ErrorMapperUseCase,
    private val context: Context,
) : ViewModel() {

    private val sendLce = mutableLce<Unit>()

    val state: StateFlow<LceState<MigrationProgressState>> =
        combine(migrationPlanRepository.observe(), exchangeRateRepository.state) { plan, rate ->
            plan?.let { createState(it, rate) }
        }.withLce(sendLce, errorStateMapper::mapToState)
            .stateIn(this)

    fun navigateBack() = navigationRouter.back()

    private fun createState(plan: MigrationPlan, exchangeRateState: ExchangeRateState): MigrationProgressState {
        val now = Clock.System.now()
        val next = plan.nextPending
        val hasOverdue = next != null && next.scheduledAt <= now
        val isResume = hasOverdue && plan.completedCount > 0
        val overdueH = if (next != null) overdueHours(next, now) else 0L

        val span = (plan.transfers.maxOfOrNull { it.scheduledAtEpochSeconds } ?: plan.createdAtEpochSeconds) -
            plan.createdAtEpochSeconds
        val subtitle = when {
            plan.isComplete -> "All ${plan.totalCount} transfers are complete."
            isResume -> "Transfer ${plan.completedCount + 1} of ${plan.totalCount} was scheduled ${overdueH}h ago but wasn't sent. Send now or reschedule."
            else -> "Your balance splits into ${plan.totalCount} transfers over " +
                "${formatMigrationDuration(span)}. There are " +
                "${plan.totalCount - plan.completedCount} remaining transfers."
        }

        return MigrationProgressState(
            title = stringRes(if (isResume) "Resume Migration" else "Migration Progress"),
            subtitle = stringRes(subtitle),
            transfers = plan.transfers.map { t ->
                MigrationProgressTransferState(
                    index = t.index + 1,
                    amount = stringRes(Zatoshi(t.amountZatoshi)),
                    fiatAmount = fiatAmount(Zatoshi(t.amountZatoshi), exchangeRateState),
                    statusLabel = transferLabel(t, now),
                    isOverdue = t.status == MigrationTransferStatus.PENDING && t.scheduledAt <= now,
                    isSent = t.status == MigrationTransferStatus.SENT,
                )
            },
            isComplete = plan.isComplete,
            hasOverdue = hasOverdue,
            onBack = ::onBack,
            onSendNow = if (!plan.isComplete && next != null) ::onSendNow else null,
            onReschedule = if (hasOverdue) ::onReschedule else null,
            onSimulateTransfer = if (BuildConfig.DEBUG && !plan.isComplete) ::onSimulateTransfer else null,
            onDone = if (plan.isComplete) ::onDone else null,
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

    private fun onBack() = sendLce.guardLoading { navigationRouter.back() }

    private fun onSendNow() = sendLce.execute {
        sdk.executeNextPendingTransfer(NetworkPrivacyOptions(useTor = false))
    }

    private fun onReschedule() = sendLce.guardLoading {
        MigrationScheduler(context).schedule(30.seconds)
    }

    private fun onDone() = navigationRouter.backToRoot()

    private fun onSimulateTransfer() = sendLce.execute {
        sdk.executeNextPendingTransfer(NetworkPrivacyOptions(useTor = false))
        val updated = migrationPlanRepository.load() ?: return@execute
        if (updated.nextPending != null) MigrationScheduler(context).schedule(5.seconds)
    }

    private fun transferLabel(t: MigrationTransfer, now: Instant): StringResource =
        when (t.status) {
            MigrationTransferStatus.SENT -> {
                val agoMinutes = (now - t.scheduledAt).inWholeMinutes
                when {
                    agoMinutes < 1 -> stringRes("Sent recently")
                    agoMinutes < 60 -> stringRes("Sent $agoMinutes min ago")
                    else -> stringRes("Sent ${agoMinutes / 60}h ago")
                }
            }
            MigrationTransferStatus.PENDING -> {
                val scheduled = t.scheduledAt
                when {
                    scheduled <= now -> stringRes("Overdue · ${overdueHours(t, now)}h ago")
                    else -> {
                        val minutesLeft = (scheduled - now).inWholeMinutes
                        when {
                            minutesLeft <= 0 -> stringRes("Ready now")
                            minutesLeft < 60 -> stringRes("~$minutesLeft min")
                            else -> stringRes("~${minutesLeft / 60} hours")
                        }
                    }
                }
            }
            MigrationTransferStatus.FAILED -> stringRes("Failed")
        }

    private fun overdueHours(t: MigrationTransfer, now: Instant) =
        (now - t.scheduledAt).inWholeHours.coerceAtLeast(0)
}
