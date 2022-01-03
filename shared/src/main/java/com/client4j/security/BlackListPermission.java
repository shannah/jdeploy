/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;

import java.security.BasicPermission;

/**
 * Permission to access black-listed files.  The security manager maintains a "black-list"
 * that apps should not have any access to under any circumstance.  These are things
 * that would allow the app to break out of the sandbox.  For example, apps should not
 * be able to write inside the ${java.home} directory - but they can read in there.
 * Also apps should not be able to access anything in the client4j workspace directory
 * except those directories explicitly granted for that app.  Each app has it's own 
 * data directory that it has full access to, but it shouldn't be able to access, for example
 * other apps' contents.
 * 
 * @author shannah
 */
public class BlackListPermission extends BasicPermission implements java.io.Serializable {
    
    public BlackListPermission(String name) {
        super(name);
    }

    public BlackListPermission() {
        super("blacklist");
    }
    
    public BlackListPermission(String name, String actions) {
        super(name, actions);
    }
    
    
    
    
}
