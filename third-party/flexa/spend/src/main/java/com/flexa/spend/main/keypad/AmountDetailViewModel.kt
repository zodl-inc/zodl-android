package com.flexa.spend.main.keypad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.entity.ExchangeRate
import com.flexa.spend.domain.ISpendInteractor
import com.flexa.spend.main.main_screen.Event
import com.flexa.spend.main.main_screen.SpendViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class AmountDetailViewModel(
    val interactor: ISpendInteractor,
) : ViewModel() {

    val exchangeRates = MutableStateFlow<List<ExchangeRate>>(emptyList())

    var percentJob: Job? = null

    init {
        subscribeAppAccounts()
    }

    override fun onCleared() {
        percentJob?.cancel()
        super.onCleared()
    }

    private var subscribeAppAccountsJob: Job? = null
    private fun subscribeAppAccounts() {
        if (subscribeAppAccountsJob?.isActive == true) return
        subscribeAppAccountsJob = viewModelScope.launch {
            kotlin.runCatching { interactor.getDbExchangeRates() }
                .onSuccess { exchangeRates.value = it }
            SpendViewModel.eventFlow.collect { event ->
                if (event is Event.ExchangeRatesUpdate) {
                    exchangeRates.value = event.items
                }
            }
        }
    }
}
