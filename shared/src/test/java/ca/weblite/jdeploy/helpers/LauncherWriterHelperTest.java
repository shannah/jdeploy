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
}
