#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
cd "$SCRIPTPATH/../jdeploy-maven-plugin"
mvn deploy -Psign-artifacts
cd "$SCRIPTPATH/../jdeploy-javafx-starter"
mvn deploy -Psign-artifacts