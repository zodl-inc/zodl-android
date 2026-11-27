package co.electriccoin.zcash.ui.common.viewonly

import androidx.annotation.DrawableRes
import androidx.navigation.NavGraphBuilder
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletBranding

/**
 * The seam between ui-lib and the feature-viewing-key module, shaped like LedgerContracts.kt: ui-lib
 * never imports feature-viewing-key classes and talks only to these contracts, which the app module
 * wires in via Koin (`featureViewingKeyModule`).
 *
 * What stays in ui-lib is what the rest of the wallet must know about a view-only account: the
 * ViewOnlyAccount model, its import and mapping in the account data source, and the spend guards
 * that reject it. Every text and image the wallet shows for such an account lives in the module
 * and reaches ui-lib through [ViewOnlyContent].
 */
interface ViewOnlyNavigator {
    /** Opens the "Add Account" options sheet from the Wallets & Hardware screen. */
    fun forwardToAddAccount()

    /**
     * Opens the sheet that confirms removing the view-only account [accountKeyId] names
     * (`AccountUuid.toStorageKeyId()`), from the Edit Account screen. Only that id goes in a route,
     * never the viewing key or any other secret.
     */
    fun forwardToRemoveAccount(accountKeyId: String)

    /** Shows the import success screen for the account that was just imported. */
    fun forwardToImported()
}

/** Installs the view-only destinations into the wallet nav graph. */
interface ViewOnlyNavContributor {
    fun contribute(navGraphBuilder: NavGraphBuilder)
}

/**
 * The shared birthday screens' way into a view-only import: whether the key the flow collected is
 * still held in memory, and the hand-over to the naming step once the birthday is known. The
 * birthday screens come before the name, so they never import the account themselves.
 */
interface ViewOnlyAccountImporter {
    /**
     * False after process death emptied the in-memory pending import; the screens then return to
     * the root.
     */
    fun hasPendingImport(): Boolean

    /**
     * Stores [birthday] with the pending import and opens the naming step, which imports the
     * account.
     *
     * @throws co.electriccoin.zcash.ui.common.model.ViewOnlyPendingImportMissingException if
     *         nothing is pending.
     */
    fun continueWithBirthday(birthday: BlockHeight?)
}

/**
 * Every text and image ui-lib shows for a view-only account. The module implements it with its own
 * resources, so none of this copy lives in ui-lib or ui-design-lib.
 */
interface ViewOnlyContent {
    /**
     * The eye of the account's icon, padding built in, drawn over a circle in the account's colour or
     * the default avatar circle.
     */
    @get:DrawableRes
    val accountIcon: Int

    /** The account name used when the name stored with the account is blank. */
    val defaultAccountName: StringResource

    /** The small label under the account name in the top app bar. */
    val topBarSubtitle: StringResource

    /** The Wallets & Hardware section header above a single view-only account. */
    val accountListSection: StringResource

    /** The Wallets & Hardware section header above several view-only accounts. */
    val accountListSectionPlural: StringResource

    /** The account list's call to action that opens the "Add Account" options. */
    val addAccountButton: StringResource

    /** The leading icon of [addAccountButton]. */
    @get:DrawableRes
    val addAccountButtonIcon: Int

    /** Title of the error shown when an action needs spend authority the account lacks. */
    val unsupportedOperationTitle: StringResource

    /** Message of the error shown when an action needs spend authority the account lacks. */
    val unsupportedOperationMessage: StringResource

    /** Title of the account's shielded receive address. */
    val receiveShieldedTitle: StringResource

    /** Title of the account's transparent receive address. */
    val receiveTransparentTitle: StringResource

    /** Why the integrations are disabled while a view-only account is selected. */
    val integrationsDisabledInfo: StringResource

    /** The prefix of the tax export file name, which is not translated. */
    val taxExportFileNamePrefix: String

    /**
     * The logo, copy and test tags of the shared birthday screens while they take a view-only
     * account's birthday; see `brandingOf`. It has no new-or-active copy, because a view-only import
     * never asks that question.
     */
    val enrollmentBranding: HWWalletBranding

    /** The instructions of the keep-open screen shown while an imported account syncs its history. */
    val keepOpenDescription: StringResource

    /** The subtitle of that keep-open screen. */
    val keepOpenSubtitle: StringResource

    /** The warning of that keep-open screen. */
    val keepOpenDisclaimer: StringResource

    /** The label of that keep-open screen's keep-screen-on checkbox. */
    val keepOpenCheckboxLabel: StringResource

    /** The icon of [removeAccountInfo]. */
    @get:DrawableRes
    val removeAccountInfoIcon: Int

    /** The Edit Account screen's line above Remove Account, saying removing asks for a confirmation. */
    val removeAccountInfo: StringResource

    /** The Edit Account screen's Remove Account button. */
    val removeAccountButton: StringResource
}
