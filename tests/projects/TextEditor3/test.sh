#!/bin/bash
set -e
echo "Running test with TextEditor3 project in $(pwd)"
echo "This tests placing jar file a couple of subdirectory levels deep"
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
echo "TextEditor 3 project test passed"


