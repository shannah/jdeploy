#!/bin/bash
# e2e-npm-link-test.sh
# End-to-end test for the default `jdeploy install` (npm link) flow.
#
# Generates a picocli project, runs `jdeploy install` (default = npm link),
# and verifies that the project's bin command is on $PATH and runnable.
#
# Usage: ./e2e-npm-link-test.sh [OPTIONS]
#   --verbose           Show verbose output
#   --keep-projects     Don't delete the generated test project
#   --help              Show this help
#
# Exit codes:
#   0 - Test passed
#   1 - Test failed
#   2 - Configuration error

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
VERBOSE=false
KEEP_PROJECTS=false
RESULTS_DIR="${SCRIPT_DIR}/results-npm-link"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
LOG_FILE="${RESULTS_DIR}/e2e-npm-link-test-${TIMESTAMP}.log"
TEST_PROJECTS_DIR="${SCRIPT_DIR}/test-projects-npm-link"

for arg in "$@"; do
    case $arg in
        --verbose)      VERBOSE=true ;;
        --keep-projects) KEEP_PROJECTS=true ;;
        --help|-h)
            grep '^#' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo "Unknown option: $arg"
            exit 2
            ;;
    esac
done

mkdir -p "$RESULTS_DIR" "$TEST_PROJECTS_DIR"

log() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] $1" | tee -a "$LOG_FILE"
}

log_verbose() { [ "$VERBOSE" = true ] && log "$1" || true; }

# Locate jdeploy CLI jar built from source.
JDEPLOY_JAR="$PROJECT_ROOT/cli/target/jdeploy-cli-1.0-SNAPSHOT.jar"
if [ ! -f "$JDEPLOY_JAR" ]; then
    log "ERROR: jdeploy CLI JAR not found at $JDEPLOY_JAR"
    log "Please run 'mvn clean install' from the project root first."
    exit 2
fi
log "jdeploy CLI: $JDEPLOY_JAR"

run_jdeploy() { java -jar "$JDEPLOY_JAR" "$@"; }

if ! command -v java >/dev/null 2>&1; then
    log "ERROR: java is not installed"
    exit 2
fi
if ! command -v mvn >/dev/null 2>&1; then
    log "ERROR: maven is not installed (required to build the picocli template)"
    exit 2
fi
if ! command -v npm >/dev/null 2>&1; then
    log "ERROR: npm is not installed"
    exit 2
fi

PROJECT_NAME="test-npm-link-${TIMESTAMP}"
PROJECT_DIR="${TEST_PROJECTS_DIR}/${PROJECT_NAME}"
INSTALL_LOG="${RESULTS_DIR}/install.log"

cleanup() {
    log "Cleaning up..."
    # npm unlink to remove the global symlink we created.
    if [ -d "$PROJECT_DIR" ]; then
        (cd "$PROJECT_DIR" && npm unlink -g 2>/dev/null || true)
    fi
    if [ "$KEEP_PROJECTS" = false ] && [ -d "$PROJECT_DIR" ]; then
        rm -rf "$PROJECT_DIR"
    fi
}
trap cleanup EXIT

log "=========================================="
log "jDeploy npm-link install E2E test"
log "=========================================="

log "Generating picocli project: $PROJECT_NAME"
run_jdeploy generate \
    -t picocli \
    -d "$TEST_PROJECTS_DIR" \
    -n "$PROJECT_NAME" \
    --appTitle="NPM Link Test" \
    -g com.test.npmlink \
    -a "$PROJECT_NAME" \
    --mainClassName=Main 2>&1 | tee -a "$LOG_FILE"

cd "$PROJECT_DIR"

log "Building project (mvn clean package)..."
mvn clean package -q 2>&1 | tee -a "$LOG_FILE"

# Read the bin command name from package.json. The picocli template registers
# the command name passed to --appTitle, lowercased / hyphenated.
BIN_NAME=$(python3 -c "import json,sys; print(list(json.load(open('package.json'))['bin'].keys())[0])" 2>/dev/null || \
           node -e "console.log(Object.keys(require('./package.json').bin)[0])")
if [ -z "$BIN_NAME" ]; then
    log "ERROR: could not read bin command name from package.json"
    exit 1
fi
log "Bin command: $BIN_NAME"

# Sanity: the command should NOT exist before install (avoid confusing a
# residual symlink on the developer's machine for a successful install).
if command -v "$BIN_NAME" >/dev/null 2>&1; then
    log "WARNING: '$BIN_NAME' was already on \$PATH before install, at $(command -v "$BIN_NAME")"
    log "         Test will only verify that it remains on \$PATH after install."
fi

log "Running: jdeploy install (default = npm link)..."
run_jdeploy install 2>&1 | tee -a "$INSTALL_LOG" | tee -a "$LOG_FILE"

log "Verifying bin command is on \$PATH..."
if ! command -v "$BIN_NAME" >/dev/null 2>&1; then
    log "FAIL: '$BIN_NAME' is not on \$PATH after 'jdeploy install'"
    log "npm global prefix: $(npm prefix -g 2>/dev/null || echo unknown)"
    log "npm global bin contents:"
    ls -la "$(npm prefix -g 2>/dev/null)/bin" 2>&1 | tee -a "$LOG_FILE" || true
    exit 1
fi
log "OK: '$BIN_NAME' resolves to $(command -v "$BIN_NAME")"

log "Running '$BIN_NAME --help' to verify it executes..."
# Capture output and exit status without aborting on non-zero — picocli
# templates may legitimately return non-zero for --help (e.g. exit 2 when
# usage is treated as an error). The thing this test really cares about
# is that the symlink resolves to something actually executable.
set +e
help_output=$("$BIN_NAME" --help 2>&1)
help_status=$?
set -e
log "Exit status: $help_status"
log "Output (first 40 lines):"
printf '%s\n' "$help_output" | head -40 | tee -a "$LOG_FILE"
echo "$help_output" >> "$LOG_FILE"

# 126 = permission denied, 127 = command not found / shim missing.
# Anything else means the bin shim and its dependencies (Node.js, jar,
# etc.) actually loaded and ran.
if [ "$help_status" -eq 126 ] || [ "$help_status" -eq 127 ]; then
    log "FAIL: '$BIN_NAME' could not be executed (exit $help_status)"
    exit 1
fi
log "OK: '$BIN_NAME' executed (exit $help_status)"

log "All checks passed."
exit 0
