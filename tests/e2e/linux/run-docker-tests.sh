#!/bin/bash
# run-docker-tests.sh
# Runs E2E tests inside a clean Linux Docker container.
# Tests jdeploy from source by building it inside the container.
#
# Usage: ./run-docker-tests.sh [OPTIONS]
#   --app=NAME          Test only the specified app
#   --skip-uninstall    Skip uninstall testing
#   --verbose           Show verbose output
#   --jdeploy-url=URL   Use custom jdeploy.com URL (default: www.jdeploy.com)
#   --keep-container    Don't remove container after tests
#   --image=IMAGE       Use custom Docker image (default: ubuntu:22.04)
#
# Example:
#   ./run-docker-tests.sh --app=jdeploy-demo-swingset2 --verbose
#   ./run-docker-tests.sh --jdeploy-url=dev.jdeploy.com

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JDEPLOY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
CONTAINER_NAME="jdeploy-e2e-linux-test"
DOCKER_IMAGE="ubuntu:22.04"
KEEP_CONTAINER=false
TEST_ARGS=""

# Parse arguments
for arg in "$@"; do
    case $arg in
        --keep-container)
            KEEP_CONTAINER=true
            ;;
        --image=*)
            DOCKER_IMAGE="${arg#*=}"
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo "  --app=NAME          Test only the specified app"
            echo "  --skip-uninstall    Skip uninstall testing"
            echo "  --verbose           Show verbose output"
            echo "  --jdeploy-url=URL   Use custom jdeploy URL"
            echo "  --keep-container    Don't remove container after tests"
            echo "  --image=IMAGE       Use custom Docker image"
            exit 0
            ;;
        *)
            # Pass through to test script
            TEST_ARGS="$TEST_ARGS $arg"
            ;;
    esac
done

echo "=========================================="
echo "jDeploy E2E Linux Docker Tests"
echo "=========================================="
echo "Docker image: $DOCKER_IMAGE"
echo "Container: $CONTAINER_NAME"
echo "jDeploy source: $JDEPLOY_ROOT"
echo "Test args: $TEST_ARGS"
echo ""

# Check Docker availability
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH"
    exit 2
fi

if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running"
    exit 2
fi

# Stop and remove existing container if any
echo "Cleaning up any existing container..."
docker stop "$CONTAINER_NAME" 2>/dev/null || true
docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
sleep 1

# Create results directory on host
RESULTS_DIR="${SCRIPT_DIR}/results"
mkdir -p "$RESULTS_DIR"

# Run container with tests
echo "Starting Docker container..."
echo ""

docker run \
    --name "$CONTAINER_NAME" \
    --rm=$( [ "$KEEP_CONTAINER" = false ] && echo "true" || echo "false" ) \
    -v "${SCRIPT_DIR}:/tests:ro" \
    -v "${JDEPLOY_ROOT}:/jdeploy-src:ro" \
    -v "${RESULTS_DIR}:/results" \
    -e DISPLAY=:99 \
    "$DOCKER_IMAGE" \
    /bin/bash -c "
        set -e
        echo 'Setting up test environment...'

        # Update package lists
        apt-get update -qq

        # Install required packages
        apt-get install -y -qq \\
            curl \\
            wget \\
            jq \\
            ca-certificates \\
            xvfb \\
            libgtk-3-0 \\
            libx11-6 \\
            libxext6 \\
            libxrender1 \\
            libxtst6 \\
            libxi6 \\
            default-jdk \\
            maven \\
            2>/dev/null

        # Start Xvfb for headless display
        echo 'Starting virtual display...'
        Xvfb :99 -screen 0 1024x768x24 &
        sleep 2

        # Copy jdeploy source to writable location and build
        echo 'Building jdeploy from source...'
        cp -r /jdeploy-src /tmp/jdeploy
        cd /tmp/jdeploy
        mvn clean install -DskipTests -q

        # Verify build succeeded
        if [ ! -f /tmp/jdeploy/cli/target/jdeploy-cli-1.0-SNAPSHOT.jar ]; then
            echo 'ERROR: jdeploy build failed'
            exit 2
        fi
        echo 'jdeploy built successfully'

        # Add jdeploy to PATH
        export PATH=\"/tmp/jdeploy/bin:\$PATH\"
        chmod +x /tmp/jdeploy/bin/jdeploy

        # Verify jdeploy works
        echo 'jdeploy version:'
        jdeploy --version || echo '(version check failed)'

        # Copy test files to writable location
        cp -r /tests /tmp/e2e-tests
        chmod +x /tmp/e2e-tests/*.sh

        # Update results directory in script
        mkdir -p /tmp/e2e-tests/results

        # Run tests
        echo ''
        echo 'Running E2E tests...'
        echo ''

        cd /tmp/e2e-tests
        ./e2e-test.sh --config=/tests/apps.conf $TEST_ARGS || TEST_EXIT=\$?

        # Copy results back to mounted volume
        cp -r /tmp/e2e-tests/results/* /results/ 2>/dev/null || true

        exit \${TEST_EXIT:-0}
    "

EXIT_CODE=$?

echo ""
echo "=========================================="
echo "Test Results"
echo "=========================================="

# Display summary if available
if [ -f "${RESULTS_DIR}/summary.json" ]; then
    cat "${RESULTS_DIR}/summary.json" | jq . 2>/dev/null || cat "${RESULTS_DIR}/summary.json"
fi

echo ""
echo "Results saved to: ${RESULTS_DIR}"

# List log files
echo ""
echo "Log files:"
ls -la "${RESULTS_DIR}"/*.log 2>/dev/null || echo "  (no log files found)"

exit $EXIT_CODE
