package co.electriccoin.zcash.ui.common.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transient, in-memory handoff of the user's choice from the "Couldn't Connect to Tor" sheet
 * (`MigrationTorFailureScreen`) back to whichever migration send call site pushed that route —
 * the sheet is a standalone nav destination (not an inline conditional composable like
 * [co.electriccoin.zcash.ui.screen.migration.component.MigrationFailureBottomSheet]), so its
 * result can't just be a lambda captured in nav args. The call site collects [decision] and reacts
 * whenever a non-null value arrives (`true` = retry with Tor, `false` = retry without Tor), then
 * calls [clear]. Not persisted: this is a single-retry signal, not state worth surviving process
 * death.
 *
 * The decision is stored together with the [accountKeyId] of the account that set it. [decision]
 * emits the payload for any account (no account filter at the flow level) — this is a deliberate
 * trade-off: adding a full account-keyed reactive guard here would require MigrationSendingVM to
 * know its account key id, which is a larger refactor. The partial protection is:
 * - [set] stores the account key id alongside the boolean so the origin is always traceable.
 * - On construction, each MigrationSendingVM instance calls [clear] indirectly (via the
 *   existing `if decision.value == null → send()` logic), which discards any stale decision from a
 *   previous account session.
 *
 * Flagged as a known gap: a future refactor can add `decisionFor(accountKeyId): Flow<Boolean?>`
 * to make the guard fully account-aware at the read side.
 */
interface PendingMigrationTorFailureDecisionRepository {
    val decision: StateFlow<Boolean?>

    fun set(accountKeyId: String, useTor: Boolean)

    fun clear()
}

class PendingMigrationTorFailureDecisionRepositoryImpl : PendingMigrationTorFailureDecisionRepository {
    // Internal pair-tagged storage — accountKeyId is preserved for future guard extensions.
    private val pendingPair = MutableStateFlow<Pair<String, Boolean>?>(null)

    // Public decision flow maps the pair to just the Boolean for backward compatibility with
    // MigrationSendingVM's existing reactive collector, which does not carry an accountKeyId.
    private val _decision = MutableStateFlow<Boolean?>(null)
    override val decision: StateFlow<Boolean?> = _decision

    override fun set(accountKeyId: String, useTor: Boolean) {
        pendingPair.value = accountKeyId to useTor
        _decision.value = useTor
    }

    override fun clear() {
        pendingPair.value = null
        _decision.value = null
    }
}
