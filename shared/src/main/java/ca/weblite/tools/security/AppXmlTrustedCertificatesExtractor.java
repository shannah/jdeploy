package ca.weblite.tools.security;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

class AppXmlTrustedCertificatesExtractor {

    public static String extractTrustedCertificates(String xmlFilePath) {
        try {
            // Initialize the DocumentBuilderFactory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            // Create a DocumentBuilder
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Parse the XML file and load it into a Document
            Document document = builder.parse(new File(xmlFilePath));

            // Get the root element (in this case, <app>)
            Element rootElement = document.getDocumentElement();

            // Get the value of the "trusted-certificates" attribute
            String trustedCertificates = rootElement.getAttribute("trusted-certificates");

            return trustedCertificates;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
