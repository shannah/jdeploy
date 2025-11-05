#!/bin/bash
# .github/scripts/download-jdeploy-baseline.sh
# Downloads package-info.json from jdeploy tag and captures ETag for optimistic locking

set -e

# Mockable functions for testing
github_api_get() {
    local url="$1"
    shift
    curl -s -w "\n%{http_code}" "$@" "$url"
}

github_api_download() {
    local url="$1"
    shift
    curl -sS -D - "$@" "$url"
}

write_to_github_env() {
    local var_name="$1"
    local var_value="$2"
    echo "${var_name}=${var_value}" >> ${GITHUB_ENV:-/dev/null}
}

# Main script logic
main() {
    REPOSITORY="$1"
    GITHUB_TOKEN="$2"

    if [ -z "$REPOSITORY" ] || [ -z "$GITHUB_TOKEN" ]; then
      echo "Usage: $0 <repository> <github_token>"
      exit 1
    fi

    REPO_URL="https://api.github.com/repos/${REPOSITORY}"

    # Step 1: Check if jdeploy release exists
    echo "Checking if jdeploy release exists..."
    RELEASE_RESPONSE=$(github_api_get \
      "${REPO_URL}/releases/tags/jdeploy" \
      -H "Accept: application/vnd.github+json" \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "X-GitHub-Api-Version: 2022-11-28")

    HTTP_CODE=$(echo "$RELEASE_RESPONSE" | tail -n1)
    RELEASE_BODY=$(echo "$RELEASE_RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" = "404" ]; then
      # Case A: jdeploy tag doesn't exist - first publish
      write_to_github_env "JDEPLOY_TAG_EXISTS" "false"
      write_to_github_env "JDEPLOY_FIRST_PUBLISH" "true"
      echo "jdeploy tag not found - this is the first publish"
      exit 0
    elif [ "$HTTP_CODE" != "200" ]; then
      # Case D: Unexpected error
      echo "ERROR: Failed to check jdeploy release (HTTP $HTTP_CODE)"
      echo "$RELEASE_BODY"
      exit 1
    fi

    write_to_github_env "JDEPLOY_TAG_EXISTS" "true"
    write_to_github_env "JDEPLOY_FIRST_PUBLISH" "false"
    RELEASE_ID=$(echo "$RELEASE_BODY" | jq -r '.id')
    write_to_github_env "JDEPLOY_RELEASE_ID" "$RELEASE_ID"

    # Step 2: Download package-info.json asset with ETag
    echo "Downloading package-info.json from jdeploy release..."
    ASSETS=$(echo "$RELEASE_BODY" | jq -r '.assets')
    ASSET_ID=$(echo "$ASSETS" | jq -r '.[] | select(.name == "package-info.json") | .id')

    if [ -z "$ASSET_ID" ] || [ "$ASSET_ID" = "null" ]; then
      # Case B: jdeploy release exists but package-info.json is missing
      echo "ERROR: jdeploy release exists but package-info.json asset is missing"
      echo "This indicates a corrupted or incomplete publish state."
      echo ""
      echo "To fix this:"
      echo "  1. Go to: https://github.com/${REPOSITORY}/releases"
      echo "  2. Delete the 'jdeploy' release (NOT the tag)"
      echo "  3. Re-run this workflow"
      exit 1
    fi

    # Get asset ETag using HEAD request (faster and more reliable)
    ASSET_URL="${REPO_URL}/releases/assets/${ASSET_ID}"
    echo "Fetching ETag for package-info.json asset..."

    # Use HEAD request with redirect following to get headers from final location
    # GitHub returns a 302 redirect to the actual asset location
    HEADERS_RESPONSE=$(curl -sS -L -I \
      -H "Accept: application/octet-stream" \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "${ASSET_URL}")

    # Extract ETag from headers - get the last occurrence (after redirects)
    ETAG=$(echo "$HEADERS_RESPONSE" | grep -i '^etag:' | tail -1 | sed 's/[Ee][Tt]ag: *//i' | tr -d '\r\n' | tr -d '"' | tr -d ' ')

    if [ -z "$ETAG" ]; then
      echo "WARNING: Could not capture ETag from package-info.json"
      echo "Optimistic locking will not be available for this publish."
      echo "This means concurrent publishes may overwrite each other."
      # Set a placeholder to allow continuation but signal no locking
      ETAG=""
    else
      echo "Captured ETag: $ETAG"
    fi

    write_to_github_env "PACKAGE_INFO_ETAG" "$ETAG"
    echo "Successfully downloaded baseline package-info.json (ETag: $ETAG)"
}

# Run main if not being sourced (for testing)
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
