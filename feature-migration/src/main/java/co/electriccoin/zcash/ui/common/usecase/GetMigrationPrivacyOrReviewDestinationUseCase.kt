package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryArgs
import co.electriccoin.zcash.ui.screen.migration.customservertor.MigrationCustomServerTorArgs
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyArgs
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewArgs

/**
 * A custom (non-bundled) server can't broadcast the migration transaction over Tor at all — unlike
 * the global Tor toggle, this isn't a preference to honor, so it takes priority over the toggle
 * check below and is shown regardless of whether Tor is already the user's global setting. Note
 * this is narrower than "manual mode": manually pinning one of our own bundled servers is still
 * fine for Tor broadcast, so the check is on the endpoint itself, not just automatic-vs-manual.
 *
 * Otherwise, the Tor sheet only has something to offer when Tor isn't already the user's global
 * setting — if it's already on, both migration entry points skip straight past it. What "past it"
 * means depends on mode: IMMEDIATE (called from Setup) goes straight to Confirm Transfer Plan,
 * since there's nothing else between Setup and Review for that path. AUTOMATIC (called from How
 * This Works, ahead of Battery/Notification) goes to the Battery screen next — asked there
 * regardless of the answer, since background delivery is scheduled unconditionally either way.
 */
class GetMigrationPrivacyOrReviewDestinationUseCase(
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider,
    private val automaticServerRepository: AutomaticServerRepository,
) {
    suspend operator fun invoke(mode: MigrationMode): Any {
        if (automaticServerRepository.isServerCustom()) return MigrationCustomServerTorArgs(mode = mode)
        val torAlreadyOn = isTorEnabledStorageProvider.get() == true
        return when (mode) {
            MigrationMode.IMMEDIATE -> {
                if (torAlreadyOn) MigrationReviewArgs(mode = mode) else MigrationPrivacyArgs(mode = mode)
            }

            MigrationMode.AUTOMATIC -> {
                if (torAlreadyOn) MigrationBatteryArgs else MigrationPrivacyArgs(mode = mode)
            }
        }
    }
}
