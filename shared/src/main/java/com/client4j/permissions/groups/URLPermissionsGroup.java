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
public class URLPermissionsGroup extends PermissionsGroup {
    public URLPermissionsGroup() {
        setName("Internet Access");
        setDescription("This app has requested permission to connect to one or more web servers.");
        getPatterns(true).add(new PermissionPattern("java.net.URLPermission", null));
    }
}
