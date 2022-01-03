/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


/**
 *
 * @author shannah
 */
public class PermissionTool {
    
    private static File getRuntimePolicyFile() {
        return new File(System.getProperty("client4j.runtime.policy"));
    }
    
    private static File getPolicyFile() {
        return new File(System.getProperty("client4j.policy"));
    }
    
    private static void append(File file, String contents) throws IOException {
        if (!file.exists()) {
            Files.write(file.toPath(), contents.getBytes(), StandardOpenOption.CREATE);
        } else {
            Files.write(file.toPath(), contents.getBytes(), StandardOpenOption.APPEND);
        }
    }
    
    private static void installPermission(Permission perm) throws IOException {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>)()->{
                String line = "\ngrant {\n"
                        + "  permission "+perm.getClass().getName()+" \"" + perm.getName()+"\", \""+perm.getActions()+"\";\n" 
                        + "};\n";
                getRuntimePolicyFile().getParentFile().mkdirs();
                append(getRuntimePolicyFile(), line);
                append(getPolicyFile(), line);
                
                //ProGradePolicy policy = (ProGradePolicy)Policy.getPolicy();
                //policy.addRuntimeGrant(perm);
                return null;
            });
        } catch (PrivilegedActionException ex) {
            throw (IOException)ex.getException();
        }
    }
    
    public static boolean requestPermissions(Component parentComponent, Collection<Permission> perms) {
        return requestPermissions(parentComponent, perms.toArray(new Permission[perms.size()]));
    }

    public static boolean requestPermissions(Component parentComponent, Permission... perms) {
        Set<Permission> missingPermissions = new HashSet<Permission>();
        for (Permission perm : perms) {
            try {
                AccessController.checkPermission(perm);
            } catch (AccessControlException ex) {
                missingPermissions.add(perm);
            }
        }
        if (missingPermissions.isEmpty()) {
            return true;
        }
        
        if (!EventQueue.isDispatchThread()) {
            boolean[] result = new boolean[1];
            try {
                EventQueue.invokeAndWait(()->{
                    result[0] = requestPermissions(parentComponent, missingPermissions.toArray(new Permission[missingPermissions.size()]));
                });
                return result[0];
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        JTextPane message = new JTextPane();
        message.setEditable(false);
        message.setEditorKit(new HTMLEditorKit());
        message.setContentType("text/html");
        message.setText("<p>This application is requesting the following permissions: "+missingPermissions+"</p><p>Do you wish to grant these permissions?</p>");
        if (JOptionPane.showConfirmDialog(parentComponent, message) == JOptionPane.OK_OPTION) {
            try {
                for (Permission perm : missingPermissions) {
                    installPermission(perm);
                }
                return true;
            } catch (IOException ex2) {
                ex2.printStackTrace();
                JOptionPane.showMessageDialog(parentComponent, "<html><p>Failed to install permission:</p><p> "+ex2.getMessage()+"</p></html");
                
                return false;
            }
        } else {

            return false;
        }
            
        
        
        
    }
    
    public static boolean requestPermission(Component parentComponent, Permission perm) {
        try {
            AccessController.checkPermission(perm);
            return true;
        } catch (AccessControlException ex) {
            if (!EventQueue.isDispatchThread()) {
                boolean[] result = new boolean[1];
                try {
                    EventQueue.invokeAndWait(()->{
                        result[0] = requestPermission(parentComponent, perm);
                    });
                    return result[0];
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
            JTextPane message = new JTextPane();
            message.setEditable(false);
            message.setEditorKit(new HTMLEditorKit());
            message.setContentType("text/html");
            message.setText("<p>This application is requesting the following permission: "+perm+"</p><p>Do you wish to grant this permission?</p>");
            if (JOptionPane.showConfirmDialog(parentComponent, message) == JOptionPane.OK_OPTION) {
                try {
                    installPermission(perm);
                    return true;
                } catch (IOException ex2) {
                    JOptionPane.showMessageDialog(parentComponent, "Failed to install permission: "+ex2.getMessage());
                    return false;
                }
            } else {
                
                return false;
            }
            
        }
        
        
    }
}
