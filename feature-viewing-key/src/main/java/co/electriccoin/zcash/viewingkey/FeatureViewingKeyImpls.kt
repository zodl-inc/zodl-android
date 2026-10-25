package co.electriccoin.zcash.viewingkey

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.ViewOnlyPendingImportMissingException
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepository
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyAccountImporter
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavContributor
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.dialogComposable
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletBranding
import co.electriccoin.zcash.ui.screen.viewingkey.addaccount.AddVKAccountArgs
import co.electriccoin.zcash.ui.screen.viewingkey.addaccount.AddVKAccountScreen
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKScreen
import co.electriccoin.zcash.ui.screen.viewingkey.imported.ImportedVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.imported.ImportedVKScreen
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKScreen
import co.electriccoin.zcash.ui.screen.viewingkey.name.NameVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.name.NameVKScreen
import co.electriccoin.zcash.ui.screen.viewingkey.remove.RemoveVKAccountArgs
import co.electriccoin.zcash.ui.screen.viewingkey.remove.RemoveVKAccountScreen
import co.electriccoin.zcash.ui.screen.viewingkey.scan.ScanVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.scan.ScanVKScreen

/**
 * The module's own copy and images for everything ui-lib shows about a view-only account.
 */
internal class ViewOnlyContentImpl : ViewOnlyContent {
    override val accountIcon: Int = R.drawable.view_only_ic_avatar_eye

    override val defaultAccountName = stringRes(R.string.view_only_account_default_name)

    override val topBarSubtitle = stringRes(R.string.view_only_top_bar_subtitle)

    override val accountListSection = stringRes(R.string.view_only_account_list_section)

    override val accountListSectionPlural = stringRes(R.string.view_only_account_list_section_plural)

    override val addAccountButton = stringRes(R.string.view_only_add_account)

    override val addAccountButtonIcon: Int = R.drawable.view_only_ic_plus

    override val unsupportedOperationTitle = stringRes(R.string.view_only_unsupported_title)

    override val unsupportedOperationMessage = stringRes(R.string.view_only_unsupported_message)

    override val receiveShieldedTitle = stringRes(R.string.view_only_receive_shielded_title)

    override val receiveTransparentTitle = stringRes(R.string.view_only_receive_transparent_title)

    override val integrationsDisabledInfo = stringRes(R.string.view_only_integrations_disabled_info)

    override val taxExportFileNamePrefix = "View_Only"

    override val enrollmentBranding =
        HWWalletBranding(
            logo = null,
            newOrActive = null,
            enterBlockHeightManually = stringRes(R.string.view_only_birthday_enter_manually),
            connect = stringRes(R.string.view_only_continue),
            enterManuallyTestTag = VKEnrollmentTag.ENTER_MANUALLY_BTN,
            connectTestTag = VKEnrollmentTag.CONTINUE_BTN,
            blockHeightFieldTestTag = VKEnrollmentTag.BLOCK_HEIGHT_FIELD,
            dateTitle = stringRes(R.string.view_only_birthday_title),
            dateSubtitle = stringRes(R.string.view_only_birthday_subtitle),
        )

    override val keepOpenDescription = stringRes(R.string.view_only_keep_open_description)

    override val keepOpenSubtitle = stringRes(R.string.view_only_keep_open_subtitle)

    override val keepOpenDisclaimer = stringRes(R.string.view_only_keep_open_warning)

    override val keepOpenCheckboxLabel = stringRes(R.string.view_only_keep_open_checkbox)

    override val removeAccountInfoIcon: Int = R.drawable.view_only_ic_info

    override val removeAccountInfo = stringRes(R.string.view_only_edit_remove_info)

    override val removeAccountButton = stringRes(R.string.view_only_remove_account)
}

private object VKEnrollmentTag {
    const val ENTER_MANUALLY_BTN = "VIEW_ONLY_DATE_ENTER_MANUALLY"
    const val CONTINUE_BTN = "VIEW_ONLY_HEIGHT_CONTINUE"
    const val BLOCK_HEIGHT_FIELD = "VIEW_ONLY_HEIGHT_BLOCK_HEIGHT_FIELD"
}

internal class ViewOnlyAccountImporterImpl(
    private val pendingImportRepository: ViewOnlyPendingImportRepository,
    private val navigationRouter: NavigationRouter,
) : ViewOnlyAccountImporter {
    override fun hasPendingImport(): Boolean = pendingImportRepository.get() != null

    override fun continueWithBirthday(birthday: BlockHeight?) {
        val pending = pendingImportRepository.get() ?: throw ViewOnlyPendingImportMissingException()
        pendingImportRepository.set(pending.copy(birthday = birthday))
        navigationRouter.forward(NameVKArgs)
    }
}

internal class ViewOnlyNavigatorImpl(
    private val navigationRouter: NavigationRouter,
) : ViewOnlyNavigator {
    override fun forwardToAddAccount() = navigationRouter.forward(AddVKAccountArgs)

    override fun forwardToImported() = navigationRouter.forward(ImportedVKArgs)

    override fun forwardToRemoveAccount(accountKeyId: String) =
        navigationRouter.forward(RemoveVKAccountArgs(accountKeyId = accountKeyId))
}

internal class ViewOnlyNavContributorImpl : ViewOnlyNavContributor {
    override fun contribute(navGraphBuilder: NavGraphBuilder) {
        with(navGraphBuilder) {
            dialogComposable<AddVKAccountArgs> { AddVKAccountScreen() }
            composable<ImportVKArgs> { ImportVKScreen() }
            composable<ScanVKArgs> { ScanVKScreen() }
            composable<NameVKArgs> { NameVKScreen() }
            composable<ImportedVKArgs> { ImportedVKScreen() }
            dialogComposable<AlreadyImportedVKArgs> { AlreadyImportedVKScreen() }
            dialogComposable<RemoveVKAccountArgs> { RemoveVKAccountScreen(it.toRoute()) }
        }
    }
}
