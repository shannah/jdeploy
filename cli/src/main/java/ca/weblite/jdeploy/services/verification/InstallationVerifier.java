package ca.weblite.jdeploy.services.verification;

import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.services.InstalledAppLocator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Main service for verifying jDeploy application installations and uninstallations.
 *
 * Supports three input methods:
 * 1. Path or URL to package.json
 * 2. Project code (jDeploy registry lookup)
 * 3. Package name with optional source URL
 */
@Singleton
public class InstallationVerifier {

    private final PackageInfoResolver packageInfoResolver;

    @Inject
    public InstallationVerifier(PackageInfoResolver packageInfoResolver) {
        this.packageInfoResolver = packageInfoResolver;
    }

    /**
     * Verifies an installation using a package.json path or URL.
     */
    public InstallationVerificationResult verifyInstallationFromPackageJson(String pathOrUrl)
            throws IOException {
        ResolvedPackageInfo pkg = packageInfoResolver.resolveFromPackageJson(pathOrUrl);
        return verifyInstallation(pkg);
    }

    /**
     * Verifies an installation using a project code.
     */
    public InstallationVerificationResult verifyInstallationFromProjectCode(String projectCode)
            throws IOException {
        ResolvedPackageInfo pkg = packageInfoResolver.resolveFromProjectCode(projectCode);
        return verifyInstallation(pkg);
    }

    /**
     * Verifies an installation using a package name and optional source.
     */
    public InstallationVerificationResult verifyInstallationFromPackage(String packageName, String source)
            throws IOException {
        ResolvedPackageInfo pkg = packageInfoResolver.resolveFromPackage(packageName, source);
        return verifyInstallation(pkg);
    }

    /**
     * Verifies an uninstallation using a package.json path or URL.
     */
    public InstallationVerificationResult verifyUninstallationFromPackageJson(String pathOrUrl)
            throws IOException {
        ResolvedPackageInfo pkg = packageInfoResolver.resolveFromPackageJson(pathOrUrl);
        return verifyUninstallation(pkg);
    }

    /**
     * Verifies an uninstallation using a project code.
     */
    public InstallationVerificationResult verifyUninstallationFromProjectCode(String projectCode)
            throws IOException {
        ResolvedPackageInfo pkg = packageInfoResolver.resolveFromProjectCode(projectCode);
        return verifyUninstallation(pkg);
    }

    /**
     * Verifies an uninstallation using a package name and optional source.
     */
    public InstallationVerificationResult verifyUninstallationFromPackage(String packageName, String source)
            throws IOException {
        ResolvedPackageInfo pkg = packageInfoResolver.resolveFromPackage(packageName, source);
        return verifyUninstallation(pkg);
    }

    /**
     * Verifies an installation using pre-resolved package info.
     */
    public InstallationVerificationResult verifyInstallation(ResolvedPackageInfo pkg) {
        PlatformVerifier verifier = getPlatformVerifier();
        InstalledAppLocator.InstalledApp app = locateApp(pkg);

        InstallationVerificationResult.Builder builder = InstallationVerificationResult.builder()
                .verificationType(InstallationVerificationResult.VerificationType.INSTALLATION)
                .appTitle(pkg.getTitle())
                .packageName(pkg.getPackageName())
                .platform(verifier.getPlatformName());

        if (app != null) {
            builder.installLocation(app.getGuiExecutable());
        }

        // Run all verification checks
        List<VerificationCheck> checks = verifier.verifyInstallation(pkg, app);
        builder.addChecks(checks);

        // Add any warnings
        if (app == null) {
            builder.addWarning("Could not locate installed application");
        }

        return builder.build();
    }

    /**
     * Verifies an uninstallation using pre-resolved package info.
     */
    public InstallationVerificationResult verifyUninstallation(ResolvedPackageInfo pkg) {
        PlatformVerifier verifier = getPlatformVerifier();
        InstalledAppLocator.InstalledApp app = locateApp(pkg);

        InstallationVerificationResult.Builder builder = InstallationVerificationResult.builder()
                .verificationType(InstallationVerificationResult.VerificationType.UNINSTALLATION)
                .appTitle(pkg.getTitle())
                .packageName(pkg.getPackageName())
                .platform(verifier.getPlatformName());

        if (app != null) {
            builder.installLocation(app.getGuiExecutable());
        }

        // Run all verification checks
        List<VerificationCheck> checks = verifier.verifyUninstallation(pkg, app);
        builder.addChecks(checks);

        return builder.build();
    }

    /**
     * Returns the appropriate platform verifier for the current OS.
     */
    private PlatformVerifier getPlatformVerifier() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("mac")) {
            return new MacVerifier();
        } else if (osName.contains("windows")) {
            return new WindowsVerifier();
        } else {
            return new LinuxVerifier();
        }
    }

    /**
     * Locates the installed application based on package info.
     */
    private InstalledAppLocator.InstalledApp locateApp(ResolvedPackageInfo pkg) {
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(
                pkg.getPackageName(),
                pkg.getSource()
        );

        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("mac")) {
            return locateMacApp(pkg.getTitle(), fqpn);
        } else if (osName.contains("windows")) {
            return locateWindowsApp(pkg.getTitle(), fqpn, pkg.getWinAppDir());
        } else {
            return locateLinuxApp(pkg.getTitle(), fqpn);
        }
    }

    private InstalledAppLocator.InstalledApp locateMacApp(String title, String fqpn) {
        File userHome = new File(System.getProperty("user.home"));
        File applicationsDir = new File(userHome, "Applications");
        File appBundle = new File(applicationsDir, title + ".app");
        File cliLauncher = new File(appBundle, "Contents/MacOS/Client4JLauncher-cli");

        return new InstalledAppLocator.InstalledApp(appBundle, cliLauncher, appBundle, title, fqpn);
    }

    private InstalledAppLocator.InstalledApp locateWindowsApp(String title, String fqpn, String winAppDir) {
        // Use WindowsAppDirResolver if available
        File localAppData = new File(System.getenv("LOCALAPPDATA"));
        File appDir;

        if (winAppDir != null && !winAppDir.isEmpty()) {
            appDir = new File(winAppDir);
        } else {
            appDir = new File(localAppData, fqpn);
        }

        // Check for usePrivateJVM case (executable in bin subdirectory)
        File binDir = new File(appDir, "bin");
        File guiExecutable;
        if (binDir.exists()) {
            guiExecutable = new File(binDir, title + ".exe");
            if (!guiExecutable.exists()) {
                guiExecutable = new File(appDir, title + ".exe");
            }
        } else {
            guiExecutable = new File(appDir, title + ".exe");
        }

        File cliLauncher = new File(guiExecutable.getParentFile(), title + "-cli.exe");

        return new InstalledAppLocator.InstalledApp(guiExecutable, cliLauncher, null, title, fqpn);
    }

    private InstalledAppLocator.InstalledApp locateLinuxApp(String title, String fqpn) {
        File userHome = new File(System.getProperty("user.home"));
        File appsDir = new File(userHome, ".jdeploy/apps");
        File appDir = new File(appsDir, fqpn);

        String binaryName = title.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "");
        File guiExecutable = new File(appDir, binaryName);

        return new InstalledAppLocator.InstalledApp(guiExecutable, guiExecutable, null, title, fqpn);
    }
}
