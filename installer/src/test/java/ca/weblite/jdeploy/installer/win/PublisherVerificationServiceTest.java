package ca.weblite.jdeploy.installer.win;

import ca.weblite.tools.security.PublisherIdentityResult;
import ca.weblite.tools.security.PublisherIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link PublisherVerificationService} discovery logic and the
 * thin glue around {@link PublisherIdentityVerifier}. Pure cryptographic
 * verification is covered by {@code PublisherIdentityVerifierTest} in the
 * shared module; this suite exercises only the orchestration concerns
 * specific to the installer.
 */
public class PublisherVerificationServiceTest {

    @Test
    public void configuredUrlsTakePrecedenceOverHomepage() {
        List<String> urls = PublisherVerificationService.buildCandidateUrls(
                Arrays.asList(
                        "https://primary.example.com/.well-known/jdeploy-publisher.cer",
                        "https://secondary.example.com/.well-known/jdeploy-publisher.cer"
                ),
                "https://acme.example.com");
        assertEquals(2, urls.size());
        assertEquals("https://primary.example.com/.well-known/jdeploy-publisher.cer", urls.get(0));
        assertEquals("https://secondary.example.com/.well-known/jdeploy-publisher.cer", urls.get(1));
    }

    @Test
    public void homepageFallbackUsedWhenNoConfiguredUrls() {
        List<String> urls = PublisherVerificationService.buildCandidateUrls(
                Collections.emptyList(),
                "https://acme.example.com/somepage");
        assertEquals(Collections.singletonList(
                "https://acme.example.com/.well-known/jdeploy-publisher.cer"), urls);
    }

    @Test
    public void homepageQueryAndFragmentAreStripped() {
        List<String> urls = PublisherVerificationService.buildCandidateUrls(
                Collections.emptyList(),
                "https://acme.example.com/path/to/page?foo=bar#frag");
        assertEquals(Collections.singletonList(
                "https://acme.example.com/.well-known/jdeploy-publisher.cer"), urls);
    }

    @Test
    public void httpHomepageRejected() {
        List<String> urls = PublisherVerificationService.buildCandidateUrls(
                Collections.emptyList(),
                "http://acme.example.com");
        assertTrue(urls.isEmpty(), "plaintext homepage must not produce a fallback URL");
    }

    @Test
    public void duplicateConfiguredUrlsDeduped() {
        List<String> urls = PublisherVerificationService.buildCandidateUrls(
                Arrays.asList(
                        "https://acme.example.com/.well-known/jdeploy-publisher.cer",
                        "https://acme.example.com/.well-known/jdeploy-publisher.cer",
                        "  ", null
                ),
                "https://acme.example.com");
        assertEquals(1, urls.size());
    }

    @Test
    public void noUrlsAndNoHomepageReturnsNoUrlsConfigured() {
        PublisherVerificationService svc = new PublisherVerificationService();
        // Mockito stub: the cert is just a non-null reference. The discovery check
        // fires before the verifier touches the cert's contents.
        X509Certificate stub = Mockito.mock(X509Certificate.class);
        PublisherIdentityResult r = svc.verify(Collections.emptyList(), null, stub);
        assertEquals(PublisherIdentityResult.Status.NOT_VERIFIED, r.getStatus());
        assertEquals(PublisherIdentityResult.FailureReason.NO_URLS_CONFIGURED, r.getFailureReason());
    }

    @Test
    public void nullCodesignCertReportsCodesignMismatch() {
        PublisherVerificationService svc = new PublisherVerificationService();
        PublisherIdentityResult r = svc.verify(
                Collections.singletonList("https://x/.well-known/jdeploy-publisher.cer"),
                "https://x", (X509Certificate) null);
        assertEquals(PublisherIdentityResult.FailureReason.CODESIGN_ROOT_MISMATCH, r.getFailureReason());
    }
}
