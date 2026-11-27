package co.electriccoin.zcash.ui.screen.home

object HomeTags {
    const val SEND = "SEND"
    const val RECEIVE = "RECEIVE"
    const val PAY = "HOME_PAY"
    const val SWAP = "HOME_SWAP"

    // Mirrors iOS's AccessibilityID.Home.syncComplete/.syncPending — an always-present,
    // invisible marker e2e flows can poll for instead of scraping the sync banner's text.
    const val SYNC_COMPLETE = "home.syncComplete"
    const val SYNC_PENDING = "home.syncPending"
}
