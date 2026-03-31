package ca.weblite.jdeploy.installer.jpm;

import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.tools.io.IOUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads package-info.json from JPM (jDeploy Package Manager) API with cascading fallback strategy.
 * Tries multiple sources in order of priority:
 * 1. Version-specific release tag (if known from successful bundle download)
 * 2. jdeploy release tag (package-info.json)
 * 3. jdeploy release tag (package-info-2.json) for backward compatibility
 */
public class JpmPackageInfoLoader {
    private static final String DEFAULT_JPM_BASE_URL = "https://packages.jdeploy.com";

    private final String owner;
    private final String repo;
    private final String successfulReleaseTag; // May be null if unknown
    private final String baseUrl;
    private final String authToken; // May be null for public apps

    /**
     * Create a new loader for a JPM repository.
     *
     * @param owner JPM repository owner
     * @param repo JPM repository name
     * @param successfulReleaseTag The tag that successfully provided the bundle (may be null)
     */
    public JpmPackageInfoLoader(String owner, String repo, String successfulReleaseTag) {
        this(owner, repo, successfulReleaseTag, null, null);
    }

    /**
     * Create a new loader for a JPM repository with optional auth and custom base URL.
     *
     * @param owner JPM repository owner
     * @param repo JPM repository name
     * @param successfulReleaseTag The tag that successfully provided the bundle (may be null)
     * @param baseUrl Custom base URL (null to use default)
     * @param authToken Auth token for private apps (null for public)
     */
    public JpmPackageInfoLoader(String owner, String repo, String successfulReleaseTag,
                                String baseUrl, String authToken) {
        this.owner = owner;
        this.repo = repo;
        this.successfulReleaseTag = successfulReleaseTag;
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : DEFAULT_JPM_BASE_URL;
        this.authToken = authToken;
    }

    /**
     * Load package-info.json with cascading fallback strategy.
     *
     * @return JSONObject containing package info
     * @throws IOException if all attempts fail
     */
    public JSONObject loadPackageInfo() throws IOException {
        List<String> urlsToTry = new ArrayList<>();

        // Priority 1: Version-specific release (if we know which tag worked)
        if (successfulReleaseTag != null && !successfulReleaseTag.isEmpty()) {
            urlsToTry.add(buildUrl(successfulReleaseTag, "package-info.json"));
            DebugLogger.log("Will try loading package-info.json from version-specific release: " + successfulReleaseTag);
        }

        // Priority 2: jdeploy tag
        urlsToTry.add(buildUrl("jdeploy", "package-info.json"));

        // Priority 3: jdeploy tag with alternative filename
        urlsToTry.add(buildUrl("jdeploy", "package-info-2.json"));

        IOException lastException = null;

        for (int i = 0; i < urlsToTry.size(); i++) {
            String url = urlsToTry.get(i);
            try {
                DebugLogger.logNetworkRequest("GET", url);
                JSONObject result = downloadAndParseJson(url);
                DebugLogger.logNetworkResponse(url, 200, "Success");
                DebugLogger.log("Successfully loaded package info from: " + url);
                return result;
            } catch (IOException e) {
                lastException = e;
                DebugLogger.logNetworkResponse(url, -1, "Failed: " + e.getMessage());

                // Only log fallback message if we have more URLs to try
                if (i < urlsToTry.size() - 1) {
                    DebugLogger.log("Failed to load from " + url + ", trying next fallback...");
                }
            }
        }

        // All attempts failed
        throw new IOException("Failed to load package-info.json from all JPM sources for " +
                owner + "/" + repo + ". Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * Build a JPM release download URL.
     *
     * @param tag Release tag
     * @param filename Filename to download
     * @return Complete URL
     */
    private String buildUrl(String tag, String filename) {
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBase + "/api/v1/" + owner + "/" + repo
                + "/releases/download/" + tag + "/" + filename;
    }

    /**
     * Download and parse JSON from a URL, with optional auth header.
     *
     * @param url URL to download from
     * @return Parsed JSONObject
     * @throws IOException if download or parse fails
     */
    private JSONObject downloadAndParseJson(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (authToken != null && !authToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + " " + conn.getResponseMessage()
                    + " for " + url);
        }

        try (InputStream inputStream = conn.getInputStream()) {
            String content = IOUtil.readToString(inputStream);
            return new JSONObject(content);
        }
    }
}
