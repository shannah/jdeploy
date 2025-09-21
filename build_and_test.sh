#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"

if [ -f "$HOME/.jdeploy/release_profile" ]; then
  source "$HOME/.jdeploy/release_profile"
fi

JDEPLOY="$SCRIPTPATH/cli/target/jdeploy-cli-1.0-SNAPSHOT.jar"

#First build the shared library which is used by both cli and installer
cd shared
mvn clean install

# Build and link jDeploy to use for building installer.
# After installer is built - we'll circle back and rebuild jDeploy with new installer
cd ../cli
mvn clean package

# Skip tests if JDEPLOY_SKIP_INTEGRATION_TESTS is set
if [ -z "$JDEPLOY_SKIP_INTEGRATION_TESTS" ]; then
  cd ../tests
  bash test.sh
fi







