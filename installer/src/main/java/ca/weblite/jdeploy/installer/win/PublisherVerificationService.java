package ca.weblite.jdeploy.installer.win;

import ca.weblite.tools.security.CertificateUtil;
import ca.weblite.tools.security.PublisherIdentityFetcher;
import ca.weblite.tools.security.PublisherIdentityResult;
import ca.weblite.tools.security.PublisherIdentityVerifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the install-time publisher verification flow described in
 * {@code rfc/website-publisher-verification-plan.md}.
 *
 * <p>For each candidate URL (taken from package.json's
 * {@code jdeploy.publisherVerificationUrls}, falling back to the
 * {@code homepage} field with a {@code /.well-known/jdeploy-publisher.cer}
 * suffix), this service fetches the publisher identity certificate and
 * delegates the cryptographic checks to
 * {@link PublisherIdentityVerifier}.
 *
 * <p><b>Trust anchors.</b> Two sources are accepted:
 * <ul>
 *   <li>The leaf code-signing cert exported from the .exe via
 *       {@link AuthenticodeSignatureChecker#exportCertificate}, supporting
 *       option-(b) chains where the codesign cert is the issuer of the
 *       identity cert.</li>
 *   <li>Any trusted roots declared in the bundle's {@code app.xml}
 *       {@code trusted-certificates} attribute, supporting option-(a)
 *       chains where the identity cert is signed directly by the pinned
 *       root.</li>
 * </ul>
 * Either chain shape will verify; both are added to the trust anchor set
 * passed to {@link PublisherIdentityVerifier}.
 */
public class PublisherVerificationService {

    private static final String WELL_KNOWN_PATH = "/.well-known/jdeploy-publisher.cer";

    private final PublisherIdentityVerifier verifier;

    public PublisherVerificationService() {
        this(new PublisherIdentityVerifier(new PublisherIdentityFetcher.Default()));
    }

    public PublisherVerificationService(PublisherIdentityVerifier verifier) {
        this.verifier = verifier;
    }

    /**
     * Run verification.
     *
     * @param configuredUrls explicit URLs from {@code jdeploy.publisherVerificationUrls};
     *                       may be null/empty.
     * @param homepageUrl    {@code homepage} field from package.json; used to derive a
     *                       {@code /.well-known/jdeploy-publisher.cer} fallback URL when
     *                       no explicit URL list is configured. May be null.
     * @param codesignCert   the leaf code-signing cert extracted from the running .exe.
     * @param pinnedRoots    optional roots from the bundle's {@code app.xml} that should
     *                       also be accepted as trust anchors (option-(a) chains).
     *                       May be null/empty.
     * @return a non-null result. Returns
     *         {@link PublisherIdentityResult.FailureReason#NO_URLS_CONFIGURED} when
     *         neither configured URLs nor a usable homepage fallback exist.
     */
    public PublisherIdentityResult verify(
            List<String> configuredUrls,
            String homepageUrl,
            X509Certificate codesignCert,
            List<X509Certificate> pinnedRoots
    ) {
        if (codesignCert == null) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.CODESIGN_ROOT_MISMATCH,
                    "No code-signing certificate available for verification"
            );
        }

        List<String> candidateUrls = buildCandidateUrls(configuredUrls, homepageUrl);
        if (candidateUrls.isEmpty()) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.NO_URLS_CONFIGURED,
                    "No publisherVerificationUrls and no usable homepage in package.json"
            );
        }

        // Build a deduplicated trust-anchor set: codesign cert (option-b) plus any
        // pinned roots from app.xml (option-a). PKIX builds whichever path applies.
        List<X509Certificate> anchors = new ArrayList<>();
        anchors.add(codesignCert);
        if (pinnedRoots != null) {
            for (X509Certificate r : pinnedRoots) {
                if (r != null && !anchors.contains(r)) anchors.add(r);
            }
        }

        return verifier.verify(
                candidateUrls,
                anchors,
                Collections.singletonList(codesignCert)
        );
    }

    /** Backwards-compatible overload that omits the {@code pinnedRoots} parameter. */
    public PublisherIdentityResult verify(
            List<String> configuredUrls,
            String homepageUrl,
            X509Certificate codesignCert
    ) {
        return verify(configuredUrls, homepageUrl, codesignCert, Collections.emptyList());
    }

    /**
     * Convenience overload that loads the codesign cert from a {@code .cer} file
     * (typically the temp file produced by
     * {@link AuthenticodeSignatureChecker#exportCertificate(File)}) and the pinned
     * roots from the bundle's {@code app.xml}.
     *
     * @param codesignCertFile the {@code .cer} file holding the leaf codesign cert.
     * @param appXmlFile       the bundle's {@code app.xml}; may be null.
     */
    public PublisherIdentityResult verify(
            List<String> configuredUrls,
            String homepageUrl,
            File codesignCertFile,
            File appXmlFile
    ) throws Exception {
        if (codesignCertFile == null || !codesignCertFile.exists()) {
            return PublisherIdentityResult.notVerified(
                    PublisherIdentityResult.FailureReason.CODESIGN_ROOT_MISMATCH,
                    "Code-signing certificate file not found"
            );
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(codesignCertFile.toPath());
        X509Certificate cert;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        List<X509Certificate> roots = (appXmlFile != null && appXmlFile.exists())
                ? CertificateUtil.loadTrustedCertificatesListFromAppXml(appXmlFile.getAbsolutePath())
                : Collections.<X509Certificate>emptyList();
        return verify(configuredUrls, homepageUrl, cert, roots);
    }

    /** Backwards-compatible overload that omits the {@code appXmlFile} parameter. */
    public PublisherIdentityResult verify(
            List<String> configuredUrls,
            String homepageUrl,
            File codesignCertFile
    ) throws Exception {
        return verify(configuredUrls, homepageUrl, codesignCertFile, null);
    }

    /**
     * Builds the ordered, de-duplicated list of URLs to try. Configured URLs come
     * first; the homepage-derived fallback is appended only when no configured
     * URLs are present (the configured list is the publisher's authoritative
     * declaration; a homepage URL with the well-known path is a best-effort guess).
     */
    static List<String> buildCandidateUrls(List<String> configuredUrls, String homepageUrl) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        if (configuredUrls != null) {
            for (String u : configuredUrls) {
                if (u == null) continue;
                String trimmed = u.trim();
                if (trimmed.isEmpty()) continue;
                if (seen.add(trimmed)) out.add(trimmed);
            }
        }
        if (out.isEmpty()) {
            String fallback = homepageFallback(homepageUrl);
            if (fallback != null && seen.add(fallback)) out.add(fallback);
        }
        return out;
    }

    private static String homepageFallback(String homepageUrl) {
        if (homepageUrl == null) return null;
        String h = homepageUrl.trim();
        if (h.isEmpty()) return null;
        if (!h.startsWith("https://")) return null; // only HTTPS homepages count
        // Strip query/fragment/path; keep scheme + authority.
        int qStart = h.indexOf('?');
        if (qStart >= 0) h = h.substring(0, qStart);
        int hashStart = h.indexOf('#');
        if (hashStart >= 0) h = h.substring(0, hashStart);
        // Find scheme://host[:port][/...]
        int schemeEnd = h.indexOf("://");
        int pathStart = h.indexOf('/', schemeEnd + 3);
        String origin = pathStart > 0 ? h.substring(0, pathStart) : h;
        return origin + WELL_KNOWN_PATH;
    }
}
