package com.flexa.core.shared

import com.flexa.R
import com.flexa.core.Flexa
import com.flexa.core.data.rest.ErrorParser


class ApiErrorAdapter {

    companion object {

        fun parseError(throwable: Throwable?): ApiError {

            val errorBundle = ErrorParser.parseError(throwable)

            val title = when(errorBundle.code) {
                500 -> {"Technical error"}
                else -> {Flexa.requiredContext.getString(R.string.connection_problem)}
            }
            val message = errorBundle.message

            return when(errorBundle.code) {
                500 -> {
                    ApiError.ReportEntity(traceId = "", data = throwable?.message)
                }
                else -> {
                    ApiError.InfoEntity(title = title, message = message)
                }
            }
        }
    }
}
