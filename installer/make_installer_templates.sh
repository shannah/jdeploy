#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
if [ ! -d "jdeploy/installers" ]; then
  mkdir jdeploy/installers
fi

if [ -d "jdeploy/installers/mac" ]; then
  rm -rf jdeploy/installers/mac
fi

mkdir jdeploy/installers/mac
MAC_INSTALLER=jdeploy/installers/mac/jdeploy-installer
mkdir "$MAC_INSTALLER"

cp -rp jdeploy/bundles/mac/jdeploy-installer.app "$MAC_INSTALLER/jdeploy-installer.app"
#mkdir "$MAC_INSTALLER/.jdeploy-files"
#touch "$MAC_INSTALLER/.jdeploy-files/app.xml"
#cp src/main/resources/ca/weblite/jdeploy/installer/icon.png "$MAC_INSTALLER/.jdeploy-files/icon.png"
cd $MAC_INSTALLER/..
tar -cvf jdeploy-installer-mac-amd64.tar jdeploy-installer
rm -rf jdeploy-installer
jar cvf jdeploy-installer-mac-amd64.jar *.tar
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=jdeploy-installer-mac-amd64.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=jdeploy-installer-template-mac-amd64 -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -DlocalRepositoryPath="$SCRIPTPATH/../maven-repository"


cd "$SCRIPTPATH"
rm -rf "$MAC_INSTALLER"

if [ -d "jdeploy/installers/windows" ]; then
  rm -rf jdeploy/installers/windows
fi

WIN_INSTALLER=jdeploy/installers/windows
mkdir "$WIN_INSTALLER"

cp -rp jdeploy/bundles/windows/jdeploy-installer.exe "$WIN_INSTALLER/jdeploy-installer-win-amd64.exe"

cd "$WIN_INSTALLER"
jar cvf jdeploy-installer-win-amd64.jar *.exe
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=jdeploy-installer-win-amd64.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=jdeploy-installer-template-win-amd64 -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -DlocalRepositoryPath="$SCRIPTPATH/../maven-repository"

cd "$SCRIPTPATH"
rm -rf "$WIN_INSTALLER"


if [ -d "jdeploy/installers/linux" ]; then
  rm -rf jdeploy/installers/linux
fi

LINUX_INSTALLER=jdeploy/installers/linux
mkdir "$LINUX_INSTALLER"

cp -rp jdeploy/bundles/linux/jdeploy-installer "$LINUX_INSTALLER/jdeploy-installer-linux-amd64"

cd "$LINUX_INSTALLER"
jar cvf jdeploy-installer-linux-amd64.jar *
mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=jdeploy-installer-linux-amd64.jar -DgroupId=ca.weblite.jdeploy \
                         -DartifactId=jdeploy-installer-template-linux-amd64 -Dversion=1.0-SNAPSHOT \
                         -Dpackaging=jar -DlocalRepositoryPath="$SCRIPTPATH/../maven-repository"
cd "$SCRIPTPATH"
rm -rf "$LINUX_INSTALLER"

# We need to purge the local (.m2) repositories so that on the next build it will fetch from
# our local (in-project) repository
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-win-amd64 -Dverbose
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-mac-amd64 -Dverbose
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-linux-amd64 -Dverbose


