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

## 3. Configure package.json

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
      "title": "{{ appTitle }}",
      "buildCommand": [
         "./gradlew",
         "buildExecutableJar"
      ]
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

## 4. Optional: GitHub Workflows for App Bundles

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
         - name: Set up JDK 21
           uses: actions/setup-java@v3
           with:
              java-version: '21'
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

## 5. Build and Validation Steps

1. **Build the Java project:**
   - Maven: `mvn clean package`
   - Gradle: `./gradlew build`

2. **Verify JAR is executable:**
   ```bash
   java -jar target/your-app.jar
   ```

3. **Validate package.json paths match actual build output**

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