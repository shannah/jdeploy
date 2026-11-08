package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.weblite.tools.env.EnvVarProvider;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * A long-lived root pinned by installers, with shorter-lived signing
 * certificates issued from it: the root must be pinned and shipped in the
 * package's chain, so that a release signed with a new signing certificate from
 * the same root still verifies on an install that pinned the old one.
 */
public class RootCertificatePinningTest {

    private static final String VERSION = "1.0.0";
    private static final long DAY = 24L * 60 * 60 * 1000;

    private KeyPair rootKeys;
    private X509Certificate root;
    private KeyPair leaf1Keys;
    private X509Certificate leaf1;
    private KeyPair leaf2Keys;
    private X509Certificate leaf2;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        rootKeys = rsa();
        root = certificate("CN=Test Root", rootKeys, "CN=Test Root", rootKeys, true, 3650);
        leaf1Keys = rsa();
        leaf1 = certificate("CN=Signing 1", leaf1Keys, "CN=Test Root", rootKeys, false, 365);
        leaf2Keys = rsa();
        leaf2 = certificate("CN=Signing 2", leaf2Keys, "CN=Test Root", rootKeys, false, 365);
    }

    @Test
    public void withoutRootOnlyTheSigningCertificateIsPinned() throws Exception {
        KeyProvider provider = envProvider(leaf1Keys, leaf1, null);
        assertEquals(Arrays.asList(leaf1), provider.getTrustedCertificates());
        assertEquals(Arrays.asList(leaf1), provider.getSigningCertificateChain());
    }

    @Test
    public void rootIsPinnedAndShippedInTheChain() throws Exception {
        KeyProvider provider = envProvider(leaf1Keys, leaf1, root);
        assertEquals(Arrays.asList(leaf1, root), provider.getTrustedCertificates());
        assertEquals(Arrays.asList(leaf1, root), provider.getSigningCertificateChain());
    }

    @Test
    public void rootThatDidNotIssueTheSigningCertificateIsRejected() throws Exception {
        KeyPair otherKeys = rsa();
        X509Certificate otherRoot = certificate("CN=Other Root", otherKeys, "CN=Other Root", otherKeys, true, 3650);
        KeyProvider provider = envProvider(leaf1Keys, leaf1, otherRoot);
        Exception ex = assertThrows(Exception.class, provider::getTrustedCertificates);
        assertTrue(ex.getMessage().contains("did not issue the signing certificate"), ex.getMessage());
    }

    @Test
    public void newSigningCertificateFromSameRootVerifiesAgainstOldPins() throws Exception {
        // An install of release 1 pins what release 1's provider trusted.
        KeyStore pinnedByRelease1 = trustStore(envProvider(leaf1Keys, leaf1, root).getTrustedCertificates());

        // Release 1 itself verifies.
        assertEquals(VerificationResult.SIGNED_CORRECTLY,
                signAndVerify("release1", envProvider(leaf1Keys, leaf1, root), pinnedByRelease1));

        // Release 2 is signed with a new signing certificate from the same root.
        assertEquals(VerificationResult.SIGNED_CORRECTLY,
                signAndVerify("release2", envProvider(leaf2Keys, leaf2, root), pinnedByRelease1));
    }

    @Test
    public void newSigningCertificateIsUntrustedWhenOnlyTheOldOneWasPinned() throws Exception {
        KeyStore pinnedWithoutRoot = trustStore(envProvider(leaf1Keys, leaf1, null).getTrustedCertificates());
        assertEquals(VerificationResult.UNTRUSTED_CERTIFICATE,
                signAndVerify("release2", envProvider(leaf2Keys, leaf2, root), pinnedWithoutRoot));
    }

    @Test
    public void fileKeyProviderPinsTheRootToo() throws Exception {
        Path key = Files.write(tempDir.resolve("key.der"), leaf1Keys.getPrivate().getEncoded());
        Path cert = Files.write(tempDir.resolve("cert.pem"), pem(leaf1).getBytes(StandardCharsets.UTF_8));
        Path rootPem = Files.write(tempDir.resolve("root.pem"), pem(root).getBytes(StandardCharsets.UTF_8));

        KeyProvider provider = new FileKeyProvider(key.toString(), cert.toString(), rootPem.toString());
        assertEquals(Arrays.asList(leaf1, root), provider.getTrustedCertificates());
        assertEquals(Arrays.asList(leaf1, root), provider.getSigningCertificateChain());

        KeyProvider withoutRoot = new FileKeyProvider(key.toString(), cert.toString());
        assertEquals(Arrays.asList(leaf1), withoutRoot.getTrustedCertificates());
    }

    private VerificationResult signAndVerify(String name, KeyProvider provider, KeyStore pins) throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve(name));
        Files.write(dir.resolve("app.jar"), ("contents of " + name).getBytes(StandardCharsets.UTF_8));
        FileSigner.signDirectory(VERSION, dir.toString(), provider);
        return FileVerifier.verifyDirectory(VERSION, dir.toString(), new SimpleCertificateVerifier(pins));
    }

    private static KeyStore trustStore(List<Certificate> pinned) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        for (int i = 0; i < pinned.size(); i++) {
            ks.setCertificateEntry("pin" + i, pinned.get(i));
        }
        return ks;
    }

    private static KeyProvider envProvider(KeyPair keys, X509Certificate cert, X509Certificate root) throws Exception {
        EnvVarProvider env = mock(EnvVarProvider.class);
        when(env.getEnv("JDEPLOY_PRIVATE_KEY")).thenReturn(
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----");
        when(env.getEnv("JDEPLOY_CERTIFICATE")).thenReturn(pem(cert));
        when(env.getEnv("JDEPLOY_ROOT_CERTIFICATE")).thenReturn(root == null ? null : pem(root));
        return new EnvKeyProvider(env);
    }

    private static String pem(Certificate cert) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(cert.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static X509Certificate certificate(
            String subject, KeyPair subjectKeys, String issuer, KeyPair issuerKeys, boolean ca, int days
    ) throws Exception {
        long now = System.currentTimeMillis();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(issuer),
                BigInteger.valueOf(now).add(BigInteger.valueOf(Arrays.hashCode(subjectKeys.getPublic().getEncoded()))).abs(),
                new Date(now - DAY),
                new Date(now + days * DAY),
                new X500Name(subject),
                subjectKeys.getPublic()
        );
        builder.addExtension(Extension.basicConstraints, true, ca ? new BasicConstraints(0) : new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(ca ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256WithRSA").build(issuerKeys.getPrivate())));
    }
}
