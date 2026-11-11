#!/usr/bin/env python3
"""Manage OHTTP configuration values for local development builds.

Commands:
  refresh    download from AWS Secrets Manager and cache locally
  clear      delete the local cache
  status     show which values are present (names only, never values)

Sources for Gradle builds, in order:
  1. Environment variables or Gradle properties (CI, unchanged)
  2. Local cache file ~/.gradle/zodl-ohttp-values.properties
  3. (refresh command only) AWS Secrets Manager via the aws CLI

The cache file is created by the refresh command and is never written into the
repository tree. Diagnostics go to stderr and never contain a value.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

NETWORKS = ("MAINNET", "TESTNET")
FIELDS = ("GATEWAY_HOST", "RELAY_URL", "KEYS_DNS_NAME", "SIGNING_PUBKEY")
NAMES = ["ZODL_OHTTP_%s_%s" % (field, network) for network in NETWORKS for field in FIELDS]
DEFAULT_CACHE_PATH = os.path.join(os.path.expanduser("~"), ".gradle", "zodl-ohttp-values.properties")
DEFAULT_AWS_PREFIX = "/github/zodl-android-private/"
DEFAULT_AWS_REGION = "us-east-1"
DEFAULT_AWS_PROFILE = "zodl-ohttp"


class ValuesError(Exception):
    pass


def warn(message):
    print("warning: %s" % message, file=sys.stderr)


def error(message):
    print("error: %s" % message, file=sys.stderr)


def cache_path():
    return os.environ.get("OHTTP_VALUES_CACHE_PATH") or DEFAULT_CACHE_PATH


def read_cache():
    """Return dict of values from cache file, or empty dict if not found."""
    path = cache_path()
    values = {}
    try:
        with open(path, "r") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if "=" in line:
                    key, _, value = line.partition("=")
                    key = key.strip()
                    value = value.strip()
                    if key in NAMES and value:
                        values[key] = value
    except FileNotFoundError:
        pass
    except (OSError, ValueError) as e:
        warn("could not read cache at %s: %s" % (path, e))
    return values


def write_cache(values):
    """Write values dict to cache file with mode 600."""
    path = cache_path()
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        # Create with mode 600
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as f:
            f.write("# OHTTP build values cache (mode 600, never commit)\n")
            f.write("# Managed by scripts/ohttp-build-values.py\n\n")
            for name in NAMES:
                if name in values:
                    f.write("%s=%s\n" % (name, values[name]))
        # Ensure mode 600 in case umask interfered
        os.chmod(path, 0o600)
    except (OSError, ValueError) as e:
        raise ValuesError("could not write cache at %s: %s" % (path, e))


def clear_cache():
    """Delete cache file."""
    path = cache_path()
    try:
        os.unlink(path)
    except FileNotFoundError:
        pass
    except OSError as e:
        warn("could not delete cache at %s: %s" % (path, e))


def find_aws():
    """Find aws CLI executable."""
    if "OHTTP_VALUES_AWS" in os.environ:
        return os.environ["OHTTP_VALUES_AWS"] or None
    return shutil.which("aws")


def aws_download():
    """Download values from AWS Secrets Manager.

    Returns (values found dict, names AWS reported absent list).
    Raises ValuesError with a safe reason on failure.
    """
    if os.environ.get("CI"):
        raise ValuesError("CI env is set; AWS download is skipped in CI")

    aws = find_aws()
    if not aws:
        raise ValuesError("the aws CLI was not found (install it or set OHTTP_VALUES_AWS)")

    prefix = os.environ.get("OHTTP_VALUES_AWS_PREFIX") or DEFAULT_AWS_PREFIX
    region = os.environ.get("OHTTP_VALUES_AWS_REGION") or DEFAULT_AWS_REGION
    profile = os.environ.get("AWS_PROFILE") or DEFAULT_AWS_PROFILE

    environment = dict(os.environ, AWS_MAX_ATTEMPTS="1", AWS_PROFILE=profile)

    # Trigger MFA prompt interactively before the captured calls. The profile assumes a role with
    # mfa_serial, so the CLI prompts "Enter MFA code" on the terminal. With capture_output=True
    # the prompt is invisible and every call times out. This pre-call lets stderr/stdin through,
    # and the CLI caches the assumed-role credentials in ~/.aws/cli/cache for the following calls.
    try:
        subprocess.run(
            [aws, "sts", "get-caller-identity", "--query", "Arn", "--output", "text"],
            env=environment,
            stdout=subprocess.DEVNULL,
            timeout=120
        )
    except (OSError, subprocess.SubprocessError) as e:
        raise ValuesError("AWS pre-call failed: %s" % e)

    values = {}
    absent = []

    for name in NAMES:
        command = [
            aws, "secretsmanager", "get-secret-value",
            "--secret-id", prefix + name,
            "--region", region,
            "--output", "json",
            "--query", "SecretString",
            "--cli-connect-timeout", "5",
            "--cli-read-timeout", "15"
        ]
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=20,
                env=environment
            )
        except (OSError, subprocess.SubprocessError) as e:
            raise ValuesError("AWS request failed or timed out: %s" % e)

        if result.returncode != 0:
            stderr = result.stderr.strip()
            if "ResourceNotFoundException" in stderr:
                absent.append(name)
                continue
            if "AccessDeniedException" in stderr:
                raise ValuesError("AWS access denied (check your profile %s has secretsmanager:GetSecretValue on %s*)" % (profile, prefix + "ZODL_OHTTP_"))
            if "ExpiredToken" in stderr or "InvalidClientTokenId" in stderr:
                raise ValuesError("AWS credentials expired or invalid for profile %s: re-run `refresh` and enter a fresh MFA code, or check ~/.aws/credentials" % profile)
            raise ValuesError("AWS request failed (exit %d): %s" % (result.returncode, stderr[:200]))

        try:
            # The output is JSON-encoded string; parse and strip quotes
            secret = json.loads(result.stdout.strip())
        except (ValueError, TypeError) as e:
            raise ValuesError("AWS answer was not understood: %s" % e)

        if isinstance(secret, str) and secret:
            values[name] = secret
        else:
            absent.append(name)

    if not values:
        raise ValuesError("AWS returned none of the OHTTP secrets (check secret names at %s*)" % (prefix + "ZODL_OHTTP_"))

    return values, absent


def command_refresh(_args):
    try:
        values, absent = aws_download()
    except ValuesError as e:
        error("refresh failed: %s" % e)
        return 1

    try:
        write_cache(values)
    except ValuesError as e:
        error(str(e))
        return 1

    print("Cache updated at %s:" % cache_path())
    print("  present: " + ", ".join(n for n in NAMES if n in values))
    if absent:
        print("  absent in AWS: " + ", ".join(absent))

    return 0


def command_clear(_args):
    clear_cache()
    print("Cache cleared: %s" % cache_path())
    return 0


def command_status(_args):
    env = [n for n in NAMES if os.environ.get(n)]
    print("environment: " + (", ".join(env) or "none"))

    cached = read_cache()
    print("cache (%s): %s" % (cache_path(), ", ".join(cached.keys()) or "none"))

    if env:
        print("source: environment")
    elif cached:
        print("source: cache")
    else:
        print("source: none (run `./scripts/ohttp-build-values.py refresh` to cache AWS values)")

    return 0


def main(argv):
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    sub = parser.add_subparsers(dest="command", required=True)

    for name, run in (
        ("refresh", command_refresh),
        ("clear", command_clear),
        ("status", command_status)
    ):
        sub.add_parser(name).set_defaults(run=run)

    args = parser.parse_args(argv)
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
