package co.electriccoin.zcash.ui.common.repository

/**
 * Gift card links and keys in every disguise that [GiftCardSecretDetector] must see through, and ordinary addresses it
 * must let pass. The links carry a key without the gift key prefix, so that each disguise of the host is tested on its
 * own; the bare keys are tested separately.
 */
object GiftCardSecretFixture {
    /** A card secret as it appears in a link's fragment. */
    const val SECRET = "Kx9fixturesecret"

    const val LINK = "https://gift.zodl.com/#v=1&key=$SECRET&height=1"

    private const val VIZOR_LINK = "https://link.vizor.cash/payment-links/open#v1=$SECRET"

    /** Each disguise of a link, named for test failure messages. */
    val disguisedLinks: Map<String, String> =
        mapOf(
            "plain" to LINK,
            "without slash before the fragment" to "https://gift.zodl.com#v=1&key=$SECRET&height=1",
            "upper case with surrounding whitespace" to "  HTTPS://GIFT.ZODL.COM/#v=1&key=$SECRET&height=1 \n",
            "vizor" to VIZOR_LINK,
            "vizor in mixed case" to "\tHttps://Link.Vizor.Cash/Payment-Links/Open#v1=$SECRET  ",
            "text before the link" to "Your gift: $LINK",
            "text around the link" to "Hi! Your gift: $LINK enjoy",
            "leading zero-width space" to "​$LINK",
            "leading byte order mark" to "﻿$LINK",
            "leading word joiner" to "⁠$LINK",
            "bidi override" to "‮$LINK‬",
            "bidi isolate" to "⁦$LINK⁩",
            "zero-width joiner inside the host" to "https://gift.‍zodl.com/#key=$SECRET",
            "soft hyphen inside the host" to "https://gift.zo­dl.com/#key=$SECRET",
            "leading quote" to "\"$LINK\"",
            "leading bracket" to "($LINK)",
            "leading angle bracket" to "<$LINK>",
            "http scheme" to "http://gift.zodl.com/#v=1&key=$SECRET",
            "no scheme" to "gift.zodl.com/#v=1&key=$SECRET",
            "vizor without scheme" to "link.vizor.cash/payment-links/open#v1=$SECRET",
            "line break inside the link" to "https://gift.\nzodl.com/#key=$SECRET",
            "full-width letters" to "https://ｇｉｆｔ.zodl.com/#key=$SECRET",
            "percent-encoded" to "https%3A%2F%2Fgift.zodl.com%2F%23v%3D1%26key%3D$SECRET",
            "percent-encoded host" to "https://%67%69%66%74%2E%7A%6F%64%6C%2E%63%6F%6D/#key=$SECRET",
            "lower-case percent escapes" to "https%3a%2f%2fgift%2ezodl%2ecom%2f%23key%3d$SECRET",
            "double percent-encoded" to "https%253A%252F%252Fgift%252Ezodl%252Ecom%252F%2523key%253D$SECRET",
            "percent-encoded zero-width space inside the host" to "https://gift.%E2%80%8Bzodl.com/#key=$SECRET",
            "percent-encoded vizor" to "https%3A%2F%2Flink.vizor.cash%2Fpayment-links%2Fopen%23v1%3D$SECRET",
        )

    /** Bare gift card keys, as they could be pasted without the link around them. */
    val bareKeys: Map<String, String> =
        mapOf(
            "mainnet key" to "zgift1qqsyqcyq5rqwzqfsqqsyqcyq5rqwzqf",
            "testnet key" to "zgifttest1qqsyqcyq5rqwzqfsqqsyqcyq5rqwzqf",
            "upper-case key" to "ZGIFT1QQSYQCYQ5RQWZQFSQQSYQCYQ5RQWZQF",
            "key inside text" to "key: zgift1qqsyqcyq5rqwzqf, thanks",
        )

    /** Everything that must be refused. */
    val all: Map<String, String> = disguisedLinks + bareKeys

    /** Real-looking addresses of the chains a swap or a contact may use, which must all pass. */
    val ordinaryAddresses: List<String> =
        listOf(
            "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
            "1BoatSLRHtKNngkdXEeobR76b53LETtpyT",
            "0x52908400098527886E0F7030069857D2E4169EE7",
            "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV",
            "t1Rv4exT7bqhZqi2j7xz8bUHDMxwosrjADU",
            "u1ordinaryunifiedaddress",
            "zs1ordinarysaplingaddress",
            "tex1ordinarytexaddress",
            "100%",
            "%zz%",
        )
}
