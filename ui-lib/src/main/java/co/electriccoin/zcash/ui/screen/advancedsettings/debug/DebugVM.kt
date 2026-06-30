package co.electriccoin.zcash.ui.screen.advancedsettings.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepository
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.db.DebugDBArgs
import co.electriccoin.zcash.ui.screen.advancedsettings.debug.text.DebugTextArgs
import co.electriccoin.zcash.ui.screen.hotfix.ephemeral.EphemeralHotfixArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DebugVM(
    private val copyToClipboardUseCase: CopyToClipboardUseCase,
    private val ephemeralAddressRepository: EphemeralAddressRepository,
    private val accountDataSource: AccountDataSource,
    private val navigationRouter: NavigationRouter,
    private val simulateSeedNotRelevant: SimulateSeedNotRelevantUseCase,
) : ViewModel() {
    val state: StateFlow<DebugState> =
        MutableStateFlow(
            DebugState(
                onBack = ::onBack,
                items =
                    listOf(
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Get Current Ephemeral Address"),
                            onClick = ::onGetEphemeralAddressClick
                        ),
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Generate an Ephemeral Address"),
                            onClick = ::onGenerateEphemeralAddressClick
                        ),
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Discover Funds"),
                            onClick = ::onDiscoverFundsClick
                        ),
                        ListItemState(
                            // bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            // smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded),
                            title = stringRes("Query Database"),
                            onClick = ::onQueryDatabaseClick
                        ),
                        ListItemState(
                            title = stringRes("Current Shield Addresses"),
                            onClick = ::onCurrentShieldAddressesClick
                        ),
                        ListItemState(
                            title = stringRes("Simulate SeedNotRelevant"),
                            onClick = ::onSimulateSeedNotRelevantClick
                        )
                    )
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()

    private fun onGetEphemeralAddressClick() =
        viewModelScope.launch {
            val address = ephemeralAddressRepository.get()
            copyToClipboardUseCase(address?.address.toString())
            navigationRouter.forward(
                DebugTextArgs(
                    title = "Current Ephemeral Address",
                    text = address.toString()
                )
            )
        }

    private fun onGenerateEphemeralAddressClick() =
        viewModelScope.launch {
            val address = ephemeralAddressRepository.create()
            copyToClipboardUseCase(address.address)
            navigationRouter.forward(
                DebugTextArgs(
                    title = "New Ephemeral Address",
                    text = address.toString()
                )
            )
        }

    private fun onCurrentShieldAddressesClick() =
        viewModelScope.launch {
            val accounts = accountDataSource.getAllAccounts()
            val text =
                accounts.joinToString("\n\n") { account ->
                    val label =
                        when (account) {
                            is ZashiAccount -> "Zashi"
                            is KeystoneAccount -> "Keystone"
                        }
                    "$label\n${account.unified.address.address}"
                }
            copyToClipboardUseCase(text)
            navigationRouter.forward(
                DebugTextArgs(
                    title = "Current Shield Addresses",
                    text = text
                )
            )
        }

    private fun onDiscoverFundsClick() = navigationRouter.forward(EphemeralHotfixArgs(null))

    private fun onQueryDatabaseClick() = navigationRouter.forward(DebugDBArgs)

    private fun onSimulateSeedNotRelevantClick() =
        viewModelScope.launch {
            simulateSeedNotRelevant()
        }
}
