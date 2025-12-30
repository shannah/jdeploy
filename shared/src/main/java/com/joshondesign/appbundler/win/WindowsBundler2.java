/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.joshondesign.appbundler.win;

import ca.weblite.jdeploy.appbundler.*;
import ca.weblite.jdeploy.helpers.LauncherWriterHelper;
import ca.weblite.jdeploy.installer.CliInstallerConstants;

import java.io.*;

/**
 *
 * @author shannah
 */
public class WindowsBundler2 {
    
    private static int verboseLevel;

    public enum TargetArchitecture {
        X64,
        ARM64
    }
    
    public static BundlerResult start(
            BundlerSettings bundlerSettings,
            TargetArchitecture  targetArchitecture,
            AppDescription app,
            String destDir,
            String releaseDir
    ) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        return start(bundlerSettings, targetArchitecture, app, destDir, releaseDir, false);
    }
    
    public static BundlerResult start(
            BundlerSettings bundlerSettings,
            TargetArchitecture  targetArchitecture,
            AppDescription app,
            String destDir,
            String releaseDir,
            boolean installer
    ) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        String targetNameWithArch = "win-" + targetArchitecture.name().toLowerCase();
        BundlerResult out = installer
                ? new BundlerResult(targetNameWithArch + "-installer")
                : new BundlerResult(targetNameWithArch);

        File winDir = new File(destDir, "windows-" + targetArchitecture.name().toLowerCase());
        winDir.mkdirs();
        
        File winReleaseDir = new File(releaseDir, "windows-" + targetArchitecture.name().toLowerCase());
        winReleaseDir.mkdirs();

        File exeFile = new File(winDir, app.getName()+".exe");
        if (installer) {
            exeFile = new File(winDir, app.getName()+" Installer.jci.exe");
        }
        String releaseFileName = exeFile.getName() + ".zip";
        releaseFileName = releaseFileName.replaceAll("\\s+", ".");
        File releaseFile = new File(winReleaseDir, releaseFileName);
        out.setOutputFile(exeFile);
        WindowsBundler2 bundler = new WindowsBundler2();
        bundler.writeLauncher(app, targetArchitecture, exeFile);
        Util.compressAsZip(releaseFile, exeFile);
        out.addReleaseFile(releaseFile);
        return out;
    }
    
    private void writeLauncher(
            AppDescription app,
            TargetArchitecture targetArchitecture,
            File destFile
    ) throws Exception {
        verboseLevel = Bundler.verboseLevel;
        if (verboseLevel > 0) {
            System.out.println("Creating "+destFile);
        }
        new LauncherWriterHelper().writeLauncher(
                app,
                destFile,
                getClient4JLauncherResource(targetArchitecture)
        );
    }

    private static InputStream getClient4JLauncherResource(TargetArchitecture targetArchitecture) {
        switch (targetArchitecture) {
            case X64:
                return WindowsBundler2.class.getResourceAsStream("x64/Client4JLauncher.exe");
            case ARM64:
                return WindowsBundler2.class.getResourceAsStream("arm64/Client4JLauncher.exe");
            default:
                throw new IllegalArgumentException("Target architecture " + targetArchitecture + " not supported");
        }
    }
}
