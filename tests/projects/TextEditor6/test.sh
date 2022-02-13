#!/bin/bash
set -e
echo "Running test with TextEditor6 project in $(pwd)"
echo "This should copy the javafx-base-11.0.2.jar file into the jdeploy directory"
if [ -z "$JDEPLOY" ]; then
  echo "JDEPLOY environment variable must be set"
  exit 1
fi
java -jar "$JDEPLOY" clean package
if [ ! -d "jdeploy-bundle" ]; then
  echo "MIssing jdeploy-bundle\n"
  exit 1
fi

if [ ! -f "jdeploy-bundle/TextEditor-1.0-SNAPSHOT.jar" ]; then
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
if [ ! -f "jdeploy-bundle/libs/javafx-base-11.0.2.jar" ]; then
  echo "jdeploy package did not copy javafx-base-11.0.2.jar file correctly."
  exit 1
fi
echo "TextEditor6 project test passed"


