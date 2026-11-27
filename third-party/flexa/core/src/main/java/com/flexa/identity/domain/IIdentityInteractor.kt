package com.flexa.identity.domain

import com.flexa.core.entity.Account
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.PutAppAccountsResponse
import com.flexa.core.entity.TokenPatch
import com.flexa.core.entity.TokensResponse
import com.flexa.core.shared.Asset
import com.flexa.core.shared.AssetAccount
import com.flexa.core.shared.AssetsResponse
import com.flexa.core.shared.Brand

internal interface IIdentityInteractor {
    suspend fun tokens(email: String): TokensResponse
    suspend fun accounts(
        firstName: String,
        lastName: String,
        email: String,
        country: String,
        birthday: String
    ): Int

    suspend fun tokenPatch(code: String? = null, link: String? = null): TokenPatch
    suspend fun getAccount(): Account
    suspend fun getAppAccounts(): List<com.flexa.core.entity.AppAccount>?
    suspend fun putAppAccounts(accounts: List<AssetAccount>): PutAppAccountsResponse
    suspend fun saveAppAccounts(account: List<com.flexa.core.entity.AppAccount>)
    suspend fun saveEmail(email: String)
    suspend fun getEmail(): String?
    suspend fun clearLoginData()

    suspend fun getAllAssets(pageSize: Int): List<Asset>
    suspend fun getAssets(pageSize: Int, nextPageToken: String? = null): AssetsResponse
    suspend fun deleteAssets()
    suspend fun saveAssets(items: List<Asset>)

    suspend fun getBrands(legacyOnly: Boolean? = null): List<Brand>
    suspend fun saveBrands(items: List<Brand>)
    suspend fun deleteBrands()

    suspend fun getExchangeRates(assetIds: List<String>, unitOfAccount: String): List<ExchangeRate>
    suspend fun saveExchangeRates(items: List<ExchangeRate>)
    suspend fun deleteExchangeRates()
}
