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
if ! [ -f ~/.jdeploy/.env.dev ]; then
  echo "Error: The ~/.jdeploy/.env.dev file does not exist."
  echo "Please create this file and add the JAVA_HOME environment variable."
  echo "This is used to specify the absolute path to the Java installation that will be used by the uninstaller"
  echo "For more information, see the README.md file."
  exit 1
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
IS_WINDOWS=false
if [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ] || [ "$(expr substr $(uname -s) 1 10)" == "MINGW64_NT" ]; then
  IS_WINDOWS=true
fi

function install_windows() {
    # run the mock_launcher_win.exe script in the CWD
    cp "$CWD/../mock_launcher/mock_launcher_win.exe" "$CWD/mock_launcher_win.exe"
    "$CWD/mock_launcher_win.exe" install

    echo "The uninstaller was written to the ~/.jdeploy/uninstallers directory."
    echo "You should test out the installer by going to Add/Remove programs and attempting to uninstall the application."
    echo "After running the uninstaller, you can check the results in the ~/.jdeploy/log/jdeploy-installer.log file."
}

function install_non_windows() {
    # run the mock_launcher.sh script in the CWD
    cp "$CWD/../mock_launcher/mock_launcher.sh" "$CWD/mock_launcher.sh"
    "$CWD/mock_launcher.sh" install
}

if [ "$IS_WINDOWS" = true ]; then
  install_windows
else
  install_non_windows
fi

