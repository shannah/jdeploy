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
        
        // If CLI commands are enabled, create a console-subsystem variant of the launcher
        if (!installer) {
            bundler.maybeCreateCliLauncher(bundlerSettings, winDir, app.getName(), exeFile);
        }
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
    
    /**
     * If CLI commands are enabled in the bundler settings, creates a console-subsystem variant
     * of the GUI launcher by copying the GUI EXE and modifying its PE header subsystem.
     * 
     * The CLI launcher is named "{appname}-cli.exe" and is placed in the same directory
     * as the GUI launcher. It is byte-identical to the GUI launcher except for the PE
     * subsystem field, which is changed from GUI (2) to Console (3).
     *
     * This method is public to allow focused unit tests to exercise only the launcher-copy
     * behavior without running the full bundling pipeline.
     *
     * @param bundlerSettings the bundler settings containing the CLI commands enabled flag
     * @param winDir the Windows output directory where the launcher EXE resides
     * @param appName the application name (used to derive the CLI launcher filename)
     * @param guiLauncher the file of the GUI launcher that was written
     * @throws IOException if copying or PE modification fails
     * @throws IllegalArgumentException if the source EXE is not a valid PE executable
     */
    public static void maybeCreateCliLauncher(BundlerSettings bundlerSettings, File winDir, String appName, File guiLauncher) 
            throws IOException, IllegalArgumentException {
        if (bundlerSettings.isCliCommandsEnabled()) {
            File cliDest = new File(winDir, appName + CliInstallerConstants.CLI_LAUNCHER_SUFFIX + ".exe");
            WindowsPESubsystemModifier.copyAndModifySubsystem(guiLauncher, cliDest);
            if (verboseLevel > 0) {
                System.out.println("Created CLI launcher: " + cliDest);
            }
        }
    }

    private static void p(String s) {
        System.out.println(s);
    }
}
