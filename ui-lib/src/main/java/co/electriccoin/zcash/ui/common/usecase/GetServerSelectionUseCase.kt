package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.provider.ServerSelectionProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetServerSelectionUseCase(
    private val serverSelectionProvider: ServerSelectionProvider
) {
    fun observe() =
        serverSelectionProvider.serverSelection
            .map { it ?: ServerSelection.automatic() }
            .distinctUntilChanged()

    suspend operator fun invoke() = serverSelectionProvider.getServerSelection() ?: ServerSelection.automatic()
}
