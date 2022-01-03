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
public class PreferencesPermissionsGroup extends PermissionsGroup {
    public PreferencesPermissionsGroup() {
        setName("Access to Preferences");
        setDescription("This app has requested access to the Java Preferences API.  This API is shared accross all apps within the same installation of Java so it may potentially allow this app to access the preference settings for other apps that you have installed.  Only grant this if you trust the application");
        getPatterns(true).add(new PermissionPattern("java.lang.RuntimePermission", "preferences"));
    }
}
