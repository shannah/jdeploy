package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.util.PackagePathResolver;
import ca.weblite.jdeploy.installer.cli.WindowsCliCommandInstaller;
import ca.weblite.tools.io.MD5;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UninstallWindows {

    private String packageName;

    private String fullyQualifiedPackageName;

    private String version;
    private String appTitle;
    private InstallWindowsRegistry installWindowsRegistry;
    private String source;

    private String appFileName;

    public UninstallWindows(
            String packageName,
            String source,
            String version,
            String appTitle,
            InstallWindowsRegistry installer
    ) {
        this.packageName = packageName;
        this.source = source;
        this.version = version;
        this.appTitle = appTitle;
        this.installWindowsRegistry = installer;
        this.fullyQualifiedPackageName = installer.getFullyQualifiedPackageName();
        this.appFileName = this.appTitle;
        if (version != null && version.startsWith("0.0.0-")) {
            this.appFileName += " " + version.substring(version.indexOf("-")+1);
        }
    }

    private File getJDeployHome() {
        return new File(System.getProperty("user.home") + File.separator + ".jdeploy");
    }

    private File getPackagePath() {
        // Use the new PackagePathResolver which checks architecture-specific paths first,
        // then falls back to legacy paths for backward compatibility
        return PackagePathResolver.resolvePackagePath(packageName, version, source);
    }

    private File getStartMenuPath() {
        return new File(System.getProperty("user.home") + File.separator +
                "AppData" + File.separator +
                "Roaming" + File.separator +
                "Microsoft" + File.separator +
                "Windows" + File.separator +
                "Start Menu");
    }

    private File getStartMenuLink(String suffix) {
        return new File(getStartMenuPath(), new File(appFileName + suffix).getName() + ".lnk");
    }

    private File getProgramsMenuPath() {
        return new File(getStartMenuPath(), "Programs");
    }

    private File getProgramsMenuLink(String suffix) {
        return new File(getProgramsMenuPath(), new File(appFileName + suffix).getName() + ".lnk");
    }

    private File getDesktopLink(String suffix) {
        return new File(
                System.getProperty("user.home") + File.separator +
                        "Desktop" + File.separator + new File(appFileName + suffix).getName() + ".lnk"
        );
    }

    private File getAppDirPath() {
        return new File(getJDeployHome(), "apps" + File.separator + new File(fullyQualifiedPackageName).getName());
    }

    private File getUninstallerPath() {

        String suffix = "";
        if (version != null && version.startsWith("0.0.0-")) {
            suffix = "-" + version.substring(version.indexOf("-")+1);
        }
        return new File(getJDeployHome(),
                "uninstallers" + File.separator +
                        new File(fullyQualifiedPackageName).getName() + File.separator +
                        new File(packageName)+suffix+"-uninstall.exe");
    }

    private Iterable<File> getVersionDirectories() {
        List<File> out = new ArrayList<>();
        if (version == null && getPackagePath().exists())  {
            for (File child : getPackagePath().listFiles()) {
                if (
                        child.isDirectory() &&
                                !child.getName().isEmpty() &&
                                Character.isDigit(child.getName().charAt(0)) &&
                                !child.getName().startsWith("0.0.0-")
                ) {
                    out.add(child);
                }
            }

        } else {
            if (getPackagePath().exists()) {
                out.add(getPackagePath());
            }
        }
        return out;
    }

    private void deletePackage() throws IOException {
        // Delete from all possible locations (architecture-specific and legacy)
        File[] allPossiblePaths = PackagePathResolver.getAllPossiblePackagePaths(packageName, version, source);

        for (File possiblePath : allPossiblePaths) {
            if (version == null && possiblePath.exists()) {
                // Delete all version subdirectories
                for (File child : possiblePath.listFiles()) {
                    if (child.isDirectory() &&
                            !child.getName().isEmpty() &&
                            Character.isDigit(child.getName().charAt(0)) &&
                            !child.getName().startsWith("0.0.0-")) {
                        System.out.println("Deleting version dir: " + child.getAbsolutePath());
                        FileUtils.deleteDirectory(child);
                    }
                }
            } else if (possiblePath.exists()) {
                // Delete specific version directory
                System.out.println("Deleting version dir: " + possiblePath.getAbsolutePath());
                FileUtils.deleteDirectory(possiblePath);
            }
        }
    }

    private void cleanupPackageDir() throws IOException {
        // Clean up both architecture-specific and legacy package directories if empty
        File[] allPossiblePaths = PackagePathResolver.getAllPossiblePackagePaths(packageName, version, source);

        for (File packageDir : allPossiblePaths) {
            if (version != null && packageDir.getParentFile() != null) {
                packageDir = packageDir.getParentFile();
            }

            // Don't delete the root packages/gh-packages directories
            if (packageDir.getName().equals("packages") ||
                    packageDir.getName().startsWith("packages-") ||
                    packageDir.getName().equals("gh-packages") ||
                    packageDir.getName().startsWith("gh-packages-")) {
                continue;
            }

            if (packageDir.exists()) {
                int numVersionDirectoriesRemaining = 0;
                File[] children = packageDir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isDirectory()) {
                            numVersionDirectoriesRemaining++;
                        }
                    }
                }

                if (numVersionDirectoriesRemaining == 0) {
                    System.out.println("Deleting package dir: " + packageDir.getAbsolutePath());
                    FileUtils.deleteDirectory(packageDir);
                }
            }
        }
    }

    private void deleteApp() throws IOException {
        if (getAppDirPath().exists()) {
            System.out.println("Deleting app dir: "+getAppDirPath().getAbsolutePath());
            FileUtils.deleteDirectory(getAppDirPath());
        }
    }

    private void scheduleDeleteUninstaller() throws IOException {
        //cmd.exe /C TIMEOUT 10 && del "{your uninstaller path}"
        Runtime
                .getRuntime()
                .exec(
                        "cmd.exe /C TIMEOUT 5 && rd /s /q \"" +
                                getUninstallerPath().getParentFile().getAbsolutePath() + "\""
                );
    }




    private void removeDesktopAlias() {
        for (String suffix : new String[]{"", InstallWindows.RUN_AS_ADMIN_SUFFIX}) {
            if (getDesktopLink(suffix).exists()) {
                System.out.println("Deleting desktop link: "+getDesktopLink(suffix).getAbsolutePath());
                getDesktopLink(suffix).delete();
            }
        }
    }

    private void removeStartMenuLink() {
        for (String suffix : new String[]{"", InstallWindows.RUN_AS_ADMIN_SUFFIX}) {
            if (getStartMenuLink(suffix).exists()) {
                System.out.println("Deleting start menu link: " + getStartMenuLink(suffix).getAbsolutePath());
                getStartMenuLink(suffix).delete();
            }
        }
    }

    private void removeProgramsMenuLink() {
        for (String suffix : new String[]{"", InstallWindows.RUN_AS_ADMIN_SUFFIX}) {
            if (getProgramsMenuLink(suffix).exists()) {
                System.out.println("Deleting programs menu link: " + getProgramsMenuLink(suffix).getAbsolutePath());
                getProgramsMenuLink(suffix).delete();
            }
        }
    }


    public void uninstall() throws IOException {
        removeDesktopAlias();
        removeProgramsMenuLink();
        removeStartMenuLink();
        deletePackage();
        cleanupPackageDir();
        deleteApp();
        installWindowsRegistry.unregister(null);

        // Delegate CLI command cleanup to WindowsCliCommandInstaller
        File appDir = getAppDirPath();
        try {
            WindowsCliCommandInstaller cliInstaller = new WindowsCliCommandInstaller();
            cliInstaller.uninstallCommands(appDir);
        } catch (Exception ex) {
            System.err.println("Warning: Failed to uninstall CLI commands: " + ex.getMessage());
        }

        File uninstallerJDeployFiles = new File(getUninstallerPath().getParentFile(), ".jdeploy-files");
        if (uninstallerJDeployFiles.exists()) {
            FileUtils.deleteDirectory(uninstallerJDeployFiles);
        }
        scheduleDeleteUninstaller();

    }







}
