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

internal class FakeInteractor : IIdentityInteractor {
    override suspend fun getEmail(): String? = null

    override suspend fun clearLoginData() {}
    override suspend fun getAllAssets(pageSize: Int): List<Asset> {
        TODO("Not yet implemented")
    }

    override suspend fun getAssets(pageSize: Int, nextPageToken: String?): AssetsResponse {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAssets() {
        TODO("Not yet implemented")
    }

    override suspend fun saveAssets(items: List<Asset>) {
        TODO("Not yet implemented")
    }

    override suspend fun getBrands(legacyOnly: Boolean?): List<Brand> {
        TODO("Not yet implemented")
    }

    override suspend fun saveBrands(items: List<Brand>) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteBrands() {
        TODO("Not yet implemented")
    }

    override suspend fun getExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): List<ExchangeRate> {
        TODO("Not yet implemented")
    }

    override suspend fun saveExchangeRates(items: List<ExchangeRate>) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteExchangeRates() {
        TODO("Not yet implemented")
    }

    override suspend fun tokens(email: String): TokensResponse = TokensResponse.Success("", "")
    override suspend fun accounts(
        firstName: String,
        lastName: String,
        email: String,
        country: String,
        birthday: String
    ): Int {
        TODO("Not yet implemented")
    }

    override suspend fun tokenPatch(code: String?, link: String?): TokenPatch {
        TODO("Not yet implemented")
    }

    override suspend fun getAccount(): Account {
        TODO("Not yet implemented")
    }

    override suspend fun getAppAccounts(): List<com.flexa.core.entity.AppAccount> {
        TODO("Not yet implemented")
    }

    override suspend fun putAppAccounts(accounts: List<AssetAccount>): PutAppAccountsResponse {
        TODO("Not yet implemented")
    }

    override suspend fun saveAppAccounts(account: List<com.flexa.core.entity.AppAccount>) {
        TODO("Not yet implemented")
    }

    override suspend fun saveEmail(email: String) {}
}
