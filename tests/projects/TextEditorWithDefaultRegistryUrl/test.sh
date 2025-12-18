#!/bin/bash
set -e
echo "Running test with TextEditor7 project in $(pwd)"
echo "This should NOT copy the javafx-base-11.0.2.jar file into the jdeploy directory"
echo "Because javafx = true so the server will provide it"
if [ -z "$JDEPLOY" ]; then
  echo "JDEPLOY environment variable must be set"
  exit 1
fi
BUNDLES='["mac-x64","mac-arm64"]'
JDEPLOY_CONFIG="$(printf  '{"jdeployHome": "%s", "bundles": %s}' "$SCRIPTPATH/jdeploy/home" "$BUNDLES" )"  \
java -Djdeploy.registry.url="https://dev.jdeploy.com/" -jar "$JDEPLOY" clean package
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
if [ -f "jdeploy-bundle/libs/javafx-base-11.0.2.jar" ]; then
  echo "jdeploy package did not copy javafx-base-11.0.2.jar file correctly."
  exit 1
fi

# We only do mac bundles in this test because the app.xml file is embedded in the executable for linux and windows
# so it is harder to check.  Need to put more work into testing for those bundles
for BUNDLE in mac-x64 mac-arm64; do
  APP_XML_PATH="jdeploy/bundles/mac-x64/Text Editor.app/Contents/app.xml"
  if [ ! -f "$APP_XML_PATH" ]; then
    echo "Missing $APP_XML_PATH"
    exit 1
  fi
  REG_URL=$(java -jar "$JDEPLOY" get-appxml-property "$APP_XML_PATH" registry-url)
  if [ "$REG_URL" != "https://dev.jdeploy.com/" ]; then
    echo "app.xml does not contain the correct registry-url attribute."
    exit 1
  fi
done
APP_XML_PATH="jdeploy/bundles/mac-x64/Text Editor.app/Contents/app.xml"

echo "TextEditorWithCustomRegistryUrl project test passed"


