#!/bin/bash
# Build script for creating a shrunk release version of the installer

set -e

SCRIPTPATH="$( cd "$(dirname "$0")" ; pwd -P )"
cd "$SCRIPTPATH"

# Set JAVA_HOME to Java 8 if available, otherwise use current
if [ -d "/Users/shannah/Library/Java/JavaVirtualMachines/corretto-1.8.0_452/Contents/Home" ]; then
    # Local development - use Java 8
    export JAVA_HOME="/Users/shannah/Library/Java/JavaVirtualMachines/corretto-1.8.0_452/Contents/Home"
    echo "Using Java 8 for build: $JAVA_HOME"
elif [ -z "$JAVA_HOME" ]; then
    # GitHub Actions or other environments - auto-detect
    JAVA_HOME=$(mvn help:evaluate -Dexpression=java.home -q -DforceStdout | sed 's|/jre||')
    echo "Auto-detected JAVA_HOME: $JAVA_HOME"
fi

echo "Building installer with Maven..."
mvn clean package

echo "Creating fat JAR with all dependencies..."
mkdir -p target/fat-jar-temp
cd target/fat-jar-temp

# Extract all JARs
echo "Extracting installer JAR..."
jar xf ../jdeploy-installer-1.0-SNAPSHOT.jar

echo "Extracting dependency JARs..."
for jar in ../libs/*.jar; do
    echo "  Extracting $(basename $jar)..."
    jar xf "$jar" 2>/dev/null || true
done

# Remove duplicate META-INF files
rm -rf META-INF/MANIFEST.MF
rm -rf META-INF/*.SF
rm -rf META-INF/*.DSA
rm -rf META-INF/*.RSA
rm -rf module-info.class

# Create manifest
echo "Creating manifest..."
cat > META-INF/MANIFEST.MF << EOF
Manifest-Version: 1.0
Main-Class: ca.weblite.jdeploy.installer.Main
EOF

# Create fat JAR
echo "Creating fat JAR..."
jar cfm ../installer-fat.jar META-INF/MANIFEST.MF .
cd ../..

echo "Running ProGuard to shrink fat JAR..."

# Download ProGuard if not present
PROGUARD_JAR="$SCRIPTPATH/../proguard-7.4.2/lib/proguard.jar"
if [ ! -f "$PROGUARD_JAR" ]; then
    echo "Downloading ProGuard..."
    cd "$SCRIPTPATH/.."
    curl -L -o proguard.tar.gz https://github.com/Guardsquare/proguard/releases/download/v7.4.2/proguard-7.4.2.tar.gz
    tar xzf proguard.tar.gz
    rm proguard.tar.gz
    cd "$SCRIPTPATH"
fi

# Create ProGuard config file
cat > target/proguard-release.conf << 'EOF'
-injars installer-fat.jar
-outjars installer-release.jar

-libraryjars <java.home>/lib/rt.jar
-libraryjars <java.home>/lib/jce.jar
-libraryjars <java.home>/lib/jsse.jar

# Keep main entry point
-keep public class ca.weblite.jdeploy.installer.Main {
    public static void main(java.lang.String[]);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ShellLink for DLL loading
-keep class com.izforge.izpack.util.os.ShellLink { *; }

# Keep appbundler classes
-keep class ca.weblite.jdeploy.appbundler.** { *; }
-keep class com.joshondesign.appbundler.** { *; }

# Keep security/tools classes
-keep class ca.weblite.tools.** { *; }

# Keep serialization
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep resources
-adaptresourcefilenames **.properties,**.dll,**.exe,**.png,**.ico,**.jar,**.tiff
-adaptresourcefilecontents **.properties,**.xml

# Don't optimize or obfuscate
-dontoptimize
-dontobfuscate

# Ignore warnings
-ignorewarnings
-dontwarn **

# Keep attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
EOF

# Run ProGuard with config file (from target directory)
cd target
$JAVA_HOME/bin/java -jar "$PROGUARD_JAR" @proguard-release.conf
cd ..

# Clean up temp directory
rm -rf target/fat-jar-temp
rm -f target/installer-fat.jar

# Replace the original JAR with the shrunk version
if [ -f target/installer-release.jar ]; then
    mv target/installer-release.jar target/jdeploy-installer-1.0-SNAPSHOT.jar
    echo "Done! Shrunk installer replaced at: target/jdeploy-installer-1.0-SNAPSHOT.jar"
    ls -lh target/jdeploy-installer-1.0-SNAPSHOT.jar
else
    echo "ERROR: ProGuard failed to create installer-release.jar"
    exit 1
fi