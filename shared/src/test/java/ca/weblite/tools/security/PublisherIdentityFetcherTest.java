package ca.weblite.tools.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the network plumbing of {@link PublisherIdentityFetcher.Default}.
 *
 * <p>Production code rejects non-HTTPS URLs. To exercise the fetcher's redirect
 * handling, size cap, error response, and timeout logic without setting up
 * TLS, these tests use the test-only constructor that accepts {@code http} as
 * an allowed protocol. The HTTPS-only enforcement of the production
 * configuration is covered by {@link #httpsOnlyByDefault()}.
 */
public class PublisherIdentityFetcherTest {

    private HttpServer primary;
    private HttpServer secondary;

    @BeforeEach
    public void start() throws IOException {
        primary = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        secondary = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        primary.start();
        secondary.start();
    }

    @AfterEach
    public void stop() {
        primary.stop(0);
        secondary.stop(0);
    }

    @Test
    public void httpsOnlyByDefault() {
        PublisherIdentityFetcher.Default fetcher = new PublisherIdentityFetcher.Default();
        IOException ex = assertThrows(IOException.class, () -> fetcher.fetch("http://127.0.0.1:1/anything"));
        assertTrue(ex.getMessage().contains("disallowed protocol"));
    }

    @Test
    public void successfulFetch() throws Exception {
        byte[] payload = "publisher-cert-bytes".getBytes(StandardCharsets.US_ASCII);
        primary.createContext("/.well-known/jdeploy-publisher.cer", staticBytes(200, payload));

        byte[] got = httpFetcher().fetch(urlOf(primary, "/.well-known/jdeploy-publisher.cer"));
        assertArrayEquals(payload, got);
    }

    @Test
    public void nonSuccessResponseRaisesIoException() {
        primary.createContext("/missing", staticBytes(404, new byte[0]));
        IOException ex = assertThrows(IOException.class,
                () -> httpFetcher().fetch(urlOf(primary, "/missing")));
        assertTrue(ex.getMessage().contains("HTTP 404"));
    }

    @Test
    public void responseLargerThanCapRaisesIoException() {
        byte[] big = new byte[16 * 1024];
        primary.createContext("/big", staticBytes(200, big));
        // cap = 1KB
        PublisherIdentityFetcher.Default fetcher = new PublisherIdentityFetcher.Default(
                10_000, 1024, allowHttp());
        IOException ex = assertThrows(IOException.class,
                () -> fetcher.fetch(urlOf(primary, "/big")));
        assertTrue(ex.getMessage().contains("exceeds maximum size"));
    }

    @Test
    public void sameOriginRedirectFollowed() throws Exception {
        byte[] payload = "ok".getBytes(StandardCharsets.US_ASCII);
        primary.createContext("/r", redirectTo("/final"));
        primary.createContext("/final", staticBytes(200, payload));

        byte[] got = httpFetcher().fetch(urlOf(primary, "/r"));
        assertArrayEquals(payload, got);
    }

    @Test
    public void crossOriginRedirectRefused() {
        primary.createContext("/r", redirectTo(urlOf(secondary, "/final")));
        secondary.createContext("/final", staticBytes(200, new byte[]{1, 2, 3}));

        IOException ex = assertThrows(IOException.class,
                () -> httpFetcher().fetch(urlOf(primary, "/r")));
        assertTrue(ex.getMessage().contains("cross-origin redirect"));
    }

    @Test
    public void redirectLoopGivesUp() {
        // 5 hops, MAX_REDIRECTS = 3 -> must fail.
        AtomicInteger n = new AtomicInteger();
        primary.createContext("/loop", exchange -> {
            int i = n.incrementAndGet();
            exchange.getResponseHeaders().set("Location", "/loop?n=" + i);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        IOException ex = assertThrows(IOException.class,
                () -> httpFetcher().fetch(urlOf(primary, "/loop")));
        assertTrue(ex.getMessage().toLowerCase().contains("redirect"));
    }

    // --- helpers ---

    private static PublisherIdentityFetcher.Default httpFetcher() {
        return new PublisherIdentityFetcher.Default(10_000, 256 * 1024, allowHttp());
    }

    private static Set<String> allowHttp() {
        Set<String> s = new HashSet<>();
        s.add("http");
        s.add("https");
        return Collections.unmodifiableSet(s);
    }

    private static String urlOf(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static HttpHandler staticBytes(int status, byte[] body) {
        return exchange -> {
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
            exchange.close();
        };
    }

    private static HttpHandler redirectTo(String location) {
        return exchange -> {
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        };
    }
}
