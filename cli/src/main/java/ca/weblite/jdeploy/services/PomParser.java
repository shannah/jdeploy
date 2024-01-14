package ca.weblite.jdeploy.services;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.io.IOException;

@Singleton
public class PomParser {

    public String getProjectName(String filePath) throws IOException {
        try {
            File pomFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("name");
            if (nodeList.getLength() > 0) {
                Node node = nodeList.item(0);
                return node.getTextContent();
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new IOException("Error parsing pom.xml file: " + e.getMessage(), e);
        }
    }

    public String getArtifactId(String filePath) throws IOException {
        try {
            File pomFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("artifactId");
            if (nodeList.getLength() > 0) {
                Node node = nodeList.item(0);
                return node.getTextContent();
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new IOException("Error parsing pom.xml file: " + e.getMessage(), e);
        }
    }

}
