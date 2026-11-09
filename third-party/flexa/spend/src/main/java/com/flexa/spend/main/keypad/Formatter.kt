package com.flexa.spend.main.keypad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal const val AMOUNT_UNSPECIFIED = Double.MIN_VALUE

class Formatter(
    maxFraction: Byte = 2,
    private val prefix: String = "$",
) {

    val dataAsFlow = MutableStateFlow<String?>(null)
    private val data = StringBuilder()
    private val fractionRegex = "\\.\\d{$maxFraction}".toRegex()
    private val oneDecimalRegex = "\\.\\d$".toRegex()

    fun getInputData(button: KeypadButton): String {
        val dataString = StringBuilder(data.toString())
        when (button) {
            is Backspace -> {
                if (dataString.isNotEmpty()) dataString.deleteCharAt(dataString.length - 1)
            }

            is Point -> {
                when {
                    dataString.isEmpty() -> dataString.append("0${button.symbol}")
                    !dataString.contains(button.symbol) -> dataString.append(button.symbol)
                }
            }

            is Symbol -> {
                if (!containsFractions()) dataString.append(button.symbol)
            }
        }
        return dataString.toString()
    }

    fun getInputState(
        maxAmount: Double,
        minAmount: Double, data: String
    ): InputState {
        val amount = data.toDoubleOrNull() ?: AMOUNT_UNSPECIFIED
        return getInputState(
            minAmount = minAmount, maxAmount = maxAmount,
            amount = amount
        )
    }

    fun getInputState(
        maxAmount: Double,
        minAmount: Double,
        amount: Double
    ): InputState {
        return when {
            maxAmount == 0.0 -> {
                if (amount == AMOUNT_UNSPECIFIED) InputState.Unspecified
                else InputState.Fine
            }

            amount == AMOUNT_UNSPECIFIED -> InputState.Unspecified
            amount < minAmount -> InputState.Min(System.currentTimeMillis())
            amount > maxAmount -> InputState.Max(System.currentTimeMillis())
            else -> {
                if (amount == AMOUNT_UNSPECIFIED) InputState.Unspecified
                else InputState.Fine
            }
        }
    }

    fun append(button: KeypadButton) {
        when (button) {
            is Backspace -> {
                if (data.isNotEmpty()) data.deleteCharAt(data.length - 1)
            }

            is Point -> {
                when {
                    data.isEmpty() -> data.append("0${button.symbol}")
                    !data.contains(button.symbol) -> data.append(button.symbol)
                }
            }

            is Symbol -> {
                if (!containsFractions()) data.append(button.symbol)
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            dataAsFlow.emit(data.toString())
        }
    }

    fun getText(): String = data.toString()

    fun getPrefix(): String = prefix

    fun getSuffix(): String {
        return when {
            data.isEmpty() -> "0"
            data.endsWith(".") -> "00"
            hasOneDecimalDigit() -> "0"
            else -> ""
        }
    }

    private fun containsFractions(): Boolean = fractionRegex.find(data) != null

    private fun hasOneDecimalDigit(): Boolean =
        oneDecimalRegex.containsMatchIn(data)
}
