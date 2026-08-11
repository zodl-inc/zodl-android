package co.electriccoin.zcash.ui.common.model.migration

import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Detail-sheet content for a collapsed "Split Balance" summary row — Figma "Prepare Your Balance"
 * bottom sheet ("PR App Designs Q3'26", node 5207:16023, 2026-08-03). Shared by
 * `MigrationReviewScreen` (pre-confirm plan) and `MigrationProgressScreen` (live migration) so the
 * sheet's copy and layout stay identical in both places — the two screens differ only in how they
 * compute [steps]/[totalAmount] from their own, very different underlying models (a proposed
 * [PreparationStep] list that nothing has happened to yet, vs. a live, partially-broadcast
 * [LiveMigrationPreparation] snapshot).
 */
data class MigrationPreparationDetails(
    val stepCount: Int,
    val totalAmount: StringResource,
    val steps: List<MigrationPreparationStepDetail>,
    val onDismiss: () -> Unit,
)

/** One row inside the "Preparation Steps" card of [MigrationPreparationDetails]. */
data class MigrationPreparationStepDetail(
    val title: StringResource,
    val timeLabel: StringResource,
    val statusLabel: StringResource,
    // Swaps the row's numbered avatar for a checkmark — Figma's pre-confirm reference never shows
    // this (nothing has been sent yet there), but the live Progress screen's sheet needs it once a
    // step actually broadcasts.
    val isDone: Boolean = false,
)

// NOTE: these helpers deliberately stay ByString literals (not Android string resources) — they're
// unit-tested in plain JVM tests (MigrationPreparationDetailsTest, MigrationProgressVMTest,
// MigrationReviewPlanShapeTest) via a StringResource.ByString-only reflection helper with no
// Android Context available to resolve a real resource. Converting them would either throw in
// those tests or require reworking that shared test harness — tracked as a known gap: these
// specific dynamic status words are not localized. See migrationSetup_title etc. for the
// resource-backed pattern used everywhere else in this module.

/** "Transaction N of M" — shared so both screens render identical copy. */
fun preparationStepTitle(number: Int, stepCount: Int): StringResource = stringRes("Transaction $number of $stepCount")

/**
 * The sheet's per-step time column always reads "in ~X" — unlike the collapsed main-timeline row
 * (which only prefixes "in" on the last row overall), Figma shows "in ~0 hours"/"in ~1 hours"/...
 * on EVERY row inside this sheet, including the one that's already due. [formatMigrationDuration]'s
 * own `coerceAtLeast` floor already turns a zero/negative [secondsUntil] into its network floor, so
 * no separate clamping is needed here.
 *
 * [fineGrained] forwards to [formatMigrationDuration] — exposed here (default unchanged: ambient
 * [isTestnetBuildFlavor]) purely so a caller (a test) can pin it explicitly instead of depending on
 * which Gradle flavor variant happened to compile the caller (MOB-1669 test-flakiness follow-up,
 * 2026-08-09: this default silently resolved differently under `testZcashmainnetStoreDebugUnitTest`
 * vs `testZcashtestnetStoreDebugUnitTest`, since both compile and run this same JVM test).
 */
fun preparationStepTimeLabel(
    secondsUntil: Long,
    fineGrained: Boolean = isTestnetBuildFlavor(),
): StringResource = stringRes("in ${formatMigrationDuration(secondsUntil, fineGrained = fineGrained)}")

/**
 * The Figma-matched status word for one preparation step, derived purely from its own
 * dependency/blocker/timing facts (no wall-clock "overdue", matching the rest of this session's
 * migration UI work). [dependsOnNumbers] is the 1-based position of each id in the step's own
 * `dependsOn` list, already resolved by the caller against the full preparation list — this
 * function only deals in positions, so it's agnostic to which concrete step model (proposed vs.
 * live) produced them.
 *
 * Priority order: a genuinely-sent step reads "Done"; a live step blocked on its own signature
 * reads "Awaiting signature" regardless of timing; an unresolved dependency always wins over
 * timing ("Waits on step(s) ..." even for a step whose own schedule slot has already arrived,
 * matching Figma's step 3, which shows "Waits on steps 1 & 2" despite being due before step 4);
 * otherwise it's "Ready to send" (due now) or "Preparing" (not yet due).
 */
fun preparationStepStatus(
    isSent: Boolean,
    isAwaitingSignature: Boolean,
    dependsOnNumbers: List<Int>,
    isDueNow: Boolean,
): StringResource =
    when {
        isSent -> {
            stringRes("Done")
        }

        isAwaitingSignature -> {
            stringRes("Awaiting signature")
        }

        dependsOnNumbers.isNotEmpty() -> {
            stringRes(waitsOnStepsCopy(dependsOnNumbers))
        }

        isDueNow -> {
            stringRes("Ready to send")
        }

        else -> {
            stringRes("Preparing")
        }
    }

private fun waitsOnStepsCopy(numbers: List<Int>): String =
    when (numbers.size) {
        1 -> "Waits on step ${numbers.single()}"
        else -> "Waits on steps ${numbers.dropLast(1).joinToString(", ")} & ${numbers.last()}"
    }
