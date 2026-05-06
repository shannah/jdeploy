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

The publisher generates a second X.509 certificate — a **publisher identity cert** — that is **signed by their root CA** (the same root that signs the codesign cert). The identity cert binds a specific URL (or GitHub repo) into the certificate itself, so it cannot be moved.

```
       Root CA  (offline, pinned in app.xml)
       /     \
      /       \
  Codesign    Publisher Identity Cert
   Cert        ├── Subject CN: Acme Corp
   (signs       ├── SAN URI: https://acme.example.com/.well-known/jdeploy-publisher.cer
   .exe +      ├── SAN URI: https://github.com/acme/widget    (optional, multi-binding)
   bundle)     └── EKU: id-kp-1.3.6.1.4.1.<jdeploy-OID>.publisher
```

- Issued from the root, **not** from the codesign cert. The root is what's pinned, and we don't want to require a path constraint chain longer than 1.
- Validity: short (90–365 days). Cheap to rotate; embeds a fresh proof of liveness.
- Hosted publicly at the URL embedded in its own SAN.

### `.well-known` URL convention

Default location:
```
https://<publisher-domain>/.well-known/jdeploy-publisher.cer
```
Encoded as PEM. The publisher may also/instead host the cert at a GitHub raw URL:
```
https://raw.githubusercontent.com/<owner>/<repo>/<ref>/.well-known/jdeploy-publisher.cer
```
…with `<ref>` either `main`, a tag, or a commit SHA pinned by the publisher.

### Verification flow at install time

When the installer encounters a signed-but-untrusted Windows .exe (see `Main.promptToTrustCertificateIfNeeded()`), and **before** showing the trust prompt:

1. **Discover candidate URLs.** Read `app.xml` for a new `publisher-verification-urls` attribute (comma-separated). Fallback: parse the `homepage` from the bundle's package.json, append `/.well-known/jdeploy-publisher.cer`.
2. For each URL:
   1. HTTPS fetch (timeout 10s, follow redirects only on same origin).
   2. Parse PEM → X.509 cert.
   3. **Build chain to root**: identity cert, then any intermediates from the response (PEM may include them), then the pinned root from `app.xml`.
   4. PKIX-validate. Reject if anything doesn't chain.
   5. Confirm the leaf cert has the jDeploy publisher EKU (custom OID, see below).
   6. **Confirm the URL match**: the URL we just fetched from must appear in the cert's SAN URI list. This is the anti-spoof check — copying the file to a different domain produces a SAN/URL mismatch.
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
| `installer` | `installer/win/PublisherVerificationService.java` | Orchestrates discovery (urls from app.xml + homepage), calls `PublisherIdentityVerifier`, returns to `Main`. |
| `cli` | `services/GeneratePublisherIdentityCertService.java` | New CLI subcommand: `jdeploy generate-publisher-cert --domain acme.example.com --root-key root.key --root-cert root.crt`. |

### Modifications

| File | Change |
|---|---|
| `installer/.../Main.java` `promptToTrustCertificateIfNeeded()` | Call `PublisherVerificationService` before invoking `form.showCertificateTrustPrompt(result)`. Pass `PublisherIdentityResult` into the form. |
| `installer/.../views/InstallationForm.java` `showCertificateTrustPrompt()` | New overload taking `PublisherIdentityResult`; render verified/unverified banner. |
| `shared/.../models/AppManifest.java` (or app.xml schema) | Read new optional attribute `publisher-verification-urls`. |
| `cli/.../JDeploy.java` | Wire up `generate-publisher-cert` subcommand. |
| `rfc/windows-codesigning-best-practices.md` | Once shipped: replace "No publisher website verification yet" caveat with a pointer to the new flow. |

### CLI surface (`jdeploy generate-publisher-cert`)

```
jdeploy generate-publisher-cert \
    --root-key   ~/jdeploy-pki/root.key \
    --root-cert  ~/jdeploy-pki/root.crt \
    --domain     acme.example.com \
    --github     acme/widget \
    --validity-days 365 \
    --out        publisher-identity.cer
```

Output: a single PEM-encoded cert file the user uploads to `https://acme.example.com/.well-known/jdeploy-publisher.cer`. The command also prints:

```
Upload the file to:  https://acme.example.com/.well-known/jdeploy-publisher.cer
                     https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer

Add to app.xml:
   publisher-verification-urls="https://acme.example.com/.well-known/jdeploy-publisher.cer,
                                https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer"
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

## Open questions

1. **Where should the URL list live — `app.xml`, `package.json`, or the cert itself?** Leaning toward "all three": cert SAN is authoritative for *binding*, but a discoverable list in `app.xml` lets the installer know which URLs to attempt before fetching anything. Discuss before Phase 3.
2. **Should we cache verification results across installs?** Probably not in v1 — fetch is cheap, freshness matters more than throughput.
3. **GitHub repo URL canonicalisation.** `github.com/owner/repo` vs `raw.githubusercontent.com/owner/repo/main/...` — settle on one form to embed in SAN; verifier accepts equivalent-resolving fetches.
4. **Should the codesign cert *also* be issued with the publisher EKU, or strictly separated?** Strictly separated is cleaner and matches the precedent of `id-kp-codeSigning` vs other EKUs; recommend keeping them as two distinct certs from the same root.
