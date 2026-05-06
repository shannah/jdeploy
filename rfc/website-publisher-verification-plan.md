# Website / Publisher Verification — Implementation Plan

**Status**: Proposed
**Related**: `windows-codesigning-best-practices.md`, `windows-authenticode-signing.md`, `bundle-publishing-spec.md`
**Branch**: `claude/code-signing-certificate-pinning-hjmVd`

## Problem

When a user installs a jDeploy app signed with a self-managed root (see `windows-codesigning-best-practices.md`), the install-time prompt currently shows raw certificate metadata — subject DN, issuer DN, thumbprint, validity. None of that lets a user say "yes, that's the same Acme Corp whose website I just downloaded the installer from."

The existing `WebsiteVerifier` (`shared/.../services/WebsiteVerifier.java`) does a *different* check: it verifies that an **app's** SHA-256 hash appears on the homepage. It's per-app and per-version, doesn't bind to the signing identity, and doesn't help an end-user evaluating an unknown publisher cert.

## Goal

Let a publisher prove "this code-signing cert belongs to me, the operator of `https://acme.example.com` (or `github.com/acme/widget`)" in a way that:

1. The installer can verify **offline-style** (one HTTP fetch, no extra services).
2. Cannot be spoofed by copying a verification file to a different domain.
3. Doesn't require the publisher to obtain an OV/EV cert from a commercial CA.
4. Surfaces a clear, human-readable "Publisher: acme.example.com (verified)" line in the install dialog, replacing the current bare cert dump.

## Design

### Companion certificate (a.k.a. "publisher identity certificate")

The publisher generates a second X.509 certificate — a **publisher identity cert** — that **chains to the pinned root**. The identity cert binds a specific URL (or GitHub repo) into the certificate itself, so it cannot be moved.

The chain can be any of:

```
   (a) Root → Identity Cert                       (signed directly by root)
   (b) Root → Codesign Cert → Identity Cert       (codesign acts as issuing CA)
   (c) Root → Issuing-CA → Identity Cert          (separate issuing intermediate)
```

```
       Root CA  (offline, pinned in app.xml)
       /     \
      /       \
  Codesign    Publisher Identity Cert       ← (a) signed by root, OR
   Cert        ├── Subject CN: Acme Corp     ← (b) signed by the codesign cert
   (signs      ├── SAN URI: https://acme.example.com/.well-known/jdeploy-publisher.cer
   .exe +      ├── SAN URI: https://github.com/acme/widget    (optional; canonical form — the
   bundle)     │                                              installer extrapolates the actual
               │                                              raw.githubusercontent.com fetch URL)
               └── EKU: id-kp-1.3.6.1.4.1.<jdeploy-OID>.publisher
```

- Whatever signs the identity cert must have `basicConstraints=CA:TRUE` (and `keyUsage=keyCertSign`). If signing with the codesign cert (option b), that cert must therefore have been issued as a sub-CA — see updated `codesign.ext` recommendation in `windows-codesigning-best-practices.md`.
- Validity: short (90–365 days). Cheap to rotate; embeds a fresh proof of liveness.
- Hosted publicly at the URL embedded in its own SAN.

**Why allow option (b)?** It means publishers can re-issue an identity cert (e.g. when adding a new domain or rotating an expiring one) using a key that already lives on their build machine / CI, without pulling the offline root key out of the safe. Option (a) is still recommended when feasible because it keeps the codesign cert as a leaf — but option (b) is operationally lighter.

### `.well-known` URL convention

Default location:
```
https://<publisher-domain>/.well-known/jdeploy-publisher.cer
```
Encoded as PEM. The publisher may also/instead host the cert in a GitHub repo. The **canonical SAN form** embedded in the cert is the repo URL:
```
https://github.com/<owner>/<repo>
```
…and the installer extrapolates the actual fetch URL to:
```
https://raw.githubusercontent.com/<owner>/<repo>/<ref>/.well-known/jdeploy-publisher.cer
```
…with `<ref>` either `main`, a tag, or a commit SHA pinned by the publisher (default `main` unless the publisher's `publisherVerificationUrls` overrides it). Keeping the SAN as `github.com/<owner>/<repo>` means the cert binding survives branch renames and ref changes; the installer accepts a fetch from any equivalent-resolving `raw.githubusercontent.com` URL whose `<owner>/<repo>` matches the SAN.

### Verification flow at install time

When the installer encounters a signed-but-untrusted Windows .exe (see `Main.promptToTrustCertificateIfNeeded()`), and **before** showing the trust prompt:

1. **Discover candidate URLs.** Read `package.json` for a new `jdeploy.publisherVerificationUrls` field (array of strings) — package.json is what the installer can read both for codesigned exes (where it ships alongside the bundle) and for installer-generated exes (where app.xml is generated, not authoritative). Fallback: take the `homepage` field from package.json and append `/.well-known/jdeploy-publisher.cer`.
2. For each URL:
   1. HTTPS fetch (timeout 10s, follow redirects only on same origin).
   2. Parse PEM → X.509 cert(s). The fetched file MAY be a concatenation of identity cert + intermediates so the verifier can build the chain offline.
   3. **Build chain to root**: identity cert (leaf), then any intermediates from the response, then the pinned root from `app.xml`. Intermediates from the bundle's `jdeploy.cer` (e.g. the codesign cert if it was the issuer) are also added to the candidate set so option-(b) chains work without re-shipping the codesign cert in the well-known file.
   4. PKIX-validate. Reject if anything doesn't chain.
   5. Confirm the leaf cert has the jDeploy publisher EKU (custom OID, see below).
   6. **Confirm the URL match**: the URL we just fetched from must appear in the cert's SAN URI list. For GitHub fetches, a `raw.githubusercontent.com/<owner>/<repo>/<ref>/...` URL matches a SAN of `https://github.com/<owner>/<repo>` (canonical form) — the installer canonicalises both sides before comparing. This is the anti-spoof check — copying the file to a different domain produces a SAN/URL mismatch.
   7. Confirm the cert is currently valid (`checkValidity()`).
   8. Confirm the codesign cert (extracted from the .exe via `AuthenticodeSignatureChecker.exportCertificate()`) chains to the *same* root the identity cert chains to.
3. If any URL succeeds → `PublisherVerificationResult.VERIFIED(url, displayName)`.
4. If all URLs fail → `PublisherVerificationResult.NOT_VERIFIED(reason)`.

Result is passed into the trust-prompt UI.

### UI changes

`InstallationForm.showCertificateTrustPrompt()` currently shows raw cert metadata. After this change, when the result is `VERIFIED`:

```
This installer is signed by an unrecognized publisher.

  Publisher : Acme Corp
  Verified  : ✓ acme.example.com
              (the publisher of this software has proven control of this domain)

  Issuer    : Acme Corp Root CA
  Valid     : 2026-05-06 to 2028-05-06
  Thumbprint: 1A2B…

[Trust this publisher]   [Cancel]
```

When `NOT_VERIFIED`, the existing prompt is shown, plus a one-line "Publisher domain could not be verified" warning so users aren't lulled into thinking absence-of-bad-news = good-news.

### Custom EKU OID

We need a private OID for the publisher EKU. Options, in order of preference:

1. Get a PEN (Private Enterprise Number) under `1.3.6.1.4.1.<num>` and use `1.3.6.1.4.1.<num>.1.1` (publisher identity). Free from IANA, ~2 weeks.
2. Until a PEN is granted: use a placeholder OID under `1.3.6.1.4.1.99999.42.1` (clearly squatted; document as provisional).

The OID will be checked by the verifier and is what allows the same root to issue *both* a codesign cert (`id-kp-codeSigning`) and a publisher identity cert (jdeploy publisher EKU) without confusion.

---

## Implementation

### New code

| Module | New file | Purpose |
|---|---|---|
| `shared` | `tools/security/PublisherIdentityVerifier.java` | Stateless: given (URL, identity cert PEM, pinned root, codesign cert), runs the full check and returns a `PublisherIdentityResult`. |
| `shared` | `tools/security/PublisherIdentityResult.java` | `enum status` + `String url`, `String displayName`, `String failureReason`. |
| `shared` | `tools/security/PublisherIdentityFetcher.java` | HTTP(S) fetch of the `.well-known/jdeploy-publisher.cer` with sane timeouts; extracted to allow mocking in tests. |
| `installer` | `installer/win/PublisherVerificationService.java` | Orchestrates discovery (urls from `package.json` `jdeploy.publisherVerificationUrls` + `homepage` fallback), calls `PublisherIdentityVerifier`, returns to `Main`. |
| `cli` | `services/GeneratePublisherIdentityCertService.java` | New CLI subcommand: `jdeploy generate-publisher-cert --domain acme.example.com --root-key root.key --root-cert root.crt`. |

### Modifications

| File | Change |
|---|---|
| `installer/.../Main.java` `promptToTrustCertificateIfNeeded()` | Call `PublisherVerificationService` before invoking `form.showCertificateTrustPrompt(result)`. Pass `PublisherIdentityResult` into the form. |
| `installer/.../views/InstallationForm.java` `showCertificateTrustPrompt()` | New overload taking `PublisherIdentityResult`; render verified/unverified banner. |
| `installer/.../NPMPackageVersion.java` (or wherever `package.json` is parsed in the installer) | Read new optional `jdeploy.publisherVerificationUrls` field (array of strings). |
| `cli/.../JDeploy.java` | Wire up `generate-publisher-cert` subcommand. |
| `rfc/windows-codesigning-best-practices.md` | Once shipped: replace "No publisher website verification yet" caveat with a pointer to the new flow. |

### CLI surface (`jdeploy generate-publisher-cert`)

The issuer can be the root **or** any cert that chains to the pinned root (typically the codesign cert if it was set up with `CA:TRUE`):

```
# Issued directly by the root (option a)
jdeploy generate-publisher-cert \
    --issuer-key  ~/jdeploy-pki/root.key \
    --issuer-cert ~/jdeploy-pki/root.crt \
    --domain      acme.example.com \
    --github      acme/widget \
    --validity-days 365 \
    --out         publisher-identity.cer

# Issued by the codesign cert (option b — no need to unlock the root)
jdeploy generate-publisher-cert \
    --issuer-key  ~/jdeploy-pki/codesign.key \
    --issuer-cert ~/jdeploy-pki/codesign.crt \
    --chain       ~/jdeploy-pki/root.crt \
    --domain      acme.example.com \
    --validity-days 90 \
    --out         publisher-identity.pem
```

`--chain` lets the user concatenate intermediates into the output PEM so the served `.well-known` file lets the verifier build the chain in one fetch.

Output: a single PEM-encoded cert file the user uploads to `https://acme.example.com/.well-known/jdeploy-publisher.cer`. The command also prints:

```
Upload the file to:  https://acme.example.com/.well-known/jdeploy-publisher.cer
                     https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer

Add to package.json:
   "jdeploy": {
     "publisherVerificationUrls": [
       "https://acme.example.com/.well-known/jdeploy-publisher.cer",
       "https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer"
     ]
   }
```

### Test strategy

- Unit tests in `shared` covering: PKIX chain build, URL/SAN match, EKU enforcement, expired cert, wrong-root rejection, swapped-domain rejection.
- A mock URL fetcher (`PublisherIdentityFetcher` interface) so tests run offline.
- An integration test in `cli` that runs `generate-publisher-cert`, then runs `PublisherIdentityVerifier` against the generated cert via a local HTTP server.
- Add a smoke test under `tests/projects/` that builds a signed app, generates a publisher cert, serves it, and asserts the installer's `PublisherVerificationService` reports VERIFIED.

### Out of scope (for v1)

- DNS-based proof-of-control (TXT records). HTTPS `.well-known` is enough; DNS adds DNSSEC complexity.
- Auto-rotating expired identity certs at install time. v1 will fail-closed if the cert is expired and the user has to update.
- Trust transitivity ("Acme Corp also vouches for Beta Corp"). Single-level only.
- Replacing the existing per-app `WebsiteVerifier` (homepage hash check). Different feature, different scope; we can revisit consolidation later.

---

## Phased rollout

| Phase | Deliverable |
|---|---|
| 1 | `PublisherIdentityVerifier` + tests in `shared`. No UI yet. |
| 2 | `generate-publisher-cert` CLI command. |
| 3 | Installer integration: `PublisherVerificationService`, `Main` wiring, dialog change. |
| 4 | End-to-end smoke test under `tests/projects/`. |
| 5 | Documentation: update `windows-codesigning-best-practices.md`; new short doc `publisher-verification.md` walking through the publisher-side workflow. |
| 6 | (Optional) Apply for a PEN-based OID and migrate from the placeholder. |

## Resolved decisions

- **URL list location.** Lives in `package.json` (`jdeploy.publisherVerificationUrls`). The installer can read package.json in both the codesigned-exe case (it's shipped in the bundle) and the unsigned-exe case (the installer generates the exe itself). app.xml is unsuitable: signed exes don't expose it, and unsigned exes have it generated *by* the installer. The cert's SAN remains authoritative for binding (anti-spoof). A future tool that inspects an exe to surface its publisher could also embed the list in app.xml, but that's not needed for the installer flow.
- **No verification-result caching in v1.** Fetch is cheap; freshness matters more than throughput.
- **GitHub canonical URL form.** SAN URI uses `https://github.com/<owner>/<repo>`; the installer extrapolates the actual `raw.githubusercontent.com/<owner>/<repo>/<ref>/.well-known/jdeploy-publisher.cer` fetch URL on its own. This keeps the cert binding stable across ref/branch changes.
- **Codesign cert and publisher EKU strictly separated.** Two distinct certs, mirroring the `id-kp-codeSigning` precedent. The codesign cert never carries the publisher EKU.

## Open questions

1. **If the codesign cert acts as an issuing CA (option b), should it be allowed to also carry `id-kp-codeSigning`?** Some validators are picky about combining `keyCertSign` with `digitalSignature` + `codeSigning` EKU. Recommend testing against `signtool verify`, `Get-AuthenticodeSignature`, and the launcher's `SimpleCertificateVerifier` before committing.
