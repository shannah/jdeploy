package ca.weblite.jdeploy.services.verification;

import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.services.InstalledAppLocator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * macOS-specific installation verification.
 */
public class MacVerifier extends PlatformVerifier {

    private static final String[] SHELL_CONFIG_FILES = {
            ".zshrc",
            ".zprofile",
            ".bash_profile",
            ".bashrc",
            ".profile"
    };

    @Override
    public String getPlatformName() {
        return "macOS " + System.getProperty("os.arch");
    }

    @Override
    protected List<VerificationCheck> getAppStructureChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();
        File appBundle = app.getAppBundle();

        if (appBundle == null) {
            checks.add(VerificationCheck.skipped("App bundle structure",
                    "No app bundle path available"));
            return checks;
        }

        // Check .app bundle exists
        if (!appBundle.exists()) {
            checks.add(VerificationCheck.failed("App bundle exists",
                    "Not found: " + appBundle.getAbsolutePath()));
            return checks;
        }

        // Check Contents directory
        File contentsDir = new File(appBundle, "Contents");
        if (!contentsDir.exists()) {
            checks.add(VerificationCheck.failed("App bundle structure",
                    "Contents directory missing: " + contentsDir.getAbsolutePath()));
            return checks;
        }

        // Check MacOS directory
        File macosDir = new File(contentsDir, "MacOS");
        if (!macosDir.exists()) {
            checks.add(VerificationCheck.failed("App bundle structure",
                    "MacOS directory missing: " + macosDir.getAbsolutePath()));
            return checks;
        }

        // Check Info.plist
        File infoPlist = new File(contentsDir, "Info.plist");
        if (!infoPlist.exists()) {
            checks.add(VerificationCheck.failed("App bundle Info.plist",
                    "Not found: " + infoPlist.getAbsolutePath()));
        } else {
            checks.add(VerificationCheck.passed("App bundle Info.plist exists"));
        }

        checks.add(VerificationCheck.passed("App bundle structure valid"));
        return checks;
    }

    @Override
    protected List<VerificationCheck> getPlatformSpecificInstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {
        // macOS doesn't have additional install checks beyond common ones
        return new ArrayList<>();
    }

    @Override
    protected List<VerificationCheck> getPlatformSpecificUninstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        // Check app bundle is removed
        File appBundle = app.getAppBundle();
        if (appBundle != null && appBundle.exists()) {
            checks.add(VerificationCheck.failed("App bundle removed",
                    "Still exists: " + appBundle.getAbsolutePath()));
        } else {
            checks.add(VerificationCheck.passed("App bundle removed"));
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
        // On macOS/Unix, command wrappers have no extension
        return commandName;
    }

    @Override
    protected File getAppDirectory(InstalledAppLocator.InstalledApp app) {
        File appBundle = app.getAppBundle();
        if (appBundle != null) {
            return new File(appBundle, "Contents");
        }
        return null;
    }

    /**
     * Checks if a shell config file contains a PATH entry for the given directory.
     */
    private boolean containsPathEntry(File configFile, String binPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Look for PATH export/assignment that includes our bin directory
                if (line.contains("PATH") && line.contains(binPath)) {
                    return true;
                }
            }
        } catch (IOException e) {
            // Couldn't read file, assume not present
        }
        return false;
    }
}
