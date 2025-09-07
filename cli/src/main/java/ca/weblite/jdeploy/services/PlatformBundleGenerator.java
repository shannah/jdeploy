package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
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

    @Inject
    public PlatformBundleGenerator(PlatformSpecificJarProcessor jarProcessor, DownloadPageSettingsService downloadPageSettingsService) {
        this.jarProcessor = jarProcessor;
        this.downloadPageSettingsService = downloadPageSettingsService;
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
            File platformBundleDir = generatePlatformBundle(project, universalPublishDir, outputDir, platform);
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

        // Process JARs using new dual-list resolution rules from RFC
        List<String> namespacesToStrip = getNamespacesToStrip(project, targetPlatform);
        List<String> namespacesToKeep = getNamespacesToKeep(project, targetPlatform);
        
        if (!namespacesToStrip.isEmpty() || !namespacesToKeep.isEmpty()) {
            processNativeNamespacesInBundle(platformBundleDir, namespacesToStrip, namespacesToKeep);
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

        // First generate platform bundles in a temp directory
        File tempDir = Files.createTempDirectory("platform-bundles").toFile();
        
        try {
            Map<Platform, File> platformBundles = generatePlatformBundles(project, universalPublishDir, tempDir);
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
     * Processes native namespaces in all JAR files using dual-list approach.
     * Keep list takes precedence over strip list for any given file.
     * 
     * @param bundleDir the bundle directory containing JAR files
     * @param namespacesToStrip list of namespaces to strip
     * @param namespacesToKeep list of namespaces to keep (overrides strip list)
     * @throws IOException if processing fails
     */
    public void processNativeNamespacesInBundle(File bundleDir, List<String> namespacesToStrip, List<String> namespacesToKeep) throws IOException {
        // Find all JAR files in the bundle
        Collection<File> jarFiles = FileUtils.listFiles(bundleDir, new String[]{"jar"}, true);
        
        for (File jarFile : jarFiles) {
            try {
                jarProcessor.processJarForPlatform(jarFile, namespacesToStrip, namespacesToKeep);
            } catch (Exception e) {
                // Log warning but continue processing other JARs
                System.err.println("Warning: Failed to process namespaces in " + jarFile.getName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Strips native namespaces from all JAR files in the bundle (backward compatibility).
     * 
     * @deprecated Use {@link #processNativeNamespacesInBundle(File, List, List)} instead
     */
    @Deprecated
    private void stripNativeNamespacesFromBundle(File bundleDir, List<String> namespacesToStrip) throws IOException {
        processNativeNamespacesInBundle(bundleDir, namespacesToStrip, Collections.emptyList());
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
     * Gets the list of platforms that will have bundles generated.
     * Respects download page settings to exclude platforms not enabled for download.
     */
    public List<Platform> getPlatformsForBundleGeneration(JDeployProject project) {
        if (!project.isPlatformBundlesEnabled()) {
            return Collections.emptyList();
        }

        // Get all platforms with native namespaces configured
        List<Platform> allPlatforms = new ArrayList<>(project.getNativeNamespaces().keySet());
        
        // Read download page settings from project
        DownloadPageSettings downloadPageSettings = downloadPageSettingsService.read(project.getPackageJSON());
        Set<DownloadPageSettings.BundlePlatform> enabledBundlePlatforms = downloadPageSettings.getResolvedPlatforms();
        
        // Filter platforms based on download page settings
        List<Platform> filteredPlatforms = new ArrayList<>();
        for (Platform platform : allPlatforms) {
            DownloadPageSettings.BundlePlatform bundlePlatform = mapToBundlePlatform(platform);
            if (bundlePlatform != null && enabledBundlePlatforms.contains(bundlePlatform)) {
                filteredPlatforms.add(platform);
            }
        }
        
        return filteredPlatforms;
    }
    
    /**
     * Maps a Platform enum to a DownloadPageSettings.BundlePlatform enum.
     * Returns null if no mapping exists.
     */
    private DownloadPageSettings.BundlePlatform mapToBundlePlatform(Platform platform) {
        switch (platform) {
            case MAC_X64:
                return DownloadPageSettings.BundlePlatform.MacX64;
            case MAC_ARM64:
                return DownloadPageSettings.BundlePlatform.MacArm64;
            case WIN_X64:
                return DownloadPageSettings.BundlePlatform.WindowsX64;
            case WIN_ARM64:
                return DownloadPageSettings.BundlePlatform.WindowsArm64;
            case LINUX_X64:
                return DownloadPageSettings.BundlePlatform.LinuxX64;
            case LINUX_ARM64:
                return DownloadPageSettings.BundlePlatform.LinuxArm64;
            default:
                return null;
        }
    }
    
    /**
     * Gets namespaces to strip for a target platform using RFC resolution rules.
     *
     * Strip List includes:
     * - All namespaces from the "ignore" list (always stripped)
     * - All native namespaces from other platforms (exclude target platform)
     * 
     * @param project the jDeploy project configuration
     * @param targetPlatform the platform to generate strip list for
     * @return list of namespaces to strip
     */
    public List<String> getNamespacesToStrip(JDeployProject project, Platform targetPlatform) {
        List<String> stripList = new ArrayList<>();
        
        // Add ignored namespaces (always stripped from all platform bundles)
        stripList.addAll(project.getIgnoredNamespaces());
        
        // Add all native namespaces from other platforms (not the target platform)
        Map<Platform, List<String>> allNamespaces = project.getNativeNamespaces();
        for (Map.Entry<Platform, List<String>> entry : allNamespaces.entrySet()) {
            Platform platform = entry.getKey();
            if (!platform.equals(targetPlatform)) {
                stripList.addAll(entry.getValue());
            }
        }
        
        return stripList;
    }
    
    /**
     * Gets namespaces to keep for a target platform using RFC resolution rules.
     * 
     * Keep List includes:
     * - All native namespaces explicitly listed for the target platform
     * 
     * Note: Keep list takes precedence over strip list for any given file.
     * 
     * @param project the jDeploy project configuration
     * @param targetPlatform the platform to generate keep list for
     * @return list of namespaces to keep
     */
    public List<String> getNamespacesToKeep(JDeployProject project, Platform targetPlatform) {
        return project.getNativeNamespacesForPlatform(targetPlatform);
    }
    
    /**
     * Explains the namespace resolution logic for debugging/logging purposes.
     * 
     * Example output for mac-x64 platform with configuration:
     * - ignore: ["com.example.test"]
     * - mac-x64: ["com.example.native.mac.x64"] 
     * - win-x64: ["com.example.native.win.x64"]
     * 
     * Results:
     * - Strip: ["com.example.test", "com.example.native.win.x64"]
     * - Keep: ["com.example.native.mac.x64"]
     * - Processing: KEEP list overrides STRIP list for each file
     * 
     * @param project the jDeploy project configuration
     * @param targetPlatform the platform to explain resolution for
     * @return human-readable explanation of the resolution logic
     */
    public String explainNamespaceResolution(JDeployProject project, Platform targetPlatform) {
        List<String> stripList = getNamespacesToStrip(project, targetPlatform);
        List<String> keepList = getNamespacesToKeep(project, targetPlatform);
        
        StringBuilder explanation = new StringBuilder();
        explanation.append("Namespace Resolution for ").append(targetPlatform.getIdentifier()).append(":\n");
        
        explanation.append("\nStrip List (").append(stripList.size()).append(" namespaces):\n");
        if (stripList.isEmpty()) {
            explanation.append("  - No namespaces to strip\n");
        } else {
            for (String namespace : stripList) {
                explanation.append("  - ").append(namespace).append("\n");
            }
        }
        
        explanation.append("\nKeep List (").append(keepList.size()).append(" namespaces):\n");
        if (keepList.isEmpty()) {
            explanation.append("  - No explicit keep namespaces\n");
        } else {
            for (String namespace : keepList) {
                explanation.append("  - ").append(namespace).append("\n");
            }
        }
        
        explanation.append("\nProcessing Logic:\n");
        explanation.append("  1. For each file in JAR:\n");
        explanation.append("  2. If file matches KEEP list → KEEP (keep list overrides strip list)\n");
        explanation.append("  3. Else if file matches STRIP list → STRIP\n");
        explanation.append("  4. Else → KEEP (default behavior)\n");
        
        return explanation.toString();
    }
}