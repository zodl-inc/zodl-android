package co.electriccoin.zcash.ui.common.usecase

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.imageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The icon an account shows: its own drawable, or for a view-only account the module's eye on a
 * circle in the colour stored in the account's metadata, the default avatar circle without one. The
 * Zodl account keeps its own icon until it has a colour, then shows its mark on a circle in it.
 * Hardware wallets always show their own drawable.
 */
class ObserveAccountIconUseCase(
    private val metadataRepository: MetadataRepository,
) {
    /** Every account's stored colour by UUID; empty at first, so a consumer never waits. Read with [iconOf]. */
    fun observeAll(): Flow<Map<AccountUuid, Color>> =
        metadataRepository.metadata
            .map { all ->
                all
                    .mapNotNull { (uuid, metadata) ->
                        metadata.accountMetadata.appearance
                            ?.color
                            ?.let { uuid to it }
                    }.toMap()
            }.distinctUntilChanged()

    /** [account]'s icon in its colour from [colors], from [observeAll]. */
    fun iconOf(
        colors: Map<AccountUuid, Color>,
        account: WalletAccount
    ): ImageResource = iconOf(account, colors[account.sdkAccount.accountUuid])

    /** [account]'s icon in [color], or as it shows without a colour when [color] is null. */
    fun iconOf(
        account: WalletAccount,
        color: Color?
    ): ImageResource =
        when {
            account is ViewOnlyAccount -> {
                ImageResource.Layered(
                    background = color ?: Color.Unspecified,
                    foreground = ImageResource.ByDrawable(account.icon),
                )
            }

            account is ZodlAccount && color != null -> {
                ImageResource.Layered(
                    background = color,
                    foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph),
                )
            }

            else -> {
                imageRes(account.icon)
            }
        }
}
