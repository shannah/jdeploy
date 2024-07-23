#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

echo "Running test with JDeploy IT Test project in $(pwd)"
if [ -z "$JDEPLOY" ]; then
  echo "JDEPLOY environment variable must be set"
  exit 1
fi

# if running on macOS on ARM, then add the mac-arm64 bundle to the bundles array
if [ "$(uname)" == "Darwin" ] && [ "$(uname -m)" == "arm64" ]; then
  BUNDLES='["mac-arm64"]'
  LAUNCHER_PATH=./jdeploy/bundles/mac-arm64/Jdeploy\ It\ Test\ Project.app/Contents/MacOS/Client4JLauncher
fi

# if running on macOS on Intel, then add the mac-x64 bundle to the bundles array
if [ "$(uname)" == "Darwin" ] && [ "$(uname -m)" == "x86_64" ]; then
  BUNDLES='["mac-x64"]'
  LAUNCHER_PATH=./jdeploy/bundles/mac-x64/Jdeploy\ It\ Test\ Project.app/Contents/MacOS/Client4JLauncher
fi

# if running on linux, then add the linux-x64 bundle to the bundles array
if [ "$(uname)" == "Linux" ]; then
  BUNDLES='["linux"]'
  LAUNCHER_PATH=./jdeploy/bundles/linux/Jdeploy\ It\ Test\ Project
fi

# if running on windows, then add the windows-x64 bundle to the bundles array
if [ "$(expr substr $(uname -s) 1 10)" == "MINGW64_NT" ]; then
  BUNDLES='["win"]'
  LAUNCHER_PATH=./jdeploy/bundles/win/Jdeploy\ It\ Test\ Project.exe
fi

# if bundles not set, then skip this test as we are obviously not running on a target platform
if [ -z "$BUNDLES" ]; then
  echo "Skipping test because we are not running on a target platform.  Found platform $(uname) $(uname -m)"
  exit 0
fi

if [ -f "$LAUNCHER_PATH" ]; then
  rm -f "$LAUNCHER_PATH"
fi

JDEPLOY_CONFIG="$(printf  '{"jdeployHome": "%s"}', "$SCRIPTPATH/jdeploy/home")" java -jar "$JDEPLOY" clean package
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
echo "JDeploy IT Test project bundle test passed"

if [ -f "$HOME/jdeploy-it-test-project.json" ]; then
  rm "$HOME/jdeploy-it-test-project.json"
fi


# Check that launcher was created
if [ ! -f "$LAUNCHER_PATH" ]; then
  echo "Expected launcher to be created at $LAUNCHER_PATH, but it was not found"
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
