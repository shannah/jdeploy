package ca.weblite.jdeploy.services;

import ca.weblite.tools.security.PublisherIdentityVerifier;
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
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a publisher identity certificate (the "companion certificate"
 * described in {@code rfc/website-publisher-verification-plan.md}).
 *
 * <p>The output certificate is a v3 X.509 cert that:
 * <ul>
 *   <li>chains to a pinned root via the supplied issuer cert / chain,</li>
 *   <li>carries the jDeploy publisher EKU
 *       ({@link PublisherIdentityVerifier#PUBLISHER_EKU_OID}),</li>
 *   <li>embeds one or more {@code SAN URI} entries binding it to the
 *       URL(s) where it is hosted.</li>
 * </ul>
 *
 * <p>The resulting cert is a passive ownership token: its private key is not
 * used at install time. The key is written to disk alongside the cert in
 * case the publisher wants to keep it for future use, but it can be safely
 * deleted after generation without affecting verification.
 */
public class GeneratePublisherIdentityCertService {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int RSA_KEY_SIZE = 3072;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;
    private static final String WELL_KNOWN_PATH = "/.well-known/jdeploy-publisher.cer";

    public static final class Request {
        private File issuerKeyFile;
        private File issuerCertFile;
        private File chainFile;                  // optional intermediates appended to output
        private final List<String> domains = new ArrayList<>();
        private final List<String> githubRepos = new ArrayList<>();
        private int validityDays = 365;
        private String subjectCommonName;        // CN= in subject DN (default: first SAN host)
        private String subjectOrganization;      // O= (optional)
        private File outputFile;
        private File outputKeyFile;              // optional; default <output>.key
        private boolean writePrivateKey = true;

        public Request issuerKeyFile(File f) { this.issuerKeyFile = f; return this; }
        public Request issuerCertFile(File f) { this.issuerCertFile = f; return this; }
        public Request chainFile(File f) { this.chainFile = f; return this; }
        public Request addDomain(String host) { this.domains.add(host); return this; }
        public Request addGithub(String ownerSlashRepo) { this.githubRepos.add(ownerSlashRepo); return this; }
        public Request validityDays(int days) { this.validityDays = days; return this; }
        public Request subjectCommonName(String cn) { this.subjectCommonName = cn; return this; }
        public Request subjectOrganization(String o) { this.subjectOrganization = o; return this; }
        public Request outputFile(File f) { this.outputFile = f; return this; }
        public Request outputKeyFile(File f) { this.outputKeyFile = f; return this; }
        public Request writePrivateKey(boolean v) { this.writePrivateKey = v; return this; }
    }

    public static final class Result {
        public final File certificateFile;
        public final File privateKeyFile;        // null if not written
        public final List<String> sanUrls;       // canonicalised SAN URI strings
        public final List<String> uploadUrls;    // where to host the .cer
        public final List<String> verificationUrls; // for package.json publisherVerificationUrls

        Result(File cert, File key, List<String> sans, List<String> uploads, List<String> verify) {
            this.certificateFile = cert;
            this.privateKeyFile = key;
            this.sanUrls = sans;
            this.uploadUrls = uploads;
            this.verificationUrls = verify;
        }
    }

    public Result generate(Request req) throws Exception {
        validate(req);

        SansAndUrls san = buildSans(req);
        X509Certificate issuerCert = readCert(req.issuerCertFile);
        PrivateKey issuerKey = readPrivateKey(req.issuerKeyFile);
        List<X509Certificate> intermediates = readChain(req.chainFile);

        KeyPair identityKp = generateRsaKeyPair();
        X509Certificate identity = buildIdentityCert(req, san, issuerCert, issuerKey, identityKp);

        writePemCerts(req.outputFile, identity, intermediates);

        File keyFile = null;
        if (req.writePrivateKey) {
            keyFile = req.outputKeyFile != null ? req.outputKeyFile
                    : new File(stripExtension(req.outputFile.getPath()) + ".key");
            writePemPrivateKey(keyFile, identityKp.getPrivate());
        }

        return new Result(req.outputFile, keyFile, san.sans, san.uploadUrls, san.uploadUrls);
    }

    private static void validate(Request r) {
        if (r.issuerKeyFile == null) throw new IllegalArgumentException("--issuer-key is required");
        if (r.issuerCertFile == null) throw new IllegalArgumentException("--issuer-cert is required");
        if (r.outputFile == null) throw new IllegalArgumentException("--out is required");
        if (r.domains.isEmpty() && r.githubRepos.isEmpty()) {
            throw new IllegalArgumentException("at least one --domain or --github must be supplied");
        }
        if (r.validityDays <= 0) throw new IllegalArgumentException("--validity-days must be positive");
    }

    private static class SansAndUrls {
        final List<String> sans = new ArrayList<>();
        final List<String> uploadUrls = new ArrayList<>();
    }

    private static SansAndUrls buildSans(Request r) {
        SansAndUrls s = new SansAndUrls();
        Set<String> seenSan = new LinkedHashSet<>();
        Set<String> seenUpload = new LinkedHashSet<>();
        for (String domain : r.domains) {
            String host = stripScheme(domain).split("/", 2)[0];
            String url = "https://" + host + WELL_KNOWN_PATH;
            if (seenSan.add(url)) s.sans.add(url);
            if (seenUpload.add(url)) s.uploadUrls.add(url);
        }
        for (String repo : r.githubRepos) {
            String slug = stripScheme(repo);
            // Accept "owner/repo" or "github.com/owner/repo".
            if (slug.startsWith("github.com/")) slug = slug.substring("github.com/".length());
            if (slug.indexOf('/') < 0) {
                throw new IllegalArgumentException("--github expects owner/repo, got: " + repo);
            }
            String[] parts = slug.split("/", 3);
            String ownerRepo = parts[0] + "/" + parts[1];
            String sanUrl = "https://github.com/" + ownerRepo;
            if (seenSan.add(sanUrl)) s.sans.add(sanUrl);
            String upload = "https://raw.githubusercontent.com/" + ownerRepo + "/main" + WELL_KNOWN_PATH;
            if (seenUpload.add(upload)) s.uploadUrls.add(upload);
        }
        return s;
    }

    private static String stripScheme(String s) {
        int i = s.indexOf("://");
        return i >= 0 ? s.substring(i + 3) : s;
    }

    private X509Certificate buildIdentityCert(
            Request r,
            SansAndUrls san,
            X509Certificate issuerCert,
            PrivateKey issuerKey,
            KeyPair identityKp
    ) throws Exception {
        Date notBefore = new Date();
        Date notAfter = new Date(notBefore.getTime() + r.validityDays * ONE_DAY_MS);
        BigInteger serial = new BigInteger(64, new SecureRandom());

        String cn = r.subjectCommonName != null ? r.subjectCommonName : firstSanHost(san);
        StringBuilder dn = new StringBuilder("CN=").append(escape(cn));
        if (r.subjectOrganization != null) dn.append(",O=").append(escape(r.subjectOrganization));
        X500Name subjectDn = new X500Name(dn.toString());
        // Use the raw encoded subject of the issuer cert, so the issuer-name in the
        // child cert is a byte-for-byte match with the trust anchor's subject-name.
        // Round-tripping through X500Principal.getName() can re-encode attributes
        // (e.g. PrintableString -> UTF8String) and break PKIX path validation.
        X509CertificateHolder issuerHolder = new JcaX509CertificateHolder(issuerCert);
        X500Name issuerDn = issuerHolder.getSubject();

        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                issuerDn, serial, notBefore, notAfter, subjectDn, identityKp.getPublic()
        );
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));

        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(KeyPurposeId.getInstance(new ASN1ObjectIdentifier(PublisherIdentityVerifier.PUBLISHER_EKU_OID)));
        b.addExtension(Extension.extendedKeyUsage, false,
                ExtendedKeyUsage.getInstance(new DERSequence(v)));

        GeneralName[] names = new GeneralName[san.sans.size()];
        for (int i = 0; i < san.sans.size(); i++) {
            names[i] = new GeneralName(GeneralName.uniformResourceIdentifier, san.sans.get(i));
        }
        b.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerKey);
        return new JcaX509CertificateConverter().getCertificate(b.build(signer));
    }

    private static String firstSanHost(SansAndUrls san) {
        if (san.sans.isEmpty()) return "Publisher";
        String url = san.sans.get(0);
        try {
            return new java.net.URI(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private static String escape(String s) {
        return s.replace(",", "\\,").replace("=", "\\=");
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(RSA_KEY_SIZE, new SecureRandom());
        return g.generateKeyPair();
    }

    private static X509Certificate readCert(File f) throws Exception {
        try (java.io.InputStream in = Files.newInputStream(f.toPath())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private static List<X509Certificate> readChain(File f) throws Exception {
        List<X509Certificate> out = new ArrayList<>();
        if (f == null) return out;
        try (java.io.InputStream in = Files.newInputStream(f.toPath())) {
            for (java.security.cert.Certificate c :
                    CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                if (c instanceof X509Certificate) out.add((X509Certificate) c);
            }
        }
        return out;
    }

    private static PrivateKey readPrivateKey(File f) throws Exception {
        try (FileReader fr = new FileReader(f); PEMParser parser = new PEMParser(fr)) {
            Object obj = parser.readObject();
            if (obj == null) throw new IOException("No PEM object in " + f);
            JcaPEMKeyConverter conv = new JcaPEMKeyConverter();
            if (obj instanceof PEMKeyPair) {
                return conv.getKeyPair((PEMKeyPair) obj).getPrivate();
            }
            if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                return conv.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) obj);
            }
            throw new IOException("Unsupported PEM object type for issuer key: "
                    + obj.getClass().getName()
                    + " (expected PRIVATE KEY or RSA PRIVATE KEY)");
        }
    }

    private static void writePemCerts(File out, X509Certificate leaf, List<X509Certificate> intermediates)
            throws Exception {
        Path p = out.toPath();
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(p), StandardCharsets.US_ASCII))) {
            writeCertPem(w, leaf);
            for (X509Certificate c : intermediates) writeCertPem(w, c);
        }
    }

    private static void writeCertPem(Writer w, X509Certificate cert) throws Exception {
        w.write("-----BEGIN CERTIFICATE-----\n");
        writeBase64Wrapped(w, cert.getEncoded());
        w.write("-----END CERTIFICATE-----\n");
    }

    private static void writePemPrivateKey(File out, PrivateKey key) throws Exception {
        Path p = out.toPath();
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(p), StandardCharsets.US_ASCII))) {
            w.write("-----BEGIN PRIVATE KEY-----\n");
            writeBase64Wrapped(w, key.getEncoded());
            w.write("-----END PRIVATE KEY-----\n");
        }
        try {
            // Best-effort lock-down on POSIX. Ignore on platforms that don't support it.
            Files.setPosixFilePermissions(p,
                    java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows: no POSIX perms.
        }
    }

    private static void writeBase64Wrapped(Writer w, byte[] data) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(data);
        for (int i = 0; i < b64.length(); i += 64) {
            w.write(b64.substring(i, Math.min(b64.length(), i + 64)));
            w.write('\n');
        }
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (dot > slash) ? path.substring(0, dot) : path;
    }
}
