package com.flexa.spend.main.assets


sealed class AssetsState {
    object Retrieving : AssetsState()
    class Fine(val incorrectAssetTickersList: List<String>) : AssetsState()
    class NoAssets(val incorrectAssetTickersList: List<String>) : AssetsState()
}
