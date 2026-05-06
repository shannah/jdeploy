package ca.weblite.jdeploy.services;

import ca.weblite.tools.security.PublisherIdentityFetcher;
import ca.weblite.tools.security.PublisherIdentityResult;
import ca.weblite.tools.security.PublisherIdentityVerifier;
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
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratePublisherIdentityCertServiceTest {

    private static final long ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000;

    @TempDir
    Path tmp;

    private KeyPair rootKp;
    private X509Certificate rootCert;
    private File rootKeyPem;
    private File rootCertPem;

    @BeforeEach
    public void setUp() throws Exception {
        rootKp = generateKeyPair();
        rootCert = buildSelfSignedRoot(rootKp);
        rootKeyPem = tmp.resolve("root.key").toFile();
        rootCertPem = tmp.resolve("root.crt").toFile();
        writePrivateKeyPem(rootKeyPem.toPath(), rootKp.getPrivate());
        writeCertPem(rootCertPem.toPath(), rootCert);
    }

    @Test
    public void roundTripDirectFromRoot() throws Exception {
        File outCert = tmp.resolve("publisher-identity.pem").toFile();
        GeneratePublisherIdentityCertService.Request req =
                new GeneratePublisherIdentityCertService.Request()
                        .issuerKeyFile(rootKeyPem)
                        .issuerCertFile(rootCertPem)
                        .addDomain("acme.example.com")
                        .addGithub("acme/widget")
                        .validityDays(180)
                        .subjectOrganization("Acme Corp")
                        .outputFile(outCert);

        GeneratePublisherIdentityCertService.Result result =
                new GeneratePublisherIdentityCertService().generate(req);

        assertTrue(result.certificateFile.exists());
        assertNotNull(result.privateKeyFile);
        assertTrue(result.privateKeyFile.exists());
        assertEquals(2, result.uploadUrls.size());
        assertTrue(result.uploadUrls.contains(
                "https://acme.example.com/.well-known/jdeploy-publisher.cer"));
        assertTrue(result.uploadUrls.contains(
                "https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer"));

        // Now run the verifier against the generated cert via both upload URLs.
        byte[] generatedPem = Files.readAllBytes(result.certificateFile.toPath());
        // Codesign cert (leaf) signed by the same root.
        X509Certificate codesignCert = buildCodesignLeaf(rootKp, rootCert);

        // Domain URL.
        String domainUrl = "https://acme.example.com/.well-known/jdeploy-publisher.cer";
        Map<String, byte[]> served = new HashMap<>();
        served.put(domainUrl, generatedPem);

        PublisherIdentityResult vr = new PublisherIdentityVerifier(stub(served)).verify(
                Collections.singletonList(domainUrl),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );
        assertTrue(vr.isVerified(), "domain URL should verify, got " + vr);
        assertEquals("acme.example.com", vr.getDisplayName());

        // GitHub raw URL with canonical SAN.
        String ghRawUrl = "https://raw.githubusercontent.com/acme/widget/main/.well-known/jdeploy-publisher.cer";
        served.clear();
        served.put(ghRawUrl, generatedPem);
        vr = new PublisherIdentityVerifier(stub(served)).verify(
                Collections.singletonList(ghRawUrl),
                Collections.singletonList(rootCert),
                Collections.singletonList(codesignCert)
        );
        assertTrue(vr.isVerified(), "github raw URL should verify, got " + vr);
    }

    @Test
    public void noKeyFlagSkipsPrivateKeyOutput() throws Exception {
        File outCert = tmp.resolve("identity-no-key.pem").toFile();
        GeneratePublisherIdentityCertService.Result result =
                new GeneratePublisherIdentityCertService().generate(
                        new GeneratePublisherIdentityCertService.Request()
                                .issuerKeyFile(rootKeyPem)
                                .issuerCertFile(rootCertPem)
                                .addDomain("acme.example.com")
                                .outputFile(outCert)
                                .writePrivateKey(false)
                );
        assertTrue(result.certificateFile.exists());
        assertEquals(null, result.privateKeyFile);
        assertFalse(new File(tmp.toFile(), "identity-no-key.key").exists());
    }

    @Test
    public void rejectsRequestWithoutBindings() {
        GeneratePublisherIdentityCertService.Request req =
                new GeneratePublisherIdentityCertService.Request()
                        .issuerKeyFile(rootKeyPem)
                        .issuerCertFile(rootCertPem)
                        .outputFile(tmp.resolve("foo.pem").toFile());
        try {
            new GeneratePublisherIdentityCertService().generate(req);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("--domain") || e.getMessage().contains("--github"));
            return;
        } catch (Exception other) {
            throw new AssertionError("expected IllegalArgumentException", other);
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    @Test
    public void cliControllerEndToEnd() throws Exception {
        File outCert = tmp.resolve("cli-identity.pem").toFile();
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();
        int code = ca.weblite.jdeploy.cli.controllers.GeneratePublisherIdentityCertCliController.run(
                new String[]{
                        "--issuer-key", rootKeyPem.getAbsolutePath(),
                        "--issuer-cert", rootCertPem.getAbsolutePath(),
                        "--domain", "acme.example.com",
                        "--validity-days", "30",
                        "--out", outCert.getAbsolutePath(),
                        "--no-key"
                },
                new PrintStream(stdout),
                new PrintStream(stderr));
        assertEquals(0, code, "stderr was: " + stderr.toString());
        assertTrue(outCert.exists());
        assertTrue(stdout.toString().contains("publisherVerificationUrls"));
    }

    @Test
    public void cliControllerHelpReturnsZero() {
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        int code = ca.weblite.jdeploy.cli.controllers.GeneratePublisherIdentityCertCliController.run(
                new String[]{"--help"}, new PrintStream(stdout), new PrintStream(new java.io.ByteArrayOutputStream()));
        assertEquals(0, code);
        assertTrue(stdout.toString().contains("issuer-key"));
    }

    @Test
    public void cliControllerMissingArgsReturnsTwo() {
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();
        int code = ca.weblite.jdeploy.cli.controllers.GeneratePublisherIdentityCertCliController.run(
                new String[]{}, new PrintStream(new java.io.ByteArrayOutputStream()), new PrintStream(stderr));
        assertEquals(2, code);
        assertTrue(stderr.toString().contains("issuer-key"));
    }

    // --- helpers ---

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static X509Certificate buildSelfSignedRoot(KeyPair kp) throws Exception {
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 20 * ONE_YEAR_MS);
        X500Name dn = new X500Name("CN=Test Root,O=Test");
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                dn, BigInteger.valueOf(1), notBefore, notAfter, dn, kp.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        b.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        ContentSigner s = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(b.build(s));
    }

    private static X509Certificate buildCodesignLeaf(KeyPair rootKp, X509Certificate rootCert) throws Exception {
        KeyPair kp = generateKeyPair();
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + 2 * ONE_YEAR_MS);
        X500Name issuer = new org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(rootCert).getSubject();
        X500Name subject = new X500Name("CN=Test Codesign,O=Test");
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                issuer, new BigInteger(64, new SecureRandom()),
                notBefore, notAfter, subject, kp.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        ContentSigner s = new JcaContentSignerBuilder("SHA256WithRSA").build(rootKp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(b.build(s));
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

    private static void writePrivateKeyPem(Path p, PrivateKey k) throws Exception {
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

    private static PublisherIdentityFetcher stub(Map<String, byte[]> data) {
        return url -> {
            byte[] v = data.get(url);
            if (v == null) throw new IOException("not in stub: " + url);
            return v;
        };
    }
}
