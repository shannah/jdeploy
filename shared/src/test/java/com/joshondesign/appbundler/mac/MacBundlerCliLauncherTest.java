package com.joshondesign.appbundler.mac;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for the macOS CLI launcher copy behavior.
 * These tests only exercise the logic that creates Client4JLauncher-cli next to
 * the GUI launcher when the appropriate system property or env var is set.
 */
public class MacBundlerCliLauncherTest {

    private File tmpDir;

    @AfterEach
    public void cleanup() throws IOException {
        if (tmpDir != null && tmpDir.exists()) {
            FileUtils.deleteDirectory(tmpDir);
        }
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

        // Create bundler settings with CLI commands enabled
        BundlerSettings bundlerSettings = new BundlerSettings();
        bundlerSettings.setCliCommandsEnabled(true);

        // Call helper directly (avoids running full bundler)
        MacBundler.maybeCreateCliLauncher(bundlerSettings, contentsDir, guiLauncher);

        File cliLauncher = new File(macosDir, "Client4JLauncher-cli");
        assertTrue(cliLauncher.exists(), "CLI launcher should have been created");
        assertTrue(cliLauncher.canExecute(), "CLI launcher should be executable");

        // Byte-identical check
        String cliContent = FileUtils.readFileToString(cliLauncher, StandardCharsets.UTF_8);
        assertEquals(launcherContent, cliContent, "CLI launcher content must match GUI launcher");
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

        // Create bundler settings with CLI commands disabled (default)
        BundlerSettings bundlerSettings = new BundlerSettings();

        // Call helper directly (avoids running full bundler)
        MacBundler.maybeCreateCliLauncher(bundlerSettings, contentsDir, guiLauncher);

        File cliLauncher = new File(macosDir, "Client4JLauncher-cli");
        assertFalse(cliLauncher.exists(), "CLI launcher should NOT have been created");
    }
}
