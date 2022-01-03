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
public class PropertyPermissionsGroup extends PermissionsGroup {
    public PropertyPermissionsGroup() {
        setName("Access to System Properties");
        setDescription("This app has requested access to read and/or write system properties.  This may potentially give access to sensitive information.");
        getPatterns(true).add(new PermissionPattern("java.util.PropertyPermission", null));
    }
}
