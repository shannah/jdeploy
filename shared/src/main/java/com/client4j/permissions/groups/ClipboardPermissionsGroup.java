/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions.groups;

import com.client4j.permissions.PermissionPattern;
import com.client4j.permissions.PermissionsGroup;

/**
 *
 * @author shannah
 */
public class ClipboardPermissionsGroup extends PermissionsGroup {
    public ClipboardPermissionsGroup() {
        setName("System Clipboard Access");
        setDescription("This app has requested access to the system clipboard.");
        getPatterns(true).add(new PermissionPattern("java.awt.AWTPermission", "accessClipboard"));
        getPatterns(true).add(new PermissionPattern("javafx.util.FXPermission", "accessClipboard"));
    }
}
