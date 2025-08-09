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

import java.io.*;

/**
 *
 * @author shannah
 */
public class LinuxBundler {

    public enum TargetArchitecture {
        X64,
        ARM64
    }
    private static int verboseLevel;
    public static BundlerResult start(
            BundlerSettings bundlerSettings,
            TargetArchitecture targetArchitecture,
            AppDescription app,
            String destDir,
            String releaseDir
    ) throws Exception {
        return start(bundlerSettings, targetArchitecture, app, destDir, releaseDir, false);
    }
    
    public static BundlerResult start(
            BundlerSettings bundlerSettings,
            TargetArchitecture targetArchitecture,
            AppDescription app,
            String destDir,
            String releaseDir,
            boolean installer
    ) throws Exception {
        BundlerResult out = installer ? new BundlerResult("linux-" + targetArchitecture.name().toLowerCase() +"-installer") : new BundlerResult("linux-" + targetArchitecture.name().toLowerCase());
        File linuxDir = new File(destDir, "linux-" + targetArchitecture.name().toLowerCase());
        linuxDir.mkdirs();
        
        File linuxReleaseDir = new File(releaseDir, "linux-"+ targetArchitecture.name().toLowerCase());
        linuxReleaseDir.mkdirs();
        
        String appName = installer ? app.getName() + " Installer.jci" : app.getName();
        File destFile = new File(linuxDir, appName);
        String releaseFileName = appName.replaceAll("\\s+", ".")+".tar.gz";
        File releaseFile = new File(linuxReleaseDir, releaseFileName);
        out.setOutputFile(destFile);
        new LinuxBundler().writeLauncher(app, targetArchitecture, destFile);
        Util.compressAsTarGz(releaseFile, destFile);
        out.addReleaseFile(releaseFile);
        return out;
    }
    
    private void writeLauncher(AppDescription app, TargetArchitecture targetArchitecture, File destFile) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        if (verboseLevel > 0) {
            System.out.println("Creating "+destFile);
        }
        new LauncherWriterHelper().writeLauncher(app, destFile, getClient4JLauncherResource(targetArchitecture));
    }

    private static InputStream getClient4JLauncherResource(TargetArchitecture targetArchitecture) {
        switch (targetArchitecture) {
            case X64:
                return LinuxBundler.class.getResourceAsStream("x64/Client4JLauncher");
            case ARM64:
                return LinuxBundler.class.getResourceAsStream("arm64/Client4JLauncher");
            default:
                throw new IllegalArgumentException("Target architecture " + targetArchitecture + " not supported");
        }
    }
    
    private static void p(String s) {
        System.out.println(s);
    }
}
