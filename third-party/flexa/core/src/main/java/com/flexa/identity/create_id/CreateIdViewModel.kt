package com.flexa.identity.create_id

import android.content.Context
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.entity.TokensResponse
import com.flexa.core.entity.error.ApiException
import com.flexa.core.shared.ApiErrorHandler
import com.flexa.identity.domain.IIdentityInteractor
import com.flexa.identity.main.UserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CreateIdViewModel(
    private val interactor: IIdentityInteractor = com.flexa.identity.Identity.identityInteractor
) : ViewModel() {
    val state = MutableStateFlow<State>(State.General)
    private val _progress = MutableStateFlow(false)
    val progress: StateFlow<Boolean> = _progress
    private val _error = MutableStateFlow(false)
    val errorHandler = ApiErrorHandler()

    fun accounts(context: Context, userData: UserData) {
        viewModelScope.launch {
            val email = userData.email?.trim() ?: ""
            runCatching {
                _progress.value = true

                val country = getCountryByTelephonyManager(context) ?: getCountryByLocale(context)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val birthday = dateFormat.format(userData.birthday ?: Date())

                val res = interactor.accounts(
                    firstName = userData.firstName?.trim() ?: "",
                    lastName = userData.lastName?.trim() ?: "",
                    email = email, country = country, birthday = birthday
                )
                res
            }
                .onFailure { ex ->
                    _progress.value = false
                    when (ex) {
                        is ApiException -> errorHandler.setApiError(ex)
                        else -> errorHandler.setError(ex)
                    }
                }
                .onSuccess { res ->
                    _progress.value = false
                    when (res) {
                        201 -> tokens(email)
                        else -> _error.value = true
                    }
                }
        }
    }

    fun clearError() {
        _error.value = false
    }

    private fun tokens(email: String) {
        viewModelScope.launch {
            runCatching {
                val res = interactor.tokens(email)
                if (res is TokensResponse.Success)
                    interactor.saveEmail(email)
                res
            }.onSuccess { result ->
                _progress.value = false
                if (result is TokensResponse.Success) {
                    state.value = State.Success(result)
                }
            }.onFailure { ex ->
                _progress.value = false
                when (ex) {
                    is ApiException -> errorHandler.setApiError(ex)
                    else -> errorHandler.setError(ex)
                }
            }
        }
    }


    private fun getCountryByTelephonyManager(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var countryCode = tm.networkCountryIso
        if (countryCode.isNullOrEmpty()) {
            countryCode = tm.simCountryIso
        }
        return countryCode?.uppercase()
    }

    private fun getCountryByLocale(context: Context): String {
        val locale: Locale = context.resources.configuration.locales[0]
        return locale.country
    }

    sealed class State {
        data object General : State()
        class Success(val data: TokensResponse.Success) : State()
    }
}
