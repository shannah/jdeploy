package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.services.WebsiteVerifier;
import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import ca.weblite.tools.io.XMLUtil;
import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultInstallationContext implements InstallationContext {
    public static final String JDEPLOY_REGISTRY = System.getProperty(
            "jdeploy.registry.url",
            "https://www.jdeploy.com/"
    );
    private static final String GITHUB_URL = "https://github.com/";

    /**
     * ThreadLocal to store the successful GitHub release tag from bundle download.
     * This allows us to reuse the same tag when loading package-info.json.
     */
    private static final ThreadLocal<String> successfulGitHubTag = new ThreadLocal<>();

    /**
     * Get the successful GitHub tag from the most recent bundle download.
     * @return The tag name, or null if not available
     */
    public static String getSuccessfulGitHubTag() {
        return successfulGitHubTag.get();
    }

    /**
     * Clear the successful GitHub tag.
     */
    public static void clearSuccessfulGitHubTag() {
        successfulGitHubTag.remove();
    }

    /**
     * Result of downloading from a GitHub release, including the successful tag.
     */
    static class GitHubDownloadResult {
        private final File jdeployFilesDir;
        private final String successfulTag;

        GitHubDownloadResult(File jdeployFilesDir, String successfulTag) {
            this.jdeployFilesDir = jdeployFilesDir;
            this.successfulTag = successfulTag;
        }

        public File getJdeployFilesDir() {
            return jdeployFilesDir;
        }

        public String getSuccessfulTag() {
            return successfulTag;
        }
    }

    /**
     * Parse the fallback registries from the jdeploy.registries.fallback system property.
     * Returns a list of registry URLs in order of preference.
     */
    private static List<String> parseFallbackRegistries() {
        List<String> registries = new ArrayList<>();
        String fallbackProp = System.getProperty("jdeploy.registries.fallback");
        if (fallbackProp != null && !fallbackProp.trim().isEmpty()) {
            String[] urls = fallbackProp.split(",");
            for (String url : urls) {
                url = url.trim();
                if (!url.isEmpty()) {
                    // Ensure URL ends with /
                    if (!url.endsWith("/")) {
                        url = url + "/";
                    }
                    registries.add(url);
                }
            }
        }
        return registries;
    }

    /**
     * Check if an IOException represents a non-retryable error.
     * Non-retryable errors include 404 (not found) and 403 (forbidden/rate limited).
     */
    private static boolean isNonRetryableError(IOException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        // Check for HTTP error codes that shouldn't be retried
        return message.contains("HTTP response code: 404") ||
               message.contains("HTTP response code: 403") ||
               message.contains("Server returned HTTP response code: 404") ||
               message.contains("Server returned HTTP response code: 403");
    }

    private Document appXMLDocument;
    private File cachedInstallFilesDir;
    public File findInstallFilesDir() {
        if (cachedInstallFilesDir != null && cachedInstallFilesDir.exists()) return cachedInstallFilesDir;
        if (System.getProperty("client4j.launcher.path") != null) {
            String launcherPath = System.getProperty("client4j.launcher.path");
            String launcherFileName = launcherPath;
            boolean isMac = Platform.getSystemPlatform().isMac();
            File appBundle = isMac ? findAppBundle() : null;
            File tmpBundleFile = new File(launcherPath);
            if (isMac && appBundle != null && appBundle.exists()) {
                launcherFileName = appBundle.getName();
                tmpBundleFile = appBundle;

            }

            String code = extractJDeployBundleCodeFromFileName(launcherFileName);
            String version = extractVersionFromFileName(launcherFileName);
            if (code != null && version != null) {
                try {
                    System.setProperty("jdeploy.bundle-code", code);
                    System.setProperty("jdeploy.bundle-version", version);
                    cachedInstallFilesDir = downloadJDeployBundleForCode(code, version, tmpBundleFile);
                    return cachedInstallFilesDir;
                } catch (IOException ex) {
                    System.err.println("Failed to download install files bundle: "+ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            }

            System.out.println("Found client4.launcher.path property: "+launcherPath);
            if (System.getProperty("client4j.appxml.path") != null) {
                cachedInstallFilesDir = findInstallFilesDir(new File(System.getProperty("client4j.appxml.path")).getParentFile());
            } else {
                cachedInstallFilesDir = findInstallFilesDir(new File(launcherPath));
            }

            return cachedInstallFilesDir;
        } else {
            System.out.println("client4j.launcher.path is not set");
        }
        System.out.println("User dir: "+new File(System.getProperty("user.dir")).getAbsolutePath());
        cachedInstallFilesDir = findInstallFilesDir(new File(System.getProperty("user.dir")));
        return cachedInstallFilesDir;
    }

    private static String extractJDeployBundleCodeFromFileName(String fileName) {
        int pos = fileName.lastIndexOf("_");
        if (pos < 0) return null;
        StringBuilder out = new StringBuilder();
        char[] chars = fileName.substring(pos+1).toCharArray();
        for (int i=0; i<chars.length; i++) {
            char c = chars[i];
            if (('0' <= c && '9' >= c) || ('A' <= c && 'Z' >= c)) {
                out.append(c);
            } else {
                break;
            }
        }
        if (out.length() == 0) return null;
        return out.toString();

    }

    private static URL getJDeployBundleURLForCode(String code, String version, File appBundle) {
        return getJDeployBundleURLForCode(code, version, appBundle, JDEPLOY_REGISTRY);
    }

    private static URL getJDeployBundleURLForCode(String code, String version, File appBundle, String registryUrl) {
        return getJDeployBundleURLForCode(code, version, appBundle, registryUrl, isPrerelease(appBundle));
    }

    private static URL getJDeployBundleURLForCode(String code, String version, File appBundle, String registryUrl, boolean prerelease) {
        try {
            String prereleaseParam = prerelease ? "&prerelease=true" : "";
            return new URL(registryUrl + "download.php?code=" +
                    URLEncoder.encode(code, "UTF-8") +
                    "&version="+URLEncoder.encode(version, "UTF-8") +
                    "&jdeploy_files=true&platform=*" +
                    prereleaseParam
            );
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException("UTF-8 Encoding doesn't seem to be supported on this platform.", ex);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Programming error.  Malformed URL for bundle. ", e);
        }
    }

    /**
     * Query the jDeploy registry to get package metadata for a bundle code.
     * Returns BundleInfo if found, null if not found (404) or on error.
     */
    private static BundleInfo queryRegistryForBundleInfo(String code) {
        return queryRegistryForBundleInfo(code, JDEPLOY_REGISTRY);
    }

    /**
     * Query a specific jDeploy registry to get package metadata for a bundle code.
     * Returns BundleInfo if found, null if not found (404) or on error.
     */
    private static BundleInfo queryRegistryForBundleInfo(String code, String registryBaseUrl) {
        try {
            URL registryUrl = new URL(registryBaseUrl + "registry/" + URLEncoder.encode(code, "UTF-8"));
            DebugLogger.logNetworkRequest("GET", registryUrl.toString());

            HttpURLConnection conn = (HttpURLConnection) registryUrl.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            DebugLogger.logNetworkResponse(registryUrl.toString(), responseCode,
                responseCode == 200 ? "Success" : "Failed");

            if (responseCode == 404) {
                System.err.println("Bundle code " + code + " not found in registry " + registryBaseUrl);
                return null;
            }

            if (responseCode != 200) {
                System.err.println("Registry lookup failed with HTTP " + responseCode + " from " + registryBaseUrl);
                return null;
            }

            try (InputStream inputStream = conn.getInputStream()) {
                String jsonStr = IOUtil.readToString(inputStream);
                JSONObject json = new JSONObject(jsonStr);

                String projectSource = json.optString("source", null);
                String packageName = json.optString("packageName", null);

                if (projectSource == null || projectSource.isEmpty()) {
                    System.err.println("Registry response missing packageName field");
                    return null;
                }

                // If packageName (source field) is empty, derive it from projectSource
                if (packageName == null || packageName.isEmpty()) {
                    // For GitHub URLs like "https://github.com/user/repo", extract "repo"
                    // For NPM packages, projectSource is already the package name
                    if (projectSource.startsWith("https://github.com/")) {
                        String path = projectSource.substring("https://github.com/".length());
                        if (path.contains("/")) {
                            packageName = path.substring(path.lastIndexOf('/') + 1);
                        } else {
                            packageName = path;
                        }
                    } else {
                        packageName = projectSource;
                    }
                }

                return new BundleInfo(projectSource, packageName, System.currentTimeMillis());
            }
        } catch (Exception e) {
            System.err.println("Failed to query registry " + registryBaseUrl + " for bundle " + code + ": " + e.getMessage());
            return null;
        }
    }

    private static File findDirectoryByNameRecursive(File startDirectory, String name) {
        if (startDirectory.isDirectory()) {
            if (startDirectory.getName().equals(name)) return startDirectory;
            for (File child : startDirectory.listFiles()) {
                File result = findDirectoryByNameRecursive(child, name);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * Download jDeploy bundle for the given code and version.
     * Uses the default jDeploy home directory for caching.
     *
     * @param code The bundle code extracted from installer filename
     * @param version The version string
     * @param appBundle The app bundle file
     * @return The extracted .jdeploy-files directory
     * @throws IOException If download or extraction fails
     */
    public static File downloadJDeployBundleForCode(String code, String version, File appBundle) throws IOException {
        return downloadJDeployBundleForCode(code, version, appBundle, null, null, null, null);
    }

    /**
     * Download jDeploy bundle for the given code and version with a custom jDeploy home.
     * Package-private for testing purposes.
     *
     * @param code The bundle code extracted from installer filename
     * @param version The version string
     * @param appBundle The app bundle file
     * @param jdeployHome The jDeploy home directory (null to use default)
     * @return The extracted .jdeploy-files directory
     * @throws IOException If download or extraction fails
     */
    static File downloadJDeployBundleForCode(String code, String version, File appBundle, File jdeployHome) throws IOException {
        return downloadJDeployBundleForCode(code, version, appBundle, jdeployHome, null, null, null, isPrerelease(appBundle));
    }

    /**
     * Download jDeploy bundle for the given code and version with explicit prerelease flag.
     * Package-private for testing purposes - allows testing prerelease without modifying global state.
     *
     * @param code The bundle code extracted from installer filename
     * @param version The version string
     * @param appBundle The app bundle file
     * @param jdeployHome The jDeploy home directory (null to use default)
     * @param prerelease Whether to request prerelease versions
     * @return The extracted .jdeploy-files directory
     * @throws IOException If download or extraction fails
     */
    static File downloadJDeployBundleForCode(String code, String version, File appBundle, File jdeployHome, boolean prerelease) throws IOException {
        return downloadJDeployBundleForCode(code, version, appBundle, jdeployHome, null, null, null, prerelease);
    }

    /**
     * Download jDeploy bundle for the given code and version with full control for testing.
     * Package-private for testing purposes - allows mocking HTTP operations.
     *
     * @param code The bundle code extracted from installer filename
     * @param version The version string
     * @param appBundle The app bundle file
     * @param jdeployHome The jDeploy home directory (null to use default)
     * @param registryLookup Custom registry lookup implementation (null to use default)
     * @param bundleDownloader Custom bundle downloader implementation (null to use default)
     * @param githubDownloader Custom GitHub downloader implementation (null to use default)
     * @return The extracted .jdeploy-files directory
     * @throws IOException If download or extraction fails
     */
    static File downloadJDeployBundleForCode(
            String code,
            String version,
            File appBundle,
            File jdeployHome,
            RegistryLookup registryLookup,
            BundleDownloader bundleDownloader,
            GitHubDownloader githubDownloader) throws IOException {
        return downloadJDeployBundleForCode(code, version, appBundle, jdeployHome, registryLookup, bundleDownloader, githubDownloader, isPrerelease(appBundle));
    }

    /**
     * Download jDeploy bundle for the given code and version with full control for testing.
     * Package-private for testing purposes - allows mocking HTTP operations and prerelease flag.
     *
     * @param code The bundle code extracted from installer filename
     * @param version The version string
     * @param appBundle The app bundle file
     * @param jdeployHome The jDeploy home directory (null to use default)
     * @param registryLookup Custom registry lookup implementation (null to use default)
     * @param bundleDownloader Custom bundle downloader implementation (null to use default)
     * @param githubDownloader Custom GitHub downloader implementation (null to use default)
     * @param prerelease Whether to request prerelease versions
     * @return The extracted .jdeploy-files directory
     * @throws IOException If download or extraction fails
     */
    static File downloadJDeployBundleForCode(
            String code,
            String version,
            File appBundle,
            File jdeployHome,
            RegistryLookup registryLookup,
            BundleDownloader bundleDownloader,
            GitHubDownloader githubDownloader,
            boolean prerelease) throws IOException {

        // Use default implementations if not provided
        if (registryLookup == null) {
            // Create a registry lookup with fallback support
            registryLookup = (bundleCode) -> {
                // Try primary registry first
                BundleInfo result = queryRegistryForBundleInfo(bundleCode, JDEPLOY_REGISTRY);
                if (result != null) {
                    return result;
                }

                // Try fallback registries
                List<String> fallbacks = parseFallbackRegistries();
                for (String fallbackUrl : fallbacks) {
                    System.out.println("Trying fallback registry: " + fallbackUrl);
                    result = queryRegistryForBundleInfo(bundleCode, fallbackUrl);
                    if (result != null) {
                        System.out.println("Successfully retrieved bundle info from fallback registry: " + fallbackUrl);
                        return result;
                    }
                }

                return null;
            };
        }
        if (bundleDownloader == null) {
            final boolean prereleaseFlag = prerelease;
            bundleDownloader = (c, v, a) -> {
                URL bundleUrl = getJDeployBundleURLForCode(c, v, a, JDEPLOY_REGISTRY, prereleaseFlag);
                DebugLogger.logNetworkRequest("GET", bundleUrl.toString());
                try {
                    InputStream stream = URLUtil.openStream(bundleUrl);
                    DebugLogger.logNetworkResponse(bundleUrl.toString(), 200, "Success");
                    return stream;
                } catch (IOException e) {
                    DebugLogger.logNetworkResponse(bundleUrl.toString(), -1, "Failed: " + e.getMessage());
                    throw e;
                }
            };
        }
        if (githubDownloader == null) {
            // Default implementation: direct HTTP download from GitHub releases
            githubDownloader = (owner, repo, tag, filename) -> {
                String downloadUrl = buildGitHubReleaseUrl(owner, repo, tag, filename);
                DebugLogger.logNetworkRequest("GET", downloadUrl);

                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                DebugLogger.logNetworkResponse(downloadUrl, responseCode,
                    responseCode == 200 ? "Success" : "Failed");

                if (responseCode == 404) {
                    throw new IOException("GitHub release not found: HTTP 404");
                }

                if (responseCode != 200) {
                    throw new IOException("GitHub download failed with HTTP " + responseCode);
                }

                return conn.getInputStream();
            };
        }

        // Build list of registries to try (primary + fallbacks)
        List<String> registriesToTry = new ArrayList<>();
        registriesToTry.add(JDEPLOY_REGISTRY);
        registriesToTry.addAll(parseFallbackRegistries());

        // Initialize cache for primary registry
        BundleRegistryCache cache = jdeployHome != null
                ? new BundleRegistryCache(JDEPLOY_REGISTRY, jdeployHome)
                : new BundleRegistryCache(JDEPLOY_REGISTRY);

        // Step 1: Check cache first
        BundleInfo bundleInfo = cache.lookup(code);
        if (bundleInfo != null) {
            System.out.println("Found bundle " + code + " in local cache");
            System.out.println("  Project: " + bundleInfo.getProjectSource());
            System.out.println("  Package: " + bundleInfo.getPackageName());
        } else {
            // Step 2: Query registry for package metadata
            // If using the default (non-mocked) registryLookup, try fallback registries
            // If using a mocked registryLookup (for testing), only use that
            System.out.println("Querying registry for bundle " + code);
            bundleInfo = registryLookup.queryBundle(code);

            if (bundleInfo != null) {
                // Step 3: Cache immediately after successful registry lookup
                System.out.println("Caching bundle info for " + code);
                System.out.println("  Project: " + bundleInfo.getProjectSource());
                System.out.println("  Package: " + bundleInfo.getPackageName());
                cache.save(code, bundleInfo.getProjectSource(), bundleInfo.getPackageName());
            }
        }

        // Step 3.5: Try GitHub direct download for GitHub-hosted projects
        // This happens AFTER we have bundleInfo (from cache or registry lookup)
        // and BEFORE we try the registry download
        File destDirectory = File.createTempFile("jdeploy-files-download", ".tmp");
        destDirectory.delete();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try {
                FileUtils.deleteDirectory(destDirectory);
            } catch (Exception ex){}
        }));

        if (bundleInfo != null && isGitHubProject(bundleInfo.getProjectSource())) {
            System.out.println("Detected GitHub project, attempting direct download from GitHub releases");
            try {
                GitHubDownloadResult result = downloadFromGitHubRelease(bundleInfo.getProjectSource(), version, destDirectory, prerelease, githubDownloader);
                // Store the successful tag for later use in package-info.json lookup
                successfulGitHubTag.set(result.getSuccessfulTag());
                System.out.println("Successfully downloaded bundle from GitHub releases");
                return result.getJdeployFilesDir();
            } catch (IOException e) {
                System.err.println("GitHub direct download failed: " + e.getMessage());
                System.err.println("Falling back to registry download...");
                // Continue to registry download below
            }
        }

        // Step 4: Download the bundle files from registry (with fallback support)
        // This is now the final fallback for both GitHub and non-GitHub projects
        File destFile = new File(destDirectory, "jdeploy-files.zip");

        List<String> attemptedDownloadUrls = new ArrayList<>();
        IOException lastException = null;

        for (String registryUrl : registriesToTry) {
            try {
                URL downloadUrl = getJDeployBundleURLForCode(code, version, appBundle, registryUrl, prerelease);
                attemptedDownloadUrls.add(downloadUrl.toString());
                System.out.println("Attempting to download bundle from " + registryUrl);

                try (InputStream inputStream = bundleDownloader.openBundleStream(code, version, appBundle)) {
                    FileUtils.copyInputStreamToFile(inputStream, destFile);
                }

                System.out.println("Successfully downloaded bundle from " + registryUrl);
                ArchiveUtil.extract(destFile, destDirectory, "");
                return findDirectoryByNameRecursive(destDirectory, ".jdeploy-files");
            } catch (IOException e) {
                lastException = e;
                System.err.println("Failed to download from " + registryUrl + ": " + e.getMessage());

                // Check if this is a non-retryable error (404, 403)
                if (isNonRetryableError(e)) {
                    System.err.println("Non-retryable error encountered, skipping fallback registries");
                    break;
                }

                // If this wasn't the last registry, continue to next one
                if (!registryUrl.equals(registriesToTry.get(registriesToTry.size() - 1))) {
                    System.out.println("Trying next registry...");
                }
            }
        }

        // If we get here, all registries failed
        if (bundleInfo != null) {
            System.err.println("Bundle file download failed, but registry info is cached:");
            System.err.println("  Project: " + bundleInfo.getProjectSource());
            System.err.println("  Package: " + bundleInfo.getPackageName());
            System.err.println("This will enable faster recovery on next attempt.");
        }

        // Throw exception with all attempted URLs
        IOException finalException = new IOException(
            "Failed to download bundle from all registries. Attempted URLs:\n" +
            String.join("\n", attemptedDownloadUrls)
        );
        if (lastException != null) {
            finalException.initCause(lastException);
        }
        throw finalException;
    }

    private File findAppBundle() {
        File start = new File(System.getProperty("client4j.launcher.path"));
        while (start != null && !start.getName().endsWith(".app")) {
            start = start.getParentFile();
        }
        return start;
    }

    private static String extractVersionFromFileName(String fileName) {
        int pos = fileName.lastIndexOf("_");
        if (pos < 0) return null;

        fileName = fileName.substring(0, pos);
        if (fileName.contains("@")) {
            int lastPos = fileName.lastIndexOf("@");
            fileName = fileName.substring(0, lastPos) + "0.0.0-" + fileName.substring(lastPos+1);
        }

        Pattern p = Pattern.compile("^.*?-(\\d[a-zA-Z0-9\\.\\-_]*)$");
        Matcher m = p.matcher(fileName);
        if (m.matches()) {
            return m.group(1);
        }
        return null;

    }

    private static boolean isPrerelease(File appBundle)  {
        if ("true".equals(System.getProperty("jdeploy.prerelease", "false"))) {
            // This property is passed in by the launcher if the app.xml contained the prerelease
            // attribute set to true.  This is useful so that the installer knows whether it is a
            // prerelease - in which case it will be obtaining bundles for prerelease builds.
            return true;
        }
        if (!findBundleAppXml(appBundle).exists()) {
            return false;
        }
        try (InputStream inputStream = new FileInputStream(findBundleAppXml(appBundle))) {
            Document doc = XMLUtil.parse(inputStream);
            return "true".equals(doc.getDocumentElement().getAttribute("prerelease"));
        } catch (Exception ex) {
            return false;
        }

    }

    private static File findBundleAppXml(File appBundle) {
        return new File(appBundle, "Contents" + File.separator + "app.xml");
    }

    private File findInstallFilesDir(File startDir) {
        if (startDir == null) return null;
        if (Platform.getSystemPlatform().isMac() && "AppTranslocation".equals(startDir.getName())) {
            System.out.println("Detected that we are running inside Gatekeeper so we can't retrieve bundle info");
            System.out.println("Attempting to download bundle info from network");
            // Gatekeeper is running the app from a random location, so we won't be able to find the
            // app.xml file the normal way.
            // We need to be creative.
            // Using the name of the installer,
            // we can extract the package name and version
            File appBundle = findAppBundle();
            if (appBundle == null) {
                System.err.println("Failed to find app bundle");
                return null;
            }
            String code = extractJDeployBundleCodeFromFileName(appBundle.getName());
            if (code == null) {
                System.err.println("Cannot download bundle info from the network because no code was found in the app name: "+appBundle.getName());
                return null;
            }
            String version = extractVersionFromFileName(appBundle.getName());
            if (version == null) {
                System.err.println("Cannot download bundle info from network because the version string was not found in the app name: "+appBundle.getName());
                return null;
            }
            try {
                return downloadJDeployBundleForCode(code, version, appBundle);
            } catch (IOException ex) {
                System.err.println("Failed to download bundle from the network for code "+code+".");
                ex.printStackTrace(System.err);
                return null;
            }


        }
        File candidate = new File(startDir, ".jdeploy-files");
        if (candidate.exists() && candidate.isDirectory()) return candidate;
        return findInstallFilesDir(startDir.getParentFile());
    }

    public void applyContext(InstallationSettings settings) {
        settings.setInstallFilesDir(findInstallFilesDir());
        NPMApplication app = settings.getNpmPackageVersion().toNPMApplication();
        if (app.getHomepage() != null) {
            WebsiteVerifier verifier = new WebsiteVerifier();

            try {
                if (verifier.verifyHomepage(app)) {
                    settings.setWebsiteURL(new URL(app.getHomepage()));
                }
            } catch (Exception ex){
                System.err.println("Failed to verify homepage "+app.getHomepage());
                ex.printStackTrace(System.err);
            }
        }
        if (settings.getWebsiteURL() == null) {
            try {
                settings.setWebsiteURL(new URL(getDefaultWebsiteUrl(app.getPackageName(), app.getSource())));
            } catch (Exception ex){}
        }
    }

    private String getDefaultWebsiteUrl(String packageName, String source) throws UnsupportedEncodingException {
        if (source.startsWith(GITHUB_URL)) {
            return source;
        } else {
            return "https://www.npmjs.com/package/" + URLEncoder.encode(packageName, "UTF-8");
        }
    }


    public File findAppXml() {
        if (System.getProperty("client4j.appxml.path") != null) {
            return new File(System.getProperty("client4j.appxml.path"));
        }
        File installFilesDir = findInstallFilesDir();
        if (installFilesDir == null) {
            return null;
        }
        File appXml =  new File(installFilesDir, "app.xml");
        if (!appXml.exists()) {
            return null;
        }
        return appXml;

    }

    public Document getAppXMLDocument() throws IOException {
        if (appXMLDocument == null) {
            File appXml = findAppXml();
            if (appXml == null) {
                return null;
            }

            try (FileInputStream fis = new FileInputStream(appXml)) {
                appXMLDocument = parseXml(fis);
            } catch (Exception ex) {
                throw new IOException("Failed to parse app.xml: "+ex.getMessage(), ex);
            }
        }
        return appXMLDocument;
    }
    private static Document parseXml(InputStream input) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            return builder.parse(input);
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Check if a project source URL is a GitHub project.
     *
     * @param projectSource The project source URL (e.g., from BundleInfo)
     * @return true if the project is hosted on GitHub
     */
    private static boolean isGitHubProject(String projectSource) {
        return projectSource != null && projectSource.startsWith(GITHUB_URL);
    }

    /**
     * Extract the owner/repo path from a GitHub project URL.
     *
     * @param projectSource GitHub URL like "https://github.com/owner/repo" or "https://github.com/owner/repo.git"
     * @return "owner/repo" or null if not a valid GitHub URL
     */
    private static String extractGitHubRepoPath(String projectSource) {
        if (projectSource == null || !projectSource.startsWith(GITHUB_URL)) {
            return null;
        }

        String path = projectSource.substring(GITHUB_URL.length());

        // Remove trailing slash
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // Remove .git suffix
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }

        return path;
    }

    /**
     * Normalize a version string to include the 'v' prefix for GitHub release tags.
     * Special case: versions starting with "v0.0.0-" are branch-based releases,
     * and the actual GitHub release tag is just the branch name after the dash.
     *
     * @param version Version string like "1.2.3", "v1.2.3", or "v0.0.0-branch-name"
     * @return Normalized version tag for GitHub releases
     */
    private static String normalizeVersionTag(String version) {
        if (version == null || version.isEmpty()) {
            return version;
        }

        // Handle branch-based releases (v0.0.0-branch-name -> branch-name)
        if (version.startsWith("v0.0.0-")) {
            return version.substring("v0.0.0-".length());
        }

        // Handle 0.0.0-branch-name -> branch-name
        if (version.startsWith("0.0.0-")) {
            return version.substring("0.0.0-".length());
        }

        // Already has 'v' prefix
        if (version.startsWith("v")) {
            return version;
        }

        // Add 'v' prefix for regular versions
        return "v" + version;
    }

    /**
     * Build a GitHub release download URL.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param tag Release tag
     * @param filename File to download
     * @return Complete GitHub release download URL
     */
    private static String buildGitHubReleaseUrl(String owner, String repo, String tag, String filename) {
        return GITHUB_URL + owner + "/" + repo + "/releases/download/" + tag + "/" + filename;
    }

    /**
     * Download jDeploy bundle files directly from GitHub releases.
     * Tries version-specific release first, then falls back to 'jdeploy' tag.
     *
     * @param projectSource GitHub project URL
     * @param version Version to download
     * @param destDirectory Destination directory for extraction
     * @param prerelease Whether this is a prerelease build
     * @param githubDownloader The GitHub downloader to use (for testing/mocking)
     * @return GitHubDownloadResult containing extracted directory and successful tag
     * @throws IOException if download fails from all GitHub sources
     */
    private static GitHubDownloadResult downloadFromGitHubRelease(
            String projectSource,
            String version,
            File destDirectory,
            boolean prerelease,
            GitHubDownloader githubDownloader
    ) throws IOException {
        String repoPath = extractGitHubRepoPath(projectSource);
        if (repoPath == null || !repoPath.contains("/")) {
            throw new IOException("Invalid GitHub repository path: " + projectSource);
        }

        String[] parts = repoPath.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        List<String> tagsToTry = new ArrayList<>();

        // Try version-specific tag first
        if (version != null && !version.isEmpty()) {
            String normalizedTag = normalizeVersionTag(version);
            tagsToTry.add(normalizedTag);

            // Also try original version if normalization added a 'v' prefix
            // This handles cases where release was created as '1.2.3' instead of 'v1.2.3'
            if (!normalizedTag.equals(version) && normalizedTag.equals("v" + version)) {
                tagsToTry.add(version);
            }
        }

        File destFile = new File(destDirectory, "jdeploy-files.zip");
        IOException lastException = null;

        for (String tag : tagsToTry) {
            try {
                System.out.println("Attempting to download from GitHub release tag: " + tag);

                // Use the injected downloader
                try (InputStream inputStream = githubDownloader.downloadFromRelease(owner, repo, tag, "jdeploy-files.zip")) {
                    FileUtils.copyInputStreamToFile(inputStream, destFile);
                }

                System.out.println("Successfully downloaded bundle from GitHub release: " + tag);

                // Extract and return with the successful tag
                ArchiveUtil.extract(destFile, destDirectory, "");
                File jdeployFilesDir = findDirectoryByNameRecursive(destDirectory, ".jdeploy-files");
                return new GitHubDownloadResult(jdeployFilesDir, tag);

            } catch (IOException e) {
                lastException = e;

                // Check if it's a 404 (not found) - continue to next tag
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    System.err.println("GitHub release not found at tag '" + tag + "', trying next tag...");
                    continue;
                }

                // Other errors - log and continue
                System.err.println("Failed to download from GitHub tag '" + tag + "': " + e.getMessage());
            }
        }

        // All GitHub attempts failed
        if (lastException != null) {
            throw new IOException("Failed to download from GitHub releases after trying all tags", lastException);
        }

        throw new IOException("Failed to download from GitHub releases: no tags to try");
    }
}
