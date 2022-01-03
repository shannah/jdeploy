/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;

/**
 *
 * @author shannah
 */
public class RuntimeGrantedPermissionReader implements Iterable<RuntimeGrantedPermission>, Closeable, AutoCloseable {
    private InputStream input;
    private RuntimeGrantedPermissions permissions = new RuntimeGrantedPermissions();
    private Document doc;
    
    public RuntimeGrantedPermissionReader(InputStream input) {
        this.input = input;
        
    }
    
    public RuntimeGrantedPermissions getPermissions() {
        try {
            load();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        return permissions;
    }
    
    private void load() throws IOException, SAXException {
        if (doc == null) {
            doc = XMLUtil.parse(input);
            for (Element el : XMLUtil.children(doc.getDocumentElement())) {
                permissions.add(parse(el));
            }
        }
    }
    
    private RuntimeGrantedPermission parse(Element el) {
        String className = el.getAttribute("className");
        String name = el.getAttribute("name");
        String actions = el.getAttribute("actions");
        String expires = el.getAttribute("expires");
        RuntimeGrantedPermission out = new RuntimeGrantedPermission();
        out.setClassName(className);
        if (name != null && !name.isEmpty()) {
            out.setName(name);
        }
        if (actions != null && !actions.isEmpty()) {
            out.setActions(actions);
        }
        if (expires != null && !expires.isEmpty()) {
            out.setExpires(new Date(Long.parseLong(expires)));
        }
        return out;
    }

    @Override
    public Iterator<RuntimeGrantedPermission> iterator() {
        try {
            load();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        return permissions.iterator();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
