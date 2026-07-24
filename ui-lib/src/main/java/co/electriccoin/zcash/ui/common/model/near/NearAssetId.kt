package co.electriccoin.zcash.ui.common.model.near

private const val BITCOIN_ASSET_ID = "nep141:btc.omft.near"
private const val BITCOIN_ROUTED_ASSET_ID = "1cs_v1:btc:native:coin"

/**
 * 1Click can quote a swap against a different representation of the same coin than the one requested: a
 * quote for `nep141:btc.omft.near` is echoed back as `1cs_v1:btc:native:coin`. `/v0/status` echoes the
 * requested id unchanged, so this only keeps the status lookup working if that ever changes.
 *
 * Apply it to echoed ids only, never to catalog ids. `/v0/tokens` lists both forms as separate assets,
 * `BTC` and `BTC(OMNI)`, with independent liquidity, so rewriting catalog ids would collapse two entries
 * onto one id and leave the lookup resolving by list position.
 */
private val routedAssetIds = mapOf(BITCOIN_ROUTED_ASSET_ID to BITCOIN_ASSET_ID)

internal fun requestedAssetId(echoedId: String): String = routedAssetIds[echoedId] ?: echoedId
