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
public class SecurityPermissionsGroup extends PermissionsGroup {
    public SecurityPermissionsGroup() {
        setName("Security Access");
        setDescription("This app has requested access to modify policy, security, provider, signer, and identity information.  This access it blocked by default to apps from untrusted URLs and it is strongly discouraged that you allow this unless you absolutely trust the app.");
        getPatterns(true).add(new PermissionPattern("java.security.SecurityPermission", null));
        setWarningLevel(PermissionWarningLevel.High);
    }
}
