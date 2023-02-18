/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.joshondesign.appbundler.linux;

import ca.weblite.jdeploy.appbundler.*;
import ca.weblite.jdeploy.helpers.LauncherWriterHelper;
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
        File linuxDir = new File(destDir, "linux");
        linuxDir.mkdirs();
        
        File linuxReleaseDir = new File(releaseDir, "linux");
        linuxReleaseDir.mkdirs();
        
        String appName = installer ? app.getName() + " Installer.jci" : app.getName();
        File destFile = new File(linuxDir, appName);
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
        new LauncherWriterHelper().writeLauncher(app, destFile, LinuxBundler.class.getResourceAsStream("Client4JLauncher"));

        
    }
    
    private static void p(String s) {
        System.out.println(s);
    }
}
