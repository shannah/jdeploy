/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

/**
 *
 * @author shannah
 */
class XMLUtil {
    public static Document parse(InputStream input) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            return builder.parse(input);
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static Document newDocument() throws IOException {
        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();

        DocumentBuilder documentBuilder;
        try {
            documentBuilder = documentFactory.newDocumentBuilder();
            return documentBuilder.newDocument();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }

    }
    
    public static void write(Document doc, OutputStream output) throws IOException {
        try {
            // create the xml file
            //transform the DOM Object to an XML File
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            DOMSource domSource = new DOMSource(doc);
            StreamResult streamResult = new StreamResult(output);
            

            // If you use
            // StreamResult result = new StreamResult(System.out);
            // the output will be pushed to the standard output ...
            // You can use that for debugging 

            transformer.transform(domSource, streamResult);

            System.out.println("Done creating XML File");

        } catch (TransformerException tfe) {
                throw new RuntimeException(tfe);
        }
        
    }
    
    public static Iterable<Element> children(Element root) {
        return new Iterable<Element>() {
            @Override
            public Iterator<Element> iterator() {
                return childIterator(root);
            }
            
        };
    }
    
    public static Iterator<Element> childIterator(Element root) {
        NodeList children = root.getChildNodes();
        int len = children.getLength();
        return new Iterator<Element>() {
            int index=0;
            Element next;
            @Override
            public boolean hasNext() {
                while (index < len && next == null) {
                    Node n = children.item(index);
                    if (n instanceof Element) {
                        next = (Element)n;
                    }
                    index++;
                }
                return next != null;
            }

            @Override
            public Element next() {
                if (hasNext()) {
                    Element out = next;
                    next = null;
                    return out;
                }
                return null;
            }
            
        };
        
    }
}
