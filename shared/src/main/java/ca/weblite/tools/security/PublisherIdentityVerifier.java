package ca.weblite.tools.security;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies that a code-signed jDeploy app's publisher controls a domain (or GitHub repo)
 * by fetching a publisher identity certificate from a well-known URL and checking that:
 * <ol>
 *   <li>It chains via PKIX to a pinned root cert.</li>
 *   <li>It carries the jDeploy publisher EKU OID.</li>
 *   <li>The URL it was fetched from appears in its SAN URI list (anti-spoof).</li>
 *   <li>It is currently within its validity window.</li>
 *   <li>The application's code-signing cert chains to the same pinned root.</li>
 * </ol>
 *
 * <p>The verifier walks each candidate URL in order and returns on the first VERIFIED
 * result. If all URLs fail, the most-informative failure reason is returned.
 *
 * <p>This class is stateless and thread-safe.
 *
 * @see PublisherIdentityFetcher
 * @see PublisherIdentityResult
 */
public final class PublisherIdentityVerifier {

    /**
     * Provisional OID for the jDeploy publisher identity EKU. Replace with a PEN-rooted
     * OID once one is allocated; verifier accepts both during the migration window.
     */
    public static final String PUBLISHER_EKU_OID = "1.3.6.1.4.1.99999.42.1";

    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_RAW_HOST = "raw.githubusercontent.com";

    private final PublisherIdentityFetcher fetcher;
    private final List<String> acceptedEkuOids;

    public PublisherIdentityVerifier(PublisherIdentityFetcher fetcher) {
        this(fetcher, Collections.singletonList(PUBLISHER_EKU_OID));
    }

    public PublisherIdentityVerifier(PublisherIdentityFetcher fetcher, List<String> acceptedEkuOids) {
        if (fetcher == null) {
            throw new IllegalArgumentException("fetcher must not be null");
        }
        if (acceptedEkuOids == null || acceptedEkuOids.isEmpty()) {
            throw new IllegalArgumentException("acceptedEkuOids must not be empty");
        }
        this.fetcher = fetcher;
        this.acceptedEkuOids = Collections.unmodifiableList(new ArrayList<>(acceptedEkuOids));
    }

    /**
     * Run verification against a list of candidate URLs.
     *
     * @param candidateUrls    URLs to attempt, in priority order. Typically from
     *                         {@code package.json}'s {@code jdeploy.publisherVerificationUrls}
     *                         plus a {@code homepage}-derived fallback.
     * @param pinnedRoots      Root certs trusted by the launcher (from {@code app.xml}).
     * @param codesignChain    The code-signing leaf cert + any intermediates extracted
     *                         from the running .exe (e.g. via
     *                         {@code AuthenticodeSignatureChecker.exportCertificate}).
     * @return a non-null result; first VERIFIED match wins, otherwise the most-relevant failure.
     */
    public PublisherIdentityResult verify(
            List<String> candidateUrls,
            List<X509Certificate> pinnedRoots,
            List<X509Certificate> codesignChain
    ) {
        if (candidateUrls == null || candidateUrls.isEmpty()) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.NO_URLS_CONFIGURED,
                    "No publisherVerificationUrls configured and no homepage fallback available"
            );
        }
        if (pinnedRoots == null || pinnedRoots.isEmpty()) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.CHAIN_INVALID,
                    "No pinned root certificates supplied"
            );
        }
        if (codesignChain == null || codesignChain.isEmpty()) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.CODESIGN_ROOT_MISMATCH,
                    "No code-signing certificate supplied"
            );
        }

        Set<TrustAnchor> trustAnchors = new HashSet<>();
        for (X509Certificate root : pinnedRoots) {
            trustAnchors.add(new TrustAnchor(root, null));
        }

        if (!chainsToTrustAnchors(codesignChain, trustAnchors)) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.CODESIGN_ROOT_MISMATCH,
                    "Code-signing certificate does not chain to any pinned root"
            );
        }

        PublisherIdentityResult lastFailure = null;
        for (String url : candidateUrls) {
            PublisherIdentityResult r = verifyOne(url, trustAnchors, codesignChain);
            if (r.isVerified()) return r;
            lastFailure = r;
        }
        return lastFailure;
    }

    private PublisherIdentityResult verifyOne(
            String url,
            Set<TrustAnchor> trustAnchors,
            List<X509Certificate> codesignChain
    ) {
        byte[] body;
        try {
            body = fetcher.fetch(url);
        } catch (Exception e) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.FETCH_FAILED,
                    "Failed to fetch " + url + ": " + e.getMessage()
            );
        }

        List<X509Certificate> served;
        try {
            served = parsePemChain(body);
        } catch (Exception e) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.INVALID_PEM,
                    "Could not parse PEM at " + url + ": " + e.getMessage()
            );
        }
        if (served.isEmpty()) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.INVALID_PEM,
                    "No certificates in response from " + url
            );
        }

        X509Certificate identity = served.get(0);

        try {
            identity.checkValidity();
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.CERT_EXPIRED,
                    "Identity certificate at " + url + " is not currently valid: " + e.getMessage()
            );
        }

        if (!hasAcceptedEku(identity)) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.MISSING_EKU,
                    "Identity certificate at " + url
                            + " is missing the jDeploy publisher EKU " + acceptedEkuOids
            );
        }

        if (!sanMatchesUrl(identity, url)) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.URL_NOT_IN_SAN,
                    "Fetch URL " + url + " does not match any SAN URI on the identity certificate"
            );
        }

        // Chain: identity + intermediates from response + intermediates from codesign chain.
        // Including the codesign chain handles option-(b) where the codesign cert is the issuer
        // of the identity cert and isn't re-shipped in the well-known PEM.
        List<X509Certificate> candidateChain = new ArrayList<>(served);
        for (int i = 0; i < codesignChain.size(); i++) {
            X509Certificate c = codesignChain.get(i);
            if (!candidateChain.contains(c)) candidateChain.add(c);
        }

        if (!chainsToTrustAnchors(candidateChain, trustAnchors)) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.CHAIN_INVALID,
                    "Identity certificate at " + url + " does not chain to any pinned root"
            );
        }

        String displayName = extractDisplayName(identity);
        return PublisherIdentityResult.verified(url, displayName, identity);
    }

    private static List<X509Certificate> parsePemChain(byte[] body) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        // generateCertificates handles a stream containing one-or-more PEM (or DER) certs.
        Collection<? extends Certificate> certs;
        try (ByteArrayInputStream in = new ByteArrayInputStream(body)) {
            certs = cf.generateCertificates(in);
        }
        List<X509Certificate> out = new ArrayList<>();
        for (Certificate c : certs) {
            if (c instanceof X509Certificate) out.add((X509Certificate) c);
        }
        return out;
    }

    private boolean hasAcceptedEku(X509Certificate cert) {
        List<String> ekus;
        try {
            ekus = cert.getExtendedKeyUsage();
        } catch (Exception e) {
            return false;
        }
        if (ekus == null) return false;
        for (String oid : ekus) {
            if (acceptedEkuOids.contains(oid)) return true;
        }
        return false;
    }

    /**
     * Match the fetch URL against the leaf cert's SAN URI list.
     *
     * <p>Two forms of match are accepted:
     * <ul>
     *   <li>Exact equality after normalisation (scheme/host lowercased, default ports stripped).</li>
     *   <li>GitHub canonicalisation: a fetch from
     *       {@code https://raw.githubusercontent.com/<owner>/<repo>/<ref>/...} matches a
     *       SAN URI of {@code https://github.com/<owner>/<repo>}.</li>
     * </ul>
     */
    static boolean sanMatchesUrl(X509Certificate cert, String fetchUrl) {
        Collection<List<?>> sans;
        try {
            sans = cert.getSubjectAlternativeNames();
        } catch (Exception e) {
            return false;
        }
        if (sans == null) return false;

        URI fetchUri;
        try {
            fetchUri = new URI(fetchUrl);
        } catch (URISyntaxException e) {
            return false;
        }

        for (List<?> entry : sans) {
            if (entry == null || entry.size() < 2) continue;
            Object type = entry.get(0);
            Object value = entry.get(1);
            if (!(type instanceof Integer) || !(value instanceof String)) continue;
            int t = (Integer) type;
            // 6 == GeneralName.uniformResourceIdentifier
            if (t != 6) continue;
            String sanUrl = (String) value;
            if (urlsEquivalent(fetchUri, sanUrl)) return true;
            if (matchesGithubCanonical(fetchUri, sanUrl)) return true;
        }
        return false;
    }

    private static boolean urlsEquivalent(URI fetchUri, String sanUrl) {
        try {
            URI s = new URI(sanUrl);
            if (s.getScheme() == null || s.getHost() == null) return false;
            if (!equalsIgnoreCase(fetchUri.getScheme(), s.getScheme())) return false;
            if (!equalsIgnoreCase(fetchUri.getHost(), s.getHost())) return false;
            int fetchPort = effectivePort(fetchUri);
            int sanPort = effectivePort(s);
            if (fetchPort != sanPort) return false;
            String fetchPath = fetchUri.getPath() == null ? "" : fetchUri.getPath();
            String sanPath = s.getPath() == null ? "" : s.getPath();
            return fetchPath.equals(sanPath);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean matchesGithubCanonical(URI fetchUri, String sanUrl) {
        if (fetchUri.getHost() == null) return false;
        if (!GITHUB_RAW_HOST.equalsIgnoreCase(fetchUri.getHost())) return false;
        URI sanUri;
        try {
            sanUri = new URI(sanUrl);
        } catch (URISyntaxException e) {
            return false;
        }
        if (sanUri.getHost() == null) return false;
        if (!GITHUB_HOST.equalsIgnoreCase(sanUri.getHost())) return false;
        if (!"https".equalsIgnoreCase(sanUri.getScheme())) return false;

        String[] fetchParts = splitPath(fetchUri.getPath());
        String[] sanParts = splitPath(sanUri.getPath());
        // Fetch path: /<owner>/<repo>/<ref>/.well-known/jdeploy-publisher.cer
        // SAN path:   /<owner>/<repo>
        if (fetchParts.length < 2 || sanParts.length != 2) return false;
        return fetchParts[0].equals(sanParts[0]) && fetchParts[1].equals(sanParts[1]);
    }

    private static String[] splitPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return new String[0];
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.split("/");
    }

    private static int effectivePort(URI u) {
        if (u.getPort() != -1) return u.getPort();
        if ("https".equalsIgnoreCase(u.getScheme())) return 443;
        if ("http".equalsIgnoreCase(u.getScheme())) return 80;
        return -1;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    /**
     * Treats {@code chain[0]} as the leaf and the remaining entries (plus the leaf itself,
     * harmlessly) as a candidate pool of intermediates. Lets PKIX pick the actual path
     * order, so callers can pass in extra certs that may or may not be on the path.
     */
    private static boolean chainsToTrustAnchors(
            List<X509Certificate> chain,
            Set<TrustAnchor> anchors
    ) {
        if (chain.isEmpty()) return false;
        try {
            X509CertSelector target = new X509CertSelector();
            target.setCertificate(chain.get(0));

            CertStore store = CertStore.getInstance(
                    "Collection",
                    new CollectionCertStoreParameters(new ArrayList<>(chain))
            );

            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, target);
            params.addCertStore(store);
            params.setRevocationEnabled(false);

            CertPathBuilder.getInstance("PKIX").build(params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractDisplayName(X509Certificate cert) {
        // Prefer SAN URIs for display since they're the verified binding; fall back to CN.
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> entry : sans) {
                    if (entry == null || entry.size() < 2) continue;
                    Object type = entry.get(0);
                    Object value = entry.get(1);
                    if (type instanceof Integer && (Integer) type == 6 && value instanceof String) {
                        return hostFor((String) value);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String dn = cert.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) return trimmed.substring(3);
        }
        return dn;
    }

    private static String hostFor(String url) {
        try {
            URI u = new URI(url);
            String host = u.getHost();
            return host != null ? host : url;
        } catch (URISyntaxException e) {
            return url;
        }
    }
}
