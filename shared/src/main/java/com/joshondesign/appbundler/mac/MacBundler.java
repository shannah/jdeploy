package com.joshondesign.appbundler.mac;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.Util;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import ca.weblite.tools.platform.Platform;
import ca.weblite.jdeploy.appbundler.BundlerResult;
import com.client4j.publisher.server.SigningRequest;
import com.github.gino0631.icns.IcnsBuilder;
import com.github.gino0631.icns.IcnsType;
import com.joshondesign.xml.XMLWriter;
import java.awt.image.BufferedImage;

import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;

public class MacBundler {
    public static int verboseLevel = Bundler.verboseLevel;
    private static final String OSNAME = "mac";

    public enum TargetArchitecture {
        X64,
        ARM64
    }

    public static BundlerResult start(TargetArchitecture targetArchitecture, AppDescription app, String dest_dir, String releaseDir) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        File destDir = new File(dest_dir+"/"+OSNAME+"-" + targetArchitecture.name().toLowerCase()+"/");
        BundlerResult out = new BundlerResult(OSNAME);
        File appDir = new File(destDir,app.getName()+".app");
        out.setOutputFile(appDir);
        if (appDir.exists()) {
            if (verboseLevel > 0) {
                System.out.println("Deleting existing "+appDir);
            }
            FileUtil.delTree(appDir);
        }
        if (verboseLevel > 0) {
            System.out.println("Creating "+appDir);
        }
        appDir.mkdirs();
        p("app dir exists = " + appDir.exists());
        File contentsDir = new File(appDir,"Contents");
        contentsDir.mkdir();
        new File(contentsDir,"MacOS").mkdir();
        File resourcesDir = new File(contentsDir, "Resources");
        resourcesDir.mkdir();
        processIcon(app, contentsDir);
        for(String ext : app.getExtensions()) {
            String exticon = app.getExtensionIcon(ext);
            if(exticon != null) {
                File ifile = new File(exticon);
                if(ifile.exists()) {
                    processIcon(app, contentsDir, ext, ifile);
                }
                
            }
        }
        processInfoPlist(app,contentsDir);
        processAppXml(app, contentsDir);
        Bundler.copyStream(
                MacBundler.class.getResourceAsStream("PkgInfo.txt"),
                new FileOutputStream(new File(contentsDir,"PkgInfo")));

        InputStream stub_path = getClient4JLauncherResource(targetArchitecture);
        File stub_dest = new File(contentsDir,"MacOS/Client4JLauncher");
        Bundler.copyStream(stub_path,new FileOutputStream(stub_dest));

        stub_dest.setExecutable(true, false);

        SigningRequest signingRequest = new SigningRequest(app.getMacDeveloperID(), app.getMacCertificateName(), app.getMacNotarizationPassword());
        signingRequest.setCodesign(app.isMacCodeSigningEnabled());
        signingRequest.setNotarize(app.isMacNotarizationEnabled());
        signingRequest.setBundleId(app.getMacBundleId());
        if (Platform.getSystemPlatform().isMac()) {
            try {
                System.out.println("Removing extended attributes from "+appDir);
                File tmpRemoveAttributesScript = File.createTempFile("remove_atttrs", ".sh");
                try (PrintWriter scriptOut = new PrintWriter(new FileOutputStream(tmpRemoveAttributesScript))) {
                    scriptOut.println("#!/bin/bash");
                    scriptOut.println("/usr/bin/xattr -cr '"+appDir.getAbsolutePath()+"'");
                    scriptOut.println("/usr/bin/xattr -lr '"+appDir.getAbsolutePath()+"'");
                }
                tmpRemoveAttributesScript.setExecutable(true, false);
                tmpRemoveAttributesScript.deleteOnExit();
                Runtime.getRuntime().exec(tmpRemoveAttributesScript.getAbsolutePath()).waitFor();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (app.isMacCodeSigningEnabled()) {
                System.out.println("Signing "+appDir.getAbsolutePath());

                File entitlementsFile = new File("jdeploy.mac.bundle.entitlements");
                if (!entitlementsFile.exists()) {
                    entitlementsFile = File.createTempFile("jdeploy.mac.bundle", ".entitlements");
                    entitlementsFile.deleteOnExit();
                    FileUtils.copyInputStreamToFile(MacBundler.class.getResourceAsStream("mac.bundle.entitlements"), entitlementsFile);
                }
                {
                    ProcessBuilder pb = new ProcessBuilder("/usr/bin/codesign",

                            "--verbose=4",
                            "-f",
                            "--options", "runtime",
                            "-s", app.getMacCertificateName(),
                            "--entitlements", entitlementsFile.getAbsolutePath(),
                            new File(appDir, "Contents/app.xml").getAbsolutePath());
                    pb.inheritIO();
                    Process p = pb.start();
                    int exitCode = p.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("Codesign failed with exit code " + exitCode);
                    }
                }
                {
                    ProcessBuilder pb = new ProcessBuilder("/usr/bin/codesign",
                            "--deep",
                            "--verbose=4",
                            "-f",
                            "--options", "runtime",
                            "-s", app.getMacCertificateName(),
                            "--entitlements", entitlementsFile.getAbsolutePath(),
                            appDir.getAbsolutePath());


                    pb.inheritIO();
                    Process p = pb.start();
                    int exitCode = p.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("Codesign failed with exit code " + exitCode);
                    }
                }
            }
        }

        File releaseDestDir = new File(releaseDir + "/" + OSNAME);
        String releaseFileName = app.getName() + ".tar.gz";
        releaseFileName = releaseFileName.replaceAll("\\s+", ".");
        File releaseFile = new File(releaseDestDir, releaseFileName);
        releaseDestDir.mkdirs();
        
        Util.compressAsTarGz(releaseFile, appDir);
        if (Platform.getSystemPlatform().isMac() && app.isMacCodeSigningEnabled() && app.isMacNotarizationEnabled()) {
            System.out.println("Attempting to notarize app.");
            // Notarization can only be enabled if the app is signed.
            if (app.isMacNotarizationEnabled()) {
                ProcessBuilder pb = new ProcessBuilder("/usr/bin/xcrun", 
                        "altool", 
                        "--notarize-app", 
                        "--primary-bundle-id", 
                        app.getMacBundleId(),
                        "--username",
                        app.getMacDeveloperID(),
                        "--password",
                        app.getMacNotarizationPassword(),
                        "--file",
                        releaseFile.getAbsolutePath()
                );
                pb.inheritIO();
                Process p = pb.start();
                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("Notarization failed with exit code "+exitCode);
                }
            }
        }
        out.addReleaseFile(releaseFile);
        
        return out;
    }

    private static InputStream getClient4JLauncherResource(TargetArchitecture targetArchitecture) {
        switch (targetArchitecture) {
            case X64:
                return MacBundler.class.getResourceAsStream("x64/Client4JLauncher");
            case ARM64:
                return MacBundler.class.getResourceAsStream("arm64/Client4JLauncher");
            default:
                throw new IllegalArgumentException("Target architecture " + targetArchitecture + " not supported");
        }
    }
    
    private static void processAppXml(AppDescription app, File contentsDir) throws Exception {
        p("Processing the app.xml file");
        XMLWriter out = new XMLWriter(new File(contentsDir, "app.xml"));
        out.header();
        if (app.getIconDataURI() == null) {
            app.setIconDataURI("");
        }
        if (app.getNpmPackage() != null && app.getNpmVersion() != null) {

            out.start("app",
                    "name", app.getName(),
                    "package", app.getNpmPackage(),
                    "version", app.getNpmVersion(),
                    "icon", app.getIconDataURI(),
                    "prerelease", app.isNpmPrerelease() ? "true" : "false",
                    "fork", ""+app.isFork()
            ).end();
        } else {
            out.start("app", "name", app.getName(), "url", app.getUrl(), "icon", app.getIconDataURI()).end();
        }
        out.close();
    }
    
    private static void createThumbnail(File f, File contentsDir, int size) throws IOException {
        Thumbnails.of(f)
                .size(size, size)
                .useOriginalFormat()
                .toFile(new File(contentsDir, "icon-"+size+".png"));
    }
    
    private static void createThumbnails(File f, File contentsDir) throws IOException {
        for (int size : new int[]{16, 32, 64, 128, 256, 512, 1024}) {
            createThumbnail(f, contentsDir, size);
        }
    }
    
    private static FileInputStream getThumbnail(File contentsDir, int size) throws IOException {
        return new FileInputStream(new File(contentsDir, "icon-"+size+".png"));
    }
    
    private static void cleanThumbnails(File contentsDir) throws IOException {
        for (File f : contentsDir.listFiles()) {
            if (f.getName().startsWith("icon-") && f.getName().endsWith(".png")) {
                f.delete();
            }
        }
    }
    
    private static class Icon {
        /**
         * Icon file.
         */
        
        private String file;

        /**
         * OSType identifier of the icon type.
         */
        
        private String osType;

        public Icon set(String file) {
            this.file = file;

            return this;
        }

        public String getFile() {
            return file;
        }

        public String getOsType() {
            return osType;
        }
    }
    
     private static InputStream getInputStream(Icon icon) throws IOException {
        return new FileInputStream(new File(icon.getFile()));
    }
    
    private static String getOsType(Icon icon) throws IllegalArgumentException {
        String osType = icon.getOsType();

        if (osType != null) {
            return osType;

        } else {
            String name = Paths.get(icon.getFile()).getFileName().toString();

            try {
                try (InputStream is = getInputStream(icon)) {
                    try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
                        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);

                        if (imageReaders.hasNext()) {
                            ImageReader reader = imageReaders.next();

                            try {
                                String format = reader.getFormatName();
                                if (format.equals("png") || format.equals("jpeg")) {
                                    reader.setInput(iis, true, true);
                                    BufferedImage img = reader.read(0, reader.getDefaultReadParam());

                                    if (img.getWidth() == img.getHeight()) {
                                        switch (img.getWidth()) {
                                            case 16:
                                                return IcnsType.ICNS_16x16_JPEG_PNG_IMAGE.getOsType();

                                            case 32:
                                                return IcnsType.ICNS_32x32_JPEG_PNG_IMAGE.getOsType();

                                            case 64:
                                                return IcnsType.ICNS_64x64_JPEG_PNG_IMAGE.getOsType();

                                            case 128:
                                                return IcnsType.ICNS_128x128_JPEG_PNG_IMAGE.getOsType();

                                            case 256:
                                                return IcnsType.ICNS_256x256_JPEG_PNG_IMAGE.getOsType();

                                            case 512:
                                                return IcnsType.ICNS_512x512_JPEG_PNG_IMAGE.getOsType();

                                            case 1024:
                                                return IcnsType.ICNS_1024x1024_2X_JPEG_PNG_IMAGE.getOsType();
                                        }
                                    }

                                    throw new IllegalArgumentException(MessageFormat.format("Size of {0} icon is not supported", name));
                                }

                            } finally {
                                reader.dispose();
                            }
                        }

                        throw new IllegalArgumentException(MessageFormat.format("Unable to determine type of {0} icon", name));
                    }
                }

            } catch (IOException e) {
                throw new IllegalArgumentException("Error determining icon type", e);
            }
        }
    }


    private static void processIcon(AppDescription app, File contentsDir) throws Exception {
        processIcon(app, contentsDir, null, null);
    }
    
    private static void processIcon(AppDescription app, File contentsDir, String ext, File iconFile) throws Exception {
        URL iconUrl;
        if (iconFile == null) {
            iconFile = ext != null ?
                    new File(contentsDir, "icon." + ext + ".png") :
                    new File(contentsDir, "icon.png");
            iconUrl = ext != null ?
                    URLUtil.url(new URL(app.getUrl()), "icon."+ext+".png") :
                    URLUtil.url(new URL(app.getUrl()), "icon.png");
        } else {
            iconUrl = iconFile.toURI().toURL();
        }

        try (InputStream in = URLUtil.openStream(iconUrl)) {
            try (OutputStream fos = new FileOutputStream(iconFile)) {
                IOUtil.copy(in, fos);
            }
        }
        Icon icn = new Icon();
        icn.set(iconFile.getAbsolutePath());
        System.out.println("Icon File: "+iconFile.getAbsolutePath());
        String osType = null;
        try {
            osType = getOsType(icn);
        } catch (Throwable t){
            t.printStackTrace(System.err);
        }
        if (osType == null) {
            Thumbnails.of(iconFile).outputFormat("png").size(512, 512).toFile(iconFile);
            try {
                osType = getOsType(icn);
            } catch (Throwable t){
                t.printStackTrace(System.err);
            }
        }
        
        if (osType == null) {
            throw new IOException("Failed to determine type of icon file.");
        }
       
        
        //createThumbnails(iconFile, contentsDir);
        try (IcnsBuilder builder = IcnsBuilder.getInstance()) {
            
            //builder.add("icp4", getThumbnail(contentsDir, 16));
            //builder.add(IcnsType.ICNS_16x16_2X_JPEG_PNG_IMAGE, getThumbnail(contentsDir, 32));
            //builder.add("icp5", getThumbnail(contentsDir, 32));
            //builder.add("icp6", getThumbnail(contentsDir, 64));
            //builder.add("ic07", getThumbnail(contentsDir, 128));
            //builder.add(IcnsType.ICNS_128x128_2X_JPEG_PNG_IMAGE, getThumbnail(contentsDir, 256));
            //builder.add("ic08", getThumbnail(contentsDir, 256));
            builder.add(osType, new FileInputStream(iconFile));
            //builder.add("ic10", getThumbnail(contentsDir, 1024));
            File icnsFile = ext != null ?
                    new File(contentsDir, "Resources/icon."+ext+".icns") :
                    new File(contentsDir, "Resources/icon.icns");
            try (FileOutputStream out = new FileOutputStream(icnsFile)) {
                builder.build().writeTo(out);
            }
            
        }
        iconFile.delete();
        //cleanThumbnails(contentsDir);
                    
        
    }
    
    private static void processInfoPlist(AppDescription app, File contentsDir) throws Exception {
        p("Processing the info plist");

        XMLWriter out = new XMLWriter(new File(contentsDir,"Info.plist"));
        out.header();
        out.start("plist","version","1.0");
        out.start("dict");

        for(String ext : app.getExtensions()) {
            //file extensions
            String role = "Viewer";
            if (app.isEditableExtension(ext)) {
                role = "Editor";
            }
            out.start("key").text("CFBundleDocumentTypes").end();
            out.start("array").start("dict");
                out.start("key").text("CFBundleTypeExtensions").end();
                out.start("array").start("string").text(ext).end().end();
                out.start("key").text("CFBundleTypeName").end();
                out.start("string").text(ext).end();
                out.start("key").text("CFBundleTypeMIMETypes").end();
                out.start("array").start("string").text(app.getExtensionMimetype(ext)).end().end();
                out.start("key").text("CFBundleTypeRole").end();
                out.start("string").text(role).end();
                String icon = app.getExtensionIcon(ext);

                if(icon != null) {
                    out.start("key").text("CFBundleTypeIconFile").end();
                    File ifile = new File(icon);
                    System.out.println("doing icon: " + ifile.getAbsolutePath());
                    out.start("string").text(ifile.getName()).end();
                    //copy over the icon
                }
            out.end().end();
        }

        if (app.hasUrlSchemes()) {
            out.start("key").text("CFBundleURLTypes").end();
            out.start("array");
            for (String scheme : app.getUrlSchemes()) {
                out.start("dict")
                        .start("key").text("CFBundleTypeRole").end()
                        .start("string").text("Viewer").end()
                        .start("key").text("CFBundleURLName").end()
                        .start("string").text(app.getMacBundleId()+"."+scheme).end()
                        .start("key").text("CFBundleURLSchemes").end()
                        .start("array")
                        .start("string").text(scheme).end()
                        .end() // array
                        .end(); // dict

            }
            out.end(); // array
        }

        out.start("key").text("CFBundleName").end().start("string").text(app.getName()).end();
        out.start("key").text("CFBundleDisplayName").end().start("string").text(app.getName()).end();
        //out.start("key").text("CFBundleExecutable").end().start("string").text(app.getName()).end();
        out.start("key").text("NSHumanReadableCopyright").end().start("string").text(app.getName()).end();

        if (app.getMacBundleId() != null && !app.getMacBundleId().isEmpty()) {
            out.start("key").text("CFBundleIdentifier").end().start("string").text(app.getMacBundleId()).end();
        }
        out.start("key").text("CFBundleVersion").end().start("string").text("1.0.0").end();
        out.start("key").text("CFBundleShortVersionString").end().start("string").text("1.0.0").end();
        out.start("key").text("CFBundleAllowMixedLocalizations").end().start("string").text("true").end();
        out.start("key").text("CFBundleExecutable").end().start("string").text("Client4JLauncher").end();
        out.start("key").text("CFBundleDevelopmentRegion").end().start("string").text("English").end();
        out.start("key").text("CFBundlePackageType").end().start("string").text("APPL").end();
        out.start("key").text("CFBundleSignature").end().start("string").text("????").end();
        out.start("key").text("CFBundleInfoDictionaryVersion").end().start("string").text("6.0").end();
        out.start("key").text("CFBundleIconFile").end().start("string").text("icon.icns").end();
        out.start("key").text("NSHighResolutionCapable").end().start("true").end();
        out.end().end(); //dict, plist
        out.close();
        fixPlistXML(new File(contentsDir, "Info.plist"));
    }
    
    private static void fixPlistXML(File f) throws IOException {
        String contents = FileUtil.readFileToString(f);
        contents = contents.replaceAll("<true>.*?</true>", "<true/>");
        FileUtil.writeStringToFile(contents, f);
    }
    
    private static void key(XMLWriter out, String key, String value) {
        out.start("key").text(key).end().start("string").text(value).end();
    }

    private static void p(String s) {
        System.out.println(s);
    }
    
    public static int getLocalXcodeVersion() throws IOException {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/Applications/Xcode.app/Contents/Developer/usr/bin/xcodebuild", "-version"});
            InputStream input = p.getInputStream();
            Scanner scanner = new Scanner(input);
            scanner.useDelimiter("\n");
            while (scanner.hasNext()) {
                String line = scanner.next();
                if (line.startsWith("Xcode")) {
                    String[] parts = line.split(" ");
                    if (parts.length < 2) {
                        throw new IOException("Xcode version line did not contain version number.");
                    }
                    if (parts[1].indexOf(".") >= 0) {
                        parts[1] = parts[1].substring(0, parts[1].indexOf(".")).trim();
                    }
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (Exception ex) {
            throw new IOException("Problem getting Xcode version: "+ex.getMessage(), ex);
        }
        throw new IOException("Did not find any lines in Xcode version that matched the patterns we were looking for.  Returning version -1");
    }
}
