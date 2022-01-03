/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions;

import ca.weblite.jdeploy.app.AppInfo;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author shannah
 */
public class PermissionsGroup {

    /**
     * @return the warningLevel
     */
    public PermissionWarningLevel getWarningLevel() {
        return warningLevel;
    }

    /**
     * @param warningLevel the warningLevel to set
     */
    public void setWarningLevel(PermissionWarningLevel warningLevel) {
        this.warningLevel = warningLevel;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }
    

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the permissions
     */
    public List<AppInfo.Permission> getPermissions() {
        return permissions;
    }
    
    public List<AppInfo.Permission> getPermissions(boolean init) {
        if (permissions == null && init) {
            permissions = new ArrayList<>();
        }
        return permissions;
    }

    

    /**
     * @return the patterns
     */
    public List<PermissionPattern> getPatterns() {
        return patterns;
    }

    public List<PermissionPattern> getPatterns(boolean init) {
        if (patterns == null && init) {
            patterns = new ArrayList<>();
        }
        return patterns;
    }
    
    public int getPriority() {
        if (patterns != null) {
            for (PermissionPattern pat : patterns) {
                if (pat.getTarget() != null && !"*".equals(pat.getTarget())) {
                    if (pat.getTarget().endsWith("*")) {
                        return 1;
                    } else {
                        return 2;
                    }
                }
            }
        }
        return 0;
    }
    
    public boolean matches(AppInfo.Permission permission) {
        if (patterns != null) {
            for (PermissionPattern pattern : patterns) {
                if (pattern.matches(permission)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    
    private String name, description;
    private List<AppInfo.Permission> permissions;
    private List<PermissionPattern> patterns;
    private PermissionWarningLevel warningLevel=PermissionWarningLevel.Normal;
    
}
