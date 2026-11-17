#!/usr/bin/env python3
"""Check OHTTP configuration values for release builds.

Validates that:
1. All eight required values are present (non-empty)
2. Gateway hosts and keys DNS names are lowercase DNS names
3. Relay URLs start with https:// and have a host
4. Signing public keys are 64 hex characters
5. Mainnet and testnet values differ
6. SHA-256 of each relay URL is in INDEPENDENT_RELAY_URL_SHA256

Reads values from environment variables (GitHub Actions secrets) and
LightWalletEndpointProvider.kt for the relay digest list. Prints no values,
only presence and validation errors.

Exit codes:
  0  all checks passed
  1  one or more checks failed
"""
import hashlib
import os
import re
import sys


NETWORKS = ("MAINNET", "TESTNET")
FIELDS = ("GATEWAY_HOST", "RELAY_URL", "KEYS_DNS_NAME", "SIGNING_PUBKEY")
NAMES = [f"ZODL_OHTTP_{field}_{network}" for network in NETWORKS for field in FIELDS]

# Lowercase DNS name pattern: letters, digits, hyphens, dots; no leading/trailing hyphen
DNS_NAME_PATTERN = re.compile(r'^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$')
HTTPS_URL_PATTERN = re.compile(r'^https://[^/]+')
HEX_64_PATTERN = re.compile(r'^[0-9a-f]{64}$')


def sha256_hex(value):
    """Compute SHA-256 digest of UTF-8 encoded value as hex string."""
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def error(message):
    """Print error message with GitHub Actions annotation format."""
    print(f"::error::{message}", file=sys.stderr)


def read_independent_relay_digests(provider_path):
    """Extract INDEPENDENT_RELAY_URL_SHA256 set from LightWalletEndpointProvider.kt."""
    try:
        with open(provider_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Find the INDEPENDENT_RELAY_URL_SHA256 set definition
        match = re.search(
            r'private val INDEPENDENT_RELAY_URL_SHA256\s*=\s*setOf\s*\((.*?)\)',
            content,
            re.DOTALL
        )
        if not match:
            error(f"Could not find INDEPENDENT_RELAY_URL_SHA256 in {provider_path}")
            return None

        # Extract quoted strings from the set
        set_content = match.group(1)
        digests = re.findall(r'"([0-9a-f]{64})"', set_content)

        if not digests:
            error(f"No digests found in INDEPENDENT_RELAY_URL_SHA256 in {provider_path}")
            return None

        return set(digests)
    except Exception as e:
        error(f"Failed to read relay digests from {provider_path}: {e}")
        return None


def check_values():
    """Check all OHTTP release values. Returns True if all checks passed."""
    errors = 0

    # Check 1: Presence
    values = {}
    for name in NAMES:
        value = os.environ.get(name, "")
        if not value:
            error(f"{name} is not set or empty.")
            errors += 1
        values[name] = value

    if errors > 0:
        return False

    # Extract values by network for further checks
    mainnet_values = {
        field: values[f"ZODL_OHTTP_{field}_MAINNET"]
        for field in FIELDS
    }
    testnet_values = {
        field: values[f"ZODL_OHTTP_{field}_TESTNET"]
        for field in FIELDS
    }

    # Check 2: Gateway hosts are lowercase DNS names
    for network, net_values in [("mainnet", mainnet_values), ("testnet", testnet_values)]:
        host = net_values["GATEWAY_HOST"]
        if not DNS_NAME_PATTERN.match(host):
            error(f"ZODL_OHTTP_GATEWAY_HOST_{network.upper()} must be a lowercase DNS name.")
            errors += 1

    # Check 3: Keys DNS names are lowercase DNS names
    for network, net_values in [("mainnet", mainnet_values), ("testnet", testnet_values)]:
        dns_name = net_values["KEYS_DNS_NAME"]
        if not DNS_NAME_PATTERN.match(dns_name):
            error(f"ZODL_OHTTP_KEYS_DNS_NAME_{network.upper()} must be a lowercase DNS name.")
            errors += 1

    # Check 4: Relay URLs start with https:// and have a host
    relay_urls = {}
    for network, net_values in [("mainnet", mainnet_values), ("testnet", testnet_values)]:
        relay_url = net_values["RELAY_URL"]
        if not HTTPS_URL_PATTERN.match(relay_url):
            error(f"ZODL_OHTTP_RELAY_URL_{network.upper()} must start with https:// and have a host.")
            errors += 1
        else:
            relay_urls[network] = relay_url

    # Check 5: Signing public keys are 64 hex chars
    for network, net_values in [("mainnet", mainnet_values), ("testnet", testnet_values)]:
        pubkey = net_values["SIGNING_PUBKEY"]
        if not HEX_64_PATTERN.match(pubkey):
            error(f"ZODL_OHTTP_SIGNING_PUBKEY_{network.upper()} must be 64 lowercase hex characters.")
            errors += 1

    # Check 6: Mainnet and testnet values differ
    for field in FIELDS:
        if mainnet_values[field] == testnet_values[field]:
            error(f"ZODL_OHTTP_{field}_MAINNET and ZODL_OHTTP_{field}_TESTNET must differ.")
            errors += 1

    # Check 7: Relay URL digests are in INDEPENDENT_RELAY_URL_SHA256
    provider_path = "ui-lib/src/main/java/co/electriccoin/zcash/ui/common/provider/LightWalletEndpointProvider.kt"
    if not os.path.exists(provider_path):
        error(f"Could not find {provider_path} to check relay digests.")
        errors += 1
    else:
        independent_digests = read_independent_relay_digests(provider_path)
        if independent_digests is None:
            errors += 1
        else:
            for network, relay_url in relay_urls.items():
                digest = sha256_hex(relay_url)
                if digest not in independent_digests:
                    error(
                        f"ZODL_OHTTP_RELAY_URL_{network.upper()} digest is not in "
                        f"INDEPENDENT_RELAY_URL_SHA256 (LightWalletEndpointProvider.kt). "
                        f"The release will not be marked as private."
                    )
                    errors += 1

    return errors == 0


def main():
    if not check_values():
        sys.exit(1)
    print("All OHTTP release value checks passed.", file=sys.stderr)
    sys.exit(0)


if __name__ == "__main__":
    main()
