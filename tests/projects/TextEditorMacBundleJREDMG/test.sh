#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

echo "Running test with JDeploy IT Test project in $(pwd)"

if [ "$(uname)" != "Darwin" ]; then
  echo "Skipping test because it is not being run on a mac"
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

if [ ! -d "$EXPECTED_JRE_PATH" ]; then
  echo "Expected JRE path $EXPECTED_JRE_PATH does not exist"
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

# Ensure that no directories starting with "jre" are in the jdeploy home directory
if [ -d "$SCRIPTPATH/jdeploy/home" ]; then
  JRE_DIRS=$(find "$SCRIPTPATH/jdeploy/home" -type d -name "jre*")
  if [ -n "$JRE_DIRS" ]; then
    echo "Found unexpected directories starting with 'jre' in the jdeploy home directory:"
    echo "$JRE_DIRS"
    exit 1
  fi
fi

