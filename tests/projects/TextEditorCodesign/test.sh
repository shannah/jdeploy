#!/bin/bash
set -e

if [ -f .env ]; then
  source .env
fi

if [ "$(uname)" != "Darwin" ]; then
  echo "Skipping test because it is not being run on a mac"
  exit 0
fi

if [ -z "$JDEPLOY_MAC_APP_BUNDLE_ID" ]; then
  echo "Skipping test because JDEPLOY_MAC_APP_BUNDLE_ID environment variable is not set"
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
java -jar "$JDEPLOY" clean package
if [ ! -d "jdeploy-bundle" ]; then
  echo "Missing jdeploy-bundle\n"
  exit 1
fi

if [ ! -f "jdeploy-bundle/TextEditor-1.0-SNAPSHOT.jar" ]; then
  echo "jdeploy package did not copy jar file correctly."
  exit 1
fi

if [ ! -f "jdeploy-bundle/icon.png" ]; then
  echo "jdeploy package did not copy icon file correctly."
  exit 1
fi

if [ ! -f "jdeploy-bundle/jdeploy.js" ]; then
  echo "jdeploy package did not copy jdeploy.js file correctly."
  exit 1
fi


echo "TextEditor project test passed"


