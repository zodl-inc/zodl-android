package com.flexa.spend.domain

import com.flexa.core.data.db.BrandSession
import com.flexa.core.entity.Account
import com.flexa.core.entity.AppAccount
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.ExchangeRatesResponse
import com.flexa.core.entity.Limit
import com.flexa.core.entity.OneTimeKey
import com.flexa.core.entity.OneTimeKeyResponse
import com.flexa.core.entity.PutAppAccountsResponse
import com.flexa.core.entity.SseEvent
import com.flexa.core.entity.TransactionFee
import com.flexa.core.shared.Asset
import com.flexa.core.shared.AssetAccount
import com.flexa.core.shared.AssetsResponse
import com.flexa.core.shared.Brand
import com.flexa.core.shared.ConnectionState
import com.flexa.spend.MockFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow


internal class FakeInteractor : ISpendInteractor {
    override suspend fun getConnectionListener(): Flow<ConnectionState>? {
        return MutableStateFlow(ConnectionState.Available)
    }

    override suspend fun getLocalAssetsAccounts(): List<AssetAccount>? {
        TODO("Not yet implemented")
    }

    override suspend fun getLocalAppAccounts(): List<AppAccount> {
        return MockFactory.getMockConfig()
    }

    override suspend fun getAllAssets(pageSize: Int): List<Asset> {
        return listOf(
            Asset(
                id = "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501", displayName = "SOL"
            ),
            Asset(
                id = "eip155:1/slip44:60", displayName = "Ether"
            ),
        )
    }

    override suspend fun getAssets(pageSize: Int, nextPageToken: String?): AssetsResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getAssetById(assetId: String): Asset {
        TODO("Not yet implemented")
    }

    override suspend fun getBrandSession(sessionId: String): BrandSession? {
        TODO("Not yet implemented")
    }

    override suspend fun deleteBrandSession(sessionId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteOutdatedSessions() {
        TODO("Not yet implemented")
    }

    override suspend fun saveBrandSession(transactionBundle: BrandSession) {
        TODO("Not yet implemented")
    }

    override suspend fun saveLastSessionId(eventId: String?) {
        TODO("Not yet implemented")
    }

    override suspend fun getLastSessionId(): String? {
        TODO("Not yet implemented")
    }

    override suspend fun getCommerceSession(sessionId: String): CommerceSession.Data {
        TODO("Not yet implemented")
    }

    override suspend fun saveLastEventId(eventId: String?) {
        TODO("Not yet implemented")
    }

    override suspend fun getLastEventId(): String? {
        TODO("Not yet implemented")
    }

    override suspend fun putAccounts(accounts: List<com.flexa.core.shared.AssetAccount>): PutAppAccountsResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getAccount(): Account {
        val remaining = "215.51"
        return Account(
            name = "Satoshi Nakamoto",
            created = 1683146197,
            limits = listOf(
                Limit(
                    amount = "750",
                    asset = "iso4217/USD",
                    description = "\$$remaining remaining",
                    label = "\$750/week",
                    name = "Spend Limit",
                    remaining = remaining,
                    resetsAt = 1723824000
                )
            )
        )
    }

    override suspend fun getAccountCached(): Account? {
        TODO("Not yet implemented")
    }

    override suspend fun getUnitOfAccount(): String {
        TODO("Not yet implemented")
    }

    override suspend fun deleteNotification(id: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getEmail(): String? {
        return "satoshi.n@gmail.com"
    }

    override suspend fun getPublishableKey(): String {
        TODO("Not yet implemented")
    }

    override suspend fun getPlacesToPayTheme(): String {
        return """
            {
            "android": {
            "light": {
            "backgroundColor": "rgba(255, 255, 255, 1.0)",
            "primary": "#6974ff",
            "cardColor": "azure"
        },
            "dark": {
            "backgroundColor": "rgba(255, 255, 255, 1.0)",
            "primary": "#6974ff",
            "cardColor": "azure"
        }
        }
        }
        """
    }

    override suspend fun getDbAssets(): List<Asset> {
        return listOf(
            Asset(
                id = "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501", displayName = "SOL"
            ),
        )
    }

    override suspend fun getDbAssetsById(vararg ids: String): List<Asset> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAssets() {
        TODO("Not yet implemented")
    }

    override suspend fun saveAssets(items: List<Asset>) {
        TODO("Not yet implemented")
    }

    override suspend fun listenEvents(lastEventId: String?): Flow<SseEvent> {
        TODO("Not yet implemented")
    }

    override suspend fun savePinnedBrands(itemsIds: List<String>) {
        TODO("Not yet implemented")
    }

    override suspend fun getPinnedBrands(): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun getBrands(legacyOnly: Boolean): List<Brand> {
        return listOf(MockFactory.getBrand())
    }

    override suspend fun getDbBrands(): List<Brand> {
        return arrayListOf<Brand>().apply {
            repeat(5) {
                MockFactory.getBrand()
            }
        }
    }

    override suspend fun saveBrands(items: List<Brand>) {
    }

    override suspend fun deleteBrands() {
    }

    override suspend fun createCommerceSession(
        brandId: String,
        amount: String,
        assetId: String,
        paymentAssetId: String
    ): CommerceSession.Data {
        TODO("Not yet implemented")
    }

    override suspend fun approveCommerceSession(commerceSessionId: String): Int {
        TODO("Not yet implemented")
    }

    override suspend fun closeCommerceSession(commerceSessionId: String): CommerceSession.Data {
        TODO("Not yet implemented")
    }

    override suspend fun confirmTransaction(
        commerceSessionId: String,
        txSignature: String
    ): String {
        TODO("Not yet implemented")
    }

    override suspend fun patchCommerceSession(
        commerceSessionId: String,
        paymentAssetId: String
    ): String {
        TODO("Not yet implemented")
    }

    override suspend fun deleteToken(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAccount(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun hasOutdatedExchangeRates(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getDbExchangeRates(): List<ExchangeRate> {
        TODO("Not yet implemented")
    }

    override suspend fun getDbExchangeRate(id: String): ExchangeRate? {
        TODO("Not yet implemented")
    }

    override suspend fun getExchangeRates(
        assetIds: List<String>,
        unitOfAccount: String
    ): ExchangeRatesResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getExchangeRatesSmart(
        assetIds: List<String>,
        unitOfAccount: String
    ): ExchangeRatesResponse {
        TODO("Not yet implemented")
    }

    override suspend fun hasOutdatedOneTimeKeys(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getDbOneTimeKey(assetId: String, livemode: Boolean?): OneTimeKey? {
        TODO("Not yet implemented")
    }

    override suspend fun getDbOneTimeKeys(): List<OneTimeKey> {
        TODO("Not yet implemented")
    }

    override suspend fun getOneTimeKeys(assetIds: List<String>): OneTimeKeyResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getOneTimeKeysSmart(assetIds: List<String>): List<OneTimeKey> {
        TODO("Not yet implemented")
    }

    override suspend fun saveOneTimeKeys(items: List<OneTimeKey>) {
        TODO("Not yet implemented")
    }

    override suspend fun saveExchangeRates(items: List<ExchangeRate>) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteExchangeRates() {
        TODO("Not yet implemented")
    }

    override suspend fun getTransactionFees(assetIds: List<String>): List<TransactionFee> {
        TODO("Not yet implemented")
    }

    override suspend fun getDbTransactionFees(): List<TransactionFee> {
        TODO("Not yet implemented")
    }

    override suspend fun saveTransactionFees(items: List<TransactionFee>) {
        TODO("Not yet implemented")
    }

    override suspend fun getDbFeeByTransactionAssetID(assetId: String): TransactionFee? {
        TODO("Not yet implemented")
    }

    override suspend fun getDbFeeByAssetID(assetId: String): TransactionFee? {
        TODO("Not yet implemented")
    }
}
