package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployIgnorePattern;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.services.PackageSigningService;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Service for generating platform-specific bundles from a universal bundle.
 * Creates optimized bundles by stripping native libraries for other platforms.
 * 
 * Compatible with existing publishing drivers:
 * - GitHub: Creates directories that can be packed with npm pack
 * - NPM: Creates directories that can be published with npm publish
 */
@Singleton
public class PlatformBundleGenerator {

    private final PlatformSpecificJarProcessor jarProcessor;
    private final DownloadPageSettingsService downloadPageSettingsService;
    private final JDeployIgnoreService ignoreService;

    @Inject
    public PlatformBundleGenerator(PlatformSpecificJarProcessor jarProcessor, DownloadPageSettingsService downloadPageSettingsService, JDeployIgnoreService ignoreService) {
        this.jarProcessor = jarProcessor;
        this.downloadPageSettingsService = downloadPageSettingsService;
        this.ignoreService = ignoreService;
    }

    /**
     * Generates platform-specific bundles from a universal publish directory.
     * 
     * @param project the jDeploy project configuration
     * @param universalPublishDir the universal bundle directory (contains package.json, jars, etc.)
     * @param outputDir the directory where platform-specific bundles will be created
     * @return map of platform to generated bundle directory
     * @throws IOException if bundle generation fails
     */
    public Map<Platform, File> generatePlatformBundles(
            JDeployProject project,
            File universalPublishDir,
            File outputDir) throws IOException {
        return generatePlatformBundles(project, universalPublishDir, outputDir, null, null);
    }

    /**
     * Generates platform-specific bundles from a universal publish directory,
     * re-signing each bundle after JAR filtering if a signing service is provided.
     *
     * @param project the jDeploy project configuration
     * @param universalPublishDir the universal bundle directory (contains package.json, jars, etc.)
     * @param outputDir the directory where platform-specific bundles will be created
     * @param signingService the signing service to re-sign after filtering (nullable)
     * @param signingVersionString the version string for signing (required if signingService is non-null)
     * @return map of platform to generated bundle directory
     * @throws IOException if bundle generation fails
     */
    public Map<Platform, File> generatePlatformBundles(
            JDeployProject project,
            File universalPublishDir,
            File outputDir,
            PackageSigningService signingService,
            String signingVersionString) throws IOException {

        if (!project.isPlatformBundlesEnabled()) {
            return Collections.emptyMap();
        }

        List<Platform> platformsRequiringBundles = getPlatformsForBundleGeneration(project);
        if (platformsRequiringBundles.isEmpty()) {
            return Collections.emptyMap();
        }

        if (!universalPublishDir.exists() || !universalPublishDir.isDirectory()) {
            throw new IllegalArgumentException("Universal publish directory must exist: " + universalPublishDir);
        }

        // Ensure output directory exists
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        Map<Platform, File> generatedBundles = new HashMap<>();

        for (Platform platform : platformsRequiringBundles) {
            File platformBundleDir = generatePlatformBundle(project, universalPublishDir, outputDir, platform, signingService, signingVersionString);
            generatedBundles.put(platform, platformBundleDir);
        }

        return generatedBundles;
    }

    /**
     * Generates a single platform-specific bundle.
     * 
     * @param project the jDeploy project configuration
     * @param universalPublishDir the universal bundle directory
     * @param outputDir the base output directory
     * @param targetPlatform the platform to generate bundle for
     * @return the generated platform bundle directory
     * @throws IOException if bundle generation fails
     */
    public File generatePlatformBundle(
            JDeployProject project,
            File universalPublishDir,
            File outputDir,
            Platform targetPlatform) throws IOException {
        return generatePlatformBundle(project, universalPublishDir, outputDir, targetPlatform, null, null);
    }

    /**
     * Generates a single platform-specific bundle, re-signing after JAR filtering
     * if a signing service is provided.
     *
     * @param project the jDeploy project configuration
     * @param universalPublishDir the universal bundle directory
     * @param outputDir the base output directory
     * @param targetPlatform the platform to generate bundle for
     * @param signingService the signing service to re-sign after filtering (nullable)
     * @param signingVersionString the version string for signing (required if signingService is non-null)
     * @return the generated platform bundle directory
     * @throws IOException if bundle generation fails
     */
    public File generatePlatformBundle(
            JDeployProject project,
            File universalPublishDir,
            File outputDir,
            Platform targetPlatform,
            PackageSigningService signingService,
            String signingVersionString) throws IOException {

        // Create platform-specific directory
        String bundleDirName = getPlatformBundleDirectoryName(project, targetPlatform);
        File platformBundleDir = new File(outputDir, bundleDirName);

        if (platformBundleDir.exists()) {
            FileUtils.deleteDirectory(platformBundleDir);
        }
        platformBundleDir.mkdirs();

        // Copy entire universal bundle to platform directory
        copyUniversalBundle(universalPublishDir, platformBundleDir);

        // Update package.json with platform-specific name if configured
        updatePackageJsonForPlatform(platformBundleDir, project, targetPlatform);

        // Process JARs using .jdpignore files if they exist
        if (ignoreService.hasIgnoreFiles(project)) {
            processJarsWithIgnoreService(platformBundleDir, project, targetPlatform);

            // Re-sign after filtering to fix certificate pinning
            if (signingService != null) {
                File jdeployBundleDir = new File(platformBundleDir, "jdeploy-bundle");
                if (jdeployBundleDir.isDirectory()) {
                    try {
                        signingService.signPackage(signingVersionString, jdeployBundleDir.getAbsolutePath());
                    } catch (Exception ex) {
                        throw new IOException("Failed to re-sign platform bundle after filtering for " + targetPlatform.getIdentifier(), ex);
                    }
                }
            }
        }

        return platformBundleDir;
    }

    /**
     * Generates platform-specific tarballs using npm pack.
     * This is useful for GitHub releases where tarballs are directly uploaded.
     * 
     * @param project the jDeploy project configuration  
     * @param universalPublishDir the universal bundle directory
     * @param outputDir the directory where tarballs will be created
     * @param npm the NPM instance to use for packing
     * @param exitOnFail whether to exit on failure
     * @return map of platform to generated tarball file
     * @throws IOException if generation fails
     */
    public Map<Platform, File> generatePlatformTarballs(
            JDeployProject project,
            File universalPublishDir,
            File outputDir,
            NPM npm,
            boolean exitOnFail) throws IOException {
        return generatePlatformTarballs(project, universalPublishDir, outputDir, npm, exitOnFail, null, null);
    }

    /**
     * Generates platform-specific tarballs using npm pack, re-signing each bundle
     * after JAR filtering if a signing service is provided.
     *
     * @param project the jDeploy project configuration
     * @param universalPublishDir the universal bundle directory
     * @param outputDir the directory where tarballs will be created
     * @param npm the NPM instance to use for packing
     * @param exitOnFail whether to exit on failure
     * @param signingService the signing service to re-sign after filtering (nullable)
     * @param signingVersionString the version string for signing (required if signingService is non-null)
     * @return map of platform to generated tarball file
     * @throws IOException if generation fails
     */
    public Map<Platform, File> generatePlatformTarballs(
            JDeployProject project,
            File universalPublishDir,
            File outputDir,
            NPM npm,
            boolean exitOnFail,
            PackageSigningService signingService,
            String signingVersionString) throws IOException {

        // First generate platform bundles in a temp directory
        File tempDir = Files.createTempDirectory("platform-bundles").toFile();

        try {
            Map<Platform, File> platformBundles = generatePlatformBundles(project, universalPublishDir, tempDir, signingService, signingVersionString);
            Map<Platform, File> tarballs = new HashMap<>();

            for (Map.Entry<Platform, File> entry : platformBundles.entrySet()) {
                Platform platform = entry.getKey();
                File bundleDir = entry.getValue();

                // Create tarball using npm pack (generates file based on package.json name)
                createTarball(bundleDir, outputDir, npm, exitOnFail);

                // Find the actual generated tarball file and rename it to RFC convention
                File renamedTarball = findAndRenameTarball(project, platform, bundleDir, outputDir);
                tarballs.put(platform, renamedTarball);
            }

            return tarballs;

        } finally {
            // Clean up temp directory
            if (tempDir.exists()) {
                FileUtils.deleteDirectory(tempDir);
            }
        }
    }

    /**
     * Gets the directory name for a platform-specific bundle.
     * Format: {packageName}-{platform} or {packageName} if no platform-specific name
     */
    private String getPlatformBundleDirectoryName(JDeployProject project, Platform platform) {
        String platformPackageName = project.getPackageName(platform);
        if (platformPackageName != null) {
            return platformPackageName;
        }
        
        // Fallback: use main package name + platform identifier
        JSONObject packageJson = project.getPackageJSON();
        String baseName = packageJson.optString("name", "app");
        return baseName + "-" + platform.getIdentifier();
    }

    /**
     * Gets the tarball filename for a platform-specific bundle.
     * Format follows RFC specification: {appname}-{version}-{platform}.tgz
     */
    private String getPlatformTarballName(JDeployProject project, Platform platform) {
        JSONObject packageJson = project.getPackageJSON();
        String version = packageJson.optString("version", "1.0.0");
        String baseName = packageJson.optString("name", "app");
        
        // RFC naming pattern: {appname}-{version}-{platform}.tgz
        return baseName + "-" + version + "-" + platform.getIdentifier() + ".tgz";
    }

    /**
     * Copies the universal bundle to the platform directory.
     */
    private void copyUniversalBundle(File source, File destination) throws IOException {
        FileUtils.copyDirectory(source, destination);
    }

    /**
     * Updates package.json in the platform bundle with platform-specific name.
     * Always updates the name to ensure unique npm pack output, even if no platform-specific name is configured.
     */
    private void updatePackageJsonForPlatform(
            File platformBundleDir, 
            JDeployProject project, 
            Platform platform) throws IOException {
        
        File packageJsonFile = new File(platformBundleDir, "package.json");
        if (!packageJsonFile.exists()) {
            return; // No package.json to update
        }

        // Read current package.json
        String content = FileUtils.readFileToString(packageJsonFile, "UTF-8");
        JSONObject packageJson = new JSONObject(content);
        
        // Always update name to ensure unique npm pack output
        String platformPackageName = project.getPackageName(platform);
        if (platformPackageName != null) {
            // Use configured platform-specific name
            packageJson.put("name", platformPackageName);
        } else {
            // Create unique name: {originalName}-temp-{platform}
            String originalName = packageJson.optString("name", "app");
            String tempName = originalName + "-temp-" + platform.getIdentifier();
            packageJson.put("name", tempName);
        }
        
        // Add metadata to indicate this is a platform-specific variant
        JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
        if (jdeploy == null) {
            jdeploy = new JSONObject();
            packageJson.put("jdeploy", jdeploy);
        }
        jdeploy.put("platformVariant", platform.getIdentifier());
        jdeploy.put("universalPackage", project.getPackageJSON().optString("name"));

        // Write updated package.json
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), "UTF-8");
    }

    
    /**
     * Processes JAR files in a bundle using .jdpignore files.
     * This is the new approach that uses the JDeployIgnoreService.
     * 
     * @param bundleDir the bundle directory containing JAR files
     * @param project the JDeploy project (for accessing .jdpignore files)
     * @param platform the target platform (can be null to use only global .jdpignore rules)
     * @throws IOException if processing fails
     */
    public void processJarsWithIgnoreService(File bundleDir, JDeployProject project, Platform platform) throws IOException {
        // Find all JAR files in the bundle
        Collection<File> jarFiles = FileUtils.listFiles(bundleDir, new String[]{"jar"}, true);
        
        for (File jarFile : jarFiles) {
            try {
                jarProcessor.processJarForPlatform(jarFile, project, platform);
            } catch (Exception e) {
                // Log warning but continue processing other JARs
                System.err.println("Warning: Failed to process JAR with ignore service " + jarFile.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Creates a tarball from a bundle directory using npm pack.
     * Uses npm pack which is cross-platform and produces npm-compatible tarballs.
     * 
     * @param bundleDir the directory to pack
     * @param outputDir the directory where the tarball will be created
     * @param npm the NPM instance
     * @param exitOnFail whether to exit on failure
     */
    private void createTarball(File bundleDir, File outputDir, NPM npm, boolean exitOnFail) throws IOException {
        // Ensure output directory exists
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Use npm.pack() which is cross-platform
        npm.pack(bundleDir, outputDir, exitOnFail);
    }
    
    /**
     * Finds the tarball generated by npm pack and renames it to RFC naming convention.
     * npm pack generates: {packageName}-{version}.tgz
     * We rename to: {originalAppName}-{version}-{platform}.tgz
     */
    private File findAndRenameTarball(JDeployProject project, Platform platform, File bundleDir, File outputDir) throws IOException {
        // Read the package.json from the bundle to get the name used by npm pack
        File packageJsonFile = new File(bundleDir, "package.json");
        if (!packageJsonFile.exists()) {
            throw new IOException("package.json not found in bundle directory: " + bundleDir);
        }
        
        String content = FileUtils.readFileToString(packageJsonFile, "UTF-8");
        JSONObject packageJson = new JSONObject(content);
        String packageName = packageJson.optString("name", "app");
        String version = packageJson.optString("version", "1.0.0");
        
        // This is the filename that npm pack generated
        String npmPackFilename = packageName + "-" + version + ".tgz";
        File npmPackFile = new File(outputDir, npmPackFilename);
        
        if (!npmPackFile.exists()) {
            throw new IOException("Expected tarball not found: " + npmPackFile.getAbsolutePath());
        }
        
        // This is the RFC-compliant filename we want
        String rfcCompliantName = getPlatformTarballName(project, platform);
        File rfcCompliantFile = new File(outputDir, rfcCompliantName);
        
        // If they're the same, no need to rename
        if (npmPackFilename.equals(rfcCompliantName)) {
            return npmPackFile;
        }
        
        // Rename to RFC-compliant name
        if (rfcCompliantFile.exists()) {
            rfcCompliantFile.delete(); // Remove any existing file
        }
        
        if (!npmPackFile.renameTo(rfcCompliantFile)) {
            throw new IOException("Failed to rename tarball from " + npmPackFilename + " to " + rfcCompliantName);
        }
        
        return rfcCompliantFile;
    }

    /**
     * Utility method to check if platform bundles should be generated.
     */
    public boolean shouldGeneratePlatformBundles(JDeployProject project) {
        return !getPlatformsForBundleGeneration(project).isEmpty();
    }

    /**
     * Checks if the default bundle should be filtered with global .jdpignore rules.
     * This is separate from platform bundle generation - we can filter the default bundle
     * even if we don't generate platform bundles.
     */
    public boolean shouldFilterDefaultBundle(JDeployProject project) {
        // Filter default bundle if global .jdpignore file exists with patterns
        return ignoreService.hasIgnoreFiles(project);
    }

    /**
     * Gets the list of platforms that will have bundles generated.
     * A platform-specific bundle is generated ONLY if:
     * 1. The platform is enabled in download page settings, AND
     * 2. There is a .jdpignore.{platform} file for that platform with at least one pattern rule
     */
    public List<Platform> getPlatformsForBundleGeneration(JDeployProject project) {
        if (!project.isPlatformBundlesEnabled()) {
            return Collections.emptyList();
        }
        
        // Read download page settings from project
        DownloadPageSettings downloadPageSettings = downloadPageSettingsService.read(project.getPackageJSON());
        Set<DownloadPageSettings.BundlePlatform> enabledBundlePlatforms = downloadPageSettings.getResolvedPlatforms();
        
        // Generate bundles only for enabled platforms that have platform-specific ignore files with patterns
        List<Platform> platforms = new ArrayList<>();
        for (DownloadPageSettings.BundlePlatform bundlePlatform : enabledBundlePlatforms) {
            Platform platform = mapFromBundlePlatform(bundlePlatform);
            if (platform != null && hasPlatformSpecificIgnoreFile(project, platform)) {
                platforms.add(platform);
            }
        }
        
        return platforms;
    }
    
    /**
     * Maps a DownloadPageSettings.BundlePlatform enum to a Platform enum.
     * Returns null if no mapping exists.
     */
    private Platform mapFromBundlePlatform(DownloadPageSettings.BundlePlatform bundlePlatform) {
        switch (bundlePlatform) {
            case MacX64:
                return Platform.MAC_X64;
            case MacArm64:
                return Platform.MAC_ARM64;
            case WindowsX64:
                return Platform.WIN_X64;
            case WindowsArm64:
                return Platform.WIN_ARM64;
            case LinuxX64:
                return Platform.LINUX_X64;
            case LinuxArm64:
                return Platform.LINUX_ARM64;
            default:
                return null;
        }
    }
    
    /**
     * Checks if a platform has a platform-specific .jdpignore file with at least one pattern.
     * @param project the jDeploy project
     * @param platform the platform to check
     * @return true if the platform has a .jdpignore.{platform} file with patterns
     */
    private boolean hasPlatformSpecificIgnoreFile(JDeployProject project, Platform platform) {
        try {
            List<JDeployIgnorePattern> patterns = ignoreService.getPlatformIgnorePatterns(project, platform);
            return !patterns.isEmpty();
        } catch (Exception e) {
            // If we can't read the file, assume it doesn't exist
            return false;
        }
    }
}