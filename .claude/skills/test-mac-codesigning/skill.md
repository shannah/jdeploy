# Test macOS Code Signing

Test the jDeploy macOS code signing feature using the scratch repository https://github.com/shannah/jdeploy-test-app.

This skill tests macOS `.app` bundle signing using either native `codesign` (on macOS) or `rcodesign` (on macOS or Linux). It uses a self-signed certificate for signing and a fake `rcodesign` wrapper for notarization (since Apple's notary API cannot be mocked).

## Usage

```
/test-mac-codesigning <jdeploy-version> [action-ref] [--local] [--rcodesign]
```

Examples:
```
# CI test via GitHub Actions (native codesign on macOS runner)
/test-mac-codesigning 6.1.1

# CI test with specific action branch
/test-mac-codesigning 6.1.1 claude/add-rcodesign-fallback-ZK6Xq

# Local test on macOS using native codesign
/test-mac-codesigning 6.1.1 --local

# Local test using rcodesign (works on macOS or Linux)
/test-mac-codesigning 6.1.1 --local --rcodesign

# CI test forcing rcodesign path
/test-mac-codesigning 6.1.1 --rcodesign
```

- `jdeploy-version`: The jDeploy CLI version to use (e.g., `6.1.1`)
- `action-ref` (optional): GitHub Action ref. If not provided, uses `v<jdeploy-version>`.
- `--local` (optional): Run the test locally instead of via GitHub Actions CI
- `--rcodesign` (optional): Force using the rcodesign path instead of native codesign

## Instructions

### Step 1: Parse Arguments

- First argument: `$JDEPLOY_VERSION` - the jDeploy CLI version
- Second argument (optional): `$ACTION_REF` - the GitHub Action ref (or a flag)
  - If not provided, default to `v$JDEPLOY_VERSION`
- Check for `--local` flag in any position to set `$LOCAL_TEST=true`
- Check for `--rcodesign` flag in any position to set `$USE_RCODESIGN=true`

### Step 2: Determine the Next Version

Check the latest GitHub release and bump the patch version:

```bash
gh release list --repo shannah/jdeploy-test-app --limit 1
```

Parse the latest version and increment the patch number.

### Step 3: Clone/Update the Test Repository

```bash
WORK_DIR=$(mktemp -d)
cd "$WORK_DIR"
git clone https://github.com/shannah/jdeploy-test-app.git
cd jdeploy-test-app
```

### Step 4: Update Project Files

Update the version in:
- `pom.xml`
- `package.json` (version and jar path)
- `Main.java` (version strings in UI - update label to indicate signing test type)

Ensure `package.json` includes:
1. The `"codesign": true` flag in the jdeploy config
2. The artifacts configuration for bundle publishing

```json
{
  "jdeploy": {
    "codesign": true,
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

**Important**: Do NOT add `"notarize": true` — we are only testing code signing, not notarization. Notarization requires real Apple credentials and cannot be tested with self-signed certs.

### Step 5A: Local Test (if --local)

#### 5A.1: Generate a Self-Signed Certificate

**On macOS (native codesign path, i.e., `--local` without `--rcodesign`):**

Create a self-signed code signing certificate in the macOS Keychain:

```bash
# Create a certificate signing request config
cat > /tmp/jdeploy-test-cert.cfg << 'EOF'
[ req ]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = v3_code_sign

[ dn ]
CN = jDeploy Test Signing
O = jDeploy Test
C = US

[ v3_code_sign ]
keyUsage = critical, digitalSignature
extendedKeyUsage = codeSigning
basicConstraints = critical, CA:FALSE
EOF

# Generate key and certificate
openssl req -x509 -newkey rsa:2048 \
  -keyout /tmp/jdeploy-test-key.pem \
  -out /tmp/jdeploy-test-cert.pem \
  -days 1 -nodes \
  -config /tmp/jdeploy-test-cert.cfg

# Create P12 bundle
openssl pkcs12 -export \
  -out /tmp/jdeploy-test.p12 \
  -inkey /tmp/jdeploy-test-key.pem \
  -in /tmp/jdeploy-test-cert.pem \
  -passout pass:test123

# Import into macOS Keychain and trust for code signing
security import /tmp/jdeploy-test.p12 \
  -k ~/Library/Keychains/login.keychain-db \
  -P test123 \
  -T /usr/bin/codesign

# Find the certificate identity name
CERT_NAME=$(security find-identity -v -p codesigning | grep "jDeploy Test Signing" | head -1 | sed 's/.*"\(.*\)".*/\1/')
echo "Certificate identity: $CERT_NAME"
```

**On any platform (rcodesign path, i.e., `--local --rcodesign`):**

```bash
# Generate key and certificate
openssl req -x509 -newkey rsa:2048 \
  -keyout /tmp/jdeploy-test-key.pem \
  -out /tmp/jdeploy-test-cert.pem \
  -days 1 -nodes \
  -subj "/CN=jDeploy Test Signing/O=jDeploy Test/C=US"

# Create P12 for rcodesign
openssl pkcs12 -export \
  -out /tmp/jdeploy-test.p12 \
  -inkey /tmp/jdeploy-test-key.pem \
  -in /tmp/jdeploy-test-cert.pem \
  -passout pass:test123
```

#### 5A.2: Verify Prerequisites

**For rcodesign path**, verify rcodesign is installed:

```bash
rcodesign --version
```

If not installed, instruct the user:
- macOS: `brew install rcodesign` or download from https://github.com/indygreg/apple-platform-rs/releases
- Linux: Download the Linux binary from https://github.com/indygreg/apple-platform-rs/releases

#### 5A.3: Build jDeploy from Current Source

```bash
cd /home/user/jdeploy
cd shared && mvn clean install -DskipTests -q
cd ../cli && mvn clean package -DskipTests -q
npm link
```

#### 5A.4: Set Environment Variables and Run

**For native codesign (macOS only, `--local` without `--rcodesign`):**

```bash
cd "$WORK_DIR/jdeploy-test-app"
mvn clean package -DskipTests -q

export JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME="$CERT_NAME"
jdeploy github-prepare-release
```

The native codesign path is triggered automatically when:
- Running on macOS (`Platform.getSystemPlatform().isMac()` is true)
- `codesign: true` in package.json
- `JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME` env var is set

**For rcodesign (`--local --rcodesign`):**

```bash
cd "$WORK_DIR/jdeploy-test-app"
mvn clean package -DskipTests -q

export JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME="jDeploy Test Signing"
export JDEPLOY_RCODESIGN_P12_FILE=/tmp/jdeploy-test.p12
export JDEPLOY_RCODESIGN_P12_PASSWORD=test123
jdeploy github-prepare-release
```

The rcodesign path is triggered when:
- NOT running on macOS, OR running on macOS but codesign would fail (rcodesign is the fallback)
- `codesign: true` in package.json
- `JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME` is set (needed to enable code signing in `AppDescription`)
- `JDEPLOY_RCODESIGN_P12_FILE` points to a valid P12 file
- `rcodesign` binary is on PATH

**Note about testing rcodesign on macOS**: The code checks `Platform.getSystemPlatform().isMac()` and prefers native codesign. To force the rcodesign path on macOS for testing, you may need to temporarily rename `/usr/bin/codesign` (not recommended) or modify the platform check. A simpler approach: test the rcodesign path on Linux, and the native codesign path on macOS.

#### 5A.5: Verify the Signature

**Native codesign verification (macOS):**

```bash
# Find the generated .app bundle
APP_BUNDLE=$(find "$WORK_DIR/jdeploy-test-app/jdeploy" -name "*.app" -type d | head -1)

# Verify the signature
codesign --verify --verbose=4 "$APP_BUNDLE"

# Display signature details
codesign --display --verbose=4 "$APP_BUNDLE"
```

**rcodesign verification (any platform):**

```bash
APP_BUNDLE=$(find "$WORK_DIR/jdeploy-test-app/jdeploy" -name "*.app" -type d | head -1)

# Verify with rcodesign
rcodesign verify "$APP_BUNDLE"

# Print signature info
rcodesign print-signature-info "$APP_BUNDLE"
```

Expected results for self-signed certificates:
- Signature is present and valid structurally
- On macOS native: `codesign --verify` may warn about untrusted cert but signature structure is valid
- With rcodesign: `rcodesign verify` confirms signature is present

#### 5A.6: Report Local Results

```
Local test complete.

**Mode**: Native codesign | rcodesign
**Platform**: macOS | Linux
**App Bundle**: <path to .app>
**Signature Verification**: <PASS/FAIL with details>
```

Skip to Step 7 (do not do Steps 5B or 6).

### Step 5B: CI Test Setup (if not --local)

#### 5B.1: Generate Certificate and Set Secrets

```bash
cd /tmp
openssl req -x509 -newkey rsa:2048 \
  -keyout test-mac-signing-key.pem \
  -out test-mac-signing-cert.pem \
  -days 365 -nodes \
  -subj "/CN=jDeploy Test Mac Signing/O=jDeploy Test/C=US"

openssl pkcs12 -export \
  -out test-mac-signing.pfx \
  -inkey test-mac-signing-key.pem \
  -in test-mac-signing-cert.pem \
  -passout pass:test123

base64 -i test-mac-signing.pfx | tr -d '\n' > test-mac-signing-base64.txt
gh secret set MAC_SIGNING_P12_BASE64 --repo shannah/jdeploy-test-app < test-mac-signing-base64.txt
gh secret set MAC_SIGNING_P12_PASSWORD --repo shannah/jdeploy-test-app --body "test123"
```

#### 5B.2: Create Workflow

Determine workflow content based on `$USE_RCODESIGN`:

**If --rcodesign (cross-platform signing on ubuntu runner):**

Create `.github/workflows/jdeploy.yml`:

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

      - name: Install rcodesign
        run: |
          curl -LO https://github.com/indygreg/apple-platform-rs/releases/download/apple-codesign%2F0.29.0/apple-codesign-0.29.0-x86_64-unknown-linux-musl.tar.gz
          tar xzf apple-codesign-0.29.0-x86_64-unknown-linux-musl.tar.gz
          sudo mv apple-codesign-0.29.0-x86_64-unknown-linux-musl/rcodesign /usr/local/bin/
          rcodesign --version

      - name: Set up macOS signing certificate
        run: |
          echo "${{ secrets.MAC_SIGNING_P12_BASE64 }}" | base64 -d > "${{ runner.temp }}/mac-signing.p12"
          echo "JDEPLOY_RCODESIGN_P12_FILE=${{ runner.temp }}/mac-signing.p12" >> $GITHUB_ENV
          echo "JDEPLOY_RCODESIGN_P12_PASSWORD=${{ secrets.MAC_SIGNING_P12_PASSWORD }}" >> $GITHUB_ENV
          echo "JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME=jDeploy Test Mac Signing" >> $GITHUB_ENV

      - name: Build and Deploy Installers
        uses: shannah/jdeploy@$ACTION_REF
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          jdeploy_version: $JDEPLOY_VERSION
```

**If not --rcodesign (native codesign on macOS runner):**

Create `.github/workflows/jdeploy.yml`:

```yaml
name: jDeploy CI

on:
  release:
    types: [created]

permissions:
  contents: write

jobs:
  jdeploy:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Import signing certificate to Keychain
        run: |
          # Create a temporary keychain
          KEYCHAIN_PATH="$RUNNER_TEMP/signing.keychain-db"
          KEYCHAIN_PASSWORD="$(openssl rand -base64 32)"

          security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
          security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
          security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

          # Decode and import certificate
          P12_PATH="$RUNNER_TEMP/mac-signing.p12"
          echo "${{ secrets.MAC_SIGNING_P12_BASE64 }}" | base64 -d > "$P12_PATH"
          security import "$P12_PATH" \
            -k "$KEYCHAIN_PATH" \
            -P "${{ secrets.MAC_SIGNING_P12_PASSWORD }}" \
            -T /usr/bin/codesign

          # Set keychain search list
          security list-keychains -d user -s "$KEYCHAIN_PATH" $(security list-keychains -d user | tr -d '"')

          # Allow codesign to use the key without prompting
          security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

          # Find and export the certificate identity
          CERT_NAME=$(security find-identity -v -p codesigning "$KEYCHAIN_PATH" | grep "jDeploy Test" | head -1 | sed 's/.*"\(.*\)".*/\1/')
          echo "JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME=$CERT_NAME" >> $GITHUB_ENV
          echo "Found certificate identity: $CERT_NAME"

      - name: Build and Deploy Installers
        uses: shannah/jdeploy@$ACTION_REF
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          jdeploy_version: $JDEPLOY_VERSION

      - name: Clean up keychain
        if: always()
        run: |
          security delete-keychain "$RUNNER_TEMP/signing.keychain-db" || true
```

**Important**: Replace `$ACTION_REF` and `$JDEPLOY_VERSION` with the actual values (do not use shell variable syntax in YAML — use the literal values).

### Step 6: Build, Commit, Push, and Release (CI only)

```bash
cd "$WORK_DIR/jdeploy-test-app"
mvn clean package -DskipTests -q
git add -A
git commit -m "Set up test app v$NEXT_VERSION with macOS code signing test (jDeploy $JDEPLOY_VERSION)"
git push origin main

gh release create "v$NEXT_VERSION" \
  --repo shannah/jdeploy-test-app \
  --title "v$NEXT_VERSION" \
  --notes "Test release v$NEXT_VERSION - macOS code signing test with jDeploy $JDEPLOY_VERSION"
```

### Step 7: Report Results

Output a concise summary:

```
Done. Test app v$NEXT_VERSION published with jDeploy $JDEPLOY_VERSION.

**Mode**: Native codesign (macOS) | rcodesign (cross-platform)
**Test type**: Local | CI (GitHub Actions)

**URLs:**
- **Release**: https://github.com/shannah/jdeploy-test-app/releases/tag/v$NEXT_VERSION
- **Actions**: https://github.com/shannah/jdeploy-test-app/actions
```

## What This Tests

The macOS code signing feature:

1. **Certificate handling** - Self-signed P12 certificate is correctly loaded
2. **Native codesign path** - On macOS, `/usr/bin/codesign` signs the `.app` bundle with runtime flag and entitlements
3. **rcodesign fallback path** - On non-macOS (or when forced), `rcodesign sign` produces valid Mach-O code signatures
4. **Entitlements** - The `mac.bundle.entitlements` file is properly applied
5. **Bundle structure** - `Contents/app.xml` is signed individually, then the entire `.app` is signed
6. **Cross-platform bundling** - JRE bundling and JCEF framework copying work on Linux (via `cp -a` / `unzip` instead of `ditto` / `ditto -xk`)

## Environment Variables Reference

### Code Signing Configuration (used by Bundler.java → AppDescription)

| Variable | Purpose | Required For |
|----------|---------|--------------|
| `JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME` | Certificate identity name (e.g., "Developer ID Application: ...") | Both paths |

### rcodesign Configuration (used by RcodesignConfig / RcodesignSigner)

| Variable | Purpose | Required For |
|----------|---------|--------------|
| `JDEPLOY_RCODESIGN_P12_FILE` | Path to PKCS#12 (.p12) certificate file | rcodesign signing |
| `JDEPLOY_RCODESIGN_P12_PASSWORD` | Password to unlock the P12 file | rcodesign signing (optional) |

### Notarization Configuration (NOT tested by this skill)

| Variable | Purpose |
|----------|---------|
| `JDEPLOY_RCODESIGN_API_KEY_PATH` | Path to App Store Connect API key JSON file |
| `JDEPLOY_RCODESIGN_API_ISSUER` | App Store Connect API issuer UUID |
| `JDEPLOY_RCODESIGN_API_KEY` | App Store Connect API key ID |

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `codesign` fails with "no identity found" | Check that the certificate was imported to keychain and is trusted for code signing |
| `rcodesign` not found | Install via `brew install rcodesign` (macOS) or download Linux binary from GitHub releases |
| `rcodesign sign` fails with P12 error | Verify P12 file path and password are correct |
| Signature verification warns "untrusted" | Expected for self-signed certificates — real Developer ID certificates won't have this |
| `isMacCodeSigningEnabled()` returns false | Ensure both `"codesign": true` in package.json AND `JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME` env var are set |
| rcodesign path not triggered on macOS | The code prefers native codesign on macOS; rcodesign is the fallback for non-macOS platforms |
| Bundle files not found after `github-prepare-release` | Check the `jdeploy/` output directory for release files |
