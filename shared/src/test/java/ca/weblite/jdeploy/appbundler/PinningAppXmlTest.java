package ca.weblite.jdeploy.appbundler;

import static org.junit.jupiter.api.Assertions.*;

import ca.weblite.jdeploy.helpers.LauncherWriterHelper;
import ca.weblite.tools.security.SelfSignedCertificateGenerator;
import com.joshondesign.appbundler.mac.MacBundler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.util.Arrays;

/**
 * Every platform's app.xml carries the pinning attributes, each with its own
 * value, and the attributes after them are not shifted.
 */
public class PinningAppXmlTest {

    private AppDescription pinnedApp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        Certificate signing = SelfSignedCertificateGenerator.generateSelfSignedCertificate(gen.generateKeyPair(), "Signing");
        Certificate root = SelfSignedCertificateGenerator.generateSelfSignedCertificate(gen.generateKeyPair(), "Root");
        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setNpmPackage("test-package");
        app.setNpmVersion("1.0.0");
        app.setIconDataURI("data:image/png;base64,test");
        app.setSplashDataURI("data:text/html;base64,PHRlc3Q+");
        app.setLauncherVersion("9.9.9");
        app.enablePackageCertificatePinning();
        app.setTrustedCertificates(Arrays.asList(signing, root));
        app.setPackageTimestampRequired(true);
        return app;
    }

    private static Element root(File xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).getDocumentElement();
    }

    private static void assertPinned(Element app) {
        assertEquals("enabled", app.getAttribute("certificate-pinning"));
        String pem = app.getAttribute("trusted-certificates");
        assertEquals(2, pem.split("-----BEGIN CERTIFICATE-----", -1).length - 1, pem);
        String fingerprints = app.getAttribute("trusted-sha1-fingerprints").trim();
        assertEquals(2, fingerprints.split("\\s+").length, fingerprints);
        assertEquals("true", app.getAttribute("timestamp-required"));
        // Attributes after the pinning ones keep their own values.
        assertEquals("9.9.9", app.getAttribute("launcher-version"));
    }

    @Test
    public void windowsAndLinuxAppXmlCarriesPinning(@TempDir Path tempDir) throws Exception {
        File xml = tempDir.resolve("app.xml").toFile();
        Method method = LauncherWriterHelper.class.getDeclaredMethod("processAppXml", AppDescription.class, File.class);
        method.setAccessible(true);
        method.invoke(null, pinnedApp(), xml);
        assertPinned(root(xml));
    }

    @Test
    public void macAppXmlCarriesPinningWithoutShiftingLaterAttributes(@TempDir Path tempDir) throws Exception {
        Method method = MacBundler.class.getDeclaredMethod("processAppXml", AppDescription.class, File.class);
        method.setAccessible(true);
        method.invoke(null, pinnedApp(), tempDir.toFile());
        assertPinned(root(tempDir.resolve("app.xml").toFile()));
    }

    @Test
    public void noPinningAttributesWhenPinningIsOff(@TempDir Path tempDir) throws Exception {
        AppDescription app = pinnedApp();
        app.disablePackageCertificatePinning();
        File xml = tempDir.resolve("app.xml").toFile();
        Method method = LauncherWriterHelper.class.getDeclaredMethod("processAppXml", AppDescription.class, File.class);
        method.setAccessible(true);
        method.invoke(null, app, xml);
        Element root = root(xml);
        assertFalse(root.hasAttribute("certificate-pinning"));
        assertFalse(root.hasAttribute("timestamp-required"));
    }
}
