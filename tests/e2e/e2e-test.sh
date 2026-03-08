#!/bin/bash
# e2e-test.sh
# End-to-end test script for jDeploy installations on Linux and macOS.
# Designed to run natively (no Docker required) in CI environments.
#
# Usage: ./e2e-test.sh [OPTIONS]
#   --app=NAME          Test only the specified app (by package name)
#   --skip-uninstall    Skip uninstall testing
#   --verbose           Show verbose output
#   --jdeploy-url=URL   Use custom jdeploy.com URL (default: www.jdeploy.com)
#   --config=FILE       Use custom apps config file
#
# Exit codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - Configuration error

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/apps.conf"
JDEPLOY_URL="www.jdeploy.com"
SKIP_UNINSTALL=false
VERBOSE=false
SINGLE_APP=""
RESULTS_DIR="${SCRIPT_DIR}/results"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
LOG_FILE="${RESULTS_DIR}/e2e-test-${TIMESTAMP}.log"

# Parse arguments
for arg in "$@"; do
    case $arg in
        --app=*)
            SINGLE_APP="${arg#*=}"
            ;;
        --skip-uninstall)
            SKIP_UNINSTALL=true
            ;;
        --verbose)
            VERBOSE=true
            ;;
        --jdeploy-url=*)
            JDEPLOY_URL="${arg#*=}"
            ;;
        --config=*)
            CONFIG_FILE="${arg#*=}"
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo "  --app=NAME          Test only the specified app"
            echo "  --skip-uninstall    Skip uninstall testing"
            echo "  --verbose           Show verbose output"
            echo "  --jdeploy-url=URL   Use custom jdeploy URL"
            echo "  --config=FILE       Use custom apps config file"
            exit 0
            ;;
        *)
            echo "Unknown option: $arg"
            exit 2
            ;;
    esac
done

# Create results directory
mkdir -p "$RESULTS_DIR"

# Logging function
log() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] $1" | tee -a "$LOG_FILE"
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        log "$1"
    fi
}

# Detect platform
detect_platform() {
    case "$(uname -s)" in
        Linux*)  echo "linux" ;;
        Darwin*) echo "mac" ;;
        *)       echo "unknown" ;;
    esac
}

PLATFORM=$(detect_platform)
log "Detected platform: $PLATFORM"

# Setup jdeploy from source
setup_jdeploy() {
    log "Setting up jdeploy from source..."

    JDEPLOY_JAR="$PROJECT_ROOT/cli/target/jdeploy-cli-1.0-SNAPSHOT.jar"

    if [ ! -f "$JDEPLOY_JAR" ]; then
        log "ERROR: jdeploy CLI JAR not found at $JDEPLOY_JAR"
        log "Please run 'mvn clean install' from the project root first."
        exit 2
    fi

    # Create a wrapper function for jdeploy
    jdeploy() {
        java -jar "$JDEPLOY_JAR" "$@"
    }
    export -f jdeploy
    export JDEPLOY_JAR

    log "jdeploy CLI ready: $JDEPLOY_JAR"
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."

    # Check for curl or wget
    if ! command -v curl &> /dev/null && ! command -v wget &> /dev/null; then
        log "ERROR: Neither curl nor wget is installed"
        return 1
    fi

    # Check for Java
    if ! command -v java &> /dev/null; then
        log "ERROR: Java is not installed"
        return 1
    fi

    # Check for jq (optional)
    if ! command -v jq &> /dev/null; then
        log "WARNING: jq not installed, some features may be limited"
    fi

    log "Prerequisites check passed"
    return 0
}

# Download and run installer for an app
install_app() {
    local package_name="$1"
    local source_url="$2"
    local app_log="${RESULTS_DIR}/${package_name}-install.log"

    log "Installing ${package_name}..."

    # Create temp directory for installer
    local temp_dir=$(mktemp -d)
    cd "$temp_dir"

    # Construct install script URL
    local install_url
    if [[ "$source_url" == https://github.com/* ]]; then
        local gh_path="${source_url#https://github.com/}"
        install_url="https://${JDEPLOY_URL}/gh/${gh_path}/install.sh?headless=true"
    else
        install_url="https://${JDEPLOY_URL}/~${package_name}/install.sh?headless=true"
    fi

    log_verbose "Install URL: $install_url"

    # Download install script
    if command -v curl &> /dev/null; then
        curl -fsSL "$install_url" -o install.sh 2>&1 | tee -a "$app_log" || {
            log "ERROR: Failed to download install script for ${package_name}"
            cd /
            rm -rf "$temp_dir"
            return 1
        }
    else
        wget -q "$install_url" -O install.sh 2>&1 | tee -a "$app_log" || {
            log "ERROR: Failed to download install script for ${package_name}"
            cd /
            rm -rf "$temp_dir"
            return 1
        }
    fi

    # Make executable and run
    chmod +x install.sh

    log "Running headless installer for ${package_name}..."
    bash install.sh 2>&1 | tee -a "$app_log" || {
        log "ERROR: Installation failed for ${package_name}"
        cd /
        rm -rf "$temp_dir"
        return 1
    }

    # Cleanup temp directory
    cd /
    rm -rf "$temp_dir"

    log "Installation completed for ${package_name}"
    return 0
}

# Verify installation using jdeploy verify-installation command
verify_installation() {
    local package_name="$1"
    local source_url="$2"
    local app_log="${RESULTS_DIR}/${package_name}-verify-install.log"

    log "Verifying installation for ${package_name}..."

    local cmd="java -jar \"$JDEPLOY_JAR\" verify-installation --package=${package_name} --verbose"
    if [ -n "$source_url" ]; then
        cmd="$cmd --source=${source_url}"
    fi

    log_verbose "Running: $cmd"
    eval "$cmd" 2>&1 | tee -a "$app_log"
    local exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
        log "Verification PASSED for ${package_name}"
        return 0
    else
        log "Verification FAILED for ${package_name} (exit code: $exit_code)"
        return 1
    fi
}

# Uninstall an app using jdeploy CLI uninstall command
uninstall_app() {
    local package_name="$1"
    local source_url="$2"
    local app_log="${RESULTS_DIR}/${package_name}-uninstall.log"

    log "Uninstalling ${package_name}..."

    local cmd="java -jar \"$JDEPLOY_JAR\" uninstall --package=${package_name}"
    if [ -n "$source_url" ]; then
        cmd="$cmd --source=${source_url}"
    fi

    log_verbose "Running: $cmd"
    eval "$cmd" 2>&1 | tee -a "$app_log"
    local exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
        log "Uninstall completed for ${package_name}"
        return 0
    else
        log "Uninstall failed for ${package_name} (exit code: $exit_code)"
        return 1
    fi
}

# Verify uninstallation using jdeploy verify-uninstallation command
verify_uninstallation() {
    local package_name="$1"
    local source_url="$2"
    local app_log="${RESULTS_DIR}/${package_name}-verify-uninstall.log"

    log "Verifying uninstallation for ${package_name}..."

    local cmd="java -jar \"$JDEPLOY_JAR\" verify-uninstallation --package=${package_name} --verbose"
    if [ -n "$source_url" ]; then
        cmd="$cmd --source=${source_url}"
    fi

    log_verbose "Running: $cmd"
    eval "$cmd" 2>&1 | tee -a "$app_log"
    local exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
        log "Uninstallation verification PASSED for ${package_name}"
        return 0
    else
        log "Uninstallation verification FAILED for ${package_name} (exit code: $exit_code)"
        return 1
    fi
}

# Test a single application
test_app() {
    local package_name="$1"
    local source_url="$2"
    local description="$3"

    log "=========================================="
    log "Testing: ${package_name}"
    log "Source: ${source_url:-npm}"
    log "Description: ${description}"
    log "=========================================="

    local test_passed=true
    local result_file="${RESULTS_DIR}/${package_name}-result.txt"

    # Step 1: Install
    if ! install_app "$package_name" "$source_url"; then
        echo "INSTALL_FAILED" > "$result_file"
        return 1
    fi

    # Step 2: Verify installation
    if ! verify_installation "$package_name" "$source_url"; then
        echo "VERIFY_INSTALL_FAILED" > "$result_file"
        test_passed=false
    fi

    # Step 3: Uninstall (if not skipped)
    if [ "$SKIP_UNINSTALL" = false ]; then
        if ! uninstall_app "$package_name" "$source_url"; then
            echo "UNINSTALL_FAILED" > "$result_file"
            test_passed=false
        fi

        # Step 4: Verify uninstallation
        if ! verify_uninstallation "$package_name" "$source_url"; then
            echo "VERIFY_UNINSTALL_FAILED" > "$result_file"
            test_passed=false
        fi
    fi

    if [ "$test_passed" = true ]; then
        echo "PASSED" > "$result_file"
        log "Test PASSED for ${package_name}"
        return 0
    else
        log "Test FAILED for ${package_name}"
        return 1
    fi
}

# Main execution
main() {
    log "=========================================="
    log "jDeploy E2E Installation Tests"
    log "=========================================="
    log "Platform: $PLATFORM"
    log "Timestamp: $TIMESTAMP"
    log "Config: $CONFIG_FILE"
    log "jDeploy URL: $JDEPLOY_URL"
    log "Skip Uninstall: $SKIP_UNINSTALL"
    log ""

    # Check prerequisites
    if ! check_prerequisites; then
        log "ERROR: Prerequisites check failed"
        exit 2
    fi

    # Setup jdeploy from source
    setup_jdeploy

    # Check config file
    if [ ! -f "$CONFIG_FILE" ]; then
        log "ERROR: Config file not found: $CONFIG_FILE"
        exit 2
    fi

    # Read applications from config
    local total_apps=0
    local passed_apps=0
    local failed_apps=0
    local failed_list=""

    while IFS='|' read -r package_name source_url description || [ -n "$package_name" ]; do
        # Skip empty lines and comments
        [[ -z "$package_name" || "$package_name" =~ ^[[:space:]]*# ]] && continue

        # Trim whitespace
        package_name=$(echo "$package_name" | xargs)
        source_url=$(echo "$source_url" | xargs)
        description=$(echo "$description" | xargs)

        # Filter by single app if specified
        if [ -n "$SINGLE_APP" ] && [ "$package_name" != "$SINGLE_APP" ]; then
            continue
        fi

        total_apps=$((total_apps + 1))

        if test_app "$package_name" "$source_url" "$description"; then
            passed_apps=$((passed_apps + 1))
        else
            failed_apps=$((failed_apps + 1))
            failed_list="${failed_list}  - ${package_name}\n"
        fi

        log ""
    done < "$CONFIG_FILE"

    # Print summary
    log "=========================================="
    log "E2E Test Summary"
    log "=========================================="
    log "Total apps tested: $total_apps"
    log "Passed: $passed_apps"
    log "Failed: $failed_apps"

    if [ -n "$failed_list" ]; then
        log ""
        log "Failed apps:"
        echo -e "$failed_list" | tee -a "$LOG_FILE"
    fi

    # Write summary JSON
    cat > "${RESULTS_DIR}/summary.json" << EOF
{
    "timestamp": "$TIMESTAMP",
    "platform": "$PLATFORM",
    "totalApps": $total_apps,
    "passed": $passed_apps,
    "failed": $failed_apps,
    "success": $([ $failed_apps -eq 0 ] && echo "true" || echo "false")
}
EOF

    log ""
    log "Results saved to: $RESULTS_DIR"
    log "Log file: $LOG_FILE"

    # Exit with appropriate code
    if [ $failed_apps -gt 0 ]; then
        exit 1
    fi
    exit 0
}

# Run main
main
