package co.electriccoin.zcash.ui.screen.swap.slippage

import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.usecase.GetSlippageUseCase
import co.electriccoin.zcash.ui.common.usecase.SetSlippageUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.MathContext

@Suppress("TooManyFunctions")
class SwapSlippageVM(
    getSlippage: GetSlippageUseCase,
    private val args: SwapSlippageArgs,
    private val setSlippage: SetSlippageUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val fiatAmount = args.fiatAmount?.toBigDecimal()

    private val slippageSelection: MutableStateFlow<BigDecimal?> = MutableStateFlow(getSlippage())

    private val slippagePickerState =
        slippageSelection
            .map {
                createSlippagePickerState(it)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = createSlippagePickerState(slippageSelection.value)
            )

    private val slippageInfoState =
        slippageSelection
            .map {
                createSlippageInfoState(it)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = createSlippageInfoState(slippageSelection.value)
            )

    private val confirmButtonState =
        slippageSelection
            .map {
                createButtonState(it)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = createButtonState(slippageSelection.value)
            )

    private val slippageWarning =
        slippageSelection
            .map {
                if (it != null && it < BigDecimal("2")) {
                    styledStringResource(
                        R.string.swap_slippage_low_warning,
                        StringResourceColor.WARNING,
                        null,
                        styledStringResource(
                            R.string.swap_slippage_low_warning_1,
                            color = StringResourceColor.WARNING,
                            fontWeight = FontWeight.Bold
                        ),
                        styledStringResource(
                            R.string.swap_slippage_low_warning_2,
                            color = StringResourceColor.WARNING,
                            fontWeight = FontWeight.Bold
                        ),
                        styledStringResource(
                            R.string.swap_slippage_low_warning_3,
                            color = StringResourceColor.WARNING,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    null
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null
            )

    val state: StateFlow<SwapSlippageState> =
        combine(
            slippagePickerState,
            slippageInfoState,
            slippageWarning,
            confirmButtonState
        ) { slippagePickerState, slippageInfoState, slippageWarning, confirmButtonState ->
            createState(slippagePickerState, slippageInfoState, slippageWarning, confirmButtonState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue =
                createState(
                    slippagePickerState = slippagePickerState.value,
                    slippageInfoState = slippageInfoState.value,
                    slippageWarning = slippageWarning.value,
                    confirmButtonState = confirmButtonState.value
                )
        )

    private fun createState(
        slippagePickerState: SlippagePickerState,
        slippageInfoState: SwapSlippageInfoState?,
        slippageWarning: StyledStringResource?,
        confirmButtonState: ButtonState
    ) = SwapSlippageState(
        picker = slippagePickerState,
        info = slippageInfoState,
        warning = slippageWarning,
        primary = confirmButtonState,
        onBack = ::onBack,
        footer = stringRes(R.string.pay_slippage_footer).takeIf { args.mode == EXACT_OUTPUT }
    )

    private fun createButtonState(amount: BigDecimal?) =
        ButtonState(
            text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_confirm),
            isEnabled = amount != null,
            onClick = ::onConfirmClick
        )

    @Suppress("MagicNumber")
    private fun createSlippageInfoState(percent: BigDecimal?): SwapSlippageInfoState? {
        if (percent == null) return null
        val percentString = stringResByNumber(percent, minDecimals = 0) + stringRes("%")
        val result =
            when {
                percent > BigDecimal("30") -> stringRes(R.string.swap_slippage_max_threshold)
                fiatAmount == null ->
                    when (args.mode) {
                        EXACT_INPUT -> stringRes(R.string.swap_slippage_info, percentString)
                        EXACT_OUTPUT -> stringRes(R.string.pay_slippage_info, percentString)
                    }

                else -> {
                    val slippageFiat =
                        fiatAmount.multiply(
                            percent.divide(BigDecimal(100), MathContext.DECIMAL128),
                            MathContext.DECIMAL128
                        )

                    val slippageFiatString = stringResByDynamicCurrencyNumber(slippageFiat, FiatCurrency.USD.symbol)
                    val infoString = percentString + stringRes(" (") + slippageFiatString + stringRes(")")
                    when (args.mode) {
                        EXACT_INPUT -> stringRes(R.string.swap_slippage_info, infoString)
                        EXACT_OUTPUT -> stringRes(R.string.pay_slippage_info, infoString)
                    }
                }
            }

        return SwapSlippageInfoState(
            title = result,
            mode =
                when {
                    percent <= BigDecimal("1") -> SwapSlippageInfoState.Mode.LOW
                    percent <= BigDecimal("2") -> SwapSlippageInfoState.Mode.MEDIUM
                    else -> SwapSlippageInfoState.Mode.HIGH
                }
        )
    }

    private fun createSlippagePickerState(amount: BigDecimal?) =
        SlippagePickerState(
            amount = amount,
            onAmountChange = ::onSlippageChanged
        )

    private fun onSlippageChanged(new: BigDecimal?) = slippageSelection.update { new }

    private fun onConfirmClick() = slippageSelection.value?.let { setSlippage(it) }

    private fun onBack() = navigationRouter.back()
}
