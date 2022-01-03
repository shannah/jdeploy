/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions.groups;

import com.client4j.permissions.PermissionPattern;
import com.client4j.permissions.PermissionWarningLevel;
import com.client4j.permissions.PermissionsGroup;

/**
 *
 * @author shannah
 */
public class ReadEnvironmentVariablesPermissionsGroup extends PermissionsGroup {
    public ReadEnvironmentVariablesPermissionsGroup() {
        setName("Access to Environment Variables");
        setDescription("This app has requested permission to read the environment variables in its running context.  Since environment variables may contain sensitive information, such as installation paths, and even passwords (if you store certain passwords in environment variables).  Grant this only for applications that you trust.");
        getPatterns(true).add(new PermissionPattern("java.lang.RuntimePermission", "getenv.*"));
        setWarningLevel(PermissionWarningLevel.High);
    }
}
