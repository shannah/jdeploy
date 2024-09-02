#!/bin/bash
set -e

if [ -f .env ]; then
  source .env
fi

if [ "$(uname)" != "Darwin" ]; then
  echo "Skipping test because it is not being run on a mac"
  exit 0
fi

if [ -z "$JDEPLOY_MAC_NOTARIZATION_PASSWORD" ]; then
  echo "Skipping test because JDEPLOY_MAC_NOTARIZATION_PASSWORD environment variable is not set"
  exit 0
fi

if [ -z "$JDEPLOY_MAC_DEVELOPER_ID" ]; then
  echo "Skipping test because JDEPLOY_MAC_DEVELOPER_ID environment variable is not set"
  exit 0
fi

if [ -z "$JDEPLOY_MAC_DEVELOPER_TEAM_ID" ]; then
  echo "Skipping test because JDEPLOY_MAC_DEVELOPER_TEAM_ID environment variable is not set"
  exit 0
fi

if [ -z "$JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME" ]; then
  echo "Skipping test because JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME environment variable is not set"
  exit 0
fi

echo "Running test with TextEditor project in $(pwd)"
if [ -z "$JDEPLOY" ]; then
  echo "JDEPLOY environment variable must be set"
  exit 1
fi
java -jar "$JDEPLOY" clean
java -jar "$JDEPLOY" dmg