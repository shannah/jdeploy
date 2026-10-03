package com.joshondesign.appbundler.mac;

import ca.weblite.jdeploy.appbundler.AppDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the document type associations {@link MacBundler} writes into a mac app bundle:
 * their icons and the CFBundleDocumentTypes entry in Info.plist.
 */
public class MacBundlerDocumentTypesTest {

    @TempDir
    File tmpDir;

    private File projectDir;
    private File contentsDir;
    private AppDescription app;

    @BeforeEach
    public void setUp() {
        projectDir = new File(tmpDir, "project");
        projectDir.mkdirs();
        contentsDir = new File(tmpDir, "Test.app/Contents");
        new File(contentsDir, "Resources").mkdirs();

        app = new AppDescription();
        app.setName("Test");
    }

    @Test
    public void leavesTheSourceIconsUntouched() throws Exception {
        File txtIcon = squareIcon(new File(projectDir, "txt.png"));
        File folderIcon = squareIcon(new File(projectDir, "folder.png"));
        byte[] txtBytes = Files.readAllBytes(txtIcon.toPath());
        byte[] folderBytes = Files.readAllBytes(folderIcon.toPath());
        app.addExtension("txt", "text/plain", txtIcon.getAbsolutePath());
        app.setDirectoryAssociation("Viewer", "Folder", folderIcon.getAbsolutePath());

        MacBundler.processDocumentTypeIcons(app, contentsDir);

        assertArrayEquals(txtBytes, Files.readAllBytes(txtIcon.toPath()));
        assertArrayEquals(folderBytes, Files.readAllBytes(folderIcon.toPath()));
        assertTrue(new File(contentsDir, "Resources/icon.txt.icns").isFile());
        assertTrue(new File(contentsDir, "Resources/icon.directory.icns").isFile());
        assertFalse(new File(contentsDir, "icon.txt.png").exists(), "working copy should be cleaned up");
    }

    @Test
    public void pointsThePlistAtTheGeneratedIcns() throws Exception {
        app.addExtension("txt", "text/plain", squareIcon(new File(projectDir, "txt.png")).getAbsolutePath());
        app.addExtension("md", "text/markdown", null);
        app.setDirectoryAssociation("Viewer", "Folder", squareIcon(new File(projectDir, "folder.png")).getAbsolutePath());

        MacBundler.processDocumentTypeIcons(app, contentsDir);
        MacBundler.processInfoPlist(app, contentsDir);

        String plist = readPlist();
        assertEquals(1, count(plist, "<key>CFBundleDocumentTypes</key>"));
        assertTrue(plist.contains("<string>icon.txt.icns</string>"), plist);
        assertTrue(plist.contains("<string>icon.directory.icns</string>"), plist);
        assertFalse(plist.contains("txt.png"), plist);
        assertFalse(plist.contains("folder.png"), plist);
        // md has no icon, so only txt and the folder carry CFBundleTypeIconFile
        assertEquals(2, count(plist, "<key>CFBundleTypeIconFile</key>"));
    }

    @Test
    public void omitsTheIconKeyWhenTheIconIsMissing() throws Exception {
        app.addExtension("txt", "text/plain", new File(projectDir, "missing.png").getAbsolutePath());

        MacBundler.processDocumentTypeIcons(app, contentsDir);
        MacBundler.processInfoPlist(app, contentsDir);

        String plist = readPlist();
        assertEquals(1, count(plist, "<key>CFBundleDocumentTypes</key>"));
        assertFalse(plist.contains("CFBundleTypeIconFile"), plist);
    }

    private String readPlist() throws Exception {
        return new String(Files.readAllBytes(new File(contentsDir, "Info.plist").toPath()), StandardCharsets.UTF_8);
    }

    private static int count(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static File squareIcon(File file) throws Exception {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillRect(64, 64, 384, 384);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", file);
        return file;
    }
}
