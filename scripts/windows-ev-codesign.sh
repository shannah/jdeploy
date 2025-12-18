#!/bin/bash

# Define the URL of the .tgz file
TAR_URL="https://github.com/shannah/winevcodesign-releases/releases/download/master/winevcodesign-0.0.0-master.tgz"
# Define the name of the downloaded file
TAR_FILE="winevcodesign-0.0.0-master.tgz"
# Define the extraction directory
EXTRACT_DIR="winevcodesign"

INPUT_FILE="$1"

OUTPUT_FILE="$2"

# Download the .tgz file using curl. You can use wget if you prefer.
curl -L -o "$TAR_FILE" "$TAR_URL"

# Alternatively, if you prefer wget, uncomment the following line and comment out the curl command above.
# wget -O "$TAR_FILE" "$TAR_URL"

# Create the extraction directory
mkdir -p "$EXTRACT_DIR"

# Extract the .tgz file
tar -xvzf "$TAR_FILE" -C "$EXTRACT_DIR"

# Run the Java application
$JAVA_HOME/bin/java -jar "$EXTRACT_DIR/package/jdeploy-bundle/winevcodesign-1.0-SNAPSHOT.jar" \
  sign "$INPUT_FILE" "$OUTPUT_FILE"

rm -rf "$EXTRACT_DIR"
rm "$TAR_FILE"
