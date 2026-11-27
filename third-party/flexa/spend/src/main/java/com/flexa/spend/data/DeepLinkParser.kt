package com.flexa.spend.data

class DeepLinkParser {

    companion object {
        private const val PREFIX = "^https?://[^/]+flexa\\.link"

        fun getDeepLink(deepLink: String): DeepLink {
            val accountRegex = Regex("$PREFIX/account$")
            val dataAndPrivacyRegex = Regex("$PREFIX/account/data$")
            val deleteAccountRegex = Regex("$PREFIX/account/delete$")
            val placesToPayRegex = Regex("$PREFIX/explore$")
            val brandsWebRegex = Regex("$PREFIX/explore/(.+)$")
            val howToPayRegex = Regex("$PREFIX/guides/how-to-pay$")
            val payRegex = Regex("$PREFIX/pay$")
            val commerceSessionRegex = Regex("$PREFIX/pay/(.+)$")
            val pinnedBrandsRegex = Regex("$PREFIX/pinned$")
            val reportIssueRegex = Regex("$PREFIX/report-an-issue$")
            val reportIssueBrandRegex = Regex("$PREFIX/report-an-issue/(.+)$")
            val supportArticleRegex = Regex("$PREFIX/support/(.+)$")
            val loginRegex = Regex("$PREFIX/verify/(.+)$")

            return when {
                accountRegex.matches(deepLink) -> DeepLink.Account
                dataAndPrivacyRegex.matches(deepLink) -> DeepLink.DataAndPrivacy
                deleteAccountRegex.matches(deepLink) -> DeepLink.DeleteAccount
                placesToPayRegex.matches(deepLink) -> DeepLink.PlacesToPay
                brandsWebRegex.matches(deepLink) -> {
                    val matchResult = brandsWebRegex.find(deepLink)
                    val url = matchResult?.groupValues?.get(1) ?: ""
                    DeepLink.Brands(url)
                }
                howToPayRegex.matches(deepLink) -> DeepLink.HowToPay
                payRegex.matches(deepLink) -> DeepLink.Pay
                commerceSessionRegex.matches(deepLink) -> {
                    val matchResult = commerceSessionRegex.find(deepLink)
                    val url = matchResult?.groupValues?.get(1) ?: ""
                    DeepLink.CommerceSession(url)
                }
                pinnedBrandsRegex.matches(deepLink) -> DeepLink.PinnedBrands
                reportIssueRegex.matches(deepLink) -> DeepLink.ReportIssue(deepLink)
                reportIssueBrandRegex.matches(deepLink) -> {
                    val matchResult = reportIssueBrandRegex.find(deepLink)
                    val url = matchResult?.groupValues?.get(1) ?: ""
                    DeepLink.ReportIssueBrand(url)
                }
                supportArticleRegex.matches(deepLink) -> {
                    val matchResult = supportArticleRegex.find(deepLink)
                    val url = matchResult?.groupValues?.get(1) ?: ""
                    DeepLink.SupportArticle(url)
                }
                loginRegex.matches(deepLink) -> {
                    DeepLink.Login(deepLink)
                }
                else -> DeepLink.Unknown
            }
        }

    }
}

sealed class DeepLink {
    data object Account: DeepLink()
    data object DataAndPrivacy: DeepLink()
    data object DeleteAccount: DeepLink()
    data object PlacesToPay: DeepLink()
    data class Brands(val url: String): DeepLink()
    data object HowToPay: DeepLink()
    data object Pay: DeepLink()
    data class CommerceSession(val url: String): DeepLink()
    data object PinnedBrands: DeepLink()
    data class ReportIssue(val url: String): DeepLink()
    data class ReportIssueBrand(val url: String): DeepLink()
    data class SupportArticle(val url: String): DeepLink()
    data class Login(val url: String): DeepLink()
    data object Unknown : DeepLink()
}
