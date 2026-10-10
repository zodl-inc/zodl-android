package co.electriccoin.zcash.ui.screen.connecthw

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Everything that differs between hardware-wallet vendors across the four shared enrollment
 * screens. Keeping it in one place is what lets those screens hold no vendor branch of their own;
 * [brandingOf] is the single point where an enrollment turns into vendor-specific resources.
 */
data class HWWalletBranding(
    @param:DrawableRes val logo: Int,
    val deviceQuestion: StringResource,
    val deviceDescription: StringResource,
    val connectNewDevice: StringResource,
    val connectActiveDevice: StringResource,
    val enterBlockHeightManually: StringResource,
    val connect: StringResource,
    val newDeviceTestTag: String,
    val activeDeviceTestTag: String,
    val enterManuallyTestTag: String,
    val connectTestTag: String,
    val blockHeightFieldTestTag: String,
)

fun brandingOf(enrollment: HWWalletEnrollment): HWWalletBranding =
    when (enrollment) {
        is HWWalletEnrollment.Keystone -> KEYSTONE_BRANDING
        is HWWalletEnrollment.Ledger -> LEDGER_BRANDING
    }

private val KEYSTONE_BRANDING =
    HWWalletBranding(
        logo = co.electriccoin.zcash.ui.design.R.drawable.image_keystone,
        deviceQuestion = stringRes(R.string.keystone_addHWWallet_deviceQuestion),
        deviceDescription = stringRes(R.string.keystone_addHWWallet_deviceDesc),
        connectNewDevice = stringRes(R.string.keystone_addHWWallet_connectNew),
        connectActiveDevice = stringRes(R.string.keystone_addHWWallet_connectActive),
        enterBlockHeightManually = stringRes(R.string.keystone_addHWWallet_enterManually),
        connect = stringRes(R.string.keystone_addHWWallet_connect),
        newDeviceTestTag = HWWalletEnrollmentTag.KEYSTONE_NEW_DEVICE,
        activeDeviceTestTag = HWWalletEnrollmentTag.KEYSTONE_ACTIVE_DEVICE,
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
        deviceQuestion = stringRes(R.string.keystone_addHWWallet_deviceQuestion),
        deviceDescription = stringRes(R.string.keystone_addHWWallet_deviceDesc),
        connectNewDevice = stringRes(R.string.keystone_addHWWallet_connectNew),
        connectActiveDevice = stringRes(R.string.keystone_addHWWallet_connectActive),
        enterBlockHeightManually = stringRes(R.string.keystone_addHWWallet_enterManually),
        connect = stringRes(R.string.keystone_addHWWallet_connect),
        newDeviceTestTag = HWWalletEnrollmentTag.LEDGER_NEW_DEVICE,
        activeDeviceTestTag = HWWalletEnrollmentTag.LEDGER_ACTIVE_DEVICE,
        enterManuallyTestTag = HWWalletEnrollmentTag.LEDGER_ENTER_MANUALLY_BTN,
        connectTestTag = HWWalletEnrollmentTag.LEDGER_CONNECT_BTN,
        blockHeightFieldTestTag = HWWalletEnrollmentTag.LEDGER_BLOCK_HEIGHT_FIELD,
    )
