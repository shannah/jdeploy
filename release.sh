#!/bin/bash
set -e
echo "====================== jDeploy Release =============================="
SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"

if [ -f "$HOME/.jdeploy/release_profile" ]; then
  source "$HOME/.jdeploy/release_profile"
fi

JDEPLOY="$SCRIPTPATH/cli/target/jdeploy-cli-1.0-SNAPSHOT.jar"

echo "-------- Installing Parent POM ---------------"
# Install the parent pom first so child modules can reference it
mvn install -N

echo "-------- Building jDeploy Shared Library ---------------"
#First build the shared library which is used by both cli and installer
cd shared
mvn clean install

echo "-------- Building jDeploy Installer Module ---------------"
# Build and install installer module (cli depends on it)
cd ../installer
mvn clean install

echo "-------- Building jDeploy CLI ------------"

# Build and link jDeploy to use for building installer.
# After installer is built - we'll circle back and rebuild jDeploy with new installer
cd ../cli
mvn clean package

echo "----------- Building jDeploy Installer -------------"

#Next build the installer because we need to sign it and bundle it
cd ../installer
bash ./build-release.sh
if [ "$GITHUB_REF_TYPE" != "tag" ] || [[ "$GITHUB_REF_NAME" =~ "-alpha" ]] || [[ "$GITHUB_REF_NAME" =~ "-dev" ]]; then
  # IF this is not a tag, or it is a tagged prerelease, then we'll mark the installer as
  # a prerelease installer so that it gets the latest installer - even prerelease.
  echo "----------------  Building Pre-release Bundle ------------------------"
  REGISTRY_URL_PROPERTY=""
  if [[ "$GITHUB_REF_NAME" =~ "-dev" ]]; then
    # Builds marked dev should use the dev registry URL.
    REGISTRY_URL_PROPERTY="-Djdeploy.registry.url=https://dev.jdeploy.com/"
  fi
  JDEPLOY_BUNDLE_PRERELEASE=true java "$REGISTRY_URL_PROPERTY" -jar "$JDEPLOY" clean package
else
  # Otherwise, we just build normally - in which case the installer will only use the latest
  # stable version.
  echo "----------------  Building Release Release Bundle ------------------------"
  java -jar "$JDEPLOY" clean package
fi

for WINDOWS_ARCH in "x64" "arm64"; do
  if [ ! -z "$EV_CODESIGN_SUBMITTER_PRIVATE_KEY" ] && [ ! -z "$EV_CODESIGN_PROCESSOR_PUBLIC_KEY" ]; then
    mkdir -p ~/.jdeploy-codesigner/private
    echo "-------------------   About to Sign Windows Installer  with EV Cert --------------------------"
    echo "$EV_CODESIGN_SUBMITTER_PRIVATE_KEY" > ~/.jdeploy-codesigner/private/submitter-private-key.pem
    echo "$EV_CODESIGN_PROCESSOR_PUBLIC_KEY" > ~/.jdeploy-codesigner/processor-public-key.pem
    bash $SCRIPTPATH/scripts/windows-ev-codesign.sh jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer.exe jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer-signed.exe
    mv jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer-signed.exe jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer.exe
    rm -rf ~/.jdeploy-codesigner
  else
    echo "-------------------   About to Sign Windows Installer with OV Cert  --------------------------"
    echo "$AUTHENTICODE_SPC" | base64 --decode > authenticode.spc
    echo "$AUTHENTICODE_KEY" | base64 --decode > authenticode.key

    osslsigncode \
      -spc authenticode.spc \
      -key authenticode.key \
      -t http://timestamp.digicert.com \
      -in jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer.exe \
      -out jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer-signed.exe \
      -n "jDeploy Application Installer" \
      -i https://www.jdeploy.com

    mv jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer-signed.exe jdeploy/bundles/windows-$WINDOWS_ARCH/jdeploy-installer.exe
    rm authenticode.spc
    rm authenticode.key
  fi
done



for MAC_ARCH in "x64" "arm64"; do
  echo "-------------------- Building Mac Installer for $MAC_ARCH arch -------------------------"
  codesign -vvvv jdeploy/bundles/mac-$MAC_ARCH/jdeploy-installer.app
  APP_PATH="jdeploy/bundles/mac-$MAC_ARCH/jdeploy-installer.app"
  ZIP_PATH="${APP_PATH}.zip"

  # Create a ZIP archive suitable for notarization.
  /usr/bin/ditto -c -k --keepParent "$APP_PATH" "$ZIP_PATH"

  TEAM_ID_FLAG=""
  if [ ! -z "$APPLE_TEAM_ID" ]; then
    TEAM_ID_FLAG="--team-id $APPLE_TEAM_ID"
  fi

  xcrun notarytool store-credentials "AC_PASSWORD" --apple-id "$APPLE_ID" $TEAM_ID_FLAG --password "$APPLE_2FA_PASSWORD"
  xcrun notarytool submit "$ZIP_PATH" --keychain-profile "AC_PASSWORD" --wait
  xcrun stapler staple "$APP_PATH"
  xcrun stapler validate "$APP_PATH"
done

echo "-------------------  About to Make Installer Templates --------------------------"

bash make_installer_templates.sh
cd ../cli

mvn clean package
CLI_VERSION=$(../json.php version)
if [ "$GITHUB_REF_TYPE" == "tag" ]; then
  npm version "$GITHUB_REF_NAME"
  VERSION="$GITHUB_REF_NAME"
  DIST_TAG="latest"  # Default fallback

  if [[ "$VERSION" =~ -alpha\.[0-9]+$ ]]; then
    DIST_TAG="alpha"
  elif [[ "$VERSION" =~ -beta\.[0-9]+$ ]]; then
    DIST_TAG="next"
  elif [[ "$VERSION" =~ -rc\.[0-9]+$ ]]; then
    DIST_TAG="next"
  elif [[ "$VERSION" =~ -dev\.[0-9]+$ ]]; then
    DIST_TAG="dev"
  fi
  npm publish --tag "$DIST_TAG"
fi

cd ../installer
INSTALLER_VERSION=$(../json.php version)
if [ "$GITHUB_REF_TYPE" == "tag" ]; then
  npm version "$GITHUB_REF_NAME"
  VERSION="$GITHUB_REF_NAME"
  DIST_TAG="latest"  # Default fallback

  if [[ "$VERSION" =~ -alpha\.[0-9]+$ ]]; then
    DIST_TAG="alpha"
  elif [[ "$VERSION" =~ -beta\.[0-9]+$ ]]; then
    DIST_TAG="next"
  elif [[ "$VERSION" =~ -rc\.[0-9]+$ ]]; then
    DIST_TAG="next"
  elif [[ "$VERSION" =~ -dev\.[0-9]+$ ]]; then
    DIST_TAG="dev"
  fi
  java -jar "$JDEPLOY" publish --tag "$DIST_TAG"
fi
