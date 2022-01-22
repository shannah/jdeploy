/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.joshondesign.appbundler.win;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerResult;
import ca.weblite.jdeploy.appbundler.Util;
import com.joshondesign.xml.XMLWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 * @author shannah
 */
public class WindowsBundler2 {
    
    private static int verboseLevel;
    
    public static BundlerResult start(AppDescription app, String destDir, String releaseDir) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        return start(app, destDir, releaseDir, false);
    }
    
    public static BundlerResult start(AppDescription app, String destDir, String releaseDir, boolean installer) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        BundlerResult out = installer ? new BundlerResult("win-installer") : new BundlerResult("win");
        File winDir = new File(destDir, "windows");
        winDir.mkdirs();
        
        File winReleaseDir = new File(releaseDir, "windows");
        winReleaseDir.mkdirs();
        
        
        File exeFile = new File(winDir, app.getName()+".exe");
        if (installer) {
            exeFile = new File(winDir, app.getName()+" Installer.jci.exe");
        }
        String releaseFileName = exeFile.getName() + ".zip";
        releaseFileName = releaseFileName.replaceAll("\\s+", ".");
        File releaseFile = new File(winReleaseDir, releaseFileName);
        out.setOutputFile(exeFile);
        new WindowsBundler2().writeLauncher(app, exeFile);
        Util.compressAsZip(releaseFile, exeFile);
        out.addReleaseFile(releaseFile);
        return out;
    }
    
    private void writeLauncher(AppDescription app, File destFile) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        if (verboseLevel > 0) {
            System.out.println("Creating "+destFile);
        }
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            IOUtil.copy(WindowsBundler2.class.getResourceAsStream("Client4JLauncher.exe"), new FileOutputStream(destFile));
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
        
    }
    
    private static void processAppXml(AppDescription app, File dest) throws Exception {
        p("Processing the app.xml file");
        XMLWriter out = new XMLWriter(dest);
        out.header();
        if (app.getNpmPackage() != null && app.getNpmVersion() != null) {

            out.start("app",
                    "name", app.getName(),
                    "package", app.getNpmPackage(),
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
