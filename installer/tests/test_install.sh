#!/bin/bash
# Tests installing a jDeploy application.
# This script should be run from inside a specific project's test directory.  E.g.
# cd ~/jdeploy/installer/tests/snapcodejava && test_install.sh
# For more information, see the README.md file.

set -e
# get path to current script
SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"

# get path to the current working directory
CWD="$(pwd)"

# Check if the ~/.jdeploy/.env.dev file exists
if ! [ -f ~/.jdeploy/.env.dev ] && [ -z "$JDEPLOY_IS_PIPELINE" ]; then
  echo "Error: The ~/.jdeploy/.env.dev file does not exist."
  echo "Please create this file and add the JAVA_HOME environment variable."
  echo "This is used to specify the absolute path to the Java installation that will be used by the uninstaller"
  echo "For more information, see the README.md file."
  exit 1
fi

if ! [ -z "$JDEPLOY_IS_PIPELINE" ]; then
  echo "JDEPLOY_IS_PIPELINE is set, skipping loading of ~/.jdeploy/.env.dev"
else
  # load the ~/.jdeploy/.env.dev file
  source ~/.jdeploy/.env.dev
fi

# verify that JAVA_HOME exists
if [ -z "$JAVA_HOME" ]; then
  echo "Error: The JAVA_HOME environment variable is not set."
  echo "Please set this variable in the ~/.jdeploy/.env.dev file."
  echo "This is used to specify the absolute path to the Java installation that will be used by the uninstaller"
  echo "For more information, see the README.md file."
  exit 1
fi

# verify that there is a .jdeploy-files directory in the current working directory
if [ ! -d "$CWD/.jdeploy-files" ]; then
  echo "Error: The .jdeploy-files directory does not exist in the current working directory."
  echo "Please create this directory and add the app.xml file to it."
  echo "This script should be run from inside a specific project's test directory.  E.g."
  echo "cd ~/jdeploy/installer/tests/snapcodejava && test_install.sh"
  echo "For more information, see the README.md file."
  exit 1
fi

# if on windows run the mock_launcher_win.exe script in the CWD else run the mock_launcher.sh script in the CWD
echo "Running on platform: $(uname -s)"
echo "Running on architecture: $(uname -m)"
echo "PROCESSOR_ARCHITECTURE=$PROCESSOR_ARCHITECTURE"
echo "PROCESSOR_ARCHITEW6432=$PROCESSOR_ARCHITEW6432"
case "$(uname -s)" in
    *ARM64*) arch="arm64" ;;
    *aarch64*|*AARCH64*) arch="arm64" ;;
    *) arch="x64" ;;
esac

IS_WINDOWS=false
if [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ] || [ "$(expr substr $(uname -s) 1 10)" == "MINGW64_NT" ]; then
  IS_WINDOWS=true
fi

function install_windows_x64() {
    # run the mock_launcher_win.exe script in the CWD
    cp "$CWD/../mock_launcher/mock_launcher_win_x64.exe" "$CWD/mock_launcher_win_x64.exe"
    "$CWD/mock_launcher_win_x64.exe" install

    echo "The uninstaller was written to the ~/.jdeploy/uninstallers directory."
    echo "You should test out the installer by going to Add/Remove programs and attempting to uninstall the application."
    echo "After running the uninstaller, you can check the results in the ~/.jdeploy/log/jdeploy-installer.log file."
}

function install_windows_arm64() {
    # run the mock_launcher_win.exe script in the CWD
    cp "$CWD/../mock_launcher/mock_launcher_win_arm64.exe" "$CWD/mock_launcher_win_arm64.exe"
    "$CWD/mock_launcher_win_arm64.exe" install

    echo "The uninstaller was written to the ~/.jdeploy/uninstallers directory."
    echo "You should test out the installer by going to Add/Remove programs and attempting to uninstall the application."
    echo "After running the uninstaller, you can check the results in the ~/.jdeploy/log/jdeploy-installer.log file."
}

function install_non_windows() {
    # run the mock_launcher.sh script in the CWD
    cp "$CWD/../mock_launcher/mock_launcher.sh" "$CWD/mock_launcher.sh"
    bash "$CWD/mock_launcher.sh" install
}

if [ "$IS_WINDOWS" = true ]; then
  if [ "$arch" == "arm64" ]; then
    install_windows_arm64
  else
    install_windows_x64
  fi
else
  install_non_windows
fi

