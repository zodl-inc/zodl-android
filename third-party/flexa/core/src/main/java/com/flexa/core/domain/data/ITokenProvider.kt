package com.flexa.core.domain.data

import com.flexa.core.data.rest.HeadersBundle

internal interface ITokenProvider {

    fun dropCache()

    fun getTokenExpiration(): Long

    fun getToken(): String

    fun saveToken(token: String)

    fun getRefreshToken(headersBundle: HeadersBundle): String
}
