#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

if [ -f .env ]; then
  source .env
fi

echo "Running test with JDeploy IT Test project in $(pwd)"

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

if [ -z "$JDEPLOY" ]; then
  echo "JDEPLOY environment variable must be set"
  exit 1
fi

# if running on macOS on ARM, then add the mac-arm64 bundle to the bundles array
if [ "$(uname)" == "Darwin" ] && [ "$(uname -m)" == "arm64" ]; then
  BUNDLES='["mac-arm64"]'
  LAUNCHER_PATH=./jdeploy/bundles/mac-arm64/Jdeploy\ It\ Test\ Project.app/Contents/MacOS/Client4JLauncher
  EXPECTED_JRE_PATH=./jdeploy/bundles/mac-arm64/Jdeploy\ It\ Test\ Project.app/Contents/jre
fi

# if running on macOS on Intel, then add the mac-x64 bundle to the bundles array
if [ "$(uname)" == "Darwin" ] && [ "$(uname -m)" == "x86_64" ]; then
  BUNDLES='["mac-x64"]'
  LAUNCHER_PATH=./jdeploy/bundles/mac-x64/Jdeploy\ It\ Test\ Project.app/Contents/MacOS/Client4JLauncher
  EXPECTED_JRE_PATH=./jdeploy/bundles/mac-x64/Jdeploy\ It\ Test\ Project.app/Contents/jre
fi

# if bundles not set, then skip this test as we are obviously not running on a target platform
if [ -z "$BUNDLES" ]; then
  echo "Skipping test because we are not running on a target platform.  Found platform $(uname) $(uname -m)"
  exit 0
fi

if [ -f "$LAUNCHER_PATH" ]; then
  rm -f "$LAUNCHER_PATH"
fi

JDEPLOY_CONFIG="$(printf  '{"jdeployHome": "%s", "bundles": %s}' "$SCRIPTPATH/jdeploy/home" "$BUNDLES" )" java -jar "$JDEPLOY" clean package
if [ ! -d "jdeploy-bundle" ]; then
  echo "Missing jdeploy-bundle\n"
  exit 1
fi

if [ ! -f "jdeploy-bundle/jdeploy-it-test-project-1.0-SNAPSHOT.jar" ]; then
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

if [ -d "$EXPECTED_JRE_PATH" ]; then
  echo "Unxpected JRE path $EXPECTED_JRE_PATH found.  Since bundleJRE is false, we should not have a JRE in the bundle"
  exit 1
fi

echo "JDeploy IT Test project bundle test passed"

if [ -f "$HOME/jdeploy-it-test-project.json" ]; then
  rm "$HOME/jdeploy-it-test-project.json"
fi


# Check that launcher was created
if [ ! -f "$LAUNCHER_PATH" ]; then
  echo "Expected launcher to be created at $LAUNCHER_PATH, but it was not found"
  echo "Contents of $bundles directory:"
  find ./jdeploy/bundles
  exit 1
else
  echo "Launcher was created successfully at $LAUNCHER_PATH"
fi

echo "Testing launcher"
"$LAUNCHER_PATH"
echo "Launcher appeared to launch successfully.  Waiting 5 seconds before checking for jdeploy-it-test-project.json"

sleep 5

# Check if the jdeploy-it-test-project.json file was created
if [ ! -f "$HOME/jdeploy-it-test-project.json" ]; then
  echo "jdeploy-it-test-project.json file was not created"
  exit 1
else
  echo "jdeploy-it-test-project.json file was created"
fi

# Ensure that at least one directory starting with "jre" is in the home directory
if [ ! -d "$SCRIPTPATH/jdeploy/home/jre"* ]; then
  echo "Expected at least one directory starting with 'jre' in the jdeploy home directory, but none were found"
  exit 1
fi


