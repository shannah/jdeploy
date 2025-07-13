#!/bin/bash
set -e

SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"

PROJECT_NAME=$1

if [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ] || [ "$(expr substr $(uname -s) 1 10)" == "MINGW64_NT" ]; then
  PLATFORM="windows"
elif [ "$(uname)" == "Darwin" ]; then
  PLATFORM="mac"
else
  PLATFORM="linux"
fi

function help() {
    echo "Usage: $0 <project_name> [--build] [--smoke] [--uninstall] [--uninstall-smoke] [--help]"
    echo "Options:"
    echo "  --build: Build the project before testing"
    echo "  --smoke: Run smoke test after testing"
    echo "  --uninstall: Uninstall the project after testing"
    echo "  --uninstall-smoke: Run uninstall smoke test after testing"
    echo "  --help: Display this help message"
}

if [ "$1" == "--help" ]; then
    help
    exit 0
fi

if [ -z "$PROJECT_NAME" ]; then
    echo "Project name is required"
    help
    exit 1
fi

PROJECT_PATH="$SCRIPT_PATH/tests/$PROJECT_NAME"

if [ ! -d "$PROJECT_PATH" ]; then
    echo "Project $PROJECT_NAME does not exist"
    help
    exit 1
fi

function load_project_details() {
  local projectDetailsPath="$PROJECT_PATH/test.env"
  if [ -f "$projectDetailsPath" ]; then
    source "$projectDetailsPath"
  fi

  if [ -z "$PROJECT_TITLE" ]; then
    echo "Error: PROJECT_TITLE is not set in $projectDetailsPath"
    help
    exit 1
  fi

  if [ -z "$JAVA_VERSION" ]; then
    JAVA_VERSION="11"
  fi

  if [ "$JAVA_VERSION" == "8" ]; then
    # When using Java 8, we need to use a private JRE
    # See https://github.com/shannah/jdeploy/issues/131
    USE_PRIVATE_JRE="true"
  else
    USE_PRIVATE_JRE="false"
  fi
}

load_project_details

function build_project() {
  cd "$SCRIPT_PATH/../shared"
  mvn package
  cd ../installer
  mvn package
}

function smoke_test() {
  # If SKIP_SMOKE_TEST is set, skip the smoke test
  if [ "$SKIP_INSTALLER_SMOKE_TESTS" == "true" ]; then
    echo "Skipping smoke test as SKIP_INSTALLER_SMOKE_TESTS is set to true"
    return
  fi
  if [ "$PLATFORM" == "windows" ]; then
    smoke_test_windows
  elif [ "$PLATFORM" == "mac" ]; then
    smoke_test_mac
  else
    smoke_test_linux
  fi
}

function smoke_test_windows() {
  echo "Running Smoke Test..."
  local UNINSTALLERS_PATH="$HOME/.jdeploy/uninstallers"
  local PROJECT_UNINSTALLER_PATH="$UNINSTALLERS_PATH/$PROJECT_NAME"
  if [ ! -d "$PROJECT_UNINSTALLER_PATH" ]; then
    echo "Smoke Test Failure: Uninstaller for $PROJECT_NAME not found at $PROJECT_UNINSTALLER_PATH"
    exit 1
  else
    echo "Found uninstaller for $PROJECT_NAME at $PROJECT_UNINSTALLER_PATH"
  fi

  local UNINSTALLER_FILES="$PROJECT_UNINSTALLER_PATH/.jdeploy-files"
  if [ ! -d "$UNINSTALLER_FILES" ]; then
    echo "Smoke Test Failure: Uninstaller files not found at $UNINSTALLER_FILES"
    exit 1
  else
    echo "Found uninstaller files at $UNINSTALLER_FILES"
  fi

  local expectedFiles=(
    "$PROJECT_UNINSTALLER_PATH/.jdeploy-files/app.xml"
    "$PROJECT_UNINSTALLER_PATH/.jdeploy-files/icon.png"
    "$PROJECT_UNINSTALLER_PATH/.jdeploy-files/installsplash.png"
    "$PROJECT_UNINSTALLER_PATH/$PROJECT_NAME-uninstall.exe"
  )

  for file in "${expectedFiles[@]}"; do
    if [ ! -f "$file" ]; then
      echo "Smoke Test Failure: Expected File $file not found"
      exit 1
    else
      echo "Found $file created successfully"
    fi
  done

  local expectedAppFiles=(
    "$HOME/.jdeploy/apps/$PROJECT_NAME/icon.ico"
    "$HOME/.jdeploy/apps/$PROJECT_NAME/icon.png"
    "$HOME/.jdeploy/apps/$PROJECT_NAME/$PROJECT_TITLE.exe"
  )

  local expectedAppFilesWhenUsingPrivateJRE=(
    "$HOME/.jdeploy/apps/$PROJECT_NAME/icon.ico"
    "$HOME/.jdeploy/apps/$PROJECT_NAME/icon.png"
    "$HOME/.jdeploy/apps/$PROJECT_NAME/bin/$PROJECT_TITLE.exe"
  )

  if [ "$USE_PRIVATE_JRE" == "true" ]; then
    expectedAppFiles=("${expectedAppFilesWhenUsingPrivateJRE[@]}")
  fi

  for file in "${expectedAppFiles[@]}"; do
    if [ ! -f "$file" ]; then
      echo "Smoke Test Failure: Expected File $file not found"
      exit 1
    else
      echo "Found $file created successfully"
    fi
  done

  echo "Smoke Test Passed"
}

function smoke_test_mac() {
  # TODO: Implement smoke test for mac
  echo "Running Smoke Test..."
  echo "Smoke Test Passed"
}

function smoke_test_linux() {
  # TODO: Implement smoke test for linux
  echo "Running Smoke Test..."
  echo "Smoke Test Passed"
}

function uninstall_project() {
  if [ "$PLATFORM" == "windows" ]; then
    uninstall_project_windows
  elif [ "$PLATFORM" == "mac" ]; then
    uninstall_project_mac
  else
    uninstall_project_linux
  fi
}

function uninstall_project_windows() {
  echo "Uninstalling $PROJECT_NAME"
  local UNINSTALLERS_PATH="$HOME/.jdeploy/uninstallers"
  local PROJECT_UNINSTALLER_PATH="$UNINSTALLERS_PATH/$PROJECT_NAME"
  if [ ! -d "$PROJECT_UNINSTALLER_PATH" ]; then
    echo "Uninstaller for $PROJECT_NAME not found at $PROJECT_UNINSTALLER_PATH"
    exit 1
  fi

  local UNINSTALLER_FILES="$PROJECT_UNINSTALLER_PATH/.jdeploy-files"
  if [ ! -d "$UNINSTALLER_FILES" ]; then
    echo "Uninstaller files not found at $UNINSTALLER_FILES"
    exit 1
  fi

  echo "Uninstalling $PROJECT_NAME"
  "$PROJECT_UNINSTALLER_PATH/$PROJECT_NAME-uninstall.exe" uninstall
}

function uninstall_project_mac() {
  echo "Uninstalling $PROJECT_NAME"
  echo "Skipping uninstall for mac because Mac doesn't require uninstall"
  echo "Just drag the app to the trash"
}

function uninstall_project_linux() {
  echo "Uninstalling $PROJECT_NAME"
  echo "Uninstall test for linux not implemented yet"
}

function uninstall_smoke_test_windows() {
  echo "Running Uninstall Smoke Test..."
  local UNINSTALLERS_PATH="$HOME/.jdeploy/uninstallers"
  local PROJECT_UNINSTALLER_PATH="$UNINSTALLERS_PATH/$PROJECT_NAME"
  local APP_PATH="$HOME/.jdeploy/apps/$PROJECT_NAME"

  # The uninstaller won't have been removed in this test because it runs delayed via the cmd command
  # and for some reason this doesn't work in the tests.  You need to manually uninstall via Add/Remove Programs
  # to test that the uninstaller is removed

  # Verify that the app directory was removed
  if [ -d "$APP_PATH" ]; then
    echo "Uninstall Smoke Test Failure: App directory $APP_PATH still exists"
    exit 1
  else
    echo "App directory $APP_PATH was removed"
  fi


  echo "Uninstall Smoke Test Passed"

}

function uninstall_smoke_test_mac() {
  echo "Running Uninstall Smoke Test..."
  echo "Skipping uninstall smoke test for mac because Mac doesn't require uninstall"
  echo "Just drag the app to the trash"
}

function uninstall_smoke_test_linux() {
  echo "Running Uninstall Smoke Test..."
  echo "Uninstall smoke test for linux not implemented yet"
}

function uninstall_smoke_test() {
  if [ "$SKIP_INSTALLER_SMOKE_TESTS" == "true" ]; then
    echo "Skipping uninstall smoke test as SKIP_INSTALLER_SMOKE_TESTS is set to true"
    return
  fi
  if [ "$PLATFORM" == "windows" ]; then
    uninstall_smoke_test_windows
  elif [ "$PLATFORM" == "mac" ]; then
    uninstall_smoke_test_mac
  else
    uninstall_smoke_test_linux
  fi
}

for arg in "$@"; do
  if [ "$arg" == "--build" ]; then
    build_project
  fi
done

cd "$SCRIPT_PATH/tests/$PROJECT_NAME"
bash ../test_install.sh

# Check for --smoke flag.  Doesn't matter what order.  There could be arbitrary number of args
# If --smoke flag is passed, run smoke test
for arg in "$@"; do
  if [ "$arg" == "--smoke" ]; then
    smoke_test
  fi
done

# If --uninstall flag is passed, uninstall the project
for arg in "$@"; do
  if [ "$arg" == "--uninstall" ]; then
    uninstall_project
  fi
done

# If --uninstall-smoke flag is passed, run uninstall smoke test
for arg in "$@"; do
  if [ "$arg" == "--uninstall-smoke" ]; then
    uninstall_smoke_test
  fi
done
