#!/usr/bin/env python3
"""Unit tests for ohttp-build-values.py."""
import json
import os
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

# Import the module under test
sys.path.insert(0, os.path.dirname(__file__))
import importlib.util
spec = importlib.util.spec_from_file_location(
    "ohttp_build_values",
    os.path.join(os.path.dirname(__file__), "ohttp-build-values.py")
)
ohttp_build_values = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ohttp_build_values)


class TestOhttpBuildValues(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.cache_file = os.path.join(self.temp_dir, "zodl-ohttp-values.properties")
        # Override cache path for tests
        os.environ["OHTTP_VALUES_CACHE_PATH"] = self.cache_file
        # Save and clear CI environment variable so AWS download tests can run
        self.saved_ci = os.environ.pop("CI", None)
        # Set fake AWS CLI path for tests
        self.fake_aws = os.path.join(self.temp_dir, "fake-aws")
        with open(self.fake_aws, "w") as f:
            f.write("#!/bin/sh\necho fake aws\n")
        os.chmod(self.fake_aws, 0o755)
        self.saved_aws = os.environ.get("OHTTP_VALUES_AWS")
        os.environ["OHTTP_VALUES_AWS"] = self.fake_aws

    def tearDown(self):
        if "OHTTP_VALUES_CACHE_PATH" in os.environ:
            del os.environ["OHTTP_VALUES_CACHE_PATH"]
        # Restore CI environment variable
        if self.saved_ci is not None:
            os.environ["CI"] = self.saved_ci
        # Restore AWS CLI environment variable
        if self.saved_aws is not None:
            os.environ["OHTTP_VALUES_AWS"] = self.saved_aws
        elif "OHTTP_VALUES_AWS" in os.environ:
            del os.environ["OHTTP_VALUES_AWS"]
        if os.path.exists(self.temp_dir):
            import shutil
            shutil.rmtree(self.temp_dir)

    def test_read_cache_empty(self):
        """Cache file doesn't exist returns empty dict."""
        result = ohttp_build_values.read_cache()
        self.assertEqual(result, {})

    def test_read_cache_with_values(self):
        """Cache file with values is parsed correctly."""
        with open(self.cache_file, "w") as f:
            f.write("# Comment\n")
            f.write("ZODL_OHTTP_GATEWAY_HOST_MAINNET=example.com\n")
            f.write("ZODL_OHTTP_RELAY_URL_TESTNET=https://relay.example\n")
            f.write("\n")
            f.write("# Another comment\n")
            f.write("ZODL_OHTTP_KEYS_DNS_NAME_MAINNET=keys.example.com\n")

        result = ohttp_build_values.read_cache()
        self.assertEqual(result, {
            "ZODL_OHTTP_GATEWAY_HOST_MAINNET": "example.com",
            "ZODL_OHTTP_RELAY_URL_TESTNET": "https://relay.example",
            "ZODL_OHTTP_KEYS_DNS_NAME_MAINNET": "keys.example.com",
        })

    def test_write_cache(self):
        """Writing cache creates file with mode 600."""
        values = {
            "ZODL_OHTTP_GATEWAY_HOST_MAINNET": "gw.example.com",
            "ZODL_OHTTP_RELAY_URL_MAINNET": "https://relay.example.com",
        }
        ohttp_build_values.write_cache(values)

        # Check file exists and has mode 600
        self.assertTrue(os.path.exists(self.cache_file))
        stat = os.stat(self.cache_file)
        mode = stat.st_mode & 0o777
        self.assertEqual(mode, 0o600, "Cache file should have mode 600")

        # Check content
        with open(self.cache_file, "r") as f:
            content = f.read()
        self.assertIn("ZODL_OHTTP_GATEWAY_HOST_MAINNET=gw.example.com", content)
        self.assertIn("ZODL_OHTTP_RELAY_URL_MAINNET=https://relay.example.com", content)

    def test_clear_cache(self):
        """Clearing cache deletes the file."""
        # Create a cache file
        with open(self.cache_file, "w") as f:
            f.write("ZODL_OHTTP_GATEWAY_HOST_MAINNET=example.com\n")

        self.assertTrue(os.path.exists(self.cache_file))
        ohttp_build_values.clear_cache()
        self.assertFalse(os.path.exists(self.cache_file))

    def test_clear_cache_nonexistent(self):
        """Clearing non-existent cache doesn't error."""
        self.assertFalse(os.path.exists(self.cache_file))
        ohttp_build_values.clear_cache()  # Should not raise

    @mock.patch("subprocess.run")
    def test_aws_download_success(self, mock_run):
        """AWS download with successful responses."""
        call_count = [0]

        # Mock successful AWS responses
        def mock_subprocess(command, **kwargs):
            call_count[0] += 1
            # First call should be the MFA pre-call (sts get-caller-identity)
            if call_count[0] == 1:
                self.assertEqual(command[1], "sts")
                self.assertEqual(command[2], "get-caller-identity")
                return mock.Mock(returncode=0, stdout="", stderr="")

            # Subsequent calls are get-secret-value
            secret_id = command[command.index("--secret-id") + 1]
            name = secret_id.split("/")[-1]
            if name == "ZODL_OHTTP_GATEWAY_HOST_MAINNET":
                return mock.Mock(returncode=0, stdout=json.dumps("gateway.example.com"), stderr="")
            elif name == "ZODL_OHTTP_RELAY_URL_MAINNET":
                return mock.Mock(returncode=0, stdout=json.dumps("https://relay.example.com"), stderr="")
            else:
                # Simulate not found for others
                return mock.Mock(returncode=254, stdout="", stderr="ResourceNotFoundException")

        mock_run.side_effect = mock_subprocess

        values, absent = ohttp_build_values.aws_download()

        # Verify MFA pre-call happened first
        self.assertGreaterEqual(call_count[0], 1)

        # Should have found two values
        self.assertEqual(values.get("ZODL_OHTTP_GATEWAY_HOST_MAINNET"), "gateway.example.com")
        self.assertEqual(values.get("ZODL_OHTTP_RELAY_URL_MAINNET"), "https://relay.example.com")

        # Others should be in absent list
        self.assertIn("ZODL_OHTTP_KEYS_DNS_NAME_MAINNET", absent)
        self.assertIn("ZODL_OHTTP_SIGNING_PUBKEY_MAINNET", absent)

    @mock.patch("subprocess.run")
    def test_aws_download_access_denied(self, mock_run):
        """AWS download with access denied error."""
        call_count = [0]

        def mock_subprocess(command, **kwargs):
            call_count[0] += 1
            # First call is MFA pre-call (succeeds), subsequent are get-secret-value (denied)
            if call_count[0] == 1:
                return mock.Mock(returncode=0, stdout="", stderr="")
            return mock.Mock(
                returncode=254,
                stdout="",
                stderr="AccessDeniedException: User is not authorized"
            )

        mock_run.side_effect = mock_subprocess

        with self.assertRaises(ohttp_build_values.ValuesError) as ctx:
            ohttp_build_values.aws_download()

        self.assertIn("access denied", str(ctx.exception).lower())

    @mock.patch("subprocess.run")
    def test_aws_download_expired_token(self, mock_run):
        """AWS download with expired credentials."""
        call_count = [0]

        def mock_subprocess(command, **kwargs):
            call_count[0] += 1
            # First call is MFA pre-call, subsequent are get-secret-value
            if call_count[0] == 1:
                return mock.Mock(returncode=0, stdout="", stderr="")
            return mock.Mock(
                returncode=255,
                stdout="",
                stderr="ExpiredToken: The security token included in the request is expired"
            )

        mock_run.side_effect = mock_subprocess

        with self.assertRaises(ohttp_build_values.ValuesError) as ctx:
            ohttp_build_values.aws_download()

        error_msg = str(ctx.exception).lower()
        self.assertIn("expired", error_msg)
        self.assertIn("mfa code", error_msg)
        self.assertIn("credentials", error_msg)

    def test_aws_download_in_ci(self):
        """AWS download skipped in CI environment."""
        os.environ["CI"] = "true"
        try:
            with self.assertRaises(ohttp_build_values.ValuesError) as ctx:
                ohttp_build_values.aws_download()
            self.assertIn("CI", str(ctx.exception))
        finally:
            del os.environ["CI"]

    @mock.patch("subprocess.run")
    def test_aws_download_mfa_pre_call_ordering(self, mock_run):
        """MFA pre-call happens before secret downloads."""
        calls = []

        def mock_subprocess(command, **kwargs):
            calls.append(command[1:3] if len(command) > 2 else command[1:])
            if "sts" in command:
                return mock.Mock(returncode=0, stdout="", stderr="")
            return mock.Mock(returncode=254, stdout="", stderr="ResourceNotFoundException")

        mock_run.side_effect = mock_subprocess

        try:
            ohttp_build_values.aws_download()
        except ohttp_build_values.ValuesError:
            pass

        # First call should be sts get-caller-identity
        self.assertGreater(len(calls), 0)
        self.assertEqual(calls[0], ["sts", "get-caller-identity"])
        # All subsequent calls should be secretsmanager get-secret-value
        for call in calls[1:]:
            self.assertEqual(call, ["secretsmanager", "get-secret-value"])

    def test_command_status_no_values(self):
        """Status command with no values."""
        result = ohttp_build_values.command_status(None)
        self.assertEqual(result, 0)

    def test_command_status_with_cache(self):
        """Status command with cached values."""
        # Write a cache file
        with open(self.cache_file, "w") as f:
            f.write("ZODL_OHTTP_GATEWAY_HOST_MAINNET=example.com\n")

        result = ohttp_build_values.command_status(None)
        self.assertEqual(result, 0)

    def test_command_clear(self):
        """Clear command deletes cache."""
        # Create a cache file
        with open(self.cache_file, "w") as f:
            f.write("ZODL_OHTTP_GATEWAY_HOST_MAINNET=example.com\n")

        result = ohttp_build_values.command_clear(None)
        self.assertEqual(result, 0)
        self.assertFalse(os.path.exists(self.cache_file))

    @mock.patch("subprocess.run")
    def test_command_refresh_success(self, mock_run):
        """Refresh command downloads and caches values."""
        call_count = [0]

        # Mock successful AWS response for one value
        def mock_subprocess(command, **kwargs):
            call_count[0] += 1
            # First call is MFA pre-call
            if call_count[0] == 1:
                return mock.Mock(returncode=0, stdout="", stderr="")

            # Subsequent calls are get-secret-value
            secret_id = command[command.index("--secret-id") + 1]
            name = secret_id.split("/")[-1]
            if name == "ZODL_OHTTP_GATEWAY_HOST_MAINNET":
                return mock.Mock(returncode=0, stdout=json.dumps("gateway.example.com"), stderr="")
            else:
                return mock.Mock(returncode=254, stdout="", stderr="ResourceNotFoundException")

        mock_run.side_effect = mock_subprocess

        result = ohttp_build_values.command_refresh(None)
        self.assertEqual(result, 0)

        # Check cache was written
        cached = ohttp_build_values.read_cache()
        self.assertEqual(cached.get("ZODL_OHTTP_GATEWAY_HOST_MAINNET"), "gateway.example.com")

    def test_names_constant(self):
        """NAMES constant has all 8 expected values."""
        self.assertEqual(len(ohttp_build_values.NAMES), 8)
        self.assertIn("ZODL_OHTTP_GATEWAY_HOST_MAINNET", ohttp_build_values.NAMES)
        self.assertIn("ZODL_OHTTP_RELAY_URL_MAINNET", ohttp_build_values.NAMES)
        self.assertIn("ZODL_OHTTP_KEYS_DNS_NAME_MAINNET", ohttp_build_values.NAMES)
        self.assertIn("ZODL_OHTTP_SIGNING_PUBKEY_MAINNET", ohttp_build_values.NAMES)
        self.assertIn("ZODL_OHTTP_GATEWAY_HOST_TESTNET", ohttp_build_values.NAMES)
        self.assertIn("ZODL_OHTTP_RELAY_URL_TESTNET", ohttp_build_values.NAMES)
        self.assertIn("ZODL_OHTTP_KEYS_DNS_NAME_TESTNET", ohttp_build_values.NAMES)
        self.assertIn("ZODL_OHTTP_SIGNING_PUBKEY_TESTNET", ohttp_build_values.NAMES)


if __name__ == "__main__":
    unittest.main()
