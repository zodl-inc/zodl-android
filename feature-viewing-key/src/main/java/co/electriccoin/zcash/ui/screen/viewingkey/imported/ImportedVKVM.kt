package co.electriccoin.zcash.ui.screen.viewingkey.imported

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The end of the import. The account is already imported and selected by the time this screen
 * shows, so its avatar takes the selected account's colour, and Done and back both close the flow
 * and land on the new account's home.
 */
internal class ImportedVKVM(
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
    metadataRepository: MetadataRepository,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ImportedVKState> =
        observeSelectedWalletAccount
            .require()
            .map { it.sdkAccount.accountUuid }
            .distinctUntilChanged()
            .flatMapLatest(metadataRepository::observeColor)
            .map(::createState)
            .stateIn(viewModel = this, initialValue = createState(null))

    private fun createState(color: Color?) =
        ImportedVKState.create(color = color, onDoneClick = ::onDone, onBack = ::onDone)

    private fun onDone() = navigationRouter.backToRoot()
}
