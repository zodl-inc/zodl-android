package co.electriccoin.zcash.ui.screen.advancedsettings.debug.orchardbalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.MockOrchardBalanceRepository
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class DebugOrchardBalanceVM(
    private val mockBalanceRepository: MockOrchardBalanceRepository,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    private val zecInput = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)

    val state =
        combine(mockBalanceRepository.observe(), zecInput, error) { balanceZatoshi, input, err ->
            createState(balanceZatoshi, input, err)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = createState(0L, "", null),
        )

    private fun createState(
        balanceZatoshi: Long,
        input: String,
        err: String?,
    ) = DebugOrchardBalanceState(
        currentBalance = stringRes(Zatoshi(balanceZatoshi)),
        zecInput =
            TextFieldState(
                value = stringRes(input),
                error = err?.let { stringRes(it) },
                onValueChange = ::onZecInputChanged,
            ),
        setBalance = ButtonState(text = stringRes("Set Balance"), onClick = ::onSetBalanceClick),
        onBack = ::onBack,
    )

    private fun onZecInputChanged(newValue: String) {
        zecInput.update { newValue }
        error.update { null }
    }

    private fun onSetBalanceClick() {
        val zec = zecInput.value.toBigDecimalOrNull()
        if (zec == null || zec < BigDecimal.ZERO) {
            error.update { "Enter a valid non-negative ZEC amount, e.g. 123.23" }
            return
        }
        val zatoshi = zec.multiply(ZATOSHI_PER_ZEC).setScale(0, RoundingMode.HALF_UP).toLong()
        viewModelScope.launch { mockBalanceRepository.set(zatoshi) }
    }

    private fun onBack() = navigationRouter.back()

    companion object {
        private val ZATOSHI_PER_ZEC = BigDecimal(100_000_000L)
    }
}
