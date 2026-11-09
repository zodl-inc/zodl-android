package com.flexa.core.domain.rest

import com.flexa.core.entity.Account
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.ExchangeRatesResponse
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.OneTimeKeyResponse
import com.flexa.core.entity.SseEvent
import com.flexa.core.entity.TokenPatch
import com.flexa.core.entity.TokensResponse
import com.flexa.core.entity.TransactionFee
import com.flexa.core.shared.Asset
import com.flexa.core.shared.AssetsResponse
import com.flexa.identity.create_id.AccountsRequest
import kotlinx.coroutines.flow.Flow

class RestInteractor(
    private val repository: IRestRepository,
) {
    suspend fun tokens(email: String, challenge: String): TokensResponse =
        repository.tokens(email, challenge)

    suspend fun accounts(request: AccountsRequest): Int =
        repository.accounts(request)

    suspend fun tokenPatch(
        id: String, verifier: String, challenge: String, code: String?, link: String?
    ): TokenPatch =
        repository.patchTokens(
            id = id, verifier = verifier, challenge = challenge,
            code = code, link = link
        )

    suspend fun getOneTimeKeys(assetIds: List<String>): OneTimeKeyResponse {
        var startingAfter: String? = null
        val items = ArrayList<OneTimeKey>(assetIds.size)
        var date: String? = null
        do {
            val response = repository.getOneTimeKeys(assetIds)
            startingAfter = response.startingAfter
            date = response.date
            items.addAll(response.data)
        } while (startingAfter != null)
        return OneTimeKeyResponse(date = date ?: "", data = items)
    }

    suspend fun getAssets(pageSize: Int, startingAfter: String?): AssetsResponse =
        repository.getAssets(pageSize, startingAfter)

    suspend fun getAssetById(assetId: String): Asset =
        repository.getAssetById(assetId)

    suspend fun getAccount(): Account = repository.getAccount()

    suspend fun deleteToken(tokenId: String): Int = repository.deleteToken(tokenId)

    suspend fun deleteAccount(): Int = repository.deleteAccount()

    suspend fun deleteNotification(id: String): Unit = repository.deleteNotification(id)

    suspend fun listenEvents(lastEventId: String?): Flow<SseEvent> =
        repository.listenEvents(lastEventId)

    suspend fun getBrands(legacyOnly: Boolean?, startingAfter: String?) =
        repository.getBrands(legacyOnly, startingAfter)

    suspend fun createCommerceSession(
        brandId: String,
        amount: String,
        assetId: String,
        paymentAssetId: String
    ) =
        repository.createCommerceSession(brandId, amount, assetId, paymentAssetId)

    suspend fun closeCommerceSession(commerceSessionId: String) =
        repository.closeCommerceSession(commerceSessionId)

    suspend fun confirmTransaction(
        commerceSessionId: String, txSignature: String
    ) =
        repository.confirmTransaction(commerceSessionId, txSignature)

    suspend fun patchCommerceSession(
        commerceSessionId: String, paymentAssetId: String
    ) = repository.patchCommerceSession(commerceSessionId, paymentAssetId)

    suspend fun approveCommerceSession(commerceSessionId: String) =
        repository.approveCommerceSession(commerceSessionId)

    suspend fun getCommerceSession(sessionId: String) =
        repository.getCommerceSession(sessionId)

    suspend fun getExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): ExchangeRatesResponse {
        val res = ArrayList<ExchangeRate>(assetIds.size)
        val chunkedCollections = assetIds.chunked(20)
        var date: String? = null
        chunkedCollections.forEach { collection ->
            val exchangeRates = repository.getExchangeRates(collection, unitOfAccount)
            date = exchangeRates.date
            res.addAll(exchangeRates.data)
        }
        return ExchangeRatesResponse(date = date, data = res)
    }

    suspend fun getTransactionFees(assetIds: List<String>): List<TransactionFee> {
        val res = ArrayList<TransactionFee>(assetIds.size)
        val chunkedCollections = assetIds.chunked(20)
        chunkedCollections.forEach { collection ->
            val response = repository.getTransactionFees(collection)
            res.addAll(response)
        }
        return res
    }

}
