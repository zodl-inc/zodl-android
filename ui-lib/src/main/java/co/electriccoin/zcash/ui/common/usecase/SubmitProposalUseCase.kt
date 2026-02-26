package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionArgs
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SubmitProposalUseCase(
    private val navigationRouter: NavigationRouter,
    private val accountDataSource: AccountDataSource,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val biometricRepository: BiometricRepository,
    private val swapRepository: SwapRepository,
    private val metadataRepository: MetadataRepository,
    private val processSwapTransaction: ProcessSwapTransactionUseCase,
    private val prefillSend: PrefillSendUseCase,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Submit Zashi proposal and navigate to Transaction Progress screen or navigate to Keystone PCZT flow.
     */
    suspend operator fun invoke() {
        try {
            biometricRepository.requestBiometrics(
                request =
                    BiometricRequest(
                        message =
                            stringRes(
                                R.string.authentication_system_ui_subtitle,
                                stringRes(R.string.authentication_use_case_send_funds)
                            )
                    )
            )
            val account = accountDataSource.getSelectedAccount()
            val proposal =
                when (account) {
                    is KeystoneAccount -> keystoneProposalRepository.getTransactionProposal()
                    is ZashiAccount -> zashiProposalRepository.getTransactionProposal()
                }
            if (proposal is SwapTransactionProposal) {
                val selectedSwapAsset = proposal.quote.destinationAsset
                metadataRepository.addSwapAssetToHistory(
                    tokenTicker = selectedSwapAsset.tokenTicker,
                    chainTicker = selectedSwapAsset.chainTicker
                )
            }
            when (account) {
                is KeystoneAccount -> navigationRouter.replace(SignKeystoneTransactionArgs)
                is ZashiAccount -> {
                    swapRepository.clear()
                    submitZashiProposal(proposal)
                    navigationRouter.replace(TransactionProgressArgs)
                }
            }
        } catch (_: BiometricsFailureException) {
            // do nothing
        } catch (_: BiometricsCancelledException) {
            // do nothing
        }
    }

    private fun submitZashiProposal(proposal: TransactionProposal) {
        scope.launch {
            try {
                val result = zashiProposalRepository.submit()
                if (proposal is SwapTransactionProposal) {
                    processSwapTransaction(proposal, result)
                }
            } catch (_: Exception) {
                // do nothing
            } finally {
                prefillSend.clear()
            }
        }
    }
}
