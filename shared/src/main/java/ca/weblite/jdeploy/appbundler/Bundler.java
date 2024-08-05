/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.jdeploy.appbundler;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.JVMSpecification;
import ca.weblite.jdeploy.jvmdownloader.JVMKit;
import ca.weblite.tools.io.URLUtil;
import com.joshondesign.appbundler.linux.LinuxBundler;
import com.joshondesign.appbundler.mac.MacBundler;
import com.joshondesign.appbundler.win.WindowsBundler2;
import com.joshondesign.xml.Doc;
import com.joshondesign.xml.Elem;
import com.joshondesign.xml.XMLParser;
import org.apache.commons.io.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

/**
 *
 * @author joshmarinacci
 * @author stevehannah
 */
public class Bundler {
    public static int verboseLevel = 0;

    public static void main(String ... args) throws Exception {
        String jarUrl = args[0];
        String target = args[1];
        String DEST_DIR = args[2];
        for(String arg : args) {
            if(arg.startsWith("--")) {
                if(arg.startsWith("--url=")) {
                    jarUrl = arg.substring("--url=".length());
                }
                if(arg.startsWith("--target=")) {
                    target = arg.substring("--target=".length());
                }
                if(arg.startsWith("--outdir=")) {
                    DEST_DIR = arg.substring("--outdir=".length());
                }
                p("Matched: " + arg);
            }
        }

        p("using target " + target);
        p("using dest_dir = " + DEST_DIR);
        p("using jar url = " + jarUrl);

        runit(new BundlerSettings(), null, jarUrl, target, DEST_DIR, DEST_DIR);
    }

    /**
     * Returns first non-empty value from candidates
     * @param candidates
     * @return 
     */
    private static String val(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isEmpty()) {
                return c;
            }
        }
        return null;
    }

    private static String toDataURI(URL url) throws IOException {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(url));
    }

    private static AppDescription createAppDescription(AppInfo appInfo, String url) throws IOException{
        AppDescription app = new AppDescription();
        if (appInfo.isCertificatePinningEnabled()) {
            app.enablePackageCertificatePinning();
            if (appInfo.getTrustedCertificates() != null) {
                app.setTrustedCertificates(appInfo.getTrustedCertificates());
            }
        }
        app.setNpmPrerelease(appInfo.isNpmAllowPrerelease());
        app.setName(appInfo.getTitle());
        app.setFork(appInfo.isFork());
        URL iconURL = URLUtil.url(appInfo.getAppURL(), "icon.png");
        app.setIconDataURI(toDataURI(iconURL));
        app.setjDeployHome(appInfo.getJdeployHome());
        app.setjDeployHomeLinux(appInfo.getLinuxJdeployHome());
        app.setjDeployHomeMac(appInfo.getMacJdeployHome());
        app.setjDeployHomeWindows(appInfo.getWindowsJdeployHome());

        if (url == null) throw new IllegalArgumentException("URL is required. It can be a file: url");

        if (url.startsWith("file:") || url.startsWith("http:") || url.startsWith("https:")) {
            app.setUrl(url);
        } else {
            throw new IllegalStateException("URL should be file:, http:, or https:");
        }

        if (appInfo.getNpmPackage() != null) {
            if (appInfo.getNpmVersion() == null) {
                appInfo.setNpmVersion("latest");
            }
            app.setNpmPackage(appInfo.getNpmPackage());
            app.setNpmVersion(appInfo.getNpmVersion());
        }
        if (appInfo.getNpmSource() != null) {
            app.setNpmSource(appInfo.getNpmSource());
        }
        setupMacCodeSigning(appInfo, app);
        setupFileAssociations(appInfo, app);
        setupUrlSchemes(appInfo, app);

        return app;
    }

    private static AppDescription setupMacCodeSigning(AppInfo appInfo, AppDescription app) {
        if (appInfo.getMacAppBundleId() != null && !appInfo.getMacAppBundleId().isEmpty()) {
            System.out.println("Setting up codesigning");
            app.setMacBundleId(appInfo.getMacAppBundleId());
            AppInfo.CodeSignSettings codeSignSettings = appInfo.getCodeSignSettings();
            boolean shouldNotarize = false;
            boolean shouldSign = false;
            if (codeSignSettings == AppInfo.CodeSignSettings.CodeSign) {
                shouldSign = true;
            }
            if (codeSignSettings == AppInfo.CodeSignSettings.CodeSignAndNotarize) {
                shouldNotarize = true;
                shouldSign = true;
            }
            if (codeSignSettings == AppInfo.CodeSignSettings.Default) {
                switch (C4JPublisherSettings.getMacSigningSettings()) {
                    case CodeSign:
                        shouldSign = true;
                        break;
                    case CodeSignAndNotarize:
                        shouldSign = true;
                        shouldNotarize = true;
                        break;
                }
            }

            System.out.println("shouldSign="+shouldSign+", shouldNotarize="+shouldNotarize);
            if (shouldSign) {
                String certName = val(
                        C4JPublisherSettings.getMacDeveloperCertificateName(appInfo),
                        C4JPublisherSettings.getMacDeveloperCertificateName());
                System.out.println("certName="+certName);
                if (certName != null) {
                    app.enableMacCodeSigning(certName);

                    if (shouldNotarize) {
                        String developerId = val(
                                C4JPublisherSettings.getMacDeveloperID(appInfo),
                                C4JPublisherSettings.getMacDeveloperID()
                        );
                        String notarizePassword = val(
                                C4JPublisherSettings.getMacNotarizationPassword(appInfo),
                                C4JPublisherSettings.getMacNotarizationPassword()
                        );
                        if (developerId != null && notarizePassword != null) {
                            app.enableMacNotarization(
                                    developerId,
                                    notarizePassword,
                                    C4JPublisherSettings.getMacDeveloperTeamID(appInfo)
                            );
                        }
                    }
                }
            }
        }

        return app;
    }

    private static void setupFileAssociations(AppInfo appInfo, AppDescription app) {
        if (appInfo.hasDocumentTypes()) {
            for (String extension : appInfo.getExtensions()) {
                app.addExtension(extension, appInfo.getMimetype(extension), appInfo.getDocumentTypeIconPath(extension));
                if (appInfo.isDocumentTypeEditor(extension)) {
                    app.addEditableExtension(extension);
                }
            }
        }
    }

    private static void setupUrlSchemes(AppInfo appInfo, AppDescription app) {
        if (appInfo.hasUrlSchemes()) {
            for (String scheme : appInfo.getUrlSchemes()) {
                app.addUrlScheme(scheme);
            }
        }
    }

    private static String getBundlerJVMsPath() {
        return Paths.get(System.getProperty("user.home"), ".jdeploy", "bundler", "JavaVirtualMachines").toString();
    }

    private static void setupBundledJVM(AppInfo appInfo, AppDescription app, Target target) throws IOException {
        if (target != Target.MacX64 && target != Target.MacArm) {
            // Currently we only support bundling JVM on Mac.
            return;
        }
        if (appInfo.isUseBundledJVM()) {
            JVMSpecification jvmSpecification = appInfo.getJVMSpecification();
            if (jvmSpecification == null) {
                throw new RuntimeException("jvmSpecification is required when useBundledJVM is true");
            }
            JVMKit jvmKit = new JVMKit();
            String arch;
            String platform = "macos";
            String bitness = "64";
            switch (target) {
                case MacX64:
                    arch = "x86";
                    break;
                case MacArm:
                    arch = "arm";
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported target: " + target);
            }
            app.setBundleJre(
                    jvmKit.createFinder(true).findJVM(
                            getBundlerJVMsPath(),
                            String.valueOf(jvmSpecification.javaVersion),
                            jvmSpecification.jdk ? "jdk" : "jre",
                            jvmSpecification.javafx,
                            platform,
                            arch,
                            bitness
                    )
            );

            if (app.getNpmVersion() != null && !app.getNpmVersion().isEmpty()) {
                // Add a qualifier to the version so that auto updates don't go beyond
                // what the bundled VM can handle
                app.setNpmVersion(app.getNpmVersion() + "[" +
                        (jvmSpecification.jdk? "jdk" : "jre") +
                        jvmSpecification.javaVersion +
                        (jvmSpecification.javafx ? "fx" : "") +
                        "]");
            }

        }
    }

    private static void cleanupBundledJVM(AppDescription app) {
        app.setBundleJre(null);
    }

    public static BundlerResult runit(
            BundlerSettings bundlerSettings,
            AppInfo appInfo,
            String url,
            String targetStr,
            String DEST_DIR,
            String RELEASE_DIR
    ) throws Exception {
        AppDescription app = createAppDescription(appInfo, url);
        verifyNativeLibs(app);
        Target target = Target.fromString(targetStr);
        setupBundledJVM(appInfo, app, target);
        if(target == Target.MacX64) {
            BundlerResult bundlerResult =  MacBundler.start(
                    bundlerSettings,
                    MacBundler.TargetArchitecture.X64,
                    app,
                    DEST_DIR,
                    RELEASE_DIR
            );
            bundlerResult.setResultForType("mac", bundlerResult);
            return bundlerResult;
        }

        if (target == Target.MacArm) {
            return MacBundler.start(
                    bundlerSettings,
                    MacBundler.TargetArchitecture.ARM64,
                    app,
                    DEST_DIR,
                    RELEASE_DIR
            );
        }
        
        if(target == Target.WinX64) {
            return WindowsBundler2.start(bundlerSettings, app,DEST_DIR, RELEASE_DIR);
        }
        if(target == Target.WinX64Installer) {
            return WindowsBundler2.start(bundlerSettings, app, DEST_DIR, RELEASE_DIR, true);
        }
        
        if (target == Target.LinuxX64) {
            return LinuxBundler.start(bundlerSettings, app, DEST_DIR, RELEASE_DIR);
        }
        if (target == Target.LinuxX64Installer) {
            return LinuxBundler.start(bundlerSettings, app, DEST_DIR, RELEASE_DIR, true);
        }
        
        if(target == Target.All) {
            BundlerResult out = new BundlerResult("all");
            setupBundledJVM(appInfo, app, Target.MacX64);
            out.setResultForType(
                    "mac",
                    MacBundler.start(bundlerSettings, MacBundler.TargetArchitecture.X64, app,DEST_DIR, RELEASE_DIR)
            );
            out.setResultForType(
                    "mac-x64",
                    out.getResultForType("mac", false)
            );
            cleanupBundledJVM(app);
            setupBundledJVM(appInfo, app, Target.MacArm);
            out.setResultForType(
                    "mac-arm64",
                    MacBundler.start(bundlerSettings, MacBundler.TargetArchitecture.ARM64, app,DEST_DIR, RELEASE_DIR)
            );
            cleanupBundledJVM(app);
            setupBundledJVM(appInfo, app, Target.WinX64);
            out.setResultForType("win", WindowsBundler2.start(bundlerSettings, app,DEST_DIR, RELEASE_DIR));
            cleanupBundledJVM(app);
            out.setResultForType(
                    "win-installer",
                    WindowsBundler2.start(bundlerSettings, app, DEST_DIR, RELEASE_DIR, true)
            );
            cleanupBundledJVM(app);
            setupBundledJVM(appInfo, app, Target.LinuxX64);
            out.setResultForType("linux", LinuxBundler.start(bundlerSettings, app, DEST_DIR, RELEASE_DIR));
            cleanupBundledJVM(app);
            out.setResultForType(
                    "linux-installer",
                    LinuxBundler.start(bundlerSettings, app, DEST_DIR, RELEASE_DIR, true)
            );
            return out;
        }
        
        throw new IllegalArgumentException("ERROR: unrecognized target: " + target);
        
    }

    private static void p(String[] args) {
        for(String s : args) {
            p(s);
        }
    }

    private static AppDescription parseDescriptor(File descriptor) throws Exception {

        AppDescription app = new AppDescription();
        Doc doc = XMLParser.parse(descriptor);
        app.setUrl(doc.xpathString("/app/@url"));
        app.setName(doc.xpathString("/app/@name"));
        for(Elem jarElem : doc.xpath("/app/jar")) {
            Jar jar = new Jar(jarElem.attr("name"));
            if(jarElem.hasAttr("main-class")) {
                jar.setMain(true);
                jar.setMainClass(jarElem.attr("main-class"));
            }
            if(jarElem.hasAttr("os")) {
                jar.setOS(jarElem.attr("os"));
            }
            app.addJar(jar);
        }
        for(Elem extElem : doc.xpath("/app/filetype")) {
            app.addExtension(extElem.attr("extension"),extElem.attr("mimetype"),extElem.attr("icon"));
        }
        for(Elem iconE : doc.xpath("/app/icon")) {
            System.out.println("got an icon: " + iconE.attr("name"));
            app.addIcon(iconE.attr("name"));
        }

        for(Elem nativeE : doc.xpath("/app/native")) {
            app.addNative(new NativeLib(nativeE.attr("name")));
        }

        for(Elem propE : doc.xpath("/app/property")) {
            System.out.println("adding property");
            app.addProp(new Prop(propE.attr("name"),propE.attr("value")));
        }

        return app;

    }

    private static void verifyNativeLibs(AppDescription app) throws Exception {
        for(NativeLib nlib : app.getNativeLibs()) {
            nlib.verify();
        }
    }
    private static void verifyJarDirs(List<String> jardirs) throws Exception {
        for(String dir : jardirs) {
            if(!new File(dir).exists()) {
                throw new Exception("directory: " + dir + " does not exist");
            }
        }
    }

    private static void findJars(AppDescription app, List<String> jardirs) throws Exception {
        for(Jar jar : app.getJars()) {
            for(String sdir : jardirs) {
                File dir = new File(sdir);
                for(File file : dir.listFiles()){
                    if(file.getName().equals(jar.getName())) {
                        jar.setFile(file);
                        break;
                    }
                }
                if(jar.getFile() != null) break;
            }
            if(jar.getFile() == null) {
                throw new Exception("jar " + jar.getName() + " not found");
            }
            p("matched jar with file: " + jar.getFile().getName() + " " + jar.getFile().length() + " bytes");
        }

        for(NativeLib nlib : app.getNativeLibs()) {
            p("looking for native lib: " + nlib.getName());
            for(String sdir : jardirs) {
                File dir = new File(sdir);
                for(File file : dir.listFiles()) {
                    //p("looking at: " + file.getName() + " is dir = " + file.isDirectory());
                    if(file.getName().equals(nlib.getName()) && file.isDirectory()) {
                        p("found native lib: " + file.getAbsolutePath());
                        nlib.setBaseDir(file);
                    }
                }
            }
            if(nlib.getBaseDir() == null) {
                p("WARNING: no basedir found for : " + nlib.getName());
            }
        }
    }

    private static void p(String string) {
        System.out.println(string);
    }

    
    
    public static void copyStream(InputStream fin, OutputStream fout) throws IOException {
        byte[] buf = new byte[1024*16];
        while(true) {
            int n = fin.read(buf);
            if(n < 0) break;
            fout.write(buf,0,n);
        }
        fin.close();
        fout.close();
    }

    private static enum Target {
        MacX64,
        MacArm,
        WinX64,
        LinuxX64,

        MacX64Installer,

        MacArmInstaller,

        WinX64Installer,

        LinuxX64Installer,

        All;

        static Target fromString(String target) {
            if("mac".equals(target) || "mac-x64".equals(target)) {
                return MacX64;
            }

            if ("mac-arm64".equals(target)) {
                return MacArm;
            }

            if("win".equals(target)) {
                return WinX64;
            }
            if("win-installer".equals(target)) {
                return WinX64Installer;

            }

            if ("linux".equals(target)) {
                return LinuxX64;

            }
            if ("linux-installer".equals(target)) {
                return LinuxX64Installer;
            }

            if("all".equals(target)) {
                return All;
            }

            throw new IllegalArgumentException("Target " + target + " not recognized as supported target.");
        }
    }
}
