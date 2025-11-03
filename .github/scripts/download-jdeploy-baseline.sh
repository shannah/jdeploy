#!/bin/bash
# .github/scripts/download-jdeploy-baseline.sh
# Downloads package-info.json from jdeploy tag and captures ETag for optimistic locking

set -e

REPOSITORY="$1"
GITHUB_TOKEN="$2"

if [ -z "$REPOSITORY" ] || [ -z "$GITHUB_TOKEN" ]; then
  echo "Usage: $0 <repository> <github_token>"
  exit 1
fi

REPO_URL="https://api.github.com/repos/${REPOSITORY}"

# Step 1: Check if jdeploy release exists
echo "Checking if jdeploy release exists..."
RELEASE_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "${REPO_URL}/releases/tags/jdeploy")

HTTP_CODE=$(echo "$RELEASE_RESPONSE" | tail -n1)
RELEASE_BODY=$(echo "$RELEASE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "404" ]; then
  # Case A: jdeploy tag doesn't exist - first publish
  echo "JDEPLOY_TAG_EXISTS=false" >> $GITHUB_ENV
  echo "JDEPLOY_FIRST_PUBLISH=true" >> $GITHUB_ENV
  echo "jdeploy tag not found - this is the first publish"
  exit 0
elif [ "$HTTP_CODE" != "200" ]; then
  # Case D: Unexpected error
  echo "ERROR: Failed to check jdeploy release (HTTP $HTTP_CODE)"
  echo "$RELEASE_BODY"
  exit 1
fi

echo "JDEPLOY_TAG_EXISTS=true" >> $GITHUB_ENV
echo "JDEPLOY_FIRST_PUBLISH=false" >> $GITHUB_ENV
RELEASE_ID=$(echo "$RELEASE_BODY" | jq -r '.id')
echo "JDEPLOY_RELEASE_ID=$RELEASE_ID" >> $GITHUB_ENV

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

# Download asset and capture ETag
ASSET_URL="${REPO_URL}/releases/assets/${ASSET_ID}"
DOWNLOAD_RESPONSE=$(curl -sS -D - \
  -H "Accept: application/octet-stream" \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "${ASSET_URL}")

# Extract ETag from headers
ETAG=$(echo "$DOWNLOAD_RESPONSE" | grep -i '^etag:' | sed 's/etag: *//i' | tr -d '\r\n' | tr -d '"')

if [ -z "$ETAG" ]; then
  echo "ERROR: Failed to capture ETag from package-info.json download"
  exit 1
fi

echo "PACKAGE_INFO_ETAG=$ETAG" >> $GITHUB_ENV
echo "Successfully downloaded baseline package-info.json (ETag: $ETAG)"
