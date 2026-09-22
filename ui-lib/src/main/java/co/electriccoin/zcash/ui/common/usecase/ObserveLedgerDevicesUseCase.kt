package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import kotlinx.coroutines.flow.Flow

class ObserveLedgerDevicesUseCase(
    private val ledgerDeviceDataSource: LedgerDeviceDataSource,
) {
    operator fun invoke(): Flow<List<LedgerBluetoothDevice>> = ledgerDeviceDataSource.observeDevices()
}
