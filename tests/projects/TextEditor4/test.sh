#!/bin/bash
set -e
echo "Running test with TextEditor4 project in $(pwd)"
echo "This tests jar file in same directory as package.json, but with leading dot in path"
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
echo "TextEditor4 project test passed"


