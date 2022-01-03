/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author shannah
 */
public class RuntimeGrantedPermissionWriter implements Closeable, AutoCloseable {
    private Document doc;
    private Element root;
    private OutputStream os;
    boolean written;
    
    public RuntimeGrantedPermissionWriter(OutputStream os) throws IOException {
        doc = XMLUtil.newDocument();
        root = doc.createElement("permissions");
        doc.appendChild(root);
        this.os = os;
        //doc.getDocumentElement().appendChild(root);
        
    }
    
    public RuntimeGrantedPermissionWriter write(RuntimeGrantedPermissions perms) {
        for (RuntimeGrantedPermission perm : perms) {
            write(perm);
        }
        return this;
    }
    
    public RuntimeGrantedPermissionWriter write(RuntimeGrantedPermission perm) {
        Element el = doc.createElement("permission");
        if (perm.getClassName() == null || perm.getClassName().isEmpty()) {
            throw new IllegalArgumentException("Permission class name cannot be null or empty");
        }
        el.setAttribute("className", perm.getClassName());
        if (perm.getName() != null) {
            el.setAttribute("name", perm.getName());
        }
        if (perm.getActions() != null) {
            el.setAttribute("actions", perm.getActions());
        }
        if (perm.getExpires() != null) {
            el.setAttribute("expires", perm.getExpires().getTime()+"");
        }
        root.appendChild(el);
        return this;
    }

    public RuntimeGrantedPermissionWriter flush()  throws IOException {
        if (written) {
            return this;
        }
        XMLUtil.write(doc, os);
        written = true;
        return this;
    }
    
    @Override
    public void close() throws IOException {
        flush();
        os.close();
    }
    
    
}
