package com.flexa.identity.domain

import com.flexa.core.data.data.PikSeeProvider
import com.flexa.core.data.rest.RestRepository.Companion.json
import com.flexa.core.data.storage.SecuredPreferences
import com.flexa.core.domain.db.DbInteractor
import com.flexa.core.domain.rest.RestInteractor
import com.flexa.core.entity.Account
import com.flexa.core.entity.AvailableAsset
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.PutAppAccountsResponse
import com.flexa.core.entity.TokenPatch
import com.flexa.core.entity.TokensResponse
import com.flexa.core.shared.Asset
import com.flexa.core.shared.AssetAccount
import com.flexa.core.shared.AssetsResponse
import com.flexa.core.shared.Brand
import com.flexa.core.shared.FlexaConstants
import com.flexa.core.shared.filterAssets
import com.flexa.identity.create_id.AccountsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.UUID

internal class IdentityInteractor(
    private val restInteractor: RestInteractor,
    private val dbInteractor: DbInteractor,
    private val preferences: SecuredPreferences
) : IIdentityInteractor {

    override suspend fun saveEmail(email: String) {
        preferences.saveString(
            FlexaConstants.EMAIL,
            email
        )
    }

    override suspend fun getEmail(): String? {
        return preferences
            .getString(FlexaConstants.EMAIL)
    }

    override suspend fun clearLoginData() = withContext(Dispatchers.IO) {
        dbInteractor.clearAllTables()

        val pubKey = preferences.getPublishableKey()
        val uniqueId = preferences.getUniqueIdentifier() ?: UUID.randomUUID().toString()
        val placesToPayTheme = preferences.getString(FlexaConstants.PLACES_TO_PAY_THEME)

        preferences.clearPreferences()

        preferences.savePublishableKey(pubKey)
        preferences.saveUniqueIdentifier(uniqueId)
        preferences.savePlacesToPayTheme(placesToPayTheme)
    }

    override suspend fun getAllAssets(pageSize: Int): List<Asset> {
        var startingAfter: String? = null
        val assets = arrayListOf<Asset>()
        do {
            val response = getAssets(pageSize, startingAfter)
            startingAfter = response.startingAfter
            assets.addAll(response.data)
        } while (startingAfter != null)
        return assets
    }

    override suspend fun getAssets(pageSize: Int, nextPageToken: String?): AssetsResponse =
        restInteractor.getAssets(pageSize, nextPageToken)

    override suspend fun deleteAssets() = dbInteractor.deleteAssets()

    override suspend fun saveAssets(items: List<Asset>) =
        dbInteractor.saveAssets(items)

    override suspend fun getBrands(legacyOnly: Boolean?): List<Brand> {
        var startingAfter: String? = null
        val brands = ArrayList<Brand>()
        do {
            val response = restInteractor.getBrands(legacyOnly, startingAfter)
            startingAfter = response.startingAfter
            brands.addAll(response.data)
        } while (startingAfter != null)
        return brands
    }

    override suspend fun saveBrands(items: List<Brand>) =
        dbInteractor.saveBrands(items)

    override suspend fun deleteBrands() = dbInteractor.deleteBrands()
    override suspend fun getExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): List<ExchangeRate> = withContext(Dispatchers.IO) {
        restInteractor.getExchangeRates(assetIds, unitOfAccount).data
    }

    override suspend fun saveExchangeRates(items: List<ExchangeRate>) =
        withContext(Dispatchers.IO) {
            dbInteractor.saveExchangeRates(items)
        }

    override suspend fun deleteExchangeRates() = withContext(Dispatchers.IO) {
        dbInteractor.deleteExchangeRates()
    }

    override suspend fun tokens(email: String): TokensResponse = withContext(Dispatchers.IO) {
        val verifier = PikSeeProvider.getCodeVerifier()
        val challenge = PikSeeProvider.getCodeChallenge(verifier)
        val res = restInteractor.tokens(email, challenge)
        if (res is TokensResponse.Success) {
            preferences.saveString(FlexaConstants.VERIFIER, verifier)
            preferences.saveString(FlexaConstants.TOKEN_ID, res.id)
        }
        res
    }

    override suspend fun accounts(
        firstName: String, lastName: String,
        email: String, country: String,
        birthday: String
    ): Int = withContext(Dispatchers.IO) {
        val request = AccountsRequest(
            country = country,
            dateOfBirth = birthday,
            email = email,
            familyName = lastName,
            givenName = firstName,
        )
        val res = restInteractor.accounts(request)
        res
    }

    override suspend fun tokenPatch(code: String?, link: String?): TokenPatch =
        withContext(Dispatchers.IO) {
            val tokenId = preferences.getString(FlexaConstants.TOKEN_ID) ?: ""
            val verifier = preferences.getString(FlexaConstants.VERIFIER) ?: ""
            val newVerifier = PikSeeProvider.getCodeVerifier()
            val challenge = PikSeeProvider.getCodeChallenge(newVerifier)
            val res = restInteractor.tokenPatch(
                id = tokenId, verifier = verifier, challenge = challenge,
                code = code, link = link
            )
            preferences.saveString(FlexaConstants.VERIFIER, newVerifier)
            preferences.saveString(FlexaConstants.TOKEN, res.value)
            preferences.saveLong(FlexaConstants.TOKEN_EXPIRATION, res.expiration)
            res
        }

    override suspend fun getAccount(): Account = withContext(Dispatchers.IO) {
        restInteractor.getAccount()
    }

    override suspend fun getAppAccounts(): List<com.flexa.core.entity.AppAccount>? {
        val data = preferences.getString(FlexaConstants.APP_ACCOUNTS)
        val accounts =
            data?.let { json.decodeFromString<List<com.flexa.core.entity.AppAccount>>(it) }
        return accounts
    }

    override suspend fun putAppAccounts(accounts: List<AssetAccount>): PutAppAccountsResponse =
        withContext(Dispatchers.IO) {
            val appAccounts = ArrayList<com.flexa.core.entity.AppAccount>(accounts.size)
            val account = runCatching { getAccount() }.getOrNull()
            val assets = getAllAssets(100)
            deleteAssets()
            saveAssets(assets)
            for (localAccount in accounts) {
                val filteredAssets = localAccount.filterAssets(assets)
                val accountAssets = filteredAssets.map { localAsset ->
                    val assetData = assets.firstOrNull { it.id == localAsset.assetId }
                    AvailableAsset(
                        assetId = localAsset.assetId,
                        balance = localAsset.balance.toString(),
                        balanceAvailable = localAsset.balanceAvailable,
                        livemode = assetData?.livemode,
                        assetData = assetData
                        )
                }

                val appAccount = com.flexa.core.entity.AppAccount(
                    accountId = localAccount.assetAccountHash,
                    displayName = localAccount.displayName,
                    unitOfAccount = account?.limits?.firstOrNull()?.asset,
                    icon = localAccount.icon,
                    availableAssets = ArrayList(accountAssets)
                )
                appAccounts.add(appAccount)
            }
            PutAppAccountsResponse(date = "", accounts = appAccounts)
        }

    override suspend fun saveAppAccounts(account: List<com.flexa.core.entity.AppAccount>) {
        val accounts = json.encodeToString(account)
        preferences.saveString(FlexaConstants.APP_ACCOUNTS, accounts)
    }
}
