package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.ServerConnectionMode
import co.electriccoin.zcash.ui.common.provider.IsPublicConnectionProvider
import co.electriccoin.zcash.ui.common.repository.AutomaticServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveServerConnectionModeUseCase(
    private val automaticServerRepository: AutomaticServerRepository,
    private val isPublicConnectionProvider: IsPublicConnectionProvider,
) {
    operator fun invoke(): Flow<ServerConnectionMode> =
        combine(
            automaticServerRepository.isServerAutomatic,
            isPublicConnectionProvider.observe()
        ) { isAutomatic, isPublic ->
            when {
                !isAutomatic -> ServerConnectionMode.MANUAL
                isPublic -> ServerConnectionMode.PUBLIC
                else -> ServerConnectionMode.PRIVATE
            }
        }.distinctUntilChanged()
}
