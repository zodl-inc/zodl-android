package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.widget.Toast
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FixEphemeralAddressUseCase(
    private val synchronizerProvider: SynchronizerProvider,
    private val navigationRouter: NavigationRouter,
    private val accountDataSource: AccountDataSource,
    private val getSwapStatus: GetSwapStatusUseCase,
    private val metadataRepository: MetadataRepository,
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val isLoading = MutableStateFlow(false)

    private var job: Job? = null

    operator fun invoke(address: String) {
        if (job?.isActive == true) return
        job =
            scope.launch {
                try {
                    isLoading.update { true }
                    val validation = synchronizerProvider.getSynchronizer().validateAddress(address)

                    val ephemeral =
                        when (validation) {
                            AddressType.Shielded,
                            AddressType.Tex,
                            AddressType.Unified -> {
                                return@launch
                            }

                            AddressType.Transparent -> {
                                address
                            }

                            is AddressType.Invalid -> {
                                val swapMetadata =
                                    metadataRepository.getSwapMetadata(address) ?: return@launch
                                getSwapStatus(swapMetadata)
                                    .status
                                    ?.quote
                                    ?.destinationAddress
                                    ?.address
                                    ?: return@launch
                            }
                        }
                    navigationRouter.back()
                    fetchUtxosByAddress(ephemeral)
                } finally {
                    isLoading.update { false }
                }
            }
    }

    fun observeIsLoading() = isLoading.asStateFlow()

    private suspend fun fetchUtxosByAddress(address: String) {
        val fundsDiscovered =
            synchronizerProvider.getSynchronizer().fetchUtxosByAddress(
                accountUuid = accountDataSource.getSelectedAccount().sdkAccount.accountUuid,
                address = address
            )
        withContext(Dispatchers.Main.immediate) {
            if (fundsDiscovered) {
                Toast.makeText(context, "Funds were successfully discovered", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "No funds were discovered", Toast.LENGTH_LONG).show()
            }
        }
    }
}
