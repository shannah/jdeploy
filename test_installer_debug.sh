#!/bin/bash
# Manually test the jDeploy installer.
#
# Replicates the "JDeploy Installer" IntelliJ run configuration:
#   Module classpath:  jdeploy-installer
#   VM options:        -Djdeploy.registry.url="https://dev.jdeploy.com/"
#   Main class:        ca.weblite.jdeploy.installer.MainDebug
#   Program args:      26AD 1.0.15
#   Working directory: <repo root>
#   Environment:       JDEPLOY_DEBUG=1
#
# Usage:
#   ./test_installer_debug.sh                       # defaults: code=26AD version=1.0.15
#   ./test_installer_debug.sh <code> [version]      # e.g. ./test_installer_debug.sh 26AD 1.0.16
#   ./test_installer_debug.sh <code> <version> install   # headless install mode
#   ./test_installer_debug.sh --build [args...]     # force rebuild of installer module first
#
# Environment overrides:
#   JDEPLOY_REGISTRY_URL  registry to download the bundle from (default https://dev.jdeploy.com/)
#   JAVA_HOME             JDK to use (default: JDK 1.8 located via /usr/libexec/java_home)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# The IntelliJ run config uses Java 1.8. Locate a JDK 8 unless JAVA_HOME is
# already a JDK (current JAVA_HOME may be a jDeploy-bundled JRE with no javac).
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 1.8 2>/dev/null || /usr/libexec/java_home)"
        export JAVA_HOME
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using JAVA_HOME: $JAVA_HOME"

REGISTRY_URL="${JDEPLOY_REGISTRY_URL:-https://dev.jdeploy.com/}"

FORCE_BUILD=0
if [ "$1" = "--build" ]; then
    FORCE_BUILD=1
    shift
fi

CODE="${1:-26AD}"
VERSION="${2:-1.0.15}"
shift 2 2>/dev/null || shift $# 2>/dev/null || true

CLASSES_DIR="$SCRIPT_DIR/installer/target/classes"
LIBS_DIR="$SCRIPT_DIR/installer/target/libs"

# Build the installer module (and its jdeploy-shared dependency) if needed.
# The maven-dependency-plugin copies runtime deps into target/libs during 'package'.
if [ "$FORCE_BUILD" = "1" ] || [ ! -d "$CLASSES_DIR" ] || [ ! -d "$LIBS_DIR" ]; then
    echo "Building jdeploy-installer module..."
    mvn -q -pl installer -am package -DskipTests
fi

echo "Registry: $REGISTRY_URL"
echo "Code:     $CODE"
echo "Version:  $VERSION"
echo

JDEPLOY_DEBUG=1 java \
    -Djdeploy.registry.url="$REGISTRY_URL" \
    -cp "$CLASSES_DIR:$LIBS_DIR/*" \
    ca.weblite.jdeploy.installer.MainDebug \
    "$CODE" "$VERSION" "$@"
