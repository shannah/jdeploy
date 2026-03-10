package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.appbundler.BundlerResult;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings.BundlePlatform;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.models.BundleArtifact;
import ca.weblite.jdeploy.models.BundleManifest;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.models.CommandSpecParser;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static ca.weblite.jdeploy.BundleConstants.*;

/**
 * Builds platform-specific native bundles at publish time and wraps them
 * in JAR files for distribution as downloadable artifacts.
 */
@Singleton
public class PublishBundleService {

    private final DownloadPageSettingsService downloadPageSettingsService;
    private final BundleCodeService bundleCodeService;

    @Inject
    public PublishBundleService(
            DownloadPageSettingsService downloadPageSettingsService,
            BundleCodeService bundleCodeService
    ) {
        this.downloadPageSettingsService = downloadPageSettingsService;
        this.bundleCodeService = bundleCodeService;
    }

    /**
     * Checks if publish bundles are enabled in package.json.
     */
    public boolean isEnabled(PackagingContext context) {
        return context.getBoolean("publishBundles", false);
    }

    /**
     * Builds native bundles for all configured platforms and wraps them in JARs.
     *
     * @param context the packaging context
     * @param source  the source URL (GitHub repo URL) or null for NPM
     * @return BundleManifest containing all built artifacts
     */
    public BundleManifest buildBundles(PackagingContext context, String source) throws IOException {
        if (!isEnabled(context)) {
            return new BundleManifest(Collections.emptyList());
        }

        String version = VersionCleaner.cleanVersion(context.getVersion());
        String packageName = context.getName();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);

        // Determine which platforms to build
        DownloadPageSettings downloadPageSettings = downloadPageSettingsService.read(context.packageJsonFile);
        Set<BundlePlatform> platforms = downloadPageSettings.getResolvedPlatforms();

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
            for (BundlePlatform platform : platforms) {
                String bundleTarget = toBundleTarget(platform);
                if (bundleTarget == null) {
                    continue;
                }

                String platformName = toPlatformName(platform);
                String arch = toArch(platform);
                if (platformName == null || arch == null) {
                    continue;
                }

                context.out.println("Building bundle for " + platformName + "-" + arch + "...");

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
                    context.err.println("Warning: Failed to build bundle for " + platformName + "-" + arch + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // Clean up temp dir on failure, but keep jar output dir since JARs may already be referenced
            throw new IOException("Failed to build publish bundles", e);
        } finally {
            // Clean up intermediate bundle/release dirs (not the JARs)
            FileUtils.deleteQuietly(bundleDestDir);
            FileUtils.deleteQuietly(bundleReleaseDir);
        }

        context.out.println("Built " + artifacts.size() + " bundle artifact(s)");
        return new BundleManifest(artifacts);
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

        // Parse CLI commands
        JSONObject jdeployJson = new JSONObject(context.mj());
        appInfo.setCommands(CommandSpecParser.parseCommands(jdeployJson));

        String jarPath = context.getString("jar", null);
        if (jarPath != null) {
            appInfo.setAppURL(new File(jarPath).toURI().toURL());
        } else {
            throw new IOException("Cannot load app info: no jar configured");
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

    private String toBundleTarget(BundlePlatform platform) {
        switch (platform) {
            case MacX64: return BUNDLE_MAC_X64;
            case MacArm64: return BUNDLE_MAC_ARM64;
            case WindowsX64: return BUNDLE_WIN_X64;
            case WindowsArm64: return BUNDLE_WIN_ARM64;
            case LinuxX64: return BUNDLE_LINUX_X64;
            case LinuxArm64: return BUNDLE_LINUX_ARM64;
            default: return null;
        }
    }

    private String toPlatformName(BundlePlatform platform) {
        switch (platform) {
            case MacX64:
            case MacArm64:
                return "mac";
            case WindowsX64:
            case WindowsArm64:
                return "win";
            case LinuxX64:
            case LinuxArm64:
                return "linux";
            default:
                return null;
        }
    }

    private String toArch(BundlePlatform platform) {
        switch (platform) {
            case MacX64:
            case WindowsX64:
            case LinuxX64:
                return "x64";
            case MacArm64:
            case WindowsArm64:
            case LinuxArm64:
                return "arm64";
            default:
                return null;
        }
    }
}
