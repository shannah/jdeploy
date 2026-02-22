package ca.weblite.jdeploy.services.verification;

import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.services.InstalledAppLocator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux-specific installation verification.
 */
public class LinuxVerifier extends PlatformVerifier {

    private static final String[] SHELL_CONFIG_FILES = {
            ".bashrc",
            ".bash_profile",
            ".profile",
            ".zshrc",
            ".zprofile"
    };

    @Override
    public String getPlatformName() {
        return "Linux " + System.getProperty("os.arch");
    }

    @Override
    protected List<VerificationCheck> getAppStructureChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        File guiExecutable = app.getGuiExecutable();
        if (guiExecutable == null) {
            checks.add(VerificationCheck.skipped("App directory structure",
                    "No executable path available"));
            return checks;
        }

        File appDir = guiExecutable.getParentFile();
        if (appDir == null || !appDir.exists()) {
            checks.add(VerificationCheck.failed("App directory exists",
                    "Not found: " + (appDir != null ? appDir.getAbsolutePath() : "null")));
            return checks;
        }

        checks.add(VerificationCheck.passed("App directory exists"));

        // Check the app is in the expected location (~/.jdeploy/apps/{fqpn})
        String fqpn = computeFqpn(pkg);
        File userHome = new File(System.getProperty("user.home"));
        File expectedAppDir = new File(userHome, ".jdeploy/apps/" + fqpn);

        if (!appDir.getAbsolutePath().equals(expectedAppDir.getAbsolutePath())) {
            checks.add(VerificationCheck.skipped("Standard installation location",
                    "App installed at custom location: " + appDir.getAbsolutePath()));
        } else {
            checks.add(VerificationCheck.passed("Standard installation location"));
        }

        return checks;
    }

    @Override
    protected List<VerificationCheck> getPlatformSpecificInstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        // Check .desktop file exists (optional, for GUI apps)
        File desktopFile = getDesktopFile(pkg);
        if (desktopFile != null) {
            if (desktopFile.exists()) {
                checks.add(VerificationCheck.passed("Desktop entry exists"));
            } else {
                // Desktop entry is optional, so just note it's missing
                checks.add(VerificationCheck.skipped("Desktop entry",
                        "Not found (optional): " + desktopFile.getAbsolutePath()));
            }
        }

        return checks;
    }

    @Override
    protected List<VerificationCheck> getPlatformSpecificUninstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        // Check .desktop file is removed
        File desktopFile = getDesktopFile(pkg);
        if (desktopFile != null && desktopFile.exists()) {
            checks.add(VerificationCheck.failed("Desktop entry removed",
                    "Still exists: " + desktopFile.getAbsolutePath()));
        } else {
            checks.add(VerificationCheck.passed("Desktop entry removed"));
        }

        // Check app directory is removed
        String fqpn = computeFqpn(pkg);
        File userHome = new File(System.getProperty("user.home"));
        File appDir = new File(userHome, ".jdeploy/apps/" + fqpn);

        if (appDir.exists()) {
            checks.add(VerificationCheck.failed("App directory removed",
                    "Still exists: " + appDir.getAbsolutePath()));
        } else {
            checks.add(VerificationCheck.passed("App directory removed"));
        }

        return checks;
    }

    @Override
    protected VerificationCheck checkPathUpdated(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        File binDir = getBinDirectory(pkg, app);
        if (binDir == null) {
            return VerificationCheck.skipped("PATH updated",
                    "Could not determine bin directory");
        }

        String binPath = binDir.getAbsolutePath();
        File userHome = new File(System.getProperty("user.home"));

        // Check each shell config file for PATH entry
        List<String> checkedFiles = new ArrayList<>();

        for (String configFileName : SHELL_CONFIG_FILES) {
            File configFile = new File(userHome, configFileName);
            if (configFile.exists()) {
                checkedFiles.add(configFileName);
                if (containsPathEntry(configFile, binPath)) {
                    return VerificationCheck.passed("PATH updated (" + configFileName + ")");
                }
            }
        }

        // Also check current environment PATH
        String currentPath = System.getenv("PATH");
        if (currentPath != null && currentPath.contains(binPath)) {
            return VerificationCheck.passed("PATH updated (current environment)");
        }

        if (checkedFiles.isEmpty()) {
            return VerificationCheck.skipped("PATH updated",
                    "No shell config files found");
        }

        return VerificationCheck.failed("PATH updated",
                "Bin directory not found in PATH. Checked: " + String.join(", ", checkedFiles));
    }

    @Override
    protected VerificationCheck checkPathRemoved(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        File binDir = getBinDirectory(pkg, app);
        if (binDir == null) {
            return VerificationCheck.passed("PATH entry removed");
        }

        String binPath = binDir.getAbsolutePath();
        File userHome = new File(System.getProperty("user.home"));

        // Check each shell config file to ensure PATH entry is removed
        for (String configFileName : SHELL_CONFIG_FILES) {
            File configFile = new File(userHome, configFileName);
            if (configFile.exists() && containsPathEntry(configFile, binPath)) {
                return VerificationCheck.failed("PATH entry removed",
                        "Still present in " + configFileName);
            }
        }

        return VerificationCheck.passed("PATH entry removed");
    }

    @Override
    protected File getBinDirectory(ResolvedPackageInfo pkg,
                                   InstalledAppLocator.InstalledApp app) {
        return CliCommandBinDirResolver.getPerAppBinDir(
                pkg.getPackageName(),
                pkg.getSource()
        );
    }

    @Override
    protected String getCommandWrapperName(String commandName) {
        // On Linux/Unix, command wrappers have no extension
        return commandName;
    }

    @Override
    protected File getAppDirectory(InstalledAppLocator.InstalledApp app) {
        File guiExecutable = app.getGuiExecutable();
        if (guiExecutable != null) {
            return guiExecutable.getParentFile();
        }
        return null;
    }

    /**
     * Gets the expected .desktop file location.
     */
    private File getDesktopFile(ResolvedPackageInfo pkg) {
        String fqpn = computeFqpn(pkg);
        File userHome = new File(System.getProperty("user.home"));
        return new File(userHome, ".local/share/applications/" + fqpn + ".desktop");
    }

    /**
     * Checks if a shell config file contains a PATH entry for the given directory.
     */
    private boolean containsPathEntry(File configFile, String binPath) {
        // Shell configs may use $HOME instead of the absolute path
        String userHome = System.getProperty("user.home");
        String homeRelativePath = binPath.startsWith(userHome)
                ? "$HOME" + binPath.substring(userHome.length())
                : null;

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Look for PATH export/assignment that includes our bin directory
                // Check both absolute path and $HOME-relative path
                if (line.contains("PATH")) {
                    if (line.contains(binPath)) {
                        return true;
                    }
                    if (homeRelativePath != null && line.contains(homeRelativePath)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            // Couldn't read file, assume not present
        }
        return false;
    }
}
