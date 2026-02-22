package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.installer.uninstall.FileUninstallManifestRepository;
import ca.weblite.jdeploy.installer.uninstall.UninstallService;
import ca.weblite.jdeploy.installer.win.JnaRegistryOperations;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Service to perform local uninstallation of jDeploy applications.
 *
 * This service is the inverse of LocalInstallService, allowing developers
 * to uninstall locally-installed applications during development.
 *
 * The local uninstallation:
 * 1. Resolves package information from package.json or command line options
 * 2. Delegates to the installer module's UninstallService
 * 3. Reports success/failure with detailed error messages
 */
@Singleton
public class LocalUninstallService {

    /**
     * Performs a local uninstallation using package.json in the current directory.
     *
     * @param projectDir The project directory containing package.json
     * @param out Output stream for progress messages
     * @return true if uninstallation was successful, false otherwise
     * @throws IOException if package.json cannot be read
     */
    public boolean uninstall(File projectDir, PrintStream out) throws IOException {
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            throw new IOException("package.json not found in: " + projectDir.getAbsolutePath());
        }

        String content = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);

        String packageName = packageJson.optString("name", null);
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IOException("package.json does not contain a 'name' field");
        }

        // Extract source from jdeploy config (GitHub URL)
        String source = null;
        if (packageJson.has("jdeploy")) {
            JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
            source = jdeploy.optString("source", null);
            if (source != null && source.trim().isEmpty()) {
                source = null;
            }
        }

        return uninstall(packageName, source, out);
    }

    /**
     * Performs a local uninstallation using package name and optional source.
     *
     * @param packageName The package name (e.g., "my-app")
     * @param source The GitHub source URL (null for NPM packages)
     * @param out Output stream for progress messages
     * @return true if uninstallation was successful, false otherwise
     */
    public boolean uninstall(String packageName, String source, PrintStream out) {
        out.println("Starting local uninstallation...");
        out.println("Package: " + packageName);
        if (source != null) {
            out.println("Source: " + source);
        }

        try {
            UninstallService uninstallService = createUninstallService();
            UninstallService.UninstallResult result = uninstallService.uninstall(packageName, source);

            if (result.isSuccess()) {
                out.println("Uninstallation completed successfully!");
                out.println("  " + result.getSuccessCount() + " item(s) cleaned up");
                return true;
            } else {
                out.println("Uninstallation completed with errors:");
                out.println("  " + result.getSuccessCount() + " item(s) cleaned up");
                out.println("  " + result.getFailureCount() + " error(s)");
                for (String error : result.getErrors()) {
                    out.println("  - " + error);
                }
                return false;
            }
        } catch (Exception e) {
            out.println("Uninstallation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates an UninstallService instance.
     *
     * Uses JNA-based registry operations on Windows, no-op implementation on other platforms.
     */
    private UninstallService createUninstallService() {
        FileUninstallManifestRepository manifestRepository = new FileUninstallManifestRepository();
        RegistryOperations registryOperations = createRegistryOperations();
        return new UninstallService(manifestRepository, registryOperations);
    }

    /**
     * Creates platform-appropriate RegistryOperations.
     *
     * Returns JNA-based implementation on Windows, no-op implementation on other platforms.
     */
    private RegistryOperations createRegistryOperations() {
        if (Platform.getSystemPlatform().isWindows()) {
            return new JnaRegistryOperations();
        }
        // Return a no-op implementation for non-Windows platforms
        return new NoOpRegistryOperations();
    }

    /**
     * No-op implementation of RegistryOperations for non-Windows platforms.
     */
    private static class NoOpRegistryOperations implements RegistryOperations {
        @Override
        public boolean keyExists(String key) {
            return false;
        }

        @Override
        public boolean valueExists(String key, String valueName) {
            return false;
        }

        @Override
        public String getStringValue(String key, String valueName) {
            return null;
        }

        @Override
        public void setStringValue(String key, String valueName, String value) {
            // No-op
        }

        @Override
        public void setLongValue(String key, long value) {
            // No-op
        }

        @Override
        public void createKey(String key) {
            // No-op
        }

        @Override
        public void deleteKey(String key) {
            // No-op
        }

        @Override
        public void deleteValue(String key, String valueName) {
            // No-op
        }

        @Override
        public Set<String> getKeys(String key) {
            return Collections.emptySet();
        }

        @Override
        public Map<String, Object> getValues(String key) {
            return Collections.emptyMap();
        }
    }
}
