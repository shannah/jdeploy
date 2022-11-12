package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.linux.MimeTypeHelper;
import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.npm.NPMPackage;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.installer.npm.NPMRegistry;
import ca.weblite.jdeploy.installer.util.JarClassLoader;
import ca.weblite.jdeploy.installer.util.ResourceUtil;
import ca.weblite.jdeploy.installer.views.DefaultUIFactory;
import ca.weblite.jdeploy.installer.views.InstallationForm;
import ca.weblite.jdeploy.installer.views.UIFactory;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.installer.win.UninstallWindows;

import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import ca.weblite.tools.io.*;
import ca.weblite.tools.platform.Platform;
import com.izforge.izpack.util.os.ShellLink;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;



import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.sf.image4j.codec.ico.ICOEncoder;
import static ca.weblite.tools.io.IOUtil.copyResourceToFile;

public class Main implements Runnable, Constants {
    public static final String JDEPLOY_REGISTRY = DefaultInstallationContext.JDEPLOY_REGISTRY;
    private final InstallationContext installationContext = new DefaultInstallationContext();
    private final InstallationSettings installationSettings = new InstallationSettings();
    private UIFactory uiFactory = new DefaultUIFactory();
    private InstallationForm installationForm;

    private Document getAppXMLDocument() throws IOException {
        return installationContext.getAppXMLDocument();
    }

    private NPMPackageVersion npmPackageVersion() {
        return installationSettings.getNpmPackageVersion();
    }

    private AppInfo appInfo() {
        return installationSettings.getAppInfo();
    }

    private void appInfo(AppInfo appInfo) {
        installationSettings.setAppInfo(appInfo);
    }

    private void loadTheme(File themeJar) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        JarClassLoader jarClassLoader = new JarClassLoader(themeJar);

        URL jdeployInfoUrl = jarClassLoader.getResource("jdeploy-info.json");
        if (jdeployInfoUrl == null) throw new IOException("Provided theme "+themeJar+" is missing the jdeploy-info.json file");

        JSONObject json = new JSONObject(IOUtil.readToString(jarClassLoader.getResourceAsStream("jdeploy-info.json")));
        if (!json.has("installerTheme") || !json.getJSONObject("installerTheme").has("factory")) {
            throw new IOException("jdeploy-info is missing the installerTheme/factory property");
        }
        JSONObject installerThemeJson = json.getJSONObject("installerTheme");

        String factoryClassName = installerThemeJson.getString("factory");
        Class factoryClazz = jarClassLoader.loadClass(factoryClassName);
        if (!UIFactory.class.isAssignableFrom(factoryClazz)) {
            throw new IOException("The specified factory class "+factoryClassName+" does not implement UIFactory while loading theme "+themeJar+".  Failed to load theme.");
        }
        uiFactory = (UIFactory)factoryClazz.newInstance();
    }

    private void loadThemeByName(String themeName) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        String resourcePath = "/themes/"+themeName+".jar";
        URL resourceURL = getClass().getResource(resourcePath);
        if (resourceURL != null) {
            loadTheme(ResourceUtil.extractZipResourceWithExecutableJarToTempDirectory(getClass(), resourcePath));
        } else {
            throw new IOException("Theme named "+themeName+" not found.  No resource at "+resourcePath);
        }
    }



    private void loadNPMPackageInfo() throws IOException {
        //System.out.println("Loading NPMPackageInfo");
        if (installationSettings.getNpmPackageVersion() == null) {
            if (appInfo() == null) {
                throw new IllegalStateException("App Info must be loaded before loading the package info");
            }
            NPMPackage pkg = new NPMRegistry().loadPackage(appInfo().getNpmPackage());
            if (pkg == null) {
                throw new IOException("Cannot find NPMPackage named "+appInfo().getNpmPackage());
            }
            installationSettings.setNpmPackageVersion(pkg.getLatestVersion(installationSettings.isPrerelease(), appInfo().getNpmVersion()));
            if (installationSettings.getNpmPackageVersion() == null) {
                throw new IOException("Cannot find version "+appInfo().getNpmVersion()+" for package "+appInfo().getNpmPackage());
            }

            String installerTheme = System.getProperty("jdeploy.installerTheme", installationSettings.getNpmPackageVersion().getInstallerTheme());
            System.out.println("Installer theme is "+installerTheme);
            if (installerTheme != null) {
                try {
                    loadThemeByName(installerTheme);
                } catch (Exception ex) {
                    System.err.println("Failed to load installer theme "+installerTheme+".  Falling back to default theme.");
                    ex.printStackTrace(System.err);
                }
            }

            if (appInfo().getDescription() == null || appInfo().getDescription().isEmpty()) {
                appInfo().setDescription(npmPackageVersion().getDescription());
            }

            if (appInfo().getDescription() == null || appInfo().getDescription().isEmpty()) {
                appInfo().setDescription("Desktop application");
            }

            for (DocumentTypeAssociation documentTypeAssociation : npmPackageVersion().getDocumentTypeAssociations()) {
                appInfo().addDocumentMimetype(documentTypeAssociation.getExtension(), documentTypeAssociation.getMimetype());
                if (documentTypeAssociation.getIconPath() != null) {
                    appInfo().addDocumentTypeIcon(documentTypeAssociation.getExtension(), documentTypeAssociation.getIconPath());
                } else {
                    File iconPath = new File(findAppXmlFile().getParentFile(), "icon."+documentTypeAssociation.getExtension()+".png");
                    if (iconPath.exists()) {
                        appInfo().addDocumentTypeIcon(documentTypeAssociation.getExtension(), iconPath.getAbsolutePath());
                    }
                }
                if (documentTypeAssociation.isEditor()) {
                    appInfo().setDocumentTypeEditor(documentTypeAssociation.getExtension());
                }
            }
            //System.out.println("Checking npm package for URL schemes: ");
            for (String scheme : npmPackageVersion().getUrlSchemes()) {
                //System.out.println("Found scheme "+scheme);
                appInfo().addUrlScheme(scheme);
            }

            // Update labels for the combobox with nice examples to show exactly which versions will be auto-updated
            // to with the given setting.
            AutoUpdateSettings.MinorOnly.setLabel(
                    AutoUpdateSettings.MinorOnly.toString() +
                            " [" +
                            createUserReadableSemVerForVersion(npmPackageVersion().getVersion(), AutoUpdateSettings.MinorOnly) +
                            "]");
            AutoUpdateSettings.PatchesOnly.setLabel(
                    AutoUpdateSettings.PatchesOnly.toString() +
                            " [" +
                            createUserReadableSemVerForVersion(npmPackageVersion().getVersion(), AutoUpdateSettings.PatchesOnly) +
                            "]"
                    );
        }
    }

    private static class InvalidAppXMLFormatException extends IOException {
        InvalidAppXMLFormatException(String message) {
            super("The app.xml file is invalid. "+message);
        }
    }

    private void loadAppInfo() throws IOException {
        File appXml = findAppXmlFile();
        if (appXml == null) {
            throw new IOException("Cannot load app info because the app.xml file could not be found");
        }
        Document doc = getAppXMLDocument();
        if (doc == null) {
            throw new IOException("There was a problem parsing app.xml.");
        }
        Element root = doc.getDocumentElement();
        appInfo(new AppInfo());
        appInfo().setAppURL(appXml.toURI().toURL());
        appInfo().setTitle(ifEmpty(root.getAttribute("title"), root.getAttribute("package"), null));
        appInfo().setNpmPackage(ifEmpty(root.getAttribute("package"), null));
        appInfo().setFork(false);

        // First we set the version in appInfo according to the app.xml file
        appInfo().setNpmVersion(ifEmpty(root.getAttribute("version"), "latest"));
        // Next we use that version to load the package info from the NPM registry.
        loadNPMPackageInfo();
        appInfo().setMacAppBundleId(ifEmpty(root.getAttribute("macAppBundleId"), "ca.weblite.jdeploy.apps."+appInfo().getNpmPackage()));

        if (appInfo().getNpmPackage() == null) {
            throw new InvalidAppXMLFormatException("Missing package attribute");
        }
    }

    private static String createUserReadableSemVerForVersion(String version, AutoUpdateSettings updateSettings) {
        switch (updateSettings) {
            case Stable:
                return "*";
            case MinorOnly: {
                StringBuilder sb = new StringBuilder();

                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");
                if (pos < 0) {
                    pos = version.length();
                }

                sb.append(version.substring(0, pos)).append(".*");
                return sb.toString();
            }
            case PatchesOnly: {
                StringBuilder sb = new StringBuilder();

                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");

                if (pos < 0) {
                    version += ".0.0";
                    pos = version.indexOf(".");
                }
                int p1 = pos;
                pos = version.indexOf(".", p1 + 1);
                if (pos < 0) {
                    version += ".0";
                    pos = version.indexOf(".", p1 + 1);
                }
                sb.append(version.substring(0, pos)).append(".*");
                return sb.toString();
            }
            case Off:
            default:
                return version;
        }
    }

    private static String createSemVerForVersion(String version, AutoUpdateSettings updateSettings) {
        switch (updateSettings) {
            case Stable:
                return "latest";
            case MinorOnly: {
                StringBuilder sb = new StringBuilder();
                sb.append("^");
                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");
                if (pos < 0) {
                    pos = version.length();
                }

                sb.append(version.substring(0, pos));
                return sb.toString();
            }
            case PatchesOnly: {
                StringBuilder sb = new StringBuilder();
                sb.append("~");
                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");

                if (pos < 0) {
                    version += ".0.0";
                    pos = version.indexOf(".");
                }
                int p1 = pos;
                pos = version.indexOf(".", p1 + 1);
                if (pos < 0) {
                    version += ".0";
                    pos = version.indexOf(".", p1 + 1);
                }
                sb.append(version.substring(0, pos));
                return sb.toString();
            }
            case Off:
            default:
                return version;
        }

    }

    private static String ifEmpty(String... value) {
        for (String s : value) {
            if (s != null && !s.isEmpty()) return s;
        }
        return null;

    }

    private static boolean uninstall;

    public static void main(String[] args) {

        if (args.length == 1 && args[0].equals("uninstall")) {
            uninstall = true;
        }

        File logFile = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "log" + File.separator + "jdeploy-installer.log");
        logFile.getParentFile().mkdirs();
        try {
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(new FileOutputStream(logFile)));
            originalOut.println("Redirecting output to "+logFile);
            System.setErr(System.out);
        } catch (IOException ex) {
            System.err.println("Failed to redirect output to "+logFile);
            ex.printStackTrace(System.err);
        }
        Main main = new Main();
        main.run();
    }

    private File findInstallFilesDir() {
        return installationContext.findInstallFilesDir();
    }

    private File findAppXmlFile() {
        return installationContext.findAppXml();

    }

    @Override
    public void run() {
        try {
            run0();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            invokeLater(()->{
                uiFactory.showModalErrorDialog(null, ex.getMessage(), "Installation failed.");
                System.exit(1);
            });
        }
    }

    private void invokeLater(Runnable r) {
        uiFactory.getUI().run(r);
    }

    private void onInstallClicked(InstallationFormEvent evt) {
        evt.getInstallationForm().showTrustConfirmationDialog();
    }

    private void onInstallCompleteOpenApp(InstallationFormEvent evt) {
        if (Platform.getSystemPlatform().isMac()) {
            try {
                Runtime.getRuntime().exec(new String[]{"open", installedApp.getAbsolutePath()});
                java.util.Timer timer = new java.util.Timer();
                TimerTask tt = new TimerTask() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                };
                timer.schedule(tt, 2000);
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                invokeLater(()-> uiFactory.showModalErrorDialog(evt.getInstallationForm(), "Failed to open app: "+ex.getMessage(), "Error"));
            }
        } else {
            try {
                Runtime.getRuntime().exec(new String[]{installedApp.getAbsolutePath()});
                java.util.Timer timer = new java.util.Timer();
                TimerTask tt = new TimerTask() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                };
                timer.schedule(tt, 2000);
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                invokeLater(()-> uiFactory.showModalErrorDialog(evt.getInstallationForm(), "Failed to open app: "+ex.getMessage(), "Error"));
            }
        }
    }

    private void onInstallCompleteRevealApp(InstallationFormEvent evt) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(installedApp.getParentFile());
                java.util.Timer timer = new java.util.Timer();
                TimerTask tt = new TimerTask() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                };
                timer.schedule(tt, 2000);
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                invokeLater(()-> uiFactory.showModalErrorDialog(evt.getInstallationForm(), "Failed to open directory: "+ex.getMessage(), "Error"));
            }
        } else {
            invokeLater(()->{
                uiFactory.showModalErrorDialog(evt.getInstallationForm(), "Reveal in explorer is not supported on this platform.", "Not supported");
            });
        }
    }

    private void onInstallCompleteCloseInstaller(InstallationFormEvent evt) {
        java.util.Timer timer = new java.util.Timer();
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                System.exit(0);
            }
        };
        timer.schedule(tt, 2000);
    }

    private void buildUI() {
        installationContext.applyContext(installationSettings);
        InstallationForm view = uiFactory.createInstallationForm(installationSettings);
        view.setEventDispatcher(new InstallationFormEventDispatcher(view));
        this.installationForm = view;
        view.getEventDispatcher().addEventListener(evt->{
            switch (evt.getType()) {
                case InstallClicked:
                    onInstallClicked(evt);
                    break;
                case InstallCompleteOpenApp:
                    onInstallCompleteOpenApp(evt);
                    break;
                case InstallCompleteRevealApp:
                    onInstallCompleteRevealApp(evt);
                    break;
                case InstallCompleteCloseInstaller:
                    onInstallCompleteCloseInstaller(evt);
                    break;
                case VisitSoftwareHomepage:
                    onVisitSoftwareHomepage(evt);
                    break;
                case ProceedWithInstallation:
                    onProceedWithInstallation(evt);
                    break;
                case CancelInstallation:
                    onCancelInstallation(evt);
                    break;
            }
        });
    }

    private void onCancelInstallation(InstallationFormEvent evt) {
        System.exit(0);
    }

    private void onProceedWithInstallation(InstallationFormEvent evt) {
        evt.setConsumed(true);
        evt.getInstallationForm().setInProgress(true, "Installing.  Please wait...");
        new Thread(()->{
            try {
                install();
                invokeLater(()->evt.getInstallationForm().setInProgress(false, ""));
                invokeLater(()-> evt.getInstallationForm().showInstallationCompleteDialog());
            } catch (Exception ex) {
                invokeLater(()->evt.getInstallationForm().setInProgress(false, ""));
                ex.printStackTrace(System.err);
                invokeLater(()-> uiFactory.showModalErrorDialog(evt.getInstallationForm(), "Installation failed. "+ex.getMessage(), "Failed"));
            }
        }).start();
    }

    private void onVisitSoftwareHomepage(InstallationFormEvent evt) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(installationSettings.getWebsiteURL().toURI());
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                uiFactory.getUI().run(()->{
                    uiFactory.showModalErrorDialog(evt.getInstallationForm(), "Failed to open webpage.", "Error");
                });
            }
        }
    }

    private void run0() throws Exception {
        loadAppInfo();
        if (uninstall && Platform.getSystemPlatform().isWindows()) {
            System.out.println("Running Windows uninstall...");
            InstallWindowsRegistry installer = new InstallWindowsRegistry(appInfo(), null, null, null);
            UninstallWindows uninstallWindows = new UninstallWindows(appInfo().getNpmPackage(), appInfo().getVersion(), appInfo().getTitle(), installer);
            uninstallWindows.uninstall();
            System.out.println("Uninstall complete");
            return;
        }
        invokeLater(()->{
            buildUI();
            installationForm.showInstallationForm();
        });

    }

    private File installedApp;

    private void install() throws Exception {

        // Based on the user's settings, let's update the version in the appInfo
        // to correspond with the auto-update settings.
        appInfo().setNpmVersion(createSemVerForVersion(npmPackageVersion().getVersion(), installationSettings.getAutoUpdate()));
        appInfo().setNpmAllowPrerelease(installationSettings.isPrerelease());

        File tmpDest = File.createTempFile("jdeploy-installer-"+appInfo().getNpmPackage(), "");
        tmpDest.delete();
        tmpDest.mkdir();
        File tmpBundles = new File(tmpDest, "bundles");
        File tmpReleases = new File(tmpDest, "releases");
        tmpBundles.mkdir();
        tmpReleases.mkdir();
        String target;
        if (Platform.getSystemPlatform().isMac()) {
            target = "mac";
            if (System.getProperty("os.arch").equals("aarch64")) {
                target += "-arm64";
            } else {
                target += "-x64";
            }
        } else if (Platform.getSystemPlatform().isWindows()) {
            target = "win";
        } else if (Platform.getSystemPlatform().isLinux()) {
            target = "linux";
        } else {
            throw new RuntimeException("Installation failed.  Your platform is not currently supported.");
        }
        {
            File icon = new File(findAppXmlFile().getParentFile(), "icon.png");
            if (!icon.exists()) {
                copyResourceToFile(Main.class, "icon.png", icon);
            }
        }

        Bundler.runit(appInfo(), findAppXmlFile().toURI().toURL().toString(), target, tmpBundles.getAbsolutePath(), tmpReleases.getAbsolutePath());

        if (Platform.getSystemPlatform().isWindows()) {
            File tmpExePath = null;
            for (File exeCandidate : new File(tmpBundles, "windows").listFiles()) {
                if (exeCandidate.getName().endsWith(".exe")) {
                    tmpExePath = exeCandidate;
                }
            }
            if (tmpExePath == null) {
                throw new RuntimeException("Failed to find exe file after creation.  Something must have gone wrong in generation process");
            }
            File userHome = new File(System.getProperty("user.home"));
            File jdeployHome = new File(userHome, ".jdeploy");
            File appsDir = new File(jdeployHome, "apps");
            File appDir = new File(appsDir, appInfo().getNpmPackage());
            appDir.mkdirs();
            File exePath = new File(appDir, tmpExePath.getName());
            FileUtil.copy(tmpExePath, exePath);
            exePath.setExecutable(true, false);

            // Copy the icon.png if it is present
            File bundleIcon = new File(findAppXmlFile().getParentFile(), "icon.png");
            File iconPath = new File(exePath.getParentFile(), "icon.png");
            File icoPath =  new File(exePath.getParentFile(), "icon.ico");

            if (Files.exists(bundleIcon.toPath())) {
                FileUtil.copy(bundleIcon, iconPath);
            }
            installWindowsLinks(exePath);

            File registryBackupLogs = new File(exePath.getParentFile(), "registry-backup-logs");

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
                InstallWindowsRegistry registryInstaller = new InstallWindowsRegistry(appInfo(), exePath, icoPath, fos);
                registryInstaller.register();

                //Try to copy the uninstaller
                File uninstallerPath = registryInstaller.getUninstallerPath();
                uninstallerPath.getParentFile().mkdirs();
                File installerExePath = new File(System.getProperty("client4j.launcher.path"));
                FileUtils.copyFile(installerExePath, uninstallerPath);
                uninstallerPath.setExecutable(true, false);
                FileUtils.copyDirectory(findInstallFilesDir(), new File(uninstallerPath.getParentFile(), findInstallFilesDir().getName()));


            } catch (Exception ex) {
                // restore
                try  {
                    InstallWindowsRegistry.rollback(registryBackupLog);
                } catch (Exception rollbackException) {
                    throw new RuntimeException("Failed to roll back registry after failed installation.", rollbackException);
                }
                // Since we rolled back the changes, we'll delete the backup log so that it doesn't get rolled back again.
                registryBackupLog.delete();
                ex.printStackTrace(System.err);
                throw new RuntimeException("Failed to update registry.  Rolling back changes.", ex);

            }


            installedApp = exePath;

        } else if (Platform.getSystemPlatform().isMac()) {
            File jdeployAppsDir = new File(System.getProperty("user.home") + File.separator + "Applications" + File.separator + "jDeploy Apps");
            if (!jdeployAppsDir.exists()) {
                jdeployAppsDir.mkdirs();
            }


            File installAppPath = new File(jdeployAppsDir, appInfo().getTitle()+".app");
            if (installAppPath.exists() && installationSettings.isOverwriteApp()) {
                FileUtils.deleteDirectory(installAppPath);
            }
            File tmpAppPath = null;
            for (File candidateApp : new File(tmpBundles, target).listFiles()) {
                if (candidateApp.getName().endsWith(".app")) {
                    int result = Runtime.getRuntime().exec(new String[]{"mv", candidateApp.getAbsolutePath(), installAppPath.getAbsolutePath()}).waitFor();
                    if (result != 0) {
                        throw new RuntimeException("Failed to copy app to "+jdeployAppsDir);
                    }
                    break;
                }
            }
            if (!installAppPath.exists()) {
                throw new RuntimeException("Failed to copy app to "+jdeployAppsDir);
            }
            installedApp = installAppPath;

            if (installationSettings.isAddToDesktop()) {
                File desktopAlias = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + appInfo().getTitle() + ".app");
                if (desktopAlias.exists()) {
                    desktopAlias.delete();
                }
                int result = Runtime.getRuntime().exec(new String[]{"ln", "-s", installAppPath.getAbsolutePath(), desktopAlias.getAbsolutePath()}).waitFor();
                if (result != 0) {
                    throw new RuntimeException("Failed to make desktop alias.");
                }
            }

            if (installationSettings.isAddToDock()) {
                /*
                #!/bin/bash
                    myapp="//Applications//System Preferences.app"
                    defaults write com.apple.dock persistent-apps -array-add "<dict><key>tile-data</key><dict><key>file-data</key><dict><key>_CFURLString</key><string>$myapp</string><key>_CFURLStringType</key><integer>0</integer></dict></dict></dict>"
                    osascript -e 'tell application "Dock" to quit'
                    osascript -e 'tell application "Dock" to activate'
                 */
                String myapp = installAppPath.getAbsolutePath().replace('/', '#').replace("#", "//");
                File shellScript = File.createTempFile("installondock", ".sh");
                shellScript.deleteOnExit();

                System.out.println("Adding to dock: "+myapp);
                String[] commands = new String[]{
                        "/usr/bin/defaults write com.apple.dock persistent-apps -array-add \"<dict><key>tile-data</key><dict><key>file-data</key><dict><key>_CFURLString</key><string>"+myapp+"</string><key>_CFURLStringType</key><integer>0</integer></dict></dict></dict>\"",
                        "/usr/bin/osascript -e 'tell application \"Dock\" to quit'",
                        "/usr/bin/osascript -e 'tell application \"Dock\" to activate'"

                };
                try (PrintWriter printWriter = new PrintWriter(new FileOutputStream(shellScript))) {
                    printWriter.println("#!/bin/bash");
                    for (String cmd : commands) {
                        printWriter.println(cmd);
                    }
                }
                shellScript.setExecutable(true, false);
                Runtime.getRuntime().exec(shellScript.getAbsolutePath());
            }
        } else if (Platform.getSystemPlatform().isLinux()) {
            File tmpExePath = null;
            for (File exeCandidate : new File(tmpBundles, "linux").listFiles()) {
                tmpExePath = exeCandidate;
            }
            if (tmpExePath == null) {
                throw new RuntimeException("Failed to find launcher file after creation.  Something must have gone wrong in generation process");
            }
            File userHome = new File(System.getProperty("user.home"));
            File jdeployHome = new File(userHome, ".jdeploy");
            File appsDir = new File(jdeployHome, "apps");
            File appDir = new File(appsDir, appInfo().getNpmPackage());
            appDir.mkdirs();
            File exePath = new File(appDir, tmpExePath.getName());
            FileUtil.copy(tmpExePath, exePath);

            // Copy the icon.png if it is present
            File bundleIcon = new File(findAppXmlFile().getParentFile(), "icon.png");
            File iconPath = new File(exePath.getParentFile(), "icon.png");
            if (bundleIcon.exists()) {
                FileUtil.copy(bundleIcon, iconPath);
            }
            installLinuxMimetypes();
            installLinuxLinks(exePath);
            installedApp = exePath;

        }

        File tmpPlatformBundles = new File(tmpBundles, target);

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



    private void installWindowsLink(int type, File exePath, File iconPath) throws Exception {


        System.out.println("Installing windows link type "+type+" for exe "+exePath+" and icon "+iconPath);
        ShellLink link = new ShellLink(type, appInfo().getTitle());
        System.out.println("current user link path: "+link.getcurrentUserLinkPath());

        link.setUserType(ShellLink.CURRENT_USER);
        if (appInfo().getDescription() == null) {
            link.setDescription("Windows application");
        } else {
            link.setDescription(appInfo().getDescription());
        }
        String iconPathString = iconPath.getCanonicalFile().getAbsolutePath();
        File homeDir = new File(System.getProperty("user.home")).getCanonicalFile();


        int homePathPos = iconPathString.indexOf(homeDir.getAbsolutePath());

        System.out.println("Setting icon path in link "+iconPathString);
        link.setIconLocation(iconPathString, 0);

        String exePathString = exePath.getCanonicalFile().getAbsolutePath();

        System.out.println("Setting exePathString: "+exePathString);
        link.setTargetPath(exePathString);
        link.save();
    }



    private void installWindowsLinks(File exePath) throws Exception {
        System.out.println("Installing Windows links for exe "+exePath);
        File pngIconPath = new File(exePath.getParentFile(), "icon.png");
        File icoPath = new File(exePath.getParentFile().getCanonicalFile(), "icon.ico");

        if (!Files.exists(pngIconPath.toPath())) {
            System.out.println("PNG igon "+pngIconPath+" doesn't exist yet.... loading default from resources.");
            copyResourceToFile(Main.class, "icon.png", pngIconPath);
        }
        if (!Files.exists(pngIconPath.toPath())) {
            System.out.println("After creating "+pngIconPath+" icon file, it still doesn't exist.  What gives.");
            throw new IOException("Failed to create the .ico file for some reason. "+icoPath);
        }
        convertWindowsIcon(pngIconPath.getCanonicalFile(), icoPath);


        if (installationSettings.isAddToDesktop()) {
            try {
                installWindowsLink(ShellLink.DESKTOP, exePath, icoPath);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to install desktop shortcut", ex);

            }
        }
        if (installationSettings.isAddToPrograms()) {
            try {
                installWindowsLink(ShellLink.PROGRAM_MENU, exePath, icoPath);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to install program menu shortcut", ex);

            }
        }
        if (installationSettings.isAddToStartMenu()) {
            try {
                installWindowsLink(ShellLink.START_MENU, exePath, icoPath);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to install start menu shortcut", ex);

            }
        }
    }

    private void installLinuxMimetypes() throws IOException {
        if (appInfo().hasDocumentTypes()) {
            MimeTypeHelper helper = new MimeTypeHelper();
            class MimeInfo {
                private String mimetype;
                private Set<String> extensions;

                private void addExtension(String extension) {
                    if (extensions == null) extensions = new HashSet<String>();
                    extensions.add(extension);
                }

                private void install() throws IOException {
                    MimeTypeHelper helper = new MimeTypeHelper();
                    helper.install(mimetype, appInfo().getTitle(), extensions.toArray(new String[extensions.size()]));
                }
            }
            class MimeInfos {
                private Map<String,MimeInfo> mimeInfos = new HashMap<>();

                private void add(String mimetype, String extension) {
                    if (!mimeInfos.containsKey(mimetype)) {
                        MimeInfo mimeInfo = new MimeInfo();
                        mimeInfo.mimetype = mimetype;
                        mimeInfos.put(mimetype, mimeInfo);
                    }
                    MimeInfo m = mimeInfos.get(mimetype);
                    m.addExtension(extension);

                }

                private void install() throws IOException {
                    for (MimeInfo info : mimeInfos.values()) {
                        info.install();
                    }
                }
            }
            MimeInfos mimeInfos = new MimeInfos();
            for (String extension : appInfo().getExtensions()) {
                String mimetype = appInfo().getMimetype(extension);
                if (!helper.isInstalled(mimetype)) {
                    mimeInfos.add(mimetype, extension);
                }
            }
            mimeInfos.install();

        }
    }

    private void writeLinuxDesktopFile(File dest, String appTitle, File appIcon, File launcher) throws IOException {
        String contents = "[Desktop Entry]\n" +
                "Version=1.0\n" +
                "Type=Application\n" +
                "Name={{APP_TITLE}}\n" +
                "Icon={{APP_ICON}}\n" +
                "Exec=\"{{LAUNCHER_PATH}}\" %U\n" +
                "Comment=Launch {{APP_TITLE}}\n" +
                "Terminal=false\n";

        if (appInfo().hasDocumentTypes() || appInfo().hasUrlSchemes()) {
            StringBuilder mimetypes = new StringBuilder();
            if (appInfo().hasDocumentTypes()) {
                for (String extension : appInfo().getExtensions()) {
                    String mimetype = appInfo().getMimetype(extension);
                    if (mimetypes.length() > 0) {
                        mimetypes.append(";");
                    }
                    mimetypes.append(mimetype);
                }
            }
            if (appInfo().hasUrlSchemes()) {
                for (String scheme : appInfo().getUrlSchemes()) {
                    if (mimetypes.length() > 0) {
                        mimetypes.append(";");
                    }
                    mimetypes.append("x-scheme-handler/").append(scheme);
                }
            }
            contents += "MimeType="+mimetypes+"\n";
        }




        contents = contents
                .replace("{{APP_TITLE}}", appTitle)
                .replace("{{APP_ICON}}", appIcon.getAbsolutePath())
                .replace("{{LAUNCHER_PATH}}", launcher.getAbsolutePath());
        FileUtil.writeStringToFile(contents, dest);
    }

    private void addLinuxDesktopFile(File desktopDir, String filePrefix, String title, File pngIcon, File launcherFile) throws IOException {
        if (desktopDir.exists()) {
            File desktopFile = new File(desktopDir, filePrefix+".desktop");
            while (desktopFile.exists()) {
                int index = 2;
                String baseName = desktopFile.getName();
                if (desktopFile.getName().lastIndexOf(" ") != -1) {
                    String indexStr = desktopFile.getName().substring(desktopFile.getName().lastIndexOf(" ")+1);
                    try {
                        index = Integer.parseInt(indexStr);
                        index++;
                        baseName = desktopFile.getName().substring(0, desktopFile.getName().lastIndexOf(" "));
                    } catch (Throwable t) {
                        index = 2;
                    }
                }
                String newName = baseName + " " + index;
                desktopFile = new File(desktopFile.getParentFile(), newName);
            }
            writeLinuxDesktopFile(desktopFile, title, pngIcon, launcherFile);
            desktopFile.setExecutable(true);

        }



    }

    public void installLinuxLinks(File launcherFile) throws Exception {
        if (!launcherFile.exists()) {
            throw new IllegalStateException("Launcher "+launcherFile+" does not exist so we cannot install a shortcut to it.");
        }

        launcherFile.setExecutable(true, false);

        File pngIcon = new File(launcherFile.getParentFile(), "icon.png");
        if (!pngIcon.exists()) {
            IOUtil.copyResourceToFile(Main.class, "icon.png", pngIcon);

        }

        if (installationSettings.isAddToDesktop()) {
            File desktopDir = new File(System.getProperty("user.home"), "Desktop");
            addLinuxDesktopFile(desktopDir, appInfo().getTitle(), appInfo().getTitle(), pngIcon, launcherFile);
        }
        if (installationSettings.isAddToPrograms()) {
            File homeDir = new File(System.getProperty("user.home"));
            File applicationsDir = new File(homeDir, ".local"+File.separator+"share"+File.separator+"applications");
            applicationsDir.mkdirs();
            addLinuxDesktopFile(applicationsDir, appInfo().getTitle(), appInfo().getTitle(), pngIcon, launcherFile);

            // We need to run update desktop database before file type associations and url schemes will be
            // recognized.
            Process p = Runtime.getRuntime().exec(new String[]{"update-desktop-database", applicationsDir.getAbsolutePath()});
            int result = p.waitFor();
            if (result != 0) {
                throw new IOException("Failed to update desktop database.  Exit code "+result);
            }
        }


    }

}
