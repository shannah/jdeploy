#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"

# Create tmp dir using system temp dir command
TMP_DIR=$(mktemp -d)

cd $TMP_DIR
npm install jdeploy
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

SIGNED_JAR="$JDEPLOY_LIBS_DIR/jdeploy-installer-template-win-amd64-1.0-SNAPSHOT.jar"
mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                         -Dfile="$SIGNED_JAR" -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=jdeploy-installer-template-win-amd64 -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -e

cd "$SCRIPTPATH"

SIGNED_JAR="$JDEPLOY_LIBS_DIR/jdeploy-installer-template-linux-amd64-1.0-SNAPSHOT.jar"
mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                         -Dfile="$SIGNED_JAR" -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=jdeploy-installer-template-linux-amd64 -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -e
