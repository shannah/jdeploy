package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.permissions.PermissionRequest;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.helpers.PrereleaseHelper;
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
import ca.weblite.jdeploy.installer.win.InstallWindows;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.installer.win.UninstallWindows;

import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import ca.weblite.tools.io.*;
import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.awt.Desktop;
import java.io.*;
import java.net.URL;
import java.util.*;
import static ca.weblite.tools.io.IOUtil.copyResourceToFile;

public class Main implements Runnable, Constants {
    private static final String JDEPLOY_REGISTRY_URL = System.getProperty(
            "jdeploy.registry.url",
            "https://www.jdeploy.com/"
    );
    private final InstallationContext installationContext = new DefaultInstallationContext();
    private final InstallationSettings installationSettings;
    private UIFactory uiFactory = new DefaultUIFactory();
    private InstallationForm installationForm;

    private Main() {
        this(new InstallationSettings());
    }

    private Main(InstallationSettings settings) {
        this.installationSettings = settings;
    }

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
        if (installationSettings.getNpmPackageVersion() == null) {
            if (appInfo() == null) {
                throw new IllegalStateException("App Info must be loaded before loading the package info");
            }
            NPMPackage pkg = new NPMRegistry().loadPackage(appInfo().getNpmPackage(), appInfo().getNpmSource());
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
            for (String scheme : npmPackageVersion().getUrlSchemes()) {
                appInfo().addUrlScheme(scheme);
            }
            
            for (Map.Entry<PermissionRequest, String> entry : npmPackageVersion().getPermissionRequests().entrySet()) {
                appInfo().addPermissionRequest(entry.getKey(), entry.getValue());
            }

            if (Platform.getSystemPlatform().isWindows() && "8".equals(npmPackageVersion().getJavaVersion())) {
                // In Windows with Java 8, we need to use a private JVM because OpenJDK 8 didn't support the use
                // of shared JVMs very well.  Share JVMs needed to be registered in the registry, which would override
                // for all applications using a shard JVM - there is no way to allow the app to be a good citizen
                // with a shared JVM.
                // However it does allow the use of a private JVM if the app resided in a directory named "bin"
                // and the JVM is in a directory named "jre" which is a sibling of the "bin" directory.
                appInfo().setUsePrivateJVM(true);
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
        appInfo().setJdeployRegistryUrl(JDEPLOY_REGISTRY_URL);
        appInfo().setAppURL(appXml.toURI().toURL());
        appInfo().setTitle(ifEmpty(root.getAttribute("title"), root.getAttribute("package"), null));
        appInfo().setNpmPackage(ifEmpty(root.getAttribute("package"), null));
        String fullyQualifiedPackageName = appInfo().getNpmPackage();
        if (root.hasAttribute("source")) {
            appInfo().setNpmSource(root.getAttribute("source"));
        }
        if (appInfo().getNpmSource() != null && !appInfo().getNpmSource().isEmpty()) {
            fullyQualifiedPackageName = MD5.getMd5(appInfo().getNpmSource()) + "." + fullyQualifiedPackageName;
        }
        appInfo().setFork(false);

        // First we set the version in appInfo according to the app.xml file
        appInfo().setNpmVersion(ifEmpty(root.getAttribute("version"), "latest"));
        // Next we use that version to load the package info from the NPM registry.
        loadNPMPackageInfo();

        String bundleSuffix = "";
        if (appInfo().getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo().getNpmVersion();
            bundleSuffix = "." +v.substring(v.indexOf("-")+1);
        }
        appInfo().setMacAppBundleId(ifEmpty(root.getAttribute("macAppBundleId"), "ca.weblite.jdeploy.apps."+fullyQualifiedPackageName + bundleSuffix));

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
        if (version.startsWith("0.0.0-")) {
            // Special case for Github branches, in which case the app should stay in sync with
            // the branch
            return version;
        }
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

    private static boolean headlessInstall;

    public static void main(String[] args) {

        if (args.length == 1 && args[0].equals("uninstall")) {
            uninstall = true;
        }

        if (args.length == 1 && args[0].equals("install")) {
            headlessInstall = true;
        }

        if (!headlessInstall) {
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
        }

        Main main = headlessInstall ? new Main(new HeadlessInstallationSettings()) :new Main();
        main.run();
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
            System.err.flush();
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
            String version = null;
            if (appInfo().getNpmVersion() != null && appInfo().getNpmVersion().startsWith("0.0.0-")) {
                version = appInfo().getNpmVersion();
            }
            UninstallWindows uninstallWindows = new UninstallWindows(
                    appInfo().getNpmPackage(),
                    appInfo().getNpmSource(),
                    version,
                    appInfo().getTitle(),
                    installer
            );
            uninstallWindows.uninstall();
            System.out.println("Uninstall complete");
            return;
        }

        if (headlessInstall) {
            runHeadlessInstall();
            return;
        }
        invokeLater(()->{
            buildUI();
            installationForm.showInstallationForm();
        });

    }
    private void runHeadlessInstall() throws Exception {
        System.out.println(
                "jDeploy installer running in headless mode.  Installing " +
                        appInfo().getTitle() + " " + npmPackageVersion().getVersion()
        );
        try {
            install();
        } catch (Exception ex) {
            System.err.println("Installation failed");
            ex.printStackTrace(System.err);
            return;
        }
        System.out.println("Installation complete");
    }

    private File installedApp;

    private void install() throws Exception {

        // Based on the user's settings, let's update the version in the appInfo
        // to correspond with the auto-update settings.
        appInfo().setNpmVersion(createSemVerForVersion(npmPackageVersion().getVersion(), installationSettings.getAutoUpdate()));
        appInfo().setNpmAllowPrerelease(installationSettings.isPrerelease());
        if (PrereleaseHelper.isPrereleaseVersion(npmPackageVersion().getVersion())) {
            appInfo().setNpmAllowPrerelease(true);
        }


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
            if (System.getProperty("os.arch").equals("aarch64") || System.getProperty("os.arch").equals("arm64")) {
                target += "-arm64";
            } else {
                target += "-x64";
            }
        } else if (Platform.getSystemPlatform().isLinux()) {
            target = "linux";
            if (System.getProperty("os.arch").equals("aarch64") || System.getProperty("os.arch").equals("arm64")) {
                target += "-arm64";
            } else {
                target += "-x64";
            }
        } else {
            throw new RuntimeException("Installation failed.  Your platform is not currently supported.");
        }
        {
            File icon = new File(findAppXmlFile().getParentFile(), "icon.png");
            if (!icon.exists()) {
                copyResourceToFile(Main.class, "icon.png", icon);
            }
        }

        BundlerSettings bundlerSettings = new BundlerSettings();
        String sourceHash = null;
        String fullyQualifiedPackageName = appInfo().getNpmPackage();
        if (appInfo().getNpmSource() != null && !appInfo().getNpmSource().isEmpty()) {
            bundlerSettings.setSource(appInfo().getNpmSource());
            sourceHash = MD5.getMd5(appInfo().getNpmSource());
            fullyQualifiedPackageName = sourceHash + "." + fullyQualifiedPackageName;
        }

        Bundler.runit(bundlerSettings, appInfo(), findAppXmlFile().toURI().toURL().toString(), target, tmpBundles.getAbsolutePath(), tmpReleases.getAbsolutePath());

        if (Platform.getSystemPlatform().isWindows()) {
            installedApp = new InstallWindows().install(
                    installationContext,
                    installationSettings,
                    fullyQualifiedPackageName,
                    tmpBundles
            );
        } else if (Platform.getSystemPlatform().isMac()) {
            File jdeployAppsDir = new File(System.getProperty("user.home") + File.separator + "Applications");
            if (!jdeployAppsDir.exists()) {
                jdeployAppsDir.mkdirs();
            }
            String nameSuffix = "";
            if (appInfo().getNpmVersion().startsWith("0.0.0-")) {
                nameSuffix = " " + appInfo().getNpmVersion().substring(appInfo().getNpmVersion().indexOf("-") + 1).trim();
            }

            String appName = appInfo().getTitle() + nameSuffix;
            File installAppPath = new File(jdeployAppsDir, appName+".app");
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
                File desktopAlias = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + appName + ".app");
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
            for (File exeCandidate : Objects.requireNonNull(new File(tmpBundles, target).listFiles())) {
                tmpExePath = exeCandidate;
            }
            if (tmpExePath == null) {
                throw new RuntimeException("Failed to find launcher file after creation.  Something must have gone wrong in generation process");
            }
            File userHome = new File(System.getProperty("user.home"));
            File jdeployHome = new File(userHome, ".jdeploy");
            File appsDir = new File(jdeployHome, "apps");
            File appDir = new File(appsDir, fullyQualifiedPackageName);
            appDir.mkdirs();

            String nameSuffix = "";
            String titleSuffix = "";
            if (appInfo().getNpmVersion().startsWith("0.0.0-")) {
                String v = appInfo().getNpmVersion();
                nameSuffix = "-" +v.substring(v.indexOf("-") + 1).trim();
                titleSuffix = "-" + v.substring(v.indexOf("-") + 1).trim();
            }

            String exeName = deriveLinuxBinaryNameFromTitle(appInfo().getTitle()) + nameSuffix;
            File exePath = new File(appDir, exeName);
            FileUtil.copy(tmpExePath, exePath);

            // Copy the icon.png if it is present
            File bundleIcon = new File(findAppXmlFile().getParentFile(), "icon.png");
            File iconPath = new File(exePath.getParentFile(), "icon.png");
            if (bundleIcon.exists()) {
                FileUtil.copy(bundleIcon, iconPath);
            }
            installLinuxMimetypes();

            installLinuxLinks(exePath, appInfo().getTitle() + titleSuffix);
            installedApp = exePath;

        }

        File tmpPlatformBundles = new File(tmpBundles, target);

    }

    private String deriveLinuxBinaryNameFromTitle(String title) {
        return title.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "");
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

    public void installLinuxLinks(File launcherFile, String title) throws Exception {
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
            addLinuxDesktopFile(desktopDir, title, title, pngIcon, launcherFile);
        }
        if (installationSettings.isAddToPrograms()) {
            File homeDir = new File(System.getProperty("user.home"));
            File applicationsDir = new File(homeDir, ".local"+File.separator+"share"+File.separator+"applications");
            applicationsDir.mkdirs();
            addLinuxDesktopFile(applicationsDir, title, title, pngIcon, launcherFile);

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
