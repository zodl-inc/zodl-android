package co.electriccoin.zcash.ui.screen.voting.scankeystone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRouteStage
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.InvalidKeystonePCZTQRException
import co.electriccoin.zcash.ui.common.usecase.ParseVotingKeystonePCZTUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.ui.screen.scankeystone.model.ScanKeystoneState
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.scankeystone.ScanKeystoneVotingPCZTRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ScanKeystoneVotingPCZTViewModel(
    private val args: ScanKeystoneVotingPCZTRequest,
    private val parseVotingKeystonePCZT: ParseVotingKeystonePCZTUseCase,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val navigationRouter: NavigationRouter,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : ViewModel() {
    val validationState = MutableStateFlow(ScanValidationState.NONE)

    val state =
        MutableStateFlow(
            ScanKeystoneState(
                progress = null,
                message = stringRes(R.string.scan_keystone_info_transaction),
            )
        )

    fun onScanned(result: String) =
        viewModelScope.launch {
            try {
                val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
                val scanResult =
                    parseVotingKeystonePCZT(
                        accountUuid = accountUuid,
                        roundId = args.roundIdHex,
                        bundleIndex = args.bundleIndex,
                        actionIndex = args.actionIndex,
                        result = result
                    )
                state.update { it.copy(progress = scanResult.progress) }
                if (scanResult.isFinished) {
                    navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
                }
            } catch (_: InvalidKeystonePCZTQRException) {
                validationState.update { ScanValidationState.INVALID }
            } catch (_: Exception) {
                validationState.update { ScanValidationState.INVALID }
            }
        }

    fun onBack() =
        viewModelScope.launch {
            val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
            votingRecoveryRepository.setPendingKeystoneRouteStage(
                accountUuid = accountUuid,
                roundId = args.roundIdHex,
                routeStage = VotingKeystoneRouteStage.SIGN
            )
            navigationRouter.back()
        }
}
