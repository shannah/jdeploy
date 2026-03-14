package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.appbundler.BundlerResult;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.models.BundleArtifact;
import ca.weblite.jdeploy.models.BundleManifest;
import ca.weblite.jdeploy.packaging.PackagingConfig;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.models.CommandSpecParser;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static ca.weblite.jdeploy.BundleConstants.*;

/**
 * Builds platform-specific native bundles at publish time and wraps them
 * in JAR files for distribution as downloadable artifacts.
 *
 * <p>Platforms to build are declared via {@code jdeploy.artifacts} in package.json.
 * Each key is a platform key (e.g. "mac-arm64", "win-x64") with at minimum
 * {@code "enabled": true}. At publish time, this service builds bundles for
 * each enabled platform and the url/sha256 fields are added alongside the
 * existing entry.</p>
 */
@Singleton
public class PublishBundleService {

    private final PackagingConfig packagingConfig;

    @Inject
    public PublishBundleService(PackagingConfig packagingConfig) {
        this.packagingConfig = packagingConfig;
    }

    /**
     * Checks if any artifact platforms are enabled in package.json.
     * Looks for jdeploy.artifacts entries with "enabled": true.
     */
    public boolean isEnabled(PackagingContext context) {
        return !getEnabledPlatformKeys(context).isEmpty();
    }

    /**
     * Builds native bundles for all enabled artifact platforms and wraps them in JARs.
     *
     * @param context the packaging context
     * @param source  the source URL (GitHub repo URL) or null for NPM
     * @return BundleManifest containing all built artifacts
     */
    public BundleManifest buildBundles(PackagingContext context, String source) throws IOException {
        List<String> platformKeys = getEnabledPlatformKeys(context);
        if (platformKeys.isEmpty()) {
            return new BundleManifest(Collections.emptyList());
        }

        String version = VersionCleaner.cleanVersion(context.getVersion());
        String packageName = context.getName();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);

        // Check if CLI commands exist
        boolean hasCliCommands = hasCliCommands(context);

        // Create temp directories for bundle output
        File tempDir = Files.createTempDirectory("jdeploy-publish-bundles").toFile();
        File bundleDestDir = new File(tempDir, "bundles");
        File bundleReleaseDir = new File(tempDir, "releases");
        File jarOutputDir = new File(tempDir, "jars");
        bundleDestDir.mkdirs();
        bundleReleaseDir.mkdirs();
        jarOutputDir.mkdirs();

        List<BundleArtifact> artifacts = new ArrayList<>();

        try {
            for (String platformKey : platformKeys) {
                String platformName = parsePlatformName(platformKey);
                String arch = parseArch(platformKey);
                String bundleTarget = toBundleTarget(platformName, arch);

                if (platformName == null || arch == null || bundleTarget == null) {
                    context.err.println("Warning: Skipping unknown artifact platform key: " + platformKey);
                    continue;
                }

                context.out.println("Building bundle for " + platformKey + "...");

                try {
                    // Build GUI bundle
                    BundlerResult result = buildBundle(context, bundleTarget, source, false);
                    if (result != null && result.getOutputFile() != null) {
                        BundleArtifact guiArtifact = wrapInJar(
                                result.getOutputFile(), jarOutputDir, fqpn,
                                platformName, arch, version, false
                        );
                        artifacts.add(guiArtifact);
                        context.out.println("  Created: " + guiArtifact.getFilename());
                    }

                    // Build CLI bundle if commands exist (Windows only -
                    // macOS includes CLI binary inside the .app bundle)
                    if (hasCliCommands && "win".equals(platformName)) {
                        BundlerResult cliResult = buildBundle(context, bundleTarget, source, true);
                        if (cliResult != null && cliResult.getOutputFile() != null) {
                            BundleArtifact cliArtifact = wrapInJar(
                                    cliResult.getOutputFile(), jarOutputDir, fqpn,
                                    platformName, arch, version, true
                            );
                            artifacts.add(cliArtifact);
                            context.out.println("  Created: " + cliArtifact.getFilename());
                        }
                    }
                } catch (Exception e) {
                    context.err.println("Warning: Failed to build bundle for " + platformKey + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to build publish bundles", e);
        } finally {
            // Clean up intermediate bundle/release dirs (not the JARs)
            FileUtils.deleteQuietly(bundleDestDir);
            FileUtils.deleteQuietly(bundleReleaseDir);
        }

        context.out.println("Built " + artifacts.size() + " bundle artifact(s)");
        return new BundleManifest(artifacts);
    }

    /**
     * Returns the list of platform keys (e.g. "mac-arm64", "win-x64") that have
     * "enabled": true in jdeploy.artifacts.
     */
    private List<String> getEnabledPlatformKeys(PackagingContext context) {
        List<String> keys = new ArrayList<>();
        Map jdeploy = context.mj();
        if (jdeploy == null || !jdeploy.containsKey("artifacts")) {
            return keys;
        }

        Object artifactsObj = jdeploy.get("artifacts");
        if (!(artifactsObj instanceof Map)) {
            return keys;
        }

        Map<String, Object> artifacts = (Map<String, Object>) artifactsObj;
        for (Map.Entry<String, Object> entry : artifacts.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Object enabled = ((Map) value).get("enabled");
                if (Boolean.TRUE.equals(enabled) || "true".equals(String.valueOf(enabled))) {
                    keys.add(entry.getKey());
                }
            }
        }
        return keys;
    }

    private BundlerResult buildBundle(
            PackagingContext context,
            String target,
            String source,
            boolean cliMode
    ) throws Exception {
        AppInfo appInfo = new AppInfo();
        loadAppInfo(context, appInfo);
        if (source != null) {
            appInfo.setNpmSource(source);
        }

        BundlerSettings settings = new BundlerSettings();
        if (source != null) {
            settings.setSource(source);
        }
        settings.setCliCommandsEnabled(cliMode);
        settings.setCompressBundles(false);
        settings.setAutoUpdateEnabled(false);

        String packageJsonVersion = context.m().get("version") != null
                ? context.m().get("version").toString() : "latest";
        appInfo.setNpmVersion(packageJsonVersion);

        // Use temp directories for output
        File tempDir = Files.createTempDirectory("jdeploy-bundle-" + target).toFile();
        String destDir = tempDir.getAbsolutePath();

        try {
            return Bundler.runit(
                    settings,
                    appInfo,
                    appInfo.getAppURL().toString(),
                    target,
                    destDir,
                    destDir
            );
        } catch (Exception e) {
            FileUtils.deleteQuietly(tempDir);
            throw e;
        }
    }

    private void loadAppInfo(PackagingContext context, AppInfo appInfo) throws IOException {
        appInfo.setNpmPackage((String) context.m().get("name"));
        String packageJsonVersion = context.m().get("version") != null
                ? context.m().get("version").toString() : "latest";
        appInfo.setNpmVersion(packageJsonVersion);

        if (context.m().containsKey("source")) {
            appInfo.setNpmSource((String) context.m().get("source"));
        }

        appInfo.setMacAppBundleId(context.getString("macAppBundleId", null));
        appInfo.setTitle(
                context.getString("displayName",
                        context.getString("title", appInfo.getNpmPackage())
                )
        );

        // Set jDeploy registry URL (used in app.xml embedded in native launchers)
        if (context.mj().containsKey("jdeployRegistryUrl")) {
            appInfo.setJdeployRegistryUrl((String) context.mj().get("jdeployRegistryUrl"));
        } else {
            appInfo.setJdeployRegistryUrl(packagingConfig.getJdeployRegistry());
        }

        // Parse CLI commands
        JSONObject jdeployJson = new JSONObject(context.mj());
        appInfo.setCommands(CommandSpecParser.parseCommands(jdeployJson));

        String jarPath = context.getString("jar", null);
        if (jarPath == null) {
            throw new IOException("Cannot load app info: no jar configured");
        }

        // Use the jar from the jdeploy-bundle directory, which contains icon.png
        // alongside it (placed there by PackageService.bundleIcon during makePackage).
        // The Bundler resolves icon.png relative to the app URL, so using the
        // jdeploy-bundle jar ensures the icon is found.
        File jdeployBundleDir = context.getJdeployBundleDir();
        String jarFilename = new File(jarPath).getName();
        File bundledJar = new File(jdeployBundleDir, jarFilename);
        if (bundledJar.exists()) {
            appInfo.setAppURL(bundledJar.toURI().toURL());
        } else {
            // Fallback: use the original jar path but ensure icon.png exists next to it
            File jarFile = new File(context.directory, jarPath);
            File iconFile = new File(jarFile.getAbsoluteFile().getParentFile(), "icon.png");
            if (!iconFile.exists()) {
                File projectIcon = new File(context.directory, "icon.png");
                if (projectIcon.exists()) {
                    FileUtils.copyFile(projectIcon, iconFile);
                }
            }
            appInfo.setAppURL(jarFile.toURI().toURL());
        }
    }

    /**
     * Wraps a bundle output file (or directory like .app) into a JAR.
     */
    private BundleArtifact wrapInJar(
            File bundleFile,
            File jarOutputDir,
            String fqpn,
            String platform,
            String arch,
            String version,
            boolean isCli
    ) throws IOException {
        String cliSuffix = isCli ? "-cli" : "";
        String jarFilename = fqpn + "-" + platform + "-" + arch + "-" + version + cliSuffix + ".jar";
        File jarFile = new File(jarOutputDir, jarFilename);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            if (bundleFile.isDirectory()) {
                // For .app bundles, add the entire directory tree
                addDirectoryToJar(jos, bundleFile, bundleFile.getName());
            } else {
                // For .exe and Linux binaries, add single file
                JarEntry entry = new JarEntry(bundleFile.getName());
                jos.putNextEntry(entry);
                Files.copy(bundleFile.toPath(), jos);
                jos.closeEntry();
            }
        }

        String sha256 = computeSha256(jarFile);

        return new BundleArtifact(jarFile, platform, arch, version, isCli, sha256, jarFilename);
    }

    private void addDirectoryToJar(JarOutputStream jos, File dir, String basePath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryName = basePath + "/" + file.getName();
            if (file.isDirectory()) {
                JarEntry dirEntry = new JarEntry(entryName + "/");
                jos.putNextEntry(dirEntry);
                jos.closeEntry();
                addDirectoryToJar(jos, file, entryName);
            } else {
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                Files.copy(file.toPath(), jos);
                jos.closeEntry();
            }
        }
    }

    private String computeSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream is = new FileInputStream(file)) {
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    private boolean hasCliCommands(PackagingContext context) {
        Map jdeploy = context.mj();
        if (jdeploy.containsKey("commands")) {
            Object commands = jdeploy.get("commands");
            if (commands instanceof List) {
                return !((List) commands).isEmpty();
            } else if (commands instanceof Map) {
                return !((Map) commands).isEmpty();
            }
        }
        return false;
    }

    /**
     * Parses the platform name from a platform key like "mac-arm64" -> "mac".
     */
    private String parsePlatformName(String platformKey) {
        int dash = platformKey.lastIndexOf('-');
        if (dash <= 0) return null;
        return platformKey.substring(0, dash);
    }

    /**
     * Parses the architecture from a platform key like "mac-arm64" -> "arm64".
     */
    private String parseArch(String platformKey) {
        int dash = platformKey.lastIndexOf('-');
        if (dash <= 0 || dash >= platformKey.length() - 1) return null;
        return platformKey.substring(dash + 1);
    }

    /**
     * Maps platform name + arch to a bundle target constant.
     */
    private String toBundleTarget(String platformName, String arch) {
        if (platformName == null || arch == null) return null;
        switch (platformName + "-" + arch) {
            case "mac-x64": return BUNDLE_MAC_X64;
            case "mac-arm64": return BUNDLE_MAC_ARM64;
            case "win-x64": return BUNDLE_WIN_X64;
            case "win-arm64": return BUNDLE_WIN_ARM64;
            case "linux-x64": return BUNDLE_LINUX_X64;
            case "linux-arm64": return BUNDLE_LINUX_ARM64;
            default: return null;
        }
    }
}
