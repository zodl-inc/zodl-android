package co.electriccoin.zcash.ui.screen.connecthw

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Everything that differs between hardware-wallet vendors across the four shared enrollment
 * screens. Keeping it in one place is what lets those screens hold no vendor branch of their own;
 * [brandingOf] is the single point where an enrollment turns into vendor-specific resources. A
 * view-only import reuses the birthday screens with the branding the view-only feature supplies;
 * it skips the new-or-active question, so its [newOrActive] is null, and it shows no logo.
 *
 * @param logo the vendor logo above the screens' heading; null shows none.
 * @param connect the label of the estimation and height screens' primary button.
 * @param dateTitle the date screen's heading; null keeps the generic heading.
 * @param dateSubtitle the text under the date screen's heading; null keeps the hardware-wallet text.
 */
data class HWWalletBranding(
    @param:DrawableRes val logo: Int?,
    val newOrActive: HWNewOrActiveBranding?,
    val enterBlockHeightManually: StringResource,
    val connect: StringResource,
    val enterManuallyTestTag: String,
    val connectTestTag: String,
    val blockHeightFieldTestTag: String,
    val dateTitle: StringResource? = null,
    val dateSubtitle: StringResource? = null,
)

/**
 * The copy and test tags of the new-or-active question, which only the hardware-wallet
 * enrollments ask.
 */
data class HWNewOrActiveBranding(
    val deviceQuestion: StringResource,
    val deviceDescription: StringResource,
    val connectNewDevice: StringResource,
    val connectActiveDevice: StringResource,
    val newDeviceTestTag: String,
    val activeDeviceTestTag: String,
)

fun brandingOf(
    enrollment: HWWalletEnrollment,
    viewOnlyContent: ViewOnlyContent,
): HWWalletBranding =
    when (enrollment) {
        is HWWalletEnrollment.Keystone -> KEYSTONE_BRANDING
        is HWWalletEnrollment.Ledger -> LEDGER_BRANDING
        is HWWalletEnrollment.ViewOnly -> viewOnlyContent.enrollmentBranding
    }

private val KEYSTONE_BRANDING =
    HWWalletBranding(
        logo = co.electriccoin.zcash.ui.design.R.drawable.image_keystone,
        newOrActive =
            HWNewOrActiveBranding(
                deviceQuestion = stringRes(R.string.keystone_addHWWallet_deviceQuestion),
                deviceDescription = stringRes(R.string.keystone_addHWWallet_deviceDesc),
                connectNewDevice = stringRes(R.string.keystone_addHWWallet_connectNew),
                connectActiveDevice = stringRes(R.string.keystone_addHWWallet_connectActive),
                newDeviceTestTag = HWWalletEnrollmentTag.KEYSTONE_NEW_DEVICE,
                activeDeviceTestTag = HWWalletEnrollmentTag.KEYSTONE_ACTIVE_DEVICE,
            ),
        enterBlockHeightManually = stringRes(R.string.keystone_addHWWallet_enterManually),
        connect = stringRes(R.string.keystone_addHWWallet_connect),
        enterManuallyTestTag = HWWalletEnrollmentTag.KEYSTONE_ENTER_MANUALLY_BTN,
        connectTestTag = HWWalletEnrollmentTag.KEYSTONE_CONNECT_BTN,
        blockHeightFieldTestTag = HWWalletEnrollmentTag.KEYSTONE_BLOCK_HEIGHT_FIELD,
    )

/**
 * Every enrollment text the Keystone flow already had is vendor-neutral — none of them names
 * Keystone — so Ledger reuses the same resources rather than carrying translated duplicates. Only
 * the logo and the test tags actually differ today.
 */
private val LEDGER_BRANDING =
    HWWalletBranding(
        logo = R.drawable.ic_ledger_wordmark,
        newOrActive =
            HWNewOrActiveBranding(
                deviceQuestion = stringRes(R.string.keystone_addHWWallet_deviceQuestion),
                deviceDescription = stringRes(R.string.keystone_addHWWallet_deviceDesc),
                connectNewDevice = stringRes(R.string.keystone_addHWWallet_connectNew),
                connectActiveDevice = stringRes(R.string.keystone_addHWWallet_connectActive),
                newDeviceTestTag = HWWalletEnrollmentTag.LEDGER_NEW_DEVICE,
                activeDeviceTestTag = HWWalletEnrollmentTag.LEDGER_ACTIVE_DEVICE,
            ),
        enterBlockHeightManually = stringRes(R.string.keystone_addHWWallet_enterManually),
        connect = stringRes(R.string.keystone_addHWWallet_connect),
        enterManuallyTestTag = HWWalletEnrollmentTag.LEDGER_ENTER_MANUALLY_BTN,
        connectTestTag = HWWalletEnrollmentTag.LEDGER_CONNECT_BTN,
        blockHeightFieldTestTag = HWWalletEnrollmentTag.LEDGER_BLOCK_HEIGHT_FIELD,
    )
