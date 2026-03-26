# macOS Code Signing and Notarization Specification

**Status**: Reference
**Last Updated**: 2026-03-22

## Overview

jDeploy signs macOS `.app` bundles so they pass Gatekeeper validation and (optionally) Apple notarization. Two signing backends are supported:

| Backend | Platform | Tool | Certificate Format |
|---------|----------|------|--------------------|
| **Native codesign** | macOS only | `/usr/bin/codesign` + `/usr/bin/xcrun notarytool` | Keychain identity (by name) |
| **rcodesign** | Any (Linux, macOS, Windows) | `rcodesign` CLI | PKCS#12 (.p12) file |

Native codesign is preferred when running on macOS. rcodesign is the fallback for cross-platform CI (e.g., Linux GitHub Actions runners).

## Decision Flow

```
Is code signing enabled?
├─ No  → Skip signing entirely
└─ Yes
   ├─ Running on macOS AND JDEPLOY_FORCE_RCODESIGN != true?
   │  └─ Use native codesign
   └─ Otherwise (non-macOS, or force flag set)
      ├─ rcodesign available AND P12 configured?
      │  └─ Use rcodesign
      └─ Neither available → Skip signing (no error)

Is notarization enabled?
├─ No  → Skip notarization
└─ Yes (requires signing to have succeeded)
   ├─ Running on macOS AND JDEPLOY_FORCE_RCODESIGN != true?
   │  └─ Use native xcrun notarytool
   └─ Otherwise
      ├─ rcodesign available AND API credentials configured?
      │  └─ Use rcodesign notary-submit
      └─ Neither available → Skip notarization
```

## Enabling Code Signing

### In package.json

The `jdeploy` section of `package.json` controls signing per-project:

```json
{
  "jdeploy": {
    "codesign": true,
    "notarize": true
  }
}
```

| Field | Type | Default | Effect |
|-------|------|---------|--------|
| `codesign` | boolean | `false` | Enable code signing for macOS bundles |
| `notarize` | boolean | `false` | Enable notarization (implies `codesign`) |

These map to the `AppInfo.CodeSignSettings` enum:

| Combination | Enum Value |
|-------------|------------|
| Neither | `Default` (defers to global preferences) |
| `codesign: true` | `CodeSign` |
| `codesign: true, notarize: true` | `CodeSignAndNotarize` |

### Global Preferences

When package.json does not specify signing settings, the global preference from `C4JPublisherSettings.MacSigningSettings` is used. This can be `None`, `CodeSign`, or `CodeSignAndNotarize`.

---

## Credential Configuration

### Native codesign (macOS)

Native codesign uses a certificate identity name to look up the signing certificate in the macOS Keychain. Credentials are resolved in priority order:

#### Certificate Name (signing identity)

| Priority | Source | Key |
|----------|--------|-----|
| 1 | Environment variable | `JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME` |
| 2 | Properties file | `mac.developer-certificate-name` in `~/.jdeploy/private/jdeploy_settings.properties` |
| 3 | Java Preferences | `mac.developer-certificate-name` via `Preferences.userNodeForPackage(C4JPublisherSettings.class)` |

The value is the Common Name (CN) of the certificate, e.g., `"Developer ID Application: Your Name (TEAMID)"`.

#### Notarization Credentials

| Credential | Environment Variable | Properties Key |
|------------|---------------------|----------------|
| Apple ID | `JDEPLOY_MAC_DEVELOPER_ID` | `mac.developer-id` |
| App-specific password | `JDEPLOY_MAC_NOTARIZATION_PASSWORD` | `mac.notarization-password` |
| Team ID | `JDEPLOY_MAC_DEVELOPER_TEAM_ID` | `mac.developer-team-id` |

All three sources (env var, properties file, Java Preferences) are checked in that priority order for each credential. Per-app overrides are also supported using the app's bundle ID as a namespace prefix in the properties file (e.g., `com.example.myapp.mac.developer-id`).

### rcodesign (cross-platform)

rcodesign uses environment variables exclusively:

#### Signing

| Variable | Required | Description |
|----------|----------|-------------|
| `JDEPLOY_RCODESIGN_P12_FILE` | Yes | Absolute path to PKCS#12 (.p12) certificate file |
| `JDEPLOY_RCODESIGN_P12_PASSWORD` | No | Password for the P12 file (omit if no password) |

Signing is considered configured when `JDEPLOY_RCODESIGN_P12_FILE` is set and the file exists on disk.

#### Notarization

| Variable | Required | Description |
|----------|----------|-------------|
| `JDEPLOY_RCODESIGN_API_KEY_PATH` | Option A | Path to App Store Connect API key JSON file |
| `JDEPLOY_RCODESIGN_API_ISSUER` | Option B | App Store Connect API issuer UUID |
| `JDEPLOY_RCODESIGN_API_KEY` | Option B | App Store Connect API key ID |

Either Option A (JSON file) or Option B (issuer + key ID) must be provided. Option A takes priority if both are set.

#### Force Flag

| Variable | Description |
|----------|-------------|
| `JDEPLOY_FORCE_RCODESIGN` | Set to `"true"` or `"1"` to force rcodesign even on macOS (useful for testing) |

---

## What Gets Signed

### Bundle Structure

Before signing, jDeploy builds a standard macOS `.app` bundle:

```
MyApp.app/
├── Contents/
│   ├── Info.plist
│   ├── PkgInfo
│   ├── MacOS/
│   │   ├── Client4JLauncher          # Native GUI launcher (Mach-O)
│   │   └── Client4JLauncher-cli      # CLI launcher (byte-identical copy, if CLI commands enabled)
│   ├── Resources/
│   │   └── icon.icns
│   ├── app.xml                       # Application manifest
│   ├── jre/                          # Bundled JRE (if configured)
│   ├── Frameworks/                   # JCEF frameworks (if configured)
│   └── Library/
│       └── LaunchAgents/             # Service controller plists (if configured)
```

### Pre-signing Steps

On macOS, extended attributes (e.g., quarantine flags) are stripped before signing:

```bash
/usr/bin/xattr -cr '<appDir>'
```

This happens regardless of signing backend.

### Native codesign: Two-Pass Signing

**Pass 1** — Sign `app.xml` individually:
```bash
/usr/bin/codesign --verbose=4 -f --options runtime \
    -s "<certificate-name>" \
    --entitlements <entitlements-file> \
    <appDir>/Contents/app.xml
```

**Pass 2** — Deep-sign the entire bundle:
```bash
/usr/bin/codesign --deep --verbose=4 -f --options runtime \
    -s "<certificate-name>" \
    --entitlements <entitlements-file> \
    <appDir>
```

The `--deep` flag recursively signs all nested code (frameworks, dylibs, JRE binaries, etc.). The `-f` flag forces re-signing if anything is already signed. The `--options runtime` flag enables the Hardened Runtime, which is required for notarization.

### rcodesign: Single-Pass Signing

```bash
rcodesign sign \
    --p12-file <p12-path> \
    --p12-password <password> \
    --entitlements-xml-path <entitlements-file> \
    --code-signature-flags runtime \
    <appDir>
```

rcodesign signs the entire `.app` bundle in one pass. It does not support signing individual non-Mach-O files (like `app.xml`) separately — instead, `app.xml` is included as a sealed resource in the bundle signature. The `--code-signature-flags runtime` flag is the equivalent of native codesign's `--options runtime`.

---

## Entitlements

An entitlements file is embedded in the code signature. jDeploy checks for a project-local override at `jdeploy.mac.bundle.entitlements` in the working directory. If not found, the built-in default is used.

### Default Entitlements

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.files.user-selected.read-write</key>
    <true/>
    <key>com.apple.security.files.bookmarks.app-scope</key>
    <true/>
    <key>com.apple.security.network.client</key>
    <true/>
    <key>com.apple.security.print</key>
    <true/>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-executable-page-protection</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
    <key>com.apple.security.cs.allow-dyld-environment-variables</key>
    <true/>
    <key>com.apple.security.automation.apple-events</key>
    <true/>
</dict>
</plist>
```

### Why These Entitlements

| Entitlement | Reason |
|-------------|--------|
| `files.user-selected.read-write` | Allow user-initiated file access |
| `files.bookmarks.app-scope` | Persist file access across launches |
| `network.client` | Allow outbound network connections |
| `print` | Allow printing |
| `cs.allow-jit` | Required for JVM JIT compilation |
| `cs.allow-unsigned-executable-memory` | Required for JVM memory management |
| `cs.disable-executable-page-protection` | Required for JVM |
| `cs.disable-library-validation` | Allow loading bundled JRE/JCEF dylibs without individual signing |
| `cs.allow-dyld-environment-variables` | Allow DYLD_ environment variables for JVM |
| `automation.apple-events` | Allow AppleScript/Apple Events |

The `cs.*` entitlements are necessary because the JVM requires freedoms that the Hardened Runtime would otherwise restrict.

---

## Notarization

Notarization submits the signed app to Apple's notary service for malware scanning. On success, a notarization ticket is stapled to the app so Gatekeeper can verify it offline.

### Native Notarization (macOS)

1. **Create ZIP**: `/usr/bin/ditto -c -k --keepParent <appDir> <appDir>.zip`
2. **Store credentials**: `/usr/bin/xcrun notarytool store-credentials AC_PASSWORD --apple-id <id> --team-id <teamId> --password <password>`
3. **Submit and wait**: `/usr/bin/xcrun notarytool submit <zip> --keychain-profile AC_PASSWORD --wait`
4. **Staple**: `/usr/bin/xcrun stapler staple <appDir>`
5. **Validate**: `/usr/bin/xcrun stapler validate <appDir>`
6. **Cleanup**: Delete temporary ZIP

### rcodesign Notarization

1. **Submit and wait**: `rcodesign notary-submit --api-key-path <key-json> --wait <appDir>`
   - Or with separate credentials: `rcodesign notary-submit --api-issuer <uuid> --api-key <keyId> --wait <appDir>`
2. **Staple**: `rcodesign staple <appDir>`

### Post-Notarization

After notarization succeeds, the release tarball (`<AppName>.tar.gz`) is regenerated to include the stapled ticket.

---

## Release Artifact

After signing (and optionally notarizing), the `.app` bundle is compressed:

```
<releaseDir>/<platform>/AppName.tar.gz
```

Where `<platform>` is `mac-x64` or `mac-arm64`. If notarization occurred, the tarball is regenerated after stapling so the downloaded artifact includes the notarization ticket.

---

## Architecture Support

Both x64 and ARM64 macOS bundles are produced. Each gets its own native launcher binary (`Client4JLauncher`) but shares the same signing and notarization flow. The architecture-specific launchers are bundled resources at:

- `com/joshondesign/appbundler/mac/x64/Client4JLauncher`
- `com/joshondesign/appbundler/mac/arm64/Client4JLauncher`

---

## CI Configuration (GitHub Actions)

### Native codesign on macOS Runner

```yaml
runs-on: macos-latest
steps:
  - uses: apple-actions/import-codesign-certs@v1
    with:
      p12-file-base64: ${{ secrets.MAC_CERTIFICATE_P12_BASE64 }}
      p12-password: ${{ secrets.MAC_CERTIFICATE_PASSWORD }}
  - run: jdeploy publish
    env:
      JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME: ${{ secrets.MAC_CERT_NAME }}
      JDEPLOY_MAC_DEVELOPER_ID: ${{ secrets.APPLE_ID }}
      JDEPLOY_MAC_NOTARIZATION_PASSWORD: ${{ secrets.APP_SPECIFIC_PASSWORD }}
      JDEPLOY_MAC_DEVELOPER_TEAM_ID: ${{ secrets.TEAM_ID }}
```

### rcodesign on Linux Runner

```yaml
runs-on: ubuntu-latest
steps:
  - name: Install rcodesign
    run: cargo install apple-codesign
  - name: Write P12 certificate
    run: echo "${{ secrets.MAC_P12_BASE64 }}" | base64 -d > /tmp/cert.p12
  - run: jdeploy publish
    env:
      JDEPLOY_RCODESIGN_P12_FILE: /tmp/cert.p12
      JDEPLOY_RCODESIGN_P12_PASSWORD: ${{ secrets.MAC_P12_PASSWORD }}
      JDEPLOY_RCODESIGN_API_KEY_PATH: /tmp/api-key.json  # For notarization
```

---

## Key Source Files

| File | Purpose |
|------|---------|
| `shared/.../mac/MacBundler.java` | Orchestrates bundle creation, signing, and notarization |
| `shared/.../mac/RcodesignConfig.java` | Environment variable configuration for rcodesign |
| `shared/.../mac/RcodesignSigner.java` | Executes `rcodesign sign` |
| `shared/.../mac/RcodesignNotaryTool.java` | Executes `rcodesign notary-submit` and `staple` |
| `shared/.../mac/NotaryTool.java` | Executes native `xcrun notarytool` |
| `shared/.../appbundler/C4JPublisherSettings.java` | Credential resolution (env → properties → Java Preferences) |
| `shared/.../appbundler/AppDescription.java` | App metadata including signing flags |
| `shared/.../mac/mac.bundle.entitlements` | Default entitlements plist |
| `cli/.../services/PublishBundleService.java` | Reads `codesign`/`notarize` from package.json |
| `cli/.../app/AppInfo.java` | `CodeSignSettings` enum definition |

---

## Limitations and Notes

1. **Native codesign requires macOS** — Apple's `/usr/bin/codesign` is only available on macOS. Cross-platform CI must use rcodesign.

2. **rcodesign does not support individual file signing** — Unlike native codesign, rcodesign cannot sign a single non-Mach-O file (like `app.xml`). The file is sealed as a resource in the bundle signature instead. This is functionally equivalent.

3. **`--deep` is used for native codesign** — This recursively signs all nested code. While Apple recommends signing from the inside out for production apps, `--deep` is simpler and sufficient for the jDeploy use case where inner components do not need distinct identities.

4. **Entitlements override** — A project can provide its own `jdeploy.mac.bundle.entitlements` file in the working directory to customize entitlements. This is checked before falling back to the built-in default.

5. **Notarization requires signing** — The notarization path is only entered if code signing was also enabled and succeeded.

6. **rcodesign notarization requires App Store Connect API credentials** — Apple ID + password authentication is not supported by rcodesign; only API key-based auth works.

7. **The force flag is for testing** — `JDEPLOY_FORCE_RCODESIGN=true` overrides the native codesign preference on macOS, primarily useful for testing the rcodesign path on a Mac.
