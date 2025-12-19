#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"

if [ -f "$HOME/.jdeploy/release_profile" ]; then
  source "$HOME/.jdeploy/release_profile"
fi

JDEPLOY="$SCRIPTPATH/cli/target/jdeploy-cli-1.0-SNAPSHOT.jar"

mvn clean install

# Skip tests if JDEPLOY_SKIP_INTEGRATION_TESTS is set
if [ -z "$JDEPLOY_SKIP_INTEGRATION_TESTS" ]; then
  cd tests
  bash test.sh
fi







