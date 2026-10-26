package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.migration.MigrationGateInfo
import co.electriccoin.zcash.ui.common.model.migration.activationHeightOrNull
import co.electriccoin.zcash.ui.common.model.migration.estimateNetworkUpgradeInstant
import kotlin.time.Clock

/**
 * Reads what the SDK's NU7 migration gate allows at the wallet's next height, plus the estimated
 * time of the activation. No network request: the SDK uses its estimated chain tip.
 *
 * If the SDK cannot be asked, the answer is [MigrationGateInfo.OPEN] rather than an error. That only
 * decides what the UI offers; the SDK refuses a closed lane regardless, and the callers handle that.
 */
class GetMigrationGateInfoUseCase(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
) {
    suspend operator fun invoke(): MigrationGateInfo =
        runCatching {
            val sdk = getOrchardMigrationSdk()
            val availability = sdk.migrationAvailability()
            val activation = availability.activationHeightOrNull()
            MigrationGateInfo(
                availability = availability,
                estimatedActivation =
                    activation?.let {
                        estimateNetworkUpgradeInstant(
                            now = Clock.System.now(),
                            tipHeight = sdk.estimatedChainTip(),
                            activationHeight = it,
                            secondsPerBlock = sdk.estimatedSecondsPerBlock(),
                            nu7ActivationHeight = sdk.nu7ActivationHeight,
                        )
                    },
            )
        }.getOrDefault(MigrationGateInfo.OPEN)
}
