# RFC: Windows Signed Executable Support

**Status**: Decisions Complete - Ready for Implementation Planning
**Author**: [Team]
**Created**: 2026-02-07

## Dependencies

This RFC depends on:
- **[Prebuilt Apps Publishing](app-bundle-publishing.md)** - Provides the infrastructure for publishing prebuilt native apps to GitHub releases and jdeploy.com registry. When Windows signing is enabled, prebuilt apps are automatically enabled for Windows platforms.

## Problem Statement

Currently, jDeploy signs the installer but not the application executable itself. The installer dynamically builds the application executable during installation, which results in an unsigned `.exe` file. Windows Defender and other security software frequently flag unsigned executables as potential malware, causing:

- Poor user experience with security warnings
- Reduced trust in distributed applications
- Potential blocking of legitimate applications by corporate security policies

## Proposed Solution Overview

Enable developers to sign their Windows executables using their own code signing certificates. The signed executable would be:

1. Generated and signed during the publishing phase
2. Published via the Prebuilt Apps infrastructure (automatically enabled when signing is configured)
3. Downloaded and installed by the installer instead of being dynamically generated

**Note**: The storage and distribution of signed executables is handled by the Prebuilt Apps Publishing RFC. This RFC focuses solely on the signing process and related concerns.

## Decisions Made

All key decisions have been finalized.

---

### Decision 1: Signing Implementation Approach

**Decision**: Pluggable signing mechanism with jsign as default

The signing mechanism will be pluggable, allowing different signing providers:

**Built-in Providers:**
- **jsign (default)** - Pure Java, cross-platform, works in CI/CD
  - Supports PKCS#12 (.pfx, .p12), JKS keystores
  - Supports PKCS#11 hardware tokens
  - Supports cloud signing (AWS, Azure, Google Cloud)

**Future/Pluggable Providers:**
- **External command** - Invoke signtool.exe or other external tools
- **Remote signing service** - For enterprise/managed signing

**Architecture:**
```java
public interface WindowsSigningProvider {
    void sign(File exeFile, SigningConfiguration config) throws SigningException;
    String getName();
    boolean isAvailable();
}
```

**Configuration:**
```json
{
  "jdeploy": {
    "windowsSigning": {
      "enabled": true,
      "provider": "jsign"
    }
  }
}
```

If `provider` is omitted, defaults to `jsign`.

---

### Decision 2: Where Does Signing Occur in the Pipeline?

**Decision**: During bundling (consistent with macOS)

Signing occurs during the bundling phase, following the same pattern as macOS code signing in `MacBundler`:

1. Check if signing is enabled (`app.isWindowsCodeSigningEnabled()` or similar)
2. After generating the exe, invoke the signing provider
3. Signed exe is included in the bundle/tarball

**Reference**: See `MacBundler.java` lines 159-205 for the macOS pattern:
```java
if (app.isMacCodeSigningEnabled()) {
    // Load entitlements, sign app.xml, then deep-sign bundle
}
```

**Windows equivalent in WindowsBundler**:
```java
if (app.isWindowsCodeSigningEnabled()) {
    WindowsSigningProvider provider = getSigningProvider(app);
    provider.sign(exeFile, signingConfig);
}
```

This approach:
- Keeps signing close to exe generation
- Consistent with existing macOS implementation
- Signed exe travels through rest of pipeline (tarball, upload)

---

### Decision 3: Relationship to Existing Certificate Pinning

**Decision**: Separate certificates allowed, but same certificate supported if technically compatible

- Exe signing and package signing are configured independently
- Users may use separate certificates (different purposes/requirements)
- Users may use the same certificate for both if technically compatible
- No automatic coupling between the features

**Rationale**:
- Windows code signing certs have specific requirements (EV certs for SmartScreen reputation)
- Package signing (certificate pinning) is about integrity verification
- Exe signing is about OS-level trust
- Maximum flexibility while allowing simplicity for users who want one certificate

---

### Decision 4: Configuration Storage for Prebuilt Apps

**Moved to**: [Prebuilt Apps Publishing RFC](app-bundle-publishing.md)

This concern applies to all prebuilt apps (signed or unsigned), not just Windows signing. See the Prebuilt Apps RFC for the decision on external settings storage.

---

### Decision 5: Certificate Trust Installation Flow

**Decision**: Background elevated process for certificate installation

**Flow**:
1. Installer checks if app is signed (public key info in package.json)
2. If signed, check if certificate is already trusted on system
3. If not trusted, show checkbox in permission prompt: "This app is signed by {developer name}. Trust this developer on this PC"
4. User clicks "Install" / "Proceed"
5. If checkbox was checked:
   - Prompt user for UAC elevation
   - Launch installer in background with special argument (e.g., `--install-certificate`)
   - Background process installs certificate to Windows trust store (no UI)
   - Background process exits
6. Main installer continues with normal installation

**Key Points**:
- All UI is in the main installer process
- Elevated process runs in background with no UI
- Special argument tells installer it's in "certificate install mode"
- End-user sees: click Install → UAC prompt → installation continues

**Additional Details**:
- **Developer opt-in**: Not required. If app is signed, trust installation is offered automatically.
- **Trust scope**: Machine-wide trust (not user-only)
- **Certificate revocation**: Not handled (out of scope)
- **Developer info displayed**:
  - Show publisher name in checkbox label
  - "Learn more" link opens dialog with full certificate details

---

### Decision 6: Required Configuration Properties

**Decision**: Follow macOS signing pattern - flags in package.json, credentials external

**In package.json** (committed to source control):
```json
{
  "jdeploy": {
    "windowsSigning": {
      "enabled": true
    }
  }
}
```

Only the enable flag is in package.json. No sensitive credentials.

**Credentials from environment variables** (highest priority):
- `JDEPLOY_WINDOWS_CERTIFICATE_PATH` - Path to .pfx/.p12 file
- `JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD` - Certificate password
- `JDEPLOY_WINDOWS_TIMESTAMP_SERVER` - Timestamp server URL (optional, has default)
- `JDEPLOY_WINDOWS_PUBLISHER_NAME` - Publisher name for display

**Credentials from properties file** (fallback):
- `~/.jdeploy/private/jdeploy_settings.properties`
- Properties: `windows.certificate-path`, `windows.certificate-password`, etc.

**Embedded at publish time** (written by jDeploy, not user):
```json
{
  "jdeploy": {
    "windowsSigning": {
      "enabled": true,
      "publisherName": "My Company, Inc.",
      "publicKey": "BASE64_ENCODED_PUBLIC_KEY"
    }
  }
}
```

**Notes**:
- `windowsPublicKey` is extracted from certificate and embedded at publish time
- Used by installer to verify signature and display trust information
- Follows same pattern as macOS: `codesign`/`notarize` flags in package.json, credentials external

**Defaults**:
- Timestamp server: `http://timestamp.digicert.com` (if not specified)
- Provider: `jsign` (if not specified)

**EV Certificates / Hardware Tokens**:
- jsign supports PKCS#11 for hardware tokens
- Additional env vars for PKCS#11 configuration (future enhancement)

---

## Work Streams

Once decisions are made, implementation will be divided into these work streams:

### Work Stream 1: Configuration & Data Model
- Add signing properties to package.json schema
- Update JDeployProject model
- Add validation for signing configuration

### Work Stream 2: GUI Integration
- Add "Windows Signing" panel to project editor
- Certificate selection/configuration UI
- Test signing configuration validation

### Work Stream 3: Signing Implementation
- Integrate signing library (likely jsign)
- Implement signing service
- Handle different certificate types
- Integrate with App Bundle Publishing pipeline (sign before upload)

### Work Stream 4: Installer Trust Flow
- Detect signed apps (check `publisherPublicKey` in package.json)
- Verify signature on downloaded bundle
- Check certificate trust status
- Implement elevated permission flow
- Install certificate to trust store

**Note**: External settings support and launcher updates are handled by the Prebuilt Apps Publishing RFC.

---

## Integration with Prebuilt Apps Publishing

This feature integrates with the Prebuilt Apps workflow as follows:

```
┌─────────────────────────────────────────────────────────────────┐
│                      Publishing Pipeline                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Generate Native App (existing bundler)                      │
│         │                                                       │
│         ▼                                                       │
│  2. Sign Windows Exe (THIS RFC)                                 │
│     - Only if windowsSigning.enabled = true                     │
│     - Uses jsign (default) or pluggable provider                │
│         │                                                       │
│         ▼                                                       │
│  3. Publish Prebuilt App (Prebuilt Apps RFC)                    │
│     - Automatically enabled when signing is enabled             │
│     - Upload to GitHub releases                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Key Integration Points**:
- Signing occurs after app generation but before prebuilt app publishing
- Enabling signing automatically enables prebuilt apps for Windows platforms
- Signed apps cannot fall back to local building (signature would be lost)

---

## Security Considerations

- **Private Key Protection**: Certificate passwords should never be stored in package.json directly
- **Certificate Validation**: Should verify certificate hasn't expired before signing
- **Trust Store Manipulation**: Installing certs to trust store has security implications
- **Timestamp Servers**: Required for signature validity after cert expiration
- **Signature Verification**: Installer must verify signature before trusting the bundle

## Open Questions

1. What happens if signing fails during CI/CD publish? (Fail the build? Publish unsigned?)
2. Should there be a "dry run" mode to test signing without publishing?
3. How to handle certificate renewal/rotation?
4. PKCS#11 configuration for EV certificates with hardware tokens (future enhancement)

## Next Steps

1. Create detailed implementation plan
2. Prioritize work streams
3. Begin implementation

---

## Appendix: Related Features

### Prebuilt Apps Publishing
See **[app-bundle-publishing.md](app-bundle-publishing.md)** for the infrastructure that handles storage and distribution of prebuilt native apps (signed or unsigned). This feature is automatically enabled when Windows signing is configured.

### Existing Certificate Pinning
jDeploy already supports certificate pinning for package integrity verification. Key classes:
- `AppInfo.enableCertificatePinning`
- `AppInfo.trustedCertificates`
- `DeveloperIdentityKeyStore`

### Platform-Specific Bundles
The platform-specific bundles RFC (`rfc/platform-specific-bundles.md`) may influence how signed exe distribution works.

### jsign Reference
[jsign](https://github.com/ebourg/jsign) is a pure Java implementation of Microsoft Authenticode for signing:
- Windows executables (exe, dll)
- Installers (msi)
- Scripts (ps1, vbs, js)
- CAB files

Supports various certificate sources:
- PKCS#12 keystores (.pfx, .p12)
- Java keystores (.jks)
- PKCS#11 hardware tokens
- Cloud signing services (AWS, Azure, Google Cloud, etc.)
