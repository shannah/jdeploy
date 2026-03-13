package ca.weblite.jdeploy.installer.prebuilt.mocknetwork;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight client for the WireMock Admin API (__admin/*).
 *
 * Uses plain HttpURLConnection so it works on Java 8 without adding
 * a WireMock library dependency. Extended with binary file serving support
 * for pre-built bundle download tests.
 */
public class WireMockAdminClient {

    private final String baseUrl;
    private final List<String> dynamicStubIds = new ArrayList<>();

    public WireMockAdminClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    // ========================================================================
    // Stub management
    // ========================================================================

    public String addStub(JSONObject stubMapping) throws IOException {
        String id = UUID.randomUUID().toString();
        stubMapping.put("id", id);
        doPost(baseUrl + "/__admin/mappings", stubMapping.toString());
        dynamicStubIds.add(id);
        return id;
    }

    /**
     * Stub a GET endpoint that returns binary content (e.g. a JAR file).
     * The content is base64-encoded in the WireMock stub response.
     *
     * @param urlPath     exact URL path (e.g. "/releases/download/v1.0.0/bundle.jar")
     * @param content     raw binary content to serve
     * @param contentType MIME type (e.g. "application/java-archive")
     */
    public String stubGetBinaryContent(String urlPath, byte[] content, String contentType) throws IOException {
        JSONObject stub = new JSONObject();

        JSONObject request = new JSONObject();
        request.put("method", "GET");
        request.put("urlPath", urlPath);
        stub.put("request", request);
        stub.put("priority", 1);

        JSONObject response = new JSONObject();
        response.put("status", 200);
        response.put("base64Body", Base64.getEncoder().encodeToString(content));
        JSONObject headers = new JSONObject();
        headers.put("Content-Type", contentType);
        response.put("headers", headers);
        stub.put("response", response);

        return addStub(stub);
    }

    /**
     * Stub a GET endpoint that returns a 302 redirect to the given location.
     */
    public String stubGetRedirect(String urlPath, String redirectTo) throws IOException {
        JSONObject stub = new JSONObject();

        JSONObject request = new JSONObject();
        request.put("method", "GET");
        request.put("urlPath", urlPath);
        stub.put("request", request);
        stub.put("priority", 1);

        JSONObject response = new JSONObject();
        response.put("status", 302);
        JSONObject headers = new JSONObject();
        headers.put("Location", redirectTo);
        response.put("headers", headers);
        stub.put("response", response);

        return addStub(stub);
    }

    /**
     * Stub a GET endpoint that returns a specific HTTP error status.
     */
    public String stubGetError(String urlPath, int status) throws IOException {
        JSONObject stub = new JSONObject();

        JSONObject request = new JSONObject();
        request.put("method", "GET");
        request.put("urlPath", urlPath);
        stub.put("request", request);
        stub.put("priority", 1);

        JSONObject response = new JSONObject();
        response.put("status", status);
        response.put("body", "Error " + status);
        stub.put("response", response);

        return addStub(stub);
    }

    public void removeAllDynamicStubs() {
        for (String id : dynamicStubIds) {
            try {
                doDelete(baseUrl + "/__admin/mappings/" + id);
            } catch (IOException e) {
                System.err.println("Warning: failed to remove stub " + id + ": " + e.getMessage());
            }
        }
        dynamicStubIds.clear();
    }

    // ========================================================================
    // Request journal - verification
    // ========================================================================

    public void resetRequestJournal() throws IOException {
        doDelete(baseUrl + "/__admin/requests");
    }

    public int countRequestsMatching(String method, String urlPathPattern) throws IOException {
        JSONObject criteria = new JSONObject();
        criteria.put("method", method);
        criteria.put("urlPathPattern", urlPathPattern);

        String responseBody = doPost(baseUrl + "/__admin/requests/count", criteria.toString());
        JSONObject response = new JSONObject(responseBody);
        return response.getInt("count");
    }

    public void verifyRequestMade(String method, String urlPathPattern) throws IOException {
        int actual = countRequestsMatching(method, urlPathPattern);
        if (actual == 0) {
            throw new AssertionError(
                    "Expected at least one " + method + " request matching '"
                            + urlPathPattern + "', but found none"
            );
        }
    }

    public void verifyNoRequestMade(String method, String urlPathPattern) throws IOException {
        int actual = countRequestsMatching(method, urlPathPattern);
        if (actual != 0) {
            throw new AssertionError(
                    "Expected no " + method + " request(s) matching '"
                            + urlPathPattern + "', but found " + actual
            );
        }
    }

    // ========================================================================
    // Connectivity check
    // ========================================================================

    public boolean isAvailable() {
        try {
            doGet(baseUrl + "/__admin/mappings");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ========================================================================
    // HTTP helpers
    // ========================================================================

    private String doGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        return readResponse(conn);
    }

    private String doPost(String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private void doDelete(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("DELETE");
        conn.getResponseCode();
        conn.disconnect();
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();
        if (is == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
