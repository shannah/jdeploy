package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultInstallationContext implements InstallationContext {
    public static final String JDEPLOY_REGISTRY = System.getProperty(
            "jdeploy.registry.url",
            "https://www.jdeploy.com/"
    );
    private static final String GITHUB_URL = "https://github.com/";

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
        try {
            String prerelease = isPrerelease(appBundle) ? "&prerelease=true" : "";
            return new URL(JDEPLOY_REGISTRY + "download.php?code=" +
                    URLEncoder.encode(code, "UTF-8") +
                    "&version="+URLEncoder.encode(version, "UTF-8") +
                    "&jdeploy_files=true&platform=*" +
                    prerelease
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
        try {
            URL registryUrl = new URL(JDEPLOY_REGISTRY + "registry/" + URLEncoder.encode(code, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) registryUrl.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                System.err.println("Bundle code " + code + " not found in registry");
                return null;
            }

            if (responseCode != 200) {
                System.err.println("Registry lookup failed with HTTP " + responseCode);
                return null;
            }

            try (InputStream inputStream = conn.getInputStream()) {
                String jsonStr = IOUtil.readToString(inputStream);
                JSONObject json = new JSONObject(jsonStr);

                // NOTE: The registry API has confusing field names:
                // - "packageName" field actually contains the project source (GitHub URL or NPM package)
                // - "source" field actually contains the package name (simple name without slashes)
                String projectSource = json.optString("packageName", null);
                String packageName = json.optString("source", null);

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
            System.err.println("Failed to query registry for bundle " + code + ": " + e.getMessage());
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
        return downloadJDeployBundleForCode(code, version, appBundle, null, null, null);
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
        return downloadJDeployBundleForCode(code, version, appBundle, jdeployHome, null, null);
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
     * @return The extracted .jdeploy-files directory
     * @throws IOException If download or extraction fails
     */
    static File downloadJDeployBundleForCode(
            String code,
            String version,
            File appBundle,
            File jdeployHome,
            RegistryLookup registryLookup,
            BundleDownloader bundleDownloader) throws IOException {

        // Use default implementations if not provided
        if (registryLookup == null) {
            registryLookup = DefaultInstallationContext::queryRegistryForBundleInfo;
        }
        if (bundleDownloader == null) {
            bundleDownloader = (c, v, a) -> URLUtil.openStream(getJDeployBundleURLForCode(c, v, a));
        }

        // Initialize cache for this registry
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

        // Step 4: Download the bundle files
        File destDirectory = File.createTempFile("jdeploy-files-download", ".tmp");
        destDirectory.delete();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try {
                FileUtils.deleteDirectory(destDirectory);
            } catch (Exception ex){}
        }));
        File destFile = new File(destDirectory, "jdeploy-files.zip");

        try {
            try (InputStream inputStream = bundleDownloader.openBundleStream(code, version, appBundle)) {
                FileUtils.copyInputStreamToFile(inputStream, destFile);
            }

            ArchiveUtil.extract(destFile, destDirectory, "");
            return findDirectoryByNameRecursive(destDirectory, ".jdeploy-files");
        } catch (IOException e) {
            // If download fails but we have cached info, log it for user visibility
            if (bundleInfo != null) {
                System.err.println("Bundle file download failed, but registry info is cached:");
                System.err.println("  Project: " + bundleInfo.getProjectSource());
                System.err.println("  Package: " + bundleInfo.getPackageName());
                System.err.println("This will enable faster recovery on next attempt.");
            }
            throw e;
        }
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
}
