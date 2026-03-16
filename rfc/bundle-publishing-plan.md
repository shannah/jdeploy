# RFC: Pre-Built Bundle Publishing

## Summary

Currently, jDeploy builds native app bundles (.exe, .app, Linux binary) at **install time** via the installer. This plan adds support for building these bundles at **publish time** and uploading them as downloadable artifacts, so users can download pre-built native apps directly.

## Motivation

- Faster installs: users download a ready-to-run app instead of waiting for the installer to bundle it
- Direct download links: pre-built bundles can be linked from websites, READMEs, etc.
- CDN distribution: S3 support enables faster global downloads
- Verifiable builds: SHA-256 hashes in package.json let clients verify integrity

## Goals

1. Build platform-specific bundles (.exe, .app, Linux binary) during publish
2. Wrap each bundle in a tar.gz archive for distribution (tar.gz preserves POSIX file permissions, unlike JAR)
3. Upload bundles to GitHub releases (default) or S3 (optional)
4. Record download URLs and SHA-256 hashes in package.json
5. Support both GUI and CLI bundles when CLI commands are configured
6. Use FQN + platform + arch + version naming convention

---

## Design

### Naming Convention

Bundle archives use `.tar.gz` format (not JAR) to preserve POSIX file permissions. They follow this pattern:

```
{fqpn}-{platform}-{arch}-{version}[-cli].tar.gz
```

Where:
- **fqpn** = fully qualified package name (e.g. `my-app` for NPM, `a1b2c3d4.my-app` for GitHub — computed via `CliCommandBinDirResolver.computeFullyQualifiedPackageName()`)
- **platform** = `mac`, `win`, `linux`
- **arch** = `x64`, `arm64`
- **version** = semver from package.json (e.g. `1.0.0`)
- **-cli** suffix = present only for CLI variant bundles

Examples:
```
my-app-mac-arm64-1.0.0.tar.gz
my-app-win-x64-1.0.0.tar.gz
my-app-linux-x64-1.0.0-cli.tar.gz
a1b2c3d4.my-app-mac-x64-2.1.0.tar.gz
```

### tar.gz Wrapping

Each native bundle is placed inside a tar.gz archive. The tar.gz format is used instead of JAR because JAR files do not preserve POSIX file permissions, which are required for executable binaries on macOS and Linux:

```
my-app-mac-arm64-1.0.0.tar.gz
  └── MyApp.app/          (the .app directory tree, preserved structure and permissions)

my-app-win-x64-1.0.0.tar.gz
  └── MyApp.exe

my-app-linux-x64-1.0.0.tar.gz
  └── my-app              (Linux binary, with execute permission preserved)
```

If CLI is enabled, a second archive is produced:
```
my-app-win-x64-1.0.0-cli.tar.gz
  └── MyApp-cli.exe
```

### package.json Schema

Bundles are recorded in `jdeploy.bundles` in the published package.json:

```json
{
  "jdeploy": {
    "bundles": {
      "mac-arm64": {
        "url": "https://github.com/user/repo/releases/download/v1.0.0/my-app-mac-arm64-1.0.0.tar.gz",
        "sha256": "abc123...",
        "cli": {
          "url": "https://github.com/user/repo/releases/download/v1.0.0/my-app-mac-arm64-1.0.0-cli.tar.gz",
          "sha256": "def456..."
        }
      },
      "win-x64": {
        "url": "https://s3.amazonaws.com/bucket/my-app-win-x64-1.0.0.tar.gz",
        "sha256": "789abc..."
      },
      "linux-x64": {
        "url": "https://github.com/user/repo/releases/download/v1.0.0/my-app-linux-x64-1.0.0.tar.gz",
        "sha256": "bcd012..."
      }
    }
  }
}
```

Each key is `{platform}-{arch}`. Values contain `url`, `sha256`, and optional `cli` sub-object.

### Upload Destinations

#### GitHub Releases (default for GitHub publish target)
- Bundle JARs are added to the version-specific release (e.g. `v1.0.0`)
- Upload uses existing `GitHubReleaseCreator.uploadArtifacts()` infrastructure
- URLs follow pattern: `https://github.com/{owner}/{repo}/releases/download/{tag}/{filename}.tar.gz`

#### S3 (optional, for both GitHub and NPM targets)
- Configured via environment variables (see below)
- When configured, bundles upload to S3 instead of GitHub releases
- For NPM-only targets, S3 is the only option (NPM registry doesn't host arbitrary assets)
- URLs follow pattern: `https://{bucket}.s3.{region}.amazonaws.com/{prefix}/{filename}.tar.gz`

### S3 Configuration

Environment variables:
- `JDEPLOY_S3_BUCKET` — S3 bucket name (required to enable S3)
- `JDEPLOY_S3_REGION` — AWS region (default: `us-east-1`)
- `JDEPLOY_S3_PREFIX` — Key prefix/path within bucket (default: `jdeploy-bundles`)
- `AWS_ACCESS_KEY_ID` — AWS credentials (standard)
- `AWS_SECRET_ACCESS_KEY` — AWS credentials (standard)

When `JDEPLOY_S3_BUCKET` is set:
- GitHub target: uploads to S3 instead of GitHub releases
- NPM target: uploads to S3

When `JDEPLOY_S3_BUCKET` is NOT set:
- GitHub target: uploads to GitHub releases (fallback)
- NPM target: bundle publishing is skipped (no destination available)

### Enabling Bundle Publishing

A new boolean flag in package.json:

```json
{
  "jdeploy": {
    "publishBundles": true
  }
}
```

When `false` or absent, publish-time bundling is skipped entirely (current behavior preserved).

---

## Implementation Plan

### Phase 1: Bundle Building Service

**New class: `PublishBundleService`** in `cli/src/main/java/ca/weblite/jdeploy/services/`

Responsibilities:
1. Read `jdeploy.publishBundles` flag from package.json; bail if false
2. Determine which platforms to build from `DownloadPageSettings.getResolvedPlatforms()`
3. For each platform+arch:
   a. Call `Bundler.runit()` to create the native bundle (same as installer does)
   b. If CLI commands exist in package.json, also build CLI variant
   c. Wrap bundle in tar.gz using Apache Commons Compress (preserves POSIX file permissions)
   d. Compute SHA-256 hash of the tar.gz
   e. Store tar.gz file and metadata for later upload
4. Return a `BundleManifest` containing: list of `BundleArtifact`(file, platform, arch, isCli, sha256)

**New model: `BundleArtifact`** in `cli/src/main/java/ca/weblite/jdeploy/models/`
- Fields: `File file`, `String platform`, `String arch`, `String version`, `boolean cli`, `String sha256`, `String filename`

**New model: `BundleManifest`**
- Fields: `List<BundleArtifact> artifacts`
- Methods: `getArtifactsForPlatform(platform, arch)`, `toPackageJsonBundles(baseUrl)` → JSONObject

### Phase 2: S3 Upload Service

**New class: `S3BundleUploader`** in `cli/src/main/java/ca/weblite/jdeploy/publishing/s3/`

Responsibilities:
1. Read S3 config from environment variables
2. Upload a list of `BundleArtifact` JARs to S3
3. Return the public URLs for each uploaded artifact
4. Use the AWS SDK for Java (add `software.amazon.awssdk:s3` to cli/pom.xml)

**New class: `S3Config`** — reads and validates env vars, provides bucket/region/prefix.

**Fallback logic**: `BundleUploadRouter` decides where to upload:
- If `JDEPLOY_S3_BUCKET` is set → S3
- Else if target is GitHub → GitHub release
- Else → skip with warning

### Phase 3: Integration with GitHub Publish Driver

**Modify: `GitHubPublishDriver.prepare()`**

After existing prepare steps:
1. Check if `publishBundles` is enabled
2. Call `PublishBundleService.buildBundles()` to produce `BundleManifest`
3. Determine upload destination via `BundleUploadRouter`
4. If uploading to GitHub: add bundle tar.gz files to the release files directory so they get uploaded with the version release
5. If uploading to S3: call `S3BundleUploader`
6. Build URLs (GitHub release URL pattern or S3 URLs)
7. Write `jdeploy.bundles` into the package.json being published (same pattern as checksums)

### Phase 4: Integration with NPM Publish Driver

**Modify: `NPMPublishDriver`**

After existing prepare steps:
1. Check if `publishBundles` is enabled
2. Call `PublishBundleService.buildBundles()`
3. If `JDEPLOY_S3_BUCKET` is set: upload to S3, write URLs to package.json
4. If not: log warning that bundle publishing requires S3 for NPM targets, skip
5. Write `jdeploy.bundles` into the package.json being published

### Phase 5: package.json Update Logic

**New class: `BundleChecksumWriter`** in `cli/src/main/java/ca/weblite/jdeploy/publishing/`

Responsibilities:
1. Accept `BundleManifest` and URL map
2. Build the `jdeploy.bundles` JSONObject
3. Write it into the publish-copy of package.json (alongside existing checksum writing in `BasePublishDriver.prepare()`)

This follows the same pattern as the icon hash: the **source** package.json is not modified, only the **publish copy** in the temp directory.

### Phase 6: Configuration UI (Optional/Future)

Add a checkbox to the jDeploy GUI settings for `publishBundles`. This can be deferred since the feature can be enabled by editing package.json directly.

---

## File Changes Summary

### New Files
| File | Description |
|------|-------------|
| `cli/.../services/PublishBundleService.java` | Builds bundles for all target platforms |
| `cli/.../models/BundleArtifact.java` | Data model for a single bundle artifact |
| `cli/.../models/BundleManifest.java` | Collection of bundle artifacts with helpers |
| `cli/.../publishing/s3/S3BundleUploader.java` | Uploads bundles to S3 |
| `cli/.../publishing/s3/S3Config.java` | S3 environment variable configuration |
| `cli/.../publishing/BundleUploadRouter.java` | Routes uploads to S3 or GitHub |
| `cli/.../publishing/BundleChecksumWriter.java` | Writes bundle URLs/hashes to package.json |

### Modified Files
| File | Change |
|------|--------|
| `cli/.../publishing/github/GitHubPublishDriver.java` | Call PublishBundleService in prepare(), add tar.gz bundles to release |
| `cli/.../publishing/npm/NPMPublishDriver.java` | Call PublishBundleService, upload to S3 if configured |
| `cli/.../publishing/BasePublishDriver.java` | Call BundleChecksumWriter after existing checksum logic |
| `cli/pom.xml` | Add AWS S3 SDK dependency |

### Dependencies
- `software.amazon.awssdk:s3` — for S3 uploads (only needed at publish time, not at install time)

---

## Build & Test Considerations

- Bundle building invokes `Bundler.runit()` which requires platform-specific launcher binaries in resources — these are already present in the shared module
- Cross-platform bundling works: the bundler can create bundles for any platform regardless of the build host (it simply copies the correct launcher binary from resources)
- SHA-256 computation uses `java.security.MessageDigest` (no external dependency)
- Integration tests should verify:
  - Bundle tar.gz archives contain the expected files with correct permissions
  - SHA-256 hashes match
  - package.json bundles section is correctly populated
  - S3 upload (can be mocked)
  - GitHub release includes bundle tar.gz archives

## Open Questions

1. **JVM bundling**: Should pre-built bundles include an embedded JVM? This would make them self-contained but much larger. Recommendation: no — keep bundles JVM-free initially, matching current installer behavior where the launcher downloads a JVM on first run.

2. **Code signing**: Should pre-built macOS bundles be signed/notarized at publish time? This requires macOS build environment. Recommendation: defer to a future phase; unsigned bundles are still useful for direct download.

3. ~~**Compression within JAR**: Should the JAR use DEFLATED or STORED entries?~~ **Resolved**: Bundles now use tar.gz format instead of JAR, which provides good compression by default and preserves POSIX file permissions.

4. **Platform-specific bundles (.jdpignore)**: When platform bundles are enabled via `.jdpignore`, should publish-time bundling use the platform-filtered JARs? Recommendation: yes — apply the same `.jdpignore` filtering before bundling.
