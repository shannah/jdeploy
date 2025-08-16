package ca.weblite.jdeploy.cli.services;

import ca.weblite.tools.io.XMLUtil;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileInputStream;

public class AppXmlPropertyExtractor {
    public String extractProperty(String appXml, String propertyName) {
        try (FileInputStream fis = new FileInputStream(new File(appXml))) {
            Document doc = XMLUtil.parse(fis);
            return doc.getDocumentElement().getAttribute(propertyName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract property '" + propertyName + "' from " + appXml, e);
        }
    }
}
