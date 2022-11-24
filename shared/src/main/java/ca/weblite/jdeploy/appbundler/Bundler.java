/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.jdeploy.appbundler;


import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.Workspace;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.URLUtil;
import com.joshondesign.appbundler.linux.LinuxBundler;
import com.joshondesign.appbundler.mac.MacBundler;
import com.joshondesign.appbundler.win.WindowsBundler2;
import com.joshondesign.xml.Doc;
import com.joshondesign.xml.Elem;
import com.joshondesign.xml.XMLParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;

/**
 *
 * @author joshmarinacci
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
        //File descriptor = new File(jarUrl);
        //if(!descriptor.exists()) throw new Error("Descriptor file: " + jarUrl + " does not exist");
        
        runit(null, jarUrl, target, DEST_DIR, DEST_DIR);

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

    public static BundlerResult runit(AppInfo appInfo, String url,
            String target,
            String DEST_DIR,
            String RELEASE_DIR) throws Exception {

        


        
        AppDescription app = new AppDescription();
        app.setNpmPrerelease(appInfo.isNpmAllowPrerelease());
        app.setName(appInfo.getTitle());
        app.setFork(appInfo.isFork());
        URL iconURL = URLUtil.url(appInfo.getAppURL(), "icon.png");
        app.setIconDataURI(toDataURI(iconURL));


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
                            app.enableMacNotarization(developerId, notarizePassword);
                        }
                    }
                    
                }
                
                
            }
        }
        
        
        //check xml
        verifyApp(app);
        
        verifyNativeLibs(app);

        if (appInfo.hasDocumentTypes()) {
            for (String extension : appInfo.getExtensions()) {
                app.addExtension(extension, appInfo.getMimetype(extension), appInfo.getDocumentTypeIconPath(extension));
                if (appInfo.isDocumentTypeEditor(extension)) {
                    app.addEditableExtension(extension);
                }
            }
        }
        if (appInfo.hasUrlSchemes()) {
            for (String scheme : appInfo.getUrlSchemes()) {
                app.addUrlScheme(scheme);
            }
        }

        if("mac".equals(target) || "mac-x64".equals(target)) {
            BundlerResult bundlerResult =  MacBundler.start(MacBundler.TargetArchitecture.X64, app,DEST_DIR, RELEASE_DIR);
            bundlerResult.setResultForType("mac", bundlerResult);
            return bundlerResult;
        }

        if ("mac-arm64".equals(target)) {
            return MacBundler.start(MacBundler.TargetArchitecture.ARM64, app,DEST_DIR, RELEASE_DIR);
        }
        
        if("win".equals(target)) {
            return WindowsBundler2.start(app,DEST_DIR, RELEASE_DIR);
            
        }
        if("win-installer".equals(target)) {
            return WindowsBundler2.start(app, DEST_DIR, RELEASE_DIR, true);
            
        }
        
        if ("linux".equals(target)) {
            return LinuxBundler.start(app, DEST_DIR, RELEASE_DIR);
            
        }
        if ("linux-installer".equals(target)) {
            return LinuxBundler.start(app, DEST_DIR, RELEASE_DIR, true);
        }
        
        if("all".equals(target)) {
            BundlerResult out = new BundlerResult("all");
            
            out.setResultForType("mac", MacBundler.start(MacBundler.TargetArchitecture.X64, app,DEST_DIR, RELEASE_DIR));
            out.setResultForType("mac-x64", out.getResultForType("mac", false));
            out.setResultForType("mac-arm64", MacBundler.start(MacBundler.TargetArchitecture.ARM64, app,DEST_DIR, RELEASE_DIR));
            out.setResultForType("win", WindowsBundler2.start(app,DEST_DIR, RELEASE_DIR));
            out.setResultForType("win-installer", WindowsBundler2.start(app, DEST_DIR, RELEASE_DIR, true));
            out.setResultForType("linux", LinuxBundler.start(app, DEST_DIR, RELEASE_DIR));
            out.setResultForType("linux-installer", LinuxBundler.start(app, DEST_DIR, RELEASE_DIR, true));
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

    private static void verifyApp(AppDescription app) throws Exception {
        //int mainCount = 0;
        //for(Jar jar : app.getJars()) {
        //    if(jar.isMain()) mainCount++;
        //}
        //if(mainCount == 0) {
        //    throw new Exception("The app must have at least one jar with a main class in it");
        //}
        //if(mainCount > 1) {
        //    throw new Exception("You cannot have more than one jar with a main class set");
        //}
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
}
