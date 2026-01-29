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
import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.mac.MacAdminLauncherGenerator;
import ca.weblite.jdeploy.installer.services.InstallationDetectionService;
import ca.weblite.jdeploy.installer.uninstall.FileUninstallManifestRepository;
import ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder;
import ca.weblite.jdeploy.installer.uninstall.UninstallManifestWriter;
import ca.weblite.jdeploy.installer.uninstall.UninstallService;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
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
import ca.weblite.jdeploy.installer.views.UpdateProgressDialog;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.win.InstallWindows;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.installer.win.JnaRegistryOperations;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import ca.weblite.jdeploy.installer.win.UninstallWindows;

import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import ca.weblite.jdeploy.models.CommandSpec;
import javax.swing.JOptionPane;
import ca.weblite.jdeploy.installer.cli.CliCommandInstaller;
import ca.weblite.jdeploy.installer.cli.MacCliCommandInstaller;
import ca.weblite.jdeploy.installer.cli.LinuxCliCommandInstaller;
import ca.weblite.jdeploy.installer.cli.WindowsCliCommandInstaller;
import ca.weblite.jdeploy.installer.cli.UIAwareCollisionHandler;
import ca.weblite.jdeploy.installer.services.ServiceDescriptor;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorService;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorServiceFactory;
import ca.weblite.jdeploy.installer.services.ServiceOperationExecutor;
import ca.weblite.jdeploy.installer.services.ServiceOperationResult;
import ca.weblite.jdeploy.installer.tray.BackgroundHelper;
import ca.weblite.jdeploy.installer.helpers.HelperCopyService;
import ca.weblite.jdeploy.installer.helpers.HelperInstallationResult;
import ca.weblite.jdeploy.installer.helpers.HelperInstallationService;
import ca.weblite.jdeploy.installer.helpers.HelperManifestHelper;
import ca.weblite.jdeploy.installer.helpers.HelperProcessManager;
import ca.weblite.jdeploy.installer.helpers.HelperUpdateService;
import ca.weblite.jdeploy.installer.ai.models.AiIntegrationInstallResult;
import ca.weblite.jdeploy.installer.ai.models.AiIntegrationManifestEntry;
import ca.weblite.jdeploy.installer.ai.services.AiIntegrationInstaller;
import ca.weblite.tools.io.*;
import ca.weblite.tools.io.MD5;
import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private InstallationDetectionService installationDetectionService = new InstallationDetectionService();
    private ca.weblite.jdeploy.installer.services.ServiceLifecycleProgressCallback serviceLifecycleProgressCallback;

    private Main() {
        this(new InstallationSettings());
    }

    private Main(InstallationSettings settings) {
        this.installationSettings = settings;
        // Initialize the service lifecycle progress callback
        // Use SILENT by default - headless mode redirects output anyway,
        // and GUI mode will set up its own callback via the UI
        this.serviceLifecycleProgressCallback =
            ca.weblite.jdeploy.installer.services.ServiceLifecycleProgressCallback.SILENT;
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

            // Extract helper actions from package.json
            installationSettings.setHelperActions(npmPackageVersion().getHelperActions());

            // Extract Windows app directory from package.json
            if (npmPackageVersion().getWinAppDir() != null) {
                installationSettings.setWinAppDir(npmPackageVersion().getWinAppDir());
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

        // For background helper mode, NPM package info is optional since it may run offline.
        // The helper only needs app.xml info (package name, source, title) for uninstallation.
        if (runAsBackgroundHelper) {
            try {
                loadNPMPackageInfo();
            } catch (IOException e) {
                System.err.println("Warning: Failed to load NPM package info (running in background helper mode): " + e.getMessage());
                System.err.println("Continuing without NPM package info - uninstall functionality will still work.");
            }
        } else {
            // For normal installer mode, NPM package info is required
            loadNPMPackageInfo();
        }

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

    private static boolean runAsBackgroundHelper;


    public static void main(String[] args) {

        if (args.length == 1 && args[0].equals("uninstall")) {
            uninstall = true;
        }

        if (args.length == 1 && args[0].equals("install")) {
            headlessInstall = true;
            // Set AWT headless mode to prevent GUI toolkit initialization
            // This must be set before any AWT classes are loaded
            System.setProperty("java.awt.headless", "true");
        }

        if (System.getProperty("jdeploy.background", "false").equals("true")) {
            runAsBackgroundHelper = true;
        }

        if (headlessInstall) {
            // For headless mode, set up output suppression immediately
            // This must happen before Main object creation which triggers verbose output
            setupStaticHeadlessOutputSuppression();
        } else {
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

    // Static fields for headless output suppression (set in main() before Main object creation)
    private static PrintStream staticOriginalOut;
    private static PrintStream staticOriginalErr;
    private static File staticLogFile;
    private static PrintStream staticLogStream;

    /**
     * Allows external callers (like MainDebug) to set the original streams
     * before Main.main() is called. This is needed when the caller has already
     * set up output redirection and wants Main to use the true original streams.
     */
    public static void setOriginalStreams(PrintStream out, PrintStream err, File logFile) {
        staticOriginalOut = out;
        staticOriginalErr = err;
        staticLogFile = logFile;
    }

    /**
     * Sets up output suppression for headless mode at the static level.
     * Must be called before Main object creation to suppress verbose output from InstallationContext.
     */
    private static void setupStaticHeadlessOutputSuppression() {
        // Only store streams if not already set by external caller (e.g., MainDebug)
        if (staticOriginalOut == null) {
            staticOriginalOut = System.out;
        }
        if (staticOriginalErr == null) {
            staticOriginalErr = System.err;
        }

        // Suppress java.util.logging output
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        java.util.logging.Handler[] handlers = rootLogger.getHandlers();
        for (java.util.logging.Handler handler : handlers) {
            handler.setLevel(java.util.logging.Level.OFF);
        }
        rootLogger.setLevel(java.util.logging.Level.OFF);

        // Only set up log file redirection if not already done
        if (staticLogFile == null) {
            staticLogFile = new File(
                System.getProperty("user.home") + File.separator + ".jdeploy" +
                File.separator + "log" + File.separator + "jdeploy-headless-install.log"
            );
        }
        staticLogFile.getParentFile().mkdirs();

        try {
            staticLogStream = new PrintStream(new FileOutputStream(staticLogFile));
            System.setOut(staticLogStream);
            System.setErr(staticLogStream);
        } catch (FileNotFoundException e) {
            staticOriginalErr.println("Warning: Could not create log file: " + e.getMessage());
        }
    }

    private File findAppXmlFile() {
        return installationContext.findAppXml();

    }

    @Override
    public void run() {
        try {
            run0();
        } catch (Exception ex) {
            // In headless mode, write to original stderr (not the redirected one)
            PrintStream errStream = staticOriginalErr != null ? staticOriginalErr : System.err;
            if (headlessInstall) {
                errStream.println("Installation failed: " + ex.getMessage());
                if (staticLogFile != null) {
                    errStream.println("See log file for details: " + staticLogFile.getAbsolutePath());
                }
                // Also log full stack trace to log file
                ex.printStackTrace(System.err);
                System.err.flush();
                System.exit(1);
            } else {
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

        // Configure CLI installation settings based on platform and available commands
        // Rule 1: On Linux, always install CLI Launcher
        if (Platform.getSystemPlatform().isLinux()) {
            installationSettings.setInstallCliLauncher(true);
        } else {
            // Rule 3: On Mac and Windows, never install CLI Launcher
            installationSettings.setInstallCliLauncher(false);
        }

        // Rule 2: On all platforms, if commands are defined, always install them
        List<CommandSpec> commands = npmPackageVersion() != null ? npmPackageVersion().getCommands() : Collections.emptyList();
        if (!commands.isEmpty()) {
            installationSettings.setInstallCliCommands(true);
        } else {
            installationSettings.setInstallCliCommands(false);
        }

        // Rule 4: If both CLI Launcher and CLI Commands are enabled, check for name conflicts
        // If a CLI command has the same name as the launcher, prefer the CLI command
        if (installationSettings.isInstallCliLauncher() && installationSettings.isInstallCliCommands()) {
            String launcherName = deriveCommandName();
            boolean hasConflict = false;
            for (CommandSpec command : commands) {
                if (launcherName.equals(command.getName())) {
                    hasConflict = true;
                    break;
                }
            }
            if (hasConflict) {
                // Disable CLI launcher installation - prefer CLI command
                installationSettings.setInstallCliLauncher(false);
                System.out.println("CLI launcher '" + launcherName + "' conflicts with a CLI command - preferring CLI command");
            }
        }

        InstallationForm view = uiFactory.createInstallationForm(installationSettings);
        view.setEventDispatcher(new InstallationFormEventDispatcher(view));
        this.installationForm = view;
        // Detect if app is already installed
        detectAndUpdateInstallationState(view);

        view.getEventDispatcher().addEventListener(evt->{
            switch (evt.getType()) {
                case InstallClicked:
                    onInstallClicked(evt);
                    break;
                case UpdateClicked:
                    onUpdateClicked(evt);
                    break;
                case UninstallClicked:
                    onUninstallClicked(evt);
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
                case UninstallCompleteQuit:
                    onUninstallCompleteQuit(evt);
                    break;
                case VisitSoftwareHomepage:
                    onVisitSoftwareHomepage(evt);
                    break;
                case ProceedWithInstallation:
                    onProceedWithInstallation(evt);
                    break;
                case ProceedWithUninstallation:
                    onProceedWithUninstallation(evt);
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

    private void detectAndUpdateInstallationState(InstallationForm view) {
        String packageName = appInfo().getNpmPackage();
        String source = appInfo().getNpmSource();
        boolean isInstalled = installationDetectionService.isInstalled(packageName, source);
        view.setAppAlreadyInstalled(isInstalled);
    }

    private void onUpdateClicked(InstallationFormEvent evt) {
        // Update is the same as install - just reinstall over the existing version
        onInstallClicked(evt);
    }

    private void onUninstallClicked(InstallationFormEvent evt) {
        int choice = JOptionPane.showConfirmDialog(
                (java.awt.Component) evt.getInstallationForm(),
                "Are you sure you want to uninstall " + appInfo().getTitle() + "?",
                "Confirm Uninstall",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            InstallationFormEvent proceedEvent = new InstallationFormEvent(InstallationFormEvent.Type.ProceedWithUninstallation);
            evt.getInstallationForm().getEventDispatcher().fireEvent(proceedEvent);
        }
    }

    private void onProceedWithUninstallation(InstallationFormEvent evt) {
        evt.setConsumed(true);
        evt.getInstallationForm().setInProgress(true, "Uninstalling. Please wait...");
        new Thread(() -> {
            try {
                performUninstall();
                invokeLater(() -> evt.getInstallationForm().setInProgress(false, ""));
                invokeLater(() -> evt.getInstallationForm().showUninstallCompleteDialog());
            } catch (Exception ex) {
                invokeLater(() -> evt.getInstallationForm().setInProgress(false, ""));
                ex.printStackTrace(System.err);
                String message = ex.getMessage();
                if (ex instanceof UserLangRuntimeException) {
                    UserLangRuntimeException userEx = (UserLangRuntimeException) ex;
                    if (userEx.hasUserFriendlyMessage()) {
                        message = userEx.getUserFriendlyMessage();
                    }
                }
                String finalMessage = message;
                invokeLater(() -> uiFactory.showModalErrorDialog(
                        evt.getInstallationForm(),
                        finalMessage,
                        "Uninstallation Failed"
                ));
            }
        }).start();
    }

    private void onUninstallCompleteQuit(InstallationFormEvent evt) {
        System.exit(0);
    }

    private void onProceedWithInstallation(InstallationFormEvent evt) {
        evt.setConsumed(true);
        proceedWithInstallationImpl(evt);
    }

    /**
     * Implementation of proceed with installation.
     */
    private void proceedWithInstallationImpl(InstallationFormEvent evt) {
        evt.getInstallationForm().setInProgress(true, "Installing.  Please wait...");

        // Create progress callback for service lifecycle management
        final InstallationForm form = evt.getInstallationForm();
        serviceLifecycleProgressCallback = new ca.weblite.jdeploy.installer.services.ServiceLifecycleProgressCallback() {
            @Override
            public void updateProgress(String message) {
                invokeLater(() -> form.setInProgress(true, message));
            }

            @Override
            public void reportWarning(String message) {
                System.err.println("Service Warning: " + message);
            }
        };

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
                invokeLater(()-> uiFactory.showModalErrorDialog(evt.getInstallationForm(), finalMessage, "Installation Failed"));
            } finally {
                serviceLifecycleProgressCallback = null;
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
        // Output suppression for headless mode is handled in main() via setupStaticHeadlessOutputSuppression()
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

        if (runAsBackgroundHelper) {
            invokeLater(()->runAsBackgroundHelperOnEdt());
            return;
        }
        invokeLater(()->{
            buildUI();
            installationForm.showInstallationForm();
        });

    }

    /**
     * Runs the application as a background helper with system tray menu.
     *
     * This mode shows only a system tray icon (no main window) for managing
     * application services. Delegates to BackgroundHelper for all functionality.
     */
    private void runAsBackgroundHelperOnEdt() {
        // Apply installation context to load application icon and other resources
        installationContext.applyContext(installationSettings);

        try {
            BackgroundHelper helper = new BackgroundHelper(installationSettings);
            helper.start();
        } catch (IllegalStateException e) {
            System.err.println("Cannot run as background helper: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Failed to start background helper: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void runHeadlessInstall() throws Exception {
        // Output suppression is handled by setupStaticHeadlessOutputSuppression() in main()
        // Use staticOriginalOut for user-facing messages

        staticOriginalOut.println(
            "Installing " + appInfo().getTitle() + " " + npmPackageVersion().getVersion() + "..."
        );

        try {
            install();
        } catch (Exception ex) {
            staticOriginalErr.println("Installation failed: " + ex.getMessage());
            if (staticLogFile != null) {
                staticOriginalErr.println("See log file for details: " + staticLogFile.getAbsolutePath());
            }
            throw ex;
        }

        staticOriginalOut.println("Installation complete");
    }

    private File installedApp;

    private void performUninstall() throws Exception {
        String packageName = appInfo().getNpmPackage();
        String source = appInfo().getNpmSource();

        // Stop and uninstall services before removing files
        stopAndUninstallServices(packageName, source);

        UninstallService uninstallService = createUninstallService();
        UninstallService.UninstallResult result = uninstallService.uninstall(packageName, source);

        if (!result.isSuccess()) {
            StringBuilder errorMessage = new StringBuilder("Uninstallation completed with errors:\n");
            for (String error : result.getErrors()) {
                errorMessage.append("- ").append(error).append("\n");
            }
            throw new Exception(errorMessage.toString());
        }
    }

    /**
     * Stops and uninstalls all services for a package before uninstallation.
     * This ensures services are cleanly removed before their files are deleted.
     *
     * @param packageName The package name
     * @param source The package source (e.g., "npm", "github", or null)
     */
    private void stopAndUninstallServices(String packageName, String source) {
        ServiceDescriptorService descriptorService = createServiceDescriptorService();
        // cliLauncherPath can be null since we don't need runApplicationUpdate for uninstall
        ServiceOperationExecutor operationExecutor = new ServiceOperationExecutor(null, packageName, source);

        try {
            List<ServiceDescriptor> services = descriptorService.listServices(packageName, source);

            for (ServiceDescriptor service : services) {
                String commandName = service.getCommandName();

                // Stop the service
                try {
                    System.out.println("Stopping service: " + commandName);
                    operationExecutor.stop(commandName);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to stop service " + commandName + ": " + e.getMessage());
                }

                // Uninstall the service
                try {
                    System.out.println("Uninstalling service: " + commandName);
                    operationExecutor.uninstall(commandName);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to uninstall service " + commandName + ": " + e.getMessage());
                }

                // Unregister the service descriptor
                try {
                    descriptorService.unregisterService(packageName, source, commandName, null);
                    System.out.println("Unregistered service: " + commandName);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to unregister service " + commandName + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to stop/uninstall services: " + e.getMessage());
        }
    }

    private UninstallService createUninstallService() {
        FileUninstallManifestRepository manifestRepository = new FileUninstallManifestRepository();
        RegistryOperations registryOperations = new JnaRegistryOperations();
        return new UninstallService(manifestRepository, registryOperations);
    }

    private ServiceDescriptorService createServiceDescriptorService() {
        return ServiceDescriptorServiceFactory.createDefault();
    }

    /**
     * Prepares services for update by assessing current state and stopping running services.
     * Implements phases 1-2 of the service lifecycle during updates.
     *
     * @param packageName The package name
     * @param newCommands The commands from the new version
     * @return Map of service states for post-installation restart
     */
    private Map<String, ca.weblite.jdeploy.installer.services.ServiceState> prepareServicesForUpdate(
            String packageName,
            List<CommandSpec> newCommands) {

        try {
            ServiceDescriptorService descriptorService = createServiceDescriptorService();
            File cliLauncherPath = findExistingCliLauncher(packageName);

            if (cliLauncherPath == null) {
                System.err.println("Warning: Could not find CLI launcher for service operations");
                return new HashMap<>();
            }

            ca.weblite.jdeploy.installer.services.ServiceOperationExecutor operationExecutor =
                new ca.weblite.jdeploy.installer.services.ServiceOperationExecutor(
                    cliLauncherPath,
                    packageName,
                    appInfo().getNpmSource()
                );

            ca.weblite.jdeploy.installer.services.ServiceLifecycleManager lifecycleManager =
                new ca.weblite.jdeploy.installer.services.ServiceLifecycleManager(
                    descriptorService,
                    operationExecutor,
                    serviceLifecycleProgressCallback
                );

            return lifecycleManager.prepareForUpdate(packageName, appInfo().getNpmSource(), newCommands);
        } catch (Exception e) {
            System.err.println("Warning: Service preparation failed: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * Terminates the Helper process before update to avoid "Text file busy" errors on Linux.
     *
     * On Linux, you cannot overwrite an executable that's currently running. The Helper
     * process holds the launcher file open, which would cause the update to fail.
     * By terminating the Helper before the installation, we ensure the file can be replaced.
     *
     * The Helper will be restarted after the update completes via updateHelperApplication().
     */
    private void terminateHelperBeforeUpdate() {
        try {
            String nameSuffix = "";
            if (appInfo().getNpmVersion() != null && appInfo().getNpmVersion().startsWith("0.0.0-")) {
                String v = appInfo().getNpmVersion();
                nameSuffix = " " + v.substring(v.indexOf("-") + 1).trim();
            }
            String appName = appInfo().getTitle() + nameSuffix;
            String packageName = appInfo().getNpmPackage();
            String source = appInfo().getNpmSource();

            HelperProcessManager processManager = new HelperProcessManager();

            if (processManager.isHelperRunning(packageName, source)) {
                System.out.println("Terminating Helper process before update...");
                boolean terminated = processManager.terminateHelper(packageName, source, appName, 5000);
                if (terminated) {
                    System.out.println("Helper process terminated successfully");
                } else {
                    System.err.println("Warning: Could not terminate Helper process, installation may fail");
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Error terminating Helper process: " + e.getMessage());
            // Continue with installation - the delete-before-copy fallback may still work
        }
    }

    /**
     * Completes services after update by running application update, installing services, and starting them.
     * Implements phases 4-6 of the service lifecycle during updates.
     *
     * @param packageName The package name
     * @param newCommands The commands from the new version
     * @param previousStates Service states from pre-installation
     * @param installedApp The installed application directory/file
     * @param version The version being installed
     */
    private void completeServicesAfterUpdate(
            String packageName,
            List<CommandSpec> newCommands,
            Map<String, ca.weblite.jdeploy.installer.services.ServiceState> previousStates,
            File installedApp,
            String version) {

        try {
            ServiceDescriptorService descriptorService = createServiceDescriptorService();
            File cliLauncherPath = findCliLauncherFromInstalledApp(installedApp);

            if (cliLauncherPath == null) {
                System.err.println("Warning: Could not find CLI launcher for service operations");
                return;
            }

            ca.weblite.jdeploy.installer.services.ServiceOperationExecutor operationExecutor =
                new ca.weblite.jdeploy.installer.services.ServiceOperationExecutor(
                    cliLauncherPath,
                    packageName,
                    appInfo().getNpmSource()
                );

            ca.weblite.jdeploy.installer.services.ServiceLifecycleManager lifecycleManager =
                new ca.weblite.jdeploy.installer.services.ServiceLifecycleManager(
                    descriptorService,
                    operationExecutor,
                    serviceLifecycleProgressCallback
                );

            lifecycleManager.completeUpdate(packageName, newCommands, previousStates, version);
        } catch (Exception e) {
            System.err.println("Warning: Service completion failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts commands that implement service_controller from a list of commands.
     *
     * @param commands The list of all commands
     * @return List of commands that implement service_controller
     */
    private List<CommandSpec> extractServiceCommands(List<CommandSpec> commands) {
        if (commands == null) {
            return Collections.emptyList();
        }

        List<CommandSpec> serviceCommands = new ArrayList<>();
        for (CommandSpec command : commands) {
            if (command.implements_("service_controller")) {
                serviceCommands.add(command);
            }
        }
        return serviceCommands;
    }

    /**
     * Checks if the app has CLI commands but no service commands.
     * Used to determine if explicit --jdeploy:update is needed after installation.
     *
     * @param allCommands All commands from package.json
     * @param serviceCommands Commands that implement service_controller
     * @return true if there are non-service commands but no service commands
     */
    private boolean hasCommandsButNoServices(List<CommandSpec> allCommands, List<CommandSpec> serviceCommands) {
        return allCommands != null && !allCommands.isEmpty()
               && (serviceCommands == null || serviceCommands.isEmpty());
    }

    /**
     * Runs --jdeploy:update for apps with commands but no services.
     * This ensures JARs are downloaded during installation rather than on first launch.
     *
     * Shows a progress dialog if the update takes longer than 1 second.
     * Once shown, dialog displays for minimum 2 seconds to avoid UI flashing.
     *
     * @param installedApp The installed application directory/file
     * @param version The version being installed
     * @throws Exception if the update fails
     */
    private void runUpdateForCommandsOnly(File installedApp, String version) throws Exception {
        File cliLauncherPath = findCliLauncherFromInstalledApp(installedApp);
        if (cliLauncherPath == null || !cliLauncherPath.exists()) {
            throw new RuntimeException("Could not find CLI launcher for update: " + cliLauncherPath);
        }

        ServiceOperationExecutor executor = new ServiceOperationExecutor(
            cliLauncherPath,
            appInfo().getNpmPackage(),
            appInfo().getNpmSource()
        );

        // In headless mode, run the update directly without GUI progress dialog
        if (headlessInstall) {
            ServiceOperationResult result = executor.runApplicationUpdate(version);
            if (result.isFailure()) {
                throw new RuntimeException(result.getMessage() != null
                    ? result.getMessage()
                    : "Application update failed");
            }
            return;
        }

        // Get parent window for dialog
        // DefaultInstallationForm extends JFrame, so it is itself a Window
        Window parentWindow = (installationForm instanceof Window) ? (Window) installationForm : null;

        UpdateProgressDialog progressDialog = new UpdateProgressDialog(parentWindow);

        progressDialog.runWithProgress(() -> {
            ServiceOperationResult result = executor.runApplicationUpdate(version);
            if (result.isFailure()) {
                throw new RuntimeException(result.getMessage());
            }
        });
    }

    /**
     * Finds the CLI launcher from the currently installed application.
     * Used before update to stop running services.
     *
     * Constructs the launcher path deterministically from package.json information
     * following the exact same logic as the installers.
     *
     * @param packageName The package name
     * @return The CLI launcher file, or null if not found
     */
    private File findExistingCliLauncher(String packageName) {
        try {
            // Compute fully qualified package name
            String source = appInfo().getNpmSource();
            String fullyQualifiedPackageName = packageName;
            if (source != null && !source.trim().isEmpty()) {
                String sourceHash = MD5.getMd5(source);
                fullyQualifiedPackageName = sourceHash + "." + packageName;
            }

            // Compute name suffix for branch versions
            String nameSuffix = "";
            if (appInfo().getNpmVersion() != null && appInfo().getNpmVersion().startsWith("0.0.0-")) {
                String v = appInfo().getNpmVersion();
                nameSuffix = " " + v.substring(v.indexOf("-") + 1).trim();
            }

            if (Platform.getSystemPlatform().isMac()) {
                // Mac: ~/Applications/{AppName}.app/Contents/MacOS/Client4JLauncher-cli
                String appName = appInfo().getTitle() + nameSuffix;
                File appsDir = new File(System.getProperty("user.home"), "Applications");
                File appBundle = new File(appsDir, appName + ".app");
                File launcher = new File(appBundle, "Contents/MacOS/" + ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_LAUNCHER_NAME);

                if (launcher.exists()) {
                    return launcher;
                }
            } else if (Platform.getSystemPlatform().isLinux()) {
                // Linux: ~/.jdeploy/apps/{fullyQualifiedPackageName}/{binaryName}
                String linuxNameSuffix = "";
                if (appInfo().getNpmVersion() != null && appInfo().getNpmVersion().startsWith("0.0.0-")) {
                    String v = appInfo().getNpmVersion();
                    linuxNameSuffix = "-" + v.substring(v.indexOf("-") + 1).trim();
                }
                String binaryName = deriveLinuxBinaryNameFromTitle(appInfo().getTitle()) + linuxNameSuffix;

                File appsDir = new File(System.getProperty("user.home"), ".jdeploy/apps");
                File appDir = new File(appsDir, fullyQualifiedPackageName);
                File launcher = new File(appDir, binaryName);

                if (launcher.exists()) {
                    return launcher;
                }
            } else if (Platform.getSystemPlatform().isWindows()) {
                // Windows: {winAppDir}/{fullyQualifiedPackageName}/{DisplayName}-cli.exe
                // Or: {winAppDir}/{fullyQualifiedPackageName}/bin/{DisplayName}-cli.exe (if using private JVM)
                String displayName = appInfo().getTitle() + nameSuffix;
                String cliExeName = displayName + ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_LAUNCHER_SUFFIX + ".exe";

                // Check configured winAppDir path first, then legacy path
                File[] appDirsToCheck = new File[] {
                    ca.weblite.jdeploy.installer.util.WindowsAppDirResolver.resolveAppDir(
                            installationSettings.getWinAppDir(), fullyQualifiedPackageName),
                    ca.weblite.jdeploy.installer.util.WindowsAppDirResolver.getLegacyAppDir(fullyQualifiedPackageName)
                };

                for (File appDir : appDirsToCheck) {
                    // Try without bin subdirectory first
                    File launcher = new File(appDir, cliExeName);
                    if (launcher.exists()) {
                        return launcher;
                    }

                    // Try with bin subdirectory (for private JVM installations)
                    File binDir = new File(appDir, "bin");
                    File launcherInBin = new File(binDir, cliExeName);
                    if (launcherInBin.exists()) {
                        return launcherInBin;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding existing CLI launcher: " + e.getMessage());
        }
        return null;
    }

    /**
     * Finds the CLI launcher from the newly installed application.
     * Used after update to start services.
     *
     * @param installedApp The installed application directory/file
     * @return The CLI launcher file, or null if not found
     */
    private File findCliLauncherFromInstalledApp(File installedApp) {
        if (installedApp == null) {
            return null;
        }

        try {
            if (Platform.getSystemPlatform().isMac()) {
                // Mac: installedApp is the .app bundle
                // CLI launcher is at Contents/MacOS/Client4JLauncher-cli
                File launcher = new File(installedApp, "Contents/MacOS/" + ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_LAUNCHER_NAME);
                if (launcher.exists()) {
                    return launcher;
                }
            } else if (Platform.getSystemPlatform().isLinux()) {
                // Linux: installedApp is the launcher executable itself
                if (installedApp.exists() && installedApp.isFile()) {
                    return installedApp;
                }
            } else if (Platform.getSystemPlatform().isWindows()) {
                // Windows: installedApp is the main GUI .exe file
                // CLI launcher is {Name}-cli.exe in the same directory
                if (installedApp.exists() && installedApp.getName().endsWith(".exe")) {
                    String cliExeName = installedApp.getName().replace(".exe",
                        ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_LAUNCHER_SUFFIX + ".exe");
                    File cliExePath = new File(installedApp.getParentFile(), cliExeName);
                    if (cliExePath.exists()) {
                        return cliExePath;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding CLI launcher from installed app: " + e.getMessage());
        }
        return null;
    }

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

        // Set packageName and source on InstallationSettings for CLI command bin directory resolution
        installationSettings.setPackageName(appInfo().getNpmPackage());
        installationSettings.setSource(appInfo().getNpmSource());

        // Prepare for service lifecycle management
        Map<String, ca.weblite.jdeploy.installer.services.ServiceState> serviceStateBeforeUpdate = null;
        List<CommandSpec> allCommands = null;
        List<CommandSpec> newServiceCommands = null;
        boolean isUpdate = false;
        if (!installationSettings.isBranchInstallation() && npmPackageVersion() != null) {
            allCommands = npmPackageVersion().getCommands();
            newServiceCommands = extractServiceCommands(allCommands);

            isUpdate = installationDetectionService.isInstalled(appInfo().getNpmPackage(), appInfo().getNpmSource());
            if (isUpdate) {
                // For updates: stop/uninstall existing services before installation
                serviceStateBeforeUpdate = prepareServicesForUpdate(appInfo().getNpmPackage(), newServiceCommands);

                // Terminate the Helper process before installation to avoid "Text file busy" on Linux.
                // The Helper holds the launcher file open, which prevents overwriting on Linux.
                terminateHelperBeforeUpdate();
            } else {
                // For initial installation: use empty map (no previous services to stop)
                serviceStateBeforeUpdate = new HashMap<>();
            }
        }

        // Enable CLI launcher creation if user requested CLI commands or launcher installation
        bundlerSettings.setCliCommandsEnabled(
            installationSettings.isInstallCliCommands()
        );

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

        // Configure JCEF frameworks if using JBR with JCEF variant
        JCEFConfigurer.configureJCEF(bundlerSettings, npmPackageVersion());

        Bundler.runit(bundlerSettings, appInfo(), findAppXmlFile().toURI().toURL().toString(), target, tmpBundles.getAbsolutePath(), tmpReleases.getAbsolutePath());

        if (Platform.getSystemPlatform().isWindows()) {
            installedApp = new InstallWindows().install(
                    installationContext,
                    installationSettings,
                    fullyQualifiedPackageName,
                    tmpBundles,
                    npmPackageVersion(),
                    new UIAwareCollisionHandler(uiFactory, installationForm)
            );
        } else if (Platform.getSystemPlatform().isMac()) {
            // Create installation logger for Mac
            InstallationLogger macLogger = null;
            try {
                macLogger = new InstallationLogger(fullyQualifiedPackageName, InstallationLogger.OperationType.INSTALL);
            } catch (IOException e) {
                System.err.println("Warning: Failed to create installation logger: " + e.getMessage());
            }
            final InstallationLogger macInstallLogger = macLogger;

            try {
                File jdeployAppsDir = new File(System.getProperty("user.home") + File.separator + "Applications");
                if (!jdeployAppsDir.exists()) {
                    jdeployAppsDir.mkdirs();
                    if (macInstallLogger != null) {
                        macInstallLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                                jdeployAppsDir.getAbsolutePath(), "User Applications directory");
                    }
                }
                String nameSuffix = "";
                if (appInfo().getNpmVersion().startsWith("0.0.0-")) {
                    nameSuffix = " " + appInfo().getNpmVersion().substring(appInfo().getNpmVersion().indexOf("-") + 1).trim();
                }

                String appName = appInfo().getTitle() + nameSuffix;
                File installAppPath = new File(jdeployAppsDir, appName+".app");
                if (installAppPath.exists() && installationSettings.isOverwriteApp()) {
                    FileUtils.deleteDirectory(installAppPath);
                    if (macInstallLogger != null) {
                        macInstallLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.DELETED,
                                installAppPath.getAbsolutePath(), "Existing app bundle (overwrite)");
                    }
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
                        if (macInstallLogger != null) {
                            macInstallLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                                    installAppPath.getAbsolutePath(), "Installed application bundle");
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
                File desktopAlias = null;

                if (appInfo().isRequireRunAsAdmin() || appInfo().isAllowRunAsAdmin()) {
                    MacAdminLauncherGenerator macAdminLauncherGenerator = new MacAdminLauncherGenerator();
                    adminWrapper  = macAdminLauncherGenerator.getAdminLauncherFile(installedApp);
                    if (adminWrapper.exists()) {
                        // delete the old recursively
                        FileUtils.deleteDirectory(adminWrapper);
                    }
                    adminWrapper = new MacAdminLauncherGenerator().generateAdminLauncher(installedApp);
                    if (macInstallLogger != null) {
                        macInstallLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                                adminWrapper.getAbsolutePath(), "Admin launcher wrapper");
                    }
                }

                if (installationSettings.isAddToDesktop()) {
                    desktopAlias = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + appName + ".app");
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
                    if (macInstallLogger != null) {
                        macInstallLogger.logShortcut(InstallationLogger.FileOperation.CREATED,
                                desktopAlias.getAbsolutePath(), targetPath);
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
                    if (macInstallLogger != null) {
                        macInstallLogger.logInfo("Added application to Dock: " + targetPath);
                    }
                }

                // Collect installation artifacts for manifest
                List<File> cliScriptFiles = new ArrayList<>();
                File cliLauncherSymlink = null;

                // Install CLI scripts/launchers on macOS (in ~/.local/bin) if the user requested either feature.
                if (installationSettings.isInstallCliCommands() || installationSettings.isInstallCliLauncher()) {
                    File cliLauncher = new File(installAppPath, "Contents" + File.separator + "MacOS" + File.separator + CliInstallerConstants.CLI_LAUNCHER_NAME);
                    if (!cliLauncher.exists()) {
                        File fallback = new File(installAppPath, "Contents" + File.separator + "MacOS" + File.separator + "Client4JLauncher");
                        if (fallback.exists()) {
                            cliLauncher = fallback;
                        }
                    }

                    List<CommandSpec> cliCommands = npmPackageVersion() != null ? npmPackageVersion().getCommands() : Collections.emptyList();
                    MacCliCommandInstaller macCliInstaller = new MacCliCommandInstaller();
                    // Wire collision handler for GUI-aware prompting
                    macCliInstaller.setCollisionHandler(new UIAwareCollisionHandler(uiFactory, installationForm));
                    macCliInstaller.setInstallationLogger(macInstallLogger);
                    macCliInstaller.setServiceDescriptorService(createServiceDescriptorService());
                    cliScriptFiles.addAll(macCliInstaller.installCommands(cliLauncher, cliCommands, installationSettings));

                    // Track the CLI launcher symlink if it was created
                    if (installationSettings.isInstallCliLauncher() && installationSettings.isCommandLineSymlinkCreated()) {
                        String commandName = deriveCommandName();
                        File binDir = new File(System.getProperty("user.home"), ".jdeploy" + File.separator + "bin-" + ArchitectureUtil.getArchitectureSuffix());
                        cliLauncherSymlink = new File(binDir, commandName);
                    }
                }

                // Build and persist uninstall manifest
                persistMacInstallationManifest(installAppPath, adminWrapper, desktopAlias,
                        cliScriptFiles, cliLauncherSymlink, appName);
            } finally {
                // Close the installation logger
                if (macInstallLogger != null) {
                    macInstallLogger.close();
                }
            }
        } else if (Platform.getSystemPlatform().isLinux()) {
            // Create installation logger for Linux
            InstallationLogger linuxLogger = null;
            try {
                linuxLogger = new InstallationLogger(fullyQualifiedPackageName, InstallationLogger.OperationType.INSTALL);
            } catch (IOException e) {
                System.err.println("Warning: Failed to create installation logger: " + e.getMessage());
            }
            final InstallationLogger linuxInstallLogger = linuxLogger;

            try {
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
                boolean appDirCreated = !appDir.exists();
                appDir.mkdirs();
                if (appDirCreated && linuxInstallLogger != null) {
                    linuxInstallLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                            appDir.getAbsolutePath(), "Application directory");
                }

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
                    // Delete existing file first to handle "Text file busy" on Linux.
                    // Linux allows deleting running executables - they keep running via inode,
                    // but the filename is freed for the new file.
                    if (exePath.exists()) {
                        exePath.delete();
                    }
                    FileUtil.copy(tmpExePath, exePath);
                    if (linuxInstallLogger != null) {
                        linuxInstallLogger.logFileOperation(InstallationLogger.FileOperation.CREATED,
                                exePath.getAbsolutePath(), "Application launcher");
                    }
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
                    if (linuxInstallLogger != null) {
                        linuxInstallLogger.logFileOperation(InstallationLogger.FileOperation.CREATED,
                                iconPath.getAbsolutePath(), "Application icon");
                    }
                }
                installLinuxMimetypes();

                installLinuxLinks(exePath, appInfo().getTitle() + titleSuffix, linuxInstallLogger);
                installedApp = exePath;
            } finally {
                // Close the installation logger
                if (linuxInstallLogger != null) {
                    linuxInstallLogger.close();
                }
            }
        }

        // Complete service lifecycle management OR run update for commands-only apps
        // This ensures JARs are downloaded during installation rather than on first launch
        if (!installationSettings.isBranchInstallation()) {
            if (newServiceCommands != null && !newServiceCommands.isEmpty()) {
                // Has services - use full service lifecycle (includes update)
                completeServicesAfterUpdate(
                    appInfo().getNpmPackage(),
                    newServiceCommands,
                    serviceStateBeforeUpdate,
                    installedApp,
                    npmPackageVersion().getVersion()
                );
            } else if (hasCommandsButNoServices(allCommands, newServiceCommands)) {
                // Has commands but no services - run explicit update to download JARs
                runUpdateForCommandsOnly(installedApp, npmPackageVersion().getVersion());
            }
        }

        // Install or update Helper application for command management
        // Skip for branch installations - Helper is only for production installs
        if (!installationSettings.isBranchInstallation()) {
            boolean commandsExist = allCommands != null && !allCommands.isEmpty();
            if (isUpdate) {
                // For updates: use HelperUpdateService which handles install/update
                // Always pass true to keep Helper installed (removal only happens during uninstall)
                updateHelperApplication(installedApp, true);
            } else if (commandsExist) {
                // For fresh installs with commands: install Helper
                installHelperApplication(installedApp);
            }
            // For fresh installs without commands: do nothing
        }

        // Install AI integrations if enabled
        if (installationSettings.isInstallAiIntegrations() &&
            installationSettings.hasAiIntegrations() &&
            !installationSettings.getSelectedAiTools().isEmpty()) {
            try {
                AiIntegrationInstallResult aiResult = installAiIntegrations(fullyQualifiedPackageName, installedApp);
                if (aiResult != null) {
                    installationSettings.setAiIntegrationResult(aiResult);
                    // Update manifest with AI integration entries
                    updateManifestWithAiIntegrations(aiResult);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to install AI integrations: " + e.getMessage());
                e.printStackTrace(System.err);
                // Continue - AI integrations are optional
            }
        }

        File tmpPlatformBundles = new File(tmpBundles, target);

    }

    private String deriveLinuxBinaryNameFromTitle(String title) {
        return title.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "");
    }

    /**
     * Installs the Helper application for service management.
     *
     * The Helper is a lightweight copy of the installer that provides:
     * - System tray icon for service status
     * - User-friendly uninstallation
     *
     * @param installedApp The main installed application file/directory
     */
    private void installHelperApplication(File installedApp) {
        String nameSuffix = "";
        if (appInfo().getNpmVersion() != null && appInfo().getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo().getNpmVersion();
            nameSuffix = " " + v.substring(v.indexOf("-") + 1).trim();
        }
        String appName = appInfo().getTitle() + nameSuffix;

        File appDirectory;
        if (Platform.getSystemPlatform().isMac()) {
            appDirectory = null; // macOS uses ~/Applications/{AppName} Helper/
        } else {
            appDirectory = installedApp.isDirectory() ? installedApp : installedApp.getParentFile();
        }

        File jdeployFilesDir = findJdeployFilesDirFromInstalledApp(installedApp);
        if (jdeployFilesDir == null) {
            jdeployFilesDir = installationContext.findInstallFilesDir();
        }

        if (jdeployFilesDir == null || !jdeployFilesDir.exists()) {
            System.err.println("Warning: Helper installation skipped - .jdeploy-files directory not found");
            return;
        }

        HelperInstallationService helperService = createHelperInstallationService();
        HelperInstallationResult result = helperService.installHelper(appName, appDirectory, jdeployFilesDir);

        if (result.isSuccess()) {
            System.out.println("Helper application installed successfully at: " + result.getHelperExecutable().getAbsolutePath());
            updateManifestWithHelper(result);
        } else {
            System.err.println("Warning: Helper installation failed: " + result.getErrorMessage());
            // Continue with installation - Helper is optional
        }
    }

    /**
     * Updates the uninstall manifest with Helper installation entries.
     *
     * This loads the existing manifest, adds the Helper entries, and saves it back.
     * If the manifest doesn't exist or cannot be updated, a warning is logged but
     * installation continues.
     *
     * @param helperResult The successful Helper installation result
     */
    private void updateManifestWithHelper(HelperInstallationResult helperResult) {
        try {
            FileUninstallManifestRepository repository = new FileUninstallManifestRepository();
            String packageName = appInfo().getNpmPackage();
            String packageSource = appInfo().getNpmSource();

            Optional<UninstallManifest> existingManifest = repository.load(packageName, packageSource);
            if (!existingManifest.isPresent()) {
                System.err.println("Warning: Could not load existing manifest to add Helper entries");
                return;
            }

            UninstallManifest manifest = existingManifest.get();
            UninstallManifestBuilder builder = new UninstallManifestBuilder();

            // Copy package info from existing manifest
            UninstallManifest.PackageInfo pkgInfo = manifest.getPackageInfo();
            builder.withPackageInfo(pkgInfo.getName(), pkgInfo.getSource(), pkgInfo.getVersion(), pkgInfo.getArchitecture());
            if (pkgInfo.getInstallerVersion() != null) {
                builder.withInstallerVersion(pkgInfo.getInstallerVersion());
            }

            // Copy existing files
            for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
                builder.addFile(file.getPath(), file.getType(), file.getDescription());
            }

            // Copy existing directories
            for (UninstallManifest.InstalledDirectory dir : manifest.getDirectories()) {
                builder.addDirectory(dir.getPath(), dir.getCleanup(), dir.getDescription());
            }

            // Copy existing registry entries (Windows)
            UninstallManifest.RegistryInfo registry = manifest.getRegistry();
            if (registry != null) {
                for (UninstallManifest.RegistryKey key : registry.getCreatedKeys()) {
                    builder.addCreatedRegistryKey(key.getRoot(), key.getPath(), key.getDescription());
                }
                for (UninstallManifest.ModifiedRegistryValue value : registry.getModifiedValues()) {
                    builder.addModifiedRegistryValue(
                            value.getRoot(), value.getPath(), value.getName(),
                            value.getPreviousValue(), value.getPreviousType(), value.getDescription());
                }
            }

            // Copy existing PATH modifications
            UninstallManifest.PathModifications pathMods = manifest.getPathModifications();
            if (pathMods != null) {
                for (UninstallManifest.WindowsPathEntry entry : pathMods.getWindowsPaths()) {
                    builder.addWindowsPathEntry(entry.getAddedEntry(), entry.getDescription());
                }
                for (UninstallManifest.ShellProfileEntry entry : pathMods.getShellProfiles()) {
                    builder.addShellProfileEntry(entry.getFile(), entry.getExportLine(), entry.getDescription());
                }
                for (UninstallManifest.GitBashProfileEntry entry : pathMods.getGitBashProfiles()) {
                    builder.addGitBashProfileEntry(entry.getFile(), entry.getExportLine(), entry.getDescription());
                }
            }

            // Copy existing AI integrations (in case they were installed before Helper)
            UninstallManifest.AiIntegrations existingAi = manifest.getAiIntegrations();
            if (existingAi != null) {
                for (UninstallManifest.McpServerEntry entry : existingAi.getMcpServers()) {
                    builder.addMcpServerEntry(entry.getConfigFile(), entry.getEntryKey(), entry.getToolName());
                }
                for (UninstallManifest.SkillEntry entry : existingAi.getSkills()) {
                    builder.addSkillEntry(entry.getPath(), entry.getName());
                }
                for (UninstallManifest.AgentEntry entry : existingAi.getAgents()) {
                    builder.addAgentEntry(entry.getPath(), entry.getName());
                }
            }

            // Add Helper entries
            HelperManifestHelper.addHelperToManifest(builder, helperResult);

            // Save updated manifest
            UninstallManifest updatedManifest = builder.build();
            repository.save(updatedManifest);

            System.out.println("Updated uninstall manifest with Helper entries");
        } catch (Exception e) {
            System.err.println("Warning: Failed to update manifest with Helper entries: " + e.getMessage());
            // Continue - Helper installation was successful, manifest update is best-effort
        }
    }

    /**
     * Updates the uninstall manifest with AI integration entries.
     *
     * This loads the existing manifest, adds the AI integration entries, and saves it back.
     * If the manifest doesn't exist or cannot be updated, a warning is logged but
     * installation continues.
     *
     * @param aiResult The AI integration installation result
     */
    private void updateManifestWithAiIntegrations(AiIntegrationInstallResult aiResult) {
        if (aiResult == null || aiResult.getManifestEntries().isEmpty()) {
            return;
        }

        try {
            FileUninstallManifestRepository repository = new FileUninstallManifestRepository();
            String packageName = appInfo().getNpmPackage();
            String packageSource = appInfo().getNpmSource();

            Optional<UninstallManifest> existingManifest = repository.load(packageName, packageSource);
            if (!existingManifest.isPresent()) {
                System.err.println("Warning: Could not load existing manifest to add AI integration entries");
                return;
            }

            UninstallManifest manifest = existingManifest.get();
            UninstallManifestBuilder builder = new UninstallManifestBuilder();

            // Copy package info from existing manifest
            UninstallManifest.PackageInfo pkgInfo = manifest.getPackageInfo();
            builder.withPackageInfo(pkgInfo.getName(), pkgInfo.getSource(), pkgInfo.getVersion(), pkgInfo.getArchitecture());
            if (pkgInfo.getInstallerVersion() != null) {
                builder.withInstallerVersion(pkgInfo.getInstallerVersion());
            }

            // Copy existing files
            for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
                builder.addFile(file.getPath(), file.getType(), file.getDescription());
            }

            // Copy existing directories
            for (UninstallManifest.InstalledDirectory dir : manifest.getDirectories()) {
                builder.addDirectory(dir.getPath(), dir.getCleanup(), dir.getDescription());
            }

            // Copy existing registry entries (Windows)
            UninstallManifest.RegistryInfo registry = manifest.getRegistry();
            if (registry != null) {
                for (UninstallManifest.RegistryKey key : registry.getCreatedKeys()) {
                    builder.addCreatedRegistryKey(key.getRoot(), key.getPath(), key.getDescription());
                }
                for (UninstallManifest.ModifiedRegistryValue value : registry.getModifiedValues()) {
                    builder.addModifiedRegistryValue(
                            value.getRoot(), value.getPath(), value.getName(),
                            value.getPreviousValue(), value.getPreviousType(), value.getDescription());
                }
            }

            // Copy existing PATH modifications
            UninstallManifest.PathModifications pathMods = manifest.getPathModifications();
            if (pathMods != null) {
                for (UninstallManifest.WindowsPathEntry entry : pathMods.getWindowsPaths()) {
                    builder.addWindowsPathEntry(entry.getAddedEntry(), entry.getDescription());
                }
                for (UninstallManifest.ShellProfileEntry entry : pathMods.getShellProfiles()) {
                    builder.addShellProfileEntry(entry.getFile(), entry.getExportLine(), entry.getDescription());
                }
                for (UninstallManifest.GitBashProfileEntry entry : pathMods.getGitBashProfiles()) {
                    builder.addGitBashProfileEntry(entry.getFile(), entry.getExportLine(), entry.getDescription());
                }
            }

            // Copy existing AI integrations (in case of updates)
            UninstallManifest.AiIntegrations existingAi = manifest.getAiIntegrations();
            if (existingAi != null) {
                for (UninstallManifest.McpServerEntry entry : existingAi.getMcpServers()) {
                    builder.addMcpServerEntry(entry.getConfigFile(), entry.getEntryKey(), entry.getToolName());
                }
                for (UninstallManifest.SkillEntry entry : existingAi.getSkills()) {
                    builder.addSkillEntry(entry.getPath(), entry.getName());
                }
                for (UninstallManifest.AgentEntry entry : existingAi.getAgents()) {
                    builder.addAgentEntry(entry.getPath(), entry.getName());
                }
            }

            // Add new AI integration entries
            for (AiIntegrationManifestEntry entry : aiResult.getManifestEntries()) {
                if (entry.isMcpServer()) {
                    builder.addMcpServerEntry(
                            entry.getConfigFilePath(),
                            entry.getEntryKey(),
                            entry.getToolType().name()
                    );
                } else if (entry.isSkill()) {
                    builder.addSkillEntry(entry.getPath(), entry.getName());
                } else if (entry.isAgent()) {
                    builder.addAgentEntry(entry.getPath(), entry.getName());
                }
            }

            // Save updated manifest
            UninstallManifest updatedManifest = builder.build();
            repository.save(updatedManifest);

            System.out.println("Updated uninstall manifest with AI integration entries");
        } catch (Exception e) {
            System.err.println("Warning: Failed to update manifest with AI integration entries: " + e.getMessage());
            // Continue - AI integration installation was successful, manifest update is best-effort
        }
    }

    /**
     * Installs AI integrations (MCP servers, skills, agents) to selected AI tools.
     *
     * @param fullyQualifiedPackageName The FQPN used as the MCP server name
     * @param installedApp The installed application file/directory
     * @return The installation result, or null if installation was skipped
     */
    private AiIntegrationInstallResult installAiIntegrations(String fullyQualifiedPackageName, File installedApp) {
        AiIntegrationInstaller aiInstaller = new AiIntegrationInstaller();

        // Determine the binary command based on platform
        String binaryCommand = getBinaryCommandForAiIntegration(installedApp);
        if (binaryCommand == null) {
            System.err.println("Warning: Could not determine binary command for AI integrations");
            return null;
        }

        // Get the MCP command name from config
        String mcpCommandName = null;
        if (installationSettings.getAiIntegrationConfig() != null &&
            installationSettings.getAiIntegrationConfig().getMcpConfig() != null) {
            mcpCommandName = installationSettings.getAiIntegrationConfig().getMcpConfig().getCommand();
        }

        if (mcpCommandName == null) {
            System.err.println("Warning: No MCP command configured for AI integrations");
            return null;
        }

        // Get bundle's ai directory
        File bundleAiDir = new File(installationSettings.getInstallFilesDir(), "ai");

        try {
            AiIntegrationInstallResult result = aiInstaller.install(
                    installationSettings.getAiIntegrationConfig(),
                    installationSettings.getSelectedAiTools(),
                    binaryCommand,
                    mcpCommandName,
                    fullyQualifiedPackageName,
                    appInfo().getTitle(),
                    bundleAiDir
            );

            // TODO: Add manifest entries to uninstall manifest in Phase 6
            System.out.println("AI integrations installed: " + result.getManifestEntries().size() + " entries");

            // Log any conflicts
            if (result.hasConflicts()) {
                System.err.println("AI integration conflicts:");
                for (AiIntegrationInstallResult.McpServerConflict conflict : result.getConflicts()) {
                    System.err.println("  - " + conflict);
                }
            }

            return result;
        } catch (IOException e) {
            System.err.println("Warning: Failed to install AI integrations: " + e.getMessage());
            e.printStackTrace(System.err);
            return null;
        }
    }

    /**
     * Gets the binary command to use for AI integration invocations.
     * Uses the CLI launcher binary which avoids GUI initialization.
     *
     * Platform-specific binaries (per cli-commands-in-installer.md RFC):
     * - macOS: {AppName}.app/Contents/MacOS/Client4JLauncher-cli
     * - Windows: {AppDir}/{AppName}-cli.exe
     * - Linux: {AppDir}/{binary} (same as GUI binary)
     *
     * @param installedApp The installed application
     * @return The binary command path, or null if not determined
     */
    private String getBinaryCommandForAiIntegration(File installedApp) {
        if (installedApp == null || !installedApp.exists()) {
            return null;
        }

        if (Platform.getSystemPlatform().isMac()) {
            // macOS: Use Client4JLauncher-cli inside the app bundle
            // This avoids GUI initialization triggered by AppKit/NSApplication
            return installedApp.getAbsolutePath() + "/Contents/MacOS/" + CliInstallerConstants.CLI_LAUNCHER_NAME;
        } else if (Platform.getSystemPlatform().isWindows()) {
            // Windows: Use {AppName}-cli.exe which has Console subsystem instead of GUI
            String appName = installedApp.getName();
            if (appName.endsWith(".exe")) {
                appName = appName.substring(0, appName.length() - 4);
            }
            return installedApp.getParent() + File.separator + appName + "-cli.exe";
        } else if (Platform.getSystemPlatform().isLinux()) {
            // Linux: Use the installed binary directly
            return installedApp.getAbsolutePath();
        }
        return null;
    }

    /**
     * Finds the .jdeploy-files directory from the installed application.
     *
     * @param installedApp The installed application file/directory
     * @return The .jdeploy-files directory, or null if not found
     */
    private File findJdeployFilesDirFromInstalledApp(File installedApp) {
        if (installedApp == null) {
            return null;
        }

        if (Platform.getSystemPlatform().isMac()) {
            // macOS: search inside the .app bundle
            return findJdeployFilesRecursive(installedApp, 5);
        } else {
            // Windows/Linux: .jdeploy-files is in the app directory
            File appDir = installedApp.isDirectory() ? installedApp : installedApp.getParentFile();
            File jdeployFiles = new File(appDir, ".jdeploy-files");
            return jdeployFiles.exists() ? jdeployFiles : null;
        }
    }

    /**
     * Recursively searches for .jdeploy-files directory within a given directory.
     *
     * @param dir The directory to search in
     * @param maxDepth Maximum depth to search
     * @return The .jdeploy-files directory, or null if not found
     */
    private File findJdeployFilesRecursive(File dir, int maxDepth) {
        if (dir == null || !dir.isDirectory() || maxDepth <= 0) {
            return null;
        }

        File candidate = new File(dir, ".jdeploy-files");
        if (candidate.exists() && candidate.isDirectory()) {
            return candidate;
        }

        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    File found = findJdeployFilesRecursive(child, maxDepth - 1);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Creates a HelperInstallationService with proper dependencies.
     *
     * @return A new HelperInstallationService instance
     */
    private HelperInstallationService createHelperInstallationService() {
        InstallationLogger logger = null;
        try {
            String fullyQualifiedPackageName = appInfo().getNpmPackage();
            if (appInfo().getNpmSource() != null && !appInfo().getNpmSource().isEmpty()) {
                String sourceHash = MD5.getMd5(appInfo().getNpmSource());
                fullyQualifiedPackageName = sourceHash + "." + fullyQualifiedPackageName;
            }
            logger = new InstallationLogger(fullyQualifiedPackageName, InstallationLogger.OperationType.INSTALL);
        } catch (IOException e) {
            System.err.println("Warning: Failed to create installation logger for Helper: " + e.getMessage());
        }
        return new HelperInstallationService(logger, new HelperCopyService(logger));
    }

    /**
     * Creates a HelperUpdateService with proper dependencies.
     *
     * @return A new HelperUpdateService instance
     */
    private HelperUpdateService createHelperUpdateService() {
        InstallationLogger logger = null;
        try {
            String fullyQualifiedPackageName = appInfo().getNpmPackage();
            if (appInfo().getNpmSource() != null && !appInfo().getNpmSource().isEmpty()) {
                String sourceHash = MD5.getMd5(appInfo().getNpmSource());
                fullyQualifiedPackageName = sourceHash + "." + fullyQualifiedPackageName;
            }
            logger = new InstallationLogger(fullyQualifiedPackageName, InstallationLogger.OperationType.INSTALL);
        } catch (IOException e) {
            System.err.println("Warning: Failed to create installation logger for Helper update: " + e.getMessage());
        }
        HelperInstallationService installationService = new HelperInstallationService(logger, new HelperCopyService(logger));
        HelperProcessManager processManager = new HelperProcessManager();
        return new HelperUpdateService(installationService, processManager, logger);
    }

    /**
     * Updates the Helper application during an application update.
     *
     * Handles two scenarios:
     * <ul>
     *   <li>Helper exists: Update Helper (terminate, delete, reinstall)</li>
     *   <li>Helper doesn't exist: Install Helper</li>
     * </ul>
     *
     * Note: Helper removal only occurs during uninstall, not during updates.
     * This method is always called with keepHelper=true.
     *
     * @param installedApp The main installed application file/directory
     * @param keepHelper True to keep/install Helper (always true for updates)
     */
    private void updateHelperApplication(File installedApp, boolean keepHelper) {
        String nameSuffix = "";
        if (appInfo().getNpmVersion() != null && appInfo().getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo().getNpmVersion();
            nameSuffix = " " + v.substring(v.indexOf("-") + 1).trim();
        }
        String appName = appInfo().getTitle() + nameSuffix;

        // Get package info for lock file identification
        String packageName = appInfo().getNpmPackage();
        String source = appInfo().getNpmSource();

        File appDirectory;
        if (Platform.getSystemPlatform().isMac()) {
            appDirectory = null; // macOS uses ~/Applications/{AppName} Helper/
        } else {
            appDirectory = installedApp.isDirectory() ? installedApp : installedApp.getParentFile();
        }

        File jdeployFilesDir = findJdeployFilesDirFromInstalledApp(installedApp);
        if (jdeployFilesDir == null) {
            jdeployFilesDir = installationContext.findInstallFilesDir();
        }

        // Helper is always kept during updates (removal only happens during uninstall)
        if (jdeployFilesDir == null || !jdeployFilesDir.exists()) {
            System.err.println("Warning: Helper update skipped - .jdeploy-files directory not found");
            return;
        }

        HelperUpdateService updateService = createHelperUpdateService();
        HelperUpdateService.HelperUpdateResult result = updateService.updateHelper(
                packageName, source, appName, appDirectory, jdeployFilesDir, keepHelper);

        if (result.isSuccess()) {
            switch (result.getType()) {
                case INSTALLED:
                    System.out.println("Helper application installed successfully");
                    updateManifestWithHelper(result.getInstallationResult());
                    break;
                case UPDATED:
                    System.out.println("Helper application updated successfully");
                    updateManifestWithHelper(result.getInstallationResult());
                    break;
                case REMOVED:
                    System.out.println("Helper application removed (services no longer defined)");
                    // Helper was removed, no manifest update needed for adding entries
                    // The Helper entries will be cleaned up when uninstall runs
                    break;
                case NO_ACTION:
                    System.out.println("No Helper update action needed");
                    break;
                default:
                    break;
            }
        } else {
            System.err.println("Warning: Helper update failed: " + result.getErrorMessage());
            // Continue with installation - Helper is optional
        }
    }

    /**
     * Persists the uninstall manifest for a macOS installation.
     * Collects all installed artifacts and writes them to the manifest.
     *
     * @param appBundle       The installed .app bundle directory
     * @param adminWrapper    The admin launcher wrapper if created (may be null)
     * @param desktopAlias    The desktop alias if created (may be null)
     * @param cliScriptFiles  List of CLI script files installed
     * @param cliLauncherSymlink The CLI launcher symlink if created (may be null)
     * @param appName         The application name (for descriptions)
     */
    private void persistMacInstallationManifest(File appBundle, File adminWrapper, File desktopAlias,
                                                List<File> cliScriptFiles, File cliLauncherSymlink,
                                                String appName) {
        try {
            // Create builder with package info
            UninstallManifestBuilder builder = new UninstallManifestBuilder();
            
            String packageName = appInfo().getNpmPackage();
            String packageSource = appInfo().getNpmSource();
            String packageVersion = npmPackageVersion() != null ? npmPackageVersion().getVersion() : appInfo().getNpmVersion();
            String arch = ArchitectureUtil.getArchitecture();
            
            builder.withPackageInfo(packageName, packageSource, packageVersion, arch);
            
            // Set installer version
            String launcherVersion = appInfo().getLauncherVersion();
            if (launcherVersion != null && !launcherVersion.isEmpty()) {
                builder.withInstallerVersion(launcherVersion);
            }
            
            // Add .app bundle as installed directory (always delete completely)
            if (appBundle != null && appBundle.exists()) {
                builder.addDirectory(appBundle.getAbsolutePath(), 
                        UninstallManifest.CleanupStrategy.ALWAYS,
                        "Installed application bundle: " + appName + ".app");
            }
            
            // Add admin wrapper if created
            if (adminWrapper != null && adminWrapper.exists()) {
                builder.addDirectory(adminWrapper.getAbsolutePath(),
                        UninstallManifest.CleanupStrategy.ALWAYS,
                        "Admin launcher wrapper for " + appName);
            }
            
            // Add desktop alias if created
            if (desktopAlias != null && desktopAlias.exists()) {
                builder.addFile(desktopAlias.getAbsolutePath(),
                        UninstallManifest.FileType.LINK,
                        "Desktop alias for " + appName);
            }
            
            // Add CLI script files
            if (cliScriptFiles != null && !cliScriptFiles.isEmpty()) {
                for (File scriptFile : cliScriptFiles) {
                    if (scriptFile.exists()) {
                        String fileType = scriptFile.isDirectory() ? 
                                "Directory for CLI scripts" : 
                                "CLI command script";
                        builder.addFile(scriptFile.getAbsolutePath(),
                                UninstallManifest.FileType.SCRIPT,
                                fileType);
                    }
                }
            }
            
            // Add CLI launcher symlink if created
            if (cliLauncherSymlink != null && cliLauncherSymlink.exists()) {
                builder.addFile(cliLauncherSymlink.getAbsolutePath(),
                        UninstallManifest.FileType.LINK,
                        "CLI launcher symlink for " + appName);
            }
            
            // Add bin directory to cleanup if CLI scripts were installed
            if ((cliScriptFiles != null && !cliScriptFiles.isEmpty()) || cliLauncherSymlink != null) {
                String binDirPath = System.getProperty("user.home") + File.separator + ".jdeploy" + 
                        File.separator + "bin-" + ArchitectureUtil.getArchitectureSuffix();
                builder.addDirectory(binDirPath,
                        UninstallManifest.CleanupStrategy.IF_EMPTY,
                        "CLI bin directory for macOS");
            }
            
            // Add shell profile PATH modifications if PATH was updated
            // The CLI installer adds the app-specific bin directory to PATH, so we need to check for that
            if (installationSettings.isAddedToPath()) {
                String userHome = System.getProperty("user.home");
                File homeDir = new File(userHome);

                // Get the app-specific bin directory that was actually added to PATH by the CLI installer
                File appBinDir = ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver.getPerAppBinDir(
                        packageName, packageSource, homeDir);

                // Construct the PATH export line using $HOME format (same as UnixPathManager)
                String pathWithHome = computePathWithHome(appBinDir, homeDir);
                String pathExportLine = "export PATH=\"" + pathWithHome + ":$PATH\"";

                // Check and add bash profile entries if they exist and contain the PATH modification
                // UnixPathManager modifies both .bash_profile and .bashrc for bash
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".bashrc",
                        pathExportLine, "PATH modification for bash");
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".bash_profile",
                        pathExportLine, "PATH modification for bash profile");

                // Check and add zsh config entry
                // UnixPathManager uses .zshrc for zsh (not .zprofile)
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".zshrc",
                        pathExportLine, "PATH modification for zsh");

                // Check and add .profile for other shells (fish, etc.)
                // UnixPathManager uses .profile for fish and other non-standard shells
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".profile",
                        pathExportLine, "PATH modification for other shells");
            }
            
            // Build and write manifest
            UninstallManifest manifest = builder.build();
            UninstallManifestWriter writer = new UninstallManifestWriter();
            File manifestFile = writer.write(manifest);
            
            System.out.println("Uninstall manifest written to: " + manifestFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to write uninstall manifest: " + e.getMessage());
            e.printStackTrace(System.err);
            throw new RuntimeException("Failed to write uninstall manifest", e);
        }
    }

    /**
     * Persists the uninstall manifest for a Linux installation.
     * Collects all installed artifacts and writes them to the manifest.
     *
     * @param launcherFile        The installed launcher executable
     * @param iconFile            The icon file (icon.png)
     * @param desktopFiles        List of desktop .desktop files created on Desktop
     * @param applicationsFiles   List of .desktop files created in ~/.local/share/applications
     * @param adminLauncher       The admin launcher wrapper if created (may be null)
     * @param cliScriptFiles      List of CLI script files installed
     * @param appName             The application name (for descriptions)
     */
    private void persistLinuxInstallationManifest(File launcherFile, File iconFile,
                                                  List<File> desktopFiles, List<File> applicationsFiles,
                                                  File adminLauncher, List<File> cliScriptFiles,
                                                  String appName) {
        try {
            // Create builder with package info
            UninstallManifestBuilder builder = new UninstallManifestBuilder();
            
            String packageName = appInfo().getNpmPackage();
            String packageSource = appInfo().getNpmSource();
            String packageVersion = npmPackageVersion() != null ? npmPackageVersion().getVersion() : appInfo().getNpmVersion();
            String arch = ArchitectureUtil.getArchitecture();
            
            builder.withPackageInfo(packageName, packageSource, packageVersion, arch);
            
            // Set installer version
            String launcherVersion = appInfo().getLauncherVersion();
            if (launcherVersion != null && !launcherVersion.isEmpty()) {
                builder.withInstallerVersion(launcherVersion);
            }
            
            // Add launcher executable as installed file
            if (launcherFile != null && launcherFile.exists()) {
                builder.addFile(launcherFile.getAbsolutePath(),
                        UninstallManifest.FileType.BINARY,
                        "Application launcher executable: " + appName);
            }
            
            // Add icon file
            if (iconFile != null && iconFile.exists()) {
                builder.addFile(iconFile.getAbsolutePath(),
                        UninstallManifest.FileType.ICON,
                        "Application icon: " + appName);
            }
            
            // Add desktop .desktop files if created
            if (desktopFiles != null && !desktopFiles.isEmpty()) {
                for (File desktopFile : desktopFiles) {
                    if (desktopFile.exists()) {
                        builder.addFile(desktopFile.getAbsolutePath(),
                                UninstallManifest.FileType.CONFIG,
                                "Desktop shortcut for " + appName);
                    }
                }
            }
            
            // Add applications directory .desktop files if created
            if (applicationsFiles != null && !applicationsFiles.isEmpty()) {
                for (File appFile : applicationsFiles) {
                    if (appFile.exists()) {
                        builder.addFile(appFile.getAbsolutePath(),
                                UninstallManifest.FileType.CONFIG,
                                "Applications menu entry for " + appName);
                    }
                }
            }
            
            // Add admin launcher if created
            if (adminLauncher != null && adminLauncher.exists()) {
                builder.addFile(adminLauncher.getAbsolutePath(),
                        UninstallManifest.FileType.SCRIPT,
                        "Admin launcher wrapper for " + appName);
            }
            
            // Add CLI script files
            if (cliScriptFiles != null && !cliScriptFiles.isEmpty()) {
                for (File scriptFile : cliScriptFiles) {
                    if (scriptFile.exists()) {
                        builder.addFile(scriptFile.getAbsolutePath(),
                                UninstallManifest.FileType.SCRIPT,
                                "CLI command script or symlink for " + appName);
                    }
                }
            }
            
            // Add application directory to cleanup if it exists and is empty
            File appDir = launcherFile.getParentFile();
            if (appDir != null && appDir.exists()) {
                builder.addDirectory(appDir.getAbsolutePath(),
                        UninstallManifest.CleanupStrategy.IF_EMPTY,
                        "Application installation directory");
            }
            
            // Add bin directory to cleanup if CLI scripts/launcher were installed
            if ((cliScriptFiles != null && !cliScriptFiles.isEmpty()) || installationSettings.isCommandLineSymlinkCreated()) {
                String binDirPath = System.getProperty("user.home") + File.separator + ".local" + 
                        File.separator + "bin";
                builder.addDirectory(binDirPath,
                        UninstallManifest.CleanupStrategy.IF_EMPTY,
                        "CLI bin directory for Linux");
            }
            
            // Add shell profile PATH modifications if PATH was updated
            // Linux uses a shared ~/.local/bin directory for all apps
            if (installationSettings.isAddedToPath()) {
                String userHome = System.getProperty("user.home");
                File homeDir = new File(userHome);
                File localBin = new File(homeDir, ".local" + File.separator + "bin");

                // Use $HOME format for the path (same as UnixPathManager)
                String pathWithHome = computePathWithHome(localBin, homeDir);
                String pathExportLine = "export PATH=\"" + pathWithHome + ":$PATH\"";

                // Check and add bash profile entries if they exist and contain the PATH modification
                // UnixPathManager modifies both .bash_profile and .bashrc for bash
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".bashrc",
                        pathExportLine, "PATH modification for bash");
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".bash_profile",
                        pathExportLine, "PATH modification for bash profile");

                // Check and add zsh config entry
                // UnixPathManager uses .zshrc for zsh (not .zprofile)
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".zshrc",
                        pathExportLine, "PATH modification for zsh");

                // Check and add .profile for other shells (fish, etc.)
                // UnixPathManager uses .profile for fish and other non-standard shells
                addShellProfileEntryIfExists(builder, userHome + File.separator + ".profile",
                        pathExportLine, "PATH modification for other shells");
            }
            
            // Add mimetype registrations if document types were installed
            if (appInfo().hasDocumentTypes()) {
                // Add a metadata file noting mimetype registrations
                String mimetypeData = "Mimetypes registered for: " + appName;
                if (appInfo().hasDocumentTypes()) {
                    StringBuilder extensions = new StringBuilder();
                    for (String ext : appInfo().getExtensions()) {
                        if (extensions.length() > 0) extensions.append(", ");
                        String mimetype = appInfo().getMimetype(ext);
                        extensions.append(ext).append(" (").append(mimetype).append(")");
                    }
                    mimetypeData += "\nExtensions: " + extensions.toString();
                }
                // Note: Mimetype registrations are handled via MimeTypeHelper, which modifies system files
                // These are tracked for informational purposes in the manifest
            }
            
            // Build and write manifest
            UninstallManifest manifest = builder.build();
            UninstallManifestWriter writer = new UninstallManifestWriter();
            File manifestFile = writer.write(manifest);
            
            System.out.println("Uninstall manifest written to: " + manifestFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to write uninstall manifest: " + e.getMessage());
            e.printStackTrace(System.err);
            throw new RuntimeException("Failed to write uninstall manifest", e);
        }
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

    private File addLinuxDesktopFile(File desktopDir, String filePrefix, String title, File pngIcon, File launcherFile) throws IOException {
        File createdDesktopFile = null;
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
            createdDesktopFile = desktopFile;
        }
        return createdDesktopFile;
    }

    public void installLinuxLinks(File launcherFile, String title) throws Exception {
        installLinuxLinks(launcherFile, title, null);
    }

    public void installLinuxLinks(File launcherFile, String title, InstallationLogger linuxInstallLogger) throws Exception {
        if (!launcherFile.exists()) {
            throw new IllegalStateException("Launcher "+launcherFile+" does not exist so we cannot install a shortcut to it.");
        }

        launcherFile.setExecutable(true, false);

        File pngIcon = new File(launcherFile.getParentFile(), "icon.png");
        if (!pngIcon.exists()) {
            IOUtil.copyResourceToFile(Main.class, "icon.png", pngIcon);
            if (linuxInstallLogger != null) {
                linuxInstallLogger.logFileOperation(InstallationLogger.FileOperation.CREATED,
                        pngIcon.getAbsolutePath(), "Default application icon");
            }
        }

        boolean hasDesktop = isDesktopEnvironmentAvailable();

        // Track artifacts for uninstall manifest
        List<File> desktopFiles = new ArrayList<>();
        List<File> applicationsDesktopFiles = new ArrayList<>();
        File adminLauncherFile = null;
        List<File> cliScriptFiles = new ArrayList<>();

        // Install desktop shortcuts only if desktop environment is available
        if (hasDesktop) {
            if (installationSettings.isAddToDesktop()) {
                File desktopDir = new File(System.getProperty("user.home"), "Desktop");
                File desktopFile = addLinuxDesktopFile(desktopDir, title, title, pngIcon, launcherFile);
                if (desktopFile != null) {
                    desktopFiles.add(desktopFile);
                    if (linuxInstallLogger != null) {
                        linuxInstallLogger.logShortcut(InstallationLogger.FileOperation.CREATED,
                                desktopFile.getAbsolutePath(), launcherFile.getAbsolutePath());
                    }
                }
            }
            if (installationSettings.isAddToPrograms()) {
                File homeDir = new File(System.getProperty("user.home"));
                File applicationsDir = new File(homeDir, ".local"+File.separator+"share"+File.separator+"applications");
                boolean appDirCreated = !applicationsDir.exists();
                applicationsDir.mkdirs();
                if (appDirCreated && linuxInstallLogger != null) {
                    linuxInstallLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                            applicationsDir.getAbsolutePath(), "Applications directory");
                }
                File desktopFile = addLinuxDesktopFile(applicationsDir, title, title, pngIcon, launcherFile);
                if (desktopFile != null) {
                    applicationsDesktopFiles.add(desktopFile);
                    if (linuxInstallLogger != null) {
                        linuxInstallLogger.logShortcut(InstallationLogger.FileOperation.CREATED,
                                desktopFile.getAbsolutePath(), launcherFile.getAbsolutePath());
                    }
                }

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
            if (linuxInstallLogger != null) {
                linuxInstallLogger.logInfo("No desktop environment detected - skipping desktop shortcuts");
            }
        }

        // Install command-line scripts and/or symlink in ~/.local/bin if user requested either feature
        if (installationSettings.isInstallCliCommands() || installationSettings.isInstallCliLauncher()) {
            List<CommandSpec> commands = npmPackageVersion() != null ? npmPackageVersion().getCommands() : Collections.emptyList();
            LinuxCliCommandInstaller linuxCliInstaller = new LinuxCliCommandInstaller();
            // Wire collision handler for GUI-aware prompting
            linuxCliInstaller.setCollisionHandler(new UIAwareCollisionHandler(uiFactory, installationForm));
            linuxCliInstaller.setInstallationLogger(linuxInstallLogger);
            linuxCliInstaller.setServiceDescriptorService(createServiceDescriptorService());
            cliScriptFiles.addAll(linuxCliInstaller.installCommands(launcherFile, commands, installationSettings));
        } else {
            System.out.println("Skipping CLI command and launcher installation (user opted out)");
            if (linuxInstallLogger != null) {
                linuxInstallLogger.logInfo("CLI command and launcher installation skipped (user opted out)");
            }
        }

        // Persist uninstall manifest with all collected artifacts
        persistLinuxInstallationManifest(launcherFile, pngIcon, desktopFiles, applicationsDesktopFiles,
                adminLauncherFile, cliScriptFiles, title);
    }

    /**
     * Converts an absolute path to use $HOME if it's under the home directory.
     * This matches the format used by UnixPathManager.
     *
     * @param path     The file path to convert
     * @param homeDir  The user's home directory
     * @return The path with $HOME prefix if applicable, otherwise the absolute path
     */
    private String computePathWithHome(File path, File homeDir) {
        String homePath = homeDir.getAbsolutePath();
        String absolutePath = path.getAbsolutePath();

        if (absolutePath.startsWith(homePath)) {
            // Remove homeDir prefix and leading separator
            String relativePath = absolutePath.substring(homePath.length());
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            return "$HOME/" + relativePath.replace(File.separatorChar, '/');
        }
        return absolutePath;
    }

    /**
     * Helper method to add a shell profile entry to the manifest if the file exists
     * and contains the specified export line.
     * This will include entries even if they were added in a previous installation,
     * ensuring the uninstaller can clean them up.
     *
     * @param builder       The UninstallManifestBuilder to add the entry to
     * @param filePath      The absolute path to the shell profile file
     * @param exportLine    The PATH export line to look for
     * @param description   Description for the manifest entry
     */
    private void addShellProfileEntryIfExists(UninstallManifestBuilder builder, String filePath,
                                               String exportLine, String description) {
        File file = new File(filePath);

        // Only add if file exists
        if (!file.exists()) {
            return;
        }

        // Check if the file contains the export line (from any installation)
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            if (content.contains(exportLine)) {
                builder.addShellProfileEntry(filePath, exportLine, description);
            }
        } catch (java.io.IOException e) {
            // If we can't read the file, don't add it to the manifest
            System.err.println("Warning: Could not read " + filePath + " to verify PATH modification: " + e.getMessage());
        }
    }

}
