package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.util.PackagePathResolver;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/**
 * Local Properties-based cache for bundle code → project source lookups.
 *
 * Key principle: The jDeploy registry is immutable - once a bundle code is assigned
 * to a project, it never changes. This means cached lookups never become stale and
 * can be trusted indefinitely.
 */
public class BundleRegistryCache {
    private final String registryUrl;
    private final File cacheFile;

    /**
     * Create a registry cache for the given registry URL.
     * Each registry gets its own cache file based on MD5 hash of the URL.
     * Uses the default jDeploy home directory.
     */
    public BundleRegistryCache(String registryUrl) {
        this(registryUrl, PackagePathResolver.getJDeployHome());
    }

    /**
     * Create a registry cache for the given registry URL with a custom jDeploy home.
     * Package-private for testing purposes.
     *
     * @param registryUrl The registry URL
     * @param jdeployHome The jDeploy home directory to use
     */
    BundleRegistryCache(String registryUrl, File jdeployHome) {
        this.registryUrl = registryUrl;
        this.cacheFile = getCacheFile(registryUrl, jdeployHome);
    }

    /**
     * Look up bundle information in the cache.
     * Returns null if not found in cache.
     */
    public BundleInfo lookup(String bundleCode) {
        if (bundleCode == null || bundleCode.isEmpty()) {
            return null;
        }

        if (!cacheFile.exists()) {
            return null;
        }

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(cacheFile)) {
            props.load(in);
            String value = props.getProperty(bundleCode);
            return BundleInfo.fromCacheValue(value);
        } catch (IOException e) {
            System.err.println("Failed to read bundle cache: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save bundle information to the cache.
     * Thread-safe for concurrent writes.
     */
    public synchronized void save(String bundleCode, String projectSource, String packageName) {
        if (bundleCode == null || bundleCode.isEmpty() ||
            projectSource == null || projectSource.isEmpty() ||
            packageName == null || packageName.isEmpty()) {
            return;
        }

        // Ensure cache directory exists
        File cacheDir = cacheFile.getParentFile();
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                System.err.println("Failed to create cache directory: " + cacheDir);
                return;
            }
        }

        Properties props = new Properties();

        // Load existing cache if present
        if (cacheFile.exists()) {
            try (InputStream in = new FileInputStream(cacheFile)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Failed to load existing cache, creating new one: " + e.getMessage());
            }
        }

        // Add new entry
        BundleInfo info = new BundleInfo(projectSource, packageName, System.currentTimeMillis());
        props.setProperty(bundleCode, info.toCacheValue());

        // Write back to file
        try (OutputStream out = new FileOutputStream(cacheFile)) {
            props.store(out, "jDeploy Bundle Registry Cache\nRegistry: " + registryUrl + "\nDO NOT EDIT");
        } catch (IOException e) {
            System.err.println("Failed to save bundle cache: " + e.getMessage());
        }
    }

    /**
     * Get the cache file path for a registry URL using default jDeploy home.
     * Location: {jdeployHome}/registry/{MD5_HASH}.properties
     */
    private static File getCacheFile(String registryUrl) {
        return getCacheFile(registryUrl, PackagePathResolver.getJDeployHome());
    }

    /**
     * Get the cache file path for a registry URL with a custom jDeploy home.
     * Location: {jdeployHome}/registry/{MD5_HASH}.properties
     *
     * @param registryUrl The registry URL
     * @param jdeployHome The jDeploy home directory
     * @return The cache file path
     */
    private static File getCacheFile(String registryUrl, File jdeployHome) {
        String hash = md5Hash(registryUrl);
        return new File(jdeployHome, "registry" + File.separator + hash + ".properties");
    }

    /**
     * Calculate MD5 hash of a string.
     */
    private static String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Extract project source from a downloaded bundle directory.
     * Looks for package.json and reads the "repository.url" or "repository" field.
     */
    public static String extractProjectSourceFromBundle(File bundleDir) {
        File packageJson = new File(bundleDir, "package.json");
        if (!packageJson.exists()) {
            return null;
        }

        try {
            String content = new String(Files.readAllBytes(packageJson.toPath()));
            // Simple JSON parsing for repository field
            // Format: "repository": {"url": "git+https://github.com/user/repo.git"}
            // or: "repository": "https://github.com/user/repo"

            // Try object format first
            int repoPos = content.indexOf("\"repository\"");
            if (repoPos < 0) {
                return null;
            }

            String afterRepo = content.substring(repoPos + "\"repository\"".length());

            // Skip whitespace and colon
            afterRepo = afterRepo.trim();
            if (afterRepo.startsWith(":")) {
                afterRepo = afterRepo.substring(1).trim();
            }

            if (afterRepo.startsWith("{")) {
                // Object format - look for "url" field
                int urlPos = afterRepo.indexOf("\"url\"");
                if (urlPos < 0) {
                    return null;
                }
                afterRepo = afterRepo.substring(urlPos + "\"url\"".length()).trim();
                if (afterRepo.startsWith(":")) {
                    afterRepo = afterRepo.substring(1).trim();
                }
            }

            // Now we should have a quoted string
            if (!afterRepo.startsWith("\"")) {
                return null;
            }

            int endQuote = afterRepo.indexOf("\"", 1);
            if (endQuote < 0) {
                return null;
            }

            String url = afterRepo.substring(1, endQuote);

            // Clean up git+ prefix and .git suffix
            if (url.startsWith("git+")) {
                url = url.substring(4);
            }
            if (url.endsWith(".git")) {
                url = url.substring(0, url.length() - 4);
            }

            return url;
        } catch (IOException e) {
            System.err.println("Failed to read package.json: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract package name from a downloaded bundle directory.
     * Looks for package.json and reads the "name" field.
     */
    public static String extractPackageNameFromBundle(File bundleDir) {
        File packageJson = new File(bundleDir, "package.json");
        if (!packageJson.exists()) {
            return null;
        }

        try {
            String content = new String(Files.readAllBytes(packageJson.toPath()));
            // Simple JSON parsing for name field
            // Format: "name": "package-name"

            int namePos = content.indexOf("\"name\"");
            if (namePos < 0) {
                return null;
            }

            String afterName = content.substring(namePos + "\"name\"".length()).trim();

            // Skip whitespace and colon
            if (afterName.startsWith(":")) {
                afterName = afterName.substring(1).trim();
            }

            // Now we should have a quoted string
            if (!afterName.startsWith("\"")) {
                return null;
            }

            int endQuote = afterName.indexOf("\"", 1);
            if (endQuote < 0) {
                return null;
            }

            return afterName.substring(1, endQuote);
        } catch (IOException e) {
            System.err.println("Failed to read package.json for name: " + e.getMessage());
            return null;
        }
    }
}