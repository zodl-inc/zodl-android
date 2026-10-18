package com.flexa.core.shared

import com.flexa.core.entity.error.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ApiErrorHandler {

    private val _error = MutableStateFlow<ApiError?>(null)
    val error = _error.asStateFlow()

    fun setError(e:  Throwable?) {
        _error.value = ApiErrorAdapter.parseError(e)
    }

    fun setApiError(e: ApiException) {
        _error.value = ApiError.ReportEntity(
            message = e.message,
            traceId = e.traceId
        )
    }

    fun clearError() {
        _error.value = null
    }
}
