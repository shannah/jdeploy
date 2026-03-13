# RFC: Pre-Built App Bundle Publishing Specification

## Status

Implemented (PR #436)

## Summary

jDeploy supports building and publishing pre-built native application bundles (.exe, .app, Linux binary) at publish time. These bundles are uploaded as downloadable artifacts (to GitHub Releases or S3) and their download URLs and SHA-256 checksums are recorded in the published `package.json`. This RFC defines the canonical `package.json` schema that clients and external tools should use to discover and verify pre-built bundles.

## Motivation

- **Faster installs**: users download a ready-to-run app instead of waiting for the installer to bundle it at install time
- **Direct download links**: pre-built bundles can be linked from websites, READMEs, and download pages
- **CDN distribution**: S3 support enables faster global downloads
- **Verifiable builds**: SHA-256 hashes in `package.json` let clients verify download integrity
- **Cross-project discovery**: other tools (installers, download page generators, CI pipelines) can read `package.json` to determine whether pre-built bundles are available and where to fetch them

## package.json Schema

### Configuration (Source package.json)

To enable bundle publishing, the author declares target platforms in `jdeploy.artifacts`. Each key is a platform key in the format `{platform}-{arch}`, and must have `"enabled": true` to opt in:

```json
{
  "name": "my-app",
  "version": "1.0.0",
  "jdeploy": {
    "jar": "target/myapp.jar",
    "artifacts": {
      "mac-arm64": { "enabled": true },
      "mac-x64": { "enabled": true },
      "win-x64": { "enabled": true },
      "win-arm64": { "enabled": true },
      "linux-x64": { "enabled": true },
      "linux-arm64": { "enabled": true }
    }
  }
}
```

When no `artifacts` entries have `"enabled": true`, bundle publishing is skipped entirely.

### Published Output (Published package.json)

After a successful publish, jDeploy merges `url` and `sha256` fields into each enabled artifact entry. The `"enabled"` field and any other user-defined fields are preserved. A `cli` sub-object is added when a CLI variant bundle was also built:

```json
{
  "name": "my-app",
  "version": "1.0.0",
  "jdeploy": {
    "jar": "target/myapp.jar",
    "artifacts": {
      "mac-arm64": {
        "enabled": true,
        "url": "https://github.com/user/repo/releases/download/v1.0.0/my-app-mac-arm64-1.0.0.jar",
        "sha256": "a1b2c3d4e5f6..."
      },
      "mac-x64": {
        "enabled": true,
        "url": "https://github.com/user/repo/releases/download/v1.0.0/my-app-mac-x64-1.0.0.jar",
        "sha256": "f6e5d4c3b2a1..."
      },
      "win-x64": {
        "enabled": true,
        "url": "https://s3.us-east-1.amazonaws.com/bucket/jdeploy-bundles/my-app-win-x64-1.0.0.jar",
        "sha256": "1a2b3c4d5e6f...",
        "cli": {
          "url": "https://s3.us-east-1.amazonaws.com/bucket/jdeploy-bundles/my-app-win-x64-1.0.0-cli.jar",
          "sha256": "6f5e4d3c2b1a..."
        }
      },
      "linux-x64": {
        "enabled": true,
        "url": "https://github.com/user/repo/releases/download/v1.0.0/my-app-linux-x64-1.0.0.jar",
        "sha256": "abcdef012345..."
      }
    }
  }
}
```

### Field Reference

#### Artifact Entry (`jdeploy.artifacts.{platform-key}`)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `enabled` | boolean | Yes (in source) | Must be `true` for the platform to be built at publish time. |
| `url` | string | Set at publish | Download URL for the GUI bundle JAR. |
| `sha256` | string | Set at publish | SHA-256 hex digest of the GUI bundle JAR. |
| `cli` | object | Set at publish (conditional) | Present only when a CLI variant was built for this platform. |
| `cli.url` | string | Set at publish | Download URL for the CLI bundle JAR. |
| `cli.sha256` | string | Set at publish | SHA-256 hex digest of the CLI bundle JAR. |

#### Valid Platform Keys

| Key | Platform | Architecture |
|-----|----------|--------------|
| `mac-x64` | macOS | Intel x86_64 |
| `mac-arm64` | macOS | Apple Silicon |
| `win-x64` | Windows | x86_64 |
| `win-arm64` | Windows | ARM64 |
| `linux-x64` | Linux | x86_64 |
| `linux-arm64` | Linux | ARM64 |

## Detecting Pre-Built Bundles

External tools should use the following algorithm to determine if a pre-built bundle is available for a given platform:

1. Read `jdeploy.artifacts` from the published `package.json`.
2. Look up the platform key matching the target (e.g. `mac-arm64`).
3. If the entry exists and has both `url` and `sha256` fields, a pre-built bundle is available.
4. If a `cli` sub-object exists with both `url` and `sha256`, a CLI variant is also available.
5. If `jdeploy.artifacts` is absent, or the platform key is missing, or `url`/`sha256` are not present, no pre-built bundle exists for that platform.

### Example Detection (pseudocode)

```
function hasBundleForPlatform(packageJson, platformKey):
    artifacts = packageJson.jdeploy?.artifacts
    if artifacts is null:
        return false
    entry = artifacts[platformKey]
    if entry is null:
        return false
    return entry.url is not null AND entry.sha256 is not null

function getBundleUrl(packageJson, platformKey):
    artifacts = packageJson.jdeploy?.artifacts
    return artifacts?[platformKey]?.url

function getCliBundleUrl(packageJson, platformKey):
    artifacts = packageJson.jdeploy?.artifacts
    return artifacts?[platformKey]?.cli?.url
```

## Bundle JAR Naming Convention

Bundle JARs follow this naming pattern:

```
{fqpn}-{platform}-{arch}-{version}[-cli].jar
```

Where:
- **fqpn** = fully qualified package name (e.g. `my-app` for NPM, `a1b2c3d4.my-app` for GitHub packages)
- **platform** = `mac`, `win`, `linux`
- **arch** = `x64`, `arm64`
- **version** = semver version from package.json (e.g. `1.0.0`)
- **-cli** suffix = present only for CLI variant bundles

Examples:
```
my-app-mac-arm64-1.0.0.jar
my-app-win-x64-1.0.0.jar
my-app-linux-x64-1.0.0-cli.jar
a1b2c3d4.my-app-mac-x64-2.1.0.jar
```

## Bundle JAR Contents

Each bundle JAR is a standard Java JAR file containing a single native application bundle. The internal structure varies by platform.

### macOS Bundle JAR

The JAR contains the full `.app` directory tree as recursive JAR entries, preserving the standard macOS application bundle structure:

```
AppName.app/
└── Contents/
    ├── MacOS/
    │   ├── Client4JLauncher          # Native launcher executable (x64 or ARM64)
    │   └── Client4JLauncher-cli      # CLI launcher (present when CLI commands are configured)
    ├── Resources/
    │   └── icon.icns                 # Application icon (ICNS format)
    ├── Library/
    │   └── LaunchAgents/             # (optional) macOS service plists
    │       └── commandName.plist     # One per service_controller command
    ├── Info.plist                     # macOS application configuration plist
    ├── PkgInfo                       # macOS package type identifier
    └── app.xml                       # jDeploy app manifest (name, package, version, icon data URI, etc.)
```

The macOS CLI binary (`Client4JLauncher-cli`) is included **inside** the `.app` bundle when CLI commands are configured, so no separate CLI JAR is produced for macOS.

### Windows Bundle JAR

The JAR contains a single `.exe` file entry:

```
AppName.exe                           # Native launcher executable (x64 or ARM64 PE binary)
```

The `.exe` is a self-contained native Windows PE executable. The jDeploy app manifest (`app.xml`) is embedded at the end of the binary using a byte-inversion encoding scheme with a 32-byte trailer for detection.

When CLI commands are configured, a separate CLI variant JAR is also produced:

```
AppName-cli.exe                       # CLI-mode launcher executable
```

CLI variant JARs are **only** built for Windows.

### Linux Bundle JAR

The JAR contains a single binary executable entry:

```
app-name                              # Native launcher executable (x64 or ARM64 ELF binary)
```

Like Windows, the Linux binary is a self-contained native ELF executable with the jDeploy app manifest (`app.xml`) embedded at the end of the binary using the same byte-inversion encoding scheme.

### Embedded App Manifest (app.xml)

All platform bundles include an `app.xml` manifest containing application metadata used by the native launcher at runtime:

- Application name and display title
- NPM package name and version
- NPM source (GitHub repository URL, if applicable)
- Application icon (embedded as a base64 data URI)
- Splash screen configuration (if configured)
- CLI command definitions (if configured)

For macOS, `app.xml` is a standalone file inside `Contents/`. For Windows and Linux, it is appended to the end of the native executable binary.

### What Bundles Do NOT Include

- **JVM runtime**: Bundles do not include an embedded JVM. The native launcher downloads an appropriate JVM on first run.
- **Application JARs**: The application's own JAR files are not included. The launcher fetches them from the NPM registry or GitHub at runtime.
- **JCEF/Chromium frameworks**: Not included in publish-time bundles by default.

## Upload Destinations

### GitHub Releases (default for GitHub publish target)

Bundle JARs are added to the version-specific GitHub release. URLs follow the pattern:

```
https://github.com/{owner}/{repo}/releases/download/{tag}/{filename}.jar
```

### S3 (optional)

When configured via environment variables, bundles upload to S3 instead. URLs follow:

```
https://{bucket}.s3.{region}.amazonaws.com/{prefix}/{filename}.jar
```

#### S3 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `JDEPLOY_S3_BUCKET` | Yes (to enable S3) | — | S3 bucket name |
| `JDEPLOY_S3_REGION` | No | `us-east-1` | AWS region |
| `JDEPLOY_S3_PREFIX` | No | `jdeploy-bundles` | Key prefix/path within bucket |
| `AWS_ACCESS_KEY_ID` | Yes (for S3) | — | AWS credentials |
| `AWS_SECRET_ACCESS_KEY` | Yes (for S3) | — | AWS credentials |

### Upload Routing Logic

| Publish Target | S3 Configured | Destination |
|----------------|---------------|-------------|
| GitHub | Yes | S3 |
| GitHub | No | GitHub Releases |
| NPM | Yes | S3 |
| NPM | No | Skipped (warning emitted) |

## Integrity Verification

All SHA-256 hashes are computed over the complete JAR file (not the inner bundle contents). Clients should:

1. Download the JAR from the `url`.
2. Compute the SHA-256 hex digest of the downloaded file.
3. Compare against the `sha256` value in `package.json`.
4. Reject the bundle if the hashes do not match.

## Important Notes

- Only the **published copy** of `package.json` is modified with `url` and `sha256` fields. The author's source `package.json` retains only the `"enabled": true` configuration.
- The `artifacts` section uses a merge strategy: built artifact data (`url`, `sha256`, `cli`) is merged into existing entries, preserving any user-defined fields.
- Bundles do **not** include an embedded JVM. The launcher downloads a JVM on first run, matching the existing installer behavior.
- macOS bundles are currently unsigned/un-notarized. Code signing may be added in a future phase.
