#!/usr/bin/env bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
function test_shellmarks() {
  cd $SCRIPTPATH
  bash test_install_single_project.sh shellmarks --build --smoke --uninstall --uninstall-smoke
}

function test_snapcodejava() {
  cd $SCRIPTPATH
  bash test_install_single_project.sh snapcodejava --smoke --uninstall --uninstall-smoke
}

if [ -z "$JDEPLOY_PROJECT_PATH" ]; then
  JDEPLOY_PROJECT_PATH="$( cd "$(dirname "$SCRIPTPATH")" ; pwd -P )"
fi
echo "Running jDeploy installer tests with JDEPLOY_PROJECT_PATH=$JDEPLOY_PROJECT_PATH"
test_shellmarks
test_snapcodejava