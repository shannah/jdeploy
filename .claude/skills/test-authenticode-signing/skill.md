# Test Windows Authenticode Signing

Test the jDeploy Windows Authenticode signing feature using the scratch repository https://github.com/shannah/jdeploy-test-app.

This skill tests the Windows code signing feature which signs `.exe` bundles with Authenticode signatures during packaging.

## Usage

```
/test-authenticode-signing <jdeploy-version> [action-ref] [--pkcs11]
```

Examples:
```
# PFX signing (default)
/test-authenticode-signing 6.1.0-dev.12

# PFX signing with specific action branch
/test-authenticode-signing 6.1.0-dev.12 claude/add-authenticode-signing-R6YwK

# PKCS#11/HSM signing with SoftHSM
/test-authenticode-signing 6.1.0-dev.12 --pkcs11
/test-authenticode-signing 6.1.0-dev.12 claude/add-authenticode-signing-R6YwK --pkcs11
```

- `jdeploy-version`: The jDeploy CLI version to use (e.g., `6.1.0-dev.12`)
- `action-ref` (optional): GitHub Action ref to use instead of the version tag. If not provided, uses `v<jdeploy-version>` as the action ref.
- `--pkcs11` (optional): Use PKCS#11/SoftHSM signing instead of PFX signing

## Instructions

### Step 1: Parse Arguments

- First argument: `$JDEPLOY_VERSION` - the jDeploy CLI version
- Second argument (optional): `$ACTION_REF` - the GitHub Action ref (or `--pkcs11` flag)
  - If not provided, default to `v$JDEPLOY_VERSION`
  - If `--pkcs11`, set `$USE_PKCS11=true`
- Check for `--pkcs11` flag in any position to set `$USE_PKCS11=true`

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

Update the version in:
- `pom.xml`
- `package.json` (version and jar path)
- `Main.java` (version strings in UI - update label to indicate PFX or PKCS#11 test)

Ensure `package.json` includes the artifacts configuration for bundle publishing:

```json
{
  "jdeploy": {
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

### Step 5A: PFX Signing Setup (if not --pkcs11)

Generate a self-signed certificate and set as GitHub secrets:

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

**Note**: Do NOT use the `-legacy` flag. The updated jsign dependency handles OpenSSL 3.x certificates correctly.

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

      - name: Build and Deploy Installers
        uses: shannah/jdeploy@$ACTION_REF
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          jdeploy_version: $JDEPLOY_VERSION
          win_signing_certificate: ${{ secrets.WIN_SIGNING_CERT_BASE64 }}
          win_signing_password: ${{ secrets.WIN_SIGNING_PASSWORD }}
          win_signing_description: "jDeploy Test App"
          win_signing_url: "https://github.com/shannah/jdeploy-test-app"
```

### Step 5B: PKCS#11/SoftHSM Signing Setup (if --pkcs11)

Create `.github/workflows/jdeploy.yml` with SoftHSM setup:

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

      - name: Install SoftHSM2
        run: |
          sudo apt-get update
          sudo apt-get install -y softhsm2 opensc

      - name: Initialize SoftHSM token and import signing key
        run: |
          # Create SoftHSM directories
          mkdir -p $HOME/softhsm/tokens

          # Create SoftHSM config with actual path
          cat > $HOME/softhsm/softhsm2.conf << EOF
          directories.tokendir = $HOME/softhsm/tokens
          objectstore.backend = file
          log.level = INFO
          EOF

          export SOFTHSM2_CONF=$HOME/softhsm/softhsm2.conf

          # Initialize the token
          softhsm2-util --init-token --slot 0 --label "jdeploy-test" --pin 1234 --so-pin 5678

          # Generate a key pair AND self-signed certificate with OpenSSL
          openssl req -x509 -newkey rsa:2048 \
            -keyout $HOME/softhsm/signing-key.pem \
            -out $HOME/softhsm/signing-cert.pem \
            -days 365 -nodes \
            -subj "/CN=jDeploy Test PKCS11/O=jDeploy Test/C=US"

          # Convert private key to DER
          openssl rsa -in $HOME/softhsm/signing-key.pem -outform DER -out $HOME/softhsm/signing-key.der

          # Convert certificate to DER
          openssl x509 -in $HOME/softhsm/signing-cert.pem -outform DER -out $HOME/softhsm/signing-cert.der

          # Import private key (must use same ID for key and cert to associate them)
          pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
            --login --pin 1234 \
            --write-object $HOME/softhsm/signing-key.der \
            --type privkey --id 01 --label "signing-key"

          # Import certificate
          pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
            --login --pin 1234 \
            --write-object $HOME/softhsm/signing-cert.der \
            --type cert --id 01 --label "signing-key"

          # List objects to verify
          echo "=== Objects in token ==="
          pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
            --login --pin 1234 \
            --list-objects

          # Create PKCS#11 config file for jsign
          cat > $HOME/softhsm/pkcs11.cfg << EOF
          name = SoftHSM
          library = /usr/lib/softhsm/libsofthsm2.so
          slotListIndex = 0
          EOF

          echo "PKCS11_CONFIG=$HOME/softhsm/pkcs11.cfg" >> $GITHUB_ENV
          echo "SOFTHSM2_CONF=$HOME/softhsm/softhsm2.conf" >> $GITHUB_ENV

      - name: Build and Deploy Installers
        uses: shannah/jdeploy@$ACTION_REF
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          jdeploy_version: $JDEPLOY_VERSION
          win_signing_keystore_type: PKCS11
          win_signing_pkcs11_config: ${{ env.PKCS11_CONFIG }}
          win_signing_password: "1234"
          win_signing_key_alias: "signing-key"
          win_signing_description: "jDeploy Test App (PKCS#11)"
          win_signing_url: "https://github.com/shannah/jdeploy-test-app"
        env:
          SOFTHSM2_CONF: ${{ env.SOFTHSM2_CONF }}
```

**Important PKCS#11 notes:**
- The private key and certificate must be imported with the **same ID** (`--id 01`) to associate them
- Generate the key pair and certificate together with OpenSSL so they match
- Do NOT generate a key in the HSM and then import a separately-generated certificate

### Step 6: Build, Commit, Push, and Release

```bash
mvn clean package -DskipTests -q
git add -A
git commit -m "Set up test app v$NEXT_VERSION with Authenticode signing test (jDeploy $JDEPLOY_VERSION)"
git push origin main

gh release create "v$NEXT_VERSION" \
  --repo shannah/jdeploy-test-app \
  --title "v$NEXT_VERSION" \
  --notes "Test release v$NEXT_VERSION - Authenticode signing test with jDeploy $JDEPLOY_VERSION"
```

### Step 7: Report Results

Output a concise summary:

```
Done. Test app v$NEXT_VERSION published with jDeploy $JDEPLOY_VERSION.

**Mode**: PFX signing | PKCS#11/SoftHSM signing

**URLs:**
- **Release**: https://github.com/shannah/jdeploy-test-app/releases/tag/v$NEXT_VERSION
- **Actions**: https://github.com/shannah/jdeploy-test-app/actions
```

## What This Tests

The Windows Authenticode signing feature (`rfc/windows-authenticode-signing.md`):

1. **Certificate handling** - The action decodes certificates or connects to PKCS#11 provider
2. **Signing with jsign** - Windows `.exe` bundles are signed using the jsign library
3. **Signature embedding** - The Authenticode signature is properly embedded in the executable
4. **Timestamping** - The signature is timestamped via DigiCert's timestamp server

## Verifying the Signature

After installation on Windows, verify the signature:

```powershell
# Using signtool (Windows SDK)
& "C:\Program Files (x86)\Windows Kits\10\bin\10.0.22621.0\x64\signtool.exe" verify /v /pa "C:\Users\<user>\.jdeploy\apps\<app-id>\<AppName>.exe"

# Or using PowerShell
$sig = Get-AuthenticodeSignature "C:\Users\<user>\.jdeploy\apps\<app-id>\<AppName>.exe"
Write-Host "Status: $($sig.Status)"
Write-Host "Signer: $($sig.SignerCertificate.Subject)"
```

Expected output for self-signed certificates:
- Signature is present and valid
- Signer shows the certificate subject (e.g., `CN=jDeploy Test Signing` or `CN=jDeploy Test PKCS11`)
- Timestamp is present (from DigiCert)
- Error about "root certificate not trusted" - this is expected for self-signed test certificates

Or right-click the `.exe` → Properties → Digital Signatures tab.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `SignerCertificate is null` | Update jsign dependency - older versions had issues with OpenSSL 3.x certificates |
| `No signature found` | Check GitHub Actions log for signing errors |
| Signature shows "untrusted" | Expected for self-signed certificates - real CA certificates won't have this issue |
| PKCS#11 key mismatch | Ensure private key and certificate are generated together and imported with same ID |
| SoftHSM config error | Use `$HOME` not `$ENV:HOME` in config paths |
