package com.flexa.core.domain.rest

import com.flexa.core.entity.Account
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.ExchangeRatesResponse
import com.flexa.core.entity.OneTimeKeyResponse
import com.flexa.core.entity.SseEvent
import com.flexa.core.entity.TokenPatch
import com.flexa.core.entity.TokensResponse
import com.flexa.core.entity.TransactionFee
import com.flexa.core.shared.Asset
import com.flexa.core.shared.AssetsResponse
import com.flexa.core.shared.BrandsResponse
import com.flexa.identity.create_id.AccountsRequest
import kotlinx.coroutines.flow.Flow

interface IRestRepository {

    suspend fun tokens(email: String, challenge: String): TokensResponse
    suspend fun accounts(request: AccountsRequest): Int
    suspend fun patchTokens(
        id: String, verifier: String, challenge: String,
        code: String? = null, link: String? = null
    ): TokenPatch

    suspend fun getOneTimeKeys(assetIds: List<String>): OneTimeKeyResponse
    suspend fun getAssets(pageSize: Int, startingAfter: String?): AssetsResponse
    suspend fun getAssetById(assetId: String): Asset
    suspend fun getAccount(): Account
    suspend fun deleteToken(tokenId: String): Int
    suspend fun deleteAccount(): Int
    suspend fun deleteNotification(id: String)
    suspend fun listenEvents(lastEventId: String?): Flow<SseEvent>
    suspend fun getBrands(legacyOnly: Boolean?, startingAfter: String?): BrandsResponse
    suspend fun createCommerceSession(
        brandId: String, amount: String, assetId: String, paymentAssetId: String
    ): CommerceSession.Data

    suspend fun closeCommerceSession(commerceSessionId: String): CommerceSession.Data
    suspend fun confirmTransaction(
        commerceSessionId: String, txSignature: String
    ): String

    suspend fun patchCommerceSession(
        commerceSessionId: String, paymentAssetId: String
    ): String

    suspend fun approveCommerceSession(commerceSessionId: String): Int

    suspend fun getCommerceSession(sessionId: String): CommerceSession.Data

    suspend fun getExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): ExchangeRatesResponse

    suspend fun getTransactionFees(assetIds: List<String>): List<TransactionFee>
}
