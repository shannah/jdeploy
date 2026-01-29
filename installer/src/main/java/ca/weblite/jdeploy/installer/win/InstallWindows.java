package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.InstallationContext;
import ca.weblite.jdeploy.installer.Main;
import ca.weblite.jdeploy.installer.cli.CollisionHandler;
import ca.weblite.jdeploy.installer.cli.UIAwareCollisionHandler;
import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorService;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorServiceFactory;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder;
import ca.weblite.jdeploy.installer.uninstall.UninstallManifestWriter;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.installer.util.WindowsAppDirResolver;
import ca.weblite.tools.io.FileUtil;
import com.izforge.izpack.util.os.ShellLink;
import com.joshondesign.appbundler.win.WindowsPESubsystemModifier;
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
            File tmpBundles,
            CollisionHandler collisionHandler
    ) throws Exception {
        return install(context, installationSettings, fullyQualifiedPackageName, tmpBundles, null, collisionHandler);
    }

    /**
     * Overload that accepts the NPMPackageVersion so we can enumerate CLI commands for wrapper generation.
     */
    public File install(
            InstallationContext context,
            InstallationSettings installationSettings,
            String fullyQualifiedPackageName,
            File tmpBundles,
            ca.weblite.jdeploy.installer.npm.NPMPackageVersion npmPackageVersion,
            CollisionHandler collisionHandler
    ) throws Exception {
        // Create installation logger
        InstallationLogger logger = null;
        try {
            logger = new InstallationLogger(fullyQualifiedPackageName, InstallationLogger.OperationType.INSTALL);
        } catch (IOException e) {
            System.err.println("Warning: Failed to create installation logger: " + e.getMessage());
        }

        try {
            return doInstall(context, installationSettings, fullyQualifiedPackageName, tmpBundles, npmPackageVersion, collisionHandler, logger);
        } finally {
            if (logger != null) {
                logger.close();
            }
        }
    }

    private File doInstall(
            InstallationContext context,
            InstallationSettings installationSettings,
            String fullyQualifiedPackageName,
            File tmpBundles,
            ca.weblite.jdeploy.installer.npm.NPMPackageVersion npmPackageVersion,
            CollisionHandler collisionHandler,
            InstallationLogger logger
    ) throws Exception {
        AppInfo appInfo = installationSettings.getAppInfo();
        File tmpExePath = findTmpExeFile(tmpBundles);
        File appXmlFile = context.findAppXml();
        File appDir = WindowsAppDirResolver.resolveAppDir(
                installationSettings.getWinAppDir(), fullyQualifiedPackageName);

        if (logger != null) {
            logger.logInfo("Starting installation of " + appInfo.getTitle() + " version " + appInfo.getNpmVersion());
            logger.logInfo("Package: " + appInfo.getNpmPackage());
            logger.logInfo("Source: " + (appInfo.getNpmSource() != null ? appInfo.getNpmSource() : "NPM"));
            logger.logSection("Creating Application Directory");
        }

        boolean appDirCreated = !appDir.exists();
        appDir.mkdirs();
        if (logger != null && appDirCreated) {
            logger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED, appDir.getAbsolutePath());
        }

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
            if (logger != null) {
                logger.logSection("Installing Application Executable");
            }
            boolean exeExisted = exePath.exists();
            FileUtil.copy(tmpExePath, exePath);
            exePath.setExecutable(true, false);
            if (logger != null) {
                logger.logFileOperation(
                    exeExisted ? InstallationLogger.FileOperation.OVERWRITTEN : InstallationLogger.FileOperation.CREATED,
                    exePath.getAbsolutePath(),
                    "Main application executable"
                );
            }

            // Copy CLI launcher if CLI commands will be installed
            if (installationSettings.isInstallCliCommands()) {
                cliExePath = new File(exePath.getParentFile(),
                        exePath.getName().replace(".exe", CliInstallerConstants.CLI_LAUNCHER_SUFFIX + ".exe"));
                boolean cliExeExisted = cliExePath.exists();
                WindowsPESubsystemModifier.copyAndModifySubsystem(exePath, cliExePath);
                cliExePath.setExecutable(true, false);
                if (logger != null) {
                    logger.logFileOperation(
                        cliExeExisted ? InstallationLogger.FileOperation.OVERWRITTEN : InstallationLogger.FileOperation.CREATED,
                        cliExePath.getAbsolutePath(),
                        "CLI launcher executable"
                    );
                }
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.logError("Failed to copy application executable", e);
            }
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
        if (logger != null) {
            logger.logSection("Installing Application Icons");
        }
        File bundleIcon = new File(appXmlFile.getParentFile(), "icon.png");
        File iconPath = new File(appDir, "icon.png");
        File icoPath =  new File(appDir, "icon.ico");

        if (Files.exists(bundleIcon.toPath())) {
            boolean iconExisted = iconPath.exists();
            FileUtil.copy(bundleIcon, iconPath);
            if (logger != null) {
                logger.logFileOperation(
                    iconExisted ? InstallationLogger.FileOperation.OVERWRITTEN : InstallationLogger.FileOperation.CREATED,
                    iconPath.getAbsolutePath(),
                    "Application icon (PNG)"
                );
            }
        }

        List<File> shortcutFiles = installWindowsLinks(
                appInfo,
                installationSettings,
                exePath,
                appDir,
                appInfo.getTitle() + nameSuffix,
                logger
        );

        if (logger != null) {
            logger.logSection("Registry Operations");
        }

        File registryBackupLogs = new File(appDir, "registry-backup-logs");

        if (!registryBackupLogs.exists()) {
            registryBackupLogs.mkdirs();
            if (logger != null) {
                logger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED, registryBackupLogs.getAbsolutePath());
            }
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
            InstallWindowsRegistry registryInstaller = new InstallWindowsRegistry(appInfo, exePath, icoPath, fos, logger);
            registryInstaller.register();

            // Install CLI commands if user requested
            List<ca.weblite.jdeploy.models.CommandSpec> commands = npmPackageVersion != null ? npmPackageVersion.getCommands() : null;
            List<File> cliWrapperFiles = null;
            if (installationSettings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
                if (logger != null) {
                    logger.logSection("Installing CLI Commands");
                    logger.logInfo("Installing " + commands.size() + " CLI command(s)");
                }
                ca.weblite.jdeploy.installer.cli.WindowsCliCommandInstaller cliInstaller =
                    new ca.weblite.jdeploy.installer.cli.WindowsCliCommandInstaller();
                cliInstaller.setCollisionHandler(collisionHandler);
                cliInstaller.setInstallationLogger(logger);
                cliInstaller.setServiceDescriptorService(ServiceDescriptorServiceFactory.createDefault());
                File launcherForCommands = cliExePath != null ? cliExePath : exePath;
                cliWrapperFiles = cliInstaller.installCommands(launcherForCommands, commands, installationSettings);
                if (logger != null) {
                    logger.logInfo("CLI commands installation complete: " + (cliWrapperFiles != null ? cliWrapperFiles.size() : 0) + " wrapper(s) created");
                }
            }

            //Try to copy the uninstaller
            if (logger != null) {
                logger.logSection("Installing Uninstaller");
            }
            File uninstallerPath = registryInstaller.getUninstallerPath();
            boolean uninstallerDirCreated = !uninstallerPath.getParentFile().exists();
            uninstallerPath.getParentFile().mkdirs();
            if (logger != null && uninstallerDirCreated) {
                logger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED, uninstallerPath.getParentFile().getAbsolutePath());
            }
            File installerExePath = new File(System.getProperty("client4j.launcher.path"));
            boolean uninstallerExisted = uninstallerPath.exists();
            if (uninstallerExisted) {
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
                    if (logger != null) {
                        logger.logFileOperation(InstallationLogger.FileOperation.FAILED, uninstallerPath.getAbsolutePath(), "Failed to delete existing uninstaller");
                    }
                    throw new IOException("Failed to delete uninstaller: "+uninstallerPath);
                }
                if (logger != null) {
                    logger.logFileOperation(InstallationLogger.FileOperation.DELETED, uninstallerPath.getAbsolutePath(), "Existing uninstaller");
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
            if (logger != null) {
                logger.logFileOperation(InstallationLogger.FileOperation.CREATED, uninstallerPath.getAbsolutePath(), "Uninstaller executable");
            }
            File jdeployFilesDestDir = new File(uninstallerPath.getParentFile(), context.findInstallFilesDir().getName());
            FileUtils.copyDirectory(context.findInstallFilesDir(), jdeployFilesDestDir);
            if (logger != null) {
                logger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED, jdeployFilesDestDir.getAbsolutePath(), "jDeploy files for uninstaller");
            }

            // Build and write uninstall manifest
            try {
                UninstallManifestBuilder manifestBuilder = new UninstallManifestBuilder();
                
                // Set package info
                String arch = ArchitectureUtil.getArchitecture();
                manifestBuilder.withPackageInfo(
                    appInfo.getNpmPackage(),
                    appInfo.getNpmSource(),
                    appInfo.getNpmVersion(),
                    arch
                );
                manifestBuilder.withWinAppDir(installationSettings.getWinAppDir());
                manifestBuilder.withInstallerVersion(appInfo.getLauncherVersion() != null ? appInfo.getLauncherVersion() : "1.0");
                
                // Add executable
                manifestBuilder.addFile(exePath.getAbsolutePath(), UninstallManifest.FileType.BINARY, "Main application executable");
                
                // Add CLI executable if created
                if (cliExePath != null && cliExePath.exists()) {
                    manifestBuilder.addFile(cliExePath.getAbsolutePath(), UninstallManifest.FileType.BINARY, "CLI launcher executable");
                }
                
                // Add icon files
                if (iconPath.exists()) {
                    manifestBuilder.addFile(iconPath.getAbsolutePath(), UninstallManifest.FileType.ICON, "Application icon (PNG)");
                }
                if (icoPath.exists()) {
                    manifestBuilder.addFile(icoPath.getAbsolutePath(), UninstallManifest.FileType.ICON, "Application icon (ICO)");
                }
                
                // Add shortcuts (shortcutFiles is the List<File> returned from installWindowsLinks)
                for (File shortcut : shortcutFiles) {
                    manifestBuilder.addFile(shortcut.getAbsolutePath(), UninstallManifest.FileType.LINK, "Windows shortcut");
                }
                
                // Add CLI command wrappers (cliWrapperFiles from WindowsCliCommandInstaller.installCommands())
                if (cliWrapperFiles != null) {
                    for (File wrapper : cliWrapperFiles) {
                        manifestBuilder.addFile(wrapper.getAbsolutePath(), UninstallManifest.FileType.SCRIPT, "CLI command wrapper");
                    }
                }
                
                // Add registry keys from InstallWindowsRegistry
                // The registry installer creates keys under HKEY_CURRENT_USER
                for (String registryPath : registryInstaller.getCreatedRegistryPaths()) {
                    manifestBuilder.addCreatedRegistryKey(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, registryPath);
                }

                // Add the RegisteredApplications value as a modified value (not a created key)
                // because Software\RegisteredApplications is a shared system key - we only add a value to it
                manifestBuilder.addModifiedRegistryValue(
                        UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
                        registryInstaller.getRegisteredApplicationsKeyPath(),
                        registryInstaller.getRegisteredApplicationsValueName(),
                        null, // No previous value - the value did not exist before
                        UninstallManifest.RegistryValueType.REG_SZ,
                        "Registered application entry"
                );

                // Add PATH modifications if CLI commands were installed
                if (installationSettings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
                    File perAppBinDir = CliCommandBinDirResolver.getPerAppBinDir(
                        installationSettings.getPackageName(),
                        installationSettings.getSource()
                    );
                    manifestBuilder.addWindowsPathEntry(perAppBinDir.getAbsolutePath(), "CLI commands bin directory");
                }
                
                // Add app directory
                manifestBuilder.addDirectory(appDir.getAbsolutePath(), UninstallManifest.CleanupStrategy.ALWAYS, "Application directory");
                
                // Write manifest
                UninstallManifestWriter writer = new UninstallManifestWriter(true); // skip schema validation
                UninstallManifest manifest = manifestBuilder.build();
                writer.write(manifest);
                
            } catch (Exception e) {
                // Log but don't fail installation if manifest writing fails
                System.err.println("Warning: Failed to write uninstall manifest: " + e.getMessage());
            }

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

    private File installWindowsLink(
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
        
        // Compute and return the shortcut file path based on type
        String userHome = System.getProperty("user.home");
        String appData = System.getenv("APPDATA");
        
        switch (type) {
            case ShellLink.DESKTOP:
                return new File(userHome, "Desktop" + File.separator + appTitle + ".lnk");
            case ShellLink.PROGRAM_MENU:
                return new File(appData, "Microsoft" + File.separator + "Windows" + File.separator + 
                               "Start Menu" + File.separator + "Programs" + File.separator + appTitle + ".lnk");
            case ShellLink.START_MENU:
                return new File(appData, "Microsoft" + File.separator + "Windows" + File.separator + 
                               "Start Menu" + File.separator + appTitle + ".lnk");
            default:
                return null;
        }
    }

    private List<File> installWindowsLinks(
            AppInfo appInfo,
            InstallationSettings installationSettings,
            File exePath,
            File appDir,
            String appTitle,
            InstallationLogger logger
    ) throws Exception {
        if (logger != null) {
            logger.logSection("Creating Windows Shortcuts");
        }
        List<File> shortcutFiles = new ArrayList<>();
        File pngIconPath = new File(appDir, "icon.png");
        File icoPath = new File(appDir.getCanonicalFile(), "icon.ico");

        if (!Files.exists(pngIconPath.toPath())) {
            copyResourceToFile(Main.class, "icon.png", pngIconPath);
            if (logger != null) {
                logger.logFileOperation(InstallationLogger.FileOperation.CREATED, pngIconPath.getAbsolutePath(), "Default icon (PNG)");
            }
        }
        if (!Files.exists(pngIconPath.toPath())) {
            throw new IOException("Failed to create the .ico file for some reason. "+icoPath);
        }
        boolean icoExisted = icoPath.exists();
        convertWindowsIcon(pngIconPath.getCanonicalFile(), icoPath);
        if (logger != null) {
            logger.logFileOperation(
                icoExisted ? InstallationLogger.FileOperation.OVERWRITTEN : InstallationLogger.FileOperation.CREATED,
                icoPath.getAbsolutePath(),
                "Windows icon (ICO)"
            );
        }


        if (installationSettings.isAddToDesktop()) {
            try {
                if (appInfo.isRequireRunAsAdmin()) {
                    File shortcut = installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle, true);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                } else if (appInfo.isAllowRunAsAdmin()) {
                    File shortcut = installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle, false);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                    File shortcutAdmin = installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle + RUN_AS_ADMIN_SUFFIX, true);
                    if (shortcutAdmin != null) {
                        shortcutFiles.add(shortcutAdmin);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcutAdmin.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                } else {
                    File shortcut = installWindowsLink(appInfo, ShellLink.DESKTOP, exePath, icoPath, appTitle, false);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                }

            } catch (Exception ex) {
                if (logger != null) logger.logError("Failed to install desktop shortcut", ex);
                throw new RuntimeException("Failed to install desktop shortcut", ex);
            }
        }
        if (installationSettings.isAddToPrograms()) {
            try {
                if (appInfo.isRequireRunAsAdmin()) {
                    File shortcut = installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle, true);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                } else if (appInfo.isAllowRunAsAdmin()) {
                    File shortcut = installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle, false);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                    File shortcutAdmin = installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle + RUN_AS_ADMIN_SUFFIX, true);
                    if (shortcutAdmin != null) {
                        shortcutFiles.add(shortcutAdmin);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcutAdmin.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                } else {
                    File shortcut = installWindowsLink(appInfo, ShellLink.PROGRAM_MENU, exePath, icoPath, appTitle, false);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                }

            } catch (Exception ex) {
                if (logger != null) logger.logError("Failed to install program menu shortcut", ex);
                throw new RuntimeException("Failed to install program menu shortcut", ex);

            }
        }
        if (installationSettings.isAddToStartMenu()) {
            try {
                if (appInfo.isRequireRunAsAdmin()) {
                    File shortcut = installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle, true);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                } else if (appInfo.isAllowRunAsAdmin()) {
                    File shortcut = installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle, false);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                    File shortcutAdmin = installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle + RUN_AS_ADMIN_SUFFIX, true);
                    if (shortcutAdmin != null) {
                        shortcutFiles.add(shortcutAdmin);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcutAdmin.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                } else {
                    File shortcut = installWindowsLink(appInfo, ShellLink.START_MENU, exePath, icoPath, appTitle, false);
                    if (shortcut != null) {
                        shortcutFiles.add(shortcut);
                        if (logger != null) logger.logShortcut(InstallationLogger.FileOperation.CREATED, shortcut.getAbsolutePath(), exePath.getAbsolutePath());
                    }
                }
            } catch (Exception ex) {
                if (logger != null) logger.logError("Failed to install start menu shortcut", ex);
                throw new RuntimeException("Failed to install start menu shortcut", ex);
            }
        }

        return shortcutFiles;
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
