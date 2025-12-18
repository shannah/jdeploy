# Claude Instructions for jDeploy Setup

When a Java developer asks you to "setup jDeploy" or similar requests, follow these instructions:

## Overview

jDeploy setup focuses on configuring the project to work with jDeploy, whether installed as a desktop app or npm package. No installation of jDeploy is required during setup.

## 1. Prerequisites Check

First, verify the project structure:
- Check for `pom.xml` (Maven) or `build.gradle` (Gradle)
- Ensure the project builds successfully
- Identify if it has a main class
- Check current JAR output configuration

## 2. Configure Executable JAR Build

### Preferred: JAR with Dependencies in lib/ Directory

**Maven (using maven-dependency-plugin):**
```xml
<plugin>
   <groupId>org.apache.maven.plugins</groupId>
   <artifactId>maven-dependency-plugin</artifactId>
   <version>3.2.0</version>
   <executions>
      <execution>
         <id>copy-dependencies</id>
         <phase>package</phase>
         <goals>
            <goal>copy-dependencies</goal>
         </goals>
         <configuration>
            <outputDirectory>${project.build.directory}/lib</outputDirectory>
         </configuration>
      </execution>
   </executions>
</plugin>
<plugin>
<groupId>org.apache.maven.plugins</groupId>
<artifactId>maven-jar-plugin</artifactId>
<version>3.2.2</version>
<configuration>
   <archive>
      <manifest>
         <addClasspath>true</addClasspath>
         <classpathPrefix>lib/</classpathPrefix>
         <mainClass>com.example.MainClass</mainClass>
      </manifest>
   </archive>
</configuration>
</plugin>
```

**Gradle (using application plugin):**
```gradle
plugins {
    id 'application'
}

application {
    mainClass = 'com.example.MainClass'
}

task copyDependencies(type: Copy) {
    from configurations.runtimeClasspath
    into "$buildDir/libs/lib"
}

jar {
    dependsOn copyDependencies
    manifest {
        attributes(
            'Main-Class': application.mainClass,
            'Class-Path': configurations.runtimeClasspath.collect { "lib/" + it.getName() }.join(' ')
        )
    }
}
```

### Alternative: Shaded/Fat JAR (if already configured)

If the project already produces a shaded JAR, that's acceptable:

**Maven (maven-shade-plugin):** Keep existing configuration
**Gradle (shadow plugin):** Keep existing configuration

## 3. Detect JavaFX Usage

Before configuring package.json, check if the project uses JavaFX:

### Check for JavaFX Imports

Search for JavaFX imports in the source code to determine if the project uses JavaFX:

```bash
# For Maven projects
grep -r "import javafx\." src/ --include="*.java" 2>/dev/null | head -5

# For Gradle projects
find . -name "*.java" -o -name "*.kt" | xargs grep "import javafx\." 2>/dev/null | head -5

# Alternative using ripgrep (if available)
rg "import javafx\." --type java --type kotlin
```

If any JavaFX imports are found (e.g., `import javafx.application.Application`, `import javafx.scene.Scene`, etc.), then set `"javafx": true` in the package.json configuration. Otherwise, set it to `false` or omit it.

## 4. Configure package.json

Create or modify `package.json` with required jDeploy configuration:

```json
{
   "bin": {"{{ appName }}": "jdeploy-bundle/jdeploy.js"},
   "author": "",
   "description": "",
   "main": "index.js",
   "preferGlobal": true,
   "repository": "",
   "version": "1.0.0",
   "jdeploy": {
      "jdk": false,
      "javaVersion": "21",
      "jar": "build/libs/{{ artifactId }}-1.0.0.jar",
      "javafx": true,
      "title": "{{ appTitle }}"
   },
   "dependencies": {
      "command-exists-promise": "^2.0.2",
      "node-fetch": "2.6.7",
      "tar": "^4.4.8",
      "yauzl": "^2.10.0",
      "shelljs": "^0.8.4"
   },
   "license": "ISC",
   "name": "{{ appName }}",
   "files": ["jdeploy-bundle"],
   "scripts": {"test": "echo \"Error: no test specified\" && exit 1"}
}
```

### Key Configuration for Different Build Types:

**JAR with lib/ directory:**
```json
"jdeploy": {
  "jar": "target/myapp-1.0.jar",
  "javaVersion": "11",
  "title": "My Application"
}
```

**Shaded JAR:**
```json
"jdeploy": {
  "jar": "target/myapp-1.0-jar-with-dependencies.jar",
  "javaVersion": "11", 
  "title": "My Application"
}
```

**JavaFX Application:**
```json
"jdeploy": {
  "jar": "target/myapp-1.0.jar",
  "javaVersion": "11",
  "javafx": true,
  "title": "My JavaFX App"
}
```

**Compose Multiplatform Desktop Application:**
```json
"jdeploy": {
  "jar": "compose-desktop/build/libs/compose-desktop-1.0-SNAPSHOT-all.jar",
  "javaVersion": "21",
  "javafx": false,
  "title": "My Compose App"
}
```

**With Automatic Build (if requested by user):**
```json
"jdeploy": {
  "jar": "compose-desktop/build/libs/compose-desktop-1.0-SNAPSHOT-all.jar",
  "javaVersion": "21",
  "javafx": false,
  "title": "My Compose App",
  "buildCommand": [
    "./gradlew",
    ":compose-desktop:buildExecutableJar"
  ]
}
```

### Required Fields:
- `name`: Unique NPM package name
- `bin`: Must include `"jdeploy-bundle/jdeploy.js"`
- `dependencies`: Must include `"shelljs": "^0.8.4"`
- `jdeploy.jar`: Path to executable JAR
- `jdeploy.javaVersion`: Java version required
- `jdeploy.title`: Human-readable name

### Optional Fields:
- `jdeploy.jdk`: Set to true if full JDK required (default: false)
- `jdeploy.javafx`: Set to true for JavaFX apps (default: false)
- `jdeploy.args`: Array of JVM arguments
- `jdeploy.buildCommand`: Array of command arguments to build the project automatically before publishing. Only add this if the user explicitly requests automatic builds on publish
- `jdeploy.platformBundlesEnabled`: Set to false by default. Only set to true when the JAR contains large native libraries (>50MB total) for multiple platforms (like Compose Multiplatform, LWJGL, SQLite). This creates platform-specific installers to reduce download sizes

## 5. Find and Configure Application Icon

jDeploy uses an `icon.png` file in the project root (same directory as `package.json`) for the application icon.

### Search for Existing Icons

Look for icon files in common locations:
- `src/main/resources/` (Maven)
- `src/main/resources/icons/`
- `src/resources/`
- `resources/`
- `assets/`
- `images/`
- Project root directory

### Icon Requirements:
- **Format**: PNG format
- **Dimensions**: Must be square (256x256, 512x512, or other square sizes)
- **Filename**: Must be named `icon.png` in project root

### Steps to Configure Icon:

1. **Search for candidate icons:**
   ```bash
   find . -name "*.png" -o -name "*.ico" -o -name "*.icns" | grep -i icon
   find . -name "*.png" | head -10  # Check first 10 PNG files
   ```

2. **Check image dimensions:**
   ```bash
   file candidate-icon.png  # Shows dimensions
   # Look for square dimensions like 256x256, 512x512, etc.
   ```

3. **Copy appropriate icon to project root:**
   ```bash
   cp src/main/resources/app-icon.png icon.png
   ```

4. **If no square icon exists:**
   - Don't worry about it.  jDeploy can proceed without an icon, but the app will use a default icon.

### Common Icon Locations by Framework:

**JavaFX Projects:**
- Often in `src/main/resources/` or `src/main/resources/images/`

**Spring Boot Projects:**
- May be in `src/main/resources/static/images/` or `src/main/resources/`

**Compose Multiplatform Projects:**
- Android app launcher icons: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- Check for highest resolution (xxxhdpi) Android icons as they are typically square and high quality

**General Java Projects:**
- Check `src/main/resources/icons/` or similar

### Validation:
After copying, verify the icon:
- File exists: `ls -la icon.png`
- Is square: `file icon.png` (check dimensions)
- Reasonable size: Should be at least 64x64, preferably 256x256 or larger

## 6. Optional: GitHub Workflows for App Bundles

Create `.github/workflows/jdeploy.yml`:

```yaml
# This workflow will build a Java project with Maven and bundle them as native app installers with jDeploy
# See https://www.jdeploy.com for more information.

name: jDeploy CI

on:
   push:
      branches: ['*', '!gh-pages']
      tags: ['*']

jobs:
   build:
      permissions:
         contents: write
      runs-on: ubuntu-latest

      steps:
         - uses: actions/checkout@v3
         - name: Set up JDK
           uses: actions/setup-java@v3
           with:
              java-version: '21'  # Match this to your project's Java version
              distribution: 'temurin'
         - name: Make gradlew executable
           run: chmod +x ./gradlew
         - name: Build with Gradle
           uses: gradle/gradle-build-action@67421db6bd0bf253fb4bd25b31ebb98943c375e1
           with:
              arguments: buildExecutableJar
         - name: Build App Installer Bundles
           uses: shannah/jdeploy@master
           with:
              github_token: ${{ secrets.GITHUB_TOKEN }}
         - name: Upload Build Artifacts for DMG Action
           if: ${{ vars.JDEPLOY_CREATE_DMG == 'true' }}  # Only needed if creating DMG
           uses: actions/upload-artifact@v4
           with:
              name: build-target
              path: ./build

   create_and_upload_dmg:
      # Enable DMG creation by setting JDEPLOY_CREATE_DMG variable on the repo.
      # See https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/store-information-in-variables#creating-configuration-variables-for-an-environment
      if: ${{ vars.JDEPLOY_CREATE_DMG == 'true' }}
      name: Create and upload DMG
      permissions:
         contents: write
      runs-on: macos-latest
      needs: build
      steps:
         - name: Set up Git
           run: |
              git config --global user.email "${{ github.actor }}@users.noreply.github.com"
              git config --global user.name "${{ github.actor }}"
         - uses: actions/checkout@v3
         - name: Download Build Artifacts
           uses: actions/download-artifact@v4
           with:
              name: build-target
              path: ./build
         - name: Create DMG and Upload to Release
           uses: shannah/jdeploy-action-dmg@main
           with:
              github_token: ${{ secrets.GITHUB_TOKEN }}
              developer_id: ${{ secrets.MAC_DEVELOPER_ID }}
              # Team ID and cert name only needed if it can't extract from the certifcate for some reason
              # developer_team_id: ${{ secrets.MAC_DEVELOPER_TEAM_ID }}
              # developer_certificate_name: ${{ secrets.MAC_DEVELOPER_CERTIFICATE_NAME }}
              developer_certificate_p12_base64: ${{ secrets.MAC_DEVELOPER_CERTIFICATE_P12_BASE64 }}
              developer_certificate_password: ${{ secrets.MAC_DEVELOPER_CERTIFICATE_PASSWORD }}
              notarization_password: ${{ secrets.MAC_NOTARIZATION_PASSWORD }}

```

## 7. Build and Validation Steps

1. **Verify Java version compatibility:**
   ```bash
   java -version
   ./gradlew -version  # For Gradle projects
   mvn -version        # For Maven projects
   ```
   **IMPORTANT**: If the project uses an older version of Java or Gradle than your system version, DO NOT upgrade the project's Java or Gradle version. Instead:
   - Use the project's existing Java version in `jdeploy.javaVersion` in package.json
   - Use the project's existing Java version in the GitHub workflow
   - Let the project's gradle wrapper (`./gradlew`) or Maven wrapper (`./mvnw`) handle the build tool version

2. **Build the Java project:**
   - Maven: `mvn clean package`
   - Gradle: `./gradlew build`

3. **Verify JAR is executable:**
   ```bash
   java -jar target/your-app.jar
   ```

4. **Validate package.json paths match actual build output**

5. **Verify icon setup:**
   - Check that `icon.png` exists in project root: `ls -la icon.png`
   - Verify it's square: `file icon.png`

## Common Project Patterns

### Maven Projects:
- Standard JAR: `target/myapp-1.0.jar` + `target/lib/`
- Shaded JAR: `target/myapp-1.0-jar-with-dependencies.jar`

### Gradle Projects:
- Standard JAR: `build/libs/myapp-1.0.jar` + `build/libs/lib/`
- Shadow JAR: `build/libs/myapp-1.0-all.jar`

## Troubleshooting

1. **JAR not found**: Verify `jdeploy.jar` path matches build output
2. **Main class not found**: Ensure JAR manifest includes Main-Class
3. **Missing dependencies**: For non-shaded JARs, ensure lib/ directory is created
4. **JavaFX issues**: Set `"javafx": true` and verify JavaFX modules are included

---

# Compose Multiplatform Desktop Applications

For Compose Multiplatform projects, follow these specific setup instructions:

## Prerequisites for Compose Multiplatform

1. **Identify Compose Desktop Module**: Look for a module named `compose-desktop`, `desktop`, or similar
2. **Check Build Structure**: Ensure it has `src/main/kotlin/main.kt` or similar main function
3. **Verify Dependencies**: Check that it uses `compose.desktop.*` dependencies

## Configure Cross-Platform Shadow JAR Build

**IMPORTANT**: For cross-platform compatibility (Windows, macOS, Linux on both x86_64 and ARM64), you must include ALL platform dependencies, not just `compose.desktop.currentOs`.

### Step 1: Add Shadow Plugin

Add the shadow plugin to your compose-desktop module's `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.shadowPlugin) // Add this line
    application
}
```

### Step 2: Configure Cross-Platform Dependencies

Replace `compose.desktop.currentOs` with explicit platform dependencies:

```kotlin
dependencies {
    // Include all desktop platforms for cross-platform compatibility
    implementation(compose.desktop.linux_x64)
    implementation(compose.desktop.linux_arm64)
    implementation(compose.desktop.macos_x64)
    implementation(compose.desktop.macos_arm64)
    implementation(compose.desktop.windows_x64)
    // Note: Windows ARM64 not yet supported in Compose Multiplatform 1.8.2
    // implementation(compose.desktop.windows_arm64)
    
    // Your other dependencies...
    implementation(projects.common)
}

application {
    mainClass.set("MainKt") // Adjust based on your main class
}
```

### Step 3: Create Build Task

Add a custom build task for jDeploy:

```kotlin
tasks.register("buildExecutableJar") {
    dependsOn("shadowJar")
    doLast {
        println("Built executable JAR: compose-desktop/build/libs/compose-desktop-1.0-SNAPSHOT-all.jar")
    }
}
```

## Package.json Configuration for Compose

Create or update `package.json` in the project root:

```json
{
  "bin": {"myapp": "jdeploy-bundle/jdeploy.js"},
  "author": "Your Name",
  "description": "My Compose Multiplatform Desktop App",
  "main": "index.js",
  "preferGlobal": true,
  "repository": "",
  "version": "1.0.0",
  "jdeploy": {
    "jdk": false,
    "javaVersion": "21",
    "javafx": false,
    "title": "My Compose App",
    "jar": "compose-desktop/build/libs/compose-desktop-1.0-SNAPSHOT-all.jar"
  },
  "dependencies": {
    "command-exists-promise": "^2.0.2",
    "node-fetch": "2.6.7",
    "tar": "^4.4.8",
    "yauzl": "^2.10.0",
    "shelljs": "^0.8.4"
  },
  "license": "ISC",
  "name": "myapp",
  "files": ["jdeploy-bundle"],
  "scripts": {"test": "echo \"Error: no test specified\" && exit 1"}
}
```

## Icon Configuration for Compose Projects

For Compose Multiplatform projects, check these locations for icons:

1. **Android launcher icons** (often the best quality):
   ```bash
   find . -path "*/app/src/main/res/mipmap-*/*.png"
   ```

2. **Check highest resolution Android icon**:
   ```bash
   file ./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
   # Should show dimensions like "192 x 192" (square)
   ```

3. **Copy to project root**:
   ```bash
   cp ./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png ./icon.png
   ```

## Build and Test

1. **Build the cross-platform JAR**:
   ```bash
   ./gradlew :compose-desktop:buildExecutableJar
   ```

2. **Verify JAR size and contents**:
   ```bash
   ls -lh compose-desktop/build/libs/
   # Should be significantly larger (~90MB+) due to all platform native libraries
   
   jar -tf compose-desktop/build/libs/compose-desktop-1.0-SNAPSHOT-all.jar | grep -E "\.(so|dll|dylib)$"
   # Should show native libraries for all platforms
   ```

3. **Test execution**:
   ```bash
   java -jar compose-desktop/build/libs/compose-desktop-1.0-SNAPSHOT-all.jar
   ```

## Platform Support Status

### ✅ Currently Supported:
- Linux x86_64
- Linux ARM64
- macOS x86_64 (Intel)
- macOS ARM64 (Apple Silicon)
- Windows x86_64

### ❌ Not Yet Supported:
- Windows ARM64 (planned for future Compose Multiplatform releases)

## GitHub Workflow Updates

For Compose projects, update your `.github/workflows/jdeploy.yml` build arguments:

```yaml
- name: Build with Gradle
  uses: gradle/gradle-build-action@67421db6bd0bf253fb4bd25b31ebb98943c375e1
  with:
     arguments: :compose-desktop:buildExecutableJar
```

## Common Issues and Solutions

### 1. JAR Only Works on Build Platform
**Problem**: JAR only runs on the platform where it was built
**Solution**: Ensure you're using explicit platform dependencies, not `compose.desktop.currentOs`

### 2. Large JAR Size
**Expected**: Cross-platform JARs will be ~90MB+ due to native libraries for all platforms
**This is normal** and required for cross-platform compatibility

### 3. Module Not Found Errors
**Check**: Ensure the compose-desktop module name matches your project structure
**Solution**: Adjust gradle task path (e.g., `:desktop:buildExecutableJar` vs `:compose-desktop:buildExecutableJar`)

### 4. Main Class Not Found
**Check**: Verify `main.kt` has a proper main function and `application.mainClass` is set correctly
**Solution**: Ensure main function is at top level: `fun main() { ... }`

---

# Platform-Specific Builds for Large Native Libraries

For applications with large native libraries (like Compose Multiplatform, LWJGL, or SQLite native), you can configure platform-specific builds to reduce bundle sizes. This creates separate installers for each platform instead of one large cross-platform bundle.

## When to Use Platform-Specific Builds

**IMPORTANT**: Platform-specific builds should ONLY be used when there is a significant size benefit.

DO NOT use platform-specific builds (`platformBundlesEnabled: true`) unless:
- Your JAR contains native libraries for multiple platforms AND
- The cross-platform JAR is very large (>50MB) AND
- Platform-specific builds would reduce the size by at least 50%

For most Java applications, including standard JavaFX apps or simple Compose apps, keep `platformBundlesEnabled: false` (or omit it entirely as false is the default).

## Configuration

### Step 1: Enable Platform Bundles

Add `platformBundlesEnabled: true` to your `package.json`:

```json
{
  "jdeploy": {
    "jar": "compose-desktop/build/libs/compose-desktop-1.0-SNAPSHOT-all.jar",
    "javaVersion": "21",
    "javafx": false,
    "title": "My Compose App",
    "platformBundlesEnabled": true
  }
}
```

### Step 2: Create Platform-Specific .jdpignore Files

Create `.jdpignore.<platform>` files in your project root to exclude unnecessary native libraries for each platform:

**`.jdpignore.linux-x64`**
```
# Common recommended patterns for Linux x64
# Generated on Sat Sep 20 06:21:14 PDT 2025
# Keep Linux x64 native libraries

# Skiko (Compose Multiplatform) native libraries
!/libskiko-linux-x64.so
/skiko-windows-*.dll
/libskiko-macos-*.dylib
/libskiko-linux-*.so
/skiko-*.dll
/libskiko-*.dylib
/libskiko-*.so

# SQLite native libraries
!/org/sqlite/native/Linux/x86_64
/org/sqlite/native

# LWJGL native libraries
/macos
/windows
/linux
!/linux/x64
**gdx*.dll
**libgdx*.so
/libgdx*.dylib
!/libgdx64.so
```

**`.jdpignore.linux-arm64`**
```
# Common recommended patterns for Linux ARM
# Generated on Sat Sep 20 06:21:18 PDT 2025
# Keep Linux ARM64 native libraries

# Skiko (Compose Multiplatform) native libraries
!/libskiko-linux-arm64.so
/skiko-windows-*.dll
/libskiko-macos-*.dylib
/libskiko-linux-*.so
/skiko-*.dll
/libskiko-*.dylib
/libskiko-*.so

# SQLite native libraries
!/org/sqlite/native/Linux/aarch64
/org/sqlite/native

# LWJGL native libraries
/macos
/windows
/linux
!/linux/arm64
**gdx*.dll
**libgdx*.so
/libgdx*.dylib
!/libgdxarm64.so
```

**`.jdpignore.mac-x64`**
```
# Common recommended patterns for macOS Intel
# Generated on Sat Sep 20 06:21:02 PDT 2025
# Keep macOS Intel native libraries

# Skiko (Compose Multiplatform) native libraries
!/libskiko-macos-x64.dylib
/skiko-windows-*.dll
/libskiko-macos-*.dylib
/libskiko-linux-*.so
/skiko-*.dll
/libskiko-*.dylib
/libskiko-*.so

# SQLite native libraries
!/org/sqlite/native/Mac/x86_64
/org/sqlite/native

# LWJGL native libraries
/linux
/windows
/macos/arm64
**gdx*.dll
**libgdx*.so
/libgdxarm64.dylib
```

**`.jdpignore.mac-arm64`**
```
# Common recommended patterns for macOS Silicon
# Generated on Sat Sep 20 06:21:07 PDT 2025
# Keep macOS Silicon native libraries

# Skiko (Compose Multiplatform) native libraries
!/libskiko-macos-arm64.dylib
/skiko-windows-*.dll
/libskiko-macos-*.dylib
/libskiko-linux-*.so
/skiko-*.dll
/libskiko-*.dylib
/libskiko-*.so

# SQLite native libraries
!/org/sqlite/native/Mac/aarch64
/org/sqlite/native

# LWJGL native libraries
/linux
/windows
/macos/x64
**gdx*.dll
**libgdx*.so
/libgdx64.dylib
```

**`.jdpignore.win-x64`**
```
# Common recommended patterns for Windows x64
# Generated on Sat Sep 20 06:21:11 PDT 2025
# Keep Windows x64 native libraries

# Skiko (Compose Multiplatform) native libraries
!/skiko-windows-x64.dll
/skiko-windows-*.dll
/libskiko-macos-*.dylib
/libskiko-linux-*.so
/skiko-*.dll
/libskiko-*.dylib
/libskiko-*.so

# SQLite native libraries
!/org/sqlite/native/Windows/x86_64
/org/sqlite/native

# LWJGL native libraries
/linux
/macos
/windows/arm64
/windows/x86
/gdx.dll
**libgdx*.so
**libgdx*.dylib
```

### Step 3: Understanding .jdpignore Patterns

The .jdpignore files use gitignore-style patterns:
- `/path`: Exclude this specific path
- `!/path`: Include this path (override exclusion)
- `**pattern`: Recursive wildcard matching
- `*.extension`: File extension matching

**Pattern Logic**:
1. Start with broad exclusions (e.g., `/org/sqlite/native` excludes all SQLite natives)
2. Add specific inclusions for the target platform (e.g., `!/org/sqlite/native/Linux/x86_64`)
3. This results in only the platform-specific libraries being included

## Benefits

### Size Reduction:
- **Before**: Single 90MB+ cross-platform JAR
- **After**: Platform-specific JARs of ~30MB each

### Platforms Supported:
- `linux-x64`: Linux on x86_64
- `linux-arm64`: Linux on ARM64 
- `mac-x64`: macOS on Intel
- `mac-arm64`: macOS on Apple Silicon
- `win-x64`: Windows on x86_64

## Build Process

When `platformBundlesEnabled: true` is set:

1. jDeploy builds the full cross-platform JAR first
2. For each supported platform, it creates a filtered JAR using the corresponding `.jdpignore` file
3. Separate installers are generated for each platform
4. Users download only the installer for their specific platform

## Validation

After setting up platform-specific builds:

1. **Check .jdpignore files exist**:
   ```bash
   ls -la .jdpignore.*
   ```

2. **Verify patterns are working** (during build):
   - Check that platform-specific JARs are smaller than the original
   - Verify each platform JAR contains only its native libraries

3. **Test platform-specific JARs**:
   ```bash
   # Extract and check contents
   jar -tf filtered-jar-for-platform.jar | grep -E "\.(so|dll|dylib)$"
   ```

## Common Libraries Supported

The provided .jdpignore patterns handle:
- **Skiko**: Compose Multiplatform's native rendering
- **SQLite**: Native database libraries
- **LWJGL**: Lightweight Java Game Library
- **libGDX**: Game development framework

## Customization

To add support for other native libraries:

1. Identify the library's native file patterns in your JAR
2. Add exclusion rules for all platforms: `/library/path`
3. Add inclusion rules for target platform: `!/library/path/platform-specific`

Example for a custom library:
```
# MyCustomLib native libraries
!/mycustomlib/native/linux-x64
/mycustomlib/native
```