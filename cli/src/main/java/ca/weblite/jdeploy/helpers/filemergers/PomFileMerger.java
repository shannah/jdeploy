package ca.weblite.jdeploy.helpers.filemergers;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;

@Singleton
public class PomFileMerger implements FileMerger {
    public void merge(File pomXml, File pomXmlPatch) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(pomXml);
        doc.getDocumentElement().normalize();

        Document patchDoc = dBuilder.parse(pomXmlPatch);
        patchDoc.getDocumentElement().normalize();

        mergeRecursively(doc.getDocumentElement(), patchDoc.getDocumentElement());

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);

        try (FileOutputStream fos = new FileOutputStream(pomXml)) {
            StreamResult result = new StreamResult(fos);
            transformer.transform(source, result);
        }
    }

    @Override
    public boolean isApplicableTo(File base, File patch) {
        return base.getName().equals("pom.xml")
                && patch.getName().equals(base.getName())
                && base.isFile()
                && patch.isFile();
    }

    private void mergeRecursively(Element baseElement, Element patchElement) {
        NodeList patchChildren = patchElement.getChildNodes();
        for (int i = 0; i < patchChildren.getLength(); i++) {
            Node patchChild = patchChildren.item(i);
            if (patchChild instanceof Element) {
                Element patchChildElement = (Element) patchChild;
                String tagName = patchChildElement.getTagName();
                if (baseElement.getElementsByTagName(tagName).getLength() > 0) {
                    // If the baseElement already has a child with the same tag name, merge the children
                    mergeRecursively((Element) baseElement.getElementsByTagName(tagName).item(0), patchChildElement);
                } else {
                    // Otherwise, append the patchChildElement to the baseElement
                    baseElement.appendChild(baseElement.getOwnerDocument().importNode(patchChildElement, true));
                }
            }
        }
    }
}
