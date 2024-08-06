#!/bin/bash
set -e

# Function to generate an RSA keypair and self-signed certificate
generate_keypair_and_certificate() {
  # Create a temporary directory to store the keys
  temp_dir=$(mktemp -d)

  # Generate RSA private key
  openssl genpkey -algorithm RSA -out "$temp_dir/private_key.pem" -pkeyopt rsa_keygen_bits:2048

  # Generate a self-signed certificate
  openssl req -new -x509 -key "$temp_dir/private_key.pem" -out "$temp_dir/certificate.pem" -days 365 -subj "/CN=jdeploy-test"

  # Read the PEM-encoded private key and certificate
  JDEPLOY_PRIVATE_KEY=$(cat "$temp_dir/private_key.pem")
  JDEPLOY_CERTIFICATE=$(cat "$temp_dir/certificate.pem")

  # Export environment variables
  export JDEPLOY_PRIVATE_KEY
  export JDEPLOY_CERTIFICATE

  # Clean up temporary directory
  rm -rf "$temp_dir"
}

# Generate the keypair and certificate before running the jdeploy command
generate_keypair_and_certificate

if [ -f .env ]; then
  source .env
fi

echo "Running test with TextEditor project in $(pwd)"
if [ -z "$JDEPLOY" ]; then
  echo "JDEPLOY environment variable must be set"
  exit 1
fi

# Run the jdeploy clean package command
java -jar "$JDEPLOY" clean package

if [ ! -d "jdeploy-bundle" ]; then
  echo "Missing jdeploy-bundle\n"
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

echo "TextEditor project test passed"
