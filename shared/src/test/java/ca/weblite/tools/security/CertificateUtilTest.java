package ca.weblite.tools.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CertificateUtilTest {

    private String tempXmlFilePath;
    private X509Certificate certificate;
    private String certificateName = "Test Certificate";

    @BeforeEach
    public void setUp() throws Exception {
        // Generate a self-signed certificate
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        certificate = SelfSignedCertificateGenerator.generateSelfSignedCertificate(keyPair, certificateName);

        // Encode the certificate to PEM format
        String pemEncodedCert = CertificateUtil.toPemEncodedString(certificate);

        // Create a temporary XML file with the trusted-certificates attribute
        File tempXmlFile = File.createTempFile("app", ".xml");
        tempXmlFilePath = tempXmlFile.getAbsolutePath();

        // Embed the PEM-encoded certificate in the XML
        String xmlContent = "<app trusted-certificates=\"" + pemEncodedCert + "\"/>";

        try (FileWriter writer = new FileWriter(tempXmlFile)) {
            writer.write(xmlContent);
        }
    }

    @Test
    public void testLoadTrustedCertificatesFromAppXml() throws Exception {
        // Load the certificates from the app XML
        KeyStore keyStore = CertificateUtil.loadTrustedCertificatesFromAppXml(tempXmlFilePath, new CommonNameAliasProvider());
        // Extract the expected alias (common name) from the generated certificate
        String expectedAlias = certificate.getSubjectX500Principal().getName().split("CN=")[1].split(",")[0];
        // Ensure that the certificate is correctly loaded into the KeyStore
        Certificate loadedCert = keyStore.getCertificate(expectedAlias);
        assertNotNull(loadedCert, "The certificate should be present in the KeyStore");
        assertEquals(certificate, loadedCert, "The loaded certificate should match the generated certificate");
    }
}
