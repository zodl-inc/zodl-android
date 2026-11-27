package co.electriccoin.zcash.ui.common.repository

import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MigrationHandoffAccountGuardTest {
    @Test
    fun pendingScheduleReturnsNullForDifferentAccount() {
        val repo = PendingMigrationScheduleRepositoryImpl()
        val schedule = mockk<cash.z.ecc.android.sdk.MigrationSchedule>()
        repo.set("accountA", schedule)
        assertNull(repo.get("accountB"))
    }

    @Test
    fun pendingScheduleReturnsValueForSameAccount() {
        val repo = PendingMigrationScheduleRepositoryImpl()
        val schedule = mockk<cash.z.ecc.android.sdk.MigrationSchedule>()
        repo.set("accountA", schedule)
        assertSame(schedule, repo.get("accountA"))
    }

    @Test
    fun restartScheduleConsumeReturnsNullForDifferentAccount() {
        val repo = RestartMigrationScheduleRepositoryImpl()
        val schedule = mockk<cash.z.ecc.android.sdk.MigrationSchedule>()
        repo.set("accountA", schedule)
        assertNull(repo.consume("accountB"))
        // mismatched consume clears — value is also gone for the right account
        assertNull(repo.consume("accountA"))
    }

    @Test
    fun keystonePcztsReturnsNullForDifferentAccount() {
        val repo = PendingKeystoneMigrationPcztsRepositoryImpl()
        val pczts =
            PendingKeystoneMigrationPczts(
                requestId = byteArrayOf(1),
                splitUnsignedPczt = null,
                transferUnsignedPczts = emptyList(),
            )
        repo.set("accountA", pczts)
        assertNull(repo.get("accountB"))
        assertSame(pczts, repo.get("accountA"))
    }

    // ---- peek() tests ----

    @Test
    fun pendingSchedulePeekMatchReturnsValueWithoutClearing() {
        val repo = PendingMigrationScheduleRepositoryImpl()
        val schedule = mockk<cash.z.ecc.android.sdk.MigrationSchedule>()
        repo.set("accountA", schedule)
        // peek must return the value without clearing it
        assertSame(schedule, repo.peek("accountA"))
        // a second peek on the same account still returns the value (not consumed)
        assertSame(schedule, repo.peek("accountA"))
        // get() also still finds it — confirming peek never mutated pending
        assertSame(schedule, repo.get("accountA"))
    }

    @Test
    fun pendingSchedulePeekMismatchReturnsNullWithoutClearing() {
        val repo = PendingMigrationScheduleRepositoryImpl()
        val schedule = mockk<cash.z.ecc.android.sdk.MigrationSchedule>()
        repo.set("accountA", schedule)
        // peek for a different account must return null …
        assertNull(repo.peek("accountB"))
        // … but must NOT clear the stored value — the right account can still retrieve it
        assertSame(schedule, repo.get("accountA"))
    }

    // ---- TorFailure partial-guard documentation test ----

    @Test
    fun torFailureDecisionIsNotAccountFilteredYet_knownPartialGuard() {
        val repo = PendingMigrationTorFailureDecisionRepositoryImpl()
        repo.set("accountA", false)
        assertEquals(false, repo.decision.value)
        // Known limitation (see repo KDoc): `decision` is NOT account-filtered — a different
        // account's set overwrites the observable value. Documented here so the gap is tracked.
        repo.set("accountB", true)
        assertEquals(true, repo.decision.value)
    }
}
