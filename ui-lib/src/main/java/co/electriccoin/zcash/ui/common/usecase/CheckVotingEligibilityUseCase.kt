package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Minimum shielded ZEC required to vote (0.125 ZEC = 12_500_000 zatoshi). */
private const val MIN_VOTING_WEIGHT_ZATOSHI = 12_500_000L

enum class IneligibilityReason {
    /** Wallet has no shielded notes from before the snapshot block. */
    NO_NOTES,

    /** Wallet balance is below the 0.125 ZEC minimum. */
    BALANCE_TOO_LOW,
}

data class EligibilityResult(
    val isEligible: Boolean,
    val eligibleWeightZatoshi: Long,
    /** Non-null when isEligible == false. */
    val ineligibilityReason: IneligibilityReason? = null,
)

/**
 * Checks whether the user has sufficient shielded balance at the voting snapshot height.
 *
 * NOTE: Full eligibility check requires the Rust voting backend (ZKP delegation proof).
 * This is a simplified check based on current wallet balance as a placeholder until
 * VotingRustBackend is available for Android.
 *
 * Mirrors iOS ineligibility reasons: .noNotes (0 balance) and .balanceTooLow (< 0.125 ZEC).
 */
class CheckVotingEligibilityUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val accountDataSource: AccountDataSource
) {
    suspend operator fun invoke(snapshotHeight: Long): EligibilityResult =
        withContext(Dispatchers.IO) {
            val synchronizer = synchronizerProvider.getSynchronizer()
            val accounts = accountDataSource.getAllAccounts()
            val balances = synchronizer.walletBalances.value

            val totalShieldedZatoshi =
                accounts.sumOf { account ->
                    val balance = balances?.get(account.sdkAccount.accountUuid) ?: return@sumOf 0L
                    (balance.sapling.available.value + balance.orchard.available.value)
                }

            when {
                totalShieldedZatoshi == 0L -> {
                    EligibilityResult(
                        isEligible = false,
                        eligibleWeightZatoshi = 0L,
                        ineligibilityReason = IneligibilityReason.NO_NOTES,
                    )
                }

                totalShieldedZatoshi < MIN_VOTING_WEIGHT_ZATOSHI -> {
                    EligibilityResult(
                        isEligible = false,
                        eligibleWeightZatoshi = totalShieldedZatoshi,
                        ineligibilityReason = IneligibilityReason.BALANCE_TOO_LOW,
                    )
                }

                else -> {
                    EligibilityResult(
                        isEligible = true,
                        eligibleWeightZatoshi = totalShieldedZatoshi,
                    )
                }
            }
        }
}
