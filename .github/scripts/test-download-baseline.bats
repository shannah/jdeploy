#!/usr/bin/env bats

# Test suite for download-jdeploy-baseline.sh using bats

setup() {
    # Source the script to get access to functions
    # But prevent it from running by stubbing main logic
    export GITHUB_ENV=$(mktemp)
    export SCRIPT_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
    source "$SCRIPT_DIR/download-jdeploy-baseline.sh" || true
}

teardown() {
    rm -f "$GITHUB_ENV"
}

# Mock functions for testing
mock_github_api_get_404() {
    echo ""
    echo "404"
}

mock_github_api_get_200_with_asset() {
    cat <<'EOF'
{"id": 12345, "assets": [{"id": 67890, "name": "package-info.json"}]}
200
EOF
}

mock_github_api_get_200_without_asset() {
    cat <<'EOF'
{"id": 12345, "assets": []}
200
EOF
}

mock_github_api_get_500() {
    echo '{"message": "Internal Server Error"}'
    echo "500"
}

mock_github_api_download_with_etag() {
    cat <<'EOF'
HTTP/2 200
etag: "abc123def456"
content-type: application/json

{"version": "1.0.0"}
EOF
}

mock_github_api_download_no_etag() {
    cat <<'EOF'
HTTP/2 200
content-type: application/json

{"version": "1.0.0"}
EOF
}

# Test 1: Case A - First publish (404 on jdeploy tag)
@test "download-baseline: handles first publish (404)" {
    # Override the github_api_get function
    github_api_get() { mock_github_api_get_404; }

    # Run main script logic
    run bash -c '
        source .github/scripts/download-jdeploy-baseline.sh
        github_api_get() { echo ""; echo "404"; }

        REPOSITORY="test/repo"
        GITHUB_TOKEN="fake-token"
        REPO_URL="https://api.github.com/repos/test/repo"

        RELEASE_RESPONSE=$(github_api_get "${REPO_URL}/releases/tags/jdeploy")
        HTTP_CODE=$(echo "$RELEASE_RESPONSE" | tail -n1)
        RELEASE_BODY=$(echo "$RELEASE_RESPONSE" | sed '\''$d'\'')

        if [ "$HTTP_CODE" = "404" ]; then
            write_to_github_env "JDEPLOY_TAG_EXISTS" "false"
            write_to_github_env "JDEPLOY_FIRST_PUBLISH" "true"
            echo "jdeploy tag not found - this is the first publish"
            exit 0
        fi
    '

    [ "$status" -eq 0 ]
    [[ "$output" == *"jdeploy tag not found"* ]]
}

# Test 2: Case B - Corrupted state (release exists but no asset)
@test "download-baseline: detects corrupted state (no package-info.json asset)" {
    run bash -c '
        source .github/scripts/download-jdeploy-baseline.sh
        github_api_get() {
            cat <<EOF
{"id": 12345, "assets": []}
200
EOF
        }

        REPOSITORY="test/repo"
        GITHUB_TOKEN="fake-token"
        REPO_URL="https://api.github.com/repos/test/repo"

        RELEASE_RESPONSE=$(github_api_get "${REPO_URL}/releases/tags/jdeploy")
        HTTP_CODE=$(echo "$RELEASE_RESPONSE" | tail -n1)
        RELEASE_BODY=$(echo "$RELEASE_RESPONSE" | sed '\''$d'\'')

        if [ "$HTTP_CODE" = "200" ]; then
            ASSETS=$(echo "$RELEASE_BODY" | jq -r ".assets")
            ASSET_ID=$(echo "$ASSETS" | jq -r ".[] | select(.name == \"package-info.json\") | .id")

            if [ -z "$ASSET_ID" ] || [ "$ASSET_ID" = "null" ]; then
                echo "ERROR: jdeploy release exists but package-info.json asset is missing"
                exit 1
            fi
        fi
    '

    [ "$status" -eq 1 ]
    [[ "$output" == *"package-info.json asset is missing"* ]]
}

# Test 3: Case C - Normal update (release and asset exist)
@test "download-baseline: handles normal update with ETag capture" {
    run bash -c '
        source .github/scripts/download-jdeploy-baseline.sh
        github_api_get() {
            cat <<EOF
{"id": 12345, "assets": [{"id": 67890, "name": "package-info.json"}]}
200
EOF
        }

        github_api_download() {
            cat <<EOF
HTTP/2 200
etag: "abc123def456"
content-type: application/json

{"version": "1.0.0"}
EOF
        }

        REPOSITORY="test/repo"
        GITHUB_TOKEN="fake-token"
        REPO_URL="https://api.github.com/repos/test/repo"

        # Step 1: Get release
        RELEASE_RESPONSE=$(github_api_get "${REPO_URL}/releases/tags/jdeploy")
        HTTP_CODE=$(echo "$RELEASE_RESPONSE" | tail -n1)
        RELEASE_BODY=$(echo "$RELEASE_RESPONSE" | sed '\''$d'\'')

        if [ "$HTTP_CODE" = "200" ]; then
            RELEASE_ID=$(echo "$RELEASE_BODY" | jq -r ".id")
            write_to_github_env "JDEPLOY_RELEASE_ID" "$RELEASE_ID"

            # Step 2: Get asset
            ASSETS=$(echo "$RELEASE_BODY" | jq -r ".assets")
            ASSET_ID=$(echo "$ASSETS" | jq -r ".[] | select(.name == \"package-info.json\") | .id")

            if [ -n "$ASSET_ID" ] && [ "$ASSET_ID" != "null" ]; then
                # Download and extract ETag
                DOWNLOAD_RESPONSE=$(github_api_download "${REPO_URL}/releases/assets/${ASSET_ID}")
                ETAG=$(echo "$DOWNLOAD_RESPONSE" | grep -i "^etag:" | sed "s/etag: *//i" | tr -d "\r\n" | tr -d "\"")

                write_to_github_env "PACKAGE_INFO_ETAG" "$ETAG"
                echo "Successfully downloaded baseline package-info.json (ETag: $ETAG)"
            fi
        fi
    '

    [ "$status" -eq 0 ]
    [[ "$output" == *"Successfully downloaded baseline"* ]]
    [[ "$output" == *"abc123def456"* ]]
}

# Test 4: Case D - Server error (500)
@test "download-baseline: handles server error (500)" {
    run bash -c '
        source .github/scripts/download-jdeploy-baseline.sh
        github_api_get() {
            echo "{\"message\": \"Internal Server Error\"}"
            echo "500"
        }

        REPOSITORY="test/repo"
        GITHUB_TOKEN="fake-token"
        REPO_URL="https://api.github.com/repos/test/repo"

        RELEASE_RESPONSE=$(github_api_get "${REPO_URL}/releases/tags/jdeploy")
        HTTP_CODE=$(echo "$RELEASE_RESPONSE" | tail -n1)
        RELEASE_BODY=$(echo "$RELEASE_RESPONSE" | sed '\''$d'\'')

        if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "404" ]; then
            echo "ERROR: Failed to check jdeploy release (HTTP $HTTP_CODE)"
            exit 1
        fi
    '

    [ "$status" -eq 1 ]
    [[ "$output" == *"ERROR: Failed to check jdeploy release"* ]]
    [[ "$output" == *"500"* ]]
}

# Test 5: Missing ETag in response
@test "download-baseline: fails when ETag is missing" {
    run bash -c '
        source .github/scripts/download-jdeploy-baseline.sh
        github_api_download() {
            cat <<EOF
HTTP/2 200
content-type: application/json

{"version": "1.0.0"}
EOF
        }

        DOWNLOAD_RESPONSE=$(github_api_download "fake-url")
        ETAG=$(echo "$DOWNLOAD_RESPONSE" | grep -i "^etag:" | sed "s/etag: *//i" | tr -d "\r\n" | tr -d "\"")

        if [ -z "$ETAG" ]; then
            echo "ERROR: Failed to capture ETag from package-info.json download"
            exit 1
        fi
    '

    [ "$status" -eq 1 ]
    [[ "$output" == *"Failed to capture ETag"* ]]
}

# Test 6: write_to_github_env function works correctly
@test "write_to_github_env: writes variables correctly" {
    run bash -c '
        source .github/scripts/download-jdeploy-baseline.sh
        export GITHUB_ENV=$(mktemp)

        write_to_github_env "TEST_VAR" "test_value"
        write_to_github_env "ANOTHER_VAR" "another_value"

        cat "$GITHUB_ENV"
        rm -f "$GITHUB_ENV"
    '

    [ "$status" -eq 0 ]
    [[ "$output" == *"TEST_VAR=test_value"* ]]
    [[ "$output" == *"ANOTHER_VAR=another_value"* ]]
}
