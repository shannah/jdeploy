package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.InstallationContext;
import ca.weblite.jdeploy.installer.Main;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.tools.io.FileUtil;
import com.izforge.izpack.util.os.ShellLink;
import net.coobird.thumbnailator.Thumbnails;
import net.sf.image4j.codec.ico.ICOEncoder;
import org.apache.commons.io.FileUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ca.weblite.tools.io.IOUtil.copyResourceToFile;

public class InstallWindows {
    public static final String RUN_AS_ADMIN_SUFFIX = " (Run as admin)";
    public File install(
            InstallationContext context,
            InstallationSettings installationSettings,
            String fullyQualifiedPackageName,
            File tmpBundles
    ) throws Exception {
        return install(context, installationSettings, fullyQualifiedPackageName, tmpBundles, null);
    }

    /**
     * Overload that accepts the NPMPackageVersion so we can enumerate CLI commands for wrapper generation.
     */
    public File install(
            InstallationContext context,
            InstallationSettings installationSettings,
            String fullyQualifiedPackageName,
            File tmpBundles,
            ca.weblite.jdeploy.installer.npm.NPMPackageVersion npmPackageVersion
    ) throws Exception {
        AppInfo appInfo = installationSettings.getAppInfo();
        File tmpExePath = findTmpExeFile(tmpBundles);
        File appXmlFile = context.findAppXml();
        File userHome = new File(System.getProperty("user.home"));
        File jdeployHome = new File(userHome, ".jdeploy");
        File appsDir = new File(jdeployHome, "apps");
        File appDir = new File(appsDir, fullyQualifiedPackageName);
        appDir.mkdirs();
        String nameSuffix = "";

        if (appInfo.getNpmVersion().startsWith("0.0.0-")) {
            nameSuffix = " " +
                    appInfo.getNpmVersion().substring(appInfo.getNpmVersion().indexOf("-") + 1)
                            .trim();
        }

        String exeName = appInfo.getTitle() + nameSuffix + ".exe";
        File exePath = new File(appDir, exeName);
        if (appInfo.isUsePrivateJVM()) {
            File appBinDir = new File(appDir, "bin");
            appBinDir.mkdirs();
            exePath = new File(appBinDir, exeName);
        }

        File cliExePath = null;
        try {
            FileUtil.copy(tmpExePath, exePath);
            exePath.setExecutable(true, false);
            
            // Copy CLI launcher if CLI commands will be installed
            if (installationSettings.isInstallCliCommands()) {
                File tmpCliExePath = findTmpCliExeFile(tmpBundles);
                if (tmpCliExePath != null) {
                    cliExePath = new File(exePath.getParentFile(), 
                        exePath.getName().replace(".exe", CliInstallerConstants.CLI_LAUNCHER_SUFFIX + ".exe"));
                    FileUtil.copy(tmpCliExePath, cliExePath);
                    cliExePath.setExecutable(true, false);
                }
            }
        } catch (IOException e) {
            String logPath = System.getProperty("user.home") + "\\.jdeploy\\log\\jdeploy-installer.log";
            String technicalMessage = "Failed to copy application executable to " + exePath.getAbsolutePath() + ": " + e.getMessage();
            String userMessage = "<html><body style='width: 400px;'>" +
                "<h3>Installation Failed</h3>" +
                "<p>Could not install the application to:<br/><b>" + appDir.getAbsolutePath() + "</b></p>" +
                "<p><b>Possible causes:</b></p>" +
                "<ul>" +
                "<li>You don't have write permission to the directory</li>" +
                "<li>The application is currently running (please close it completely and try again)</li>" +
                "<li>Antivirus software may be blocking the installation</li>" +
                "</ul>" +
                "<p style='margin-top: 12px;'><small>For technical details, check the log file:<br/>" +
                logPath + "</small></p>" +
                "</body></html>";
            throw new ca.weblite.jdeploy.installer.UserLangRuntimeException(technicalMessage, userMessage, e);
        }

        // Copy the icon.png if it is present
        File bundleIcon = new File(appXmlFile.getParentFile(), "icon.png");
        File iconPath = new File(appDir, "icon.png");
        File icoPath =  new File(appDir, "icon.ico");

        if (Files.exists(bundleIcon.toPath())) {
            FileUtil.copy(bundleIcon, iconPath);
        }
        installWindowsLinks(
                appInfo,
                installationSettings,
                exePath,
                appDir,
                appInfo.getTitle() + nameSuffix
        );

        File registryBackupLogs = new File(appDir, "registry-backup-logs");

        if (!registryBackupLogs.exists()) {
            registryBackupLogs.mkdirs();
        }
        int nextLogIndex = 0;
        for (File child : registryBackupLogs.listFiles()) {
            if (child.getName().endsWith(".reg")) {
                String logIndex = child.getName().substring(0, child.getName().lastIndexOf("."));
                try {
                    int logIndexInt = Integer.parseInt(logIndex);
                    if (logIndexInt >= nextLogIndex) {
                        nextLogIndex = logIndexInt+1;
                    }
                } catch (Exception ex) {}
            }
        }
        File registryBackupLog = new File(registryBackupLogs, nextLogIndex+".reg");
        try (FileOutputStream fos = new FileOutputStream(registryBackupLog)) {
            InstallWindowsRegistry registryInstaller = new InstallWindowsRegistry(appInfo, exePath, icoPath, fos);
            registryInstaller.register();

            // Install CLI commands if user requested
            List<ca.weblite.jdeploy.models.CommandSpec> commands = npmPackageVersion != null ? npmPackageVersion.getCommands() : null;
            if (installationSettings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
                ca.weblite.jdeploy.installer.cli.WindowsCliCommandInstaller cliInstaller = 
                    new ca.weblite.jdeploy.installer.cli.WindowsCliCommandInstaller();
                File launcherForCommands = cliExePath != null ? cliExePath : exePath;
                cliInstaller.installCommands(launcherForCommands, commands, installationSettings);
            }

            //Try to copy the uninstaller
            File uninstallerPath = registryInstaller.getUninstallerPath();
            uninstallerPath.getParentFile().mkdirs();
            File installerExePath = new File(System.getProperty("client4j.launcher.path"));
            if (uninstallerPath.exists()) {
                if (!uninstallerPath.canWrite()) {
                    uninstallerPath.setWritable(true, false);
                    try {
                        // Give windows time to update its cache
                        Thread.sleep(1000L);
                    } catch (InterruptedException interruptedException) {
                        // Ignore
                    }
                }
                if (!uninstallerPath.delete()) {
                    throw new IOException("Failed to delete uninstaller: "+uninstallerPath);
                }

                try {
                    // Give Windows time to update its cache
                    Thread.sleep(1000L);
                } catch (InterruptedException interruptedException) {
                    // Ignore
                }
            }
            FileUtils.copyFile(installerExePath, uninstallerPath);
            uninstallerPath.setExecutable(true, false);
            FileUtils.copyDirectory(
                    context.findInstallFilesDir(),
                    new File(
                            uninstallerPath.getParentFile(),
                            context.findInstallFilesDir().getName()
                    )
            );

            return exePath;

        } catch (Exception ex) {
            // restore
            try  {
                InstallWindowsRegistry.rollback(registryBackupLog);
            } catch (Exception rollbackException) {
                throw new RuntimeException(
                        "Failed to roll back registry after failed installation.",
                        rollbackException
                );
            }
            // Since we rolled back the changes, we'll delete the backup log so that it doesn't get rolled back again.
            registryBackupLog.delete();
            ex.printStackTrace(System.err);
            throw new RuntimeException("Failed to update registry.  Rolling back changes.", ex);

        }
    }

    private void installWindowsLink(
            AppInfo appInfo,
            int type,
            File exePath,
            File iconPath,
            String appTitle,
            boolean runAsAdmin
    ) throws Exception {
        ShellLink link = new ShellLink(type, appTitle);
        link.setUserType(ShellLink.CURRENT_USER);
        if (appInfo.getDescription() == null) {
            link.setDescription("Windows application");
        } else {
            link.setDescription(appInfo.getDescription());
        }
        String iconPathString = iconPath.getCanonicalFile().getAbsolutePath();
        link.setIconLocation(iconPathString, 0);
        String exePathString = exePath.getCanonicalFile().getAbsolutePath();
        link.setTargetPath(exePathString);
        if (runAsAdmin) {
            link.setRunAsAdministrator(true);
        }
        link.save();
    }

    private void installWindowsLinks(
            AppInfo appInfo,
            InstallationSettings installationSettings,
            File exePath,
            File appDir,
            String appTitle
    ) throws Exception {
        File pngIconPath = new File(appDir, "icon.png");
        File icoPath = new File(appDir.getCanonicalFile(), "icon.ico");

        if (!Files.exists(pngIconPath.toPath())) {
            copyResourceToFile(Main.class, "icon.png", pngIconPath);
        }
        if (!Files.exists(pngIconPath.toPath())) {
            throw new IOException("Failed to create the .ico file for some reason. "+icoPath);
        }
        convertWindowsIcon(pngIconPath.getCanonicalFile(), icoPath);


        if (installationSettings.isAddToDesktop()) {
            try {
                if (appInfo.isRequireRunAsAdmin()) {
                    installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle, true);
                } else if (appInfo.isAllowRunAsAdmin()) {
                    installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle, false);
                    installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle + RUN_AS_ADMIN_SUFFIX, true);
                } else {
                    installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle, false);
                }

            } catch (Exception ex) {
                throw new RuntimeException("Failed to install desktop shortcut", ex);
            }
        }
        if (installationSettings.isAddToPrograms()) {
            try {
                if (appInfo.isRequireRunAsAdmin()) {
                    installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle, true);
                } else if (appInfo.isAllowRunAsAdmin()) {
                    installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle, false);
                    installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle + RUN_AS_ADMIN_SUFFIX, true);
                } else {
                    installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle, false);
                }

            } catch (Exception ex) {
                throw new RuntimeException("Failed to install program menu shortcut", ex);

            }
        }
        if (installationSettings.isAddToStartMenu()) {
            try {
                if (appInfo.isRequireRunAsAdmin()) {
                    installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle, true);
                } else if (appInfo.isAllowRunAsAdmin()) {
                    installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle, false);
                    installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle + RUN_AS_ADMIN_SUFFIX, true);
                } else {
                    installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle, false);
                }
            } catch (Exception ex) {
                throw new RuntimeException("Failed to install start menu shortcut", ex);
            }
        }
    }

    private void convertWindowsIcon(File srcPng, File destIco) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<Integer> bppList = new ArrayList<>();
        for (int i : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            BufferedImage img = Thumbnails.of(srcPng).size(i, i).asBufferedImage();
            images.add(img);
            bppList.add(32);
            if (i <= 48) {
                images.add(img);
                bppList.add(8);
                images.add(img);
                bppList.add(4);
            }
        }
        int[] bppArray = bppList.stream().mapToInt(i->i).toArray();
        try (FileOutputStream fileOutputStream = new FileOutputStream(destIco)) {
            ICOEncoder.write(images,bppArray, fileOutputStream);
        }
    }

    private File findTmpExeFile(File tmpBundles) {
        File tmpExePath = null;
        for (File exeCandidate : Objects.requireNonNull(new File(tmpBundles, "windows" + getBundlesDirExtension()).listFiles())) {
            if (exeCandidate.getName().endsWith(".exe")) {
                tmpExePath = exeCandidate;
            }
        }
        if (tmpExePath == null) {
            throw new RuntimeException("Failed to find exe file after creation.  Something must have gone wrong in generation process");
        }

        return tmpExePath;
    }

    private File findTmpCliExeFile(File tmpBundles) {
        File windowsDir = new File(tmpBundles, "windows" + getBundlesDirExtension());
        File[] exeFiles = windowsDir.listFiles();
        if (exeFiles == null) {
            return null;
        }
        for (File exeCandidate : exeFiles) {
            if (exeCandidate.getName().contains(CliInstallerConstants.CLI_LAUNCHER_SUFFIX) 
                    && exeCandidate.getName().endsWith(".exe")) {
                return exeCandidate;
            }
        }
        return null;
    }

    private String getBundlesDirExtension() {
        // Use centralized architecture detection utility
        return ArchitectureUtil.getArchitectureSuffix();
    }
}
