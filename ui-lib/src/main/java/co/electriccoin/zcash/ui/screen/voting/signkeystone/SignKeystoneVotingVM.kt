package co.electriccoin.zcash.ui.screen.voting.signkeystone

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.toVotingRawZecLabel
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneResumeSubmissionException
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRouteStage
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneScanNotice
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneScanNoticeType
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSigningBundle
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.CreateVotingKeystonePcztEncoderUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SkipRemainingKeystoneBundlesUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.addressbook.ADDRESS_MAX_LENGTH
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionBottomSheetState
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.ZashiAccountInfoListItemState
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.scankeystone.ScanKeystoneVotingPCZTRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class SignKeystoneVotingVM(
    private val args: SignKeystoneVotingArgs,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val createVotingKeystonePcztEncoder: CreateVotingKeystonePcztEncoderUseCase,
    private val skipRemainingKeystoneBundles: SkipRemainingKeystoneBundlesUseCase,
    private val votingRecoveryRepository: VotingRecoveryRepository,
) : ViewModel() {
    private var signingBundle: VotingKeystoneSigningBundle? = null
    private val selectedAccountUuid =
        observeSelectedWalletAccount
            .require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }
            .stateIn(this)

    private val isLoading = MutableStateFlow(true)
    private val errorSheetState = MutableStateFlow<ZashiConfirmationState?>(null)
    private val isBottomSheetVisible = MutableStateFlow(false)
    private val isSkipBottomSheetVisible = MutableStateFlow(false)
    private val currentQrPart = MutableStateFlow<String?>(null)
    private val signingBundleState = MutableStateFlow<VotingKeystoneSigningBundle?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val recovery =
        selectedAccountUuid
            .filterNotNull()
            .flatMapLatest { accountUuid ->
                votingRecoveryRepository.observe(accountUuid, args.roundIdHex)
            }.stateIn(this)

    val loading: StateFlow<Boolean> = isLoading
    val errorSheet: StateFlow<ZashiConfirmationState?> = errorSheetState
    val scanNoticeSheet: StateFlow<ZashiConfirmationState?> =
        recovery
            .map { snapshot ->
                snapshot
                    ?.pendingKeystoneRequest
                    ?.scanNotice
                    ?.toScanNoticeSheet()
            }.stateIn(this)

    val bottomSheetState =
        isBottomSheetVisible
            .map { isVisible ->
                if (isVisible) {
                    SignKeystoneTransactionBottomSheetState(
                        onBack = ::onCloseBottomSheetClick,
                        positiveButton =
                            ButtonState(
                                text = stringRes(R.string.sign_keystone_transaction_bottom_sheet_go_back),
                                onClick = ::onCloseBottomSheetClick
                            ),
                        negativeButton =
                            ButtonState(
                                text = stringRes(R.string.sign_keystone_transaction_bottom_sheet_reject),
                                onClick = ::onRejectBottomSheetClick
                            ),
                    )
                } else {
                    null
                }
            }.stateIn(this)

    val skipBottomSheetState =
        combine(
            isSkipBottomSheetVisible,
            recovery
        ) { isVisible, recovery ->
            recovery
                ?.takeIf { isVisible }
                ?.toSkipBottomSheetState()
        }.stateIn(this)

    val state: StateFlow<AuthorizeVoteSignKeystoneState?> =
        combine(
            observeSelectedWalletAccount.require(),
            currentQrPart,
            signingBundleState,
            recovery
        ) { wallet, qrData, bundle, recovery ->
            bundle?.let {
                val signedCount = bundle.bundleIndex
                val bundleWeights = recovery?.bundleWeights ?: emptyList()
                val signedWeight = bundleWeights.take(signedCount).sum()
                val awaitingWeight = bundleWeights.drop(signedCount).sum()

                AuthorizeVoteSignKeystoneState(
                    onBack = ::onCancelClick,
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = wallet.icon,
                            title = wallet.name,
                            subtitle = stringRes("${wallet.unified.address.address.take(ADDRESS_MAX_LENGTH)}...")
                        ),
                    badgeText = stringRes(R.string.sign_keystone_transaction_badge),
                    qrData = qrData,
                    generateNextQrCode = { currentQrPart.update { signingBundle?.encoder?.nextPart() } },
                    currentBundleNumber = bundle.bundleIndex + 1,
                    totalBundles = bundle.bundleCount,
                    signedBundleCount = signedCount,
                    signedZec = stringRes(R.string.authorize_vote_zec_signed, signedWeight.toVotingWeightLabel()),
                    pendingZec = stringRes(R.string.authorize_vote_zec_awaiting, awaitingWeight.toVotingWeightLabel()),
                    memoText =
                        stringRes(
                            R.string.vote_confirm_memo_authorize,
                            bundle.roundTitle,
                            bundle.memoWeightZatoshi.toVotingRawZecLabel()
                        ),
                    useSignedBundlesOnly =
                        if (signedCount > 0) {
                            UseSignedBundlesOnlyState(
                                remainingZec = stringRes(awaitingWeight.toVotingWeightLabel()),
                                onClick = ::onSkipRemainingClick
                            )
                        } else {
                            null
                        },
                    scanButton =
                        ButtonState(
                            text = stringRes(R.string.sign_keystone_voting_scan_signature),
                            onClick = ::onSignTransactionClick
                        ),
                )
            }
        }.stateIn(this)

    init {
        loadSigningBundle()
    }

    private fun onRejectBottomSheetClick() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            isBottomSheetVisible.update { false }
            votingRecoveryRepository.setPendingKeystoneRouteStage(
                accountUuid = accountUuid,
                roundId = args.roundIdHex,
                routeStage = VotingKeystoneRouteStage.SIGN
            )
            delay(350.milliseconds)
            navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
        }
    }

    private fun onCloseBottomSheetClick() {
        isBottomSheetVisible.update { false }
    }

    private fun onCloseSkipBottomSheetClick() {
        isSkipBottomSheetVisible.update { false }
    }

    fun onScreenBack() {
        navigationRouter.back()
    }

    private fun onCancelClick() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            votingRecoveryRepository.setPendingKeystoneRouteStage(
                accountUuid = accountUuid,
                roundId = args.roundIdHex,
                routeStage = VotingKeystoneRouteStage.SIGN
            )
            navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
        }
    }

    private fun onSignTransactionClick() {
        val bundle = signingBundle ?: return
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            votingRecoveryRepository.setPendingKeystoneRouteStage(
                accountUuid = accountUuid,
                roundId = bundle.roundId,
                routeStage = VotingKeystoneRouteStage.SCAN
            )
            navigationRouter.forward(
                ScanKeystoneVotingPCZTRequest(
                    roundIdHex = bundle.roundId,
                    bundleIndex = bundle.bundleIndex,
                    actionIndex = bundle.actionIndex
                )
            )
        }
    }

    private fun onSkipRemainingClick() {
        isSkipBottomSheetVisible.value = true
    }

    private fun onConfirmSkipRemainingClick() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value ?: return@launch
            isSkipBottomSheetVisible.value = false
            runCatching {
                skipRemainingKeystoneBundles(
                    accountUuid = accountUuid,
                    roundId = args.roundIdHex
                )
            }.onSuccess {
                navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
            }.onFailure { throwable ->
                Log.e("SignKeystoneVoting", "Failed to skip Keystone voting bundles", throwable)
                errorSheetState.value =
                    ZashiConfirmationState.error(
                        onPrimary = ::onConfirmSkipRemainingClick,
                        onBack = { errorSheetState.value = null }
                    )
            }
        }
    }

    private fun loadSigningBundle() {
        viewModelScope.launch {
            isLoading.value = true
            errorSheetState.value = null
            currentQrPart.value = null
            signingBundle = null
            signingBundleState.value = null
            val accountUuid = selectedAccountUuid.filterNotNull().first()
            recovery.filterNotNull().first()
            runCatching { createVotingKeystonePcztEncoder(accountUuid, args.roundIdHex) }
                .onSuccess { bundle ->
                    signingBundle = bundle
                    signingBundleState.value = bundle
                    currentQrPart.value = bundle.encoder.nextPart()
                }.onFailure { throwable ->
                    if (throwable is VotingKeystoneResumeSubmissionException) {
                        Log.i(
                            "SignKeystoneVoting",
                            "Returning to vote confirmation for ${args.roundIdHex}: ${throwable.message}"
                        )
                        navigationRouter.backTo(VoteConfirmSubmissionArgs::class)
                        return@onFailure
                    }
                    Log.e(
                        "SignKeystoneVoting",
                        "Failed to create Keystone voting QR bundle for ${args.roundIdHex}",
                        throwable
                    )
                    errorSheetState.value =
                        ZashiConfirmationState.error(
                            onPrimary = ::loadSigningBundle,
                            onBack = { errorSheetState.value = null }
                        )
                }
            isLoading.value = false
        }
    }

    private fun VotingRecoverySnapshot.toSkipBottomSheetState(): SkipKeystoneBundlesBottomSheetState? {
        val bundleCount = bundleCount ?: return null
        val signedCount = signedBundlePrefixCount(bundleCount)
        val remainingCount = bundleCount - signedCount
        if (signedCount <= 0 || remainingCount <= 0 || bundleWeights.size < bundleCount) {
            return null
        }

        val signedWeight = bundleWeights.take(signedCount).sum()
        val skippedWeight = bundleWeights.subList(signedCount, bundleCount).sum()

        return SkipKeystoneBundlesBottomSheetState(
            message =
                stringRes(
                    R.string.sign_keystone_voting_skip_remaining_message,
                    signedWeight.toVotingWeightLabel(),
                    skippedWeight.toVotingWeightLabel()
                ),
            onBack = ::onCloseSkipBottomSheetClick,
            skipButton =
                ButtonState(
                    text = stringRes(R.string.sign_keystone_voting_skip_remaining_confirm),
                    style = ButtonStyle.DESTRUCTIVE2,
                    onClick = ::onConfirmSkipRemainingClick
                ),
            cancelButton =
                ButtonState(
                    text = stringRes(R.string.sign_keystone_voting_cancel),
                    onClick = ::onCloseSkipBottomSheetClick
                )
        )
    }

    private fun VotingRecoverySnapshot.signedBundlePrefixCount(bundleCount: Int): Int =
        (0 until bundleCount)
            .takeWhile { bundleIndex -> bundleIndex in keystoneBundleSignatures }
            .count()

    private fun VotingKeystoneScanNotice.toScanNoticeSheet() =
        ZashiConfirmationState.error(
            title = stringRes(R.string.scan_keystone_voting_rejected_title),
            message = toScanNoticeMessage(),
            primaryText = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
            secondaryText = null,
            primaryStyle = ButtonStyle.PRIMARY,
            onPrimary = ::onDismissScanNotice,
            onBack = ::onDismissScanNotice
        )

    private fun onDismissScanNotice() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.filterNotNull().first()
            votingRecoveryRepository.clearPendingKeystoneScanNotice(
                accountUuid = accountUuid,
                roundId = args.roundIdHex
            )
        }
    }

    private fun VotingKeystoneScanNotice.toScanNoticeMessage() =
        when (type) {
            VotingKeystoneScanNoticeType.DUPLICATE_SIGNATURE -> {
                stringRes(
                    R.string.scan_keystone_voting_duplicate_signature,
                    bundleNumber,
                    bundleCount
                )
            }

            VotingKeystoneScanNoticeType.WRONG_SIGNATURE -> {
                stringRes(
                    R.string.scan_keystone_voting_wrong_signature,
                    bundleNumber,
                    bundleCount
                )
            }
        }

    private fun Long.toVotingWeightLabel(): String {
        // Keystone bundle weights are quantized in 0.125 ZEC increments, so three decimals are exact here.
        return String.format(Locale.US, "%.3f ZEC", this / ZATOSHI_PER_ZEC)
    }

    private companion object {
        const val ZATOSHI_PER_ZEC = 100_000_000.0
    }
}
