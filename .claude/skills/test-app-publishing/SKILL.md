# Test App Publishing

Test the full jDeploy publishing and installation flow using the scratch repository https://github.com/shannah/jdeploy-test-app.

## Overview

This skill creates a sample Java application in the `jdeploy-test-app` scratch repository, configures it for jDeploy, publishes it (to npm or GitHub), and provides the download URL so the user can manually test the installation experience.

The `jdeploy-test-app` repo is a disposable scratchpad. It is safe to delete all existing content and start fresh each time.

## Instructions

### Step 1: Gather Requirements from User

Ask the user these questions before proceeding:

**Question 1: Special requirements**

Ask: "Do you have any special requirements for the test app? For example: specific package.json properties, JavaFX, commands/services, MCP server config, platform bundles, specific JVM args, etc. Or should I create a basic Swing GUI app?"

Let the user describe any features they want to test. This might include:
- Specific `jdeploy` properties in package.json (e.g., `platformBundlesEnabled`, `javafx`, custom `commands`)
- A specific app type (CLI, service, MCP server, GUI)
- Specific Java version requirements
- Custom build configuration
- Any new/experimental jDeploy features they want to validate

If the user has no special requirements, default to a simple Swing GUI "Hello World" app.

**Question 2: Publish target**

Ask: "Where should I publish: npm or GitHub?"

- **npm**: Publishes to the npm registry as the package `jdeploy-test-app`. Users install with `npx jdeploy-test-app` or via the jDeploy download page.
- **GitHub**: Creates a GitHub Release on `shannah/jdeploy-test-app`, triggering the jDeploy GitHub Action to build native installers.

### Step 2: Clone and Reset the Test Repository

```bash
# Clone the test repo into a temporary working directory
WORK_DIR=$(mktemp -d)
cd "$WORK_DIR"
git clone https://github.com/shannah/jdeploy-test-app.git
cd jdeploy-test-app
```

Delete all existing content to start fresh:

```bash
# Remove everything except .git
find . -maxdepth 1 ! -name '.git' ! -name '.' -exec rm -rf {} +
```

### Step 3: Determine the Next Version Number

Before creating the app, determine what version to use by checking what's already published.

**For npm:**
```bash
# Check the latest published version on npm
npm view jdeploy-test-app version 2>/dev/null || echo "0.0.0"
```

**For GitHub:**
```bash
# Check latest GitHub release tag
gh release list --repo shannah/jdeploy-test-app --limit 1 2>/dev/null
# Also check tags
git tag -l 'v*' --sort=-v:refname | head -1
```

Take the latest version found and bump the **patch** number by 1. For example:
- If latest is `1.0.5`, use `1.0.6`
- If latest is `0.0.0` or nothing is published, use `1.0.0`
- If latest is `2.3.10`, use `2.3.11`

Store this as `$NEXT_VERSION` for use in subsequent steps.

### Step 4: Create the Sample Java Application

Use the jdeploy-claude plugin skills as a reference for how to structure projects. Follow the CLAUDE.md setup guide from https://github.com/shannah/jdeploy-claude for the full package.json schema and build configuration.

#### Default: Simple Swing GUI App (Maven)

If no special requirements were given, create a minimal Swing app:

**Directory structure:**
```
jdeploy-test-app/
  pom.xml
  package.json
  icon.png          (optional - skip if not readily available)
  src/main/java/com/example/testapp/
    Main.java
```

**pom.xml** - Standard Maven project with:
- `groupId`: `com.example`
- `artifactId`: `jdeploy-test-app`
- `version`: `$NEXT_VERSION`
- Java 8 source/target compatibility
- `maven-jar-plugin` configured with `Main-Class` manifest entry
- `maven-dependency-plugin` to copy dependencies to `target/lib/`

**Main.java** - Simple Swing window:
```java
package com.example.testapp;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("jDeploy Test App v" + getVersion());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 300);

            JLabel label = new JLabel("Hello from jDeploy Test App!", SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.BOLD, 18));
            frame.add(label);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static String getVersion() {
        // Read from package if available, otherwise hardcoded
        return "$NEXT_VERSION";
    }
}
```

#### Custom App

If the user specified special requirements, adapt the project accordingly:
- For **CLI apps**: Use picocli or simple args parsing, add `jdeploy.commands` to package.json, add GUI fallback
- For **JavaFX apps**: Add JavaFX dependencies, set `"javafx": true` in package.json
- For **Service apps**: Add Spring Boot or similar, configure `service_controller` command
- For **MCP servers**: Add MCP dependencies, configure `jdeploy.ai.mcp` in package.json
- For **any special package.json properties**: Add them exactly as requested by the user

### Step 5: Create package.json

Create `package.json` with the appropriate jDeploy configuration:

```json
{
  "name": "jdeploy-test-app",
  "version": "$NEXT_VERSION",
  "description": "Test application for jDeploy publishing",
  "bin": {
    "jdeploy-test-app": "jdeploy-bundle/jdeploy.js"
  },
  "preferGlobal": true,
  "jdeploy": {
    "jdk": false,
    "javaVersion": "11",
    "javafx": false,
    "jar": "target/jdeploy-test-app-$NEXT_VERSION.jar",
    "title": "jDeploy Test App"
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

Modify this template based on user requirements (add commands, javafx, platformBundlesEnabled, ai.mcp, etc.).

### Step 6: Build the Project

```bash
mvn clean package -DskipTests
```

Verify the JAR was created:
```bash
ls -la target/jdeploy-test-app-*.jar
```

If the build fails, diagnose and fix the issue before proceeding.

### Step 7: Set Up GitHub Workflow (if publishing to GitHub)

If the publish target is GitHub, create `.github/workflows/jdeploy.yml` following the template from the jdeploy-claude CLAUDE.md "github-workflows" section. Make sure to adjust the Java version and build tool to match the project.

### Step 8: Commit and Push

```bash
git add -A
git commit -m "Set up test app v$NEXT_VERSION"
git push origin main
```

### Step 9: Publish

#### npm Publishing

```bash
npm publish
```

If this fails with an authentication error (ENEEDAUTH, 403, etc.):
1. Tell the user: "npm publish failed due to authentication. Please run `npm login` in your terminal to authenticate, then tell me to retry."
2. Wait for the user to confirm they've logged in.
3. Retry `npm publish`.

For scoped packages, use `npm publish --access public`.

#### GitHub Publishing

```bash
# Create a release (this triggers the jDeploy GitHub Action)
gh release create "v$NEXT_VERSION" \
  --repo shannah/jdeploy-test-app \
  --title "v$NEXT_VERSION" \
  --notes "Test release v$NEXT_VERSION"
```

If this fails due to authentication, instruct the user to run `gh auth login`.

### Step 10: Report Results

After successful publishing, provide the user with the relevant URLs:

**For npm:**
- npm package page: `https://www.npmjs.com/package/jdeploy-test-app`
- jDeploy download page: `https://www.jdeploy.com/~jdeploy-test-app`
- Install command: `npm install -g jdeploy-test-app` or `npx jdeploy-test-app`

**For GitHub:**
- Release page: `https://github.com/shannah/jdeploy-test-app/releases/tag/v$NEXT_VERSION`
- Latest release: `https://github.com/shannah/jdeploy-test-app/releases/latest`
- GitHub Actions progress: `https://github.com/shannah/jdeploy-test-app/actions`
- Note: "Native installers will be built by the GitHub Action. This typically takes 5-10 minutes. Check the Actions tab for progress."

Tell the user: "The app has been published. You can now test the installation manually using the links above."

## Troubleshooting

### npm Issues

| Problem | Solution |
|---------|----------|
| `ENEEDAUTH` or `403` | User needs to run `npm login` |
| Package name taken | This shouldn't happen since we use `jdeploy-test-app` which is owned by the test account |
| Version already exists | Bump the version higher and retry |

### GitHub Issues

| Problem | Solution |
|---------|----------|
| `gh: not authenticated` | User needs to run `gh auth login` |
| Release already exists for tag | Delete the existing release: `gh release delete v$VERSION --repo shannah/jdeploy-test-app -y` and retry |
| GitHub Action fails | Check the Actions tab for logs. Common issues: Java version mismatch, build failure |

### Build Issues

| Problem | Solution |
|---------|----------|
| Maven not found | Install Maven or use `./mvnw` wrapper |
| Java version mismatch | Adjust `pom.xml` source/target and `jdeploy.javaVersion` in package.json |
| JAR path wrong in package.json | Check `target/` for the actual JAR filename and update package.json |

## Important Notes

- The `jdeploy-test-app` repository is a scratchpad. It is safe to completely wipe and recreate its contents.
- Always check for the latest published version before choosing a new version number to avoid conflicts.
- The npm package name `jdeploy-test-app` is fixed. Do not change it.
- The GitHub repository is `shannah/jdeploy-test-app`. Do not change it.
- All work happens in the cloned test repo, NOT in the main jdeploy repository.
