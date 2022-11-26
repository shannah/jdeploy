/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.joshondesign.appbundler.linux;

import ca.weblite.jdeploy.appbundler.*;
import com.joshondesign.appbundler.win.*;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import com.joshondesign.xml.XMLWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 * @author shannah
 */
public class LinuxBundler {
    
    private static int verboseLevel;
    public static BundlerResult start(BundlerSettings bundlerSettings, AppDescription app, String destDir, String releaseDir) throws Exception {
        return start(bundlerSettings, app, destDir, releaseDir, false);
    }
    
    public static BundlerResult start(BundlerSettings bundlerSettings, AppDescription app, String destDir, String releaseDir, boolean installer) throws Exception {
        BundlerResult out = installer ? new BundlerResult("linux-installer") : new BundlerResult("linux");
        File winDir = new File(destDir, "linux");
        winDir.mkdirs();
        
        File linuxReleaseDir = new File(releaseDir, "linux");
        linuxReleaseDir.mkdirs();
        
        String appName = installer ? app.getName() + " Installer.jci" : app.getName();
        File destFile = new File(winDir, appName);
        String releaseFileName = appName.replaceAll("\\s+", ".")+".tar.gz";
        File releaseFile = new File(linuxReleaseDir, releaseFileName);
        out.setOutputFile(destFile);
        new LinuxBundler().writeLauncher(app, destFile);
        Util.compressAsTarGz(releaseFile, destFile);
        out.addReleaseFile(releaseFile);
        return out;
    }
    
    private void writeLauncher(AppDescription app, File destFile) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        if (verboseLevel > 0) {
            System.out.println("Creating "+destFile);
        }
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            IOUtil.copy(LinuxBundler.class.getResourceAsStream("Client4JLauncher"), new FileOutputStream(destFile));
        }
        long origSize = destFile.length();
        File appXml = new File(destFile.getParentFile(), "app.xml");
        processAppXml(app, appXml);
        try (FileOutputStream fos = new FileOutputStream(destFile, true)) {
            try (FileInputStream fis = new FileInputStream(appXml)) {
                IOUtil.copy(fis, fos);
            }
            byte[] bytes = String.valueOf(origSize).getBytes("UTF-8");
            
            // Record the position of the start of the data file
            // As a UTF-8 string
            fos.write(bytes);
            
            // Record the length of the position string
            // When we read this from golang, we will walk backwards.
            // First read the last byte of the file which will tell us
            // where to start reading the position string from.
            // Then read the position string, convert it to a long,
            // and then we can read the data file from that position 
            // in the exe

            fos.write(bytes.length);
        }
        destFile.setExecutable(true, false);
        appXml.delete();
        
        
    }
    
    private static void processAppXml(AppDescription app, File dest) throws Exception {
        p("Processing the app.xml file");
        XMLWriter out = new XMLWriter(dest);
        out.header();

        if (app.getNpmPackage() != null && app.getNpmVersion() != null) {

            out.start("app",
                    "name", app.getName(),
                    "package", app.getNpmPackage(),
                    "source", app.getNpmSource(),
                    "version", app.getNpmVersion(),
                    "icon", app.getIconDataURI(),
                    "prerelease", app.isNpmPrerelease()+"",
                    "fork", ""+app.isFork()
            ).end();
        } else {
            out.start("app", "name", app.getName(), "url", app.getUrl(), "icon", app.getIconDataURI()).end();
        }
        out.close();
    }
    
    private static void p(String s) {
        System.out.println(s);
    }
}
