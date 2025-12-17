package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.permissions.PermissionRequest;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.helpers.PrereleaseHelper;
import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.linux.LinuxAdminLauncherGenerator;
import ca.weblite.jdeploy.installer.linux.MimeTypeHelper;
import ca.weblite.jdeploy.installer.mac.MacAdminLauncherGenerator;
import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.npm.NPMPackage;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.installer.npm.NPMRegistry;
import ca.weblite.jdeploy.installer.npm.RunAsAdministratorSettings;
import ca.weblite.jdeploy.installer.util.JarClassLoader;
import ca.weblite.jdeploy.installer.util.ResourceUtil;
import ca.weblite.jdeploy.installer.views.DefaultUIFactory;
import ca.weblite.jdeploy.installer.views.InstallationForm;
import ca.weblite.jdeploy.installer.views.UIFactory;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.win.InstallWindows;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.installer.win.UninstallWindows;

import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import ca.weblite.jdeploy.models.CommandSpec;
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
            System.out.println("Looking up package info for: " + appInfo().getNpmPackage() + " version: " + appInfo().getNpmVersion());

            // Get the successful GitHub tag from the bundle download (if available)
            String successfulTag = DefaultInstallationContext.getSuccessfulGitHubTag();

            NPMPackage pkg = new NPMRegistry().loadPackage(appInfo().getNpmPackage(), appInfo().getNpmSource(), successfulTag);
            if (pkg == null) {
                throw new IOException("Cannot find NPMPackage named "+appInfo().getNpmPackage());
            }
            installationSettings.setNpmPackageVersion(pkg.getLatestVersion(installationSettings.isPrerelease(), appInfo().getNpmVersion()));
            if (installationSettings.getNpmPackageVersion() == null) {
                throw new IOException(
                    "Cannot find version " + appInfo().getNpmVersion() + " for package " + appInfo().getNpmPackage() + ". " +
                    "For GitHub projects, ensure the 'jdeploy' release tag contains package-info.json with this version."
                );
            }
            System.out.println("Found version: " + installationSettings.getNpmPackageVersion().getVersion());

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
                if (documentTypeAssociation.isDirectory()) {
                    // Handle directory association - check for default directory icon if none specified
                    DocumentTypeAssociation dirAssoc = documentTypeAssociation;
                    if (documentTypeAssociation.getIconPath() == null) {
                        File dirIconPath = new File(findAppXmlFile().getParentFile(), "icon.directory.png");
                        if (dirIconPath.exists()) {
                            // Create new association with default icon
                            dirAssoc = new DocumentTypeAssociation(
                                    documentTypeAssociation.getRole(),
                                    documentTypeAssociation.getDescription(),
                                    dirIconPath.getAbsolutePath()
                            );
                        }
                    }
                    appInfo().setDirectoryAssociation(dirAssoc);
                } else {
                    // Handle file extension association
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

            RunAsAdministratorSettings runAsAdministratorSettings = npmPackageVersion().getRunAsAdministratorSettings();
            switch (runAsAdministratorSettings) {
                case Required:
                    appInfo().setRequireRunAsAdmin(true);
                    break;
                case Allowed:
                    appInfo().setAllowRunAsAdmin(true);
                    break;
            }
        }
    }

    private static class InvalidAppXMLFormatException extends IOException {
        InvalidAppXMLFormatException(String message) {
            super("The app.xml file is invalid. "+message);
        }
    }

    /**
     * Checks if the specified app path is already in the macOS dock.
     * @param appPath The absolute path to the .app file
     * @return true if the app is already in the dock, false otherwise
     */
    public static boolean isAppInDock(String appPath) {
        if (!Platform.getSystemPlatform().isMac()) {
            return false;
        }

        try {
            // Use defaults read to get the persistent-apps array from dock plist
            Process process = Runtime.getRuntime().exec(new String[]{
                "/usr/bin/defaults", "read", "com.apple.dock", "persistent-apps"
            });

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();

            // The dock stores paths as file:// URLs with trailing slash and URL encoding
            // e.g., "file:///Users/shannah/Applications/Hello%20Java%20FX.app/"
            // Spaces are encoded as %20
            String encodedPath = appPath.replace(" ", "%20");
            String fileUrl = "file://" + encodedPath;
            if (!fileUrl.endsWith("/")) {
                fileUrl += "/";
            }

            // Check if the file URL appears in the dock configuration
            return output.toString().contains(fileUrl);
        } catch (Exception e) {
            // If we can't read the dock settings, assume it's not in the dock
            System.err.println("Warning: Could not check dock status: " + e.getMessage());
            return false;
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
                String message = ex.getMessage();
                if (ex instanceof UserLangRuntimeException) {
                    UserLangRuntimeException userEx = (UserLangRuntimeException) ex;
                    if (userEx.hasUserFriendlyMessage()) {
                        message = userEx.getUserFriendlyMessage();
                    }
                }
                uiFactory.showModalErrorDialog(null, message, "Installation failed.");
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
        } else if (Platform.getSystemPlatform().isLinux()) {
            try {
                // On Linux, use nohup and redirect output to properly detach the process
                // This prevents the child from being terminated when the installer exits
                ProcessBuilder pb = new ProcessBuilder(
                    "nohup",
                    installedApp.getAbsolutePath()
                );
                pb.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
                pb.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                pb.start();

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

        // Check if app is already in the dock (macOS only)
        if (Platform.getSystemPlatform().isMac() && appInfo() != null) {
            String nameSuffix = "";
            if (appInfo().getNpmVersion().startsWith("0.0.0-")) {
                nameSuffix = " " + appInfo().getNpmVersion().substring(appInfo().getNpmVersion().indexOf("-") + 1).trim();
            }
            String appName = appInfo().getTitle() + nameSuffix;
            String appPath = System.getProperty("user.home") + "/Applications/" + appName + ".app";

            // Only check if the app exists on disk - if it doesn't exist, it can't be in the dock
            File appFile = new File(appPath);
            if (appFile.exists()) {
                installationSettings.setAlreadyAddedToDock(isAppInDock(appPath));
            }
        }

        // Check for desktop environment on Linux
        if (Platform.getSystemPlatform().isLinux()) {
            installationSettings.setHasDesktopEnvironment(isDesktopEnvironmentAvailable());

            // Set command line path if ~/.local/bin exists
            File localBinDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
            String commandName = deriveCommandName();
            File symlinkPath = new File(localBinDir, commandName);
            installationSettings.setCommandLinePath(symlinkPath.getAbsolutePath());
        }

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
                String message = ex.getMessage();
                if (ex instanceof UserLangRuntimeException) {
                    UserLangRuntimeException userEx = (UserLangRuntimeException) ex;
                    if (userEx.hasUserFriendlyMessage()) {
                        message = userEx.getUserFriendlyMessage();
                    }
                }
                String finalMessage = message;
                invokeLater(()-> uiFactory.showModalErrorDialog(evt.getInstallationForm(), "Installation failed. "+finalMessage, "Failed"));
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
            target = "mac" + ArchitectureUtil.getArchitectureSuffix();
        } else if (Platform.getSystemPlatform().isWindows()) {
            target = "win" + ArchitectureUtil.getArchitectureSuffix();
        } else if (Platform.getSystemPlatform().isLinux()) {
            target = "linux" + ArchitectureUtil.getArchitectureSuffix();
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

        // Set launcher version from system property if available (installer context only)
        String launcherVersion = System.getProperty("jdeploy.app.version");
        if (launcherVersion != null && !launcherVersion.isEmpty()) {
            appInfo().setLauncherVersion(launcherVersion);
        }

        // Set initial app version from system property if available (parsed from installer filename)
        String initialAppVersion = npmPackageVersion().getVersion();
        if (initialAppVersion != null && !initialAppVersion.isEmpty()) {
            appInfo().setInitialAppVersion(initialAppVersion);
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
                        String logPath = System.getProperty("user.home") + "/.jdeploy/log/jdeploy-installer.log";
                        String technicalMessage = "Failed to move application bundle to " + installAppPath.getAbsolutePath() + ". mv command returned exit code " + result;
                        String userMessage = "<html><body style='width: 400px;'>" +
                            "<h3>Installation Failed</h3>" +
                            "<p>Could not install the application to:<br/><b>" + jdeployAppsDir.getAbsolutePath() + "</b></p>" +
                            "<p><b>Possible causes:</b></p>" +
                            "<ul>" +
                            "<li>You don't have write permission to the Applications directory</li>" +
                            "<li>The application is currently running (please close it and try again)</li>" +
                            "</ul>" +
                            "<p style='margin-top: 12px;'><small>For technical details, check the log file:<br/>" +
                            logPath + "</small></p>" +
                            "</body></html>";
                        throw new UserLangRuntimeException(technicalMessage, userMessage);
                    }
                    break;
                }
            }
            if (!installAppPath.exists()) {
                String logPath = System.getProperty("user.home") + "/.jdeploy/log/jdeploy-installer.log";
                String technicalMessage = "Application bundle does not exist at " + installAppPath.getAbsolutePath() + " after installation attempt";
                String userMessage = "<html><body style='width: 400px;'>" +
                    "<h3>Installation Failed</h3>" +
                    "<p>Could not install the application to:<br/><b>" + jdeployAppsDir.getAbsolutePath() + "</b></p>" +
                    "<p><b>Possible causes:</b></p>" +
                    "<ul>" +
                    "<li>You don't have write permission to the Applications directory</li>" +
                    "<li>The application is currently running (please close it and try again)</li>" +
                    "</ul>" +
                    "<p style='margin-top: 12px;'><small>For technical details, check the log file:<br/>" +
                    logPath + "</small></p>" +
                    "</body></html>";
                throw new UserLangRuntimeException(technicalMessage, userMessage);
            }
            installedApp = installAppPath;
            File adminWrapper = null;

            if (appInfo().isRequireRunAsAdmin() || appInfo().isAllowRunAsAdmin()) {
                MacAdminLauncherGenerator macAdminLauncherGenerator = new MacAdminLauncherGenerator();
                adminWrapper  = macAdminLauncherGenerator.getAdminLauncherFile(installedApp);
                if (adminWrapper.exists()) {
                    // delete the old recursively
                    FileUtils.deleteDirectory(adminWrapper);
                }
                adminWrapper = new MacAdminLauncherGenerator().generateAdminLauncher(installedApp);
            }

            if (installationSettings.isAddToDesktop()) {
                File desktopAlias = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + appName + ".app");
                if (desktopAlias.exists()) {
                    desktopAlias.delete();
                }
                String targetPath = installAppPath.getAbsolutePath();
                if (adminWrapper != null && appInfo().isRequireRunAsAdmin()) {
                    targetPath = adminWrapper.getAbsolutePath();
                }
                int result = Runtime.getRuntime().exec(new String[]{"ln", "-s", targetPath, desktopAlias.getAbsolutePath()}).waitFor();
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
                String targetPath = installAppPath.getAbsolutePath();
                if (adminWrapper != null && appInfo().isRequireRunAsAdmin()) {
                    targetPath = adminWrapper.getAbsolutePath();
                }
                String myapp = targetPath.replace('/', '#').replace("#", "//");
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
            try {
                FileUtil.copy(tmpExePath, exePath);
            } catch (IOException e) {
                String logPath = System.getProperty("user.home") + "/.jdeploy/log/jdeploy-installer.log";
                String technicalMessage = "Failed to copy application launcher to " + exePath.getAbsolutePath() + ": " + e.getMessage();
                String userMessage = "<html><body style='width: 400px;'>" +
                    "<h3>Installation Failed</h3>" +
                    "<p>Could not install the application to:<br/><b>" + appDir.getAbsolutePath() + "</b></p>" +
                    "<p><b>Possible causes:</b></p>" +
                    "<ul>" +
                    "<li>You don't have write permission to the directory</li>" +
                    "<li>The application is currently running (please close it and try again)</li>" +
                    "</ul>" +
                    "<p style='margin-top: 12px;'><small>For technical details, check the log file:<br/>" +
                    logPath + "</small></p>" +
                    "</body></html>";
                throw new UserLangRuntimeException(technicalMessage, userMessage, e);
            }

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

    /**
     * Detects if a desktop environment is available on Linux.
     * This checks for an actual desktop environment (GNOME, KDE, XFCE, etc.),
     * not just a graphical environment.
     * @return true if a desktop environment is detected, false otherwise
     */
    private boolean isDesktopEnvironmentAvailable() {
        if (!Platform.getSystemPlatform().isLinux()) {
            return true; // Non-Linux platforms always have desktop support in this context
        }

        // Check for desktop environment specific variables
        // XDG_CURRENT_DESKTOP indicates which desktop environment is running
        String currentDesktop = System.getenv("XDG_CURRENT_DESKTOP");
        if (currentDesktop != null && !currentDesktop.isEmpty()) {
            return true;
        }

        // DESKTOP_SESSION also indicates a desktop environment
        String desktopSession = System.getenv("DESKTOP_SESSION");
        if (desktopSession != null && !desktopSession.isEmpty()) {
            return true;
        }

        // Check if desktop directory exists (usually created by desktop environments)
        File desktopDir = new File(System.getProperty("user.home"), "Desktop");
        if (desktopDir.exists() && desktopDir.isDirectory()) {
            // Also verify update-desktop-database exists, which is part of desktop file utilities
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"which", "update-desktop-database"});
                int result = p.waitFor();
                if (result == 0) {
                    return true;
                }
            } catch (Exception e) {
                // Command not found
            }
        }

        return false;
    }

    /**
     * Derives the command name for the CLI from package.json.
     * Priority:
     * 1. jdeploy.command property
     * 2. bin object key that maps to "jdeploy-bundle/jdeploy.js"
     * 3. name property
     *
     * @return the command name to use
     */
    private String deriveCommandName() {
        NPMPackageVersion pkgVersion = npmPackageVersion();
        if (pkgVersion != null) {
            String commandName = pkgVersion.getCommandName();
            if (commandName != null) {
                return commandName;
            }
        }

        // Fallback to title
        return appInfo().getTitle().toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "");
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
        // Derive StartupWMClass from the npm package name
        String wmClass = npmPackageVersion().getMainClass();
        if (wmClass != null) {
            wmClass = wmClass.replaceAll("\\.", "-");
        }
        if (npmPackageVersion().getWmClassName() != null) {
            wmClass = npmPackageVersion().getWmClassName();
        }

        String contents = "[Desktop Entry]\n" +
                "Version=1.0\n" +
                "Type=Application\n" +
                "Name={{APP_TITLE}}\n" +
                "Icon={{APP_ICON}}\n" +
                "Exec=\"{{LAUNCHER_PATH}}\" %U\n" +
                "Comment=Launch {{APP_TITLE}}\n" +
                "Terminal=false\n";

        // Add StartupWMClass if we have a valid value
        if (wmClass != null && !wmClass.isEmpty()) {
            contents += "StartupWMClass=" + wmClass + "\n";
        }

        if (appInfo().hasDocumentTypes() || appInfo().hasUrlSchemes() || appInfo().hasDirectoryAssociation()) {
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
            if (appInfo().hasDirectoryAssociation()) {
                if (mimetypes.length() > 0) {
                    mimetypes.append(";");
                }
                mimetypes.append("inode/directory");
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
            File runAsAdminFile = new File(desktopDir, filePrefix+" (Run as Admin).desktop");
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
            if (appInfo().isRequireRunAsAdmin()) {
                // For required admin, generate admin launcher and use it in the desktop file
                LinuxAdminLauncherGenerator generator = new LinuxAdminLauncherGenerator();
                File adminLauncher = generator.generateAdminLauncher(launcherFile);
                writeLinuxDesktopFile(desktopFile, title, pngIcon, adminLauncher);
            } else if (appInfo().isAllowRunAsAdmin()) {
                // For allowed admin, create both regular and admin launchers
                writeLinuxDesktopFile(desktopFile, title, pngIcon, launcherFile);

                // Create admin launcher and desktop file
                LinuxAdminLauncherGenerator generator = new LinuxAdminLauncherGenerator();
                File adminLauncher = generator.generateAdminLauncher(launcherFile);
                writeLinuxDesktopFile(runAsAdminFile, title + " (Run as Admin)", pngIcon, adminLauncher);
                runAsAdminFile.setExecutable(true);
            } else {
                // Regular launcher only
                writeLinuxDesktopFile(desktopFile, title, pngIcon, launcherFile);
            }
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

        boolean hasDesktop = isDesktopEnvironmentAvailable();

        // Install desktop shortcuts only if desktop environment is available
        if (hasDesktop) {
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
                // recognized. Only do this if desktop environment is available.
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"update-desktop-database", applicationsDir.getAbsolutePath()});
                    int result = p.waitFor();
                    if (result != 0) {
                        System.err.println("Warning: Failed to update desktop database. Exit code "+result);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to run update-desktop-database: " + e.getMessage());
                }
            }
        } else {
            System.out.println("No desktop environment detected. Skipping desktop shortcuts and mimetype registration.");
        }

        // Install command-line scripts and/or symlink in ~/.local/bin if user requested it
        if (installationSettings.isInstallCliCommand()) {
            File localBinDir = new File(System.getProperty("user.home"), ".local"+File.separator+"bin");

            // Create ~/.local/bin if it doesn't exist
            if (!localBinDir.exists()) {
                if (!localBinDir.mkdirs()) {
                    System.err.println("Warning: Failed to create ~/.local/bin directory");
                    return;
                }
                System.out.println("Created ~/.local/bin directory");
            }

            boolean anyCreated = false;

            // Create per-command scripts if commands are declared in package metadata
            try {
                List<CommandSpec> commands = npmPackageVersion() != null ? npmPackageVersion().getCommands() : Collections.emptyList();
                if (commands != null && !commands.isEmpty()) {
                    for (CommandSpec cs : commands) {
                        String cmdName = cs.getName();
                        File scriptPath = new File(localBinDir, cmdName);
                        if (scriptPath.exists()) {
                            scriptPath.delete();
                        }
                        try {
                            ca.weblite.jdeploy.installer.linux.LinuxCliScriptWriter.writeExecutableScript(scriptPath, launcherFile.getAbsolutePath(), cmdName);
                            System.out.println("Created command-line script: " + scriptPath.getAbsolutePath());
                            anyCreated = true;
                        } catch (IOException ioe) {
                            System.err.println("Warning: Failed to create command script for " + cmdName + ": " + ioe.getMessage());
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Warning: Failed to enumerate commands: " + ex.getMessage());
            }

            // Maintain compatibility: also create traditional single symlink for primary command
            String commandName = deriveCommandName();
            File symlinkPath = new File(localBinDir, commandName);
            if (symlinkPath.exists()) {
                symlinkPath.delete();
            }
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"ln", "-s", launcherFile.getAbsolutePath(), symlinkPath.getAbsolutePath()});
                int result = p.waitFor();
                if (result == 0) {
                    System.out.println("Created command-line symlink: " + symlinkPath.getAbsolutePath());
                    installationSettings.setCommandLineSymlinkCreated(true);
                    anyCreated = true;
                } else {
                    System.err.println("Warning: Failed to create command-line symlink. Exit code "+result);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to create command-line symlink: " + e.getMessage());
            }

            if (anyCreated) {
                // Check if ~/.local/bin is in PATH
                String path = System.getenv("PATH");
                String localBinPath = localBinDir.getAbsolutePath();
                if (path == null || !path.contains(localBinPath)) {
                    boolean pathUpdated = addToPath(localBinDir);
                    installationSettings.setAddedToPath(pathUpdated);
                } else {
                    installationSettings.setAddedToPath(true);
                }
            }
        } else {
            System.out.println("Skipping CLI command installation (user opted out)");
        }
    }

    /**
     * Adds ~/.local/bin to the user's PATH by updating their shell configuration file.
     * This method detects the user's shell and appends the PATH export to the appropriate config file.
     *
     * @param localBinDir The ~/.local/bin directory to add to PATH
     * @return true if PATH was successfully updated or already contains the directory, false otherwise
     */
    private boolean addToPath(File localBinDir) {
        try {
            // Detect the user's shell
            String shell = System.getenv("SHELL");
            if (shell == null || shell.isEmpty()) {
                shell = "/bin/bash"; // Default to bash
            }

            File configFile = null;
            String shellName = new File(shell).getName();

            // Determine which config file to update based on the shell
            File homeDir = new File(System.getProperty("user.home"));
            switch (shellName) {
                case "bash":
                    // For bash, prefer .bashrc, but use .bash_profile if .bashrc doesn't exist
                    File bashrc = new File(homeDir, ".bashrc");
                    File bashProfile = new File(homeDir, ".bash_profile");
                    configFile = bashrc.exists() ? bashrc : bashProfile;
                    break;
                case "zsh":
                    configFile = new File(homeDir, ".zshrc");
                    break;
                case "fish":
                    // Fish uses a different syntax, skip for now
                    System.out.println("Note: Fish shell detected. Please manually add ~/.local/bin to your PATH:");
                    System.out.println("  set -U fish_user_paths ~/.local/bin $fish_user_paths");
                    return false;
                default:
                    // For unknown shells, try .profile as a fallback
                    configFile = new File(homeDir, ".profile");
                    break;
            }

            // Check if the PATH export already exists in the config file
            if (configFile.exists()) {
                String content = IOUtil.readToString(new FileInputStream(configFile));
                if (content.contains("$HOME/.local/bin") || content.contains(localBinDir.getAbsolutePath())) {
                    System.out.println("~/.local/bin is already in PATH configuration");
                    return true;
                }
            }

            // Append PATH export to the config file
            String pathExport = "\n# Added by jDeploy installer\nexport PATH=\"$HOME/.local/bin:$PATH\"\n";
            try (FileOutputStream fos = new FileOutputStream(configFile, true)) {
                fos.write(pathExport.getBytes());
            }

            System.out.println("Added ~/.local/bin to PATH in " + configFile.getName());
            System.out.println("Please restart your terminal or run: source " + configFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            System.err.println("Warning: Failed to add ~/.local/bin to PATH: " + e.getMessage());
            System.out.println("You may need to manually add ~/.local/bin to your PATH");
            return false;
        }
    }

}
