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

### GitHub Actions

```yaml
- name: Publish with code signing
  env:
    JDEPLOY_WIN_KEYSTORE_PATH: ${{ runner.temp }}/certificate.pfx
    JDEPLOY_WIN_KEYSTORE_PASSWORD: ${{ secrets.WIN_SIGNING_PASSWORD }}
  run: |
    echo "${{ secrets.WIN_SIGNING_CERT_BASE64 }}" | base64 -d > ${{ runner.temp }}/certificate.pfx
    npx jdeploy publish
```

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
