# Publisher Verification — How to Set It Up

**Audience.** App publishers using jDeploy who want the install-time trust prompt to display "✓ Verified publisher: `<your-domain>`" instead of just a raw certificate dump. Pairs with `windows-codesigning-best-practices.md`.

**What this gets you.** When an end-user installs your app and Windows flags the .exe as "signed by an unrecognized publisher", the jDeploy installer can fetch a small certificate file from a URL **you control** and prove that the same key signed both. The trust prompt then surfaces a green banner instead of a bare certificate fingerprint. Without this, users have only the certificate's subject DN to evaluate.

**What this does NOT get you.**
- Microsoft SmartScreen reputation. That requires an OV/EV certificate from a Microsoft-trusted CA.
- Avoidance of the SmartScreen warning on first install. The warning still appears; this verification only changes what the *installer* shows after the user clicks through.
- Trust in users who have never heard of your domain. The flow makes "Acme controls acme.example.com" verifiable; the user still has to decide whether to trust Acme.

## Prerequisites

You need:
1. A working Windows code-signing setup as described in `windows-codesigning-best-practices.md` — root cert pinned in `app.xml`, codesign cert + key available to your build, `jdeploy publish` producing a signed `.exe`.
2. A jDeploy CLI built from this branch (Phases 1–3 of `website-publisher-verification-plan.md`).
3. A place to host a small static file over HTTPS — your project's website or a public GitHub repo are both fine.

## Step 1 — Generate the publisher identity certificate

The publisher identity cert is a fresh X.509 certificate signed by your root (or by your codesign cert if you set it up as a sub-CA — see `windows-codesigning-best-practices.md` § 3). It carries the URL(s) where it will be hosted as `SAN URI` entries — that binding is what prevents the file from being copied to a different domain.

**Issued directly by the offline root** (most secure; recommended when you don't mind unlocking the root once a year):

```bash
jdeploy generate-publisher-cert \
    --issuer-key   ~/jdeploy-pki/root.key \
    --issuer-cert  ~/jdeploy-pki/root.crt \
    --domain       acme.example.com \
    --github       acme/widget \
    --validity-days 365 \
    --out          publisher-identity.pem
```

**Issued by the codesign cert** (operationally lighter; requires the codesign cert to be a `CA:TRUE, pathlen:0` sub-CA):

```bash
jdeploy generate-publisher-cert \
    --issuer-key   ~/jdeploy-pki/codesign.key \
    --issuer-cert  ~/jdeploy-pki/codesign.crt \
    --chain        ~/jdeploy-pki/root.crt \
    --domain       acme.example.com \
    --validity-days 90 \
    --out          publisher-identity.pem
```

The `--chain` flag concatenates intermediates onto the output PEM so a single fetch of the well-known URL gives the verifier the full chain.

The command also produces `publisher-identity.key`. **You do not need this private key for verification to work** — the publisher identity cert is a passive ownership token. You can delete `publisher-identity.key` after generation, or pass `--no-key` to skip writing it. If you do keep it, treat it like any other private key (`chmod 0600`, don't commit).

Output you'll see:

```
Wrote publisher identity certificate: /tmp/publisher-identity.pem
Wrote identity private key (optional, not used at install time): /tmp/publisher-identity.key

Upload the certificate to:
  https://acme.example.com/.well-known/jdeploy-publisher.cer
  https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer

Add to package.json:
  "jdeploy": {
    "publisherVerificationUrls": [
      "https://acme.example.com/.well-known/jdeploy-publisher.cer",
      "https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer"
    ]
  }
```

## Step 2 — Host the certificate

Upload `publisher-identity.pem` (rename to `jdeploy-publisher.cer` if you prefer) at the URL(s) printed by the CLI.

### On your own website

Drop the file at `/.well-known/jdeploy-publisher.cer`. Make sure your server returns it as `text/plain`, `application/x-pem-file`, or `application/x-x509-ca-cert` with HTTP 200. Don't redirect to a CDN on a different host — the verifier refuses cross-origin redirects.

If your site auto-strips dotfile directories (some static hosts do), serve from a non-dot path and use the explicit URL form in `--domain` ... actually the SAN binding is to the exact URL, so you can't easily move it without re-issuing the cert. The cleanest fix is to enable dotfile serving for `/.well-known/`.

### In a GitHub repo

Commit the file to your repo at `.well-known/jdeploy-publisher.cer`. The CLI assumes the `main` branch and a default `<owner>/<repo>` slug. The cert's SAN URI is the canonical `https://github.com/<owner>/<repo>` form; the installer extrapolates the actual `https://raw.githubusercontent.com/<owner>/<repo>/main/.well-known/jdeploy-publisher.cer` fetch URL on its own. This means the cert binding survives ref/branch changes — any equivalent-resolving raw URL with the same `<owner>/<repo>` matches.

If you want to pin the cert to a specific commit or tag instead of `main`, override the URL in `package.json` (next step) with the desired raw URL — the SAN comparison will still succeed because the canonicalisation strips the ref.

## Step 3 — Wire it into `package.json`

Paste the snippet the CLI printed:

```json
{
  "name": "your-app",
  "version": "1.2.3",
  "homepage": "https://acme.example.com",
  "jdeploy": {
    "publisherVerificationUrls": [
      "https://acme.example.com/.well-known/jdeploy-publisher.cer",
      "https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer"
    ]
  }
}
```

The installer also accepts a single string instead of an array. If `publisherVerificationUrls` is omitted entirely, the installer falls back to `<homepage>/.well-known/jdeploy-publisher.cer` — so for the simplest setup, just hosting the cert at that exact path under your existing `homepage` is enough; you don't have to add the array at all.

If neither the array nor a usable HTTPS `homepage` is configured, the installer **silently skips verification** (no warning banner). You only see the "could not be verified" warning if the publisher *opted in* and verification then failed.

## Step 4 — Publish and test

Run `jdeploy publish` as usual. On a test Windows VM (or a colleague's machine that's never seen your cert):

1. Download the installer from your release page.
2. Click through SmartScreen.
3. The trust prompt should now show a green "✓ Verified publisher: acme.example.com" banner instead of just the certificate fingerprint.

If you see the amber "Publisher domain could not be verified" warning, hover the warning to see the failure reason in the tooltip. Common causes:

| Reason in tooltip | What to fix |
|---|---|
| `FETCH_FAILED` | URL returns non-200, isn't reachable, or redirects cross-origin. Check your hosting. |
| `INVALID_PEM` | The served file isn't a valid PEM cert. Re-upload the `--out` file from Step 1. |
| `URL_NOT_IN_SAN` | The cert's SAN URIs don't include the URL the installer fetched from. Re-issue the cert with the right `--domain`/`--github` values. |
| `MISSING_EKU` | The cert is missing the jDeploy publisher EKU. Use the CLI to mint it; don't hand-craft the cert. |
| `CERT_EXPIRED` | The identity cert is past its validity window. Re-run `generate-publisher-cert`. |
| `CHAIN_INVALID` | The identity cert doesn't chain to the codesign cert in the .exe. Either you signed it with the wrong issuer key, or the codesign cert in the installer isn't from the same PKI. |
| `CODESIGN_ROOT_MISMATCH` | The codesign cert in the .exe was issued by a different root than expected. Probably a mis-pointed CI secret. |

## Rotation

The identity cert has a short validity (90–365 days). When it's close to expiring, re-run the same `generate-publisher-cert` command and re-upload the file. **No `package.json` change is needed** — the URL stays the same, only the cert content changes.

You don't need to rotate when you ship a new version of the app. The identity cert is decoupled from the app version.

## Multiple domains

`--domain` and `--github` are repeatable. The CLI emits a SAN URI for each, and the installer accepts the cert at any of the configured URLs:

```bash
jdeploy generate-publisher-cert \
    --issuer-key   root.key \
    --issuer-cert  root.crt \
    --domain       acme.example.com \
    --domain       acme-widget.com \
    --github       acme/widget \
    --validity-days 365 \
    --out          publisher-identity.pem
```

Useful when an app has both a corporate-domain landing page and a GitHub project page.

## Caveats

- **HTTPS only.** The fetcher refuses plaintext and cross-origin redirects. If your hosting requires a redirect (e.g. www → apex), make sure both endpoints are on the same origin or the SAN URI matches the post-redirect URL.
- **No verification at runtime.** This flow runs at *install* time. The launcher's certificate pinning (`packageCertificatePinningEnabled`) is independent and runs every launch.
- **No revocation.** If a hosted identity cert is compromised, the only mitigation is to re-issue and re-upload; expired or replaced certs simply stop verifying. No CRL or OCSP support in v1.
- **Trust anchor at install time.** v1 chains the identity cert to the codesign cert extracted from the .exe (option-(b) in the plan doc). Identity certs that chain only to a separate root in `app.xml` (option-(a)) without going through the codesign cert will currently fail with `CHAIN_INVALID`. This is a Phase 4+ enhancement.

## See also

- `windows-codesigning-best-practices.md` — root + codesign + pinning setup the publisher cert chains into.
- `website-publisher-verification-plan.md` — design and implementation status for this feature.
