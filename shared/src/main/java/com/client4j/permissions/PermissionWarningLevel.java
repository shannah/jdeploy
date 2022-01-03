/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions;

/**
 *
 * @author shannah
 */
public enum PermissionWarningLevel {
    Low("Low risk.  Should be safe"),
    Normal("Should be safe, but be aware of the risks"),
    High("Potentially grants access to sensitive information.  Only grant for apps you trust"),
    Severe("Potentially provides full access to your computer.  Only grant for apps you *absolutely* trust.");
    
    private String description;
    
    private PermissionWarningLevel(String desc) {
        this.description = desc;
    }
}
