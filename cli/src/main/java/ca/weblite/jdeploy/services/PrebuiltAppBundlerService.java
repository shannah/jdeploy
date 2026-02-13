package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerResult;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.publishing.PublishingContext;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for generating native app bundles for prebuilt app distribution.
 *
 * This service wraps the existing bundler infrastructure to generate platform-specific
 * native app bundles (exe, .app, etc.) that can be packaged and distributed as prebuilt apps.
 */
@Singleton
public class PrebuiltAppBundlerService {

    @Inject
    public PrebuiltAppBundlerService() {
    }

    /**
     * Generates native app bundles for the specified platforms.
     *
     * @param context the publishing context
     * @param bundlerSettings the bundler settings
     * @param appInfo the app info
     * @param platforms the platforms to generate bundles for
     * @param outputDir the directory where bundles will be created
     * @return map of platform to generated bundle directory
     * @throws IOException if bundle generation fails
     */
    public Map<Platform, File> generateNativeBundles(
            PublishingContext context,
            BundlerSettings bundlerSettings,
            AppInfo appInfo,
            List<Platform> platforms,
            File outputDir) throws IOException {

        Map<Platform, File> results = new HashMap<>();

        for (Platform platform : platforms) {
            try {
                File bundleDir = generateNativeBundle(context, bundlerSettings, appInfo, platform, outputDir);
                if (bundleDir != null && bundleDir.exists()) {
                    results.put(platform, bundleDir);
                    context.out().println("Generated native bundle for " + platform.getIdentifier() + ": " + bundleDir.getName());
                }
            } catch (Exception e) {
                context.err().println("Warning: Failed to generate native bundle for " + platform.getIdentifier() + ": " + e.getMessage());
                // Continue with other platforms
            }
        }

        return results;
    }

    /**
     * Generates a native app bundle for a specific platform.
     *
     * @param context the publishing context
     * @param bundlerSettings the bundler settings
     * @param appInfo the app info
     * @param platform the target platform
     * @param outputDir the directory where the bundle will be created
     * @return the generated bundle directory, or null if generation failed
     * @throws Exception if bundle generation fails
     */
    public File generateNativeBundle(
            PublishingContext context,
            BundlerSettings bundlerSettings,
            AppInfo appInfo,
            Platform platform,
            File outputDir) throws Exception {

        String targetStr = platformToTarget(platform);
        if (targetStr == null) {
            throw new IllegalArgumentException("Unsupported platform for prebuilt apps: " + platform);
        }

        // Create platform-specific output directory
        File platformOutputDir = new File(outputDir, "prebuilt-" + platform.getIdentifier());
        if (platformOutputDir.exists()) {
            FileUtils.deleteDirectory(platformOutputDir);
        }
        platformOutputDir.mkdirs();

        // Generate the bundle
        BundlerResult result = Bundler.runit(
                bundlerSettings,
                appInfo,
                bundlerSettings.getSource(),
                targetStr,
                platformOutputDir.getAbsolutePath(),
                platformOutputDir.getAbsolutePath()
        );

        // Get the output file/directory from the result
        File outputFile = result.getOutputFile();
        if (outputFile == null) {
            // Try to find the output based on platform type
            outputFile = findBundleOutput(platformOutputDir, platform);
        }

        return outputFile != null ? outputFile : platformOutputDir;
    }

    /**
     * Converts a Platform enum to the bundler's target string.
     */
    private String platformToTarget(Platform platform) {
        switch (platform) {
            case WIN_X64:
                return "win";
            case WIN_ARM64:
                return "win-arm64";
            case MAC_X64:
                return "mac";
            case MAC_ARM64:
                return "mac-arm";
            case LINUX_X64:
                return "linux";
            case LINUX_ARM64:
                return "linux-arm64";
            default:
                return null;
        }
    }

    /**
     * Attempts to find the bundle output file/directory based on platform conventions.
     */
    private File findBundleOutput(File outputDir, Platform platform) {
        if (!outputDir.exists() || !outputDir.isDirectory()) {
            return null;
        }

        File[] files = outputDir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }

        // Look for platform-specific artifacts
        for (File file : files) {
            String name = file.getName().toLowerCase();
            switch (platform) {
                case WIN_X64:
                case WIN_ARM64:
                    if (name.endsWith(".exe") || file.isDirectory()) {
                        return file;
                    }
                    break;
                case MAC_X64:
                case MAC_ARM64:
                    if (name.endsWith(".app") || file.isDirectory()) {
                        return file;
                    }
                    break;
                case LINUX_X64:
                case LINUX_ARM64:
                    if (file.isDirectory() || file.canExecute()) {
                        return file;
                    }
                    break;
            }
        }

        // If no specific artifact found, return the directory itself
        return outputDir;
    }
}
