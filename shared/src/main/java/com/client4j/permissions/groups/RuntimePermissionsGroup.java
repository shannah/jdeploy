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
public class RuntimePermissionsGroup extends PermissionsGroup {
    public RuntimePermissionsGroup() {
        setName("Runtime Environment Access");
        setDescription("This app has requested restricted access to the runtime environment.");
        getPatterns(true).add(new PermissionPattern("java.lang.RuntimePermission", null));
    }
}
