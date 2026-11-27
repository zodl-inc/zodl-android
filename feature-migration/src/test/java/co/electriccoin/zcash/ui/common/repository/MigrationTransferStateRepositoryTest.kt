package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.MigrationTransferStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MigrationTransferStateRepositoryTest {
    private fun readout(
        tipHeight: Long,
        estimatedTip: Long = tipHeight,
        estimatedSecondsPerBlock: Long = 75L,
        migrationState: MigrationState? = MigrationState.Complete,
        hasOverdueTransfers: Boolean = false,
    ) = MigrationLiveReadout(
        states = MigrationTransferStates(transfers = emptyList(), tipHeight = tipHeight),
        estimatedTip = estimatedTip,
        estimatedSecondsPerBlock = estimatedSecondsPerBlock,
        migrationState = migrationState,
        hasOverdueTransfers = hasOverdueTransfers,
    )

    @Test
    fun observe_before_any_publish_is_null() {
        val repo = MigrationTransferStateRepositoryImpl()
        assertNull(repo.observe("account-1").value)
    }

    @Test
    fun publish_is_immediately_visible_to_observe() {
        val repo = MigrationTransferStateRepositoryImpl()
        repo.publish("account-1", readout(42L))
        assertEquals(
            42L,
            repo
                .observe("account-1")
                .value
                ?.states
                ?.tipHeight
        )
    }

    @Test
    fun accounts_are_isolated_from_each_other() {
        val repo = MigrationTransferStateRepositoryImpl()
        repo.publish("account-1", readout(1L))
        repo.publish("account-2", readout(2L))
        assertEquals(
            1L,
            repo
                .observe("account-1")
                .value
                ?.states
                ?.tipHeight
        )
        assertEquals(
            2L,
            repo
                .observe("account-2")
                .value
                ?.states
                ?.tipHeight
        )
    }

    @Test
    fun a_readout_with_null_states_is_distinct_from_never_having_published() {
        // Publishing a readout whose states field is null (e.g. no migration in_progress anymore)
        // is a real, meaningful value — it must NOT read back the same as observe()'s cold-start
        // null (driver never published for this account at all). The VM tells these apart: the
        // former means "genuinely nothing to show", the latter means "fall back to a direct read".
        val repo = MigrationTransferStateRepositoryImpl()
        repo.publish("account-1", readout(1L))
        repo.publish(
            "account-1",
            MigrationLiveReadout(
                states = null,
                estimatedTip = -1L,
                estimatedSecondsPerBlock = 0L,
                migrationState = null,
                hasOverdueTransfers = false,
            )
        )
        val republished = repo.observe("account-1").value
        assertNull(republished?.states)
        assertEquals(-1L, republished?.estimatedTip) // the readout ITSELF is not null
    }

    @Test
    fun observe_called_twice_for_the_same_account_returns_the_same_flow_instance() {
        // Repeated observe() calls (e.g. one per screen recomposition) must not fragment into
        // independent flows that could each cache a different value.
        val repo = MigrationTransferStateRepositoryImpl()
        val first = repo.observe("account-1")
        val second = repo.observe("account-1")
        repo.publish("account-1", readout(7L))
        assertEquals(7L, first.value?.states?.tipHeight)
        assertEquals(7L, second.value?.states?.tipHeight)
    }
}
