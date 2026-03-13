package ca.weblite.jdeploy.publishing;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight client for the WireMock Admin API (__admin/*).
 *
 * Uses plain HttpURLConnection so it works on Java 8 without adding
 * a WireMock library dependency. Provides per-test stub management
 * and request verification for mock network publishing tests.
 */
public class WireMockAdminClient {

    private final String baseUrl;
    private final List<String> dynamicStubIds = new ArrayList<>();

    /**
     * @param baseUrl WireMock base URL, e.g. "http://localhost:8080"
     */
    public WireMockAdminClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    // ========================================================================
    // Stub management
    // ========================================================================

    /**
     * Register a stub mapping and track its ID for cleanup.
     * Returns the stub UUID assigned by WireMock.
     */
    public String addStub(JSONObject stubMapping) throws IOException {
        String id = UUID.randomUUID().toString();
        stubMapping.put("id", id);

        String responseBody = doPost(baseUrl + "/__admin/mappings", stubMapping.toString());
        dynamicStubIds.add(id);
        return id;
    }

    /**
     * Convenience: create a stub for GET url matching a regex pattern.
     */
    public String stubGetUrlMatching(String urlPattern, int status, JSONObject responseBody) throws IOException {
        return stubGetUrlMatching(urlPattern, status, responseBody, 1);
    }

    public String stubGetUrlMatching(String urlPattern, int status, JSONObject responseBody, int priority) throws IOException {
        JSONObject stub = new JSONObject();

        JSONObject request = new JSONObject();
        request.put("method", "GET");
        request.put("urlPathPattern", urlPattern);
        stub.put("request", request);
        stub.put("priority", priority);

        JSONObject response = new JSONObject();
        response.put("status", status);
        if (responseBody != null) {
            response.put("body", responseBody.toString());
            response.put("headers", new JSONObject().put("Content-Type", "application/json"));
        }
        stub.put("response", response);

        return addStub(stub);
    }

    /**
     * Convenience: create a stub for POST url matching a regex pattern.
     */
    public String stubPostUrlMatching(String urlPattern, int status, JSONObject responseBody) throws IOException {
        return stubPostUrlMatching(urlPattern, status, responseBody, 1);
    }

    public String stubPostUrlMatching(String urlPattern, int status, JSONObject responseBody, int priority) throws IOException {
        JSONObject stub = new JSONObject();

        JSONObject request = new JSONObject();
        request.put("method", "POST");
        request.put("urlPathPattern", urlPattern);
        stub.put("request", request);
        stub.put("priority", priority);

        JSONObject response = new JSONObject();
        response.put("status", status);
        if (responseBody != null) {
            response.put("body", responseBody.toString());
            response.put("headers", new JSONObject().put("Content-Type", "application/json"));
        }
        stub.put("response", response);

        return addStub(stub);
    }

    /**
     * Remove all stubs that were added via this client instance.
     */
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

    /**
     * Reset the request journal (clear all recorded requests).
     */
    public void resetRequestJournal() throws IOException {
        doDelete(baseUrl + "/__admin/requests");
    }

    /**
     * Count requests matching a method and URL path pattern.
     */
    public int countRequestsMatching(String method, String urlPathPattern) throws IOException {
        JSONObject criteria = new JSONObject();
        criteria.put("method", method);
        criteria.put("urlPathPattern", urlPathPattern);

        String responseBody = doPost(baseUrl + "/__admin/requests/count", criteria.toString());
        JSONObject response = new JSONObject(responseBody);
        return response.getInt("count");
    }

    /**
     * Find requests matching a method and URL path pattern.
     * Returns the list of request objects from WireMock's journal.
     */
    public List<JSONObject> findRequestsMatching(String method, String urlPathPattern) throws IOException {
        JSONObject criteria = new JSONObject();
        criteria.put("method", method);
        criteria.put("urlPathPattern", urlPathPattern);

        String responseBody = doPost(baseUrl + "/__admin/requests/find", criteria.toString());
        JSONObject response = new JSONObject(responseBody);
        JSONArray requests = response.optJSONArray("requests");

        List<JSONObject> result = new ArrayList<>();
        if (requests != null) {
            for (int i = 0; i < requests.length(); i++) {
                result.add(requests.getJSONObject(i));
            }
        }
        return result;
    }

    /**
     * Assert that exactly {@code expectedCount} requests were made matching the criteria.
     * Throws AssertionError with a descriptive message on mismatch.
     */
    public void verifyRequestCount(String method, String urlPathPattern, int expectedCount) throws IOException {
        int actual = countRequestsMatching(method, urlPathPattern);
        if (actual != expectedCount) {
            throw new AssertionError(
                    "Expected " + expectedCount + " " + method + " request(s) matching '"
                            + urlPathPattern + "', but found " + actual
            );
        }
    }

    /**
     * Assert that at least one request was made matching the criteria.
     */
    public void verifyRequestMade(String method, String urlPathPattern) throws IOException {
        int actual = countRequestsMatching(method, urlPathPattern);
        if (actual == 0) {
            throw new AssertionError(
                    "Expected at least one " + method + " request matching '"
                            + urlPathPattern + "', but found none"
            );
        }
    }

    /**
     * Assert that no requests were made matching the criteria.
     */
    public void verifyNoRequestMade(String method, String urlPathPattern) throws IOException {
        int actual = countRequestsMatching(method, urlPathPattern);
        if (actual != 0) {
            throw new AssertionError(
                    "Expected no " + method + " request(s) matching '"
                            + urlPathPattern + "', but found " + actual
            );
        }
    }

    /**
     * Get the total number of requests recorded in the journal.
     */
    public int getTotalRequestCount() throws IOException {
        String responseBody = doGet(baseUrl + "/__admin/requests");
        JSONObject response = new JSONObject(responseBody);
        JSONArray requests = response.optJSONArray("requests");
        return requests != null ? requests.length() : 0;
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
        conn.getResponseCode(); // trigger the request
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
