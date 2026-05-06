package ca.weblite.tools.security;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublisherIdentityVerifierTest {

    private static final long ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000;
    private static final String EKU = PublisherIdentityVerifier.PUBLISHER_EKU_OID;

    private KeyPair rootKp;
    private X509Certificate rootCert;

    private KeyPair codesignKp;
    private X509Certificate codesignCert;

    @BeforeEach
    public void setUp() throws Exception {
        rootKp = generateKeyPair();
        rootCert = buildCert(
                "CN=Acme Root CA", rootKp,
                "CN=Acme Root CA", rootKp,
                now(), plusYears(20),
                /*isCa*/ true, /*ekus*/ null, /*sanUris*/ null
        );
        codesignKp = generateKeyPair();
        codesignCert = buildCert(
                "CN=Acme Code Signing", codesignKp,
                "CN=Acme Root CA", rootKp,
                now(), plusYears(2),
                false,
                Collections.singletonList(KeyPurposeId.id_kp_codeSigning),
                null
        );
    }

    @Test
    public void verifiesIdentityCertSignedDirectlyByRoot() throws Exception {
        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        X509Certificate identity = buildIdentity(rootKp, rootCert, "CN=Acme Corp",
                Collections.singletonList(url), now(), plusYears(1));

        PublisherIdentityResult result = newVerifier(map(url, pem(identity))).verify(
                Collections.singletonList(url),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );

        assertTrue(result.isVerified(), "expected VERIFIED, got " + result);
        assertEquals(url, result.getUrl());
        assertEquals("acme.example.com", result.getDisplayName());
    }

    @Test
    public void verifiesOptionBChainCodesignIssuesIdentity() throws Exception {
        // Codesign cert needs to be a sub-CA (CA:TRUE) to issue further certs.
        KeyPair issuingCsKp = generateKeyPair();
        X509Certificate issuingCsCert = buildCert(
                "CN=Acme Code Signing CA", issuingCsKp,
                "CN=Acme Root CA", rootKp,
                now(), plusYears(2),
                /*isCa*/ true,
                Collections.singletonList(KeyPurposeId.id_kp_codeSigning),
                null
        );
        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        X509Certificate identity = buildIdentity(
                issuingCsKp, issuingCsCert, "CN=Acme Corp",
                Collections.singletonList(url), now(), plusYears(1)
        );

        // Identity cert is served alone; verifier must also pull intermediates from the
        // codesign chain to build a full path to the pinned root.
        PublisherIdentityResult result = newVerifier(map(url, pem(identity))).verify(
                Collections.singletonList(url),
                Collections.singletonList(rootCert),
                Arrays.asList(issuingCsCert)
        );

        assertTrue(result.isVerified(), "expected VERIFIED, got " + result);
    }

    @Test
    public void verifiesGithubCanonicalSan() throws Exception {
        String fetchUrl = "https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer";
        String sanUrl = "https://github.com/acme/widget";
        X509Certificate identity = buildIdentity(rootKp, rootCert, "CN=Acme Corp",
                Collections.singletonList(sanUrl), now(), plusYears(1));

        PublisherIdentityResult result = newVerifier(map(fetchUrl, pem(identity))).verify(
                Collections.singletonList(fetchUrl),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );

        assertTrue(result.isVerified(), "expected VERIFIED, got " + result);
    }

    @Test
    public void rejectsCertWithMissingEku() throws Exception {
        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        X509Certificate identity = buildCert(
                "CN=Acme Corp", generateKeyPair(),
                "CN=Acme Root CA", rootKp,
                now(), plusYears(1),
                false,
                /*ekus*/ Collections.emptyList(),
                Collections.singletonList(url)
        );

        PublisherIdentityResult result = newVerifier(map(url, pem(identity))).verify(
                Collections.singletonList(url),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );

        assertFalse(result.isVerified());
        assertEquals(PublisherIdentityResult.FailureReason.MISSING_EKU, result.getFailureReason());
    }

    @Test
    public void rejectsSwappedDomain() throws Exception {
        String embeddedUrl = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        String fetchUrl = "https://attacker.example.org/.well-known/jdeploy-publisher.cer";
        // Cert says it lives at acme.example.com but we fetched it from attacker.example.org.
        X509Certificate identity = buildIdentity(rootKp, rootCert, "CN=Acme Corp",
                Collections.singletonList(embeddedUrl), now(), plusYears(1));

        PublisherIdentityResult result = newVerifier(map(fetchUrl, pem(identity))).verify(
                Collections.singletonList(fetchUrl),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );

        assertFalse(result.isVerified());
        assertEquals(PublisherIdentityResult.FailureReason.URL_NOT_IN_SAN, result.getFailureReason());
    }

    @Test
    public void rejectsUntrustedRoot() throws Exception {
        // Build an alternate root and have the identity cert chain to it instead.
        KeyPair otherRootKp = generateKeyPair();
        X509Certificate otherRoot = buildCert(
                "CN=Other Root", otherRootKp,
                "CN=Other Root", otherRootKp,
                now(), plusYears(20), true, null, null
        );
        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        X509Certificate identity = buildIdentity(otherRootKp, otherRoot, "CN=Acme Corp",
                Collections.singletonList(url), now(), plusYears(1));

        PublisherIdentityResult result = newVerifier(map(url, pem(identity))).verify(
                Collections.singletonList(url),
                Collections.singletonList(rootCert),  // pinned root != otherRoot
                Collections.singletonList(codesignCert)
        );

        assertFalse(result.isVerified());
        assertEquals(PublisherIdentityResult.FailureReason.CHAIN_INVALID, result.getFailureReason());
    }

    @Test
    public void rejectsExpiredIdentityCert() throws Exception {
        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        Date past = new Date(System.currentTimeMillis() - 2 * ONE_YEAR_MS);
        Date alsoPast = new Date(System.currentTimeMillis() - ONE_YEAR_MS);
        X509Certificate identity = buildIdentity(rootKp, rootCert, "CN=Acme Corp",
                Collections.singletonList(url), past, alsoPast);

        PublisherIdentityResult result = newVerifier(map(url, pem(identity))).verify(
                Collections.singletonList(url),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );

        assertFalse(result.isVerified());
        assertEquals(PublisherIdentityResult.FailureReason.CERT_EXPIRED, result.getFailureReason());
    }

    @Test
    public void rejectsCodesignFromDifferentRoot() throws Exception {
        KeyPair otherRootKp = generateKeyPair();
        X509Certificate otherRoot = buildCert(
                "CN=Other Root", otherRootKp,
                "CN=Other Root", otherRootKp,
                now(), plusYears(20), true, null, null
        );
        X509Certificate otherCodesign = buildCert(
                "CN=Other Codesign", generateKeyPair(),
                "CN=Other Root", otherRootKp,
                now(), plusYears(2),
                false, Collections.singletonList(KeyPurposeId.id_kp_codeSigning), null
        );

        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        X509Certificate identity = buildIdentity(rootKp, rootCert, "CN=Acme Corp",
                Collections.singletonList(url), now(), plusYears(1));

        PublisherIdentityResult result = newVerifier(map(url, pem(identity))).verify(
                Collections.singletonList(url),
                Collections.singletonList(rootCert),
                Collections.singletonList(otherCodesign)
        );

        assertFalse(result.isVerified());
        assertEquals(PublisherIdentityResult.FailureReason.CODESIGN_ROOT_MISMATCH, result.getFailureReason());
    }

    @Test
    public void reportsFetchFailures() {
        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        PublisherIdentityFetcher failing = u -> { throw new IOException("connection refused"); };
        PublisherIdentityResult result = new PublisherIdentityVerifier(failing).verify(
                Collections.singletonList(url),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );
        assertFalse(result.isVerified());
        assertEquals(PublisherIdentityResult.FailureReason.FETCH_FAILED, result.getFailureReason());
        assertNotNull(result.getDetail());
        assertTrue(result.getDetail().contains("connection refused"));
    }

    @Test
    public void noUrlsReturnsNoUrlsConfigured() {
        PublisherIdentityResult result = new PublisherIdentityVerifier(stubFetcher(Collections.emptyMap()))
                .verify(Collections.emptyList(),
                        Collections.singletonList(rootCert),
                        Collections.singletonList(codesignCert));
        assertEquals(PublisherIdentityResult.FailureReason.NO_URLS_CONFIGURED,
                result.getFailureReason());
    }

    @Test
    public void firstVerifiedUrlWins() throws Exception {
        String url1 = "https://primary.example.com/.well-known/jdeploy-publisher.cer";
        String url2 = "https://secondary.example.com/.well-known/jdeploy-publisher.cer";
        X509Certificate identity = buildIdentity(rootKp, rootCert, "CN=Acme Corp",
                Arrays.asList(url1, url2), now(), plusYears(1));

        Map<String, byte[]> served = new HashMap<>();
        served.put(url1, pem(identity));
        served.put(url2, pem(identity));

        AtomicInteger calls = new AtomicInteger();
        PublisherIdentityFetcher counting = u -> {
            calls.incrementAndGet();
            return served.get(u);
        };

        PublisherIdentityResult result = new PublisherIdentityVerifier(counting).verify(
                Arrays.asList(url1, url2),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );

        assertTrue(result.isVerified());
        assertEquals(url1, result.getUrl());
        assertEquals(1, calls.get(), "verifier should short-circuit after first match");
    }

    // --- helpers ---

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static Date now() { return new Date(); }

    private static Date plusYears(int years) {
        return new Date(System.currentTimeMillis() + (long) years * ONE_YEAR_MS);
    }

    private X509Certificate buildIdentity(
            KeyPair issuerKp,
            X509Certificate issuerCert,
            String subjectDn,
            List<String> sanUris,
            Date notBefore,
            Date notAfter
    ) throws Exception {
        return buildCert(
                subjectDn, generateKeyPair(),
                issuerCert.getSubjectX500Principal().getName(), issuerKp,
                notBefore, notAfter,
                false,
                Collections.singletonList(KeyPurposeId.getInstance(new ASN1ObjectIdentifier(EKU))),
                sanUris
        );
    }

    /**
     * Builds an X.509 v3 cert. If subjectKp == issuerKp (and subject DN == issuer DN)
     * the cert is self-signed.
     */
    private X509Certificate buildCert(
            String subjectDn, KeyPair subjectKp,
            String issuerDn, KeyPair issuerKp,
            Date notBefore, Date notAfter,
            boolean isCa,
            List<KeyPurposeId> ekus,
            List<String> sanUris
    ) throws Exception {
        BigInteger serial = new BigInteger(64, new java.security.SecureRandom());
        PublicKey subjectPub = subjectKp.getPublic();
        PrivateKey issuerPriv = issuerKp.getPrivate();

        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                new X500Name(issuerDn),
                serial,
                notBefore,
                notAfter,
                new X500Name(subjectDn),
                subjectPub
        );

        if (isCa) {
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            b.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        } else {
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            b.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature));
        }

        if (ekus != null && !ekus.isEmpty()) {
            ASN1EncodableVector v = new ASN1EncodableVector();
            for (KeyPurposeId k : ekus) v.add(k);
            b.addExtension(Extension.extendedKeyUsage, false,
                    ExtendedKeyUsage.getInstance(new DERSequence(v)));
        }

        if (sanUris != null && !sanUris.isEmpty()) {
            GeneralName[] names = new GeneralName[sanUris.size()];
            for (int i = 0; i < sanUris.size(); i++) {
                names[i] = new GeneralName(GeneralName.uniformResourceIdentifier, sanUris.get(i));
            }
            b.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(issuerPriv);
        return new JcaX509CertificateConverter().getCertificate(b.build(signer));
    }

    private static byte[] pem(X509Certificate cert) throws Exception {
        java.util.Base64.Encoder enc = java.util.Base64.getMimeEncoder(64,
                "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        String body = enc.encodeToString(cert.getEncoded());
        String s = "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
        return s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static Map<String, byte[]> map(String url, byte[] body) {
        Map<String, byte[]> m = new HashMap<>();
        m.put(url, body);
        return m;
    }

    private static PublisherIdentityFetcher stubFetcher(Map<String, byte[]> data) {
        return url -> {
            byte[] v = data.get(url);
            if (v == null) throw new IOException("not found: " + url);
            return v;
        };
    }

    private static PublisherIdentityVerifier newVerifier(Map<String, byte[]> data) {
        return new PublisherIdentityVerifier(stubFetcher(data));
    }
}
