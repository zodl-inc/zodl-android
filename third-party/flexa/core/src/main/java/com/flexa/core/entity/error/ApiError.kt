package com.flexa.core.entity.error

data class ApiException(
    val code: Int? = null,
    override val message: String? = null,
    val type: String? = null,
    val traceId: String? = null,
) : Exception(message)
