package co.electriccoin.zcash.ui.screen.voting.signkeystone

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRouteStage
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSigningBundle
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.CreateVotingKeystonePcztEncoderUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SkipRemainingKeystoneBundlesUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.addressbook.ADDRESS_MAX_LENGTH
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionBottomSheetState
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionState
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.ZashiAccountInfoListItemState
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.scankeystone.ScanKeystoneVotingPCZTRequest
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        observeSelectedWalletAccount.require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private val isLoading = MutableStateFlow(true)
    private val errorMessage = MutableStateFlow<StringResource?>(null)
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
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                initialValue = null
            )

    val loading: StateFlow<Boolean> = isLoading
    val error: StateFlow<StringResource?> = errorMessage

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
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                initialValue = null
            )

    val skipBottomSheetState =
        combine(
            isSkipBottomSheetVisible,
            recovery
        ) { isVisible, recovery ->
            recovery
                ?.takeIf { isVisible }
                ?.toSkipBottomSheetState()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            initialValue = null
        )

    val state: StateFlow<SignKeystoneTransactionState?> =
        combine(
            observeSelectedWalletAccount.require(),
            currentQrPart,
            signingBundleState,
            recovery
        ) { wallet, qrData, bundle, recovery ->
            bundle?.let {
                SignKeystoneTransactionState(
                    barTitle = stringRes(R.string.sign_keystone_voting_bar_title),
                    title = stringRes(R.string.sign_keystone_transaction_title),
                    subtitle = stringRes(R.string.sign_keystone_voting_subtitle),
                    accountInfo =
                        ZashiAccountInfoListItemState(
                            icon = wallet.icon,
                            title = wallet.name,
                            subtitle = stringRes("${wallet.unified.address.address.take(ADDRESS_MAX_LENGTH)}...")
                        ),
                    badgeText = stringRes(R.string.sign_keystone_transaction_badge),
                    generateNextQrCode = { currentQrPart.update { signingBundle?.encoder?.nextPart() } },
                    qrData = qrData,
                    positiveButton =
                        ButtonState(
                            text = stringRes(R.string.sign_keystone_voting_scan_signature),
                            onClick = ::onSignTransactionClick
                        ),
                    negativeButton =
                        ButtonState(
                            text = stringRes(R.string.sign_keystone_voting_cancel),
                            onClick = ::onCancelClick
                        ),
                    secondaryButton = recovery?.toSkipRemainingButton(),
                    onBack = ::onCancelClick,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            initialValue = null
        )

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

    fun onRetry() {
        if (isLoading.value) {
            return
        }
        loadSigningBundle()
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
                signingBundle = null
                signingBundleState.value = null
                errorMessage.value = throwable.message
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::stringRes)
                    ?: stringRes(R.string.sign_keystone_voting_error_skip_remaining)
            }
        }
    }

    private fun loadSigningBundle() {
        viewModelScope.launch {
            val accountUuid = selectedAccountUuid.value
            if (accountUuid == null) {
                errorMessage.value = stringRes(R.string.sign_keystone_voting_error_no_account)
                isLoading.value = false
                return@launch
            }
            isLoading.value = true
            errorMessage.value = null
            currentQrPart.value = null
            signingBundle = null
            signingBundleState.value = null
            runCatching { createVotingKeystonePcztEncoder(accountUuid, args.roundIdHex) }
                .onSuccess { bundle ->
                    signingBundle = bundle
                    signingBundleState.value = bundle
                    currentQrPart.value = bundle.encoder.nextPart()
                }.onFailure { throwable ->
                    Log.e(
                        "SignKeystoneVoting",
                        "Failed to create Keystone voting QR bundle for ${args.roundIdHex}",
                        throwable
                    )
                    errorMessage.value =
                        throwable.message
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::stringRes)
                            ?: stringRes(R.string.sign_keystone_voting_error_prepare_request)
                }
            isLoading.value = false
        }
    }

    private fun VotingRecoverySnapshot.toSkipRemainingButton(): ButtonState? {
        val bundleCount = bundleCount ?: return null
        val signedCount = signedBundlePrefixCount(bundleCount)
        val remainingCount = bundleCount - signedCount
        if (signedCount <= 0 || remainingCount <= 0 || bundleWeights.size < bundleCount) {
            return null
        }

        val buttonText = if (remainingCount == 1) {
            stringRes(R.string.sign_keystone_voting_skip_remaining_bundle)
        } else {
            stringRes(R.string.sign_keystone_voting_skip_remaining_bundles, remainingCount)
        }

        return ButtonState(
            text = buttonText,
            style = ButtonStyle.SECONDARY,
            onClick = ::onSkipRemainingClick
        )
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
            message = stringRes(
                R.string.sign_keystone_voting_skip_remaining_message,
                signedWeight.toVotingWeightLabel(),
                skippedWeight.toVotingWeightLabel()
            ),
            onBack = ::onCloseSkipBottomSheetClick,
            skipButton = ButtonState(
                text = stringRes(R.string.sign_keystone_voting_skip_remaining_confirm),
                style = ButtonStyle.DESTRUCTIVE2,
                onClick = ::onConfirmSkipRemainingClick
            ),
            cancelButton = ButtonState(
                text = stringRes(R.string.sign_keystone_voting_cancel),
                onClick = ::onCloseSkipBottomSheetClick
            )
        )
    }

    private fun VotingRecoverySnapshot.signedBundlePrefixCount(bundleCount: Int): Int =
        (0 until bundleCount)
            .takeWhile { bundleIndex -> bundleIndex in keystoneBundleSignatures }
            .count()

    private fun Long.toVotingWeightLabel(): String {
        // Keystone bundle weights are quantized in 0.125 ZEC increments, so three decimals are exact here.
        return String.format(Locale.US, "%.3f ZEC", this / ZATOSHI_PER_ZEC)
    }

    private companion object {
        const val ZATOSHI_PER_ZEC = 100_000_000.0
    }
}
