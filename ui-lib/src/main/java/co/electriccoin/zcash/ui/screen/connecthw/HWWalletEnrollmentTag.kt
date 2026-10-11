package co.electriccoin.zcash.ui.screen.connecthw

/**
 * The Keystone values are the ones the Keystone-only screens used before the two flows were
 * merged, unchanged so nothing that targets them has to move.
 */
object HWWalletEnrollmentTag {
    const val KEYSTONE_NEW_DEVICE = "KEYSTONE_NEW_OR_ACTIVE_NEW_DEVICE"
    const val KEYSTONE_ACTIVE_DEVICE = "KEYSTONE_NEW_OR_ACTIVE_ACTIVE_DEVICE"
    const val KEYSTONE_ENTER_MANUALLY_BTN = "KEYSTONE_DATE_ENTER_MANUALLY"
    const val KEYSTONE_CONNECT_BTN = "KEYSTONE_HEIGHT_CONNECT"
    const val KEYSTONE_BLOCK_HEIGHT_FIELD = "KEYSTONE_HEIGHT_BLOCK_HEIGHT_FIELD"

    const val LEDGER_NEW_DEVICE = "LEDGER_NEW_OR_ACTIVE_NEW_DEVICE"
    const val LEDGER_ACTIVE_DEVICE = "LEDGER_NEW_OR_ACTIVE_ACTIVE_DEVICE"
    const val LEDGER_ENTER_MANUALLY_BTN = "LEDGER_DATE_ENTER_MANUALLY"
    const val LEDGER_CONNECT_BTN = "LEDGER_HEIGHT_CONNECT"
    const val LEDGER_BLOCK_HEIGHT_FIELD = "LEDGER_HEIGHT_BLOCK_HEIGHT_FIELD"
}
