package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.installer.util.WindowsAppDirResolver;
import ca.weblite.jdeploy.packaging.PackagingContext;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service to locate installed jDeploy applications based on package.json configuration.
 * Supports macOS, Windows, and Linux platforms.
 */
@Singleton
public class InstalledAppLocator {

    /**
     * Result of locating an installed application.
     */
    public static class InstalledApp {
        private final File guiExecutable;
        private final File cliLauncher;
        private final File appBundle;  // macOS .app bundle, null on other platforms
        private final String title;
        private final String fqpn;

        public InstalledApp(File guiExecutable, File cliLauncher, File appBundle, String title, String fqpn) {
            this.guiExecutable = guiExecutable;
            this.cliLauncher = cliLauncher;
            this.appBundle = appBundle;
            this.title = title;
            this.fqpn = fqpn;
        }

        public File getGuiExecutable() {
            return guiExecutable;
        }

        public File getCliLauncher() {
            return cliLauncher;
        }

        public File getAppBundle() {
            return appBundle;
        }

        public String getTitle() {
            return title;
        }

        public String getFqpn() {
            return fqpn;
        }

        public boolean isInstalled() {
            return guiExecutable != null && guiExecutable.exists();
        }
    }

    /**
     * Locate installed app from the project directory specified in the PackagingContext.
     *
     * @param context The packaging context containing the project directory
     * @return InstalledApp with paths to executables (may not be installed)
     * @throws IOException if package.json cannot be read
     */
    public InstalledApp locate(PackagingContext context) throws IOException {
        File projectDir = context.directory;
        File packageJsonFile = new File(projectDir, "package.json");

        if (!packageJsonFile.exists()) {
            throw new IOException("No package.json found in " + projectDir.getAbsolutePath());
        }

        String content = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);

        // Extract required properties
        String packageName = packageJson.getString("name");
        String title = getTitle(packageJson);
        String source = getSource(packageJson);
        String winAppDir = getWinAppDir(packageJson);

        // Calculate FQPN
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);

        // Determine paths based on OS
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("mac")) {
            return locateMacApp(title, fqpn);
        } else if (osName.contains("windows")) {
            return locateWindowsApp(title, fqpn, winAppDir);
        } else {
            // Linux and other Unix-like systems
            return locateLinuxApp(title, fqpn);
        }
    }

    private InstalledApp locateMacApp(String title, String fqpn) {
        File userHome = new File(System.getProperty("user.home"));
        File applicationsDir = new File(userHome, "Applications");
        File appBundle = new File(applicationsDir, title + ".app");
        File cliLauncher = new File(appBundle, "Contents/MacOS/" + CliInstallerConstants.CLI_LAUNCHER_NAME);

        // On macOS, the GUI executable is the .app bundle itself (launched via 'open')
        return new InstalledApp(appBundle, cliLauncher, appBundle, title, fqpn);
    }

    private InstalledApp locateWindowsApp(String title, String fqpn, String winAppDir) {
        File appDir = WindowsAppDirResolver.resolveAppDir(winAppDir, fqpn);

        // Check for usePrivateJVM case (executable in bin subdirectory)
        File binDir = new File(appDir, "bin");
        File guiExecutable;
        if (binDir.exists()) {
            guiExecutable = new File(binDir, title + ".exe");
            if (!guiExecutable.exists()) {
                // Fall back to root app directory
                guiExecutable = new File(appDir, title + ".exe");
            }
        } else {
            guiExecutable = new File(appDir, title + ".exe");
        }

        // CLI launcher is in the same directory as GUI executable
        File cliLauncher = new File(guiExecutable.getParentFile(),
                title + CliInstallerConstants.CLI_LAUNCHER_SUFFIX + ".exe");

        return new InstalledApp(guiExecutable, cliLauncher, null, title, fqpn);
    }

    private InstalledApp locateLinuxApp(String title, String fqpn) {
        File userHome = new File(System.getProperty("user.home"));
        File appsDir = new File(userHome, ".jdeploy/apps");
        File appDir = new File(appsDir, fqpn);

        // Linux binary name is derived from title
        String binaryName = deriveLinuxBinaryNameFromTitle(title);
        File guiExecutable = new File(appDir, binaryName);

        // On Linux, CLI launcher is the same as GUI executable
        return new InstalledApp(guiExecutable, guiExecutable, null, title, fqpn);
    }

    /**
     * Derives the Linux binary name from the application title.
     * Matches the logic in installer Main.deriveLinuxBinaryNameFromTitle().
     */
    private String deriveLinuxBinaryNameFromTitle(String title) {
        return title.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "");
    }

    /**
     * Gets the application title from package.json.
     * Priority: jdeploy.title > name
     */
    private String getTitle(JSONObject packageJson) {
        if (packageJson.has("jdeploy")) {
            JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
            if (jdeploy.has("title")) {
                return jdeploy.getString("title");
            }
        }
        return packageJson.getString("name");
    }

    /**
     * Gets the source URL from package.json jdeploy config.
     * Returns null for NPM packages.
     */
    private String getSource(JSONObject packageJson) {
        if (packageJson.has("jdeploy")) {
            JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
            if (jdeploy.has("source")) {
                String source = jdeploy.getString("source");
                return source.isEmpty() ? null : source;
            }
        }
        return null;
    }

    /**
     * Gets the winAppDir from package.json jdeploy config.
     * Returns null to use default.
     */
    private String getWinAppDir(JSONObject packageJson) {
        if (packageJson.has("jdeploy")) {
            JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
            if (jdeploy.has("winAppDir")) {
                String winAppDir = jdeploy.getString("winAppDir");
                return winAppDir.isEmpty() ? null : winAppDir;
            }
        }
        return null;
    }
}
