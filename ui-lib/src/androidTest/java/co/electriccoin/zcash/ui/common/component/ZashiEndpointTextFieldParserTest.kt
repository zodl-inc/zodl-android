package co.electriccoin.zcash.ui.common.component

import androidx.test.filters.SmallTest
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Test

/**
 * Comprehensive tests for ZashiEndpointTextFieldParser functionality.
 *
 * Tests the robust URL parsing implementation that handles protocol schemes,
 * invalid hostnames, and full port range validation without crashing.
 */
class ZashiEndpointTextFieldParserTest {
    @Test
    @SmallTest
    fun endpointWithProtocolSchemeTest() {
        // Expected behavior: Should parse successfully and return valid endpoint
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("https://zec.rocks:443")
        // Should successfully parse the endpoint, extracting host and port
        MatcherAssert.assertThat("Protocol scheme should be handled gracefully", result, CoreMatchers.notNullValue())
        MatcherAssert.assertThat(
            "Hostname should be extracted correctly",
            result?.host,
            CoreMatchers.equalTo("zec.rocks")
        )
        MatcherAssert.assertThat("Port should be extracted correctly", result?.port, CoreMatchers.equalTo(443))
        MatcherAssert.assertThat("Should default to secure connection", result?.isSecure, CoreMatchers.equalTo(true))
    }

    @Test
    @SmallTest
    fun endpointWithHttpProtocolTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("http://mainnet.lightwalletd.com:9067")
        // Should successfully parse the endpoint, extracting host and port
        MatcherAssert.assertThat(
            "HTTP protocol scheme should be handled gracefully",
            result,
            CoreMatchers.notNullValue()
        )
        MatcherAssert.assertThat(
            "Hostname should be extracted correctly",
            result?.host,
            CoreMatchers.equalTo("mainnet.lightwalletd.com")
        )
        MatcherAssert.assertThat("Port should be extracted correctly", result?.port, CoreMatchers.equalTo(9067))
        MatcherAssert.assertThat("Should default to secure connection", result?.isSecure, CoreMatchers.equalTo(true))
    }

    @Test
    @SmallTest
    fun endpointWithInvalidHostnameLeadingDotTest() {
        // Expected: Should return null (invalid hostname) without crashing
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull(".zec.rocks:443")
        MatcherAssert.assertThat(
            "Invalid hostname with leading dot should return null",
            result,
            CoreMatchers.nullValue()
        )
    }

    @Test
    @SmallTest
    fun endpointWithTrailingDotHostnameTest() {
        // Expected: Should return null (invalid hostname) without crashing
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("zec.rocks.:443")
        MatcherAssert.assertThat(
            "Invalid hostname with trailing dot should return null",
            result,
            CoreMatchers.nullValue()
        )
    }

    @Test
    @SmallTest
    fun endpointValidStandardFormatTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("mainnet.lightwalletd.com:9067")
        MatcherAssert.assertThat("Valid endpoint should not be null", result, CoreMatchers.notNullValue())
        MatcherAssert.assertThat(
            "Hostname should be correct",
            result?.host,
            CoreMatchers.equalTo("mainnet.lightwalletd.com")
        )
        MatcherAssert.assertThat("Port should be correct", result?.port, CoreMatchers.equalTo(9067))
        MatcherAssert.assertThat("Secure should be true by default", result?.isSecure, CoreMatchers.equalTo(true))
    }

    @Test
    @SmallTest
    fun endpointValidWithSingleDigitPortTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:8")
        MatcherAssert.assertThat(
            "Valid endpoint with single digit port should not be null",
            result,
            CoreMatchers.notNullValue()
        )
        MatcherAssert.assertThat("Hostname should be correct", result?.host, CoreMatchers.equalTo("example.com"))
        MatcherAssert.assertThat("Port should be correct", result?.port, CoreMatchers.equalTo(8))
    }

    @Test
    @SmallTest
    fun endpointValidWithMaxPortTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:65535")
        MatcherAssert.assertThat("Port 65535 should be valid (max valid port)", result, CoreMatchers.notNullValue())
        MatcherAssert.assertThat("Hostname should be correct", result?.host, CoreMatchers.equalTo("example.com"))
        MatcherAssert.assertThat("Port should be correct", result?.port, CoreMatchers.equalTo(65535))
    }

    @Test
    @SmallTest
    fun endpointEmptyStringTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("")
        MatcherAssert.assertThat("Empty string should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointMissingPortTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("mainnet.lightwalletd.com")
        MatcherAssert.assertThat("Endpoint without port should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointMissingHostnameTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull(":9067")
        MatcherAssert.assertThat("Endpoint without hostname should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointInvalidPortZeroTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:0")
        MatcherAssert.assertThat("Port 0 should be invalid", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointInvalidPortNegativeTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:-1")
        MatcherAssert.assertThat("Negative port should be invalid", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointInvalidPortNonNumericTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:abc")
        MatcherAssert.assertThat("Non-numeric port should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointInvalidPortWithLettersTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:80a")
        MatcherAssert.assertThat("Port with letters should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointMultipleColonsTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:80:80")
        MatcherAssert.assertThat("Multiple colons should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointWithPathTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:80/path")
        MatcherAssert.assertThat("Endpoint with path should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointWithQueryParamsTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:80?param=value")
        MatcherAssert.assertThat("Endpoint with query params should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointWithFragmentTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("example.com:80#fragment")
        MatcherAssert.assertThat("Endpoint with fragment should return null", result, CoreMatchers.nullValue())
    }

    @Test
    @SmallTest
    fun endpointLocalhostTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("localhost:9067")
        MatcherAssert.assertThat("Localhost should be valid", result, CoreMatchers.notNullValue())
        MatcherAssert.assertThat("Hostname should be localhost", result?.host, CoreMatchers.equalTo("localhost"))
        MatcherAssert.assertThat("Port should be correct", result?.port, CoreMatchers.equalTo(9067))
    }

    @Test
    @SmallTest
    fun endpointIpAddressTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("127.0.0.1:9067")
        MatcherAssert.assertThat("IP address should be valid", result, CoreMatchers.notNullValue())
        MatcherAssert.assertThat("Hostname should be IP address", result?.host, CoreMatchers.equalTo("127.0.0.1"))
        MatcherAssert.assertThat("Port should be correct", result?.port, CoreMatchers.equalTo(9067))
    }

    @Test
    @SmallTest
    fun endpointWithSubdomainTest() {
        val result = ZashiEndpointTextFieldParser.toEndpointOrNull("api.mainnet.lightwalletd.com:9067")
        MatcherAssert.assertThat("Subdomain should be valid", result, CoreMatchers.notNullValue())
        MatcherAssert.assertThat(
            "Hostname should include subdomain",
            result?.host,
            CoreMatchers.equalTo("api.mainnet.lightwalletd.com")
        )
        MatcherAssert.assertThat("Port should be correct", result?.port, CoreMatchers.equalTo(9067))
    }
}
