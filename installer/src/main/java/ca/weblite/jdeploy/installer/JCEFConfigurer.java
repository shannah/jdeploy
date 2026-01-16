package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.tools.platform.Platform;

import java.io.File;

/**
 * Configures JCEF frameworks for JBR (JetBrains Runtime) when the JCEF variant is specified.
 *
 * <p>This class checks if the application's package.json specifies that it wants to use
 * JBR with the JCEF variant. If so, it locates the JCEF frameworks from the JBR installation
 * (using java.home) and configures the bundler settings to copy them into the app bundle.
 *
 * <p>JCEF (Java Chromium Embedded Framework) requires the Chromium frameworks to be
 * located in the macOS app bundle's Contents/Frameworks/ directory to function properly.
 */
public class JCEFConfigurer {

    /**
     * Configures JCEF framework paths in the bundler settings if the application
     * uses JBR with JCEF variant.
     *
     * @param bundlerSettings The bundler settings to configure
     * @param packageVersion The NPM package version containing the package.json configuration
     */
    public static void configureJCEF(BundlerSettings bundlerSettings, NPMPackageVersion packageVersion) {
        // JCEF framework copying is only needed on macOS
        if (!Platform.getSystemPlatform().isMac()) {
            return;
        }

        // Check if package.json specifies JBR with JCEF variant
        if (!isJBRWithJCEF(packageVersion)) {
            return;
        }

        // Get the JBR installation path from java.home
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isEmpty()) {
            System.err.println("Warning: java.home is not set. Cannot locate JCEF frameworks.");
            return;
        }

        File javaHomeDir = new File(javaHome);
        if (!javaHomeDir.exists()) {
            System.err.println("Warning: java.home directory does not exist: " + javaHome);
            return;
        }

        // JBR structure:
        // java.home = <JBR_ROOT>/Contents/Home
        // JCEF frameworks are at <JBR_ROOT>/Contents/Frameworks
        // So we go up one level from java.home to Contents, then into Frameworks
        File contentsDir = javaHomeDir.getParentFile();
        if (contentsDir == null || !contentsDir.getName().equals("Contents")) {
            System.err.println("Warning: Unexpected JBR directory structure. Expected java.home to be inside Contents/Home");
            return;
        }

        File jcefFrameworksDir = new File(contentsDir, "Frameworks");
        if (!jcefFrameworksDir.exists() || !jcefFrameworksDir.isDirectory()) {
            System.err.println("Warning: JCEF Frameworks directory not found at " + jcefFrameworksDir.getAbsolutePath());
            return;
        }

        // Verify JCEF is actually present by checking for the main framework
        File chromiumFramework = new File(jcefFrameworksDir, "Chromium Embedded Framework.framework");
        if (!chromiumFramework.exists()) {
            System.err.println("Warning: JBR variant is set to JCEF but Chromium Embedded Framework not found in "
                    + jcefFrameworksDir.getAbsolutePath());
            return;
        }

        // Set the JCEF frameworks path in bundler settings
        bundlerSettings.setJcefFrameworksPath(jcefFrameworksDir.getAbsolutePath());
        System.out.println("Detected JBR with JCEF. JCEF frameworks will be copied from: "
                + jcefFrameworksDir.getAbsolutePath());
    }

    /**
     * Checks if the package.json specifies JBR with JCEF variant.
     *
     * @param packageVersion The NPM package version to check
     * @return true if jdkProvider is "jbr" and jbrVariant contains "jcef"
     */
    private static boolean isJBRWithJCEF(NPMPackageVersion packageVersion) {
        try {
            String jdkProvider = packageVersion.getJdkProvider();
            String jbrVariant = packageVersion.getJbrVariant();

            return "jbr".equals(jdkProvider) && jbrVariant != null && jbrVariant.contains("jcef");
        } catch (Exception e) {
            System.err.println("Warning: Error checking for JBR JCEF configuration: " + e.getMessage());
            return false;
        }
    }
}
