package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
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
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RFC 3161 timestamps: the timestamp authority's time, not the signer's, is
 * the signing time, so a leaked signing key cannot back-date a package.
 */
public class TimestampAuthorityTest {

    private static final String VERSION = "1.0.0";
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis());

    @TempDir
    Path tempDir;

    private KeyPair rootKeys;
    private X509Certificate root;
    private KeyPair tsaRootKeys;
    private X509Certificate tsaRoot;
    private KeyPair tsaKeys;
    private X509Certificate tsaCert;
    private KeyStore tsaTrust;

    @BeforeEach
    public void setUp() throws Exception {
        rootKeys = rsa();
        root = cert("CN=Signing Root", rootKeys, "CN=Signing Root", rootKeys.getPrivate(), -1000, 3650, Kind.CA);
        tsaRootKeys = rsa();
        tsaRoot = cert("CN=TSA Root", tsaRootKeys, "CN=TSA Root", tsaRootKeys.getPrivate(), -1000, 3650, Kind.CA);
        tsaKeys = rsa();
        tsaCert = cert("CN=Test TSA", tsaKeys, "CN=TSA Root", tsaRootKeys.getPrivate(), -1000, 3650, Kind.TSA);
        tsaTrust = trustStore(Collections.singletonList(tsaRoot));
    }

    @Test
    public void timestampedPackageVerifies() throws Exception {
        KeyPair keys = rsa();
        X509Certificate signing = cert("CN=Signing", keys, "CN=Signing Root", rootKeys.getPrivate(), -1, 730, Kind.SIGNING);
        Path dir = sign("ok", keys, signing, new Date(), tsaAt(new Date()));
        assertTrue(Files.exists(dir.resolve(TimestampAuthority.TOKEN_FILENAME)));
        assertEquals(VerificationResult.SIGNED_CORRECTLY, verify(dir, true, tsaTrust));
    }

    @Test
    public void backDatedPackageFromLeakedExpiredKeyIsUntrusted() throws Exception {
        // The certificate expired 10 days ago; the attacker back-dates the
        // manifest into its validity window, but the TSA records the real time.
        KeyPair keys = rsa();
        X509Certificate expired = cert("CN=Old", keys, "CN=Signing Root", rootKeys.getPrivate(), -400, -10, Kind.SIGNING);
        Date backDated = new Date(System.currentTimeMillis() - 100 * DAY);
        Path dir = sign("leak", keys, expired, backDated, tsaAt(new Date()));
        assertEquals(VerificationResult.UNTRUSTED_CERTIFICATE, verify(dir, true, tsaTrust));
    }

    @Test
    public void packageTimestampedWhileCertificateWasValidVerifiesAfterExpiry() throws Exception {
        KeyPair keys = rsa();
        X509Certificate expired = cert("CN=Old", keys, "CN=Signing Root", rootKeys.getPrivate(), -400, -10, Kind.SIGNING);
        Date then = new Date(System.currentTimeMillis() - 100 * DAY);
        Path dir = sign("old", keys, expired, then, tsaAt(then));
        assertEquals(VerificationResult.SIGNED_CORRECTLY, verify(dir, true, tsaTrust));
    }

    @Test
    public void missingTimestampIsUntrustedWhenRequired() throws Exception {
        KeyPair keys = rsa();
        X509Certificate signing = cert("CN=Signing", keys, "CN=Signing Root", rootKeys.getPrivate(), -1, 730, Kind.SIGNING);
        Path dir = sign("none", keys, signing, new Date(), null);
        assertEquals(VerificationResult.UNTRUSTED_CERTIFICATE, verify(dir, true, tsaTrust));
        // Not required: the manifest timestamp is used, as before.
        assertEquals(VerificationResult.SIGNED_CORRECTLY, verify(dir, false, tsaTrust));
    }

    @Test
    public void tokenForAnotherPackageIsASignatureMismatch() throws Exception {
        KeyPair keys = rsa();
        X509Certificate signing = cert("CN=Signing", keys, "CN=Signing Root", rootKeys.getPrivate(), -1, 730, Kind.SIGNING);
        Path a = sign("a", keys, signing, new Date(), tsaAt(new Date()));
        Path b = sign("b", keys, signing, new Date(), tsaAt(new Date()));
        Files.copy(a.resolve(TimestampAuthority.TOKEN_FILENAME), b.resolve(TimestampAuthority.TOKEN_FILENAME),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertEquals(VerificationResult.SIGNATURE_MISMATCH, verify(b, true, tsaTrust));
    }

    @Test
    public void untrustedTimestampAuthorityIsUntrusted() throws Exception {
        KeyPair keys = rsa();
        X509Certificate signing = cert("CN=Signing", keys, "CN=Signing Root", rootKeys.getPrivate(), -1, 730, Kind.SIGNING);
        Path dir = sign("tsa", keys, signing, new Date(), tsaAt(new Date()));
        KeyPair otherKeys = rsa();
        X509Certificate otherRoot = cert("CN=Other", otherKeys, "CN=Other", otherKeys.getPrivate(), -1000, 3650, Kind.CA);
        assertEquals(VerificationResult.UNTRUSTED_CERTIFICATE,
                verify(dir, true, trustStore(Collections.singletonList(otherRoot))));
    }

    @Test
    public void resigningWithoutATimestampRemovesTheStaleToken() throws Exception {
        KeyPair keys = rsa();
        X509Certificate signing = cert("CN=Signing", keys, "CN=Signing Root", rootKeys.getPrivate(), -1, 730, Kind.SIGNING);
        Path dir = sign("resign", keys, signing, new Date(), tsaAt(new Date()));
        FileSigner.signDirectory(VERSION, dir.toString(), provider(keys, signing), new Date(), null);
        assertFalse(Files.exists(dir.resolve(TimestampAuthority.TOKEN_FILENAME)));
        assertEquals(VerificationResult.SIGNED_CORRECTLY, verify(dir, false, tsaTrust));
    }

    // ---------------------------------------------------------------------

    private Path sign(String name, KeyPair keys, X509Certificate signing, Date signedAt,
                      TimestampAuthority.Transport tsa) throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve(name));
        Files.write(dir.resolve("app.jar"), ("contents of " + name).getBytes(StandardCharsets.UTF_8));
        FileSigner.signDirectory(VERSION, dir.toString(), provider(keys, signing), signedAt, tsa);
        return dir;
    }

    private VerificationResult verify(Path dir, boolean requireTimestamp, KeyStore tsaAnchors) throws Exception {
        KeyStore pins = trustStore(Collections.singletonList(root));
        return FileVerifier.verifyDirectory(VERSION, dir.toString(), new SimpleCertificateVerifier(pins),
                requireTimestamp, tsaAnchors);
    }

    /** A local TSA that answers with the given time. */
    private TimestampAuthority.Transport tsaAt(Date genTime) {
        return requestBytes -> {
            try {
                TimeStampTokenGenerator tokenGenerator = new TimeStampTokenGenerator(
                        new JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", tsaKeys.getPrivate(), tsaCert),
                        new JcaDigestCalculatorProviderBuilder().build().get(
                                new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                        org.bouncycastle.asn1.oiw.OIWObjectIdentifiers.idSHA1)),
                        new ASN1ObjectIdentifier("1.2.3.4.1"));
                tokenGenerator.addCertificates(new JcaCertStore(Arrays.asList(tsaCert, tsaRoot)));
                TimeStampResponseGenerator responseGenerator =
                        new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED);
                return responseGenerator.generate(new TimeStampRequest(requestBytes),
                        BigInteger.valueOf(SERIAL.incrementAndGet()), genTime).getEncoded();
            } catch (Exception ex) {
                throw new java.io.IOException(ex);
            }
        };
    }

    private static KeyProvider provider(KeyPair keys, X509Certificate signing) {
        return new KeyProvider() {
            public PrivateKey getSigningKey() { return keys.getPrivate(); }
            public Certificate getSigningCertificate() { return signing; }
            public List<Certificate> getSigningCertificateChain() { return Collections.singletonList(signing); }
            public List<Certificate> getTrustedCertificates() { return Collections.singletonList(signing); }
        };
    }

    private static KeyStore trustStore(List<X509Certificate> certs) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        for (int i = 0; i < certs.size(); i++) {
            ks.setCertificateEntry("c" + i, certs.get(i));
        }
        return ks;
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private enum Kind { CA, SIGNING, TSA }

    private static X509Certificate cert(String subject, KeyPair subjectKeys, String issuer, PrivateKey issuerKey,
                                        int fromDays, int toDays, Kind kind) throws Exception {
        long now = System.currentTimeMillis();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(issuer), BigInteger.valueOf(SERIAL.incrementAndGet()),
                new Date(now + fromDays * DAY), new Date(now + toDays * DAY),
                new X500Name(subject), subjectKeys.getPublic());
        switch (kind) {
            case CA:
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
                break;
            case SIGNING:
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
                builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning));
                break;
            case TSA:
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
                builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
                break;
        }
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey)));
    }
}
