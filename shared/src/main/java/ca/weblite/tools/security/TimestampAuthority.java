package ca.weblite.tools.security;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenInfo;
import org.bouncycastle.util.Store;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RFC 3161 timestamps for signed packages.
 *
 * <p>The signer asks a timestamp authority (TSA) to countersign the SHA-256 of
 * the manifest signature, and stores the token beside it as
 * {@value #TOKEN_FILENAME}. A verifier that finds the token takes the TSA's
 * time as the signing time instead of the manifest's own timestamp, which the
 * signer chooses: so a leaked signing key cannot back-date a package into its
 * certificate's validity window.</p>
 *
 * <p>Uses BouncyCastle, an optional dependency of this module: the class is
 * loaded only when a TSA is configured or a token is present.</p>
 */
public final class TimestampAuthority {

    /** The token's file name in a signed package directory. */
    public static final String TOKEN_FILENAME = "jdeploy.mf.tsr";

    /** Sends a DER timestamp request and returns the DER response. */
    public interface Transport {
        byte[] send(byte[] request) throws IOException;
    }

    /** Thrown when a token is missing, malformed, or does not verify. */
    public static class TimestampException extends Exception {
        private final boolean untrustedAuthority;

        TimestampException(String message, boolean untrustedAuthority, Throwable cause) {
            super(message, cause);
            this.untrustedAuthority = untrustedAuthority;
        }

        /** True when the token is intact but its TSA does not chain to a trusted root. */
        public boolean isUntrustedAuthority() {
            return untrustedAuthority;
        }
    }

    private TimestampAuthority() {
    }

    /** An HTTP transport to the TSA at {@code url} (RFC 3161 section 3.4). */
    public static Transport http(final String url) {
        return request -> {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("Content-Type", "application/timestamp-query");
            connection.setRequestProperty("Accept", "application/timestamp-reply");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(request);
            }
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("Timestamp authority " + url + " answered HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    body.write(buffer, 0, n);
                }
                return body.toByteArray();
            }
        };
    }

    /**
     * Requests a token over {@code data} (the manifest signature) and returns it
     * DER-encoded, after checking the response's status, nonce and imprint.
     */
    public static byte[] requestToken(Transport transport, byte[] data) throws Exception {
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        BigInteger nonce = new BigInteger(64, new SecureRandom());
        TimeStampRequest request = generator.generate(TSPAlgorithms.SHA256, sha256(data), nonce);
        TimeStampResponse response = new TimeStampResponse(transport.send(request.getEncoded()));
        response.validate(request);
        TimeStampToken token = response.getTimeStampToken();
        if (token == null) {
            throw new IOException("Timestamp authority refused the request: " + response.getStatusString());
        }
        return token.getEncoded();
    }

    /**
     * Verifies {@code token} over {@code data} and returns the TSA's time.
     *
     * @param tsaTrustAnchors roots a TSA certificate must chain to; null means
     *                        the JVM's default trust store
     */
    public static Date verifyToken(byte[] token, byte[] data, KeyStore tsaTrustAnchors) throws TimestampException {
        TimeStampToken timeStampToken;
        TimeStampTokenInfo info;
        X509CertificateHolder signerHolder;
        List<X509Certificate> certificates = new ArrayList<>();
        try {
            timeStampToken = new TimeStampToken(new CMSSignedData(token));
            info = timeStampToken.getTimeStampInfo();
            if (!NISTObjectIdentifiers.id_sha256.equals(info.getMessageImprintAlgOID())
                    || !Arrays.equals(info.getMessageImprintDigest(), sha256(data))) {
                throw new TimestampException("The timestamp is not for this package's manifest signature", false, null);
            }
            Store<X509CertificateHolder> store = timeStampToken.getCertificates();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = store.getMatches(timeStampToken.getSID());
            if (matches.isEmpty()) {
                throw new TimestampException("The timestamp does not include its authority's certificate", false, null);
            }
            signerHolder = matches.iterator().next();
            // Checks the TSA's signature, the signing-certificate attribute, the
            // certificate's critical timeStamping key usage, and that the time lies
            // within the certificate's validity.
            timeStampToken.validate(new JcaSimpleSignerInfoVerifierBuilder().build(signerHolder));
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> all = store.getMatches(null);
            for (X509CertificateHolder holder : all) {
                certificates.add(converter.getCertificate(holder));
            }
        } catch (TimestampException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TimestampException("The timestamp does not verify: " + ex.getMessage(), false, ex);
        }

        Date time = info.getGenTime();
        try {
            X509Certificate signer = new JcaX509CertificateConverter().getCertificate(signerHolder);
            X509CertSelector target = new X509CertSelector();
            target.setCertificate(signer);
            PKIXBuilderParameters params = new PKIXBuilderParameters(trustAnchors(tsaTrustAnchors), target);
            params.setRevocationEnabled(false);
            params.setDate(time);
            params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(certificates)));
            CertPathBuilder.getInstance("PKIX").build(params);
        } catch (Exception ex) {
            throw new TimestampException(
                    "The timestamp authority is not trusted: " + ex.getMessage(), true, ex);
        }
        return time;
    }

    private static Set<TrustAnchor> trustAnchors(KeyStore keyStore) throws Exception {
        Set<TrustAnchor> anchors = new HashSet<>();
        if (keyStore != null) {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isCertificateEntry(alias)) {
                    anchors.add(new TrustAnchor((X509Certificate) keyStore.getCertificate(alias), null));
                }
            }
            return anchors;
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (javax.net.ssl.TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                for (X509Certificate root : ((X509TrustManager) manager).getAcceptedIssuers()) {
                    anchors.add(new TrustAnchor(root, null));
                }
            }
        }
        return anchors;
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
}
