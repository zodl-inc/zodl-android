package com.flexa.spend

import com.flexa.core.entity.AppAccount
import com.flexa.core.entity.AssetKey
import com.flexa.core.entity.AvailableAsset
import com.flexa.core.entity.BalanceBundle
import com.flexa.core.entity.CommerceSession
import com.flexa.core.entity.ExchangeRate
import com.flexa.core.entity.Notification
import com.flexa.core.shared.Asset
import com.flexa.core.shared.Brand
import com.flexa.core.shared.SelectedAsset
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class MockFactory {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun getMockConfig(): List<AppAccount> =
            listOf(
                AppAccount(
                    accountId = UUID.randomUUID().toString(),
                    displayName = "Example Wallet",
                    icon = "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                    availableAssets = arrayListOf(
                        AvailableAsset(
                            assetId = "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501",
                            balance = "0.5",
                        ),
                        AvailableAsset(
                            assetId = "eip155:1/slip44:60",
                            balance = "0.025",
                        ),
                    )
                )
            )

        fun getMockSelectedAsset(): SelectedAsset =
            SelectedAsset(
                accountId = "1",
                asset = AvailableAsset(
                    assetId = "eip155:1/slip44:60",
                    balance = "0.5",
                    livemode = true,
                    assetData = Asset(
                        id = "eip155:1/slip44:60",
                        displayName = "ETH",
                        symbol = "ETH"
                    ),
                    key = AssetKey(
                        prefix = "123", secret = "321", length = 3
                    ),
                    balanceBundle = getBalanceBundle()
                )
            )

        fun getCommerceSession(price: String = "32.15"): CommerceSession {
            val time = Instant.now().plusSeconds(60 * 30).epochSecond
            return json.decodeFromString<CommerceSession>(
                """
                {
    "id": "event_id",
    "api_version": "2024-06-11",
    "data": {
        "account": "acc",
        "amount": "$price",
        "asset": "iso4217/USD",
        "brand": {
            "category_name": "",
            "color": "#8043FF",
            "created": 1672411062,
            "id": "id",
            "logo_url": "https://flexa.network/img/merchants/flexa-demo-store.png",
            "name": "Flexa Demo Store",
            "slug": "flexa-demo-store",
            "status": "active",
            "updated": 1716496094
        },
        "created": 1721215290,
        "debits": [
            {
                "amount": "$price",
                "asset": "iso4217/USD",
                "created": 1721215290,
                "id": "id",
                "intent_id": "intent_id",
                "kind": "total",
                "label": "${'$'}$price",
                "session_id": "session_id",
                "test_mode": true,
                "updated": 1721215290
            }
        ],
        "id": "id_123_789",
        "intent": "intent",
        "preferences": {
            "app": "flexa",
            "payment_asset": "eip155:1/erc20:0xe7ae9b78373d0D54BAC81a85525826Fd50a1E2d3"
        },
        "rate": {
            "expires_at": -62135596800,
            "label": "1 CR = ${'$'}1.00"
        },
        "status": "pending",
        "test_mode": true,
        "transactions": [
            {
                "amount": "0.1",
                "asset": "eip155:1/erc20:0xe7ae9b78373d0D54BAC81a85525826Fd50a1E2d3",
                "created": 1721215290,
                "destination": {
                    "address": "address",
                    "label": "label"
                },
                "expires_at": $time,
                "fee": {
                    "amount": "0.003",
                    "asset": "eip155:1/slip44:60",
                    "equivalent": "${'$'}10.40",
                    "label": "0.003 ETH",
                    "price": {
                        "amount": "0.000000015",
                        "label": "<1 gwei",
                        "priority": "0.000000004464273208"
                    },
                    "zone": "medium"
                },
                "id": "id",
                "label": "0.1 CR",
                "session": "session",
                "size": "200000",
                "status": "requested",
                "test_mode": true,
                "updated": 1721215290
            }
        ],
        "updated": 1721215290
    },
    "created": 1721215292,
    "type": "commerce_session.updated"
}
                """.trimIndent()
            )
        }

        fun getMockCommerceSessionCompleted(): CommerceSession {
            return json.decodeFromString<CommerceSession>(
                """
                {
    "id": "id",
    "api_version": "2024-06-11",
    "data": {
        "account": "acc",
        "amount": "0.1",
        "asset": "iso4217/USD",
        "brand": {
            "category_name": "",
            "color": "#8043FF",
            "created": 1672411062,
            "id": "id",
            "logo_url": "",
            "name": "Flexa",
            "slug": "flexa",
            "status": "active",
            "updated": 1716496094
        },
        "authorization": {
            "instructions": "Scan as **Gift** or **Store Credit**",
            "number": "number",
            "details": "PIN: 1234"
        },
        "created": 1721215290,
        "debits": [
            {
                "amount": "0.1",
                "asset": "iso4217/USD",
                "created": 1721215290,
                "id": "id",
                "intent_id": "intent_id",
                "kind": "total",
                "label": "${'$'}0.10",
                "session_id": "session_id",
                "test_mode": true,
                "updated": 1721215290
            }
        ],
        "id": "id",
        "intent": "intent",
        "preferences": {
            "app": "flexa",
            "payment_asset": "eip155:1/erc20:0xe7ae9b78373d0D54BAC81a85525826Fd50a1E2d3"
        },
        "rate": {
            "expires_at": -62135596800,
            "label": "1 CR = ${'$'}1.00"
        },
        "status": "pending",
        "test_mode": true,
        "transactions": [
            {
                "amount": "0.1",
                "asset": "eip155:1/erc20:0xe7ae9b78373d0D54BAC81a85525826Fd50a1E2d3",
                "created": 1721215290,
                "destination": {
                    "address": "address",
                    "label": "label"
                },
                "expires_at": 1721218890,
                "fee": {
                    "amount": "0.003",
                    "asset": "eip155:1/slip44:60",
                    "label": "0.003 ETH",
                    "transaction_asset": "eip155:1/slip44:60",
                    "price": {
                        "amount": "0.000000015",
                        "label": "<1 gwei",
                        "priority": "0.000000004464273208"
                    },
                    "zone": "medium"
                },
                "id": "id",
                "label": "0.1 CR",
                "session": "session",
                "size": "200000",
                "status": "requested",
                "test_mode": true,
                "updated": 1721215290
            }
        ],
        "updated": 1721215290
    },
    "created": 1721215292,
    "type": "commerce_session.updated"
}
                """.trimIndent()
            )
        }

        fun getBrand(): Brand {
            return json.decodeFromString<Brand>(
                """
                    {
                            "category_name": "",
                            "color": "#8043FF",
                            "created": 1672411062,
                            "id": "id",
                            "logo_url": "https://flexa.network/img/merchants/flexa-demo-store.png",
                            "name": "Flexa Demo Store",
                            "slug": "flexa-demo-store",
                            "status": "active",
                            "updated": 1716496094
                        }
                """.trimIndent()
            )
        }

        fun getAppNotification(): Notification = Notification(
            action = Notification.Action(
                label = "Learn How to Pay",
                url = "https://app.flexa.link/flexa/how-to-pay"
            ),
            body = "Pay your favorite places directly from your wallet—no card required.",
            iconUrl = "https://flexa.media/icons/flexa.svg",
            id = "id",
            title = "Get started with Flexa"
        )

        fun getExchangeRate(): ExchangeRate = ExchangeRate(
            asset = getMockSelectedAsset().asset.assetId,
            label = "\$2642.56",
            precision = 6,
            price = "2642.56",
            unitOfAccount = "iso4217/USD"
        )

        fun getBalanceBundle(): BalanceBundle = BalanceBundle(
            total = BigDecimal(68.43),
            available = BigDecimal(23.0),
            totalLabel = "\$68.43",
            availableLabel = "\$23.00",
        )

        fun getExchangeRates(): List<ExchangeRate> {
            val randomPrice = String.format("%.2f", Random.nextDouble(2676.0, 2690.0))
            return json.decodeFromString<List<ExchangeRate>>(
                """
                [
        {
            "asset": "eip155:1/slip44:60",
            "expires_at": 1727546252,
            "label": "${'$'}$randomPrice",
            "precision": 6,
            "price": "$randomPrice",
            "unit_of_account": "iso4217/USD"
        },
        {
            "asset": "cip34:1-764824073/slip44:1815",
            "expires_at": 1727546404,
            "label": "${'$'}0.40",
            "precision": 0,
            "price": "0.40",
            "unit_of_account": "iso4217/USD"
        },
        {
            "asset": "eip155:1/erc20:0xe7ae9b78373d0D54BAC81a85525826Fd50a1E2d3",
            "expires_at": 1727546252,
            "label": "${'$'}1.00",
            "precision": 2,
            "price": "1.00",
            "unit_of_account": "iso4217/USD"
        },
        {
            "asset": "eip155:1/erc20:0xdBdb4d16EdA451D0503b854CF79D55697F90c8DF",
            "expires_at": 1727546252,
            "label": "${'$'}14.82",
            "precision": 4,
            "price": "14.82",
            "unit_of_account": "iso4217/USD"
        }
    ]
                """.trimIndent()
            )
        }
    }
}
