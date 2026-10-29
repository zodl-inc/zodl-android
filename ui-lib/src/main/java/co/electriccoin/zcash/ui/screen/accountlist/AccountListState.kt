package co.electriccoin.zcash.ui.screen.accountlist

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.listitem.WalletListItemState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber

/**
 * The full-screen "Wallets & Hardware" account selector: the wallets grouped into Zodl, Keystone,
 * Ledger and view-only sections, the Keystone promo while no Keystone is connected, and the "Add
 * Account" call to action.
 *
 * @param sections Null while the accounts are loading.
 * @param keystonePromo Null while loading or once a Keystone account is connected.
 * @param addAccountButton Null while the accounts are loading.
 */
@Immutable
data class AccountListState(
    val title: StringResource,
    val sections: List<AccountListSectionState>?,
    val keystonePromo: KeystonePromoState?,
    val addAccountButton: ButtonState?,
    val onBack: () -> Unit,
) {
    companion object {
        val preview: AccountListState =
            preview(listOf(previewZodl(isSelected = true)))

        val previewKeystone: AccountListState =
            preview(listOf(previewZodl(isSelected = true), previewKeystone(isSelected = false)))

        val previewLedger: AccountListState =
            preview(listOf(previewZodl(isSelected = true), previewLedger(isSelected = false)))

        val previewKeystoneAndLedger: AccountListState =
            preview(
                listOf(
                    previewZodl(isSelected = true),
                    previewKeystone(isSelected = false),
                    previewLedger(isSelected = false),
                )
            )

        val previewViewOnly: AccountListState =
            preview(listOf(previewZodl(isSelected = true), previewViewOnly("Zodl Vault", isSelected = false)))

        val previewViewOnlyMultiple: AccountListState =
            preview(
                listOf(
                    previewZodl(isSelected = true),
                    previewViewOnly("Zodl Vault", isSelected = false),
                    previewViewOnly("ZodlKeys26", isSelected = false),
                    previewViewOnly("ZecAthMoon", isSelected = false),
                )
            )

        val previewKeystoneAndViewOnly: AccountListState =
            preview(
                listOf(
                    previewZodl(isSelected = true),
                    previewKeystone(isSelected = false),
                    previewViewOnly("Zodl Vault", isSelected = false),
                )
            )

        val previewAll: AccountListState =
            preview(
                listOf(
                    previewZodl(isSelected = true),
                    previewKeystone(isSelected = false),
                    previewLedger(isSelected = false),
                    previewViewOnly("Zodl Vault", isSelected = false),
                )
            )

        val previewHardwareSelected: AccountListState =
            preview(
                listOf(
                    previewZodl(isSelected = false, balance = null),
                    previewKeystone(isSelected = true),
                    previewViewOnly("Zodl Vault", isSelected = false),
                )
            )

        val previewLoading: AccountListState = preview(entries = null)

        /**
         * Groups [entries] into sections in the fixed order Zodl, Keystone, Ledger, view-only. A
         * section header turns plural and shows a count tag once it holds more than one wallet. A
         * row whose entry has [AccountListEntry.onOptionsClick] gets the "..." action, any other
         * row a chevron that does what tapping the row does.
         *
         * @param entries The wallets, null while they are loading.
         * @param viewOnlySection The header above one view-only account.
         * @param viewOnlySectionPlural The header above several view-only accounts.
         * @param viewOnlyLabel The label appended to a view-only account's balance.
         */
        fun create(
            entries: List<AccountListEntry>?,
            viewOnlySection: StringResource,
            viewOnlySectionPlural: StringResource,
            viewOnlyLabel: StringResource,
            addAccountText: StringResource,
            @DrawableRes addAccountIcon: Int,
            onKeystonePromoClick: () -> Unit,
            onAddAccountClick: () -> Unit,
            onBack: () -> Unit,
        ) = AccountListState(
            title = stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_drawer_title),
            sections =
                entries?.let { loaded ->
                    AccountListKind.entries.mapNotNull { kind ->
                        val ofKind = loaded.filter { it.kind == kind }
                        if (ofKind.isEmpty()) {
                            null
                        } else {
                            AccountListSectionState(
                                title =
                                    sectionTitle(
                                        kind = kind,
                                        isPlural = ofKind.size > 1,
                                        viewOnlySection = viewOnlySection,
                                        viewOnlySectionPlural = viewOnlySectionPlural
                                    ),
                                count = stringResByNumber(ofKind.size, minDecimals = 0).takeIf { ofKind.size > 1 },
                                items = ofKind.map { it.toItemState(viewOnlyLabel) },
                            )
                        }
                    }
                },
            keystonePromo =
                KeystonePromoState(
                    title = stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_promo_title),
                    message = stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_promo_message),
                    image = imageRes(co.electriccoin.zcash.ui.R.drawable.img_keystone_promo),
                    onClick = onKeystonePromoClick,
                ).takeIf { entries != null && entries.none { it.kind == AccountListKind.KEYSTONE } },
            addAccountButton =
                ButtonState(
                    text = addAccountText,
                    style = ButtonStyle.PRIMARY,
                    icon = addAccountIcon,
                    onClick = onAddAccountClick,
                ).takeIf { entries != null },
            onBack = onBack,
        )

        private fun sectionTitle(
            kind: AccountListKind,
            isPlural: Boolean,
            viewOnlySection: StringResource,
            viewOnlySectionPlural: StringResource,
        ): StringResource =
            when (kind) {
                AccountListKind.ZODL -> {
                    stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_section_zodl)
                }

                AccountListKind.KEYSTONE -> {
                    if (isPlural) {
                        stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_section_keystone_plural)
                    } else {
                        stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_section_keystone)
                    }
                }

                AccountListKind.LEDGER -> {
                    if (isPlural) {
                        stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_section_ledger_plural)
                    } else {
                        stringRes(co.electriccoin.zcash.ui.R.string.hwWallet_section_ledger)
                    }
                }

                AccountListKind.VIEW_ONLY -> {
                    if (isPlural) viewOnlySectionPlural else viewOnlySection
                }
            }

        private fun AccountListEntry.toItemState(viewOnlyLabel: StringResource): WalletListItemState {
            val formattedBalance = balance?.let { stringRes(it).asPrivacySensitive() }
            return WalletListItemState(
                icon = icon,
                title = name,
                subtitle =
                    if (kind == AccountListKind.VIEW_ONLY) {
                        formattedBalance?.let { it + SUBTITLE_SEPARATOR + viewOnlyLabel }
                    } else {
                        formattedBalance
                    },
                isSelected = isSelected,
                trailingAction =
                    if (onOptionsClick != null) {
                        IconButtonState(
                            icon = R.drawable.ic_wallet_list_more,
                            contentDescription =
                                stringRes(
                                    co.electriccoin.zcash.ui.R.string.hwWallet_walletOptions_contentDescription,
                                    name
                                ),
                            onClick = onOptionsClick,
                        )
                    } else {
                        IconButtonState(
                            icon = R.drawable.ic_wallet_list_chevron,
                            contentDescription = null,
                            onClick = onClick,
                        )
                    },
                onClick = onClick,
            )
        }

        private fun preview(entries: List<AccountListEntry>?) =
            create(
                entries = entries,
                viewOnlySection = stringRes("View Only Account"),
                viewOnlySectionPlural = stringRes("View Only Accounts"),
                viewOnlyLabel = stringRes("View Only"),
                addAccountText = stringRes("Add Account"),
                addAccountIcon = co.electriccoin.zcash.ui.R.drawable.ic_address_book_plus,
                onKeystonePromoClick = {},
                onAddAccountClick = {},
                onBack = {},
            )

        private fun previewZodl(
            isSelected: Boolean,
            balance: Zatoshi? = Zatoshi(PREVIEW_ZODL_BALANCE)
        ) = AccountListEntry(
            kind = AccountListKind.ZODL,
            icon = imageRes(R.drawable.ic_item_zashi),
            name = stringRes("Zodl"),
            balance = balance,
            isSelected = isSelected,
            onClick = {},
            onOptionsClick = null,
        )

        private fun previewKeystone(isSelected: Boolean) =
            AccountListEntry(
                kind = AccountListKind.KEYSTONE,
                icon = imageRes(R.drawable.ic_item_keystone),
                name = stringRes("Keystone"),
                balance = Zatoshi(PREVIEW_KEYSTONE_BALANCE),
                isSelected = isSelected,
                onClick = {},
                onOptionsClick = {},
            )

        private fun previewLedger(isSelected: Boolean) =
            AccountListEntry(
                kind = AccountListKind.LEDGER,
                icon = imageRes(R.drawable.ic_item_ledger),
                name = stringRes("Ledger"),
                balance = Zatoshi(PREVIEW_LEDGER_BALANCE),
                isSelected = isSelected,
                onClick = {},
                onOptionsClick = {},
            )

        private fun previewViewOnly(
            name: String,
            isSelected: Boolean
        ) = AccountListEntry(
            kind = AccountListKind.VIEW_ONLY,
            icon = imageRes(R.drawable.ic_item_zashi),
            name = stringRes(name),
            balance = Zatoshi(PREVIEW_VIEW_ONLY_BALANCE),
            isSelected = isSelected,
            onClick = {},
            onOptionsClick = {},
        )

        private const val SUBTITLE_SEPARATOR = " · "
        private const val PREVIEW_ZODL_BALANCE = 2_345_730_000L
        private const val PREVIEW_KEYSTONE_BALANCE = 900_000_010_000L
        private const val PREVIEW_LEDGER_BALANCE = 400_000_010_000L
        private const val PREVIEW_VIEW_ONLY_BALANCE = 34_223_480_000L
    }
}

/**
 * One section of the screen: its header, the count tag shown when it holds more than one wallet,
 * and its rows.
 */
@Immutable
data class AccountListSectionState(
    val title: StringResource,
    val count: StringResource?,
    val items: List<WalletListItemState>,
)

/**
 * The Keystone shop card, shown while no Keystone account is connected.
 */
@Immutable
data class KeystonePromoState(
    val title: StringResource,
    val message: StringResource,
    val image: ImageResource,
    val onClick: () -> Unit,
)

/**
 * The section a wallet belongs to, in the order the sections appear.
 */
enum class AccountListKind {
    ZODL,
    KEYSTONE,
    LEDGER,
    VIEW_ONLY
}

/**
 * One wallet as [AccountListState.create] needs it.
 *
 * @param balance The account's total balance, null while it has not loaded.
 * @param onClick Selects the wallet and closes the screen.
 * @param onOptionsClick Opens the wallet's options; null for a wallet without any, whose row then
 *        shows a chevron instead of the "..." action.
 */
data class AccountListEntry(
    val kind: AccountListKind,
    val icon: ImageResource,
    val name: StringResource,
    val balance: Zatoshi?,
    val isSelected: Boolean,
    val onClick: () -> Unit,
    val onOptionsClick: (() -> Unit)?,
)
