/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.joshondesign.appbundler.win;

import ca.weblite.jdeploy.appbundler.*;
import ca.weblite.jdeploy.helpers.LauncherWriterHelper;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import com.joshondesign.xml.XMLWriter;

import java.io.*;

/**
 *
 * @author shannah
 */
public class WindowsBundler2 {
    
    private static int verboseLevel;
    
    public static BundlerResult start(BundlerSettings bundlerSettings, AppDescription app, String destDir, String releaseDir) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        return start(bundlerSettings, app, destDir, releaseDir, false);
    }
    
    public static BundlerResult start(BundlerSettings bundlerSettings, AppDescription app, String destDir, String releaseDir, boolean installer) throws Exception {
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
        new LauncherWriterHelper().writeLauncher(app, destFile, WindowsBundler2.class.getResourceAsStream("Client4JLauncher.exe"));
        
    }
    
    private static void p(String s) {
        System.out.println(s);
    }
}
