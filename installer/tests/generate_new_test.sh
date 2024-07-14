#!/bin/bash
set -e

# get path to current script directory
SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"

# get test name from script args
TEST_NAME=$1

# error if test name is not provided
if [ -z "$TEST_NAME" ]; then
    echo "Test name is required"
    exit 1
fi

# error if test name already exists
if [ -d "$SCRIPT_PATH/$TEST_NAME" ]; then
    echo "Test $TEST_NAME already exists"
    exit 1
fi

cp -r "$SCRIPT_PATH/template" "$SCRIPT_PATH/$TEST_NAME"

echo "Test $TEST_NAME created at $SCRIPT_PATH/$TEST_NAME"

echo "Modify the $SCRIPT_PATH/$TEST_NAME/.jdeploy-files/app.xml file as you see fit"
