/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security.net;

import java.lang.reflect.Constructor;
import java.security.Permission;

/**
 *
 * @author shannah
 */
public class PermissionRequest implements java.io.Serializable {

    
    
    /**
     * @return the className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @param className the className to set
     */
    public void setClassName(String className) {
        this.className = className;
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
     * @return the actions
     */
    public String getActions() {
        return actions;
    }

    /**
     * @param actions the actions to set
     */
    public void setActions(String actions) {
        this.actions = actions;
    }
   
    /**
     * @return the center
     */
    public java.awt.Point getCenter() {
        return center;
    }

    /**
     * @param center the center to set
     */
    public void setCenter(java.awt.Point center) {
        this.center = center;
    }
    
    public Permission createPermission() {
        try {
            Class cls = Class.forName(className);
            try {
                Constructor const2 = cls.getConstructor(String.class, String.class);
                return (Permission)const2.newInstance(name, actions);
            } catch (Throwable ex) {} 
            try {
                Constructor const1 = cls.getConstructor(String.class);
                return (Permission)const1.newInstance(name);
            } catch (Throwable t) {}
            try {
                return (Permission)cls.newInstance();
            } catch (Throwable t){}
        } catch (Throwable t){}
        
        return null;
    }

    private String className, name, actions;
    private java.awt.Point center;

}
