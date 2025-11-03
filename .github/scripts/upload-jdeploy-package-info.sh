#!/bin/bash
# .github/scripts/upload-jdeploy-package-info.sh
# Uploads package-info.json and package-info-2.json to jdeploy tag with concurrency control

set -e

REPOSITORY="$1"
GITHUB_TOKEN="$2"
RELEASE_FILES_DIR="$3"

if [ -z "$REPOSITORY" ] || [ -z "$GITHUB_TOKEN" ] || [ -z "$RELEASE_FILES_DIR" ]; then
  echo "Usage: $0 <repository> <github_token> <release_files_dir>"
  exit 1
fi

REPO_URL="https://api.github.com/repos/${REPOSITORY}"

# Create package-info-2.json as a copy of package-info.json
cp "${RELEASE_FILES_DIR}/package-info.json" "${RELEASE_FILES_DIR}/package-info-2.json"
echo "Created package-info-2.json backup file"

if [ "$JDEPLOY_FIRST_PUBLISH" = "true" ]; then
  echo "First publish - creating jdeploy release..."

  # Create release atomically
  CREATE_RESPONSE=$(curl -sS -w "\n%{http_code}" -X POST \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "${REPO_URL}/releases" \
    -d '{
      "tag_name": "jdeploy",
      "name": "jdeploy",
      "body": "Release metadata for jDeploy releases",
      "draft": false,
      "prerelease": true
    }')

  HTTP_CODE=$(echo "$CREATE_RESPONSE" | tail -n1)
  RESPONSE_BODY=$(echo "$CREATE_RESPONSE" | sed '$d')

  if [ "$HTTP_CODE" = "422" ]; then
    # Concurrent first publish - another workflow created the release
    echo "ERROR: Concurrent publish detected during first publish"
    echo "Another workflow created the jdeploy release while this workflow was running."
    echo "The version-specific release was created successfully."
    echo "Please re-run this workflow to complete the jdeploy tag update."
    exit 1
  elif [ "$HTTP_CODE" != "201" ]; then
    echo "ERROR: Failed to create jdeploy release (HTTP $HTTP_CODE)"
    echo "$RESPONSE_BODY"
    exit 1
  fi

  RELEASE_ID=$(echo "$RESPONSE_BODY" | jq -r '.id')
  UPLOAD_URL=$(echo "$RESPONSE_BODY" | jq -r '.upload_url' | sed 's/{?name,label}//')

else
  echo "Updating existing jdeploy release..."
  RELEASE_ID="$JDEPLOY_RELEASE_ID"

  # Fetch upload URL
  RELEASE_DETAILS=$(curl -sS \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "${REPO_URL}/releases/${RELEASE_ID}")

  UPLOAD_URL=$(echo "$RELEASE_DETAILS" | jq -r '.upload_url' | sed 's/{?name,label}//')

  # Delete existing package-info.json asset (conditional on ETag)
  ASSETS=$(echo "$RELEASE_DETAILS" | jq -r '.assets')
  OLD_ASSET_ID=$(echo "$ASSETS" | jq -r '.[] | select(.name == "package-info.json") | .id')

  if [ -n "$OLD_ASSET_ID" ] && [ "$OLD_ASSET_ID" != "null" ]; then
    # Verify ETag hasn't changed
    CURRENT_ETAG=$(curl -sS -I \
      -H "Accept: application/octet-stream" \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "${REPO_URL}/releases/assets/${OLD_ASSET_ID}" | \
      grep -i '^etag:' | sed 's/etag: *//i' | tr -d '\r\n' | tr -d '"')

    if [ "$CURRENT_ETAG" != "$PACKAGE_INFO_ETAG" ]; then
      echo "ERROR: Concurrent publish detected"
      echo "The jdeploy tag was modified during this workflow execution."
      echo "Expected ETag: $PACKAGE_INFO_ETAG"
      echo "Current ETag:  $CURRENT_ETAG"
      echo ""
      echo "The version-specific release was created successfully."
      echo "Please re-run this workflow to complete the jdeploy tag update."
      exit 1
    fi

    # Delete old asset
    curl -sS -X DELETE \
      -H "Accept: application/vnd.github+json" \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "${REPO_URL}/releases/assets/${OLD_ASSET_ID}"
  fi
fi

# Upload package-info.json (primary file)
echo "Uploading package-info.json..."
curl -sS -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  -H "Content-Type: application/json" \
  "${UPLOAD_URL}?name=package-info.json" \
  --data-binary "@${RELEASE_FILES_DIR}/package-info.json"

# Upload package-info-2.json (backup file)
echo "Uploading package-info-2.json (backup)..."

# Delete existing package-info-2.json if it exists
ASSETS=$(curl -sS \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "${REPO_URL}/releases/${RELEASE_ID}" | jq -r '.assets')

OLD_ASSET2_ID=$(echo "$ASSETS" | jq -r '.[] | select(.name == "package-info-2.json") | .id')
if [ -n "$OLD_ASSET2_ID" ] && [ "$OLD_ASSET2_ID" != "null" ]; then
  curl -sS -X DELETE \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "${REPO_URL}/releases/assets/${OLD_ASSET2_ID}"
fi

curl -sS -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  -H "Content-Type: application/json" \
  "${UPLOAD_URL}?name=package-info-2.json" \
  --data-binary "@${RELEASE_FILES_DIR}/package-info-2.json"

echo "✓ Successfully updated jdeploy tag with package-info.json and package-info-2.json"
