/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions;

import ca.weblite.jdeploy.app.AppInfo;

/**
 *
 * @author shannah
 */
public class PermissionPattern {

    
    public PermissionPattern(String name, String target) {
        this.name = name;
        this.target = target;
    }
    
    public boolean matches(AppInfo.Permission permission) {
        return (matchesClassName(permission) && matchesPermissionName(permission) && matchesAction(permission));

    }

    private boolean matchesClassName(AppInfo.Permission permission) {
        if (name == null || "*".equals(name)) {
            return true;
        }
        if (name.contains("*")) {
            if (name.indexOf("*") == 0) {
                return permission.getName().endsWith(name.substring(1));
            } else if (name.indexOf("*") == name.length() - 1) {
                return permission.getName().startsWith(name.substring(0, name.length() - 1));
            } else {
                return permission.getName().startsWith(name.substring(0, name.indexOf("*")))
                        && permission.getName().endsWith(name.substring(name.indexOf("*") + 1));
            }
        } else {
            return permission.getName().equals(name);
        }
    }

    private boolean matchesPermissionName(AppInfo.Permission permission) {
        //TODO Handle FilePermission better

        if (target == null || "*".equals(target)) {
            return true;
        }
        if (permission.getTarget() == null || "*".equals(permission.getTarget())) {
            return false;
        }

        if (target.contains("*")) {
            if (target.indexOf("*") == 0) {
                return permission.getTarget().endsWith(target.substring(1));
            } else if (target.indexOf("*") == target.length() - 1) {
                return permission.getTarget().startsWith(target.substring(0, target.length() - 1));
            } else {
                return permission.getTarget().startsWith(target.substring(0, target.indexOf("*")))
                        && permission.getTarget().endsWith(target.substring(target.indexOf("*") + 1));
            }
        } else {
            return permission.getTarget().equals(target);
        }
    }

    private boolean matchesAction(AppInfo.Permission permission) {
        if (action == null || "*".equals(action)) {
            return true;
        }
        if (permission.getAction() == null || "*".equals(permission.getAction())) {
            return false;
        }

        // TODO:  Handle this better
        return false;
    }

    /**
     * @return the className
     */
    public String getName() {
        return name;
    }

    /**
     * @param className the className to set
     */
    public void setName(String className) {
        this.name = className;
    }

    /**
     * @return the permissionName
     */
    public String getTarget() {
        return target;
    }

    /**
     * @param permissionName the permissionName to set
     */
    public void setTarget(String permissionName) {
        this.target = permissionName;
    }

    /**
     * @return the action
     */
    public String getAction() {
        return action;
    }

    /**
     * @param action the action to set
     */
    public void setAction(String action) {
        this.action = action;
    }

    private String name, target, action;

}
