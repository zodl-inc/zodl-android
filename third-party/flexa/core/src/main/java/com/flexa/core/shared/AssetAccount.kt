package com.flexa.core.shared

import android.graphics.Color

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetAccount(
    /**
     * SHA256 hash of Account ID string
     */
    @SerialName("asset_account_hash")
    val assetAccountHash: String,
    @SerialName("custody_model")
    val custodyModel: CustodyModel,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("icon")
    val icon: String? = null,
    @SerialName("available_assets")
    val availableAssets: List<AvailableAsset>,
)

@Serializable
data class AvailableAsset(
    @SerialName("asset_id")
    val assetId: String,
    @SerialName("balance")
    val balance: Double,
    /**
     * Due to the nature of the UTXO model used on Bitcoin and Zcash,
     * spendable balance can fluctuate, sometimes to zero temporarily
     * as the transaction is being mined and confirmed on-chain
     */
    @SerialName("balance_available")
    val balanceAvailable: Double? = null,
    @SerialName("icon")
    val icon: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("symbol")
    val symbol: String? = null,
    @SerialName("accent_color")
    @Serializable(with = ColorSerializer::class)
    val accentColor: Color? = null,
)

@Serializable
enum class CustodyModel {
    LOCAL, MANAGED
}

fun List<AssetAccount>.filterAssets(assets: List<Asset>): List<AssetAccount> {
    val accounts = this.distinctBy { it.assetAccountHash }
    val appAccounts = ArrayList<AssetAccount>(accounts.size)
    accounts.forEach { acc ->
        val filteredAssets = acc.filterAssets(assets)
        appAccounts.add(acc.copy(availableAssets = filteredAssets))
    }
    return appAccounts
}

fun AssetAccount.filterAssets(assets: List<Asset>): List<AvailableAsset> {
    return this.availableAssets.filter { asset ->
        assets.any { it.id == asset.assetId }
    }
}