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
public class LoadLibraryPermissionsGroup extends PermissionsGroup {
    public LoadLibraryPermissionsGroup() {
        setName("Native Library Access");
        setDescription("This app has requested permission to load native libraries.  This is strongly discouraged and is disabled by default for apps from untrusted sources.  Granting access to load a native library gives the app the ability to run native code that is not subject to any of Java's security restrictions, which renders all of our security precautions moot.  Only grant this access if you absolutely trust the app.");
        getPatterns(true).add(new PermissionPattern("java.lang.RuntimePermission", "loadLibrary.*"));
        getPatterns(true).add(new PermissionPattern("java.lang.RuntimePermission", "loadLibrary"));
        setWarningLevel(PermissionWarningLevel.Severe);
    }
}
