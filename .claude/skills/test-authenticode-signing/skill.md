# Test Windows Authenticode Signing

Test the jDeploy Windows Authenticode signing feature using the scratch repository https://github.com/shannah/jdeploy-test-app.

This skill tests the Windows code signing feature which signs `.exe` bundles with Authenticode signatures during packaging.

## Usage

```
/test-authenticode-signing <jdeploy-version> [action-ref]
```

Examples:
```
/test-authenticode-signing 6.1.0-dev.12
/test-authenticode-signing 6.1.0-dev.12 claude/add-authenticode-signing-R6YwK
```

- `jdeploy-version`: The jDeploy CLI version to use (e.g., `6.1.0-dev.12`)
- `action-ref` (optional): GitHub Action ref to use instead of the version tag. If not provided, uses `v<jdeploy-version>` as the action ref.

## Instructions

### Step 1: Parse Arguments

- First argument: `$JDEPLOY_VERSION` - the jDeploy CLI version
- Second argument (optional): `$ACTION_REF` - the GitHub Action ref
  - If not provided, default to `v$JDEPLOY_VERSION`
  - If provided, use it directly (e.g., `claude/add-authenticode-signing-R6YwK` for a branch)

### Step 2: Generate Self-Signed Code Signing Certificate

Generate a test certificate for signing:

```bash
cd /tmp
openssl req -x509 -newkey rsa:2048 \
  -keyout test-signing-key.pem \
  -out test-signing-cert.pem \
  -days 365 -nodes \
  -subj "/CN=jDeploy Test Signing/O=jDeploy Test/C=US" 2>/dev/null

openssl pkcs12 -export \
  -out test-signing.pfx \
  -inkey test-signing-key.pem \
  -in test-signing-cert.pem \
  -passout pass:test123
```

**Note**: Do NOT use the `-legacy` flag. The updated jsign dependency handles OpenSSL 3.x certificates correctly.

### Step 3: Update GitHub Secrets

Base64 encode the certificate and set as GitHub secrets:

```bash
base64 -i /tmp/test-signing.pfx | tr -d '\n' > /tmp/test-signing-base64.txt
gh secret set WIN_SIGNING_CERT_BASE64 --repo shannah/jdeploy-test-app < /tmp/test-signing-base64.txt
gh secret set WIN_SIGNING_PASSWORD --repo shannah/jdeploy-test-app --body "test123"
```

### Step 4: Determine the Next Version

Check the latest GitHub release and bump the patch version:

```bash
gh release list --repo shannah/jdeploy-test-app --limit 1
```

### Step 5: Clone/Update the Test Repository

```bash
WORK_DIR=$(mktemp -d)
cd "$WORK_DIR"
git clone https://github.com/shannah/jdeploy-test-app.git
cd jdeploy-test-app
```

### Step 6: Update Project Files

Update the version in:
- `pom.xml`
- `package.json` (version and jar path)
- `Main.java` (version strings in UI)

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

### Step 7: Update GitHub Workflow

Update `.github/workflows/jdeploy.yml`:

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

### Step 8: Build, Commit, Push, and Release

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

### Step 9: Report Results

Output a concise summary:

```
Done. Test app v$NEXT_VERSION published with jDeploy $JDEPLOY_VERSION.

**URLs:**
- **Release**: https://github.com/shannah/jdeploy-test-app/releases/tag/v$NEXT_VERSION
- **Actions**: https://github.com/shannah/jdeploy-test-app/actions
```

## What This Tests

The Windows Authenticode signing feature (`rfc/windows-authenticode-signing.md`):

1. **Certificate handling** - The action decodes the base64 certificate and sets up environment variables
2. **Signing with jsign** - Windows `.exe` bundles are signed using the jsign library
3. **Signature embedding** - The Authenticode signature is properly embedded in the executable

## Verifying the Signature

After installation on Windows, verify the signature:

```powershell
$sig = Get-AuthenticodeSignature "C:\Users\<user>\.jdeploy\apps\<app-id>\<AppName>.exe"
Write-Host "Status: $($sig.Status)"
Write-Host "Signer: $($sig.SignerCertificate.Subject)"
```

Expected output:
- Status: `Valid` (or `UnknownError` for self-signed, but SignerCertificate should NOT be null)
- Signer: `CN=jDeploy Test Signing, O=jDeploy Test, C=US`

Or right-click the `.exe` → Properties → Digital Signatures tab.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `SignerCertificate is null` | Update jsign dependency - older versions had issues with OpenSSL 3.x certificates |
| Signature shows "untrusted" | Expected for self-signed certificates - real CA certificates won't have this issue |
| No signature present | Check GitHub Actions log for signing errors |
