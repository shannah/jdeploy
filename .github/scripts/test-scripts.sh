#!/bin/bash
# Test suite for jdeploy GitHub Action scripts
# This tests the download and upload scripts with various scenarios

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Mock GitHub API server (would need a real test server or mocking framework)
# For now, we'll do basic validation tests

echo "========================================="
echo "jDeploy Action Scripts Test Suite"
echo "========================================="
echo ""

# Test 1: Script existence and permissions
test_script_files() {
    echo -n "Test 1: Scripts exist and are executable... "
    TESTS_RUN=$((TESTS_RUN + 1))

    if [ ! -f ".github/scripts/download-jdeploy-baseline.sh" ]; then
        echo -e "${RED}FAILED${NC}"
        echo "  download-jdeploy-baseline.sh not found"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if [ ! -x ".github/scripts/download-jdeploy-baseline.sh" ]; then
        echo -e "${RED}FAILED${NC}"
        echo "  download-jdeploy-baseline.sh is not executable"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if [ ! -f ".github/scripts/upload-jdeploy-package-info.sh" ]; then
        echo -e "${RED}FAILED${NC}"
        echo "  upload-jdeploy-package-info.sh not found"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if [ ! -x ".github/scripts/upload-jdeploy-package-info.sh" ]; then
        echo -e "${RED}FAILED${NC}"
        echo "  upload-jdeploy-package-info.sh is not executable"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 2: Script syntax validation
test_script_syntax() {
    echo -n "Test 2: Script syntax is valid... "
    TESTS_RUN=$((TESTS_RUN + 1))

    if ! bash -n .github/scripts/download-jdeploy-baseline.sh 2>/dev/null; then
        echo -e "${RED}FAILED${NC}"
        echo "  download-jdeploy-baseline.sh has syntax errors"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! bash -n .github/scripts/upload-jdeploy-package-info.sh 2>/dev/null; then
        echo -e "${RED}FAILED${NC}"
        echo "  upload-jdeploy-package-info.sh has syntax errors"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 3: Download script requires parameters
test_download_script_params() {
    echo -n "Test 3: Download script validates parameters... "
    TESTS_RUN=$((TESTS_RUN + 1))

    # Script should fail if called without parameters
    if .github/scripts/download-jdeploy-baseline.sh 2>/dev/null; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script should fail when called without parameters"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    # Script should fail if called with only one parameter
    if .github/scripts/download-jdeploy-baseline.sh "test-repo" 2>/dev/null; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script should fail when called with only one parameter"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 4: Upload script requires parameters
test_upload_script_params() {
    echo -n "Test 4: Upload script validates parameters... "
    TESTS_RUN=$((TESTS_RUN + 1))

    # Script should fail if called without parameters
    if .github/scripts/upload-jdeploy-package-info.sh 2>/dev/null; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script should fail when called without parameters"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    # Script should fail if called with only two parameters
    if .github/scripts/upload-jdeploy-package-info.sh "test-repo" "test-token" 2>/dev/null; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script should fail when called with only two parameters"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 5: Scripts have proper shebang
test_shebang() {
    echo -n "Test 5: Scripts have proper shebang... "
    TESTS_RUN=$((TESTS_RUN + 1))

    if ! head -n1 .github/scripts/download-jdeploy-baseline.sh | grep -q "^#!/bin/bash"; then
        echo -e "${RED}FAILED${NC}"
        echo "  download-jdeploy-baseline.sh missing proper shebang"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! head -n1 .github/scripts/upload-jdeploy-package-info.sh | grep -q "^#!/bin/bash"; then
        echo -e "${RED}FAILED${NC}"
        echo "  upload-jdeploy-package-info.sh missing proper shebang"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 6: Scripts use 'set -e'
test_error_handling() {
    echo -n "Test 6: Scripts use 'set -e' for error handling... "
    TESTS_RUN=$((TESTS_RUN + 1))

    if ! grep -q "^set -e" .github/scripts/download-jdeploy-baseline.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  download-jdeploy-baseline.sh missing 'set -e'"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! grep -q "^set -e" .github/scripts/upload-jdeploy-package-info.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  upload-jdeploy-package-info.sh missing 'set -e'"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 7: Download script handles environment variables
test_download_env_vars() {
    echo -n "Test 7: Download script sets environment variables... "
    TESTS_RUN=$((TESTS_RUN + 1))

    # Check that script writes to GITHUB_ENV
    if ! grep -q "GITHUB_ENV" .github/scripts/download-jdeploy-baseline.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script doesn't write to GITHUB_ENV"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    # Check for expected environment variables
    if ! grep -q "JDEPLOY_FIRST_PUBLISH" .github/scripts/download-jdeploy-baseline.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script doesn't set JDEPLOY_FIRST_PUBLISH"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! grep -q "PACKAGE_INFO_ETAG" .github/scripts/download-jdeploy-baseline.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script doesn't set PACKAGE_INFO_ETAG"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 8: Upload script creates backup file
test_backup_file_creation() {
    echo -n "Test 8: Upload script creates package-info-2.json... "
    TESTS_RUN=$((TESTS_RUN + 1))

    if ! grep -q "package-info-2.json" .github/scripts/upload-jdeploy-package-info.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script doesn't mention package-info-2.json"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! grep -q "cp.*package-info.json.*package-info-2.json" .github/scripts/upload-jdeploy-package-info.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script doesn't copy package-info.json to package-info-2.json"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 9: Upload script checks for concurrent publishes
test_concurrent_publish_detection() {
    echo -n "Test 9: Upload script detects concurrent publishes... "
    TESTS_RUN=$((TESTS_RUN + 1))

    if ! grep -q "Concurrent publish detected" .github/scripts/upload-jdeploy-package-info.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script doesn't have concurrent publish detection"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! grep -q "CURRENT_ETAG" .github/scripts/upload-jdeploy-package-info.sh; then
        echo -e "${RED}FAILED${NC}"
        echo "  Script doesn't check current ETag"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Test 10: action.yml references the scripts correctly
test_action_yml() {
    echo -n "Test 10: action.yml references scripts correctly... "
    TESTS_RUN=$((TESTS_RUN + 1))

    if [ ! -f "action.yml" ]; then
        echo -e "${RED}FAILED${NC}"
        echo "  action.yml not found"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! grep -q "download-jdeploy-baseline.sh" action.yml; then
        echo -e "${RED}FAILED${NC}"
        echo "  action.yml doesn't reference download-jdeploy-baseline.sh"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! grep -q "upload-jdeploy-package-info.sh" action.yml; then
        echo -e "${RED}FAILED${NC}"
        echo "  action.yml doesn't reference upload-jdeploy-package-info.sh"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    if ! grep -q "github.action_path" action.yml; then
        echo -e "${RED}FAILED${NC}"
        echo "  action.yml doesn't use github.action_path"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi

    echo -e "${GREEN}PASSED${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

# Run all tests
echo "Running tests..."
echo ""

test_script_files
test_script_syntax
test_download_script_params
test_upload_script_params
test_shebang
test_error_handling
test_download_env_vars
test_backup_file_creation
test_concurrent_publish_detection
test_action_yml

# Print summary
echo ""
echo "========================================="
echo "Test Summary"
echo "========================================="
echo -e "Total tests run: ${TESTS_RUN}"
echo -e "Tests passed:    ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Tests failed:    ${RED}${TESTS_FAILED}${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed.${NC}"
    exit 1
fi
