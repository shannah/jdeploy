package ca.weblite.tools.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
        private static final Set<String> DEFAULT_PROTOCOLS;
        static {
            Set<String> s = new HashSet<>();
            s.add("https");
            DEFAULT_PROTOCOLS = Collections.unmodifiableSet(s);
        }

        private final int timeoutMs;
        private final int maxBytes;
        private final Set<String> allowedProtocols;

        public Default() {
            this(DEFAULT_TIMEOUT_MS, MAX_BYTES);
        }

        public Default(int timeoutMs, int maxBytes) {
            this(timeoutMs, maxBytes, DEFAULT_PROTOCOLS);
        }

        /**
         * Test-only constructor that accepts an alternate set of allowed protocols.
         * Production code should use {@link #Default()} or {@link #Default(int, int)};
         * those reject anything other than {@code https}.
         */
        public Default(int timeoutMs, int maxBytes, Set<String> allowedProtocols) {
            this.timeoutMs = timeoutMs;
            this.maxBytes = maxBytes;
            Set<String> normalised = new HashSet<>();
            for (String p : allowedProtocols) normalised.add(p.toLowerCase(Locale.ROOT));
            this.allowedProtocols = Collections.unmodifiableSet(normalised);
        }

        @Override
        public byte[] fetch(String url) throws IOException {
            String currentUrl = url;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                URL parsed = new URL(currentUrl);
                if (!allowedProtocols.contains(parsed.getProtocol().toLowerCase(Locale.ROOT))) {
                    throw new IOException("Refusing to fetch URL with disallowed protocol: " + currentUrl);
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
                    if (!allowedProtocols.contains(next.getProtocol().toLowerCase(Locale.ROOT))) {
                        throw new IOException("Refusing to follow redirect to URL with disallowed protocol: " + next);
                    }
                    if (!sameOrigin(parsed, next)) {
                        throw new IOException("Refusing to follow cross-origin redirect: "
                                + originString(parsed) + " -> " + originString(next));
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

        private static boolean sameOrigin(URL a, URL b) {
            if (!a.getProtocol().equalsIgnoreCase(b.getProtocol())) return false;
            if (!a.getHost().equalsIgnoreCase(b.getHost())) return false;
            return effectivePort(a) == effectivePort(b);
        }

        private static int effectivePort(URL u) {
            int p = u.getPort();
            if (p != -1) return p;
            if ("https".equalsIgnoreCase(u.getProtocol())) return 443;
            if ("http".equalsIgnoreCase(u.getProtocol())) return 80;
            return -1;
        }

        private static String originString(URL u) {
            return u.getProtocol() + "://" + u.getHost() + ":" + effectivePort(u);
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
