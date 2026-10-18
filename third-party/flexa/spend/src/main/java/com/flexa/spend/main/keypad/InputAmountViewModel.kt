package com.flexa.spend.main.keypad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.shared.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_AMOUNT_DELAY = 5000L
internal const val INPUT_DELAY = 1000L

internal class InputAmountViewModel : ViewModel() {

    val formatter = Formatter()
    val done = MutableStateFlow(false)
    private val _inputState = MutableSharedFlow<InputState>()
    val inputState = _inputState.asSharedFlow()
    private val _inputStateDelayed = MutableSharedFlow<InputState>()
    val inputStateDelayed = _inputStateDelayed.asSharedFlow()
    private val _showBalanceRestrictions = MutableStateFlow<Boolean>(false)
    val showBalanceRestrictions = _showBalanceRestrictions.asStateFlow()
    private var maxAmount = 0.0
    private var minAmount = 0.0
    private var amountVerifyJob: Job? = null
    private var stateJob: Job? = null

    init {
        listenInput()
    }

    override fun onCleared() {
        clean()
        super.onCleared()
    }

    fun showBalanceRestrictions(show: Boolean) {
        _showBalanceRestrictions.value = show
    }

    fun setMinMaxValue(brand: Brand?) {
        maxAmount = brand?.legacyFlexcodes?.firstOrNull()?.amount?.maximum?.toDoubleOrNull() ?: 0.0
        minAmount = brand?.legacyFlexcodes?.firstOrNull()?.amount?.minimum?.toDoubleOrNull() ?: 0.0
    }

    fun getInputState(data: String): InputState {
        val amount = data.toDoubleOrNull() ?: AMOUNT_UNSPECIFIED
        return formatter.getInputState(
            minAmount = minAmount, maxAmount = maxAmount,
            amount = amount
        )
    }

    fun setInputState(inputState: InputState) {
        stateJob?.cancel()
        stateJob = viewModelScope.launch {
            if (isActive) {
                _inputState.emit(inputState)
                _inputStateDelayed.emit(inputState)
            }
            if (inputState is InputState.Max) {
                delay(MAX_AMOUNT_DELAY)
                if (isActive) {
                    _inputState.emit(InputState.Fine)
                    _inputStateDelayed.emit(InputState.Fine)
                }
            }
        }
    }

    private fun clean() {
        viewModelScope.launch {
            amountVerifyJob?.cancel()
            stateJob?.cancel()
            _inputState.emit(InputState.Unspecified)
        }
    }

    private fun checkMinMaxValue(amount: Double) {
        amountVerifyJob = viewModelScope.launch {
            val inputState = formatter.getInputState(
                minAmount = minAmount, maxAmount = maxAmount,
                amount = amount
            )
            _inputState.emit(inputState)

            when (inputState) {
                is InputState.Min -> {
                    delay(INPUT_DELAY)
                    if (isActive) {
                        _inputStateDelayed.emit(InputState.Min(System.currentTimeMillis()))
                    }
                }

                else -> { _inputStateDelayed.emit(inputState)}
            }
        }
    }

    private fun listenInput() {
        viewModelScope.launch {
            formatter.dataAsFlow
                .drop(1)
                .map { it?.toDoubleOrNull() ?: AMOUNT_UNSPECIFIED }
                .onEach { amountVerifyJob?.cancel() }
                .collect { a -> checkMinMaxValue(a) }
        }
    }
}

sealed class InputState {
    class Min(timestamp: Long) : InputState()

    class Max(timestamp: Long) : InputState()

    data object Fine : InputState()
    data object Unspecified : InputState()
}

