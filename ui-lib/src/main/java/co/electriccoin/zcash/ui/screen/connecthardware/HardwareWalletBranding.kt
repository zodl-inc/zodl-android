package co.electriccoin.zcash.ui.screen.connecthardware

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Everything that differs between hardware-wallet vendors across the four shared enrollment
 * screens. Keeping it in one place is what lets those screens hold no vendor branch of their own;
 * [brandingOf] is the single point where an enrollment turns into vendor-specific resources.
 */
data class HardwareWalletBranding(
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

fun brandingOf(enrollment: HardwareWalletEnrollment): HardwareWalletBranding =
    when (enrollment) {
        is HardwareWalletEnrollment.Keystone -> KEYSTONE_BRANDING
        is HardwareWalletEnrollment.Ledger -> LEDGER_BRANDING
    }

private val KEYSTONE_BRANDING =
    HardwareWalletBranding(
        logo = co.electriccoin.zcash.ui.design.R.drawable.image_keystone,
        deviceQuestion = stringRes(R.string.keystone_addHWWallet_deviceQuestion),
        deviceDescription = stringRes(R.string.keystone_addHWWallet_deviceDesc),
        connectNewDevice = stringRes(R.string.keystone_addHWWallet_connectNew),
        connectActiveDevice = stringRes(R.string.keystone_addHWWallet_connectActive),
        enterBlockHeightManually = stringRes(R.string.keystone_addHWWallet_enterManually),
        connect = stringRes(R.string.keystone_addHWWallet_connect),
        newDeviceTestTag = HardwareWalletEnrollmentTag.KEYSTONE_NEW_DEVICE,
        activeDeviceTestTag = HardwareWalletEnrollmentTag.KEYSTONE_ACTIVE_DEVICE,
        enterManuallyTestTag = HardwareWalletEnrollmentTag.KEYSTONE_ENTER_MANUALLY_BTN,
        connectTestTag = HardwareWalletEnrollmentTag.KEYSTONE_CONNECT_BTN,
        blockHeightFieldTestTag = HardwareWalletEnrollmentTag.KEYSTONE_BLOCK_HEIGHT_FIELD,
    )

/**
 * Every enrollment text the Keystone flow already had is vendor-neutral — none of them names
 * Keystone — so Ledger reuses the same resources rather than carrying translated duplicates. Only
 * the logo and the test tags actually differ today.
 */
private val LEDGER_BRANDING =
    HardwareWalletBranding(
        logo = R.drawable.ic_ledger_wordmark,
        deviceQuestion = stringRes(R.string.keystone_addHWWallet_deviceQuestion),
        deviceDescription = stringRes(R.string.keystone_addHWWallet_deviceDesc),
        connectNewDevice = stringRes(R.string.keystone_addHWWallet_connectNew),
        connectActiveDevice = stringRes(R.string.keystone_addHWWallet_connectActive),
        enterBlockHeightManually = stringRes(R.string.keystone_addHWWallet_enterManually),
        connect = stringRes(R.string.keystone_addHWWallet_connect),
        newDeviceTestTag = HardwareWalletEnrollmentTag.LEDGER_NEW_DEVICE,
        activeDeviceTestTag = HardwareWalletEnrollmentTag.LEDGER_ACTIVE_DEVICE,
        enterManuallyTestTag = HardwareWalletEnrollmentTag.LEDGER_ENTER_MANUALLY_BTN,
        connectTestTag = HardwareWalletEnrollmentTag.LEDGER_CONNECT_BTN,
        blockHeightFieldTestTag = HardwareWalletEnrollmentTag.LEDGER_BLOCK_HEIGHT_FIELD,
    )
