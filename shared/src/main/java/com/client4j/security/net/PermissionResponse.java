/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security.net;

import java.util.Date;

/**
 *
 * @author shannah
 */
public class PermissionResponse implements java.io.Serializable {

    /**
     * @return the next
     */
    public PermissionResponse getNext() {
        return next;
    }

    /**
     * @param next the next to set
     */
    public void setNext(PermissionResponse next) {
        this.next = next;
    }

    /**
     * @return the approved
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * @param approved the approved to set
     */
    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    
    
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
     * @return the expires
     */
    public Date getExpires() {
        return expires;
    }

    /**
     * @param expires the expires to set
     */
    public void setExpires(Date expires) {
        this.expires = expires;
    }
    
    private String className, name, actions;

    private Date expires;
    private boolean approved;
    
    /**
     * Allows chaining multiple permissions in the response.
     */
    private PermissionResponse next;
    
    /**
     * Appends another permission to the end of the chain.
     * @param resp 
     */
    public void append(PermissionResponse resp) {
        PermissionResponse nex = this;
        while (nex.getNext() != null) {
            nex = nex.getNext();
        }
        nex.setNext(resp);
    }

}
