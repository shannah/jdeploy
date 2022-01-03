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
public class FilePermissionsGroup extends PermissionsGroup {
    public FilePermissionsGroup() {
        setName("File System Access");
        setDescription("This app has requested access to your file system.  By default, apps only have access to their own sandboxed directory of the file system.  Granting access to other locations of your file system may be risky and should only be granted as necessary - and only to apps that you trust.");
        getPatterns(true).add(new PermissionPattern("java.io.FilePermission", null));
        setWarningLevel(PermissionWarningLevel.High);
        
    }
}
