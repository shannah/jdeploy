package com.joshondesign.appbundler.mac;

import ca.weblite.jdeploy.appbundler.AppDescription;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the Info.plist version keys reflect the app version rather than a hard-coded 1.0.0.
 */
public class MacBundlerInfoPlistVersionTest {

    private File tmpDir;

    @AfterEach
    public void cleanup() throws IOException {
        if (tmpDir != null && tmpDir.exists()) {
            FileUtils.deleteDirectory(tmpDir);
        }
    }

    @Test
    public void processInfoPlist_withVersion_writesAppVersion() throws Exception {
        String plist = plistFor("1.0.138");
        assertTrue(plist.contains("<key>CFBundleShortVersionString</key><string>1.0.138</string>"), plist);
        assertTrue(plist.contains("<key>CFBundleVersion</key><string>1.0.138</string>"), plist);
    }

    @Test
    public void processInfoPlist_withoutVersion_fallsBackTo100() throws Exception {
        String plist = plistFor(null);
        assertTrue(plist.contains("<key>CFBundleShortVersionString</key><string>1.0.0</string>"), plist);
        assertTrue(plist.contains("<key>CFBundleVersion</key><string>1.0.0</string>"), plist);
    }

    private String plistFor(String version) throws Exception {
        tmpDir = Files.createTempDirectory("jdeploy-plist-test").toFile();
        AppDescription app = new AppDescription();
        app.setName("Test App");
        app.setVersion(version);
        MacBundler.processInfoPlist(app, tmpDir);

        return new String(
                Files.readAllBytes(new File(tmpDir, "Info.plist").toPath()),
                StandardCharsets.UTF_8
        ).replaceAll("\\s*\\n\\s*", "");
    }
}
