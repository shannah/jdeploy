#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
export JDEPLOY="$SCRIPTPATH/../cli/target/jdeploy-cli-1.0-SNAPSHOT.jar"
cd projects
bash test.sh
echo "All Tests passed"