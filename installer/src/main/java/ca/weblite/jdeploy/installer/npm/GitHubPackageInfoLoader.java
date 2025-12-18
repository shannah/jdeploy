package ca.weblite.jdeploy.installer.npm;

import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads package-info.json from GitHub releases with cascading fallback strategy.
 * Tries multiple sources in order of priority:
 * 1. Version-specific release tag (if known from successful bundle download)
 * 2. jdeploy release tag (package-info.json)
 * 3. jdeploy release tag (package-info-2.json) for backward compatibility
 */
class GitHubPackageInfoLoader {
    private static final String GITHUB_URL = "https://github.com/";

    private final String owner;
    private final String repo;
    private final String successfulReleaseTag; // May be null if unknown

    /**
     * Create a new loader for a GitHub repository.
     *
     * @param owner GitHub repository owner
     * @param repo GitHub repository name
     * @param successfulReleaseTag The tag that successfully provided jdeploy-files.zip (may be null)
     */
    GitHubPackageInfoLoader(String owner, String repo, String successfulReleaseTag) {
        this.owner = owner;
        this.repo = repo;
        this.successfulReleaseTag = successfulReleaseTag;
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
        throw new IOException("Failed to load package-info.json from all sources for " +
                owner + "/" + repo + ". Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * Build a GitHub release download URL.
     *
     * @param tag Release tag
     * @param filename Filename to download
     * @return Complete URL
     */
    private String buildUrl(String tag, String filename) {
        return GITHUB_URL + owner + "/" + repo + "/releases/download/" + tag + "/" + filename;
    }

    /**
     * Download and parse JSON from a URL.
     *
     * @param url URL to download from
     * @return Parsed JSONObject
     * @throws IOException if download or parse fails
     */
    private JSONObject downloadAndParseJson(String url) throws IOException {
        try (InputStream inputStream = URLUtil.openStream(new URL(url))) {
            String content = IOUtil.readToString(inputStream);
            return new JSONObject(content);
        }
    }
}
