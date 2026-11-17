package co.electriccoin.zcash.ui.screen.toomanynotes

import co.electriccoin.zcash.ui.common.usecase.NavigateToTooManyNotesUseCase
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The too-many-notes sheet as a destination, for flows with no form of their own to host it, such as the swap and pay
 * quote. It answers the caller that opened it through [NavigateToTooManyNotesUseCase]: OK dismisses it and tells the
 * caller to clear its amount and focus the field; back, scrim and drag only dismiss it.
 */
@Serializable
data class TooManyNotesArgs(
    val requestId: String = UUID.randomUUID().toString()
)
