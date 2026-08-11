package co.electriccoin.zcash.ui.common.model.migration

import co.electriccoin.zcash.ui.design.util.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-logic coverage for the "Prepare Your Balance" detail-sheet helpers shared by
 * MigrationReviewScreen and MigrationProgressScreen (Figma "PR App Designs Q3'26" node
 * 5207:16023, 2026-08-03).
 */
class MigrationPreparationDetailsTest {
    @Test
    fun preparationStepTitle_formats_transaction_n_of_m() {
        assertEquals("Transaction 1 of 4", preparationStepTitle(1, 4).asString())
        assertEquals("Transaction 4 of 4", preparationStepTitle(4, 4).asString())
    }

    @Test
    fun preparationStepTimeLabel_always_prefixes_in_even_when_already_due() {
        // Unlike the main timeline's last-row-only "in" rule, the sheet ALWAYS prefixes "in" —
        // including a step whose own schedule slot has already arrived (Figma shows "in ~0 hours"
        // for the immediately-due first step, never a bare "Ready to send" in this column).
        // fineGrained pinned explicitly (MOB-1669 test-flakiness follow-up, 2026-08-09) — this
        // testnet-floor expectation must not depend on which Gradle flavor variant compiled this
        // JVM test (isTestnetBuildFlavor()'s ambient BuildConfig.FLAVOR default resolves
        // differently under testZcashmainnetStoreDebugUnitTest vs testZcashtestnetStoreDebugUnitTest).
        assertEquals("in ~10 min", preparationStepTimeLabel(0L, fineGrained = true).asString())
        assertEquals("in ~10 min", preparationStepTimeLabel(-100L, fineGrained = true).asString())
    }

    @Test
    fun preparationStepStatus_sent_always_reads_done_regardless_of_other_facts() {
        val status =
            preparationStepStatus(
                isSent = true,
                isAwaitingSignature = true,
                dependsOnNumbers = listOf(1, 2),
                isDueNow = false,
            )
        assertEquals("Done", status.asString())
    }

    @Test
    fun preparationStepStatus_awaiting_signature_wins_over_timing_when_not_sent() {
        val status =
            preparationStepStatus(
                isSent = false,
                isAwaitingSignature = true,
                dependsOnNumbers = emptyList(),
                isDueNow = true,
            )
        assertEquals("Awaiting signature", status.asString())
    }

    @Test
    fun preparationStepStatus_dependency_wins_over_due_now() {
        // Figma's step 3: due before step 4, but still blocked on 1 & 2 — dependency always wins.
        val status =
            preparationStepStatus(
                isSent = false,
                isAwaitingSignature = false,
                dependsOnNumbers = listOf(1, 2),
                isDueNow = true,
            )
        assertEquals("Waits on steps 1 & 2", status.asString())
    }

    @Test
    fun preparationStepStatus_single_dependency_uses_singular_step_copy() {
        val status =
            preparationStepStatus(
                isSent = false,
                isAwaitingSignature = false,
                dependsOnNumbers = listOf(3),
                isDueNow = false,
            )
        assertEquals("Waits on step 3", status.asString())
    }

    @Test
    fun preparationStepStatus_three_or_more_dependencies_joins_with_commas_and_ampersand() {
        val status =
            preparationStepStatus(
                isSent = false,
                isAwaitingSignature = false,
                dependsOnNumbers = listOf(1, 2, 3),
                isDueNow = false,
            )
        assertEquals("Waits on steps 1, 2 & 3", status.asString())
    }

    @Test
    fun preparationStepStatus_no_dependency_due_now_reads_ready_to_send() {
        val status =
            preparationStepStatus(
                isSent = false,
                isAwaitingSignature = false,
                dependsOnNumbers = emptyList(),
                isDueNow = true,
            )
        assertEquals("Ready to send", status.asString())
    }

    @Test
    fun preparationStepStatus_no_dependency_not_due_reads_preparing() {
        val status =
            preparationStepStatus(
                isSent = false,
                isAwaitingSignature = false,
                dependsOnNumbers = emptyList(),
                isDueNow = false,
            )
        assertEquals("Preparing", status.asString())
    }

    @Suppress("UNCHECKED_CAST")
    private fun StringResource.asString(): String =
        when (this) {
            is StringResource.ByString -> {
                value
            }

            else -> {
                val resourcesField = this::class.java.getDeclaredField("resources").also { it.isAccessible = true }
                val parts = resourcesField.get(this) as List<StringResource>
                parts.joinToString(separator = "") { it.asString() }
            }
        }
}
