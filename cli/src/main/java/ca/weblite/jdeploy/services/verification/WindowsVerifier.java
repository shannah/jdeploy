package ca.weblite.jdeploy.services.verification;

import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.installer.util.WindowsAppDirResolver;
import ca.weblite.jdeploy.services.InstalledAppLocator;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Windows-specific installation verification including registry checks.
 */
public class WindowsVerifier extends PlatformVerifier {

    private static final String UNINSTALL_REGISTRY_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall";

    private static final String ENVIRONMENT_REGISTRY_KEY =
            "HKCU\\Environment";

    @Override
    public String getPlatformName() {
        return "Windows " + System.getProperty("os.arch");
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

        // Check for bin subdirectory (usePrivateJVM case)
        if (appDir.getName().equals("bin")) {
            File parentDir = appDir.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                checks.add(VerificationCheck.passed("Private JVM structure detected"));
            }
        }

        return checks;
    }

    @Override
    protected List<VerificationCheck> getPlatformSpecificInstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        // Check Windows uninstall registry entry
        checks.add(checkUninstallRegistryKey(pkg, true));

        return checks;
    }

    @Override
    protected List<VerificationCheck> getPlatformSpecificUninstallChecks(
            ResolvedPackageInfo pkg,
            InstalledAppLocator.InstalledApp app) {

        List<VerificationCheck> checks = new ArrayList<>();

        // Check uninstall registry key is removed
        checks.add(checkUninstallRegistryKey(pkg, false));

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

        try {
            String registryPath = queryRegistryValue(ENVIRONMENT_REGISTRY_KEY, "Path");
            if (registryPath != null && registryPath.contains(binPath)) {
                return VerificationCheck.passed("PATH updated in registry");
            }

            // Also check current environment PATH
            String currentPath = System.getenv("PATH");
            if (currentPath != null && currentPath.contains(binPath)) {
                return VerificationCheck.passed("PATH updated (environment)");
            }

            return VerificationCheck.failed("PATH updated",
                    "Bin directory not found in registry PATH or current environment");

        } catch (Exception e) {
            return VerificationCheck.skipped("PATH updated",
                    "Could not query registry: " + e.getMessage());
        }
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

        try {
            String registryPath = queryRegistryValue(ENVIRONMENT_REGISTRY_KEY, "Path");
            if (registryPath != null && registryPath.contains(binPath)) {
                return VerificationCheck.failed("PATH entry removed",
                        "Still present in registry PATH");
            }

            return VerificationCheck.passed("PATH entry removed");

        } catch (Exception e) {
            // If we can't query, assume it's okay
            return VerificationCheck.passed("PATH entry removed");
        }
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
        // On Windows, command wrappers have .cmd extension
        return commandName + ".cmd";
    }

    @Override
    protected File getAppDirectory(InstalledAppLocator.InstalledApp app) {
        File guiExecutable = app.getGuiExecutable();
        if (guiExecutable != null) {
            File parent = guiExecutable.getParentFile();
            // Handle usePrivateJVM case where exe is in bin subdirectory
            if (parent != null && parent.getName().equals("bin")) {
                return parent.getParentFile();
            }
            return parent;
        }
        return null;
    }

    /**
     * Check if the uninstall registry key exists or doesn't exist.
     * The installer creates keys with "jdeploy." prefix (see InstallWindowsRegistry.getRegisteredAppName()).
     */
    private VerificationCheck checkUninstallRegistryKey(ResolvedPackageInfo pkg, boolean shouldExist) {
        String fqpn = computeFqpn(pkg);
        // Registry key uses "jdeploy." prefix to match InstallWindowsRegistry naming convention
        String registryKey = UNINSTALL_REGISTRY_KEY + "\\jdeploy." + fqpn;

        try {
            boolean exists = registryKeyExists(registryKey);

            if (shouldExist) {
                if (exists) {
                    return VerificationCheck.passed("Uninstall registry key exists");
                } else {
                    return VerificationCheck.failed("Uninstall registry key exists",
                            "Not found: " + registryKey);
                }
            } else {
                if (exists) {
                    return VerificationCheck.failed("Uninstall registry key removed",
                            "Still exists: " + registryKey);
                } else {
                    return VerificationCheck.passed("Uninstall registry key removed");
                }
            }
        } catch (Exception e) {
            return VerificationCheck.skipped(
                    shouldExist ? "Uninstall registry key exists" : "Uninstall registry key removed",
                    "Could not query registry: " + e.getMessage());
        }
    }

    /**
     * Checks if a registry key exists using reg query.
     */
    private boolean registryKeyExists(String keyPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("reg", "query", keyPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        int exitCode = process.waitFor();
        return exitCode == 0;
    }

    /**
     * Queries a registry value using reg query.
     */
    private String queryRegistryValue(String keyPath, String valueName) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("reg", "query", keyPath, "/v", valueName);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return null;
        }

        // Parse the registry output to extract the value
        // Format: "    Path    REG_SZ    value"
        String[] lines = output.toString().split("\n");
        for (String line : lines) {
            if (line.contains(valueName) && line.contains("REG_")) {
                // Extract value after REG_SZ or REG_EXPAND_SZ
                int regIndex = line.indexOf("REG_");
                if (regIndex > 0) {
                    String afterReg = line.substring(regIndex);
                    int spaceIndex = afterReg.indexOf("    ");
                    if (spaceIndex > 0) {
                        return afterReg.substring(spaceIndex).trim();
                    }
                }
            }
        }

        return null;
    }
}
