package com.joshondesign.appbundler.mac;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class MacProvisioningProfileTest {

    private File tmpDir;
    private File workingDir;
    private File contentsDir;

    @BeforeEach
    public void setUp() throws IOException {
        tmpDir = Files.createTempDirectory("provisionprofile-test").toFile();
        workingDir = new File(tmpDir, "project");
        contentsDir = new File(tmpDir, "App.app/Contents");
        assertTrue(workingDir.mkdirs());
        assertTrue(new File(contentsDir, "MacOS").mkdirs());
    }

    @AfterEach
    public void cleanup() throws IOException {
        FileUtils.deleteDirectory(tmpDir);
    }

    private File entitlements(String... keys) throws IOException {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
                + "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist version=\"1.0\">\n<dict>\n");
        for (String k : keys) {
            if (k.equals("keychain-access-groups")) {
                sb.append("  <key>").append(k).append("</key>\n  <array><string>TEAM.app</string></array>\n");
            } else {
                sb.append("  <key>").append(k).append("</key>\n  <true/>\n");
            }
        }
        sb.append("</dict>\n</plist>\n");
        File f = new File(workingDir, "jdeploy.mac.bundle.entitlements");
        FileUtils.writeStringToFile(f, sb.toString(), StandardCharsets.UTF_8);
        return f;
    }

    private void profile() throws IOException {
        FileUtils.writeStringToFile(new File(workingDir, MacProvisioningProfile.OVERRIDE_FILE), "profile-bytes",
                StandardCharsets.UTF_8);
    }

    @Test
    public void noProfileAndNoRestrictedEntitlementsDoesNothing() throws IOException {
        MacProvisioningProfile.prepare(contentsDir, entitlements("com.apple.security.cs.allow-jit"), workingDir);
        assertFalse(new File(contentsDir, MacProvisioningProfile.EMBEDDED_NAME).exists());
    }

    @Test
    public void aProfileIsEmbedded() throws IOException {
        profile();
        MacProvisioningProfile.prepare(contentsDir, entitlements("com.apple.security.cs.allow-jit"), workingDir);
        File embedded = new File(contentsDir, MacProvisioningProfile.EMBEDDED_NAME);
        assertEquals("profile-bytes", FileUtils.readFileToString(embedded, StandardCharsets.UTF_8));
    }

    @Test
    public void restrictedEntitlementsWithAProfileAreAccepted() throws IOException {
        profile();
        MacProvisioningProfile.prepare(contentsDir, entitlements("com.apple.application-identifier",
                "com.apple.developer.team-identifier", "keychain-access-groups"), workingDir);
        assertTrue(new File(contentsDir, MacProvisioningProfile.EMBEDDED_NAME).isFile());
    }

    @Test
    public void restrictedEntitlementsWithoutAProfileFail() throws IOException {
        File e = entitlements("com.apple.security.cs.allow-jit", "keychain-access-groups");
        IOException ex = assertThrows(IOException.class,
                () -> MacProvisioningProfile.prepare(contentsDir, e, workingDir));
        assertTrue(ex.getMessage().contains("keychain-access-groups"));
        assertTrue(ex.getMessage().contains(MacProvisioningProfile.OVERRIDE_FILE));
    }

    @Test
    public void restrictedEntitlementsWithTheCliLauncherFail() throws IOException {
        profile();
        FileUtils.writeStringToFile(new File(contentsDir, "MacOS/Client4JLauncher-cli"), "", StandardCharsets.UTF_8);
        File e = entitlements("com.apple.application-identifier");
        assertThrows(IOException.class, () -> MacProvisioningProfile.prepare(contentsDir, e, workingDir));
    }

    @Test
    public void restrictedKeysAreListedInOrder() throws IOException {
        File e = entitlements("com.apple.security.network.client", "com.apple.application-identifier",
                "com.apple.developer.team-identifier", "keychain-access-groups");
        assertEquals(Arrays.asList("com.apple.application-identifier", "com.apple.developer.team-identifier",
                "keychain-access-groups"), MacProvisioningProfile.restrictedEntitlements(e));
    }

    @Test
    public void theDefaultEntitlementsClaimNothingRestricted() throws IOException {
        File f = new File(tmpDir, "default.entitlements");
        try (InputStream in = MacBundler.class.getResourceAsStream("mac.bundle.entitlements")) {
            assertNotNull(in);
            FileUtils.copyInputStreamToFile(in, f);
        }
        assertEquals(Collections.emptyList(), MacProvisioningProfile.restrictedEntitlements(f));
    }
}
