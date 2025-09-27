package ca.weblite.jdeploy.installer.mac;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;
import org.junit.Assume;

public class AdminLauncherGeneratorTest {

    private File testDir;
    private File mockApp;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Test only runs on macOS",
            System.getProperty("os.name").toLowerCase().contains("mac"));

        testDir = Files.createTempDirectory("admin-launcher-test").toFile();

        mockApp = new File(testDir, "TestApp.app");
        File contentsDir = new File(mockApp, "Contents");
        File macosDir = new File(contentsDir, "MacOS");
        File resourcesDir = new File(contentsDir, "Resources");

        macosDir.mkdirs();
        resourcesDir.mkdirs();

        File executable = new File(macosDir, "TestApp");
        Files.write(executable.toPath(), "#!/bin/bash\necho 'Test App Running'\n".getBytes());
        executable.setExecutable(true);

        String infoPlist = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                          "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                          "<plist version=\"1.0\">\n" +
                          "<dict>\n" +
                          "    <key>CFBundleExecutable</key>\n" +
                          "    <string>TestApp</string>\n" +
                          "    <key>CFBundleIconFile</key>\n" +
                          "    <string>AppIcon</string>\n" +
                          "</dict>\n" +
                          "</plist>";
        Files.write(new File(contentsDir, "Info.plist").toPath(), infoPlist.getBytes());

        File iconFile = new File(resourcesDir, "AppIcon.icns");
        Files.write(iconFile.toPath(), new byte[]{0, 1, 2, 3, 4});
    }

    @After
    public void tearDown() throws Exception {
        if (testDir != null && testDir.exists()) {
            deleteRecursively(testDir);
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    @Test
    public void testGenerateAdminLauncher() throws Exception {
        Assume.assumeTrue("Test only runs on macOS",
            System.getProperty("os.name").toLowerCase().contains("mac"));

        AdminLauncherGenerator generator = new AdminLauncherGenerator();
        File adminApp = generator.generateAdminLauncher(mockApp);

        assertNotNull("Admin app should be created", adminApp);
        assertTrue("Admin app should exist", adminApp.exists());
        assertTrue("Admin app should be a directory", adminApp.isDirectory());
        assertEquals("Admin app should have correct name", "TestApp" + AdminLauncherGenerator.ADMIN_LAUNCHER_SUFFIX + ".app", adminApp.getName());
        assertEquals("Admin app should be in same directory as source",
                    testDir.getAbsolutePath(), adminApp.getParentFile().getAbsolutePath());

        File adminContents = new File(adminApp, "Contents");
        assertTrue("Admin app should have Contents directory", adminContents.exists());

        File adminResources = new File(adminContents, "Resources");
        assertTrue("Admin app should have Resources directory", adminResources.exists());

        File adminIcon = new File(adminResources, "AppIcon.icns");
        assertTrue("Admin app should have copied icon", adminIcon.exists());

        File adminInfoPlist = new File(adminContents, "Info.plist");
        assertTrue("Admin app should have Info.plist", adminInfoPlist.exists());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSourceApp() throws Exception {
        Assume.assumeTrue("Test only runs on macOS",
            System.getProperty("os.name").toLowerCase().contains("mac"));

        AdminLauncherGenerator generator = new AdminLauncherGenerator();
        generator.generateAdminLauncher(new File("/nonexistent.app"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonAppBundle() throws Exception {
        Assume.assumeTrue("Test only runs on macOS",
            System.getProperty("os.name").toLowerCase().contains("mac"));

        File notAnApp = new File(testDir, "notanapp");
        notAnApp.mkdir();

        AdminLauncherGenerator generator = new AdminLauncherGenerator();
        generator.generateAdminLauncher(notAnApp);
    }
}