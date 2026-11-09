package com.flexa.identity.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.entity.TokensResponse
import com.flexa.core.shared.ApiErrorHandler
import com.flexa.identity.domain.IIdentityInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class MainViewModel(
    private val interactor: IIdentityInteractor = com.flexa.identity.Identity.identityInteractor
) : ViewModel() {

    val icons = MutableStateFlow<List<String>>(emptyList())
    val state = MutableStateFlow<State>(State.General)
    private val _progress = MutableStateFlow(false)
    val progress: StateFlow<Boolean> = _progress
    val errorHandler = ApiErrorHandler()

    init {
        initMockIcons()
    }

    fun tokens(email: String) {
        viewModelScope.launch {
            runCatching {
                _progress.value = true
                val res = interactor.tokens(email)
                if (res is TokensResponse.Success)
                    interactor.saveEmail(email)
                res
            }.onSuccess { result ->
                try {
                    when (result) {
                        is TokensResponse.Success -> state.value = State.Success(result)
                        is TokensResponse.Error -> state.value = State.Error(result)
                    }
                } finally {
                    _progress.value = false
                }
            }.onFailure {
                _progress.value = false
                errorHandler.setError(it)
            }
        }
    }

    fun setState(state: State) {
        this.state.value = state
    }

    private fun initMockIcons() {
        viewModelScope.launch(Dispatchers.IO) {
            icons.value = arrayListOf<String>().apply {
                repeat(16) {
                    add("https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png")
                }
            }
        }
    }

    sealed class State {
        data object Info : State()
        data object General : State()
        class Success(val data: TokensResponse.Success) : State()
        class Error(val data: TokensResponse.Error) : State()
    }
}
