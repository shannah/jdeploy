#!/bin/bash
set -e
echo "Running NPE fix test for jdeploy init in $(pwd)"
echo "This tests that 'jdeploy init' doesn't throw NullPointerException with relative package.json path"

if [ -z "$JDEPLOY" ]; then
  echo "JDEPLOY environment variable must be set"
  exit 1
fi

# Create a clean test environment
rm -f package.json
rm -rf dist jdeploy-bundle

# Create a minimal Java project structure that jdeploy can recognize
mkdir -p dist
echo "dummy jar content" > dist/app.jar

# Create a minimal pom.xml to help jdeploy detect the project
cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.test</groupId>
    <artifactId>npe-test-app</artifactId>
    <version>1.0.0</version>
    <name>NPE Test App</name>
    <build>
        <finalName>app</finalName>
    </build>
</project>
EOF

echo "Created test project structure"
echo "Running 'jdeploy init --no-prompt --no-workflow'"

# This is the critical test: run jdeploy init
# Before the fix, this would throw:
# Exception in thread "main" java.lang.RuntimeException: java.lang.NullPointerException
#     at ca.weblite.jdeploy.JDeploy.main(JDeploy.java:714)
# Caused by: java.lang.NullPointerException
#     at ca.weblite.jdeploy.JDeploy.init(JDeploy.java:197)

java -jar "$JDEPLOY" init --no-prompt --no-workflow

# If we get here without an exception, the NPE fix is working
echo "jdeploy init completed successfully (no NPE thrown)"

# Verify that package.json was created
if [ ! -f "package.json" ]; then
  echo "ERROR: package.json was not created by jdeploy init"
  exit 1
fi

echo "package.json was created successfully"

# Verify the package.json has expected content
if ! grep -q "jdeploy" package.json; then
  echo "ERROR: package.json does not contain jdeploy configuration"
  exit 1
fi

echo "package.json contains jdeploy configuration"

# Clean up
rm -f package.json pom.xml
rm -rf dist

echo "InitNPETest passed: jdeploy init works without NullPointerException"