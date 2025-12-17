package com.joshondesign.appbundler.mac;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Focused tests for the macOS CLI launcher copy behavior.
 * These tests only exercise the logic that creates Client4JLauncher-cli next to
 * the GUI launcher when the appropriate system property or env var is set.
 */
public class MacBundlerCliLauncherTest {

    private File tmpDir;

    @After
    public void cleanup() throws IOException {
        if (tmpDir != null && tmpDir.exists()) {
            FileUtils.deleteDirectory(tmpDir);
        }
        // Clear property to avoid affecting other tests
        System.clearProperty("jdeploy.commands.exists");
    }

    @Test
    public void testCliLauncherCreatedWhenPropertySet() throws Exception {
        tmpDir = Files.createTempDirectory("macbundler-test").toFile();
        File appDir = new File(tmpDir, "TestApp.app");
        File contentsDir = new File(appDir, "Contents");
        File macosDir = new File(contentsDir, "MacOS");
        assertTrue(macosDir.mkdirs());

        File guiLauncher = new File(macosDir, "Client4JLauncher");
        String launcherContent = "LAUNCHER-TEST-BYTES";
        FileUtils.writeStringToFile(guiLauncher, launcherContent, StandardCharsets.UTF_8);
        guiLauncher.setExecutable(true, false);

        // Ensure property is set to signal CLI launcher creation
        System.setProperty("jdeploy.commands.exists", "true");

        // Call helper directly (avoids running full bundler)
        MacBundler.maybeCreateCliLauncher(contentsDir, guiLauncher);

        File cliLauncher = new File(macosDir, "Client4JLauncher-cli");
        assertTrue("CLI launcher should have been created", cliLauncher.exists());
        assertTrue("CLI launcher should be executable", cliLauncher.canExecute());

        // Byte-identical check
        String cliContent = FileUtils.readFileToString(cliLauncher, StandardCharsets.UTF_8);
        assertEquals("CLI launcher content must match GUI launcher", launcherContent, cliContent);
    }

    @Test
    public void testCliLauncherNotCreatedWhenPropertyNotSet() throws Exception {
        tmpDir = Files.createTempDirectory("macbundler-test").toFile();
        File appDir = new File(tmpDir, "TestApp.app");
        File contentsDir = new File(appDir, "Contents");
        File macosDir = new File(contentsDir, "MacOS");
        assertTrue(macosDir.mkdirs());

        File guiLauncher = new File(macosDir, "Client4JLauncher");
        String launcherContent = "LAUNCHER-TEST-BYTES";
        FileUtils.writeStringToFile(guiLauncher, launcherContent, StandardCharsets.UTF_8);
        guiLauncher.setExecutable(true, false);

        // Ensure property is not set (default)
        System.clearProperty("jdeploy.commands.exists");

        // Call helper directly (avoids running full bundler)
        MacBundler.maybeCreateCliLauncher(contentsDir, guiLauncher);

        File cliLauncher = new File(macosDir, "Client4JLauncher-cli");
        assertFalse("CLI launcher should NOT have been created", cliLauncher.exists());
    }
}
