# Test Bundle Publishing

Test the jDeploy bundle publishing feature using the scratch repository https://github.com/shannah/jdeploy-test-app.

This skill tests the `jdeploy.artifacts` feature which builds and publishes pre-built native app bundles (.app, .exe, Linux binary) to GitHub Releases.

## Usage

```
/test-bundle-publishing <jdeploy-version>
```

Example:
```
/test-bundle-publishing 6.1.0-dev.11
```

## Instructions

### Step 1: Get the jDeploy Version

The jDeploy version should be provided as an argument. If not provided, ask:

"Which version of jDeploy do you want to test? (e.g., `6.1.0-dev.11`, `latest`)"

Store this as `$JDEPLOY_VERSION`.

### Step 2: Clone/Reset the Test Repository

```bash
WORK_DIR=$(mktemp -d)
cd "$WORK_DIR"
git clone https://github.com/shannah/jdeploy-test-app.git
cd jdeploy-test-app
```

### Step 3: Determine the Next Version

Check the latest GitHub release and bump the patch version:

```bash
gh release list --repo shannah/jdeploy-test-app --limit 1
```

If latest is `v1.0.7`, use `1.0.8`. Store as `$NEXT_VERSION`.

### Step 4: Update Project Files

Update these files with the new version:

**pom.xml** - Update `<version>$NEXT_VERSION</version>`

**package.json** - Update version and jar path:
```json
{
  "name": "jdeploy-test-app",
  "version": "$NEXT_VERSION",
  "description": "Test application for jDeploy bundle publishing",
  "bin": {
    "jdeploy-test-app": "jdeploy-bundle/jdeploy.js"
  },
  "preferGlobal": true,
  "jdeploy": {
    "jdk": false,
    "javaVersion": "11",
    "javafx": false,
    "jar": "target/jdeploy-test-app-$NEXT_VERSION.jar",
    "title": "jDeploy Test App",
    "artifacts": {
      "mac-arm64": { "enabled": true },
      "mac-x64": { "enabled": true },
      "win-x64": { "enabled": true },
      "win-arm64": { "enabled": true },
      "linux-x64": { "enabled": true },
      "linux-arm64": { "enabled": true }
    }
  },
  "dependencies": {
    "command-exists-promise": "^2.0.2",
    "node-fetch": "2.6.7",
    "tar": "^4.4.8",
    "yauzl": "^2.10.0",
    "shelljs": "^0.8.4"
  },
  "files": ["jdeploy-bundle"],
  "license": "ISC"
}
```

**src/main/java/com/example/testapp/Main.java** - Update version strings in the UI

**.github/workflows/jdeploy.yml**:
```yaml
name: jDeploy CI

on:
  release:
    types: [created]

permissions:
  contents: write

jobs:
  jdeploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Build and Deploy Installers
        uses: shannah/jdeploy@v$JDEPLOY_VERSION
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          jdeploy_version: $JDEPLOY_VERSION
```

### Step 5: Build, Commit, Push, and Release

```bash
# Build
mvn clean package -DskipTests -q

# Commit and push
git add -A
git commit -m "Set up test app v$NEXT_VERSION with jDeploy $JDEPLOY_VERSION bundle publishing"
git push origin main

# Create release
gh release create "v$NEXT_VERSION" \
  --repo shannah/jdeploy-test-app \
  --title "v$NEXT_VERSION" \
  --notes "Test release v$NEXT_VERSION - Bundle publishing test with jDeploy $JDEPLOY_VERSION"
```

### Step 6: Report Results

Output a concise summary:

```
Done. Test app v$NEXT_VERSION published with jDeploy $JDEPLOY_VERSION.

**URLs:**
- **Release**: https://github.com/shannah/jdeploy-test-app/releases/tag/v$NEXT_VERSION
- **Actions**: https://github.com/shannah/jdeploy-test-app/actions
```

## What This Tests

The bundle publishing feature (`rfc/bundle-publishing-spec.md`):

1. **Bundle building** - jDeploy should build native app bundles for each enabled platform in `jdeploy.artifacts`
2. **Bundle uploading** - Bundle JARs should be uploaded to the GitHub Release
3. **Package.json update** - The published `package.json` should have `url` and `sha256` fields added to each artifact entry

## Expected Success Criteria

After the GitHub Action completes (~5-10 minutes):

1. The release page should have bundle JAR files attached (e.g., `jdeploy-test-app-mac-arm64-$VERSION.jar`)
2. The published `package.json` should contain:
   ```json
   "artifacts": {
     "mac-arm64": {
       "enabled": true,
       "url": "https://github.com/shannah/jdeploy-test-app/releases/download/v.../...",
       "sha256": "..."
     },
     ...
   }
   ```

## Troubleshooting

If bundles are not being built/uploaded:
- Check the GitHub Actions log for errors
- Verify the jDeploy version supports bundle publishing
- Check that `jdeploy.artifacts` entries have `"enabled": true`