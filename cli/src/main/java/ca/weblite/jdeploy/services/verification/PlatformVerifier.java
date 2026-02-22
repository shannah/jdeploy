package ca.weblite.jdeploy.services.verification;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.jdeploy.services.InstalledAppLocator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for platform-specific installation verification.
 */
public abstract class PlatformVerifier {

    /**
     * Verify that an application is properly installed.
     */
    public List<VerificationCheck> verifyInstallation(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        // Check GUI executable exists
        checks.add(checkGuiExecutableExists(app));

        // Platform-specific app structure checks
        checks.addAll(getAppStructureChecks(pkg, app));

        // Check CLI launcher if commands are defined
        if (pkg.hasCommands()) {
            checks.add(checkCliLauncherExists(app));

            // Check CLI metadata file
            checks.add(checkCliMetadataExists(app));

            // Check command wrappers
            checks.addAll(checkCommandWrappers(pkg, app));

            // Check PATH is updated
            checks.add(checkPathUpdated(pkg, app));
        }

        // Platform-specific checks (registry on Windows, etc.)
        checks.addAll(getPlatformSpecificInstallChecks(pkg, app));

        return checks;
    }

    /**
     * Verify that an application is properly uninstalled.
     */
    public List<VerificationCheck> verifyUninstallation(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        // Check GUI executable is removed
        checks.add(checkGuiExecutableRemoved(app));

        // Check CLI launcher is removed if commands were defined
        if (pkg.hasCommands()) {
            checks.add(checkCliLauncherRemoved(app));
            checks.add(checkCliMetadataRemoved(app));
            checks.addAll(checkCommandWrappersRemoved(pkg, app));
            checks.add(checkPathRemoved(pkg, app));
        }

        // Platform-specific uninstall checks
        checks.addAll(getPlatformSpecificUninstallChecks(pkg, app));

        return checks;
    }

    // ---- Common checks ----

    protected VerificationCheck checkGuiExecutableExists(InstalledAppLocator.InstalledApp app) {
        File exe = app.getGuiExecutable();
        if (exe == null) {
            return VerificationCheck.failed("GUI executable exists", "Path not determined");
        }
        if (!exe.exists()) {
            return VerificationCheck.failed("GUI executable exists",
                    "Not found: " + exe.getAbsolutePath());
        }
        if (!exe.canExecute() && !exe.isDirectory()) { // .app bundles are directories
            return VerificationCheck.failed("GUI executable is executable",
                    "Not executable: " + exe.getAbsolutePath());
        }
        return VerificationCheck.passed("GUI executable exists");
    }

    protected VerificationCheck checkGuiExecutableRemoved(InstalledAppLocator.InstalledApp app) {
        File exe = app.getGuiExecutable();
        if (exe == null) {
            return VerificationCheck.passed("GUI executable removed");
        }
        if (exe.exists()) {
            return VerificationCheck.failed("GUI executable removed",
                    "Still exists: " + exe.getAbsolutePath());
        }
        return VerificationCheck.passed("GUI executable removed");
    }

    protected VerificationCheck checkCliLauncherExists(InstalledAppLocator.InstalledApp app) {
        File cli = app.getCliLauncher();
        if (cli == null) {
            return VerificationCheck.skipped("CLI launcher exists",
                    "No CLI launcher path configured");
        }
        if (!cli.exists()) {
            return VerificationCheck.failed("CLI launcher exists",
                    "Not found: " + cli.getAbsolutePath());
        }
        return VerificationCheck.passed("CLI launcher exists");
    }

    protected VerificationCheck checkCliLauncherRemoved(InstalledAppLocator.InstalledApp app) {
        File cli = app.getCliLauncher();
        if (cli == null) {
            return VerificationCheck.passed("CLI launcher removed");
        }
        if (cli.exists()) {
            return VerificationCheck.failed("CLI launcher removed",
                    "Still exists: " + cli.getAbsolutePath());
        }
        return VerificationCheck.passed("CLI launcher removed");
    }

    protected VerificationCheck checkCliMetadataExists(InstalledAppLocator.InstalledApp app) {
        File appDir = getAppDirectory(app);
        if (appDir == null) {
            return VerificationCheck.skipped("CLI metadata exists",
                    "Could not determine app directory");
        }
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (!metadataFile.exists()) {
            // Graceful degradation - older installations may not have this file
            return VerificationCheck.skipped("CLI metadata exists",
                    "Legacy installation (metadata file not present)");
        }
        return VerificationCheck.passed("CLI metadata exists");
    }

    protected VerificationCheck checkCliMetadataRemoved(InstalledAppLocator.InstalledApp app) {
        File appDir = getAppDirectory(app);
        if (appDir == null) {
            return VerificationCheck.passed("CLI metadata removed");
        }
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (metadataFile.exists()) {
            return VerificationCheck.failed("CLI metadata removed",
                    "Still exists: " + metadataFile.getAbsolutePath());
        }
        return VerificationCheck.passed("CLI metadata removed");
    }

    protected List<VerificationCheck> checkCommandWrappers(ResolvedPackageInfo pkg,
                                                           InstalledAppLocator.InstalledApp app) {
        List<VerificationCheck> checks = new ArrayList<>();
        File binDir = getBinDirectory(pkg, app);

        if (binDir == null) {
            checks.add(VerificationCheck.skipped("Command wrappers exist",
                    "Could not determine bin directory"));
            return checks;
        }

        for (CommandSpec cmd : pkg.getCommands()) {
            String wrapperName = getCommandWrapperName(cmd.getName());
            File wrapper = new File(binDir, wrapperName);

            if (!wrapper.exists()) {
                checks.add(VerificationCheck.failed("Command '" + cmd.getName() + "' wrapper exists",
                        "Not found: " + wrapper.getAbsolutePath()));
            } else {
                checks.add(VerificationCheck.passed("Command '" + cmd.getName() + "' wrapper exists"));
            }
        }

        return checks;
    }

    protected List<VerificationCheck> checkCommandWrappersRemoved(ResolvedPackageInfo pkg,
                                                                   InstalledAppLocator.InstalledApp app) {
        List<VerificationCheck> checks = new ArrayList<>();
        File binDir = getBinDirectory(pkg, app);

        if (binDir == null || !binDir.exists()) {
            // Bin directory doesn't exist, so wrappers are definitely removed
            checks.add(VerificationCheck.passed("Command wrappers removed"));
            return checks;
        }

        for (CommandSpec cmd : pkg.getCommands()) {
            String wrapperName = getCommandWrapperName(cmd.getName());
            File wrapper = new File(binDir, wrapperName);

            if (wrapper.exists()) {
                checks.add(VerificationCheck.failed("Command '" + cmd.getName() + "' wrapper removed",
                        "Still exists: " + wrapper.getAbsolutePath()));
            } else {
                checks.add(VerificationCheck.passed("Command '" + cmd.getName() + "' wrapper removed"));
            }
        }

        return checks;
    }

    // ---- Abstract methods for platform-specific behavior ----

    /**
     * Returns platform-specific app structure checks (e.g., .app bundle on macOS).
     */
    protected abstract List<VerificationCheck> getAppStructureChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app);

    /**
     * Returns platform-specific installation checks (e.g., registry on Windows).
     */
    protected abstract List<VerificationCheck> getPlatformSpecificInstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app);

    /**
     * Returns platform-specific uninstallation checks.
     */
    protected abstract List<VerificationCheck> getPlatformSpecificUninstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app);

    /**
     * Check if PATH is updated with the bin directory.
     */
    protected abstract VerificationCheck checkPathUpdated(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app);

    /**
     * Check if PATH entry is removed.
     */
    protected abstract VerificationCheck checkPathRemoved(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app);

    /**
     * Get the bin directory where command wrappers are installed.
     */
    protected abstract File getBinDirectory(ResolvedPackageInfo pkg,
                                            InstalledAppLocator.InstalledApp app);

    /**
     * Get the command wrapper filename for this platform.
     */
    protected abstract String getCommandWrapperName(String commandName);

    /**
     * Get the app installation directory.
     */
    protected abstract File getAppDirectory(InstalledAppLocator.InstalledApp app);

    /**
     * Get the platform name for display.
     */
    public abstract String getPlatformName();

    // ---- Utility methods ----

    protected String computeFqpn(ResolvedPackageInfo pkg) {
        return CliCommandBinDirResolver.computeFullyQualifiedPackageName(
                pkg.getPackageName(),
                pkg.getSource()
        );
    }
}
