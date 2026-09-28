package co.electriccoin.zcash.ui.screen.exportvk.detail

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.usecase.DeleteSharedQRImagesUseCase
import co.electriccoin.zcash.ui.common.usecase.GetVKUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.SegmentedControlItem
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
internal class VKDetailVM(
    private val args: VKDetailArgs,
    getVK: GetVKUseCase,
    private val shareQR: ShareQRUseCase,
    private val deleteSharedQRImages: DeleteSharedQRImagesUseCase,
    private val biometricRepository: BiometricRepository,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val key = MutableStateFlow<String?>(null)

    private val isRevealed = MutableStateFlow(false)

    private val tab = MutableStateFlow(Tab.QR)

    val state =
        combine(key, isRevealed, tab) { key, isRevealed, tab ->
            key?.let { createState(it, isRevealed, tab) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            val loaded = getVK.observe(args.type).first()
            if (loaded == null) {
                navigationRouter.back()
            } else {
                key.update { loaded }
            }
        }
    }

    private fun createState(
        key: String,
        isRevealed: Boolean,
        tab: Tab
    ): VKDetailState =
        VKDetailState(
            title = stringRes(args.type.titleRes),
            subtitle = stringRes(args.type.subtitleRes),
            tabs =
                Tab.entries.map { item ->
                    SegmentedControlItem(
                        text = stringRes(item.titleRes),
                        isSelected = item == tab,
                        onClick = { onTabClick(item) }
                    )
                },
            content = createContent(key, isRevealed, tab),
            disclaimer = stringRes(disclaimerRes(tab)),
            secondaryButton = createShareButton(isRevealed),
            primaryButton = createRevealButton(isRevealed),
            onBack = ::onBack
        )

    private fun createContent(
        key: String,
        isRevealed: Boolean,
        tab: Tab
    ): VKContentState =
        when (tab) {
            Tab.QR -> {
                VKContentState.Qr(
                    data = if (isRevealed) key else HIDDEN_KEY_PLACEHOLDER,
                    contentDescription = stringRes(R.string.exportViewingKey_hidden_content_description),
                    hiddenLabel = stringRes(R.string.exportViewingKey_reveal),
                    isRevealed = isRevealed
                )
            }

            Tab.KEY -> {
                VKContentState.Key(
                    data = if (isRevealed) key else HIDDEN_KEY_PLACEHOLDER,
                    contentDescription = stringRes(R.string.exportViewingKey_hidden_content_description),
                    hiddenLabel = stringRes(R.string.exportViewingKey_reveal),
                    isRevealed = isRevealed
                )
            }
        }

    private fun createShareButton(isRevealed: Boolean) =
        ButtonState(
            text = stringRes(R.string.exportViewingKey_share),
            icon = R.drawable.ic_share,
            isEnabled = isRevealed,
            onClick = ::onShareClick
        )

    private fun createRevealButton(isRevealed: Boolean) =
        ButtonState(
            text =
                if (isRevealed) {
                    stringRes(R.string.exportViewingKey_hide)
                } else {
                    stringRes(R.string.exportViewingKey_reveal)
                },
            icon = if (isRevealed) R.drawable.ic_seed_hide else R.drawable.ic_seed_show,
            hapticFeedbackType = if (isRevealed) HapticFeedbackType.Confirm else null,
            onClick = ::onRevealClick
        )

    private fun disclaimerRes(tab: Tab) =
        when (tab) {
            Tab.QR -> {
                when (args.type) {
                    VKType.INCOMING -> R.string.exportViewingKey_disclaimer_qr_incoming
                    VKType.FULL -> R.string.exportViewingKey_disclaimer_qr_full
                }
            }

            Tab.KEY -> {
                when (args.type) {
                    VKType.INCOMING -> R.string.exportViewingKey_disclaimer_key_incoming
                    VKType.FULL -> R.string.exportViewingKey_disclaimer_key_full
                }
            }
        }

    private fun onTabClick(selected: Tab) = tab.update { selected }

    private fun onRevealClick() =
        viewModelScope.launch {
            if (!isRevealed.value) {
                try {
                    biometricRepository.requestBiometrics(
                        BiometricRequest(
                            message =
                                stringRes(
                                    R.string.authentication_system_ui_subtitle,
                                    stringRes(R.string.exportViewingKey_auth_use_case)
                                )
                        )
                    )
                    isRevealed.update { true }
                } catch (_: BiometricsFailureException) {
                    isRevealed.update { false }
                } catch (_: BiometricsCancelledException) {
                    isRevealed.update { false }
                }
            } else {
                isRevealed.update { false }
            }
        }

    private fun onShareClick() =
        viewModelScope.launch {
            val key = key.value ?: return@launch
            if (!isRevealed.value) return@launch
            shareQR(
                qrData = key,
                shareText = stringRes(key),
                sharePickerText = stringRes(R.string.exportViewingKey_share_picker_title),
                filenamePrefix = VK_QR_FILENAME_PREFIX,
                centerIcon = null,
            )
        }

    override fun onCleared() {
        deleteSharedQRImages(VK_QR_FILENAME_PREFIX)
        super.onCleared()
    }

    private fun onBack() = navigationRouter.back()

    private enum class Tab {
        QR,
        KEY
    }

    private val Tab.titleRes: Int
        get() =
            when (this) {
                Tab.QR -> R.string.exportViewingKey_tab_qr
                Tab.KEY -> R.string.exportViewingKey_tab_key
            }

    private val VKType.titleRes: Int
        get() =
            when (this) {
                VKType.INCOMING -> R.string.exportViewingKey_detail_title_incoming
                VKType.FULL -> R.string.exportViewingKey_detail_title_full
            }

    private val VKType.subtitleRes: Int
        get() =
            when (this) {
                VKType.INCOMING -> R.string.exportViewingKey_detail_subtitle_incoming
                VKType.FULL -> R.string.exportViewingKey_detail_subtitle_full
            }
}

/**
 * Stands in for the key in the state while it is hidden, so the real key never reaches the composition before
 * biometrics pass (MOB-1376 rule, see `ZashiSeedText`); shaped like a key so the blurred picture looks right.
 */
private val HIDDEN_KEY_PLACEHOLDER = "*".repeat(HIDDEN_KEY_PLACEHOLDER_LENGTH)

private const val HIDDEN_KEY_PLACEHOLDER_LENGTH = 300

private const val VK_QR_FILENAME_PREFIX = "zodl_viewing_key_qr_"
