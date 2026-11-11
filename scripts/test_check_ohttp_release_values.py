#!/usr/bin/env python3
"""Unit tests for check_ohttp_release_values.py."""
import os
import sys
import tempfile
import unittest
from unittest import mock

# Import the module under test
sys.path.insert(0, os.path.dirname(__file__))
import importlib.util
spec = importlib.util.spec_from_file_location(
    "check_ohttp_release_values",
    os.path.join(os.path.dirname(__file__), "check_ohttp_release_values.py")
)
check_ohttp_release_values = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check_ohttp_release_values)


class TestCheckOhttpReleaseValues(unittest.TestCase):
    def setUp(self):
        """Set up valid test values."""
        self.valid_values = {
            "ZODL_OHTTP_GATEWAY_HOST_MAINNET": "gateway.example.com",
            "ZODL_OHTTP_RELAY_URL_MAINNET": "https://relay-mainnet.example.com/relay",
            "ZODL_OHTTP_KEYS_DNS_NAME_MAINNET": "keys.example.com",
            "ZODL_OHTTP_SIGNING_PUBKEY_MAINNET": "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
            "ZODL_OHTTP_GATEWAY_HOST_TESTNET": "gateway-testnet.example.com",
            "ZODL_OHTTP_RELAY_URL_TESTNET": "https://relay-testnet.example.com/relay",
            "ZODL_OHTTP_KEYS_DNS_NAME_TESTNET": "keys-testnet.example.com",
            "ZODL_OHTTP_SIGNING_PUBKEY_TESTNET": "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        }
        self.temp_dir = tempfile.mkdtemp()
        self.provider_path = os.path.join(
            self.temp_dir,
            "ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/LightWalletEndpointProvider.kt"
        )
        self.saved_env = os.environ.copy()

    def tearDown(self):
        """Clean up environment."""
        os.environ.clear()
        os.environ.update(self.saved_env)
        if os.path.exists(self.temp_dir):
            import shutil
            shutil.rmtree(self.temp_dir)

    def create_provider_file(self, digests):
        """Create a mock LightWalletEndpointProvider.kt file with the given digests."""
        os.makedirs(os.path.dirname(self.provider_path), exist_ok=True)
        digests_str = ",\n                ".join(f'"{d}"' for d in digests)
        content = f"""
        private val INDEPENDENT_RELAY_URL_SHA256 =
            setOf(
                {digests_str},
            )
        """
        with open(self.provider_path, "w") as f:
            f.write(content)

    def test_sha256_hex(self):
        """SHA-256 computation matches expected format."""
        url = "https://relay.example.com/relay"
        digest = check_ohttp_release_values.sha256_hex(url)
        self.assertEqual(len(digest), 64)
        self.assertTrue(all(c in "0123456789abcdef" for c in digest))

    def test_dns_name_pattern(self):
        """DNS name pattern validates correctly."""
        pattern = check_ohttp_release_values.DNS_NAME_PATTERN
        self.assertIsNotNone(pattern.match("example.com"))
        self.assertIsNotNone(pattern.match("sub.example.com"))
        self.assertIsNotNone(pattern.match("a-b.example.com"))
        self.assertIsNone(pattern.match("Example.com"))  # uppercase
        self.assertIsNone(pattern.match("-example.com"))  # leading hyphen
        self.assertIsNone(pattern.match("example.com-"))  # trailing hyphen

    def test_https_url_pattern(self):
        """HTTPS URL pattern validates correctly."""
        pattern = check_ohttp_release_values.HTTPS_URL_PATTERN
        self.assertIsNotNone(pattern.match("https://example.com"))
        self.assertIsNotNone(pattern.match("https://example.com/path"))
        self.assertIsNone(pattern.match("http://example.com"))
        self.assertIsNone(pattern.match("ftp://example.com"))

    def test_hex_64_pattern(self):
        """Hex 64 pattern validates correctly."""
        pattern = check_ohttp_release_values.HEX_64_PATTERN
        self.assertIsNotNone(pattern.match("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"))
        self.assertIsNone(pattern.match("D75A980182B10AB7D54BFED3C964073A0EE172F3DAA62325AF021A68F707511A"))  # uppercase
        self.assertIsNone(pattern.match("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511"))  # 63 chars
        self.assertIsNone(pattern.match("g75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"))  # non-hex

    def test_all_values_present(self):
        """Check passes when all values are present and valid."""
        os.environ.update(self.valid_values)
        # Create provider file with matching digests
        mainnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_MAINNET"])
        testnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_TESTNET"])
        self.create_provider_file([mainnet_digest, testnet_digest])

        with mock.patch("os.path.exists", return_value=True):
            with mock.patch("builtins.open", mock.mock_open(read_data=open(self.provider_path).read())):
                result = check_ohttp_release_values.check_values()
        self.assertTrue(result)

    def test_missing_value(self):
        """Check fails when a value is missing."""
        env = self.valid_values.copy()
        del env["ZODL_OHTTP_GATEWAY_HOST_MAINNET"]
        os.environ.update(env)

        result = check_ohttp_release_values.check_values()
        self.assertFalse(result)

    def test_empty_value(self):
        """Check fails when a value is empty."""
        env = self.valid_values.copy()
        env["ZODL_OHTTP_GATEWAY_HOST_MAINNET"] = ""
        os.environ.update(env)

        result = check_ohttp_release_values.check_values()
        self.assertFalse(result)

    def test_gateway_host_not_lowercase(self):
        """Check fails when gateway host has uppercase."""
        os.environ.update(self.valid_values)
        os.environ["ZODL_OHTTP_GATEWAY_HOST_MAINNET"] = "Gateway.example.com"
        mainnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_MAINNET"])
        testnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_TESTNET"])
        self.create_provider_file([mainnet_digest, testnet_digest])

        with mock.patch("os.path.exists", return_value=True):
            with mock.patch("builtins.open", mock.mock_open(read_data=open(self.provider_path).read())):
                result = check_ohttp_release_values.check_values()
        self.assertFalse(result)

    def test_relay_url_not_https(self):
        """Check fails when relay URL doesn't start with https://."""
        os.environ.update(self.valid_values)
        os.environ["ZODL_OHTTP_RELAY_URL_MAINNET"] = "http://relay.example.com/relay"
        mainnet_digest = check_ohttp_release_values.sha256_hex("http://relay.example.com/relay")
        testnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_TESTNET"])
        self.create_provider_file([mainnet_digest, testnet_digest])

        with mock.patch("os.path.exists", return_value=True):
            with mock.patch("builtins.open", mock.mock_open(read_data=open(self.provider_path).read())):
                result = check_ohttp_release_values.check_values()
        self.assertFalse(result)

    def test_signing_pubkey_not_hex_64(self):
        """Check fails when signing pubkey is not 64 hex chars."""
        os.environ.update(self.valid_values)
        os.environ["ZODL_OHTTP_SIGNING_PUBKEY_MAINNET"] = "not64hexchars"
        mainnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_MAINNET"])
        testnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_TESTNET"])
        self.create_provider_file([mainnet_digest, testnet_digest])

        with mock.patch("os.path.exists", return_value=True):
            with mock.patch("builtins.open", mock.mock_open(read_data=open(self.provider_path).read())):
                result = check_ohttp_release_values.check_values()
        self.assertFalse(result)

    def test_mainnet_testnet_values_same(self):
        """Check fails when mainnet and testnet values are the same."""
        os.environ.update(self.valid_values)
        os.environ["ZODL_OHTTP_GATEWAY_HOST_TESTNET"] = self.valid_values["ZODL_OHTTP_GATEWAY_HOST_MAINNET"]
        mainnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_MAINNET"])
        testnet_digest = check_ohttp_release_values.sha256_hex(self.valid_values["ZODL_OHTTP_RELAY_URL_TESTNET"])
        self.create_provider_file([mainnet_digest, testnet_digest])

        with mock.patch("os.path.exists", return_value=True):
            with mock.patch("builtins.open", mock.mock_open(read_data=open(self.provider_path).read())):
                result = check_ohttp_release_values.check_values()
        self.assertFalse(result)

    def test_relay_digest_not_in_list(self):
        """Check fails when relay URL digest is not in INDEPENDENT_RELAY_URL_SHA256."""
        os.environ.update(self.valid_values)
        # Create provider file with different digests
        self.create_provider_file(["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"])

        with mock.patch("os.path.exists", return_value=True):
            with mock.patch("builtins.open", mock.mock_open(read_data=open(self.provider_path).read())):
                result = check_ohttp_release_values.check_values()
        self.assertFalse(result)

    def test_provider_file_not_found(self):
        """Check fails when LightWalletEndpointProvider.kt is not found."""
        os.environ.update(self.valid_values)

        with mock.patch("os.path.exists", return_value=False):
            result = check_ohttp_release_values.check_values()
        self.assertFalse(result)


if __name__ == "__main__":
    unittest.main()
