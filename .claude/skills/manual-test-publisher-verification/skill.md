# Manual Test: Publisher Verification

Walk a manual tester through verifying the install-time **publisher identity certificate** flow on Windows, end-to-end. Covers PKI setup, identity-cert generation, hosting, signed installer build, install verification, and intentional failure-mode probes.

This is a **manual** test skill — it produces commands and a checklist for the user to run/observe themselves, because the trust prompt is a Windows-only Swing dialog that has to be eyeballed. The skill cannot finish on its own; it pauses at each manual checkpoint and waits for the user to confirm what they saw.

## Scope

This skill validates these flows from the publisher verification feature (`rfc/website-publisher-verification-plan.md`):

1. `jdeploy generate-publisher-cert` produces a valid X.509 cert with the right SAN URIs and EKU.
2. The cert verifies through `PublisherIdentityVerifier` end-to-end against the chain it claims.
3. The install-time trust prompt shows the **green "Verified publisher" banner** when verification succeeds.
4. The trust prompt shows the **amber "Publisher domain could not be verified" warning** when verification fails, with the right `FailureReason` in the tooltip.
5. Both chain shapes work: option-(a) `Root → Identity` and option-(b) `Codesign → Identity`.
6. Both URL forms work: a `--domain` (https URL) and a `--github` (canonical github.com SAN).

Code-signing setup itself is a prerequisite — that's covered by the `test-authenticode-signing` skill. Bundle signing/pinning is a separate concern; this skill focuses on the *publisher identity* part.

## Prerequisites

Before invoking this skill, confirm with the user:

1. **Platform.** A Windows machine or VM is required for the install-time prompt. macOS/Linux can be used for the *generate* and *inspect* phases but not the install phase. If the user has only macOS/Linux, do steps 1–5 and report that step 6+ requires Windows.
2. **OpenSSL** in PATH. Used to mint the test root + codesign keys.
3. **Java + Maven**, plus a built `jdeploy` CLI from the `claude/code-signing-certificate-pinning-hjmVd` branch (or a tagged release that includes the publisher verification feature — Phase 1+ of `rfc/website-publisher-verification-plan.md`). Confirm with `npx jdeploy@<version> generate-publisher-cert --help`; if the help text appears, the feature is present.
4. **A throwaway GitHub repo** the user owns (optional, used in the GitHub-canonical-SAN test). The `shannah/jdeploy-test-app` repo from the `test-app-publishing` skill is fine.
5. **A test signed Windows installer.** If the user doesn't already have one, run the `test-authenticode-signing` skill first, then come back to this skill.

Ask the user up front:

- "What jdeploy version are you testing?" → store as `$JDEPLOY_VERSION`.
- "Do you have a signed Windows installer ready, or should we build one?" → if not, redirect to `test-authenticode-signing`.
- "Which chain shape do you want to test? Option-(a) `Root → Identity`, option-(b) `Codesign → Identity`, or both?" → default both.
- "Do you have a Windows machine for the install step, or do we stop after step 5?"

Make a working directory:

```bash
WORK_DIR=$(mktemp -d -t jdeploy-pubverify-XXXX)
cd "$WORK_DIR"
echo "Working in $WORK_DIR"
```

## Step 1 — Mint the test PKI

The publisher verification flow chains an identity cert to either the **codesign cert** in the .exe or a **root** pinned in `app.xml`. We mint both so we can exercise either chain.

### 1a. Root CA

```bash
openssl genrsa -out root.key 3072
openssl req -x509 -new -key root.key -sha256 -days 7300 \
    -out root.crt \
    -subj "/CN=Test Root CA/O=Manual-Test/C=US" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"
```

Sanity-check:

```bash
openssl x509 -in root.crt -noout -subject -ext basicConstraints,keyUsage
```

You should see `CA:TRUE` and `keyCertSign`.

### 1b. Codesign cert

For option-(b) chains, the codesign cert needs to be a sub-CA. Use the broader `codesign-ca.ext` for both option-a and option-b tests — it's CA:TRUE so it can issue identity certs (option-b) and still works as a code-signing leaf (it has `digitalSignature, keyCertSign` and the `codeSigning` EKU).

```bash
openssl genrsa -out codesign.key 3072
openssl req -new -key codesign.key -out codesign.csr \
    -subj "/CN=Test Codesign Sub-CA/O=Manual-Test/C=US"

cat > codesign.ext <<'EOF'
basicConstraints = critical, CA:TRUE, pathlen:0
keyUsage         = critical, digitalSignature, keyCertSign
extendedKeyUsage = codeSigning
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid:always
EOF

openssl x509 -req -in codesign.csr \
    -CA root.crt -CAkey root.key -CAcreateserial \
    -out codesign.crt -days 730 -sha256 \
    -extfile codesign.ext

openssl pkcs12 -export \
    -out codesign.pfx \
    -inkey codesign.key \
    -in codesign.crt \
    -certfile root.crt \
    -name "Test Codesign" \
    -password pass:test1234
```

Pause here and tell the user: "Generated `root.crt` (offline anchor), `codesign.crt` + `codesign.key` (issuing cert), `codesign.pfx` (combined PFX with chain). The PFX password is `test1234`."

## Step 2 — Generate the publisher identity cert

### 2a. Option-(a) — issued by the root

```bash
npx jdeploy@$JDEPLOY_VERSION generate-publisher-cert \
    --issuer-key   root.key \
    --issuer-cert  root.crt \
    --domain       acme-test.example \
    --github       acme/widget \
    --validity-days 60 \
    --out          publisher-identity-a.pem
```

### 2b. Option-(b) — issued by the codesign sub-CA

```bash
npx jdeploy@$JDEPLOY_VERSION generate-publisher-cert \
    --issuer-key   codesign.key \
    --issuer-cert  codesign.crt \
    --chain        root.crt \
    --domain       acme-test.example \
    --validity-days 30 \
    --out          publisher-identity-b.pem
```

Note: `--chain root.crt` means the served PEM contains both the identity cert and the root, so a single fetch gives the verifier the whole chain.

Expected output for each command:

```
Wrote publisher identity certificate: <path>/publisher-identity-{a,b}.pem
Wrote identity private key (optional, not used at install time): <path>/publisher-identity-{a,b}.key

Upload the certificate to:
  https://acme-test.example/.well-known/jdeploy-publisher.cer
  https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer

Add to package.json:
  "jdeploy": {
    "publisherVerificationUrls": [
      ...
    ]
  }
```

Confirm both files were created.

## Step 3 — Inspect each cert

This is a sanity check before any network/install steps. If anything looks wrong here, the verifier will fail later for the same reason and you'll save a lot of time by catching it now.

```bash
for f in publisher-identity-a.pem publisher-identity-b.pem; do
  echo "=== $f ==="
  openssl x509 -in "$f" -noout -subject -issuer
  openssl x509 -in "$f" -noout -ext subjectAltName,extendedKeyUsage
done
```

Confirm with the user that each cert shows:

| Field | Expected |
|---|---|
| Subject | `CN=acme-test.example` (or the SAN host as CN) and `O=` if you passed `--organization` |
| Issuer  | for `-a.pem`: `CN=Test Root CA, O=Manual-Test, C=US`<br>for `-b.pem`: `CN=Test Codesign Sub-CA, O=Manual-Test, C=US` |
| `subjectAltName` | exactly the URLs from `--domain` (full `https://...well-known/jdeploy-publisher.cer` form) and `--github` (canonical `https://github.com/<owner>/<repo>` form) |
| `extendedKeyUsage` | contains `1.3.6.1.4.1.99999.42.1` (the placeholder publisher EKU OID — see `rfc/website-publisher-verification-plan.md` for the migration plan when a real PEN is registered) |

If any of these don't match, stop and investigate.

Verify each chain manually with openssl:

```bash
# Option-a: identity chains directly to root
openssl verify -CAfile root.crt publisher-identity-a.pem

# Option-b: identity chains via codesign cert; pass codesign.crt as untrusted intermediate
openssl verify -CAfile root.crt -untrusted codesign.crt publisher-identity-b.pem
```

Both must say `OK`. If not, the verifier will fail with `CHAIN_INVALID` later and the bug is in the cert generation.

## Step 4 — Host the cert

Pick the easiest hosting option for the user's setup. Hosting requires HTTPS — the verifier rejects http and cross-origin redirects.

### Option H1 (recommended) — GitHub repo

This needs zero setup beyond a public repo and gives you a real HTTPS URL.

1. In a throwaway repo the user owns (e.g. `<their-user>/jdeploy-publisher-test`), commit `publisher-identity-a.pem` (or `-b.pem`) at the path `.well-known/jdeploy-publisher.cer`.
2. The fetch URL is `https://raw.githubusercontent.com/<owner>/<repo>/main/.well-known/jdeploy-publisher.cer`. **Test the URL in a browser** — you should see the PEM text, not a 404 or redirect.
3. The cert was issued with SAN `https://github.com/acme/widget`. **This will not match a fetch from `<owner>/<repo>` — the SAN is bound to a specific owner/repo.** You'll need to re-mint the identity cert with `--github <real-owner>/<real-repo>` so the SAN matches what the verifier will canonicalise the fetch URL to. Re-run step 2 with the user's real GitHub slug, then commit and push.

If the test plan requires *only* the GitHub form, you can skip the `--domain` flag in step 2 and re-run with `--github <real-owner>/<real-repo>` only.

### Option H2 — ngrok

If the user has ngrok set up, this gives a public HTTPS URL pointing at a local file server.

```bash
# Serve publisher-identity-a.pem at /.well-known/jdeploy-publisher.cer locally on :8000
mkdir -p .well-known
cp publisher-identity-a.pem .well-known/jdeploy-publisher.cer
python3 -m http.server 8000 &
SERVER_PID=$!

# Tunnel
ngrok http 8000
```

ngrok prints something like `Forwarding  https://abcd-1234.ngrok-free.app -> http://localhost:8000`. Confirm `https://abcd-1234.ngrok-free.app/.well-known/jdeploy-publisher.cer` works in a browser.

The cert's SAN was `https://acme-test.example/.well-known/jdeploy-publisher.cer` — *which won't match the ngrok host*. So either:
- Re-run step 2 with `--domain abcd-1234.ngrok-free.app` to bind the SAN to the ngrok hostname (ngrok URLs are stable per session), then re-host; or
- Override the URL the installer fetches via `package.json` so it asks for the ngrok URL, but with a SAN that says `acme-test.example` — and confirm the verifier *fails* with `URL_NOT_IN_SAN` (intentional negative test, see step 8).

Stop the local server when finished:

```bash
kill $SERVER_PID
pkill -f "ngrok http"
```

### Option H3 — your own webserver

If the user has DNS + HTTPS for a domain they control, just upload the file at `/.well-known/jdeploy-publisher.cer`. Step 2's `--domain <yourdomain>` already produced the right SAN URI for this case. Verify the URL in a browser.

## Step 5 — Build the test app with publisher verification wired in

Use the `test-app-publishing` skill (or the user's existing test app) to produce a published Windows installer. After the skill finishes, edit the test app's `package.json` to add the `publisherVerificationUrls` array and re-publish.

```jsonc
{
  "homepage": "https://acme-test.example",          // or the real domain you used
  "jdeploy": {
    "publisherVerificationUrls": [
      "https://raw.githubusercontent.com/<your-owner>/<your-repo>/main/.well-known/jdeploy-publisher.cer"
    ]
    // ... rest of jdeploy config
  }
}
```

For the Windows .exe to be **signed-but-untrusted** (the precondition for the trust prompt to fire), the publisher pipeline needs to:
1. Sign the exe with `codesign.pfx` from step 1b (via `JDEPLOY_WIN_KEYSTORE_PATH` etc., per `rfc/windows-authenticode-signing.md`). This is what makes the .exe carry an Authenticode signature.
2. Not pre-install the test root in the user's trust store. (It won't be — the root was generated locally and is unknown to Windows.)

If the user is running the `test-authenticode-signing` skill, point it at `codesign.pfx` and re-publish.

## Step 6 — Install on the Windows test machine

Pause and tell the user the following. Wait for them to do it and report what they saw.

> 1. On a clean Windows VM (or one where the test root is NOT in `Trusted Root Certification Authorities`), download the installer from the test app's release page.
> 2. SmartScreen will warn — click **More info → Run anyway**.
> 3. The jDeploy installer launches. After it finishes installing the binaries, it shows the **trust prompt** dialog.
>
> Look at the dialog **carefully**. There should be a small banner **above** the "Would you like to trust this publisher?" question:
>
> - **Green ✓** "Verified publisher: `acme-test.example`" (or whatever domain you used) → publisher verification succeeded. Note the domain shown.
> - **Amber ⚠** "Publisher domain could not be verified." → verification failed. Hover the warning to see the failure reason in the tooltip (e.g. `FETCH_FAILED`, `URL_NOT_IN_SAN`, `CHAIN_INVALID`).
> - **No banner at all** → publisher verification was not attempted. This happens when neither `publisherVerificationUrls` nor an HTTPS `homepage` is in package.json. If you set up step 5 correctly, you should NOT see this — it means the installer didn't read your config.
>
> Click **Skip** (don't add the cert to your trust store). We'll exercise the trust path in a separate step if needed.
>
> Tell me which of the three you saw.

Expected for the happy path: green ✓ banner with `acme-test.example` (or your domain).

If you got the amber warning instead, treat it as a bug and capture: (a) the tooltip text, (b) the URL the installer would have fetched, (c) the SAN URIs on the cert (from step 3). Cross-reference with the failure-reason table in `rfc/publisher-verification.md`.

## Step 7 — Repeat for the other configurations

If the user agreed to test more than one configuration in step 0, repeat steps 2 → 6 for each:

- Option-(a) chain with a domain SAN.
- Option-(a) chain with a GitHub SAN.
- Option-(b) chain (already covered by `publisher-identity-b.pem`).
- A test app where the root is *also* pinned in `app.xml`'s `trusted-certificates` attribute (validates the Phase 7 path: identity cert chains via `app.xml` root, not via codesign cert).

For the `app.xml` test:
- The bundle's `app.xml` should contain the root cert PEM in `trusted-certificates="..."`. This is normally done by the build pipeline if the user has set `packageCertificatePinningEnabled: true` in `package.json` and the root PEM is referenced; consult the user's project setup.
- Use the option-(a) `publisher-identity-a.pem` (signed by root, not by codesign).
- Verify the green banner still appears even though the identity cert has no relationship to the codesign cert.

## Step 8 — Negative tests (failure-mode walkthroughs)

These confirm each `FailureReason` surfaces correctly. For each, edit the published config to break one specific thing, re-publish (or just edit the `.well-known` cert in your hosting), re-install on Windows, and confirm the banner shows the corresponding failure.

| Break | Expected `FailureReason` | How |
|---|---|---|
| Take the cert offline | `FETCH_FAILED` | Make the well-known URL return 404, or remove the file. |
| Serve garbage at the URL | `INVALID_PEM` | `echo "hello" > .well-known/jdeploy-publisher.cer` |
| Move the cert to a different host | `URL_NOT_IN_SAN` | Change `publisherVerificationUrls` in package.json to a host that isn't in the cert's SAN. |
| Use a cert with no EKU | `MISSING_EKU` | Mint a fresh cert with `openssl x509` (no EKU) instead of `jdeploy generate-publisher-cert`, host that. |
| Use a cert past its `notAfter` | `CERT_EXPIRED` | Use `--validity-days 1` and wait, or backdate via a custom openssl cert. |
| Identity cert chains to a different root | `CHAIN_INVALID` | Mint identity from a fresh root NOT pinned anywhere in the test app. |
| Codesign cert is from a different PKI | `CODESIGN_ROOT_MISMATCH` | Sign the .exe with one PKI, generate identity cert with another. (rare; only matters when both an `app.xml` root and a separate codesign cert exist) |

For each, ask the user to confirm: "Did the banner show amber? Did the tooltip mention `<expected reason>`?" — capture and report.

For the case where verification simply isn't configured, also test:

- Remove `publisherVerificationUrls` and any HTTPS `homepage` from package.json. Re-publish. Re-install. The banner should be **absent** (not amber). This validates the `NO_URLS_CONFIGURED` short-circuit in `Main.runPublisherVerification` — publishers who haven't opted in shouldn't be penalised with a warning.

## Step 9 — Clean up

```bash
# Remove the test PKI files. They're unrelated to any production setup, but
# treat root.key like any other private key meanwhile.
shred -u "$WORK_DIR"/*.key 2>/dev/null || rm -f "$WORK_DIR"/*.key
rm -rf "$WORK_DIR"
```

If the user added the test root to their Windows VM trust store at any point during testing, walk them through removing it:

```
certutil -user -delstore Root "Test Root CA"
```

If the test app was published to npm or GitHub, you can leave the published artifacts (the test repo is a scratchpad) or delete the release.

## Reporting

After all selected configurations have been walked through, summarise to the user:

- Configurations tested (option-a / option-b / domain SAN / github SAN / app.xml roots).
- Negative tests run and which `FailureReason` each surfaced.
- Anything that didn't behave as expected — flag those as bugs with: configuration, expected, observed, screenshot if possible.

## Reference

- `rfc/website-publisher-verification-plan.md` — design + status.
- `rfc/publisher-verification.md` — publisher-side how-to (failure-reason table is the source of truth).
- `rfc/windows-codesigning-best-practices.md` — PKI setup that this skill mirrors.
- `shared/src/main/java/ca/weblite/tools/security/PublisherIdentityVerifier.java` — verification logic.
- `installer/src/main/java/ca/weblite/jdeploy/installer/win/PublisherVerificationService.java` — install-time orchestration.
