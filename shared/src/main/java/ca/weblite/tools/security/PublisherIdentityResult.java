package ca.weblite.tools.security;

import java.security.cert.X509Certificate;

/**
 * Outcome of a {@link PublisherIdentityVerifier} run.
 *
 * <p>VERIFIED results carry the URL that was successfully verified, the human-readable
 * publisher display name (taken from the identity cert's subject CN), and the leaf
 * identity certificate itself. NOT_VERIFIED results carry a {@link FailureReason}
 * and a free-form detail string suitable for logging.
 */
public final class PublisherIdentityResult {

    public enum Status { VERIFIED, NOT_VERIFIED }

    public enum FailureReason {
        NO_URLS_CONFIGURED,
        FETCH_FAILED,
        INVALID_PEM,
        CHAIN_INVALID,
        MISSING_EKU,
        URL_NOT_IN_SAN,
        CERT_EXPIRED,
        CODESIGN_ROOT_MISMATCH
    }

    private final Status status;
    private final String url;
    private final String displayName;
    private final X509Certificate identityCertificate;
    private final FailureReason failureReason;
    private final String detail;

    private PublisherIdentityResult(
            Status status,
            String url,
            String displayName,
            X509Certificate identityCertificate,
            FailureReason failureReason,
            String detail
    ) {
        this.status = status;
        this.url = url;
        this.displayName = displayName;
        this.identityCertificate = identityCertificate;
        this.failureReason = failureReason;
        this.detail = detail;
    }

    public static PublisherIdentityResult verified(
            String url,
            String displayName,
            X509Certificate identityCertificate
    ) {
        return new PublisherIdentityResult(
                Status.VERIFIED, url, displayName, identityCertificate, null, null
        );
    }

    public static PublisherIdentityResult notVerified(FailureReason reason, String detail) {
        return new PublisherIdentityResult(
                Status.NOT_VERIFIED, null, null, null, reason, detail
        );
    }

    public Status getStatus() { return status; }
    public boolean isVerified() { return status == Status.VERIFIED; }
    public String getUrl() { return url; }
    public String getDisplayName() { return displayName; }
    public X509Certificate getIdentityCertificate() { return identityCertificate; }
    public FailureReason getFailureReason() { return failureReason; }
    public String getDetail() { return detail; }

    @Override
    public String toString() {
        if (isVerified()) {
            return "PublisherIdentityResult{VERIFIED, url=" + url
                    + ", displayName=" + displayName + "}";
        }
        return "PublisherIdentityResult{NOT_VERIFIED, reason=" + failureReason
                + ", detail=" + detail + "}";
    }
}
