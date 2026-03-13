#!/bin/bash
#
# Run mock-network publishing integration tests.
#
# Usage:
#   ./test-infra/run-mock-network-tests.sh          # Full Docker run (build + test)
#   ./test-infra/run-mock-network-tests.sh --up      # Start services only (for local dev)
#   ./test-infra/run-mock-network-tests.sh --down     # Stop services
#   ./test-infra/run-mock-network-tests.sh --local    # Run tests locally against running services
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

case "${1:-}" in
    --up)
        echo "Starting mock services..."
        cd "$SCRIPT_DIR"
        docker-compose up -d verdaccio wiremock-github wiremock-jdeploy
        echo ""
        echo "Services running:"
        echo "  Verdaccio (npm):     http://localhost:4873"
        echo "  WireMock (GitHub):   http://localhost:8080"
        echo "  WireMock (jDeploy):  http://localhost:8081"
        echo ""
        echo "To run tests locally:"
        echo "  export NPM_CONFIG_REGISTRY=http://localhost:4873"
        echo "  export GITHUB_API_BASE_URL=http://localhost:8080"
        echo "  export GITHUB_BASE_URL=http://localhost:8080"
        echo "  export JDEPLOY_REGISTRY_URL=http://localhost:8081/"
        echo "  export GITHUB_TOKEN=mock-github-token-for-testing"
        echo "  cd cli && mvn test -Dtest='ca.weblite.jdeploy.publishing.*MockNetwork*,ca.weblite.jdeploy.publishing.*MockPublish*'"
        echo "  cd installer && mvn test -Dtest='ca.weblite.jdeploy.installer.prebuilt.mocknetwork.PrebuiltBundleDownloadMockNetworkTest'"
        ;;

    --down)
        echo "Stopping mock services..."
        cd "$SCRIPT_DIR"
        docker-compose down -v
        echo "Services stopped."
        ;;

    --local)
        echo "Running tests locally against mock services..."
        echo "Make sure services are running (use --up first)"
        cd "$PROJECT_DIR"
        export NPM_CONFIG_REGISTRY="${NPM_CONFIG_REGISTRY:-http://localhost:4873}"
        export GITHUB_API_BASE_URL="${GITHUB_API_BASE_URL:-http://localhost:8080}"
        export GITHUB_BASE_URL="${GITHUB_BASE_URL:-http://localhost:8080}"
        export JDEPLOY_REGISTRY_URL="${JDEPLOY_REGISTRY_URL:-http://localhost:8081/}"
        export GITHUB_TOKEN="${GITHUB_TOKEN:-mock-github-token-for-testing}"
        echo "=== CLI publishing tests ==="
        cd "$PROJECT_DIR/cli" && mvn test -Dtest="ca.weblite.jdeploy.publishing.*MockNetwork*,ca.weblite.jdeploy.publishing.*MockPublish*"
        echo "=== Installer pre-built bundle download tests ==="
        cd "$PROJECT_DIR/installer" && mvn test -Dtest="ca.weblite.jdeploy.installer.prebuilt.mocknetwork.PrebuiltBundleDownloadMockNetworkTest" -Dsurefire.failIfNoSpecifiedTests=false
        ;;

    *)
        echo "=== Mock Network Publishing Tests ==="
        echo ""
        echo "Building and running tests in Docker..."
        cd "$SCRIPT_DIR"
        docker-compose up --build --abort-on-container-exit --exit-code-from test-runner
        echo ""
        echo "Cleaning up..."
        docker-compose down -v
        echo "Done."
        ;;
esac
