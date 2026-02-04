package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.VersionCleaner;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;

/**
 * Service for processing default bundles with global .jdpignore rules.
 * This service combines JAR processing and tarball generation for the default bundle,
 * allowing it to be reused across different publishing drivers (GitHub, NPM, etc.).
 * 
 * The default bundle processing applies only global .jdpignore patterns and excludes
 * platform-specific patterns, making it universal while still benefiting from filtering.
 */
@Singleton
public class DefaultBundleService {
    
    private final PlatformBundleGenerator platformBundleGenerator;
    private final JDeployProjectFactory projectFactory;
    
    @Inject
    public DefaultBundleService(
            PlatformBundleGenerator platformBundleGenerator,
            JDeployProjectFactory projectFactory
    ) {
        this.platformBundleGenerator = platformBundleGenerator;
        this.projectFactory = projectFactory;
    }
    
    /**
     * Processes the default bundle by applying global .jdpignore rules and generating
     * the default tarball. This method should be called after platform-specific bundles
     * have been generated to ensure proper filtering.
     * 
     * The processing includes:
     * 1. Loading project configuration to check for .jdpignore rules
     * 2. Processing JARs in the publish directory using only global patterns
     * 3. Generating the default tarball if needed
     * 
     * @param context the publishing context containing directories and configuration
     * @throws IOException if processing fails
     */
    public void processDefaultBundle(PublishingContext context) throws IOException {
        try {
            // Load the project configuration from package.json
            JDeployProject project = projectFactory.createProject(context.packagingContext.packageJsonFile.toPath());
            
            // Check if the default bundle should be filtered
            if (!platformBundleGenerator.shouldFilterDefaultBundle(project)) {
                context.out().println("No global .jdpignore rules found, skipping default bundle filtering");
                return;
            }
            
            context.out().println("Processing default bundle with global .jdpignore rules...");
            
            // Process JARs in the publish directory using global ignore patterns only
            // Platform.DEFAULT ensures only global patterns are applied, not platform-specific ones
            platformBundleGenerator.processJarsWithIgnoreService(
                    context.getPublishDir(),
                    project,
                    Platform.DEFAULT  // DEFAULT platform means global patterns only
            );

            // Re-sign after filtering to fix certificate pinning
            if (context.packagingContext.isPackageSigningEnabled()) {
                File jdeployBundleDir = new File(context.getPublishDir(), "jdeploy-bundle");
                if (jdeployBundleDir.isDirectory()) {
                    String versionString = getPackageSigningVersionString(context);
                    context.packagingContext.packageSigningService.signPackage(
                            versionString, jdeployBundleDir.getAbsolutePath()
                    );
                }
            }

            context.out().println("Successfully processed default bundle with global .jdpignore rules");
            
        } catch (Exception e) {
            // Log the error but don't fail the entire publishing process
            context.err().println("Warning: Failed to process default bundle: " + e.getMessage());
            context.err().println("Default bundle will be published without .jdpignore processing");
            e.printStackTrace(context.err());
        }
    }
    
    /**
     * Processes the default bundle and generates a tarball.
     * This is a convenience method that combines JAR processing with tarball creation.
     * 
     * @param context the publishing context
     * @param outputDir the directory where the tarball should be created
     * @throws IOException if processing or tarball creation fails
     */
    public void processDefaultBundleAndCreateTarball(PublishingContext context, File outputDir) throws IOException {
        // First process the JARs with global ignore rules
        processDefaultBundle(context);
        
        // Then create the tarball
        try {
            context.out().println("Creating default bundle tarball...");
            context.npm.pack(
                    context.getPublishDir(),
                    outputDir,
                    false
            );
            context.out().println("Successfully created default bundle tarball");
        } catch (Exception e) {
            context.err().println("Warning: Failed to create default bundle tarball: " + e.getMessage());
            throw new IOException("Failed to create default bundle tarball", e);
        }
    }
    
    private String getPackageSigningVersionString(PublishingContext context) throws IOException {
        JSONObject packageJSON = new JSONObject(
                FileUtils.readFileToString(context.packagingContext.packageJsonFile, "UTF-8")
        );
        String versionString = VersionCleaner.cleanVersion(packageJSON.getString("version"));
        if (packageJSON.has("commitHash")) {
            versionString += "#" + packageJSON.getString("commitHash");
        }
        return versionString;
    }

    /**
     * Checks if the project has any global .jdpignore rules that would affect the default bundle.
     * This is a convenience method for drivers to check if default bundle processing is needed.
     * 
     * @param packageJsonFile the package.json file of the project
     * @return true if the default bundle should be processed with .jdpignore rules
     * @throws IOException if the project cannot be loaded
     */
    public boolean shouldProcessDefaultBundle(File packageJsonFile) throws IOException {
        JDeployProject project = projectFactory.createProject(packageJsonFile.toPath());
        return platformBundleGenerator.shouldFilterDefaultBundle(project);
    }
}