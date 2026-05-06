package ca.weblite.tools.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetches the raw bytes of a publisher identity certificate hosted at a well-known URL.
 *
 * <p>Extracted as an interface so {@link PublisherIdentityVerifier} can be unit-tested
 * without making network calls.
 */
public interface PublisherIdentityFetcher {

    /**
     * Fetches the bytes at the given URL.
     *
     * @param url the URL to fetch
     * @return the response body bytes (PEM-encoded certificate(s))
     * @throws IOException if the fetch fails for any reason (timeout, non-2xx, etc.)
     */
    byte[] fetch(String url) throws IOException;

    /**
     * Default HTTPS-only fetcher. Rejects non-https URLs, follows up to a small number
     * of same-origin redirects, applies a 10s connect/read timeout, and caps the response
     * size to prevent memory exhaustion on a hostile server.
     */
    final class Default implements PublisherIdentityFetcher {

        private static final int DEFAULT_TIMEOUT_MS = 10_000;
        private static final int MAX_BYTES = 256 * 1024;
        private static final int MAX_REDIRECTS = 3;

        private final int timeoutMs;
        private final int maxBytes;

        public Default() {
            this(DEFAULT_TIMEOUT_MS, MAX_BYTES);
        }

        public Default(int timeoutMs, int maxBytes) {
            this.timeoutMs = timeoutMs;
            this.maxBytes = maxBytes;
        }

        @Override
        public byte[] fetch(String url) throws IOException {
            String currentUrl = url;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                URL parsed = new URL(currentUrl);
                if (!"https".equalsIgnoreCase(parsed.getProtocol())) {
                    throw new IOException("Refusing to fetch non-HTTPS URL: " + currentUrl);
                }
                HttpURLConnection conn = (HttpURLConnection) parsed.openConnection();
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Accept", "application/x-pem-file, application/x-x509-ca-cert, */*");

                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (location == null) {
                        throw new IOException("Redirect without Location header from " + currentUrl);
                    }
                    URL next = new URL(parsed, location);
                    if (!"https".equalsIgnoreCase(next.getProtocol())) {
                        throw new IOException("Refusing to follow redirect to non-HTTPS URL: " + next);
                    }
                    if (!next.getHost().equalsIgnoreCase(parsed.getHost())) {
                        throw new IOException("Refusing to follow cross-origin redirect: "
                                + parsed.getHost() + " -> " + next.getHost());
                    }
                    currentUrl = next.toString();
                    continue;
                }
                if (code < 200 || code >= 300) {
                    conn.disconnect();
                    throw new IOException("HTTP " + code + " from " + currentUrl);
                }
                try (InputStream in = conn.getInputStream()) {
                    return readCapped(in, maxBytes);
                } finally {
                    conn.disconnect();
                }
            }
            throw new IOException("Too many redirects starting at " + url);
        }

        private static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("Response exceeds maximum size of " + maxBytes + " bytes");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
