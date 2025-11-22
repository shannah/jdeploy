package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.appbundler.AppDescription;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LauncherWriterHelper splash attribute in app.xml.
 */
public class LauncherWriterHelperTest {

    @Test
    public void testProcessAppXmlIncludesSplashForNpmApp(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setSplashDataURI("data:text/html;base64,PHRlc3Q+");

        File tempXml = tempDir.resolve("app.xml").toFile();

        // Use reflection to call private method
        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.contains("splash='data:text/html;base64,PHRlc3Q+'"),
            "XML should contain splash attribute");
        assertTrue(xmlContent.contains("package='test-package'"),
            "XML should contain package attribute");
    }

    @Test
    public void testProcessAppXmlIncludesSplashForUrlApp(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setUrl("http://example.com/app.xml");
        app.setIconDataURI("data:image/png;base64,test");
        app.setSplashDataURI("data:text/html;base64,PHRlc3Q+");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.contains("splash='data:text/html;base64,PHRlc3Q+'"),
            "XML should contain splash attribute");
        assertTrue(xmlContent.contains("url='http://example.com/app.xml'"),
            "XML should contain url attribute");
    }

    @Test
    public void testProcessAppXmlHandlesNullSplash(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setSplashDataURI(null);

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        // XMLWriter should handle null gracefully - either omit or include as null
        // Just verify the file was created and is valid XML
        assertTrue(xmlContent.contains("<app"), "XML should contain app element");
        assertTrue(xmlContent.contains("name='TestApp'"), "XML should contain name");
    }

    @Test
    public void testProcessAppXmlWithBothIconAndSplash(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,icondata");
        app.setSplashDataURI("data:text/html;base64,splashdata");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.contains("icon='data:image/png;base64,icondata'"),
            "XML should contain icon attribute");
        assertTrue(xmlContent.contains("splash='data:text/html;base64,splashdata'"),
            "XML should contain splash attribute");
    }

    @Test
    public void testProcessAppXmlCreatesValidXml(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setSplashDataURI("data:text/html;base64,test");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        // Verify the file exists and contains XML header
        assertTrue(tempXml.exists(), "XML file should be created");
        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.startsWith("<?xml"), "Should start with XML declaration");
        assertTrue(xmlContent.contains("<app"), "Should contain app element");
    }

    @Test
    public void testProcessAppXmlIncludesLauncherVersionForNpmApp(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setLauncherVersion("5.4.3");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.contains("launcher-version='5.4.3'"),
            "XML should contain launcher-version attribute");
    }

    @Test
    public void testProcessAppXmlIncludesLauncherVersionForUrlApp(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setUrl("http://example.com/app.xml");
        app.setIconDataURI("data:image/png;base64,test");
        app.setLauncherVersion("5.4.3");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.contains("launcher-version='5.4.3'"),
            "XML should contain launcher-version attribute");
    }

    @Test
    public void testProcessAppXmlOmitsLauncherVersionWhenNull(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setLauncherVersion(null);

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertFalse(xmlContent.contains("launcher-version"),
            "XML should not contain launcher-version attribute when null");
    }

    @Test
    public void testProcessAppXmlOmitsLauncherVersionWhenEmpty(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setLauncherVersion("");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertFalse(xmlContent.contains("launcher-version"),
            "XML should not contain launcher-version attribute when empty");
    }

    @Test
    public void testProcessAppXmlIncludesInitialAppVersionForNpmApp(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setInitialAppVersion("2.1.0");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.contains("initial-app-version='2.1.0'"),
            "XML should contain initial-app-version attribute");
    }

    @Test
    public void testProcessAppXmlIncludesInitialAppVersionForUrlApp(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setUrl("http://example.com/app.xml");
        app.setIconDataURI("data:image/png;base64,test");
        app.setInitialAppVersion("2.1.0");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertTrue(xmlContent.contains("initial-app-version='2.1.0'"),
            "XML should contain initial-app-version attribute");
    }

    @Test
    public void testProcessAppXmlOmitsInitialAppVersionWhenNull(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setInitialAppVersion(null);

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertFalse(xmlContent.contains("initial-app-version"),
            "XML should not contain initial-app-version attribute when null");
    }

    @Test
    public void testProcessAppXmlOmitsInitialAppVersionWhenEmpty(@TempDir Path tempDir) throws Exception {
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setInitialAppVersion("");

        File tempXml = tempDir.resolve("app.xml").toFile();

        java.lang.reflect.Method method = LauncherWriterHelper.class.getDeclaredMethod(
            "processAppXml", AppDescription.class, File.class
        );
        method.setAccessible(true);
        method.invoke(null, app, tempXml);

        String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
        assertFalse(xmlContent.contains("initial-app-version"),
            "XML should not contain initial-app-version attribute when empty");
    }
}
