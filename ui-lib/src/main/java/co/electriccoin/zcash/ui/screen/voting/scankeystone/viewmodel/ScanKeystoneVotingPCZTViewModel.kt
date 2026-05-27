package co.electriccoin.zcash.ui.screen.voting.scankeystone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneDuplicateSignatureException
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRouteStage
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneScanNotice
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneScanNoticeType
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneWrongSignatureException
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
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingArgs
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
                validationState.update { ScanValidationState.NONE }
                state.update {
                    it.copy(
                        message = stringRes(R.string.scan_keystone_info_transaction),
                        progress = scanResult.progress
                    )
                }
                if (scanResult.isFinished) {
                    val recovery = votingRecoveryRepository.get(accountUuid, args.roundIdHex)
                    val bundleCount = recovery?.bundleCount ?: 0
                    if (args.bundleIndex + 1 < bundleCount) {
                        navigationRouter.forward(SignKeystoneVotingArgs(roundIdHex = args.roundIdHex))
                    } else {
                        navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
                    }
                }
            } catch (exception: VotingKeystoneDuplicateSignatureException) {
                storeRejectedScanNoticeAndReturn(
                    type = VotingKeystoneScanNoticeType.DUPLICATE_SIGNATURE,
                    bundleNumber = exception.signedBundleIndex + 1,
                    bundleCount = exception.bundleCount
                )
            } catch (exception: VotingKeystoneWrongSignatureException) {
                storeRejectedScanNoticeAndReturn(
                    type = VotingKeystoneScanNoticeType.WRONG_SIGNATURE,
                    bundleNumber = exception.currentBundleIndex + 1,
                    bundleCount = exception.bundleCount
                )
            } catch (_: InvalidKeystonePCZTQRException) {
                validationState.update { ScanValidationState.INVALID }
            } catch (_: Exception) {
                validationState.update { ScanValidationState.INVALID }
            }
        }

    private suspend fun storeRejectedScanNoticeAndReturn(
        type: VotingKeystoneScanNoticeType,
        bundleNumber: Int,
        bundleCount: Int
    ) {
        val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
        votingRecoveryRepository.storePendingKeystoneScanNotice(
            accountUuid = accountUuid,
            roundId = args.roundIdHex,
            scanNotice =
                VotingKeystoneScanNotice(
                    type = type,
                    bundleNumber = bundleNumber,
                    bundleCount = bundleCount
                )
        )
        navigationRouter.back()
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
