#!/bin/bash
# Setup script for SoftHSM2-based PKCS#11 signing integration tests.
#
# This installs SoftHSM2 on the CI runner so that
# WindowsSigningPkcs11IntegrationTest can exercise the full PKCS#11 signing
# path without a real HSM subscription.
#
# Usage:
#   sudo bash cli/src/test/resources/softhsm2-test-setup.sh
#
# Supported platforms:
#   - Ubuntu/Debian (apt)
#   - macOS (brew)
#   - RHEL/CentOS/Fedora (dnf/yum)
#
# After running this script, the following should be available:
#   - softhsm2-util
#   - libsofthsm2.so (or .dylib on macOS)
#   - keytool (from JDK, assumed pre-installed)

set -euo pipefail

echo "=== SoftHSM2 Test Setup ==="

install_ubuntu() {
    apt-get update -qq
    apt-get install -y -qq softhsm2 opensc
}

install_macos() {
    brew install softhsm opensc
}

install_rhel() {
    if command -v dnf &>/dev/null; then
        dnf install -y softhsm opensc
    else
        yum install -y softhsm opensc
    fi
}

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    if command -v apt-get &>/dev/null; then
        install_ubuntu
    elif command -v dnf &>/dev/null || command -v yum &>/dev/null; then
        install_rhel
    else
        echo "ERROR: Unsupported Linux distribution" >&2
        exit 1
    fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
    install_macos
else
    echo "ERROR: Unsupported OS: $OSTYPE" >&2
    exit 1
fi

# Verify installation
echo ""
echo "=== Verification ==="
echo "softhsm2-util: $(which softhsm2-util)"
echo "Library:"
find /usr/lib /usr/local/lib /opt/homebrew/lib -name "libsofthsm2.*" 2>/dev/null || echo "  (not found in standard paths)"
echo ""
echo "SoftHSM2 is ready for PKCS#11 integration tests."
