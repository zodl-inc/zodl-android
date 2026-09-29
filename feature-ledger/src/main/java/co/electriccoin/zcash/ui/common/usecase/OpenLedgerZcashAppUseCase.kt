package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository

/**
 * Asks the Ledger the user picked and bonded with to open the Zcash app, over a link of its own
 * that is closed again before this returns. Declines and device errors surface as the SDK's
 * `LedgerException`s. Returns [OpenLedgerZcashAppResult.NoDevice] when process death has emptied
 * [LedgerSelectedDeviceRepository], without talking to any device.
 */
class OpenLedgerZcashAppUseCase(
    private val ledgerDeviceDataSource: LedgerDeviceDataSource,
    private val ledgerSelectedDeviceRepository: LedgerSelectedDeviceRepository,
) {
    suspend operator fun invoke(): OpenLedgerZcashAppResult {
        val device = ledgerSelectedDeviceRepository.get() ?: return OpenLedgerZcashAppResult.NoDevice
        ledgerDeviceDataSource.openZcashApp(device)
        return OpenLedgerZcashAppResult.Opened
    }
}

sealed interface OpenLedgerZcashAppResult {
    data object Opened : OpenLedgerZcashAppResult

    data object NoDevice : OpenLedgerZcashAppResult
}
