#!/bin/bash
# e2e-local-test.sh
# End-to-end test script for jDeploy local project lifecycle on Linux and macOS.
# Tests: generate project -> build -> install -> verify -> uninstall -> verify
#
# Usage: ./e2e-local-test.sh [OPTIONS]
#   --template=NAME     Test only the specified template
#   --skip-uninstall    Skip uninstall testing
#   --verbose           Show verbose output
#   --config=FILE       Use custom templates config file
#   --keep-projects     Don't delete generated test projects
#
# Exit codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - Configuration error

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/templates.conf"
SKIP_UNINSTALL=false
VERBOSE=false
KEEP_PROJECTS=false
SINGLE_TEMPLATE=""
RESULTS_DIR="${SCRIPT_DIR}/results-local"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
LOG_FILE="${RESULTS_DIR}/e2e-local-test-${TIMESTAMP}.log"
TEST_PROJECTS_DIR="${SCRIPT_DIR}/test-projects"

# Parse arguments
for arg in "$@"; do
    case $arg in
        --template=*)
            SINGLE_TEMPLATE="${arg#*=}"
            ;;
        --skip-uninstall)
            SKIP_UNINSTALL=true
            ;;
        --verbose)
            VERBOSE=true
            ;;
        --config=*)
            CONFIG_FILE="${arg#*=}"
            ;;
        --keep-projects)
            KEEP_PROJECTS=true
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo "  --template=NAME     Test only the specified template"
            echo "  --skip-uninstall    Skip uninstall testing"
            echo "  --verbose           Show verbose output"
            echo "  --config=FILE       Use custom templates config file"
            echo "  --keep-projects     Don't delete generated test projects"
            exit 0
            ;;
        *)
            echo "Unknown option: $arg"
            exit 2
            ;;
    esac
done

# Create directories
mkdir -p "$RESULTS_DIR"
mkdir -p "$TEST_PROJECTS_DIR"

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

    export JDEPLOY_JAR
    log "jdeploy CLI ready: $JDEPLOY_JAR"
}

# Run jdeploy command
run_jdeploy() {
    java -jar "$JDEPLOY_JAR" "$@"
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."

    # Check for Java
    if ! command -v java &> /dev/null; then
        log "ERROR: Java is not installed"
        return 1
    fi

    # Check for Maven
    if ! command -v mvn &> /dev/null; then
        log "WARNING: Maven not installed, some templates may fail"
    fi

    log "Prerequisites check passed"
    return 0
}

# Generate a unique project name
generate_project_name() {
    local template="$1"
    echo "test-${template}-${TIMESTAMP}"
}

# Generate a new project from template
generate_project() {
    local template="$1"
    local project_name="$2"
    local project_log="${RESULTS_DIR}/${template}-generate.log"

    log "Generating project from template '${template}'..."

    log_verbose "Running: java -jar $JDEPLOY_JAR generate -t ${template} -d ${TEST_PROJECTS_DIR} -n ${project_name} --appTitle=\"Test App ${template}\" -g com.test.e2e -a ${project_name} --mainClassName=Main"

    java -jar "$JDEPLOY_JAR" generate \
        -t "${template}" \
        -d "${TEST_PROJECTS_DIR}" \
        -n "${project_name}" \
        --appTitle="Test App ${template}" \
        -g com.test.e2e \
        -a "${project_name}" \
        --mainClassName=Main 2>&1 | tee -a "$project_log"

    local exit_code=${PIPESTATUS[0]}

    if [ $exit_code -eq 0 ]; then
        log "Project generated successfully: ${project_name}"
        return 0
    else
        log "ERROR: Failed to generate project from template '${template}' (exit code: $exit_code)"
        return 1
    fi
}

# Build the project
build_project() {
    local project_dir="$1"
    local build_cmd="$2"
    local template="$3"
    local project_log="${RESULTS_DIR}/${template}-build.log"

    log "Building project in ${project_dir}..."
    log_verbose "Build command: $build_cmd"

    cd "$project_dir"

    # Make mvnw executable if it exists
    if [ -f "mvnw" ]; then
        chmod +x mvnw
        # Replace mvn with ./mvnw in build command
        build_cmd="${build_cmd//mvn/.\/mvnw}"
    fi

    if eval "$build_cmd" 2>&1 | tee -a "$project_log"; then
        log "Project built successfully"
        cd "$SCRIPT_DIR"
        return 0
    else
        log "ERROR: Build failed"
        cd "$SCRIPT_DIR"
        return 1
    fi
}

# Install the project locally using jdeploy install
install_project() {
    local project_dir="$1"
    local template="$2"
    local project_log="${RESULTS_DIR}/${template}-install.log"

    log "Installing project locally..."

    cd "$project_dir"

    if run_jdeploy install -y 2>&1 | tee -a "$project_log"; then
        log "Project installed successfully"
        cd "$SCRIPT_DIR"
        return 0
    else
        log "ERROR: Installation failed"
        cd "$SCRIPT_DIR"
        return 1
    fi
}

# Verify installation using jdeploy verify-installation command
verify_installation() {
    local project_dir="$1"
    local template="$2"
    local project_log="${RESULTS_DIR}/${template}-verify-install.log"

    log "Verifying installation..."

    cd "$project_dir"

    local cmd="run_jdeploy verify-installation --package-json=package.json --verbose"
    log_verbose "Running: $cmd"

    if eval "$cmd" 2>&1 | tee -a "$project_log"; then
        log "Verification PASSED"
        cd "$SCRIPT_DIR"
        return 0
    else
        log "Verification FAILED"
        cd "$SCRIPT_DIR"
        return 1
    fi
}

# Uninstall the project using jdeploy uninstall
uninstall_project() {
    local project_dir="$1"
    local template="$2"
    local project_log="${RESULTS_DIR}/${template}-uninstall.log"

    log "Uninstalling project..."

    cd "$project_dir"

    if run_jdeploy uninstall -y 2>&1 | tee -a "$project_log"; then
        log "Uninstall completed"
        cd "$SCRIPT_DIR"
        return 0
    else
        log "Uninstall failed"
        cd "$SCRIPT_DIR"
        return 1
    fi
}

# Verify uninstallation
verify_uninstallation() {
    local project_dir="$1"
    local template="$2"
    local project_log="${RESULTS_DIR}/${template}-verify-uninstall.log"

    log "Verifying uninstallation..."

    cd "$project_dir"

    local cmd="run_jdeploy verify-uninstallation --package-json=package.json --verbose"
    log_verbose "Running: $cmd"

    if eval "$cmd" 2>&1 | tee -a "$project_log"; then
        log "Uninstallation verification PASSED"
        cd "$SCRIPT_DIR"
        return 0
    else
        log "Uninstallation verification FAILED"
        cd "$SCRIPT_DIR"
        return 1
    fi
}

# Cleanup test project
cleanup_project() {
    local project_dir="$1"

    if [ "$KEEP_PROJECTS" = false ] && [ -d "$project_dir" ]; then
        log "Cleaning up project directory: ${project_dir}"
        rm -rf "$project_dir"
    fi
}

# Test a single template
test_template() {
    local template="$1"
    local build_cmd="$2"
    local description="$3"

    log "=========================================="
    log "Testing template: ${template}"
    log "Build command: ${build_cmd}"
    log "Description: ${description}"
    log "=========================================="

    local test_passed=true
    local result_file="${RESULTS_DIR}/${template}-result.txt"
    local project_name=$(generate_project_name "$template")
    local project_dir="${TEST_PROJECTS_DIR}/${project_name}"

    # Step 1: Generate project
    if ! generate_project "$template" "$project_name"; then
        echo "GENERATE_FAILED" > "$result_file"
        cleanup_project "$project_dir"
        return 1
    fi

    # Step 2: Build project
    if ! build_project "$project_dir" "$build_cmd" "$template"; then
        echo "BUILD_FAILED" > "$result_file"
        cleanup_project "$project_dir"
        return 1
    fi

    # Step 3: Install project
    if ! install_project "$project_dir" "$template"; then
        echo "INSTALL_FAILED" > "$result_file"
        cleanup_project "$project_dir"
        return 1
    fi

    # Step 4: Verify installation
    if ! verify_installation "$project_dir" "$template"; then
        echo "VERIFY_INSTALL_FAILED" > "$result_file"
        test_passed=false
    fi

    # Step 5: Uninstall (if not skipped)
    if [ "$SKIP_UNINSTALL" = false ]; then
        if ! uninstall_project "$project_dir" "$template"; then
            echo "UNINSTALL_FAILED" > "$result_file"
            test_passed=false
        fi

        # Step 6: Verify uninstallation
        if ! verify_uninstallation "$project_dir" "$template"; then
            echo "VERIFY_UNINSTALL_FAILED" > "$result_file"
            test_passed=false
        fi
    fi

    # Cleanup
    cleanup_project "$project_dir"

    if [ "$test_passed" = true ]; then
        echo "PASSED" > "$result_file"
        log "Test PASSED for template: ${template}"
        return 0
    else
        log "Test FAILED for template: ${template}"
        return 1
    fi
}

# Main execution
main() {
    log "=========================================="
    log "jDeploy Local Project E2E Tests"
    log "=========================================="
    log "Platform: $PLATFORM"
    log "Timestamp: $TIMESTAMP"
    log "Config: $CONFIG_FILE"
    log "Skip Uninstall: $SKIP_UNINSTALL"
    log "Keep Projects: $KEEP_PROJECTS"
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

    # Read templates from config
    local total_templates=0
    local passed_templates=0
    local failed_templates=0
    local failed_list=""

    while IFS='|' read -r template build_cmd description || [ -n "$template" ]; do
        # Skip empty lines and comments
        [[ -z "$template" || "$template" =~ ^[[:space:]]*# ]] && continue

        # Trim whitespace
        template=$(echo "$template" | xargs)
        build_cmd=$(echo "$build_cmd" | xargs)
        description=$(echo "$description" | xargs)

        # Filter by single template if specified
        if [ -n "$SINGLE_TEMPLATE" ] && [ "$template" != "$SINGLE_TEMPLATE" ]; then
            continue
        fi

        total_templates=$((total_templates + 1))

        if test_template "$template" "$build_cmd" "$description"; then
            passed_templates=$((passed_templates + 1))
        else
            failed_templates=$((failed_templates + 1))
            failed_list="${failed_list}  - ${template}\n"
        fi

        log ""
    done < "$CONFIG_FILE"

    # Print summary
    log "=========================================="
    log "E2E Local Project Test Summary"
    log "=========================================="
    log "Total templates tested: $total_templates"
    log "Passed: $passed_templates"
    log "Failed: $failed_templates"

    if [ -n "$failed_list" ]; then
        log ""
        log "Failed templates:"
        echo -e "$failed_list" | tee -a "$LOG_FILE"
    fi

    # Write summary JSON
    cat > "${RESULTS_DIR}/summary.json" << EOF
{
    "timestamp": "$TIMESTAMP",
    "platform": "$PLATFORM",
    "totalTemplates": $total_templates,
    "passed": $passed_templates,
    "failed": $failed_templates,
    "success": $([ $failed_templates -eq 0 ] && echo "true" || echo "false")
}
EOF

    log ""
    log "Results saved to: $RESULTS_DIR"
    log "Log file: $LOG_FILE"

    # Cleanup test projects directory if empty
    if [ "$KEEP_PROJECTS" = false ]; then
        rmdir "$TEST_PROJECTS_DIR" 2>/dev/null || true
    fi

    # Exit with appropriate code
    if [ $failed_templates -gt 0 ]; then
        exit 1
    fi
    exit 0
}

# Run main
main
