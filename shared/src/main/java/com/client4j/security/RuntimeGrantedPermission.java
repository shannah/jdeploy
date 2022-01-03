/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;



import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.Objects;
import java.util.Observable;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author shannah
 */
public class RuntimeGrantedPermission extends Observable implements java.io.Serializable {

    private static ClassLoader appClassLoader;
    
    public static void setAppClassLoader(ClassLoader loader) {
        if (appClassLoader != null) {
            throw new IllegalStateException("setAppClassLoader can only be called once");
        }
        appClassLoader = loader;
    }
    
    
    public RuntimeGrantedPermission(RuntimeGrantedPermission perm) {
        className = perm.className;
        name = perm.name;
        actions = perm.actions;
        expires = perm.expires;
        if (expires != null) {
            expires = new Date(expires.getTime());
        }
    }

    public RuntimeGrantedPermission() {
        
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
        if (!Objects.equals(className, this.className)) {
            this.className = className;
            setChanged();
        }
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
        if (!Objects.equals(name, this.name)) {
            this.name = name;
            setChanged();
        }
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
        if (!Objects.equals(actions, this.actions)) {
            this.actions = actions;
            setChanged();
        }
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
        if (!Objects.equals(expires, this.expires)) {
            this.expires = expires;
            setChanged();
        }
    }
    
    public Permission createPermission() {
        return AccessController.doPrivileged(new PrivilegedAction<Permission>() {
                @Override
                public Permission run() {
                    //System.out.println("In doPrivileged creating permission for "+RuntimeGrantedPermission.this);
                    //System.out.println("AppClassLoader: "+appClassLoader);
                    try {
                        Class cls = appClassLoader == null ?
                                Class.forName(className) :
                                appClassLoader.loadClass(className);
                        //System.out.println("Class loaded: "+cls);
                        try {
                            Constructor const2 = cls.getConstructor(String.class, String.class);
                            return (Permission)const2.newInstance(name, actions);
                        } catch (Throwable ex) {
                            //System.out.println("Name="+name+"; Actions="+actions);
                            ex.printStackTrace();
                        } 
                        try {
                            Constructor const1 = cls.getConstructor(String.class);
                            return (Permission)const1.newInstance(name);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                        try {
                            return (Permission)cls.newInstance();
                        } catch (Throwable t){
                            t.printStackTrace();
                        }
                    } catch (Throwable t){
                        t.printStackTrace();
                    }

                    return null;
                }
        });
    }
    
    public String toPolicyString() {
        return "\""+className+"\" \""+name+"\" \""+actions+"\"";
    }
    
    public String toString() {
        return toPolicyString();
    }
    
    
    private static Pattern propRegex = Pattern.compile("\\$\\{([^\\}]+)\\}");
    public void injectProperties(Properties props) {
        String src = this.getName();
        if (src == null) {
            src = "";
        }
        Matcher m = propRegex.matcher(src);
        int pos = 0;
        
        StringBuilder sb = new StringBuilder();
        while (m.find(pos)) {
            sb.append(src.substring(pos, m.start()));
            String prop = m.group(1);
            sb.append(props.getProperty(prop, ""));
            pos = m.end();
            
        }
        sb.append(src.substring(pos));
        this.setName(sb.toString());
    }
    
    private String className, name, actions;
    private Date expires;
}
