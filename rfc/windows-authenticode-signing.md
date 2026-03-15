# Windows Authenticode Signing

## Overview

jDeploy supports signing Windows `.exe` bundles with Authenticode signatures during the packaging step. When signing is configured via environment variables, all Windows bundles (x64 and ARM64) are automatically signed before release artifacts are created.

Signing uses the [jsign](https://ebourg.github.io/jsign/) library and supports local keystores (PFX/PKCS12, JKS) as well as hardware security modules via PKCS#11.

## Motivation

Unsigned Windows executables trigger SmartScreen warnings and may be flagged or blocked by antivirus software. Authenticode signing establishes publisher identity and reduces these warnings, improving the end-user installation experience.

## Configuration

Signing is configured entirely through environment variables. No changes to `package.json` are required.

### Required Variables

For **local keystore** signing (PFX/PKCS12 or JKS):

| Variable | Description |
|---|---|
| `JDEPLOY_WIN_KEYSTORE_PATH` | Path to the keystore file (`.pfx`, `.p12`, or `.jks`) |
| `JDEPLOY_WIN_KEYSTORE_PASSWORD` | Password for the keystore |

For **PKCS#11 / HSM** signing (SafeNet, YubiKey, etc.):

| Variable | Description |
|---|---|
| `JDEPLOY_WIN_KEYSTORE_TYPE` | Must be set to `PKCS11` |
| `JDEPLOY_WIN_PKCS11_CONFIG` | Path to the PKCS#11 provider configuration file |
| `JDEPLOY_WIN_KEYSTORE_PASSWORD` | PIN or password for the token |

### Optional Variables

| Variable | Default | Description |
|---|---|---|
| `JDEPLOY_WIN_KEYSTORE_TYPE` | `PKCS12` | Keystore type: `PKCS12`, `JKS`, or `PKCS11` |
| `JDEPLOY_WIN_KEY_ALIAS` | First alias in keystore | Alias of the signing key within the keystore |
| `JDEPLOY_WIN_KEY_PASSWORD` | Same as keystore password | Password for the private key (if different from keystore password) |
| `JDEPLOY_WIN_TIMESTAMP_URL` | `http://timestamp.digicert.com` | RFC 3161 timestamp server URL |
| `JDEPLOY_WIN_HASH_ALGORITHM` | `SHA-256` | Hash algorithm for the signature |
| `JDEPLOY_WIN_SIGN_DESCRIPTION` | *(none)* | Description embedded in the Authenticode signature |
| `JDEPLOY_WIN_SIGN_URL` | *(none)* | URL embedded in the Authenticode signature |

## Behavior

### When signing is enabled

1. After each Windows bundle (x64 or ARM64) is created, jDeploy checks for signing configuration via environment variables.
2. If configuration is present, the `.exe` file inside the bundle is signed in-place using the jsign library.
3. Any release `.zip` files are then regenerated to include the signed executable.

### When signing is not configured

- If neither `JDEPLOY_WIN_KEYSTORE_PATH` (for local keystores) nor `JDEPLOY_WIN_PKCS11_CONFIG` + `JDEPLOY_WIN_KEYSTORE_TYPE=PKCS11` (for HSM) are set, signing is silently skipped.
- No error is raised and packaging proceeds normally with unsigned executables.

### Platform scope

- Only Windows bundles (`win` and `win-arm64`) are signed.
- macOS and Linux bundles are unaffected.

## Usage Examples

### Local PFX keystore

```bash
export JDEPLOY_WIN_KEYSTORE_PATH="/path/to/certificate.pfx"
export JDEPLOY_WIN_KEYSTORE_PASSWORD="my-password"
jdeploy publish
```

### Local PFX with explicit alias and description

```bash
export JDEPLOY_WIN_KEYSTORE_PATH="/path/to/certificate.pfx"
export JDEPLOY_WIN_KEYSTORE_PASSWORD="my-password"
export JDEPLOY_WIN_KEY_ALIAS="my-cert-alias"
export JDEPLOY_WIN_SIGN_DESCRIPTION="My Application"
export JDEPLOY_WIN_SIGN_URL="https://example.com"
jdeploy publish
```

### PKCS#11 HSM token

```bash
export JDEPLOY_WIN_KEYSTORE_TYPE="PKCS11"
export JDEPLOY_WIN_PKCS11_CONFIG="/path/to/pkcs11.cfg"
export JDEPLOY_WIN_KEYSTORE_PASSWORD="token-pin"
jdeploy publish
```

### GitHub Actions (manual environment setup)

```yaml
- name: Publish with code signing
  env:
    JDEPLOY_WIN_KEYSTORE_PATH: ${{ runner.temp }}/certificate.pfx
    JDEPLOY_WIN_KEYSTORE_PASSWORD: ${{ secrets.WIN_SIGNING_PASSWORD }}
  run: |
    echo "${{ secrets.WIN_SIGNING_CERT_BASE64 }}" | base64 -d > ${{ runner.temp }}/certificate.pfx
    npx jdeploy publish
```

## GitHub Action Integration

The jDeploy GitHub Action (`action.yml`) has built-in support for Windows Authenticode signing. The action handles certificate decoding, environment variable setup, and certificate cleanup automatically.

### Action Inputs

| Input | Required | Description |
|---|---|---|
| `win_signing_certificate` | No | Base64-encoded PFX/PKCS12 certificate. The action decodes this to a temp file and sets `JDEPLOY_WIN_KEYSTORE_PATH` automatically. |
| `win_signing_password` | No | Password/PIN for the keystore or PKCS#11 token. Maps to `JDEPLOY_WIN_KEYSTORE_PASSWORD`. |
| `win_signing_keystore_type` | No | Keystore type: `PKCS12` (default), `JKS`, or `PKCS11`. Maps to `JDEPLOY_WIN_KEYSTORE_TYPE`. |
| `win_signing_pkcs11_config` | No | Path to a PKCS#11 provider config file on the runner (for HSM signing). Maps to `JDEPLOY_WIN_PKCS11_CONFIG`. |
| `win_signing_key_alias` | No | Alias of the signing key. Maps to `JDEPLOY_WIN_KEY_ALIAS`. |
| `win_signing_key_password` | No | Private key password (if different from keystore password). Maps to `JDEPLOY_WIN_KEY_PASSWORD`. |
| `win_signing_timestamp_url` | No | RFC 3161 timestamp server URL. Maps to `JDEPLOY_WIN_TIMESTAMP_URL`. |
| `win_signing_hash_algorithm` | No | Hash algorithm (e.g. `SHA-256`). Maps to `JDEPLOY_WIN_HASH_ALGORITHM`. |
| `win_signing_description` | No | Description embedded in the signature. Maps to `JDEPLOY_WIN_SIGN_DESCRIPTION`. |
| `win_signing_url` | No | URL embedded in the signature. Maps to `JDEPLOY_WIN_SIGN_URL`. |

Signing is activated when either `win_signing_certificate` or `win_signing_pkcs11_config` is provided. If neither is set, signing is skipped and executables are left unsigned.

### Local PFX certificate

Store the base64-encoded certificate and its password as repository secrets, then pass them as action inputs:

```yaml
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: shannah/jdeploy@main
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          win_signing_certificate: ${{ secrets.WIN_SIGNING_CERT_BASE64 }}
          win_signing_password: ${{ secrets.WIN_SIGNING_PASSWORD }}
```

To create the base64 secret from a `.pfx` file:

```bash
base64 -w 0 certificate.pfx | pbcopy   # macOS
base64 -w 0 certificate.pfx | xclip    # Linux
```

### Local PFX with optional parameters

```yaml
      - uses: shannah/jdeploy@main
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          win_signing_certificate: ${{ secrets.WIN_SIGNING_CERT_BASE64 }}
          win_signing_password: ${{ secrets.WIN_SIGNING_PASSWORD }}
          win_signing_key_alias: my-cert-alias
          win_signing_description: "My Application"
          win_signing_url: "https://example.com"
```

### PKCS#11 / HSM signing

For hardware security module signing (SafeNet, YubiKey, etc.), use a self-hosted runner with the HSM client and PKCS#11 provider library installed:

```yaml
jobs:
  publish:
    runs-on: self-hosted   # Runner with HSM client/driver installed
    steps:
      - uses: actions/checkout@v4

      - uses: shannah/jdeploy@main
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          win_signing_keystore_type: PKCS11
          win_signing_pkcs11_config: /opt/safenet/pkcs11.cfg
          win_signing_password: ${{ secrets.HSM_TOKEN_PIN }}
          win_signing_key_alias: my-signing-key
```

The `win_signing_pkcs11_config` input must point to a PKCS#11 provider configuration file on the runner. A typical config file looks like:

```
name = SafeNet
library = /opt/safenet/lib/libCryptoki2_64.so
```

### Security notes

- Store certificates and passwords as [encrypted secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets). Never commit them to the repository.
- The action automatically cleans up the decoded certificate file after the workflow completes (including on failure).
- The certificate file is written to `${{ runner.temp }}`, which is ephemeral to the workflow run.

## Validation

The signing configuration is validated before signing begins:

- For local keystores (`PKCS12` / `JKS`): `JDEPLOY_WIN_KEYSTORE_PATH` must be set and non-empty.
- For PKCS#11: `JDEPLOY_WIN_PKCS11_CONFIG` must be set and non-empty.
- If no key alias is provided, the first alias in the keystore is used automatically.
- If no key password is provided, the keystore password is used.

## Implementation Details

- **Library**: [jsign-core 7.0](https://ebourg.github.io/jsign/) (added as a Maven dependency to the `cli` module)
- **Entry point**: `PackageService.signWindowsExeIfConfigured()` is called after `windowsX64Bundle()` and `windowsArm64Bundle()`
- **Key classes**:
  - `WindowsSigningConfig` — configuration POJO with validation
  - `WindowsSigningConfigFactory` — reads environment variables into a `WindowsSigningConfig`
  - `WindowsSigningService` — performs the actual Authenticode signing via jsign

## Version

Introduced in jDeploy via PR #431.
