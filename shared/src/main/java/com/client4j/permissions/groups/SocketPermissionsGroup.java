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
public class SocketPermissionsGroup extends PermissionsGroup {
    public SocketPermissionsGroup() {
        setName("Network Access");
        setDescription("This app has requested permission to read and/or write from the network.  This may include both client and server operations.");
        getPatterns(true).add(new PermissionPattern("java.net.SocketPermission", null));
    }
}
