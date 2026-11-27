package co.electriccoin.zcash.ui.screen.migration.review

import co.electriccoin.zcash.ui.common.model.migration.MigrationKeystoneRound
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationDetails
import co.electriccoin.zcash.ui.common.model.migration.MigrationTransferFailureState
import co.electriccoin.zcash.ui.design.util.StringResource

data class MigrationReviewPreparationState(
    val number: Int,
    val scheduledLabel: StringResource,
)

data class MigrationReviewState(
    val mode: MigrationMode,
    val totalAmount: StringResource,
    // Only populated for AUTOMATIC — feeds the "Split Balance" row shown above the transfer
    // timeline on Confirm Transfer Plan.
    val totalFiatAmount: StringResource? = null,
    val estimatedDuration: StringResource,
    // Individual note-split (preparation) rows; empty whenever there's more than one preparation
    // (that case renders a single collapsed "Split Balance" row instead — see
    // [preparationsSummarySubtitle]/[preparationDetails]), and for single-note wallets that need
    // no split at all.
    val preparations: List<MigrationReviewPreparationState> = emptyList(),
    // "in ~X hours · N steps" — the collapsed row's subtitle. Non-null only alongside
    // [preparationDetails] (more than one preparation). Figma "PR App Designs Q3'26" node
    // 5207:16023, 2026-08-03.
    val preparationsSummarySubtitle: StringResource? = null,
    val preparationDetails: MigrationPreparationDetails? = null,
    val transfers: List<MigrationReviewTransferState>,
    val isKeystone: Boolean = false,
    // See MigrationKeystoneRound's kdoc — only non-null for a genuine multi-round Keystone
    // campaign (estimated run count > 1); null otherwise, including single-round Keystone
    // migrations and all non-Keystone accounts.
    val keystoneRound: MigrationKeystoneRound? = null,
    // Only populated for MigrationMode.IMMEDIATE — the single-transfer flow shows a fee line on
    // its Details card. AUTOMATIC's PrivacyReviewContent doesn't use this field.
    val fee: StringResource? = null,
    val isConfirming: Boolean = false,
    val onConfirm: () -> Unit,
    val onBack: () -> Unit,
    // Set for AUTOMATIC + MANUAL delivery, where confirming sends transfer #1 synchronously in
    // the foreground and can fail right here on this screen, and for IMMEDIATE, where confirming
    // is now a single inline sign+submit action with no separate Sending-screen hand-off.
    val failureSheet: MigrationTransferFailureState? = null,
)

data class MigrationReviewTransferState(
    val index: Int,
    val totalCount: Int,
    val amount: StringResource,
    val fiatAmount: StringResource?,
    val scheduledLabel: StringResource,
)
