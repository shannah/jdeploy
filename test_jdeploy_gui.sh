#!/bin/bash
# Launch the jDeploy GUI for manual testing.
#
# Replicates the "JDeploy" IntelliJ run configuration:
#   Module classpath:  jdeploy-cli
#   Main class:        ca.weblite.jdeploy.JDeploy
#   Program args:      (none)
#   Working directory: /Users/shannah/jdeploy-demos/jdeploy-service-example
#
# Usage:
#   ./test_jdeploy_gui.sh                      # open the default demo project
#   ./test_jdeploy_gui.sh <project-dir>        # open a different project
#   ./test_jdeploy_gui.sh --build [project-dir]  # force rebuild of cli module first
#
# Environment overrides:
#   JDEPLOY_PROJECT_DIR  project to open (same as passing <project-dir>)
#   JAVA_HOME            JDK to use (default: JDK 1.8 located via /usr/libexec/java_home)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# The IntelliJ run config uses Java 8. Locate a JDK 8 unless JAVA_HOME is
# already a JDK (current JAVA_HOME may be a jDeploy-bundled JRE with no javac).
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 1.8 2>/dev/null || /usr/libexec/java_home)"
        export JAVA_HOME
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using JAVA_HOME: $JAVA_HOME"

FORCE_BUILD=0
if [ "$1" = "--build" ]; then
    FORCE_BUILD=1
    shift
fi

PROJECT_DIR="${1:-${JDEPLOY_PROJECT_DIR:-/Users/shannah/jdeploy-demos/jdeploy-service-example}}"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "Error: project directory does not exist: $PROJECT_DIR" >&2
    exit 1
fi

CLASSES_DIR="$SCRIPT_DIR/cli/target/classes"
LIBS_DIR="$SCRIPT_DIR/cli/target/libs"

# Build the cli module (and its jdeploy-shared/jdeploy-installer dependencies)
# if needed. The maven-dependency-plugin copies runtime deps into target/libs
# during 'package'.
if [ "$FORCE_BUILD" = "1" ] || [ ! -d "$CLASSES_DIR" ] || [ ! -d "$LIBS_DIR" ]; then
    echo "Building jdeploy-cli module..."
    (cd "$SCRIPT_DIR" && mvn -q -pl cli -am package -DskipTests)
fi

echo "Project: $PROJECT_DIR"
echo

cd "$PROJECT_DIR"
java \
    -cp "$CLASSES_DIR:$LIBS_DIR/*" \
    ca.weblite.jdeploy.JDeploy
