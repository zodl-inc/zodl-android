package co.electriccoin.zcash.ui.screen.migration.review

import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import kotlinx.serialization.Serializable

@Serializable
data class MigrationReviewArgs(val mode: MigrationMode)
