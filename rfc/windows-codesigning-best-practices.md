# Windows Code Signing & Certificate Pinning — Best Practices

## Audience

App developers using jDeploy who want to publish trustworthy Windows builds **without paying for an OV/EV code-signing certificate** ($100+/month for HSM-hosted DigiCert / SSL.com EV certs).

This guide describes the recommended end-to-end setup using a **self-managed two-tier PKI**: an offline root certificate plus a working code-signing certificate. The same certificate is used both for Windows Authenticode signing of the `.exe` and for jDeploy bundle signing (certificate pinning at launch). A future update will add **publisher website verification** so end-users can confirm the certificate belongs to a domain or GitHub repo they recognise — see `website-publisher-verification-plan.md`.

> **Note on trust model.** A self-signed cert will never be silently trusted by Windows the way a DigiCert/SSL.com cert is — SmartScreen reputation is gated on commercial CAs. What this setup buys you is: (a) **tamper detection** at launch via certificate pinning, (b) **publisher continuity** across versions (the same key signs every release), and (c) **a path to user-verifiable identity** via the install-time trust prompt and (soon) the companion-cert website verification.

## The Two-Tier Setup

```
   ┌────────────────────────┐
   │  Root CA Certificate   │   ← offline, vault, USB stick, password manager
   │  (e.g. 20-year self-   │     Used ONLY to issue / re-issue codesign certs.
   │  signed RSA-3072)      │     Public cert is what gets PINNED in bundles.
   └───────────┬────────────┘
               │ signs
               ▼
   ┌────────────────────────┐
   │ Code-Signing Cert      │   ← lives in GitHub Actions secrets / HSM / dev box
   │ (e.g. 2-year, RSA-3072)│     Used to sign every .exe and every jdeploy-bundle.
   └────────────────────────┘
```

**Why two tiers?** Code-signing keys are exposed every time you run a release — on a CI runner, a developer laptop, or an HSM. They will eventually be lost, leaked, or expire. The root key never touches a build machine, so:

- You can **rotate** the codesign cert without invalidating every shipped app.
- Users that previously trusted your root continue to trust new releases.
- Bundles signed years apart all chain back to the same pinned root, so jDeploy's verifier accepts them.

**What gets pinned?** The launcher's `trusted-certificates` (in `app.xml`) should contain **the root cert**, not the codesign cert. The chain is verified at launch via `SimpleCertificateVerifier` (PKIX) — both the codesign cert (leaf) and any intermediates are loaded from `jdeploy.cer` inside the bundle.

---

## One-Time Setup: Generate the Root and Codesign Certificates

You need OpenSSL. Pick somewhere safe to do this — ideally an offline machine. Everything below runs locally; nothing is uploaded.

### 1. Create the root key + self-signed root cert

```bash
mkdir -p ~/jdeploy-pki && cd ~/jdeploy-pki

# 3072-bit RSA root key, encrypted with a strong passphrase
openssl genrsa -aes256 -out root.key 3072

# 20-year self-signed root, marked as a CA
openssl req -x509 -new -key root.key -sha256 -days 7300 \
    -out root.crt \
    -subj "/CN=Acme Corp Root CA/O=Acme Corp/C=US" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"
```

Burn `root.key` + the passphrase to two USB sticks. Keep one in a safe, give one to a co-founder. **You will not need this key again unless you rotate the codesign cert.**

### 2. Create the code-signing CSR + key

```bash
openssl genrsa -out codesign.key 3072

openssl req -new -key codesign.key -out codesign.csr \
    -subj "/CN=Acme Corp Code Signing/O=Acme Corp/C=US"
```

### 3. Sign the codesign CSR with the root

Save this as `codesign.ext`:

```
basicConstraints = critical, CA:FALSE
keyUsage         = critical, digitalSignature
extendedKeyUsage = codeSigning
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid:always
```

> **Optional — issuing-CA codesign cert.** If you also want to be able to issue **publisher identity certs** (see `website-publisher-verification-plan.md`) without unlocking the offline root every time, instead use:
>
> ```
> basicConstraints = critical, CA:TRUE, pathlen:0
> keyUsage         = critical, digitalSignature, keyCertSign
> extendedKeyUsage = codeSigning
> subjectKeyIdentifier   = hash
> authorityKeyIdentifier = keyid:always
> ```
>
> This makes the codesign cert a constrained sub-CA: it can sign the .exe + bundle as before, *and* can issue short-lived identity certs that chain `root → codesign → identity`. Trade-off: a wider blast radius if the codesign key leaks (an attacker could mint identity certs for any domain) — only do this if your codesign key lives in an HSM or is otherwise well protected.

Then issue the cert (2-year validity is a reasonable default):

```bash
openssl x509 -req -in codesign.csr \
    -CA root.crt -CAkey root.key -CAcreateserial \
    -out codesign.crt -days 730 -sha256 \
    -extfile codesign.ext
```

### 4. Bundle the codesign cert into a PFX for jsign / Authenticode

jDeploy's Windows signer (jsign) and GitHub Actions both consume PFX/PKCS12. Include the root cert in the chain so SmartScreen / Get-AuthenticodeSignature can build it:

```bash
openssl pkcs12 -export \
    -out codesign.pfx \
    -inkey codesign.key \
    -in codesign.crt \
    -certfile root.crt \
    -name "Acme Corp Code Signing" \
    -password pass:CHANGE_ME_PFX_PASSWORD
```

You now have:

| File | Purpose | Where it lives |
|---|---|---|
| `root.key` | Issuing root private key | **Offline only.** Vault/USB. |
| `root.crt` | Pinned root public cert | Embedded in `app.xml` (`trusted-certificates`) — committed to repo. |
| `codesign.key` | Code-signing private key | Wherever you sign — dev box or CI secret. |
| `codesign.crt` | Code-signing public cert | Bundled inside the PFX. |
| `codesign.pfx` | Combined keystore for jsign | Base64-encoded into a GitHub Actions secret. |

---

## Wiring It Into a jDeploy Project

### A. Pin the root cert in `app.xml`

The pinned certificate is what jDeploy's `FileVerifier` validates the chain against (see `shared/src/main/java/ca/weblite/tools/security/FileVerifier.java`). Place the root in the project's `app.xml` `trusted-certificates` attribute as a PEM string:

```xml
<app trusted-certificates="-----BEGIN CERTIFICATE-----
MIIE...your root.crt contents here...==
-----END CERTIFICATE-----" ...>
```

If you have multiple roots (e.g. transitioning from an old root to a new one), concatenate them — `AppXmlTrustedCertificatesExtractor` reads all of them. Don't put the codesign cert here; the codesign cert is shipped inside each bundle as `jdeploy.cer` and is only trusted because it chains to the pinned root.

### B. Enable certificate pinning at launch

In `package.json`'s `jdeploy` block:

```json
{
  "jdeploy": {
    "packageCertificatePinningEnabled": true,
    ...
  }
}
```

When this is on the launcher will run `FileVerifier.verifyDirectory(...)` on every launch and refuse to start if the bundle's manifest signature, file hashes, or chain-to-root all don't validate.

### C. Sign the bundle (jdeploy-bundle/jdeploy.mf)

The `PackageSigningService` reads the codesign key via the configured `KeyProvider` (env-var or keystore). For CI, the simplest path is the env-var provider — the codesign cert + key are loaded from environment:

```yaml
env:
  JDEPLOY_BUNDLE_SIGNING_KEY: ${{ secrets.CODESIGN_KEY_PEM }}
  JDEPLOY_BUNDLE_SIGNING_CERT: ${{ secrets.CODESIGN_CERT_CHAIN_PEM }}
```

`JDEPLOY_BUNDLE_SIGNING_CERT` should be the **full chain in PEM** (codesign cert first, then root) so `jdeploy.cer` ends up with both, letting the verifier walk the chain.

### D. Sign the Windows .exe (Authenticode)

This is already documented in `windows-authenticode-signing.md`. Use the same `codesign.pfx` you built above. With the GitHub Action:

```yaml
- uses: shannah/jdeploy@main
  with:
    github_token: ${{ secrets.GITHUB_TOKEN }}
    win_signing_certificate: ${{ secrets.WIN_SIGNING_CERT_BASE64 }}   # base64 of codesign.pfx
    win_signing_password:    ${{ secrets.WIN_SIGNING_PASSWORD }}
    win_signing_description: "Acme Widget"
    win_signing_url:         "https://acme.example.com"
```

To produce the secret value:

```bash
base64 -w 0 codesign.pfx > codesign.pfx.b64    # contents → GitHub secret WIN_SIGNING_CERT_BASE64
```

> **Important:** Use the *same* PFX (codesign cert + chain-to-root) for both `JDEPLOY_BUNDLE_SIGNING_*` and `win_signing_certificate`. That way the launcher pins the same root that Windows sees on the Authenticode signature, and the install-time "trust this publisher?" prompt installs the root that a user can later recognise on every signed file you ship.

### E. End-user install flow

1. User downloads installer from your release page.
2. Windows SmartScreen warns ("Unrecognized publisher") because the root isn't in Microsoft's CA program. User clicks "Run anyway".
3. jDeploy installer runs, detects the .exe is **signed-but-untrusted** (`AuthenticodeSignatureChecker` reports `Status=UnknownError`/`NotTrusted`), and shows the cert details dialog with subject, issuer, thumbprint, validity dates.
4. User accepts → `CertificateTrustService.addToUserTrustStore()` runs `certutil -user -addstore Root <cert>`.
5. From now on, every release signed by your codesign cert chains to a root the user has trusted, so step 2 disappears.
6. At each launch, the launcher (with pinning enabled) re-verifies the bundle against the pinned root in `app.xml`.

---

## Operational Recipes

### Rotating the codesign cert (annual / expiry / suspected leak)

1. Pull `root.key` out of the safe, on an offline box.
2. Generate a new `codesign.key` + CSR, sign with the root → `codesign-v2.crt`.
3. Build `codesign-v2.pfx`, replace the GitHub secrets.
4. Replace `JDEPLOY_BUNDLE_SIGNING_*` with the v2 cert chain.
5. Ship a release. Existing pinned roots in `app.xml` still match, so users see no warning and verification still passes.
6. Revoke `codesign-v1.crt` (CRL or just stop using it — jDeploy doesn't currently consume CRLs, see "Known Limitations").

**No `app.xml` change is needed.**

### Rotating the root cert (rare, e.g. compromise)

This *is* a hard reset. Plan it:

1. Generate `root-v2`. Sign a new codesign cert from it.
2. **Add `root-v2` to `app.xml`'s `trusted-certificates` alongside the old root.** Ship a release signed by either old or new codesign cert — both chains validate. This release is what users need before the cutover.
3. After enough users have updated, ship the next release signed by the new chain only and remove the old root from `app.xml`.
4. Old installs that never updated past step 2 will keep working with old releases but won't auto-update past step 3 (their pinned root no longer matches).

### Multiple developers / forks

The codesign key is the bottleneck — only one person/team should hold it. For OSS projects with outside contributors, sign only on the upstream release runner, never in PR CI.

### "I lost the root key"

You cannot revoke or rotate. Best path: generate `root-v2`, ship it as an *additional* trusted cert on the next release, and accept that any future codesign rotation requires users to first install that release.

### Storing the root key

- Avoid keeping it on any always-online machine.
- Two encrypted USB sticks in two physical locations.
- Optional: split with `ssss` or Shamir's Secret Sharing for multi-person control.
- Never paste it into a chat, password manager note, or cloud sync folder.

---

## Anatomy of a Signed Bundle (Recap)

After `jdeploy publish` runs with both bundle signing and Authenticode signing configured:

```
my-app-1.2.3-win-x64/
├── jdeploy-bundle/
│   ├── app.xml                 # contains <... trusted-certificates="-----BEGIN CERT...root.crt...">
│   ├── jdeploy.mf              # JSON: SHA-256 of every file + per-file signatures
│   ├── jdeploy.mf.sig          # RSA-SHA256(jdeploy.mf || version) using codesign.key
│   ├── jdeploy.cer             # codesign.crt + root.crt (concatenated DER)
│   └── ...app files...
└── my-app.exe                  # Authenticode-signed using codesign.pfx (same key)
```

At launch, with pinning on:
1. `FileVerifier` reads `jdeploy.cer`, builds the chain.
2. `SimpleCertificateVerifier.isTrusted()` does PKIX validation against `root.crt` from `app.xml`.
3. Verifies `jdeploy.mf.sig` over `jdeploy.mf || version` using the codesign cert's public key.
4. Walks every file in the manifest, recomputes SHA-256, verifies per-file signatures.
5. Any mismatch → launch refused.

---

## Known Limitations & Caveats

- **No CRL/OCSP.** A leaked codesign key cannot be revoked in a way the launcher will honour. Mitigation: short codesign cert lifetimes (1–2 years), and rotate proactively. A future enhancement could fetch a revocation list URL from the bundle.
- **No timestamping for bundle signatures.** Authenticode signatures are RFC-3161 timestamped, but `FileSigner` is not — once the codesign cert expires, fresh installs of that bundle will fail validity check. Mitigation: bundles are tied to a release; users on old releases keep their already-extracted bundle. New verification on disk does still re-check `cert.checkValidity(timestamp)` against the manifest's embedded timestamp, so a not-yet-expired-at-build bundle stays valid for the lifetime of the cert. We may add RFC-3161 timestamping to bundle signatures later.
- **SmartScreen reputation isn't fixable here.** Only OV/EV certs from a Microsoft-trusted CA build SmartScreen reputation. Self-managed PKI gives you integrity + identity-continuity, not silent SmartScreen acceptance.
- **No publisher website verification yet.** A user trusting an unknown root has no easy way to confirm it actually belongs to the developer they think it does. The companion-cert / `.well-known` URL flow described in `website-publisher-verification-plan.md` will close this gap.

---

## Quick Reference: Checklist

- [ ] Generated offline 3072-bit RSA root, 20-year self-signed.
- [ ] Issued a 2-year codesign cert from the root with `extendedKeyUsage=codeSigning`.
- [ ] Built `codesign.pfx` containing codesign cert + root chain.
- [ ] Pasted `root.crt` PEM into `app.xml`'s `trusted-certificates`.
- [ ] Set `packageCertificatePinningEnabled: true` in `package.json`.
- [ ] Stored `WIN_SIGNING_CERT_BASE64`, `WIN_SIGNING_PASSWORD`, `JDEPLOY_BUNDLE_SIGNING_KEY`, `JDEPLOY_BUNDLE_SIGNING_CERT` as GitHub Actions secrets.
- [ ] Verified an installer build: `Get-AuthenticodeSignature` shows the cert; `FileVerifier` returns `SIGNED_CORRECTLY` against the pinned root.
- [ ] Locked `root.key` away in two physically separate places.

---

## See Also

- `windows-authenticode-signing.md` — env-var reference for the .exe signer.
- `bundle-publishing-spec.md` — bundle layout + integrity model.
- `website-publisher-verification-plan.md` — implementation plan for publisher-domain verification (proposed).
