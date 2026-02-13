# Implementation Plan: Windows Code Signing

**RFC**: `rfc/windows-signed-exe-support.md`
**Status**: Implementation Tasks
**Created**: 2026-02-13

## Overview

This plan outlines the implementation tasks for Windows Code Signing, enabling developers to sign their Windows executables using their own code signing certificates. Signed executables are published via the Prebuilt Apps infrastructure.

## Dependencies

- **Prebuilt Apps Publishing** (Phases 1-4) - Complete
- **Client4JLauncher External Preferences** - Ready (PR pending)

## Component Breakdown

---

## 1. Configuration & Data Model (`shared/`)

### 1.1 JDeployProject Model Updates

Add methods to read Windows signing configuration from package.json.

**File:** `shared/src/main/java/ca/weblite/jdeploy/models/JDeployProject.java`

**Tasks:**

- [x] **1.1.1** Add `isWindowsSigningEnabled()` method:
  ```java
  public boolean isWindowsSigningEnabled() {
      JSONObject jdeployConfig = getJDeployConfig();
      JSONObject windowsSigning = jdeployConfig.optJSONObject("windowsSigning");
      return windowsSigning != null && windowsSigning.optBoolean("enabled", false);
  }
  ```

- [x] **1.1.2** Add `getWindowsSigningProvider()` method:
  ```java
  public String getWindowsSigningProvider() {
      JSONObject windowsSigning = getWindowsSigningConfig();
      if (windowsSigning == null) return "jsign";
      return windowsSigning.optString("provider", "jsign");
  }
  ```

- [x] **1.1.3** Add `getWindowsSigningConfig()` method:
  ```java
  public JSONObject getWindowsSigningConfig() {
      return getJDeployConfig().optJSONObject("windowsSigning");
  }
  ```

- [x] **1.1.4** Add unit tests for Windows signing config methods

---

### 1.2 AppDescription Updates

Add Windows signing properties to the bundler's app description model.

**File:** `shared/src/main/java/ca/weblite/jdeploy/appbundler/AppDescription.java`

**Tasks:**

- [x] **1.2.1** Add Windows signing fields:
  ```java
  private boolean windowsCodeSigningEnabled;
  private String windowsCertificatePath;
  private String windowsCertificatePassword;
  private String windowsTimestampServer = "http://timestamp.digicert.com";
  private String windowsPublisherName;
  private String windowsPublicKey; // Extracted from cert, embedded in package.json
  ```

- [x] **1.2.2** Add getters and setters for all Windows signing fields

- [x] **1.2.3** Add `isWindowsCodeSigningEnabled()` method (follow macOS pattern)

- [x] **1.2.4** Update `AppDescriptionBuilder` to load Windows signing config:
  ```java
  if (project.isWindowsSigningEnabled()) {
      app.setWindowsCodeSigningEnabled(true);
      app.setWindowsCertificatePath(getEnvOrProperty("JDEPLOY_WINDOWS_CERTIFICATE_PATH", "windows.certificate-path"));
      app.setWindowsCertificatePassword(getEnvOrProperty("JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD", "windows.certificate-password"));
      app.setWindowsTimestampServer(getEnvOrProperty("JDEPLOY_WINDOWS_TIMESTAMP_SERVER", "windows.timestamp-server", "http://timestamp.digicert.com"));
      app.setWindowsPublisherName(getEnvOrProperty("JDEPLOY_WINDOWS_PUBLISHER_NAME", "windows.publisher-name"));
  }
  ```

- [x] **1.2.5** Add unit tests for Windows signing properties

---

### 1.3 WindowsSigningConfiguration Model

Create a dedicated configuration model for Windows signing.

**New File:** `shared/src/main/java/ca/weblite/jdeploy/signing/WindowsSigningConfiguration.java`

**Tasks:**

- [x] **1.3.1** Create `WindowsSigningConfiguration` class:
  ```java
  public class WindowsSigningConfiguration {
      private String certificatePath;
      private String certificatePassword;
      private String timestampServer;
      private String publisherName;
      private String algorithm = "SHA-256"; // Signing algorithm

      // Builder pattern
      public static Builder builder() { return new Builder(); }

      public static class Builder {
          // fluent setters
      }
  }
  ```

- [x] **1.3.2** Add factory method to create from environment/properties:
  ```java
  public static WindowsSigningConfiguration fromEnvironment() {
      return builder()
          .certificatePath(System.getenv("JDEPLOY_WINDOWS_CERTIFICATE_PATH"))
          .certificatePassword(System.getenv("JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD"))
          .timestampServer(System.getenv().getOrDefault("JDEPLOY_WINDOWS_TIMESTAMP_SERVER", "http://timestamp.digicert.com"))
          .publisherName(System.getenv("JDEPLOY_WINDOWS_PUBLISHER_NAME"))
          .build();
  }
  ```

- [x] **1.3.3** Add validation method:
  ```java
  public void validate() throws SigningConfigurationException {
      if (certificatePath == null || certificatePath.isEmpty()) {
          throw new SigningConfigurationException("Certificate path is required");
      }
      if (!new File(certificatePath).exists()) {
          throw new SigningConfigurationException("Certificate file not found: " + certificatePath);
      }
      // Additional validation...
  }
  ```

- [x] **1.3.4** Add unit tests for configuration and validation

---

## 2. Signing Implementation (`shared/`)

### 2.1 WindowsSigningProvider Interface

Create pluggable signing mechanism.

**New File:** `shared/src/main/java/ca/weblite/jdeploy/signing/WindowsSigningProvider.java`

**Tasks:**

- [ ] **2.1.1** Create `WindowsSigningProvider` interface:
  ```java
  public interface WindowsSigningProvider {
      /**
       * Signs a Windows executable file.
       * @param exeFile the executable to sign
       * @param config the signing configuration
       * @throws SigningException if signing fails
       */
      void sign(File exeFile, WindowsSigningConfiguration config) throws SigningException;

      /**
       * @return the provider name (e.g., "jsign")
       */
      String getName();

      /**
       * @return true if this provider is available on the current system
       */
      boolean isAvailable();

      /**
       * Extracts the public key from the certificate for embedding.
       * @param config the signing configuration
       * @return Base64-encoded public key
       */
      String extractPublicKey(WindowsSigningConfiguration config) throws SigningException;

      /**
       * Extracts the publisher name from the certificate.
       * @param config the signing configuration
       * @return the publisher/subject name
       */
      String extractPublisherName(WindowsSigningConfiguration config) throws SigningException;
  }
  ```

- [ ] **2.1.2** Create `SigningException` class:
  ```java
  public class SigningException extends Exception {
      public SigningException(String message) { super(message); }
      public SigningException(String message, Throwable cause) { super(message, cause); }
  }
  ```

---

### 2.2 JsignWindowsSigningProvider

Implement signing using jsign library.

**New File:** `shared/src/main/java/ca/weblite/jdeploy/signing/JsignWindowsSigningProvider.java`

**Tasks:**

- [ ] **2.2.1** Add jsign dependency to `shared/pom.xml`:
  ```xml
  <dependency>
      <groupId>net.jsign</groupId>
      <artifactId>jsign-core</artifactId>
      <version>6.0</version>
  </dependency>
  ```

- [ ] **2.2.2** Implement `JsignWindowsSigningProvider`:
  ```java
  public class JsignWindowsSigningProvider implements WindowsSigningProvider {

      @Override
      public void sign(File exeFile, WindowsSigningConfiguration config) throws SigningException {
          try {
              KeyStore keystore = loadKeyStore(config);
              String alias = findSigningAlias(keystore);

              AuthenticodeSigner signer = new AuthenticodeSigner(keystore, alias, config.getCertificatePassword())
                  .withProgramName(config.getPublisherName())
                  .withProgramURL(null)
                  .withTimestamping(true)
                  .withTimestampingMode(TimestampingMode.RFC3161)
                  .withTimestampingAuthority(config.getTimestampServer())
                  .withDigestAlgorithm(DigestAlgorithm.SHA256);

              signer.sign(new PEFile(exeFile));

          } catch (Exception e) {
              throw new SigningException("Failed to sign executable: " + e.getMessage(), e);
          }
      }

      @Override
      public String getName() {
          return "jsign";
      }

      @Override
      public boolean isAvailable() {
          return true; // Pure Java, always available
      }

      private KeyStore loadKeyStore(WindowsSigningConfiguration config) throws Exception {
          KeyStore keystore = KeyStore.getInstance("PKCS12");
          try (FileInputStream fis = new FileInputStream(config.getCertificatePath())) {
              keystore.load(fis, config.getCertificatePassword().toCharArray());
          }
          return keystore;
      }

      private String findSigningAlias(KeyStore keystore) throws Exception {
          Enumeration<String> aliases = keystore.aliases();
          while (aliases.hasMoreElements()) {
              String alias = aliases.nextElement();
              if (keystore.isKeyEntry(alias)) {
                  return alias;
              }
          }
          throw new SigningException("No signing key found in keystore");
      }
  }
  ```

- [ ] **2.2.3** Implement `extractPublicKey()`:
  ```java
  @Override
  public String extractPublicKey(WindowsSigningConfiguration config) throws SigningException {
      try {
          KeyStore keystore = loadKeyStore(config);
          String alias = findSigningAlias(keystore);
          Certificate cert = keystore.getCertificate(alias);
          return Base64.getEncoder().encodeToString(cert.getPublicKey().getEncoded());
      } catch (Exception e) {
          throw new SigningException("Failed to extract public key: " + e.getMessage(), e);
      }
  }
  ```

- [ ] **2.2.4** Implement `extractPublisherName()`:
  ```java
  @Override
  public String extractPublisherName(WindowsSigningConfiguration config) throws SigningException {
      try {
          KeyStore keystore = loadKeyStore(config);
          String alias = findSigningAlias(keystore);
          X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
          X500Principal subject = cert.getSubjectX500Principal();
          // Extract CN from subject
          String dn = subject.getName();
          for (String part : dn.split(",")) {
              if (part.trim().startsWith("CN=")) {
                  return part.trim().substring(3);
              }
          }
          return dn; // Fallback to full DN
      } catch (Exception e) {
          throw new SigningException("Failed to extract publisher name: " + e.getMessage(), e);
      }
  }
  ```

- [ ] **2.2.5** Add unit tests for JsignWindowsSigningProvider

---

### 2.3 WindowsSigningProviderFactory

Create factory for signing providers.

**New File:** `shared/src/main/java/ca/weblite/jdeploy/signing/WindowsSigningProviderFactory.java`

**Tasks:**

- [ ] **2.3.1** Create factory class:
  ```java
  public class WindowsSigningProviderFactory {

      private static final Map<String, WindowsSigningProvider> providers = new HashMap<>();

      static {
          registerProvider(new JsignWindowsSigningProvider());
      }

      public static void registerProvider(WindowsSigningProvider provider) {
          providers.put(provider.getName().toLowerCase(), provider);
      }

      public static WindowsSigningProvider getProvider(String name) {
          WindowsSigningProvider provider = providers.get(name.toLowerCase());
          if (provider == null) {
              throw new IllegalArgumentException("Unknown signing provider: " + name);
          }
          if (!provider.isAvailable()) {
              throw new IllegalStateException("Signing provider not available: " + name);
          }
          return provider;
      }

      public static WindowsSigningProvider getDefaultProvider() {
          return getProvider("jsign");
      }
  }
  ```

- [ ] **2.3.2** Add unit tests for factory

---

### 2.4 WindowsSigningService

Create service to orchestrate signing.

**New File:** `shared/src/main/java/ca/weblite/jdeploy/services/WindowsSigningService.java`

**Tasks:**

- [ ] **2.4.1** Create `WindowsSigningService`:
  ```java
  public class WindowsSigningService {

      private final PrintStream out;
      private final PrintStream err;

      public WindowsSigningService() {
          this(System.out, System.err);
      }

      public WindowsSigningService(PrintStream out, PrintStream err) {
          this.out = out;
          this.err = err;
      }

      /**
       * Signs a Windows executable.
       */
      public void signExecutable(File exeFile, WindowsSigningConfiguration config, String providerName)
              throws SigningException {
          out.println("Signing Windows executable: " + exeFile.getName());

          config.validate();

          WindowsSigningProvider provider = WindowsSigningProviderFactory.getProvider(providerName);
          out.println("Using signing provider: " + provider.getName());

          long startTime = System.currentTimeMillis();
          provider.sign(exeFile, config);
          long elapsed = System.currentTimeMillis() - startTime;

          out.println("Successfully signed " + exeFile.getName() + " in " + elapsed + "ms");
      }

      /**
       * Extracts signing metadata for embedding in package.json.
       */
      public SigningMetadata extractMetadata(WindowsSigningConfiguration config, String providerName)
              throws SigningException {
          WindowsSigningProvider provider = WindowsSigningProviderFactory.getProvider(providerName);
          return new SigningMetadata(
              provider.extractPublisherName(config),
              provider.extractPublicKey(config)
          );
      }
  }

  public class SigningMetadata {
      private final String publisherName;
      private final String publicKey;

      // constructor, getters
  }
  ```

- [ ] **2.4.2** Add unit tests for WindowsSigningService

---

## 3. Bundler Integration (`shared/`)

### 3.1 WindowsBundler Updates

Integrate signing into the Windows bundler.

**File:** `shared/src/main/java/com/joshondesign/appbundler/win/WindowsBundler.java`

**Tasks:**

- [ ] **3.1.1** Add signing after exe generation (follow MacBundler pattern):
  ```java
  // After exe is generated and copied to destDir
  File exeFile = new File(destDir, exeName);

  if (app.isWindowsCodeSigningEnabled()) {
      System.out.println("Signing " + exeFile.getAbsolutePath());

      WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
          .certificatePath(app.getWindowsCertificatePath())
          .certificatePassword(app.getWindowsCertificatePassword())
          .timestampServer(app.getWindowsTimestampServer())
          .publisherName(app.getWindowsPublisherName())
          .build();

      WindowsSigningService signingService = new WindowsSigningService();
      signingService.signExecutable(exeFile, config, "jsign");
  }
  ```

- [ ] **3.1.2** Handle signing failures gracefully:
  ```java
  try {
      signingService.signExecutable(exeFile, config, "jsign");
  } catch (SigningException e) {
      System.err.println("Warning: Failed to sign executable: " + e.getMessage());
      if (app.isWindowsSigningRequired()) {
          throw new RuntimeException("Windows code signing failed", e);
      }
      // Continue without signing if not required
  }
  ```

- [ ] **3.1.3** Add integration test for signed exe generation

---

### 3.2 Package.json Embedding

Embed signing metadata in package.json during publish.

**File:** `cli/src/main/java/ca/weblite/jdeploy/services/GithubReleasePackageJsonEmbedder.java` (or similar)

**Tasks:**

- [ ] **3.2.1** Extract and embed signing metadata:
  ```java
  if (project.isWindowsSigningEnabled()) {
      WindowsSigningConfiguration config = WindowsSigningConfiguration.fromEnvironment();
      WindowsSigningService signingService = new WindowsSigningService();
      SigningMetadata metadata = signingService.extractMetadata(config, "jsign");

      JSONObject windowsSigning = packageJson.getJSONObject("jdeploy")
          .getJSONObject("windowsSigning");
      windowsSigning.put("publisherName", metadata.getPublisherName());
      windowsSigning.put("publicKey", metadata.getPublicKey());
  }
  ```

- [ ] **3.2.2** Add test for metadata embedding

---

## 4. Installer Trust Flow (`installer/`)

### 4.1 Signature Verification

Verify signatures on downloaded prebuilt apps.

**New File:** `installer/src/main/java/ca/weblite/jdeploy/installer/signing/WindowsSignatureVerifier.java`

**Tasks:**

- [ ] **4.1.1** Create `WindowsSignatureVerifier`:
  ```java
  public class WindowsSignatureVerifier {

      /**
       * Verifies that an executable is signed.
       * @return true if signed, false if not signed
       */
      public boolean isSigned(File exeFile) {
          // Use jsign or native Windows API to check signature
      }

      /**
       * Extracts the public key from a signed executable.
       */
      public String extractPublicKey(File exeFile) throws VerificationException {
          // Extract public key from signature
      }

      /**
       * Verifies signature matches expected public key.
       */
      public boolean verifySignature(File exeFile, String expectedPublicKey) {
          String actualKey = extractPublicKey(exeFile);
          return expectedPublicKey.equals(actualKey);
      }
  }
  ```

- [ ] **4.1.2** Add verification to prebuilt app download flow:
  ```java
  // In PrebuiltAppInstaller or similar
  if (packageJson has windowsSigning.publicKey) {
      WindowsSignatureVerifier verifier = new WindowsSignatureVerifier();
      if (!verifier.verifySignature(downloadedExe, expectedPublicKey)) {
          throw new SecurityException("Signature verification failed");
      }
  }
  ```

- [ ] **4.1.3** Add unit tests for signature verification

---

### 4.2 Certificate Trust Service

Check and install certificate trust.

**New File:** `installer/src/main/java/ca/weblite/jdeploy/installer/signing/CertificateTrustService.java`

**Tasks:**

- [ ] **4.2.1** Create `CertificateTrustService`:
  ```java
  public class CertificateTrustService {

      /**
       * Checks if the signing certificate is trusted on this system.
       */
      public boolean isCertificateTrusted(String publicKey) {
          // Check Windows trust store
          // This may require native code or PowerShell
      }

      /**
       * Installs certificate to Windows trust store.
       * Requires elevation.
       */
      public void installCertificate(File certificateFile) throws TrustException {
          // Use certutil or PowerShell to install cert
          // Must be run elevated
      }

      /**
       * Extracts certificate from signed executable for installation.
       */
      public File extractCertificate(File signedExe) throws TrustException {
          // Extract cert from signature
      }
  }
  ```

- [ ] **4.2.2** Add unit tests for trust service

---

### 4.3 Elevated Process for Trust Installation

Implement UAC elevation flow.

**New File:** `installer/src/main/java/ca/weblite/jdeploy/installer/signing/ElevatedTrustInstaller.java`

**Tasks:**

- [ ] **4.3.1** Create `ElevatedTrustInstaller`:
  ```java
  public class ElevatedTrustInstaller {

      /**
       * Launches installer with elevated privileges to install certificate.
       * @param certificatePath path to certificate file
       * @return true if installation succeeded
       */
      public boolean installWithElevation(String certificatePath) {
          // Launch self with --install-certificate argument
          // Uses ShellExecute with "runas" verb on Windows
      }

      /**
       * Called when installer is launched with --install-certificate.
       * Runs in elevated context.
       */
      public static void runElevatedInstall(String certificatePath) {
          CertificateTrustService trustService = new CertificateTrustService();
          trustService.installCertificate(new File(certificatePath));
      }
  }
  ```

- [ ] **4.3.2** Add command-line argument handling in Main.java:
  ```java
  if (args contains "--install-certificate") {
      String certPath = args[next];
      ElevatedTrustInstaller.runElevatedInstall(certPath);
      System.exit(0);
  }
  ```

- [ ] **4.3.3** Add integration test for elevation flow

---

### 4.4 UI Integration

Add trust checkbox to installer UI.

**File:** `installer/src/main/java/ca/weblite/jdeploy/installer/views/` (identify permission panel)

**Tasks:**

- [ ] **4.4.1** Add "Trust this developer" checkbox:
  ```java
  // In permission/installation panel
  if (isAppSigned && !isCertificateTrusted) {
      JCheckBox trustCheckbox = new JCheckBox(
          "Trust " + publisherName + " on this PC"
      );
      trustCheckbox.setToolTipText(
          "Installing trust allows apps from this developer to run without security warnings"
      );
      panel.add(trustCheckbox);
  }
  ```

- [ ] **4.4.2** Handle checkbox state during installation:
  ```java
  if (trustCheckbox.isSelected()) {
      // After main installation
      ElevatedTrustInstaller installer = new ElevatedTrustInstaller();
      installer.installWithElevation(certificatePath);
  }
  ```

- [ ] **4.4.3** Add "Learn more" link with certificate details dialog

- [ ] **4.4.4** Add UI tests

---

## 5. GUI Integration (`cli/` GUI components)

### 5.1 Windows Signing Panel

Add signing configuration to project editor.

**Tasks:**

- [ ] **5.1.1** Create Windows Signing panel in project editor:
  - Enable/disable checkbox
  - Certificate path field (with browse button)
  - Password field (masked)
  - Timestamp server field (with default)
  - "Test Signing" button

- [ ] **5.1.2** Add validation feedback:
  - Certificate file exists
  - Certificate can be loaded
  - Certificate not expired

- [ ] **5.1.3** Add help text explaining Windows signing requirements

---

## Implementation Phases

### Phase 1: Core Infrastructure
| Task | Description | Dependencies |
|------|-------------|--------------|
| 1.1.1-1.1.4 | JDeployProject model updates | None |
| 1.2.1-1.2.5 | AppDescription updates | None |
| 1.3.1-1.3.4 | WindowsSigningConfiguration | None |

### Phase 2: Signing Implementation
| Task | Description | Dependencies |
|------|-------------|--------------|
| 2.1.1-2.1.2 | WindowsSigningProvider interface | Phase 1 |
| 2.2.1-2.2.5 | JsignWindowsSigningProvider | 2.1.x |
| 2.3.1-2.3.2 | WindowsSigningProviderFactory | 2.2.x |
| 2.4.1-2.4.2 | WindowsSigningService | 2.3.x |

### Phase 3: Bundler Integration
| Task | Description | Dependencies |
|------|-------------|--------------|
| 3.1.1-3.1.3 | WindowsBundler signing integration | Phase 2 |
| 3.2.1-3.2.2 | Package.json metadata embedding | 3.1.x |

### Phase 4: Installer Trust Flow
| Task | Description | Dependencies |
|------|-------------|--------------|
| 4.1.1-4.1.3 | Signature verification | Phase 3 |
| 4.2.1-4.2.2 | Certificate trust service | 4.1.x |
| 4.3.1-4.3.3 | Elevated trust installer | 4.2.x |
| 4.4.1-4.4.4 | UI integration | 4.3.x |

### Phase 5: GUI & Polish
| Task | Description | Dependencies |
|------|-------------|--------------|
| 5.1.1-5.1.3 | Windows Signing panel | Phase 3 |
| - | End-to-end testing | All phases |
| - | Documentation | All phases |

---

## Testing Strategy

### Unit Tests
- Configuration validation
- Signing provider (with mock certificates)
- Metadata extraction
- Trust service (mocked)

### Integration Tests
- Full signing flow with test certificate
- Bundler produces signed exe
- Signature verification passes

### Manual Tests
| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| Sign with valid cert | Enable signing, publish | Signed exe in release |
| Missing cert | Enable signing, no cert file | Clear error message |
| Expired cert | Use expired cert | Warning/error |
| Installer verifies signature | Download signed app | Verification passes |
| Trust installation | Check trust box, install | UAC prompt, cert installed |

---

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `JDEPLOY_WINDOWS_CERTIFICATE_PATH` | Path to .pfx/.p12 file | Yes |
| `JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD` | Certificate password | Yes |
| `JDEPLOY_WINDOWS_TIMESTAMP_SERVER` | Timestamp server URL | No (default: digicert) |
| `JDEPLOY_WINDOWS_PUBLISHER_NAME` | Display name override | No (extracted from cert) |

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| jsign compatibility issues | Medium | Fallback to external signtool |
| Certificate loading failures | High | Detailed error messages, validation |
| UAC elevation fails | Medium | Graceful fallback, continue without trust |
| Timestamp server unavailable | Low | Multiple fallback servers |

---

## Estimated Effort

| Phase | Tasks | Complexity |
|-------|-------|------------|
| Phase 1 | 13 | Low |
| Phase 2 | 12 | Medium |
| Phase 3 | 5 | Medium |
| Phase 4 | 12 | High |
| Phase 5 | 3+ | Low |

**Total**: ~45 tasks

---

## Next Steps

1. Begin Phase 1 implementation
2. Set up test certificate for development
3. Verify jsign library compatibility
