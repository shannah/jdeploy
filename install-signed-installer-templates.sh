#!/bin/bash
# Usage: ./install-signed-installer-templates.sh [version]
# Example: ./install-signed-installer-templates.sh 4.0.97
# If no version is specified, installs the latest version
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"

# Default to latest version if no version specified
JDEPLOY_VERSION=${1:-"latest"}

# Create tmp dir using system temp dir command
TMP_DIR=$(mktemp -d)

cd $TMP_DIR
if [ "$JDEPLOY_VERSION" = "latest" ]; then
  npm install jdeploy
else
  npm install jdeploy@$JDEPLOY_VERSION
fi
JDEPLOY_LIBS_DIR=$TMP_DIR/node_modules/jdeploy/bin/libs

for MAC_ARCH in "x64" "arm64"; do
  ARCH_SUFFIX="amd64"
  if [ "$MAC_ARCH" = "arm64" ]; then
    ARCH_SUFFIX="arm64"
  fi
  SIGNED_JAR="$JDEPLOY_LIBS_DIR/jdeploy-installer-template-mac-$ARCH_SUFFIX-1.0-SNAPSHOT.jar"
  mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                           -Dfile="$SIGNED_JAR" -DgroupId=ca.weblite.jdeploy \
                           -DartifactId=jdeploy-installer-template-mac-$ARCH_SUFFIX -Dversion=1.0-SNAPSHOT \
                           -Dpackaging=jar -e -X
  cd "$SCRIPTPATH"
done

for WINDOWS_ARCH in "amd64" "arm64"; do
  SIGNED_JAR="$JDEPLOY_LIBS_DIR/jdeploy-installer-template-win-$WINDOWS_ARCH-1.0-SNAPSHOT.jar"
  mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                           -Dfile="$SIGNED_JAR" -DgroupId=ca.weblite.jdeploy \
                           -DartifactId=jdeploy-installer-template-win-$WINDOWS_ARCH -Dversion=1.0-SNAPSHOT \
                           -Dpackaging=jar -e
  cd "$SCRIPTPATH"
done

for LINUX_ARCH in "amd64" "arm64"; do
  SIGNED_JAR="$JDEPLOY_LIBS_DIR/jdeploy-installer-template-linux-$LINUX_ARCH-1.0-SNAPSHOT.jar"
  mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                           -Dfile="$SIGNED_JAR" -DgroupId=ca.weblite.jdeploy \
                           -DartifactId=jdeploy-installer-template-linux-$LINUX_ARCH -Dversion=1.0-SNAPSHOT \
                           -Dpackaging=jar -e
  cd "$SCRIPTPATH"
done
