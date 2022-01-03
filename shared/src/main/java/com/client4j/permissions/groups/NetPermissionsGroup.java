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
public class NetPermissionsGroup extends PermissionsGroup {
    public NetPermissionsGroup() {
        setName("Network Stack Access");
        setDescription("This app has requested permission to the network stack, so that it can inspect and possibly modify such things as cookie handling proxy settings, and cache settings");
        getPatterns(true).add(new PermissionPattern("java.net.NetPermission", null));
    }
}
