package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.services.GeneratePublisherIdentityCertService;
import ca.weblite.tools.security.PublisherIdentityResult;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-7 coverage: identity certs that chain {@code Root -> Identity}
 * (option-a) verify when the root is supplied via the bundle's
 * {@code app.xml} {@code trusted-certificates} attribute, even though the
 * codesign cert in the .exe never signed the identity cert.
 *
 * <p>Lives in the {@code cli} module rather than {@code installer} because
 * the installer's test classpath doesn't include BouncyCastle, and BC is
 * needed to mint the test certificates and write the app.xml file. The cli
 * module depends on installer so it can call into the installer types.
 */
public class PublisherVerificationServiceAppXmlRootsTest {

    private static final long ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000;

    @TempDir
    Path tmp;

    private KeyPair rootKp;
    private X509Certificate rootCert;
    private X509Certificate codesignCert;
    private File appXmlFile;
    private File codesignCertFile;

    @BeforeEach
    public void setUp() throws Exception {
        rootKp = generateKeyPair();
        rootCert = buildRootCa(rootKp);

        // Codesign cert is a leaf (CA:FALSE), signed by the root.
        KeyPair codesignKp = generateKeyPair();
        codesignCert = buildLeaf(codesignKp, rootKp, rootCert,
                "CN=Test Codesign,O=Test", false);

        appXmlFile = tmp.resolve("app.xml").toFile();
        writeAppXml(appXmlFile.toPath(), rootCert);
        codesignCertFile = tmp.resolve("codesign.cer").toFile();
        Files.write(codesignCertFile.toPath(), codesignCert.getEncoded());
    }

    @Test
    public void optionAChainVerifiesViaAppXmlRoot() throws Exception {
        // Mint the identity cert directly from the root (option-a). The codesign
        // cert plays no role in this chain.
        File issuerKey = tmp.resolve("root.key").toFile();
        File issuerCert = tmp.resolve("root.crt").toFile();
        writeKeyPem(issuerKey.toPath(), rootKp.getPrivate());
        writeCertPem(issuerCert.toPath(), rootCert);

        File identityPem = tmp.resolve("publisher-identity.pem").toFile();
        new GeneratePublisherIdentityCertService().generate(
                new GeneratePublisherIdentityCertService.Request()
                        .issuerKeyFile(issuerKey)
                        .issuerCertFile(issuerCert)
                        .addDomain("acme.example.com")
                        .validityDays(60)
                        .outputFile(identityPem)
                        .writePrivateKey(false));

        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        PublisherVerificationService svc = new PublisherVerificationService(
                new ca.weblite.tools.security.PublisherIdentityVerifier(
                        u -> Files.readAllBytes(identityPem.toPath())));

        PublisherIdentityResult r = svc.verify(
                Collections.singletonList(url), null, codesignCertFile, appXmlFile);
        assertTrue(r.isVerified(), "expected VERIFIED, got " + r);
        assertEquals("acme.example.com", r.getDisplayName());
    }

    @Test
    public void optionBChainStillVerifiesAfterAddingRoots() throws Exception {
        // Codesign cert is set up as a CA:TRUE sub-CA so it can issue the identity cert
        // (option-b). Even with root.crt in app.xml, this chain (Codesign -> Identity)
        // must still verify because the codesign cert is in the trust anchor set too.
        KeyPair issuingCsKp = generateKeyPair();
        X509Certificate issuingCs = buildLeaf(issuingCsKp, rootKp, rootCert,
                "CN=Test Codesign Sub-CA,O=Test", true);
        File issuingCsKeyFile = tmp.resolve("subca.key").toFile();
        File issuingCsCertFile = tmp.resolve("subca.crt").toFile();
        writeKeyPem(issuingCsKeyFile.toPath(), issuingCsKp.getPrivate());
        writeCertPem(issuingCsCertFile.toPath(), issuingCs);

        File identityPem = tmp.resolve("identity-b.pem").toFile();
        new GeneratePublisherIdentityCertService().generate(
                new GeneratePublisherIdentityCertService.Request()
                        .issuerKeyFile(issuingCsKeyFile)
                        .issuerCertFile(issuingCsCertFile)
                        .addDomain("acme.example.com")
                        .outputFile(identityPem)
                        .writePrivateKey(false));

        // The codesign cert in the .exe now IS the issuing sub-CA (typical for option-b).
        File issuingCsAsCodesignFile = tmp.resolve("issuingCs.cer").toFile();
        Files.write(issuingCsAsCodesignFile.toPath(), issuingCs.getEncoded());

        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        PublisherVerificationService svc = new PublisherVerificationService(
                new ca.weblite.tools.security.PublisherIdentityVerifier(
                        u -> Files.readAllBytes(identityPem.toPath())));

        PublisherIdentityResult r = svc.verify(
                Collections.singletonList(url), null,
                issuingCsAsCodesignFile, appXmlFile);
        assertTrue(r.isVerified(), "expected VERIFIED, got " + r);
    }

    @Test
    public void identityNotChainingToAnyAnchorFails() throws Exception {
        // Mint an identity cert from a fresh root that is NOT in app.xml.
        KeyPair otherRootKp = generateKeyPair();
        X509Certificate otherRoot = buildRootCa(otherRootKp);
        File otherKey = tmp.resolve("other.key").toFile();
        File otherCert = tmp.resolve("other.crt").toFile();
        writeKeyPem(otherKey.toPath(), otherRootKp.getPrivate());
        writeCertPem(otherCert.toPath(), otherRoot);

        File identityPem = tmp.resolve("identity-orphan.pem").toFile();
        new GeneratePublisherIdentityCertService().generate(
                new GeneratePublisherIdentityCertService.Request()
                        .issuerKeyFile(otherKey)
                        .issuerCertFile(otherCert)
                        .addDomain("acme.example.com")
                        .outputFile(identityPem)
                        .writePrivateKey(false));

        String url = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        PublisherVerificationService svc = new PublisherVerificationService(
                new ca.weblite.tools.security.PublisherIdentityVerifier(
                        u -> Files.readAllBytes(identityPem.toPath())));

        PublisherIdentityResult r = svc.verify(
                Collections.singletonList(url), null, codesignCertFile, appXmlFile);
        assertEquals(PublisherIdentityResult.FailureReason.CHAIN_INVALID, r.getFailureReason());
    }

    // --- helpers ---

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static X509Certificate buildRootCa(KeyPair kp) throws Exception {
        Date nb = new Date();
        Date na = new Date(nb.getTime() + 20 * ONE_YEAR_MS);
        X500Name dn = new X500Name("CN=Test Root,O=Test");
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                dn, BigInteger.ONE, nb, na, dn, kp.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        b.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        ContentSigner s = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(b.build(s));
    }

    private static X509Certificate buildLeaf(
            KeyPair subjectKp, KeyPair issuerKp, X509Certificate issuerCert,
            String subjectDn, boolean ca) throws Exception {
        Date nb = new Date();
        Date na = new Date(nb.getTime() + 2 * ONE_YEAR_MS);
        X500Name issuer = new org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(issuerCert).getSubject();
        X500Name subject = new X500Name(subjectDn);
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                issuer, new BigInteger(64, new SecureRandom()),
                nb, na, subject, subjectKp.getPublic());
        if (ca) {
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            b.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature));
        } else {
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        }
        ContentSigner s = new JcaContentSignerBuilder("SHA256WithRSA").build(issuerKp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(b.build(s));
    }

    private static void writeAppXml(Path p, X509Certificate root) throws Exception {
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE-----\n");
        String b64 = Base64.getEncoder().encodeToString(root.getEncoded());
        for (int i = 0; i < b64.length(); i += 64) {
            pem.append(b64, i, Math.min(b64.length(), i + 64)).append('\n');
        }
        pem.append("-----END CERTIFICATE-----\n");

        // Embed the PEM in the trusted-certificates attribute. We put the cert in
        // an attribute value, so newlines are fine inside the quoted attribute.
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<app trusted-certificates=\"" + pem + "\"/>\n";
        Files.write(p, xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeCertPem(Path p, X509Certificate cert) throws Exception {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(p), StandardCharsets.US_ASCII))) {
            w.write("-----BEGIN CERTIFICATE-----\n");
            String b64 = Base64.getEncoder().encodeToString(cert.getEncoded());
            for (int i = 0; i < b64.length(); i += 64) {
                w.write(b64.substring(i, Math.min(b64.length(), i + 64)));
                w.write('\n');
            }
            w.write("-----END CERTIFICATE-----\n");
        }
    }

    private static void writeKeyPem(Path p, PrivateKey k) throws Exception {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(p), StandardCharsets.US_ASCII))) {
            w.write("-----BEGIN PRIVATE KEY-----\n");
            String b64 = Base64.getEncoder().encodeToString(k.getEncoded());
            for (int i = 0; i < b64.length(); i += 64) {
                w.write(b64.substring(i, Math.min(b64.length(), i + 64)));
                w.write('\n');
            }
            w.write("-----END PRIVATE KEY-----\n");
        }
    }
}
