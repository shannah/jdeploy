#!/bin/bash
set -e
cd ../..
export JDEPLOY_INSTALLER_ARGS="-Djdeploy.installerTheme=fxtheme"
bash test_shellmarks.sh
