package ca.weblite.jdeploy.publishing.npm;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.npm.OneTimePasswordRequestedException;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishDriverInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.services.PackageSigningService;
import ca.weblite.jdeploy.services.PlatformBundleGenerator;
import ca.weblite.jdeploy.services.DefaultBundleService;
import ca.weblite.jdeploy.services.VersionCleaner;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Singleton
public class NPMPublishDriver implements PublishDriverInterface {

    private static final String REGISTRY_URL="https://registry.npmjs.org/";

    private final PublishDriverInterface basePublishDriver;
    private final PlatformBundleGenerator platformBundleGenerator;
    private final DefaultBundleService defaultBundleService;
    private final JDeployProjectFactory projectFactory;

    @Inject
    public NPMPublishDriver(
            BasePublishDriver basePublishDriver,
            PlatformBundleGenerator platformBundleGenerator,
            DefaultBundleService defaultBundleService,
            JDeployProjectFactory projectFactory
    ) {
        this.basePublishDriver = basePublishDriver;
        this.platformBundleGenerator = platformBundleGenerator;
        this.defaultBundleService = defaultBundleService;
        this.projectFactory = projectFactory;
    }

    @Override
    public void publish(
            PublishingContext context,
            PublishTargetInterface target,
            OneTimePasswordProviderInterface otpProvider
    ) throws IOException {
        // Load the project configuration to check for platform bundles
        JDeployProject project = projectFactory.createProject(context.packagingContext.packageJsonFile.toPath());
        
        if (!project.isPlatformBundlesEnabled()) {
            // Legacy behavior: publish universal bundle only
            // Process the default bundle with global .jdpignore rules before publishing
            defaultBundleService.processDefaultBundle(context);
            publishSinglePackage(context, target, otpProvider, context.getPublishDir(), "main package");
            return;
        }
        
        // Platform bundles are enabled - publish multiple packages
        publishPlatformBundles(context, target, otpProvider, project);

        // Now generate the default bundle
        defaultBundleService.processDefaultBundle(context);
        publishSinglePackage(context, target, otpProvider, context.getPublishDir(), "main package");
    }

    /**
     * Publishes platform-specific bundles to separate NPM packages
     */
    private void publishPlatformBundles(
            PublishingContext context,
            PublishTargetInterface target,
            OneTimePasswordProviderInterface otpProvider,
            JDeployProject project
    ) throws IOException {
        // Get platforms that have NPM package names configured
        List<Platform> platformsWithPackageNames = project.getPlatformsWithNpmPackageNames();
        if (platformsWithPackageNames.isEmpty()) {
            context.out().println("No platform-specific NPM package names configured, skipping platform bundle publishing");
            // Process the default bundle with global .jdpignore rules before publishing
            defaultBundleService.processDefaultBundle(context);
            // Publish universal bundle to main package name
            publishSinglePackage(context, target, otpProvider, context.getPublishDir(), "universal bundle");

            return;
        }
        
        context.out().println("Publishing platform-specific bundles to " + platformsWithPackageNames.size() + " additional NPM packages...");
        
        // Generate platform-specific bundles in a temporary directory
        File tempDir = new File(context.directory(), "jdeploy/npm-platform-bundles");
        if (tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
        tempDir.mkdirs();
        
        try {
            // Pass signing service so platform bundles are re-signed after JAR filtering
            PackageSigningService signingService = null;
            String signingVersionString = null;
            if (context.packagingContext.isPackageSigningEnabled()) {
                signingService = context.packagingContext.packageSigningService;
                signingVersionString = getPackageSigningVersionString(context);
            }

            Map<Platform, File> platformBundles = platformBundleGenerator.generatePlatformBundles(
                    project,
                    context.getPublishDir(),
                    tempDir,
                    signingService,
                    signingVersionString
            );
            
            // Publish each platform-specific bundle
            for (Platform platform : platformsWithPackageNames) {
                File platformBundleDir = platformBundles.get(platform);
                if (platformBundleDir != null && platformBundleDir.exists()) {
                    // Create platform-specific package.json with correct name
                    createPlatformSpecificPackageJson(context, project, platform, platformBundleDir);
                    
                    String platformPackageName = project.getPackageName(platform);
                    String description = "platform bundle for " + platform.getIdentifier() + " (" + platformPackageName + ")"; 
                    publishSinglePackage(context, target, otpProvider, platformBundleDir, description);
                } else {
                    context.out().println("Warning: Platform bundle not generated for " + platform.getIdentifier() + ", skipping NPM publishing");
                }
            }
            
        } finally {
            // Clean up temporary directory
            if (tempDir.exists()) {
                FileUtils.deleteDirectory(tempDir);
            }
        }
        
        context.out().println("All platform bundles published to npm successfully.");
    }
    
    /**
     * Publishes a single package directory to NPM
     */
    private void publishSinglePackage(
            PublishingContext context,
            PublishTargetInterface target,
            OneTimePasswordProviderInterface otpProvider,
            File publishDir,
            String description
    ) throws IOException {
        context.out().println("Publishing " + description + "...");
        
        try {
            context.npm.publish(
                    publishDir,
                    context.packagingContext.exitOnFail,
                    null,
                    context.getDistTag()
            );
        } catch (OneTimePasswordRequestedException ex) {
            String otp = otpProvider.promptForOneTimePassword(context, target);
            if (otp == null || otp.isEmpty()) {
                throw new IOException("Failed to publish " + description + " to npm. No OTP provided.");
            }
            try {
                context.npm.publish(
                        publishDir,
                        context.packagingContext.exitOnFail,
                        otp,
                        context.getDistTag()
                );
            } catch (OneTimePasswordRequestedException ex2) {
                throw new IOException("Failed to publish " + description + " to npm. Invalid OTP provided.");
            }
        }
        
        context.out().println("Successfully published " + description + " to npm.");
    }
    
    /**
     * Creates a platform-specific package.json with the correct NPM package name
     */
    private void createPlatformSpecificPackageJson(
            PublishingContext context,
            JDeployProject project,
            Platform platform,
            File platformBundleDir
    ) throws IOException {
        
        File originalPackageJson = new File(platformBundleDir, "package.json");
        if (!originalPackageJson.exists()) {
            throw new IOException("Platform bundle directory missing package.json: " + platformBundleDir);
        }
        
        // Read the original package.json
        String packageJsonContent = FileUtils.readFileToString(originalPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(packageJsonContent);
        
        // Update the package name to the platform-specific name
        String platformPackageName = project.getPackageName(platform);
        packageJson.put("name", platformPackageName);
        
        // Add platform-specific metadata
        packageJson.put("description", packageJson.optString("description", "") + " (" + platform.getIdentifier() + " bundle)");
        
        JSONObject jdeployConfig = packageJson.optJSONObject("jdeploy");
        if (jdeployConfig == null) {
            jdeployConfig = new JSONObject();
            packageJson.put("jdeploy", jdeployConfig);
        }
        jdeployConfig.put("platformBundle", platform.getIdentifier());
        
        // Write the modified package.json back
        FileUtils.writeStringToFile(originalPackageJson, packageJson.toString(2), StandardCharsets.UTF_8);
        
        context.out().println("Created platform-specific package.json for " + platformPackageName + " (" + platform.getIdentifier() + ")");
    }

    @Override
    public void prepare(PublishingContext context, PublishTargetInterface target, BundlerSettings bundlerSettings) throws IOException {
        basePublishDriver.prepare(context, target, bundlerSettings);
    }

    @Override
    public void makePackage(PublishingContext context, PublishTargetInterface target, BundlerSettings bundlerSettings) throws IOException {
        basePublishDriver.makePackage(context, target, bundlerSettings);
    }

    public JSONObject fetchPackageInfoFromPublicationChannel(String packageName, PublishTargetInterface target) throws IOException {
        URL u = new URL(getPackageUrl(packageName, target));
        HttpURLConnection conn = (HttpURLConnection)u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        if (conn.getResponseCode() != 200) {
            throw new IOException("Failed to fetch Package info for package "+packageName+". "+conn.getResponseMessage());
        }
        return new JSONObject(IOUtil.readToString(conn.getInputStream()));
    }

    public boolean isVersionPublished(String packageName, String version, PublishTargetInterface target) {
        try {
            JSONObject jsonObject = fetchPackageInfoFromPublicationChannel(packageName, target);
            return (jsonObject.has("versions") && jsonObject.getJSONObject("versions").has(version))
                    || (jsonObject.has("versions") && jsonObject.getJSONObject("versions").has(cleanVersion(version)));
        } catch (Exception ex) {
            return false;
        }
    }

    private String getPackageUrl(String packageName, PublishTargetInterface target) throws UnsupportedEncodingException {
        return REGISTRY_URL+ URLEncoder.encode(packageName, "UTF-8");
    }

    private String getPackageSigningVersionString(PublishingContext context) {
        try {
            JSONObject packageJSON = new JSONObject(
                    FileUtils.readFileToString(context.packagingContext.packageJsonFile, StandardCharsets.UTF_8)
            );
            String versionString = VersionCleaner.cleanVersion(packageJSON.getString("version"));
            if (packageJSON.has("commitHash")) {
                versionString += "#" + packageJSON.getString("commitHash");
            }
            return versionString;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read package.json for signing version string", e);
        }
    }

    private String cleanVersion(String version) {
        // Extract suffix from version to make it exempt from cleaning.  We re-append at the end
        String suffix = "";
        int suffixIndex = version.indexOf("-");
        if (suffixIndex != -1) {
            suffix = version.substring(suffixIndex);
            version = version.substring(0, suffixIndex);
        }

        // strip leading zeroes from each component of the version
        String[] parts = version.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(".");
            }
            sb.append(Integer.parseInt(parts[i]));
        }
        return sb.toString() + suffix;
    }
}
