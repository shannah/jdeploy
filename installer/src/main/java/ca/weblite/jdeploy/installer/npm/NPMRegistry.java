package ca.weblite.jdeploy.installer.npm;

import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.beans.Encoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class NPMRegistry {
    private static final String REGISTRY_URL="https://registry.npmjs.org/";
    private static final String GITHUB_URL = "https://github.com/";

    /**
     * Load package info with optional successful release tag for fallback strategy.
     *
     * @param packageName NPM package name
     * @param source Source URL (GitHub or NPM registry)
     * @param successfulReleaseTag The GitHub release tag that successfully provided the bundle (may be null)
     * @return NPMPackage containing version information
     * @throws IOException if loading fails
     */
    public NPMPackage loadPackage(String packageName, String source, String successfulReleaseTag) throws IOException {
        if (source.startsWith(GITHUB_URL)) {
            // Extract owner and repo from GitHub URL
            String repoPath = source.substring(GITHUB_URL.length());
            // Remove trailing slash and .git
            repoPath = repoPath.replaceAll("/$", "").replaceAll("\\.git$", "");
            String[] parts = repoPath.split("/", 2);

            if (parts.length < 2) {
                throw new IOException("Invalid GitHub repository URL: " + source);
            }

            String owner = parts[0];
            String repo = parts[1];

            // Use GitHubPackageInfoLoader with cascading fallback strategy
            GitHubPackageInfoLoader loader = new GitHubPackageInfoLoader(owner, repo, successfulReleaseTag);
            JSONObject packageInfo = loader.loadPackageInfo();
            return new NPMPackage(packageInfo);
        } else {
            // NPM registry
            String packageUrl = REGISTRY_URL + URLEncoder.encode(packageName, "UTF-8");
            DebugLogger.logNetworkRequest("GET", packageUrl);

            try (InputStream inputStream = URLUtil.openStream(new URL(packageUrl))) {
                DebugLogger.logNetworkResponse(packageUrl, 200, "Success");
                String str = IOUtil.readToString(inputStream);
                return new NPMPackage(new JSONObject(str));
            } catch (IOException e) {
                DebugLogger.logNetworkResponse(packageUrl, -1, "Failed: " + e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Load package info without successful release tag (uses default fallback strategy).
     *
     * @param packageName NPM package name
     * @param source Source URL (GitHub or NPM registry)
     * @return NPMPackage containing version information
     * @throws IOException if loading fails
     */
    public NPMPackage loadPackage(String packageName, String source) throws IOException {
        return loadPackage(packageName, source, null);
    }

}
