package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.MigrationSchedule
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Transient, in-memory handoff of the [MigrationSchedule] returned by
 * [cash.z.ecc.android.sdk.OrchardMigrationSdk.restartCurrentMigrationStep] — that method's own doc
 * requires its returned schedule to go through the normal user confirmation flow
 * (MigrationReviewVM → signAndStoreMigrationSchedule), not to be silently discarded in favor of an
 * independently re-proposed one (the two calls compute independent guesses over the same balance
 * and are not guaranteed to agree, same reasoning as proposeMigrationTransfersFromSplit's doc).
 *
 * `MigrationTransferInvalidVM.onContinue()` sets this right before navigating to the Confirm
 * Transfer Plan screen; `MigrationReviewVM` consumes (reads-and-clears) it once, at init, falling
 * back to a fresh `proposeMigrationTransfers()` call when nothing is pending — the ordinary,
 * non-recovery entry point.
 *
 * Deliberately a separate slot from [PendingMigrationScheduleRepository] (that one's Keystone
 * sign/scan hand-off, one step further down the same screen) — the two flows can run back-to-back
 * (restart → Review → Keystone sign) inside a single confirmation, and keeping them in separate
 * slots means an abandoned Keystone attempt's leftover state can never be mistaken for a pending
 * restart schedule on some later, unrelated Review entry.
 *
 * The schedule is stored together with the [accountKeyId] of the account that set it.
 * [consume] returns `null` and clears the stored value when the caller's key id does not match
 * the stored one, preventing cross-account contamination on an account switch mid-flow.
 */
interface RestartMigrationScheduleRepository {
    fun set(accountKeyId: String, schedule: MigrationSchedule)

    /** Reads and clears the pending schedule in one step — consumed at most once.
     *  Returns `null` (and clears) when the stored account key id differs from [accountKeyId]. */
    fun consume(accountKeyId: String): MigrationSchedule?
}

class RestartMigrationScheduleRepositoryImpl : RestartMigrationScheduleRepository {
    private val pending = MutableStateFlow<Pair<String, MigrationSchedule>?>(null)

    override fun set(accountKeyId: String, schedule: MigrationSchedule) {
        pending.value = accountKeyId to schedule
    }

    override fun consume(accountKeyId: String): MigrationSchedule? {
        val current = pending.value.also { pending.value = null } ?: return null
        return if (current.first == accountKeyId) current.second else null
    }
}
