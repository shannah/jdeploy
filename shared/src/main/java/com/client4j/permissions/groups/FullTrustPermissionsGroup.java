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
public class FullTrustPermissionsGroup extends PermissionsGroup  {
    public FullTrustPermissionsGroup() {
        setName("All Access");
        setDescription("This application has requested full access to your computer.");
        getPatterns(true).add(new PermissionPattern("java.security.AllPermission", null));
        setWarningLevel(PermissionWarningLevel.Severe);
    }
}
