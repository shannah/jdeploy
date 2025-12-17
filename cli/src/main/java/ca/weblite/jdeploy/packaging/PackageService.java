package ca.weblite.jdeploy.packaging;

import ca.weblite.jdeploy.BundleConstants;
import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.JVMSpecification;
import ca.weblite.jdeploy.app.permissions.PermissionRequest;
import ca.weblite.jdeploy.app.permissions.PermissionRequestService;
import ca.weblite.jdeploy.appbundler.*;
import ca.weblite.jdeploy.appbundler.mac.DmgCreator;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.helpers.PrereleaseHelper;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.ProjectBuilderService;
import ca.weblite.jdeploy.services.VersionCleaner;
import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.XMLUtil;
import ca.weblite.tools.platform.Platform;
import com.codename1.processing.Result;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.w3c.dom.Document;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ca.weblite.jdeploy.PathUtil.fromNativePath;
import static ca.weblite.jdeploy.PathUtil.toNativePath;

@Singleton
public class PackageService implements BundleConstants {

    private static final int DEFAULT_JAVA_VERSION = 11;

    private final Environment environment;

    private final JarFinder jarFinder;

    private final ClassPathFinder classPathFinder;

    private final CompressionService compressionService;

    private final BundleCodeService bundleCodeService;

    private final CopyJarRuleBuilder copyJarRuleBuilder;

    private final ProjectBuilderService projectBuilderService;

    private final PackagingConfig packagingConfig;

    private final PermissionRequestService permissionRequestService;

    @Inject
    public PackageService(
            Environment environment,
            JarFinder jarFinder,
            ClassPathFinder classPathFinder,
            CompressionService compressionService,
            BundleCodeService bundleCodeService,
            CopyJarRuleBuilder copyJarRuleBuilder,
            ProjectBuilderService projectBuilderService,
            PackagingConfig packagingConfig,
            PermissionRequestService permissionRequestService
    ) {
        this.environment = environment;
        this.jarFinder = jarFinder;
        this.classPathFinder = classPathFinder;
        this.compressionService = compressionService;
        this.bundleCodeService = bundleCodeService;
        this.copyJarRuleBuilder = copyJarRuleBuilder;
        this.projectBuilderService = projectBuilderService;
        this.packagingConfig = packagingConfig;
        this.permissionRequestService = permissionRequestService;
    }

    public void createJdeployBundle(
            PackagingContext context
    ) throws IOException {
        createJdeployBundle(context, new BundlerSettings());
    }

    public void createJdeployBundle(
            PackagingContext context,
            BundlerSettings bundlerSettings
        ) throws IOException {
        if (isBuildRequired(context)) {
            if (projectBuilderService.isBuildSupported(context)) {
                context.out.println("Building project...");
                projectBuilderService.buildProject(context, null);
            } else {
                context.out.println("Skipping build step.  No build tool detected.");
            }
        }
        File jdeployBundle = new File(context.directory, "jdeploy-bundle");
        if (context.alwaysClean) {
            if (jdeployBundle.exists()) {
                FileUtils.deleteDirectory(jdeployBundle);
            }
            if (context.getInstallersDir().exists()) {
                FileUtils.deleteDirectory(context.getInstallersDir());
            }
        }
        copyToBin(context);
        if (context.isPackageSigningEnabled()) {
            try {
                context.packageSigningService.signPackage(
                        getPackageSigningVersionString(context.getPackageJsonResult()),
                        jdeployBundle.getAbsolutePath()
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            allBundles(context, bundlerSettings);
        } catch (Exception ex) {
            if (ex instanceof IOException) {
                throw (IOException)ex;
            } else {
                throw new IOException("Failed to create bundles", ex);
            }
        }
        try {
            allInstallers(context, bundlerSettings);
        } catch (Exception ex) {
            if (ex instanceof IOException) {
                throw (IOException)ex;
            } else {
                throw new IOException("Failed to create installers", ex);
            }
        }
    }

    public void allInstallers(PackagingContext context) throws Exception {
        allInstallers(context, new BundlerSettings());
    }

    public void allInstallers(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        Set<String> installers = context.installers();
        for (String target : installers) {
            String version = "latest";
            if (target.contains("@")) {
                version = target.substring(target.indexOf("@")+1);
                target = target.substring(0, target.indexOf("@"));
            }
            installer(context, target, version, bundlerSettings);
        }
    }

    private void installer(PackagingContext context, String target, String version, BundlerSettings bundlerSettings) throws Exception {
        AppInfo appInfo = new AppInfo();
        loadAppInfo(context, appInfo);
        String source = bundlerSettings.getSource();
        if (source != null && !source.isEmpty()) {
            appInfo.setNpmSource(source);
            appInfo.setJdeployBundleCode(bundleCodeService.fetchJdeployBundleCode(source + "# " + appInfo.getNpmPackage()));
        }
        String packageJSONVersion = (String)context.m().get("version");
        appInfo.setNpmVersion(version);
        if (packageJSONVersion != null) {
            packageJSONVersion = VersionCleaner.cleanVersion(packageJSONVersion);
            appInfo.setNpmVersion(packageJSONVersion);
        }

        File installerDir = context.getInstallersDir();
        installerDir.mkdirs();

        String _newName = appInfo.getTitle() + " Installer-${{ platform }}";
        String dmgSuffix = "-${{ platform }}";
        String versionStr = appInfo.getNpmVersion();
        if (versionStr.startsWith("0.0.0-")) {
            versionStr = "@" + versionStr.substring("0.0.0-".length());
        }
        if (appInfo.getJdeployBundleCode() != null) {
            _newName += "-"+versionStr+"_"+appInfo.getJdeployBundleCode();
        }
        dmgSuffix += "-"+versionStr + ".dmg";

        File installerZip;
        if (target.equals("mac") || target.equals(BUNDLE_MAC_X64)) {
            _newName = _newName.replace("${{ platform }}", BUNDLE_MAC_X64);
            installerZip = new File(installerDir, _newName + ".tar");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-mac-amd64.tar"), installerZip);
        } else if (target.equals(BUNDLE_MAC_ARM64)) {
            _newName = _newName.replace("${{ platform }}", BUNDLE_MAC_ARM64);
            installerZip = new File(installerDir, _newName +  ".tar");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-mac-arm64.tar"), installerZip);
        } else if (target.equals(BUNDLE_MAC_ARM64_DMG)) {
            if (!Platform.getSystemPlatform().isMac()) {
                context.out.println("DMG bundling is only supported on macOS.  Skipping DMG generation");
                return;
            }
            dmgSuffix = dmgSuffix.replace("${{ platform }}", BUNDLE_MAC_ARM64);
            macArmDmg(context, bundlerSettings, installerDir, dmgSuffix);
            return;
        } else if (target.equals(BUNDLE_MAC_X64_DMG)) {
            if (!Platform.getSystemPlatform().isMac()) {
                context.out.println("DMG bundling is only supported on macOS.  Skipping DMG generation");
                return;
            }

            dmgSuffix = dmgSuffix.replace("${{ platform }}", BUNDLE_MAC_X64);
            macIntelDmg(context, bundlerSettings, installerDir, dmgSuffix);
            return;
        } else if (target.equals(BUNDLE_WIN) || target.equals(BUNDLE_WIN_LEGACY)) {
            String newNameLegacy = _newName.replace("${{ platform }}", BUNDLE_WIN_LEGACY);
            _newName = _newName.replace("${{ platform }}", BUNDLE_WIN_X64);
            installerZip = new File(installerDir, _newName + ".exe");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-win-amd64.exe"), installerZip);
            installerZip.setExecutable(true, false);
            if (bundlerSettings.isCompressBundles() && !bundlerSettings.isDoNotZipExeInstaller()) {
                installerZip = compressionService.compress(target, installerZip);
            }

            if (context.isGenerateLegacyBundles()) {
                context.out.println("Generating duplicate x64 Windows installer named " + newNameLegacy +" for for compatibility with legacy automation tools");
                context.out.println("This will be removed in a future release.  Please update your automation tools to use the "+_newName+" installer instead.");
                context.out.println("You can disable this behavior by setting generateLegacyBundles to false in the jdeploy object of your package.json file.");
                File legacyInstallerZip = new File(installerDir, newNameLegacy + ".exe");
                FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-win-amd64.exe"), legacyInstallerZip);
                legacyInstallerZip.setExecutable(true, false);
                if (bundlerSettings.isCompressBundles() && !bundlerSettings.isDoNotZipExeInstaller()) {
                    compressionService.compress(BUNDLE_WIN_LEGACY, legacyInstallerZip);
                }
            }

            return;

        } else if (target.equals(BUNDLE_WIN_ARM64)) {
            _newName = _newName.replace("${{ platform }}", BUNDLE_WIN_ARM64);
            installerZip = new File(installerDir, _newName + ".exe");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-win-arm64.exe"), installerZip);
            installerZip.setExecutable(true, false);
            if (bundlerSettings.isCompressBundles() && !bundlerSettings.isDoNotZipExeInstaller()) {
                installerZip = compressionService.compress(target, installerZip);
            }
            return;

        } else if (target.equals(BUNDLE_LINUX) || target.equals(BUNDLE_LINUX_LEGACY)) {
            String newNameLegacy = _newName.replace("${{ platform }}", BUNDLE_LINUX_LEGACY);
            _newName = _newName.replace("${{ platform }}", "linux-x64");
            installerZip = new File(installerDir, _newName);
            File installerTarGz = new File(installerDir, _newName + ".tar.gz");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-linux-amd64"), installerZip);
            installerZip.setExecutable(true, false);
            if (bundlerSettings.isCompressBundles()) {
                Util.compressAsTarGz(installerTarGz, installerZip);
                FileUtils.deleteQuietly(installerZip);
            }
            if (context.isGenerateLegacyBundles()) {
                context.out.println("Generating duplicate x64 Linux installer named " + newNameLegacy +" for for compatibility with legacy automation tools");
                context.out.println("This will be removed in a future release.  Please update your automation tools to use the "+_newName+" installer instead.");
                context.out.println("You can disable this behavior by setting generateLegacyBundles to false in the jdeploy object of your package.json file.");

                File legacyInstallerZip = new File(installerDir, newNameLegacy);
                FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-linux-amd64"), legacyInstallerZip);
                legacyInstallerZip.setExecutable(true, false);
                if (bundlerSettings.isCompressBundles()) {
                    compressionService.compress(BUNDLE_LINUX_LEGACY, legacyInstallerZip);
                }
            }
            return;

        } else if (target.equals(BUNDLE_LINUX_ARM64)) {
            _newName = _newName.replace("${{ platform }}", "linux-arm64");
            installerZip = new File(installerDir, _newName);
            File installerTarGz = new File(installerDir, _newName + ".tar.gz");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-linux-arm64"), installerZip);
            installerZip.setExecutable(true, false);
            if (bundlerSettings.isCompressBundles()) {
                Util.compressAsTarGz(installerTarGz, installerZip);
                FileUtils.deleteQuietly(installerZip);
            }
            return;

        } else {
            throw new IllegalArgumentException("Unsupported installer type: "+target+".  Only mac, win, and linux supported");
        }
        final String newName = _newName;

        // We are no longer embedding jdeploy files at all because
        // Gatekeeper runs the installer in a random directory anyways, so we can't locate them.
        // Instead we now use a code in the installer app name.
        boolean embedJdeployFiles = false;
        File bundledAppXmlFile = null;
        File bundledSplashFile = null;
        File bundledIconFile = null;
        File bundledLauncherSplashFile = null;
        if (embedJdeployFiles) {
            byte[] appXmlBytes;

            {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("<?xml version='1.0'?>\n<app/>".getBytes("UTF-8"));

                Document document = XMLUtil.parse(byteArrayInputStream);
                org.w3c.dom.Element appElement = document.getDocumentElement();
                appElement.setAttribute("title", appInfo.getTitle());
                appElement.setAttribute("package", appInfo.getNpmPackage());
                appElement.setAttribute("version", appInfo.getNpmVersion());
                appElement.setAttribute("macAppBundleId", appInfo.getMacAppBundleId());
                appElement.setAttribute("source", source);


                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                XMLUtil.write(document, baos);
                appXmlBytes = baos.toByteArray();
                bundledAppXmlFile = new File(context.getJdeployBundleDir(), "app.xml");
                FileUtils.writeByteArrayToFile(bundledAppXmlFile, appXmlBytes);
            }
            byte[] iconBytes;

            {
                File jarFile = new File(context.directory, toNativePath(context.getString("jar", null)));
                File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
                File iconFile = new File(absoluteParent, "icon.png");
                if (!iconFile.exists()) {
                    FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("icon.png"), iconFile);
                }
                File bin = context.getJdeployBundleDir();

                bundledIconFile = new File(bin, "icon.png");
                if (!bundledIconFile.exists()) {
                    FileUtils.copyFile(iconFile, bundledIconFile);
                }
                iconBytes = FileUtils.readFileToByteArray(bundledIconFile);

            }
            byte[] installSplashBytes;
            {
                File jarFile = new File(context.directory, toNativePath(context.getString("jar", null)));
                File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
                File splashFile = new File(absoluteParent, "installsplash.png");
                if (!splashFile.exists()) {
                    FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("installsplash.png"), splashFile);
                }
                File bin = context.getJdeployBundleDir();

                bundledSplashFile = new File(bin, "installsplash.png");
                if (!bundledSplashFile.exists()) {
                    FileUtils.copyFile(splashFile, bundledSplashFile);
                }
                installSplashBytes = FileUtils.readFileToByteArray(bundledSplashFile);
            }
            {
                File jarFile = new File(context.directory, toNativePath(context.getString("jar", null)));
                File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
                File launcherSplashFile = new File(absoluteParent, "launcher-splash.html");

                if (launcherSplashFile.exists()) {
                    File bin = context.getJdeployBundleDir();
                    bundledLauncherSplashFile = new File(bin, "launcher-splash.html");
                    FileUtils.copyFile(launcherSplashFile, bundledLauncherSplashFile);
                }
            }
        }

        ArchiveUtil.NameFilter filter = new ArchiveUtil.NameFilter() {
            @Override
            public String filterName(String name) {

                if (target.startsWith("mac")) {
                    name = name

                            .replaceFirst("^jdeploy-installer/jdeploy-installer\\.app/(.*)", newName + "/" + newName + ".app/$1")
                            .replaceFirst("^jdeploy-installer/\\._jdeploy-installer\\.app$", newName + "/._" + newName + ".app")
                            .replaceFirst("^jdeploy-installer/(.*)", newName + "/$1");
                } else if (target.startsWith("win")) {
                    name = name


                            .replaceFirst("^jdeploy-installer/jdeploy-installer\\.exe$", newName + "/"+newName+".exe")
                            .replaceFirst("^jdeploy-installer/(.*)", newName + "/$1");
                } else {
                    name = name

                            .replaceFirst("^jdeploy-installer/jdeploy-installer", newName + "/"+newName)
                            .replaceFirst("^jdeploy-installer/(.*)", newName + "/$1");
                }

                return name;
            }
        };

        ArrayList<ArchiveUtil.ArchiveFile> filesToAdd = new ArrayList<ArchiveUtil.ArchiveFile>();
        if (embedJdeployFiles) {
            filesToAdd.add(new ArchiveUtil.ArchiveFile(bundledAppXmlFile, newName + "/.jdeploy-files/app.xml"));
            filesToAdd.add(new ArchiveUtil.ArchiveFile(bundledSplashFile, newName + "/.jdeploy-files/installsplash.png"));
            filesToAdd.add(new ArchiveUtil.ArchiveFile(bundledIconFile, newName + "/.jdeploy-files/icon.png"));
            if (bundledLauncherSplashFile != null && bundledLauncherSplashFile.exists()) {
                filesToAdd.add(new ArchiveUtil.ArchiveFile(bundledLauncherSplashFile, newName + "/.jdeploy-files/launcher-splash.html"));
            }
        }
        if (target.startsWith("mac") || target.startsWith("linux")) {
            // Mac and linux use tar file
            ArchiveUtil.filterNamesInTarFile(installerZip, filter, filesToAdd);
        }  else {
            // Windows uses zip file
            ArchiveUtil.filterNamesInZipFile(installerZip, filter, filesToAdd);
        }

        if (bundlerSettings.isCompressBundles()) {
            installerZip = compressionService.compress(target, installerZip);
        }

    }

    public void copyToBin(PackagingContext context) throws IOException {

        loadDefaults();
        if (context.getPreCopyScript(null) != null) {
            int code = 0;
            if ((code = runScript(context.getPreCopyScript(null))) != 0) {
                fail(context, "Pre-copy script failed", code);
                return;
            }
        }

        File antFile = new File(context.getAntFile("build.xml"));
        if (antFile.exists() && context.getPreCopyTarget(null) != null) {
            int code = runAntTask(context.getAntFile("build.xml"), context.getPreCopyTarget(null));
            if (code != 0) {
                fail(context, "Pre-copy ant task failed", code);
                return;

            }
        }

        // Actually copy the files
        List<CopyRule> includes = getFiles(context);


        File bin = new File(context.directory, context.getBinDir());
        bin.mkdir();

        if (context.getJar(null) == null && context.getWar(null) == null) {
            // no jar or war explicitly specified... need to scan
            context.out.println("No jar, war, or web app explicitly specified.  Scanning directory to find best candidate.");

            File best = jarFinder.findBestCandidate(context);
            context.out.println("Found "+best);
            context.out.println("To explicitly set the jar, war, or web app to build, use the \"war\" or \"jar\" property of the \"jdeploy\" section of the package.json file.");
            if (best == null) {
            } else if (best.getName().endsWith(".jar")) {
                context.setJar(fromNativePath(best.getPath()));
            } else {
                context.setWar(best.getPath());
            }

        }
        boolean serverProvidedJavaFX = "true".equals(context.getString("javafx", "false"));
        boolean stripJavaFXFilesFlag = "true".equals(context.getString("stripJavaFXFiles", "true"));
        boolean javafxVersionProvided = !context.getString("javafxVersion", "").isEmpty();
        boolean stripFXFiles = stripJavaFXFilesFlag && (serverProvidedJavaFX || javafxVersionProvided);

        if (context.doNotStripJavaFXFiles) {
            stripFXFiles = false;
        }
        File jarFile = null;
        if (context.getJar(null) != null) {
            // We need to include the jar at least

            jarFile = jarFinder.findJarFile(context);
            if (jarFile == null) {
                throw new IOException("Could not find jar file: "+context.getJar(null));
            }
            includes.addAll(copyJarRuleBuilder.build(context, jarFile));
        } else if (context.getWar(null) != null) {
            File warFile = jarFinder.findWarFile(context);
            if (warFile == null) {
                throw new IOException("Could not find war file: "+context.getWar(null));
            }
            String parentPath = warFile.getParentFile() != null ? warFile.getParentFile().getPath() : ".";
            includes.add(new CopyRule(context, parentPath, warFile.getName(), null, true));
        } else {

            throw new RuntimeException("No jar, war, or web app was found to build in this directory.");

        }

        if (includes.isEmpty()) {
            throw new RuntimeException("No files were found to include in the bundle");
        }

        // Now actually copy the files
        for (CopyRule r : includes) {
            try {
                r.copyTo(bin);
            } catch (Exception ex) {
                context.err.println("Failed to copy to "+bin+" with rule "+r);
                context.err.println("Files: "+includes);
                throw ex;
            }
        }

        if (jarFile != null && jarFile.exists() && stripFXFiles) {
            context.out.println("Since JavaFX will be provided, we are stripping it from the build jar");
            context.out.println("If this causes problems you can disable this by setting stripJavaFXFiles to false in the jdeploy object of your package.json file.");
            try {
                File mainJarFileInBin = null;
                for (File child : bin.listFiles()) {
                    if (child.getName().equals(jarFile.getName())) {
                        mainJarFileInBin = child;
                        break;
                    }
                }
                if (mainJarFileInBin != null) {
                    ZipFile jarZipFile = new ZipFile(mainJarFileInBin);

                    String[] pathsToCheck = new String[]{
                            "javafx/",
                            "com/sun/javafx/",
                            "api-ms-win-core-console-l1-1-0.dll",
                            "api-ms-win-core-datetime-l1-1-0.dll",
                            "api-ms-win-core-debug-l1-1-0.dll",
                            "api-ms-win-core-errorhandling-l1-1-0.dll",
                            "api-ms-win-core-file-l1-1-0.dll",
                            "api-ms-win-core-file-l1-2-0.dll",
                            "api-ms-win-core-file-l2-1-0.dll",
                            "api-ms-win-core-handle-l1-1-0.dll",
                            "api-ms-win-core-heap-l1-1-0.dll",
                            "api-ms-win-core-interlocked-l1-1-0.dll",
                            "api-ms-win-core-libraryloader-l1-1-0.dll",
                            "api-ms-win-core-localization-l1-2-0.dll",
                            "api-ms-win-core-memory-l1-1-0.dll",
                            "api-ms-win-core-namedpipe-l1-1-0.dll",
                            "api-ms-win-core-processenvironment-l1-1-0.dll",
                            "api-ms-win-core-processthreads-l1-1-0.dll",
                            "api-ms-win-core-processthreads-l1-1-1.dll",
                            "api-ms-win-core-profile-l1-1-0.dll",
                            "api-ms-win-core-rtlsupport-l1-1-0.dll",
                            "api-ms-win-core-string-l1-1-0.dll",
                            "api-ms-win-core-synch-l1-1-0.dll",
                            "api-ms-win-core-synch-l1-2-0.dll",
                            "api-ms-win-core-sysinfo-l1-1-0.dll",
                            "api-ms-win-core-timezone-l1-1-0.dll",
                            "api-ms-win-core-util-l1-1-0.dll",
                            "api-ms-win-crt-conio-l1-1-0.dll",
                            "api-ms-win-crt-convert-l1-1-0.dll",
                            "api-ms-win-crt-environment-l1-1-0.dll",
                            "api-ms-win-crt-filesystem-l1-1-0.dll",
                            "api-ms-win-crt-heap-l1-1-0.dll",
                            "api-ms-win-crt-locale-l1-1-0.dll",
                            "api-ms-win-crt-math-l1-1-0.dll",
                            "api-ms-win-crt-multibyte-l1-1-0.dll",
                            "api-ms-win-crt-private-l1-1-0.dll",
                            "api-ms-win-crt-process-l1-1-0.dll",
                            "api-ms-win-crt-runtime-l1-1-0.dll",
                            "api-ms-win-crt-stdio-l1-1-0.dll",
                            "api-ms-win-crt-string-l1-1-0.dll",
                            "api-ms-win-crt-time-l1-1-0.dll",
                            "api-ms-win-crt-utility-l1-1-0.dll",
                            "concrt140.dll",
                            "decora_sse.dll",
                            "glass.dll",
                            "javafx_font.dll",
                            "javafx_iio.dll",
                            "msvcp140.dll",
                            "prism_common.dll",
                            "prism_d3d.dll",
                            "prism_sw.dll",
                            "ucrtbase.dll",
                            "vcruntime140.dll",
                            "jfxwebkit.dll",
                            "libjavafx_font_freetype.so",
                            "libglassgtk3.so",
                            "libjavafx_iio.so",
                            "libprism_sw.so",
                            "libglassgtk2.so",
                            "libprism_common.so",
                            "libglass.so",
                            "libprism_es2.so",
                            "libdecora_sse.so",
                            "libjavafx_font_pango.so",
                            "libjavafx_font.so",
                            "libjfxwebkit.so",
                            "libjavafx_iio.dylib",
                            "libglass.dylib",
                            "libjavafx_font.dylib",
                            "libprism_common.dylib",
                            "libprism_es2.dylib",
                            "libdecora_sse.dylib",
                            "libprism_sw.dylib",
                            "libjfxmedia_avf.dylib",
                            "libglib-lite.dylib",
                            "libfxplugins.dylib",
                            "libgstreamer-lite.dylib",
                            "libjfxmedia.dylib",
                            "libjfxwebkit.dylib"
                    };
                    Set<String> pathsToCheckSet = new HashSet<>(Arrays.asList(pathsToCheck));
                    List<String> pathsToRemove = new ArrayList<>();
                    for (FileHeader header : jarZipFile.getFileHeaders()) {
                        if (pathsToCheckSet.contains(header.getFileName())) {
                            pathsToRemove.add(header.getFileName());
                        }
                    }
                    jarZipFile.removeFiles(pathsToRemove);

                }
            } catch (Exception ex) {
                context.err.println("Attempt to strip JavaFX files from the application jar file failed.");
                ex.printStackTrace(context.err);
                throw ex;
            }
        }

        if (context.getWar(null) != null) {
            bundleJetty(context);
        }

        bundleJdeploy(context);
        bundleJarRunner(context);
        bundleIcon(context);
        bundleSplash(context);

        if (context.getPostCopyScript(null) != null) {
            int code = 0;
            if ((code = runScript(context.getPostCopyScript(null))) != 0) {
                fail(context, "Post-copy script failed", code);
                return;
            }
        }

        if (antFile.exists() && context.getPostCopyTarget(null) != null) {
            int code = runAntTask(context.getAntFile("build.xml"), context.getPostCopyTarget(null));
            if (code != 0) {
                fail(context, "Post-copy ant task failed", code);
                return;
            }
        }

    }

    private void loadDefaults() throws IOException {

    }

    private String getPackageSigningVersionString(JSONObject packageJSON) {
        String versionString = packageJSON.getString("version");
        if (packageJSON.has("commitHash")) {
            versionString += "#" + packageJSON.getString("commitHash");
        }

        return versionString;
    }

    private String getPackageSigningVersionString(Result packageJSON) {
        String versionString = packageJSON.getAsString("version");
        if (packageJSON.get("commitHash") != null) {
            versionString += "#" + packageJSON.getAsString("commitHash");
        }

        return versionString;
    }

    private Map<String, BundlerResult> allBundles(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        Set<String> bundles = context.bundles();
        Map<String, BundlerResult> results = new HashMap<>();
        if (bundles.contains("mac") || bundles.contains(BUNDLE_MAC_X64)) {
            results.put(BUNDLE_MAC_X64, macIntelBundle(context, bundlerSettings));
        }
        if (bundles.contains(BUNDLE_MAC_ARM64)) {
            results.put(BUNDLE_MAC_ARM64, macArmBundle(context, bundlerSettings));
        }
        if (bundles.contains(BUNDLE_WIN) || bundles.contains(BUNDLE_WIN_LEGACY)) {
            results.put(BUNDLE_WIN, windowsX64Bundle(context, bundlerSettings));
        }
        if (bundles.contains(BUNDLE_WIN_ARM64)) {
            results.put(BUNDLE_WIN_ARM64, windowsArm64Bundle(context, bundlerSettings));
        }
        if (bundles.contains(BUNDLE_LINUX) || bundles.contains(BUNDLE_LINUX_LEGACY)) {
            results.put(BUNDLE_LINUX, linuxX64Bundle(context, bundlerSettings));
        }
        if (bundles.contains(BUNDLE_LINUX_ARM64)) {
            results.put(BUNDLE_LINUX, linuxArm64Bundle(context, bundlerSettings));
        }

        return results;
    }

    private BundlerResult macIntelBundle(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        return bundle(context, BUNDLE_MAC_X64, bundlerSettings);
    }

    private BundlerResult macIntelBundle(
            PackagingContext context,
            BundlerSettings bundlerSettings,
            String overrideDestDir,
            String overrideReleaseDir
    ) throws Exception {
        return bundle(context, BUNDLE_MAC_X64, bundlerSettings, overrideDestDir, overrideReleaseDir);
    }

    private BundlerResult macArmBundle(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        return bundle(context, BUNDLE_MAC_ARM64, bundlerSettings);
    }

    private BundlerResult macArmBundle(
            PackagingContext context,
            BundlerSettings bundlerSettings,
            String overrideDestDir,
            String overrideReleaseDir
    ) throws Exception {
        return bundle(context, BUNDLE_MAC_ARM64, bundlerSettings, overrideDestDir, overrideReleaseDir);
    }

    private BundlerResult macArmDmg(PackagingContext context, BundlerSettings bundlerSettings, File destDir, String suffix) throws Exception {
        return dmg(bundlerSettings, destDir, suffix, BUNDLE_MAC_ARM64_DMG, (bundlerSettings1, bundleDestDir, bundleReleaseDir) -> {
            return macArmBundle(context, bundlerSettings1, bundleDestDir, bundleReleaseDir);
        });
    }

    private BundlerResult macIntelDmg(PackagingContext context, BundlerSettings bundlerSettings, File destDir, String suffix) throws Exception {
        return dmg(bundlerSettings, destDir, suffix, BUNDLE_MAC_X64_DMG, (bundlerSettings1, bundleDestDir, bundleReleaseDir) -> {
            return macIntelBundle(context, bundlerSettings1, bundleDestDir, bundleReleaseDir);
        });
    }

    private BundlerResult dmg(
            BundlerSettings bundlerSettings,
            File destDir,
            String suffix,
            String bundleType,
            BundlerCall bundlerCall
    ) throws Exception {
        if (bundlerSettings.getSource() == null && System.getenv("JDEPLOY_SOURCE") != null){
            String source = System.getenv("JDEPLOY_SOURCE");
            bundlerSettings.setSource(source);
        }
        File tmpDir = File.createTempFile("jdeploy", "dmg");
        tmpDir.delete();
        tmpDir.mkdirs();
        try {

            BundlerResult bundleResult = bundlerCall.bundle(
                    bundlerSettings,
                    tmpDir.getAbsolutePath(),
                    tmpDir.getAbsolutePath()
            );
            String appName = bundleResult.getOutputFile().getName();
            String appNameWithoutExtension = appName.substring(0, appName.lastIndexOf("."));
            String dmgName = appNameWithoutExtension + suffix;
            File dmgFile = new File(destDir, dmgName);
            DmgCreator.createDmg(
                    bundleResult.getOutputFile().getAbsolutePath(),
                    dmgFile.getAbsolutePath()
            );
            BundlerResult newResult = new BundlerResult(bundleType);
            newResult.setOutputFile(dmgFile);
            bundleResult.setResultForType(bundleType, newResult);

            return bundleResult;

        } finally {
            FileUtils.deleteDirectory(tmpDir);
        }
    }

    private BundlerResult windowsX64Bundle(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        return bundle(context, "win", bundlerSettings);
    }
    private BundlerResult windowsArm64Bundle(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        return bundle(context, "win-arm64", bundlerSettings);
    }
    private BundlerResult linuxX64Bundle(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        return bundle(context, "linux", bundlerSettings);
    }
    private BundlerResult linuxArm64Bundle(PackagingContext context, BundlerSettings bundlerSettings) throws Exception {
        return bundle(context, "linux-arm64", bundlerSettings);
    }

    private BundlerResult bundle(
            PackagingContext context,
            String target,
            BundlerSettings bundlerSettings
    ) throws Exception {
        return bundle(context, target, bundlerSettings, null, null);
    }

    private BundlerResult bundle(
            PackagingContext context,
            String target,
            BundlerSettings bundlerSettings,
            String overrideDestDir,
            String overrideReleaseDir
    ) throws Exception {
        AppInfo appInfo = new AppInfo();
        loadAppInfo(context, appInfo);
        if (bundlerSettings.getSource() != null) {
            appInfo.setNpmSource(bundlerSettings.getSource());
        }

        String packageJsonVersion = context.m().get("version") != null ? context.m().get("version").toString() : "latest";
        if (bundlerSettings.getBundleVersion() != null) {
            appInfo.setNpmVersion(bundlerSettings.getBundleVersion());
        } else if (bundlerSettings.isAutoUpdateEnabled() && !packageJsonVersion.startsWith("0.0.0-")) {
            appInfo.setNpmVersion("latest");
        } else if (packageJsonVersion.startsWith("0.0.0-") || !bundlerSettings.isAutoUpdateEnabled()) {
            appInfo.setNpmVersion(packageJsonVersion);
        }

        return Bundler.runit(
                bundlerSettings,
                appInfo,
                appInfo.getAppURL().toString(),
                target,
                overrideDestDir == null ? "jdeploy" + File.separator + "bundles" : overrideDestDir,
                overrideReleaseDir == null ? "jdeploy" + File.separator + "releases" : overrideReleaseDir
        );
    }

    private void loadAppInfo(PackagingContext context, AppInfo appInfo) throws IOException {
        appInfo.setNpmPackage((String)context.m().get("name"));
        String packageJsonVersion = context.m().get("version") != null ? context.m().get("version").toString() : "latest";
        appInfo.setNpmVersion(packageJsonVersion);
        if (context.isPackageSigningEnabled()) {
            try {
                appInfo.setEnableCertificatePinning(true);
                appInfo.setTrustedCertificates(context.keyProvider.getTrustedCertificates());
            } catch (Exception ex) {
                throw new IOException("Failed to load private key for package signing", ex);
            }
        }

        if (context.m().containsKey("source")) {
            appInfo.setNpmSource((String)context.m().get("source"));
        }
        if (appInfo.getNpmVersion() != null && appInfo.getNpmPackage() != null) {
            appInfo.setJdeployBundleCode(bundleCodeService.fetchJdeployBundleCode(appInfo));
        }
        appInfo.setMacAppBundleId(context.getString("macAppBundleId", null));
        appInfo.setTitle(
                context.getString(
                        "displayName",
                        context.getString("title", appInfo.getNpmPackage())
                )
        );

        appInfo.setNpmAllowPrerelease(
                "true".equals(
                        getenv(
                                "JDEPLOY_BUNDLE_PRERELEASE",
                                context.getString("prerelease", "false")
                        )
                )
        );
        if (
                PrereleaseHelper.isPrereleaseVersion(appInfo.getNpmVersion()) ||
                        PrereleaseHelper.isPrereleaseVersion(appInfo.getVersion())
        ) {
            appInfo.setNpmAllowPrerelease(true);
        }
        appInfo.setFork("true".equals(context.getString("fork", "false")));

        if (context.rj().getAsBoolean("codesign") && context.rj().getAsBoolean("notarize")) {
            appInfo.setCodeSignSettings(AppInfo.CodeSignSettings.CodeSignAndNotarize);
        } else if (context.rj().getAsBoolean("codesign")) {
            appInfo.setCodeSignSettings(AppInfo.CodeSignSettings.CodeSign);
        }

        if (context.rj().getAsBoolean("bundleJVM")) {
            appInfo.setUseBundledJVM(true);
            JVMSpecification jvmSpec = new JVMSpecification();
            jvmSpec.javaVersion = context.getJavaVersion(DEFAULT_JAVA_VERSION);
            jvmSpec.javafx = "true".equals(context.getString("javafx", "false"));
            jvmSpec.jdk = "true".equals(context.getString("jdk", "false"));
            appInfo.setJVMSpecification(jvmSpec);
        }

        if (context.mj().get("jdeployHome") != null) {
            System.out.println("Setting jdeployHome to "+context.rj().getAsString("jdeployHome"));
            appInfo.setJdeployHome(context.rj().getAsString("jdeployHome"));
        }
        if (context.mj().get("jdeployHomeLinux") != null) {
            appInfo.setLinuxJdeployHome(context.rj().getAsString("jdeployHomeLinux"));
        }
        if (context.mj().get("jdeployHomeMac") != null) {
            appInfo.setMacJdeployHome(context.rj().getAsString("jdeployHomeMac"));
        }
        if (context.mj().get("jdeployHomeWindows") != null) {
            appInfo.setWindowsJdeployHome(context.rj().getAsString("jdeployHomeWindows"));
        }

        if (context.mj().get("jdeployRegistryUrl") != null) {
            appInfo.setJdeployRegistryUrl(context.rj().getAsString("jdeployRegistryUrl"));
        } else {
            appInfo.setJdeployRegistryUrl(packagingConfig.getJdeployRegistry());
        }

        String jarPath = context.getString("jar", null);
        if (jarPath != null) {
            JarFile jarFile = new JarFile(new File(context.directory, toNativePath(jarPath)));
            String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (appInfo.getMacAppBundleId() == null) {
                appInfo.setMacAppBundleId(mainClass.toLowerCase());
            }
            appInfo.setAppURL(new File(jarPath).toURL());
        } else {
            throw new IOException("Cannot load app info because find jar file "+jarPath);
        }

        for (
                Map.Entry<PermissionRequest, String> permissionRequestEntry
                : permissionRequestService.getPermissionRequests(context.packageJsonObject()).entrySet()
        ) {
            appInfo.addPermissionRequest(permissionRequestEntry.getKey(), permissionRequestEntry.getValue());
        }

        File jarFile = new File(jarPath);
        File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
        File iconFile = new File(absoluteParent, "icon.png");
        if (!iconFile.exists()) {
            File projectIcon = new File("icon.png");

            if (projectIcon.exists()) {
                FileUtils.copyFile(projectIcon, iconFile);
            } else {
                FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("icon.png"), iconFile);
            }

        }

    }

    private String getenv(String key, String defaultValue) {
        String value = environment.get(key);
        if (value == null) return defaultValue;
        return value;
    }

    private List<CopyRule> getFiles(PackagingContext context) {
        List files = context.getList("files", true);
        List<CopyRule> out = new ArrayList<CopyRule>();

        for (Object o : files) {
            if (o instanceof Map) {
                Map m = (Map)o;

                String dir = (String)m.get("dir");
                ArrayList<String> incs = null;
                if (m.containsKey("includes")) {
                    Object i = m.get("includes");
                    incs = new ArrayList<String>();
                    if (i instanceof List) {
                        incs.addAll((List<String>)i);
                    } else if (i instanceof String){
                        incs.addAll(Arrays.asList(((String)i).split(",")));
                    }
                }
                ArrayList<String> excs = null;
                if (m.containsKey("excludes")) {
                    Object i = m.get("excludes");
                    excs = new ArrayList<String>();
                    if (i instanceof List) {
                        excs.addAll((List<String>)i);
                    } else if (i instanceof String){
                        excs.addAll(Arrays.asList(((String)i).split(",")));
                    }
                }
                out.add(new CopyRule(context, dir, incs, excs, false));
            } else if (o instanceof String) {
                out.add(new CopyRule(context, (String)o, (String)null, (String)null, false));
            }
        }
        return out;
    }

    private int runScript(String script) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(script);
            Process p = pb.start();
            return p.waitFor();
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        }
    }

    private int runAntTask(String antFile, String target) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("ant", "-f", antFile, target);
            Process p = pb.start();
            return p.waitFor();

        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        }
    }

    private void bundleJdeploy(PackagingContext context) throws IOException {
        File bin = context.getJdeployBundleDir();
        InputStream jdeployJs = JDeploy.class.getResourceAsStream("jdeploy.js");
        File jDeployFile = new File(bin, "jdeploy.js");
        FileUtils.copyInputStreamToFile(jdeployJs, jDeployFile);
        String jdeployContents = FileUtils.readFileToString(jDeployFile, "UTF-8");
        jdeployContents = processJdeployTemplate(context, jdeployContents);
        FileUtils.writeStringToFile(jDeployFile, jdeployContents, "UTF-8");

        // Write jdeploy metadata (including jdeploy.commands) into the jdeploy bundle
        writeJdeployMetadata(context);
    }

    /**
     * Writes a small metadata JSON file into the jdeploy bundle directory containing the jdeploy
     * configuration object (verbatim) so installer/runtime can consume it. The file is named
     * "package-info.json" and contains at least name, version, and jdeploy properties.
     *
     * This method is static so it can be exercised easily by unit tests.
     */
    public static void writeJdeployMetadata(PackagingContext context) throws IOException {
        JSONObject pkgObj = context.packageJsonObject();
        JSONObject jdeployObj = new JSONObject();
        if (pkgObj != null && pkgObj.has("jdeploy")) {
            jdeployObj = pkgObj.getJSONObject("jdeploy");
        }
        JSONObject metadata = new JSONObject();
        metadata.put("name", pkgObj != null ? pkgObj.optString("name", "") : "");
        metadata.put("version", pkgObj != null ? pkgObj.optString("version", "") : "");
        metadata.put("jdeploy", jdeployObj);

        File bin = context.getJdeployBundleDir();
        if (!bin.exists()) {
            if (!bin.mkdirs()) {
                throw new IOException("Failed to create jdeploy bundle dir: " + bin.getAbsolutePath());
            }
        }

        File metadataFile = new File(bin, "package-info.json");
        FileUtils.writeStringToFile(metadataFile, metadata.toString(2), "UTF-8");
    }

    private String processJdeployTemplate(PackagingContext context, String jdeployContents) {
        jdeployContents = jdeployContents.replace("{{JAVA_VERSION}}", String.valueOf(context.getJavaVersion(DEFAULT_JAVA_VERSION)));
        jdeployContents = jdeployContents.replace("{{PORT}}", String.valueOf(context.getPort(0)));
        if (context.getWar(null) != null) {
            jdeployContents = jdeployContents.replace("{{WAR_PATH}}", new File(context.getWar(null)).getName());
        } else {
            jdeployContents = jdeployContents.replace("{{WAR_PATH}}", "");
        }

        if ("true".equals(context.getString("javafx", "false")) ) {
            jdeployContents = jdeployContents.replace("{{JAVAFX}}", "true");
        }
        if ("true".equals(context.getString("jdk", "false"))) {
            jdeployContents = jdeployContents.replace("{{JDK}}", "true");
        }
        if (context.getJar(null) != null) {
            File jarFile = jarFinder.findJarFile(context);
            if (jarFile == null) {
                throw new RuntimeException("Could not find jar file: "+context.getJar(null));
            }
            jdeployContents = jdeployContents.replace("{{JAR_NAME}}", jarFinder.findJarFile(context).getName());
        } else if (context.getMainClass(null) != null) {
            jdeployContents = jdeployContents.replace("{{CLASSPATH}}", context.getClassPath("."));
            jdeployContents = jdeployContents.replace("{{MAIN_CLASS}}", context.getMainClass(null));
        } else {
            throw new RuntimeException("No main class or jar specified.  Cannot fill template");
        }
        return jdeployContents;
    }

    private void bundleJetty(PackagingContext context) throws IOException {
        // Now we need to create the stub.
        File bin = context.getJdeployBundleDir();
        InputStream warRunnerInput = JDeploy.class.getResourceAsStream("WarRunner.jar");

        InputStream jettyRunnerJarInput = JDeploy.class.getResourceAsStream("jetty-runner.jar");
        File libDir = new File(bin, "lib");
        libDir.mkdir();
        File jettyRunnerDest = new File(libDir, "jetty-runner.jar");
        File warRunnerDest = new File(libDir, "WarRunner.jar");
        FileUtils.copyInputStreamToFile(jettyRunnerJarInput, jettyRunnerDest);
        FileUtils.copyInputStreamToFile(warRunnerInput, warRunnerDest);

        context.setMainClass("ca.weblite.jdeploy.WarRunner");
        context.setClassPath("."+File.pathSeparator+"lib/jetty-runner.jar"+File.pathSeparator+"lib/WarRunner.jar");

    }

    private void bundleJarRunner(PackagingContext context) throws IOException {
        File bin = context.getJdeployBundleDir();
        InputStream jarRunnerJar = JDeploy.class.getResourceAsStream("jar-runner.jar");
        File jarRunnerFile = new File(bin, "jar-runner.jar");
        FileUtils.copyInputStreamToFile(jarRunnerJar, jarRunnerFile);
    }

    private void bundleIcon(PackagingContext context) throws IOException {
        File jarFile = new File(context.directory, toNativePath(context.getString("jar", null)));

        File iconFile = new File(jarFile.getAbsoluteFile().getParentFile(), "icon.png");
        if (!iconFile.exists()) {
            File _iconFile = new File(context.directory, "icon.png");
            if (_iconFile.exists()) {
                FileUtils.copyFile(_iconFile, iconFile);
            }
        }
        if (!iconFile.exists()) {
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("icon.png"), iconFile);
        }
        File bin = context.getJdeployBundleDir();

        File bundledIconFile = new File(bin, "icon.png");
        FileUtils.copyFile(iconFile, bundledIconFile);
    }


    private void bundleSplash(PackagingContext context) throws IOException {
        File jarFile = new File(context.directory, toNativePath(context.getString("jar", null)));
        File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
        File splashFile = new File(absoluteParent, "splash.png");
        if (!splashFile.exists() && absoluteParent.isDirectory()) {
            for (File child : absoluteParent.listFiles()) {
                if (child.getName().equals("splash.jpg") || child.getName().equals("splash.gif")) {
                    splashFile = child;

                }
            }

        }
        if (!splashFile.exists() && context.directory.isDirectory()) {
            for (File child : context.directory.listFiles()) {
                if (child.getName().equals("splash.jpg") || child.getName().equals("splash.gif") || child.getName().equals("splash.png")) {
                    File _splashFile = child;
                    splashFile = new File(absoluteParent, _splashFile.getName());
                    FileUtils.copyFile(_splashFile, splashFile);
                }
            }
        }
        if (!splashFile.exists()) {

            return;
        }
        File bin = context.getJdeployBundleDir();

        File bundledSplashFile = new File(bin, splashFile.getName());
        FileUtils.copyFile(splashFile, bundledSplashFile);
    }

    private void fail(PackagingContext context, String message, int code) {
        if (context.exitOnFail) {
            context.err.println(message);
            System.exit(code);
        } else {
            throw new JDeploy.FailException(message, code);
        }
    }

    private boolean isBuildRequired(PackagingContext context) {
        if (context.isBuildRequired) {
            return true;
        }

        String jar = context.getJar(null);
        if (jar != null) {
            return jarFinder.findJarFile(context) == null;
        }

        return false;
    }

}
