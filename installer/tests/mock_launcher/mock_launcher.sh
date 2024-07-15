#!/bin/env bash

# get path to current script
SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
SCRIPT_SELF="$(readlink -f "$0")"

# get path to installer directory
INSTALLER_PATH="$SCRIPT_PATH/../.."

INSTALLER_JAR_PATH="$INSTALLER_PATH/target/jdeploy-installer-1.0-SNAPSHOT.jar"
$JAVA_HOME/bin/java \
  -Dclient4j.appxml.path="$SCRIPT_PATH/.jdeploy-files/app.xml" \
  -Dclient4j.launcher.path="$SCRIPT_SELF" \
  -jar $JDEPLOY_INSTALLER_ARGS "$INSTALLER_JAR_PATH" $@