package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationAvailability
import cash.z.ecc.android.sdk.MigrationWalletTipUnavailableException
import co.electriccoin.zcash.ui.common.model.migration.MigrationGateInfo
import co.electriccoin.zcash.ui.common.model.migration.activationHeightOrNull
import co.electriccoin.zcash.ui.common.model.migration.estimateNetworkUpgradeInstant
import kotlin.time.Clock

/**
 * Reads what the SDK's NU7 migration gate allows at the wallet's next height, plus the estimated
 * time of the activation. No network request: the SDK judges the wallet's own chain tip.
 *
 * A wallet that has no chain tip yet (it has not synced) gets a closed scheduled lane without a date: the SDK refuses
 * the scheduled lane for it, and it is not known whether the upgrade has happened. If the SDK cannot be asked for any
 * other reason, the answer is [MigrationGateInfo.OPEN] rather than an error. That only decides what the UI offers;
 * the SDK refuses a closed lane regardless, and the callers handle that.
 */
class GetMigrationGateInfoUseCase(
    private val getOrchardMigrationSdk: GetOrchardMigrationSdkUseCase,
) {
    suspend operator fun invoke(): MigrationGateInfo =
        runCatching {
            val sdk = getOrchardMigrationSdk()
            try {
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
            } catch (e: MigrationWalletTipUnavailableException) {
                val activation = sdk.nu7ActivationHeight ?: throw e
                MigrationGateInfo(
                    MigrationAvailability.PausedUntilNetworkUpgrade(activation),
                    estimatedActivation = null
                )
            }
        }.getOrDefault(MigrationGateInfo.OPEN)
}
