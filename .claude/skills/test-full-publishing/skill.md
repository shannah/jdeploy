# Test Full Publishing with Code Signing

Test the complete jDeploy bundle publishing flow with optional Windows Authenticode and macOS code signing using the scratch repository https://github.com/shannah/jdeploy-test-app.

This skill combines:
- **Bundle publishing** - Native app bundles for all platforms
- **Windows Authenticode signing** - PFX or PKCS#11/HSM signing
- **macOS code signing** - Native codesign or rcodesign

## Usage

```
/test-full-publishing <jdeploy-version> [action-ref] [options]
```

### Options

| Option | Description |
|--------|-------------|
| `--win-sign` | Enable Windows Authenticode signing (PFX by default) |
| `--win-pkcs11` | Enable Windows signing with PKCS#11/SoftHSM |
| `--mac-sign` | Enable macOS code signing (native codesign by default) |
| `--mac-rcodesign` | Enable macOS signing using rcodesign |
| `--local` | Run test locally instead of via GitHub Actions |

### Examples

```bash
# Basic bundle publishing (no signing)
/test-full-publishing 6.1.1

# Bundle publishing with Windows Authenticode signing
/test-full-publishing 6.1.1 --win-sign

# Bundle publishing with macOS code signing
/test-full-publishing 6.1.1 --mac-sign

# Full signing (Windows + macOS)
/test-full-publishing 6.1.1 --win-sign --mac-sign

# With specific action branch
/test-full-publishing 6.1.1 claude/feature-branch --win-sign --mac-sign

# Local test with macOS signing using rcodesign
/test-full-publishing 6.1.1 --local --mac-rcodesign

# Full signing with PKCS#11 for Windows
/test-full-publishing 6.1.1 --win-pkcs11 --mac-sign
```

## Instructions

### Step 1: Parse Arguments

- First argument: `$JDEPLOY_VERSION` - the jDeploy CLI version
- Second argument (optional): `$ACTION_REF` - GitHub Action ref (defaults to `v$JDEPLOY_VERSION`)
- Flags:
  - `--win-sign` → `$WIN_SIGN=true`, `$WIN_PKCS11=false`
  - `--win-pkcs11` → `$WIN_SIGN=true`, `$WIN_PKCS11=true`
  - `--mac-sign` → `$MAC_SIGN=true`, `$MAC_RCODESIGN=false`
  - `--mac-rcodesign` → `$MAC_SIGN=true`, `$MAC_RCODESIGN=true`
  - `--local` → `$LOCAL_TEST=true`

### Step 2: Determine the Next Version

Check the latest GitHub release and bump the patch version:

```bash
gh release list --repo shannah/jdeploy-test-app --limit 1
```

### Step 3: Clone/Update the Test Repository

```bash
WORK_DIR=$(mktemp -d)
cd "$WORK_DIR"
git clone https://github.com/shannah/jdeploy-test-app.git
cd jdeploy-test-app
```

### Step 4: Update Project Files

Update version in `pom.xml`, `package.json`, and `Main.java`.

Configure `package.json` with appropriate settings:

```json
{
  "name": "jdeploy-test-app",
  "version": "$NEXT_VERSION",
  "jdeploy": {
    "jar": "target/jdeploy-test-app-$NEXT_VERSION.jar",
    "title": "jDeploy Test App",
    "javaVersion": "11",
    "codesign": true,
    "macAppBundleId": "com.example.jdeploy-test-app",
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

**Important**: Include `"codesign": true` and `"macAppBundleId"` when `$MAC_SIGN=true`.

### Step 5A: Local Test (if --local)

#### 5A.1: Build jDeploy from Source

```bash
cd /Users/shannah/projects/jdeploy
cd shared && mvn clean install -DskipTests -q
cd ../cli && mvn clean package -DskipTests -q
npm link
```

#### 5A.2: Set Up Signing Certificates

**For Windows signing (`$WIN_SIGN=true`):**

Not applicable for local testing - Windows signing requires CI with signtool or jsign.

**For macOS native codesign (`$MAC_SIGN=true`, `$MAC_RCODESIGN=false`):**

```bash
# Generate self-signed certificate
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

openssl req -x509 -newkey rsa:2048 \
  -keyout /tmp/jdeploy-test-key.pem \
  -out /tmp/jdeploy-test-cert.pem \
  -days 1 -nodes \
  -config /tmp/jdeploy-test-cert.cfg

openssl pkcs12 -export \
  -out /tmp/jdeploy-test.p12 \
  -inkey /tmp/jdeploy-test-key.pem \
  -in /tmp/jdeploy-test-cert.pem \
  -passout pass:test123 -legacy

security import /tmp/jdeploy-test.p12 \
  -k ~/Library/Keychains/login.keychain-db \
  -P test123 \
  -T /usr/bin/codesign

CERT_NAME=$(security find-identity -v -p codesigning | grep "jDeploy Test Signing" | head -1 | sed 's/.*"\(.*\)".*/\1/')
export JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME="$CERT_NAME"
```

**For macOS rcodesign (`$MAC_RCODESIGN=true`):**

```bash
# Generate P12 for rcodesign
openssl req -x509 -newkey rsa:2048 \
  -keyout /tmp/jdeploy-test-key.pem \
  -out /tmp/jdeploy-test-cert.pem \
  -days 1 -nodes \
  -subj "/CN=jDeploy Test Signing/O=jDeploy Test/C=US"

openssl pkcs12 -export \
  -out /tmp/jdeploy-test.p12 \
  -inkey /tmp/jdeploy-test-key.pem \
  -in /tmp/jdeploy-test-cert.pem \
  -passout pass:test123 -legacy

export JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME="jDeploy Test Signing"
export JDEPLOY_RCODESIGN_P12_FILE=/tmp/jdeploy-test.p12
export JDEPLOY_RCODESIGN_P12_PASSWORD=test123
export JDEPLOY_FORCE_RCODESIGN=true
```

#### 5A.3: Build and Run

```bash
cd "$WORK_DIR/jdeploy-test-app"
mvn clean package -DskipTests -q
jdeploy github-prepare-release
```

#### 5A.4: Verify Signatures

**macOS signature verification:**

```bash
APP_BUNDLE=$(find "$WORK_DIR/jdeploy-test-app/jdeploy" -name "*.app" -type d | head -1)
codesign --verify --verbose=4 "$APP_BUNDLE"
codesign --display --verbose=4 "$APP_BUNDLE"
```

Skip to Step 7.

### Step 5B: CI Test Setup (if not --local)

#### 5B.1: Set Up GitHub Secrets

**For Windows signing (`$WIN_SIGN=true`, `$WIN_PKCS11=false`):**

```bash
cd /tmp
openssl req -x509 -newkey rsa:2048 \
  -keyout test-signing-key.pem \
  -out test-signing-cert.pem \
  -days 365 -nodes \
  -subj "/CN=jDeploy Test Signing/O=jDeploy Test/C=US"

openssl pkcs12 -export \
  -out test-signing.pfx \
  -inkey test-signing-key.pem \
  -in test-signing-cert.pem \
  -passout pass:test123

base64 -i test-signing.pfx | tr -d '\n' > test-signing-base64.txt
gh secret set WIN_SIGNING_CERT_BASE64 --repo shannah/jdeploy-test-app < test-signing-base64.txt
gh secret set WIN_SIGNING_PASSWORD --repo shannah/jdeploy-test-app --body "test123"
```

**For macOS signing (`$MAC_SIGN=true`):**

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

Generate `.github/workflows/jdeploy.yml` based on the combination of options:

**Base workflow structure:**

```yaml
name: jDeploy CI

on:
  release:
    types: [created]

permissions:
  contents: write

jobs:
  jdeploy:
    runs-on: $RUNNER
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      # === INSERT SIGNING SETUP STEPS HERE ===

      - name: Build and Deploy Installers
        uses: shannah/jdeploy@$ACTION_REF
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          jdeploy_version: $JDEPLOY_VERSION
          # === INSERT SIGNING ACTION INPUTS HERE ===
```

**Runner selection (`$RUNNER`):**
- If `$MAC_SIGN=true` and `$MAC_RCODESIGN=false`: `macos-latest` (native codesign)
- Otherwise: `ubuntu-latest`

**Windows PFX signing steps (if `$WIN_SIGN=true`, `$WIN_PKCS11=false`):**

Add to action inputs:
```yaml
          win_signing_certificate: ${{ secrets.WIN_SIGNING_CERT_BASE64 }}
          win_signing_password: ${{ secrets.WIN_SIGNING_PASSWORD }}
          win_signing_description: "jDeploy Test App"
          win_signing_url: "https://github.com/shannah/jdeploy-test-app"
```

**Windows PKCS#11 signing steps (if `$WIN_PKCS11=true`):**

Add setup step:
```yaml
      - name: Install SoftHSM2
        run: |
          sudo apt-get update
          sudo apt-get install -y softhsm2 opensc

      - name: Initialize SoftHSM token and import signing key
        run: |
          mkdir -p $HOME/softhsm/tokens
          cat > $HOME/softhsm/softhsm2.conf << EOF
          directories.tokendir = $HOME/softhsm/tokens
          objectstore.backend = file
          log.level = INFO
          EOF

          export SOFTHSM2_CONF=$HOME/softhsm/softhsm2.conf
          softhsm2-util --init-token --slot 0 --label "jdeploy-test" --pin 1234 --so-pin 5678

          openssl req -x509 -newkey rsa:2048 \
            -keyout $HOME/softhsm/signing-key.pem \
            -out $HOME/softhsm/signing-cert.pem \
            -days 365 -nodes \
            -subj "/CN=jDeploy Test PKCS11/O=jDeploy Test/C=US"

          openssl rsa -in $HOME/softhsm/signing-key.pem -outform DER -out $HOME/softhsm/signing-key.der
          openssl x509 -in $HOME/softhsm/signing-cert.pem -outform DER -out $HOME/softhsm/signing-cert.der

          pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
            --login --pin 1234 \
            --write-object $HOME/softhsm/signing-key.der \
            --type privkey --id 01 --label "signing-key"

          pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
            --login --pin 1234 \
            --write-object $HOME/softhsm/signing-cert.der \
            --type cert --id 01 --label "signing-key"

          cat > $HOME/softhsm/pkcs11.cfg << EOF
          name = SoftHSM
          library = /usr/lib/softhsm/libsofthsm2.so
          slotListIndex = 0
          EOF

          echo "PKCS11_CONFIG=$HOME/softhsm/pkcs11.cfg" >> $GITHUB_ENV
          echo "SOFTHSM2_CONF=$HOME/softhsm/softhsm2.conf" >> $GITHUB_ENV
```

Add to action inputs:
```yaml
          win_signing_keystore_type: PKCS11
          win_signing_pkcs11_config: ${{ env.PKCS11_CONFIG }}
          win_signing_password: "1234"
          win_signing_key_alias: "signing-key"
          win_signing_description: "jDeploy Test App (PKCS#11)"
          win_signing_url: "https://github.com/shannah/jdeploy-test-app"
        env:
          SOFTHSM2_CONF: ${{ env.SOFTHSM2_CONF }}
```

**macOS native codesign steps (if `$MAC_SIGN=true`, `$MAC_RCODESIGN=false`):**

Add setup step:
```yaml
      - name: Import signing certificate to Keychain
        run: |
          KEYCHAIN_PATH="$RUNNER_TEMP/signing.keychain-db"
          KEYCHAIN_PASSWORD="$(openssl rand -base64 32)"

          security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
          security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
          security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

          P12_PATH="$RUNNER_TEMP/mac-signing.p12"
          echo "${{ secrets.MAC_SIGNING_P12_BASE64 }}" | base64 -d > "$P12_PATH"
          security import "$P12_PATH" \
            -k "$KEYCHAIN_PATH" \
            -P "${{ secrets.MAC_SIGNING_P12_PASSWORD }}" \
            -T /usr/bin/codesign

          security list-keychains -d user -s "$KEYCHAIN_PATH" $(security list-keychains -d user | tr -d '"')
          security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

          CERT_NAME=$(security find-identity -v -p codesigning "$KEYCHAIN_PATH" | grep "jDeploy Test" | head -1 | sed 's/.*"\(.*\)".*/\1/')
          echo "JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME=$CERT_NAME" >> $GITHUB_ENV

      - name: Clean up keychain
        if: always()
        run: |
          security delete-keychain "$RUNNER_TEMP/signing.keychain-db" || true
```

**macOS rcodesign steps (if `$MAC_RCODESIGN=true`):**

Add setup step:
```yaml
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
```

### Step 6: Build, Commit, Push, and Release (CI only)

```bash
cd "$WORK_DIR/jdeploy-test-app"
mvn clean package -DskipTests -q
git add -A
git commit -m "Set up test app v$NEXT_VERSION with jDeploy $JDEPLOY_VERSION"
git push origin main

gh release create "v$NEXT_VERSION" \
  --repo shannah/jdeploy-test-app \
  --title "v$NEXT_VERSION" \
  --notes "Test release v$NEXT_VERSION - Publishing test with jDeploy $JDEPLOY_VERSION"
```

### Step 7: Report Results

Output a concise summary:

```
Done. Test app v$NEXT_VERSION published with jDeploy $JDEPLOY_VERSION.

**Configuration:**
- Windows signing: Disabled | PFX | PKCS#11
- macOS signing: Disabled | Native codesign | rcodesign
- Test type: Local | CI (GitHub Actions)

**URLs:**
- **Release**: https://github.com/shannah/jdeploy-test-app/releases/tag/v$NEXT_VERSION
- **Actions**: https://github.com/shannah/jdeploy-test-app/actions
```

## What This Tests

This combined skill tests:

1. **Bundle Publishing** - Native app bundles for all platforms (mac-arm64, mac-x64, win-x64, win-arm64, linux-x64, linux-arm64)
2. **Windows Authenticode** - PFX or PKCS#11/HSM signing via jsign
3. **macOS Code Signing** - Native codesign or cross-platform rcodesign

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Windows signing fails | Check WIN_SIGNING_CERT_BASE64 secret is set correctly |
| macOS native codesign fails | Ensure running on macOS runner and keychain is properly configured |
| rcodesign not found | Install via CI step or ensure binary is on PATH |
| Signature shows "untrusted" | Expected for self-signed certificates |
| `isMacCodeSigningEnabled()` returns false | Both `codesign: true` and `JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME` must be set |
| rcodesign not triggered on macOS | Set `JDEPLOY_FORCE_RCODESIGN=true` to force rcodesign path |

## Related Skills

- `/test-app-publishing` - Basic npm/GitHub publishing (no signing)
- `/test-bundle-publishing` - Bundle publishing only (no signing)
- `/test-authenticode-signing` - Windows signing only
- `/test-mac-codesigning` - macOS signing only
