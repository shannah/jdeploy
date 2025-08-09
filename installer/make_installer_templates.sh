#!/bin/bash
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
if [ ! -d "jdeploy/installers" ]; then
  mkdir jdeploy/installers
fi

for MAC_ARCH in "x64" "arm64"; do
  ARCH_SUFFIX="amd64"
  if [ "$MAC_ARCH" = "arm64" ]; then
    ARCH_SUFFIX="arm64"
  fi
  if [ -d "jdeploy/installers/mac-$MAC_ARCH" ]; then
    rm -rf "jdeploy/installers/mac-$MAC_ARCH"
  fi

  mkdir "jdeploy/installers/mac-$MAC_ARCH"
  MAC_INSTALLER="jdeploy/installers/mac-$MAC_ARCH/jdeploy-installer"
  mkdir "$MAC_INSTALLER"

  cp -rp "jdeploy/bundles/mac-$MAC_ARCH/jdeploy-installer.app" "$MAC_INSTALLER/jdeploy-installer.app"
  cd $MAC_INSTALLER/..
  tar -cvf jdeploy-installer-mac-$ARCH_SUFFIX.tar jdeploy-installer
  rm -rf jdeploy-installer
  jar cvf jdeploy-installer-mac-$ARCH_SUFFIX.jar *.tar
  mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                           -Dfile=jdeploy-installer-mac-$ARCH_SUFFIX.jar -DgroupId=ca.weblite.jdeploy \
                           -DartifactId=jdeploy-installer-template-mac-$ARCH_SUFFIX -Dversion=1.0-SNAPSHOT \
                           -Dpackaging=jar -DlocalRepositoryPath="$SCRIPTPATH/../maven-repository" -e -X


  cd "$SCRIPTPATH"
  rm -rf "$MAC_INSTALLER"
done

for WINDOWS_ARCH in "x64" "arm64"; do
  if [ "$WINDOWS_ARCH" == "x64" ]; then
    WINDOWS_EXT="amd64"
  else
    WINDOWS_EXT="$WINDOWS_ARCH"
  fi
  if [ -d "jdeploy/installers/windows-$WINDOWS_ARCH" ]; then
    rm -rf "jdeploy/installers/windows-$WINDOWS_ARCH"
  fi

  WIN_INSTALLER="jdeploy/installers/windows-$WINDOWS_ARCH"
  mkdir "$WIN_INSTALLER"

  cp -rp "jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer.exe" "$WIN_INSTALLER/jdeploy-installer-win-$WINDOWS_EXT.exe"

  cd "$WIN_INSTALLER"
  jar cvf "jdeploy-installer-win-$WINDOWS_EXT.jar" *.exe
  mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                           "-Dfile=jdeploy-installer-win-$WINDOWS_EXT.jar" -DgroupId=ca.weblite.jdeploy \
                           "-DartifactId=jdeploy-installer-template-win-$WINDOWS_EXT" -Dversion=1.0-SNAPSHOT \
                           -Dpackaging=jar -DlocalRepositoryPath="$SCRIPTPATH/../maven-repository" -e

  cd "$SCRIPTPATH"
  rm -rf "$WIN_INSTALLER"
done
for LINUX_ARCH in "x64" "arm64"; do
  if [ "$LINUX_ARCH" == "x64" ]; then
    LINUX_EXT="amd64"
  else
    LINUX_EXT="$LINUX_ARCH"
  fi
  if [ -d "jdeploy/installers/linux-$LINUX_ARCH" ]; then
    rm -rf "jdeploy/installers/linux-$LINUX_ARCH"
  fi

  LINUX_INSTALLER="jdeploy/installers/linux-$LINUX_ARCH"
  mkdir "$LINUX_INSTALLER"

  cp -rp "jdeploy/bundles/linux-$LINUX_ARCH/jdeploy-installer" "$LINUX_INSTALLER/jdeploy-installer-linux-$LINUX_EXT"

  cd "$LINUX_INSTALLER"
  jar cvf jdeploy-installer-linux-$LINUX_EXT.jar *
  mvn org.apache.maven.plugins:maven-install-plugin:3.1.0:install-file \
                           -Dfile=jdeploy-installer-linux-$LINUX_EXT.jar -DgroupId=ca.weblite.jdeploy \
                           -DartifactId=jdeploy-installer-template-linux-$LINUX_EXT -Dversion=1.0-SNAPSHOT \
                           -Dpackaging=jar -DlocalRepositoryPath="$SCRIPTPATH/../maven-repository" -e
  cd "$SCRIPTPATH"
  rm -rf "$LINUX_INSTALLER"
done

# We need to purge the local (.m2) repositories so that on the next build it will fetch from
# our local (in-project) repository
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-win-amd64 -Dverbose
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-win-arm64 -Dverbose
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-mac-amd64 -Dverbose
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-mac-arm64 -Dverbose
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-linux-amd64 -Dverbose
mvn dependency:purge-local-repository -DmanualInclude=ca.weblite.jdeploy:jdeploy-installer-template-linux-arm64 -Dverbose


