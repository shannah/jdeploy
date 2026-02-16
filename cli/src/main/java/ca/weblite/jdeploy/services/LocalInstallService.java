package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.installer.Main;
import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.jdeploy.packaging.PackagingContext;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Service to perform local installation of jDeploy applications.
 *
 * This service runs the full headless installer using local paths,
 * enabling developers to test their applications in the full jDeploy
 * harness without publishing to npm or GitHub.
 *
 * The local installation:
 * 1. Packages the application using the standard packaging flow
 * 2. Generates .jdeploy-files directory with local-mode app.xml
 * 3. Runs the headless installer pointing to local paths
 * 4. Installs the application with all features (CLI commands, services, etc.)
 */
@Singleton
public class LocalInstallService {

    private final PackageService packageService;
    private final LocalJDeployFilesGenerator localJDeployFilesGenerator;

    @Inject
    public LocalInstallService(
            PackageService packageService,
            LocalJDeployFilesGenerator localJDeployFilesGenerator
    ) {
        this.packageService = packageService;
        this.localJDeployFilesGenerator = localJDeployFilesGenerator;
    }

    /**
     * Performs a local installation of the application.
     *
     * @param context The packaging context
     * @param out Output stream for progress messages
     * @throws IOException if installation fails
     */
    public void install(PackagingContext context, PrintStream out) throws IOException {
        File projectDir = context.directory;
        File bundleDir = new File(projectDir, "jdeploy-bundle");

        out.println("Starting local installation...");

        // Step 1: Package the application
        out.println("Packaging application...");
        packageService.createJdeployBundle(context);

        if (!bundleDir.exists() || !bundleDir.isDirectory()) {
            throw new IOException("jdeploy-bundle directory not found after packaging. " +
                    "Expected at: " + bundleDir.getAbsolutePath());
        }

        // Step 2: Generate .jdeploy-files directory with local-mode app.xml
        out.println("Generating local jdeploy-files...");
        File jdeployDir = new File(projectDir, "jdeploy");
        jdeployDir.mkdirs();

        File jdeployFilesDir = localJDeployFilesGenerator.generate(projectDir, bundleDir, jdeployDir);
        out.println("Generated jdeploy-files at: " + jdeployFilesDir.getAbsolutePath());

        // Step 3: Run the headless installer
        out.println("Running headless installer...");
        runHeadlessInstaller(jdeployFilesDir, out);

        out.println("Local installation completed successfully!");
    }

    /**
     * Runs the headless installer with the generated jdeploy-files.
     */
    private void runHeadlessInstaller(File jdeployFilesDir, PrintStream out) throws IOException {
        File appXmlFile = new File(jdeployFilesDir, "app.xml");
        if (!appXmlFile.exists()) {
            throw new IOException("app.xml not found in jdeploy-files directory: " + jdeployFilesDir);
        }

        try {
            // Use the programmatic headless install method
            Main.runHeadlessInstallProgrammatic(
                appXmlFile.getAbsolutePath(),
                out,
                System.err
            );
        } catch (Exception e) {
            throw new IOException("Headless installer failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the project is configured for local installation.
     *
     * @param projectDir The project directory
     * @return true if the project has the necessary configuration
     */
    public boolean canInstallLocally(File projectDir) {
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            return false;
        }

        try {
            String content = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
            JSONObject packageJson = new JSONObject(content);

            // Check for jdeploy configuration
            return packageJson.has("jdeploy");
        } catch (Exception e) {
            return false;
        }
    }
}
