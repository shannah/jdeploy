#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
cd "$SCRIPTPATH/../jdeploy-maven-plugin"
mvn install -Psign-artifacts
cd "$SCRIPTPATH/../jdeploy-javafx-starter"
mvn install -Psign-artifacts
cd "$SCRIPTPATH/../jdeploy-javafx-archetype"
mvn install -Psign-artifacts