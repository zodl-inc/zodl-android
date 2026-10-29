package co.electriccoin.zcash.ui.common.usecase

import androidx.annotation.DrawableRes
import cash.z.ecc.android.sdk.MigrationPausedUntilNetworkUpgradeException
import cash.z.ecc.android.sdk.MigrationWalletTipUnavailableException
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.LceError
import co.electriccoin.zcash.ui.common.model.errorCopy
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import com.flexa.core.Flexa.scope
import kotlinx.coroutines.launch

/**
 * A scheduled migration was asked for before the NU7 activation. The SDK refuses it, and the
 * generic failure copy would hide why, so it gets its own message.
 */
fun Throwable?.isMigrationPausedUntilNetworkUpgrade(): Boolean =
    generateSequence(this) { it.cause }.any { it is MigrationPausedUntilNetworkUpgradeException }

/**
 * A scheduled migration was asked for while the wallet has no chain tip yet, so whether NU7 has activated is not known.
 * Syncing fixes it, and the pause copy would be the wrong reason once the upgrade has happened.
 */
fun Throwable?.isMigrationWalletTipUnavailable(): Boolean =
    generateSequence(this) { it.cause }.any { it is MigrationWalletTipUnavailableException }

class ErrorMapperUseCase(
    private val sendEmail: SendEmailUseCase,
    private val viewOnlyContent: ViewOnlyContent,
) {
    /**
     * An [AccountOperationUnsupportedException] carries its own copy and overrides whatever title
     * and message the call site passed — the caller cannot know a Ledger or view-only account was
     * selected, and the generic failure copy would be misleading.
     *
     * The defaults draw retry above contact support; a screen whose design puts contact support
     * first passes [isSupportFirst] together with its own [primaryStyle], [secondaryStyle] and
     * [icon].
     */
    fun mapToState(
        error: LceContent.Error,
        title: StringResource? = null,
        message: StringResource? = null,
        primaryStyle: ButtonStyle? = null,
        secondaryStyle: ButtonStyle? = null,
        isSupportFirst: Boolean = false,
        @DrawableRes icon: Int? = null,
    ): LceError.BottomSheet {
        val unsupported = (error.cause as? AccountOperationUnsupportedException)?.kind?.errorCopy(viewOnlyContent)
        val isMigrationPaused = error.cause.isMigrationPausedUntilNetworkUpgrade()
        val isMigrationTipUnavailable = error.cause.isMigrationWalletTipUnavailable()
        return mapToBottomSheet(
            error = error,
            title =
                when {
                    unsupported != null -> {
                        unsupported.title
                    }

                    isMigrationPaused -> {
                        stringRes(co.electriccoin.zcash.ui.design.R.string.migrationUpgradePaused_title)
                    }

                    isMigrationTipUnavailable -> {
                        stringRes(co.electriccoin.zcash.ui.design.R.string.migrationTipUnavailable_title)
                    }

                    else -> {
                        title ?: stringRes(co.electriccoin.zcash.ui.design.R.string.coinVote_error_title)
                    }
                },
            message =
                when {
                    unsupported != null -> {
                        unsupported.message
                    }

                    isMigrationPaused -> {
                        stringRes(co.electriccoin.zcash.ui.design.R.string.migrationUpgradePaused_message)
                    }

                    isMigrationTipUnavailable -> {
                        stringRes(co.electriccoin.zcash.ui.design.R.string.migrationTipUnavailable_message)
                    }

                    else -> {
                        message
                            ?: stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_laterDesc)
                    }
                },
            primaryStyle = primaryStyle,
            secondaryStyle = secondaryStyle,
            isSupportFirst = isSupportFirst,
            icon = icon,
        )
    }

    private fun mapToBottomSheet(
        error: LceContent.Error,
        title: StringResource,
        message: StringResource,
        primaryStyle: ButtonStyle?,
        secondaryStyle: ButtonStyle?,
        isSupportFirst: Boolean,
        @DrawableRes icon: Int?,
    ) = LceError.BottomSheet(
        ZashiConfirmationState.error(
            title = title,
            message = message,
            primaryStyle = primaryStyle ?: ButtonStyle.TERTIARY,
            secondaryStyle = secondaryStyle ?: ButtonStyle.PRIMARY,
            isSecondaryFirst = isSupportFirst,
            icon = icon ?: R.drawable.ic_reset_zashi_warning,
            onPrimary = error.restart,
            onBack = error.dismiss,
            onSecondary = {
                error.dismiss()
                scope.launch {
                    sendEmail(error.cause as? Exception ?: Exception(error.cause.message, error.cause))
                }
            },
        )
    )
}
