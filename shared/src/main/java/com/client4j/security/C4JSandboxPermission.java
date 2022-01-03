/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;

import com.client4j.security.net.PermissionRequest;

import java.io.File;
import java.io.FilePermission;
import java.security.BasicPermission;
import java.security.Permission;
import java.util.*;

/**
 *
 * @author shannah
 */
public class C4JSandboxPermission extends BasicPermission implements java.io.Serializable  {

    private static final String $(String val) {
        return System.getProperty(val);
    }
    
    private static final String path(String path) {
        return new File(path).getPath();
    }
    
    private static Permission[] initPerms() {
        
        List<Permission> lperms = new ArrayList<>();
        for (Permission p : new Permission[]{
                new FilePermission(path($("java.home")+"/lib/"), "read"),
                new FilePermission(path($("java.home")+"/lib/-"), "read"),
                new FilePermission(path($("user.dir")), "read,write,delete"),
                new FilePermission(path($("user.dir")+"/-"), "read,write,delete"),
                new FilePermission(path($("user.home")), "read,write"),
                new FilePermission(path($("user.home")), "read,write,delete"),
                new FilePermission(path($("user.home")+"/-"), "read,write,delete"),
                new FilePermission(path($("client4j.app.install.dir")), "read"),
                new FilePermission(path($("client4j.app.install.dir")+"/-"), "read"),
                new FilePermission("jar:file:"+path($("client4j.app.install.dir")+"/-"), "read"),
                new FilePermission("jar:file:"+File.separator+path($("client4j.app.install.dir")+"/-"), "read"),
                new java.net.SocketPermission($("client4j.host")+":"+$("client4j.port"), "connect,resolve"),
            }) {
            lperms.add(p);
        }
        String appClasspath = $("client4j.app.classpath");
        if (appClasspath != null) {
            String[] parts = appClasspath.split(File.pathSeparator);
            for (String part : parts) {
                lperms.add(new FilePermission(path(part), "read"));
            }
        }
        
        Set<String> safeRuntimePerms = new HashSet<>(Arrays.asList( 
                "createClassLoader",
                "getClassLoader",
                "setContextClassLoader",
                "enableContextClassLoaderOverride",
                "closeClassLoader",
                //"setSecurityManager",
                //"createSecurityManager",
                //"getenv.*",
                "exitVM.*",
                "shutdownHooks",
                "setFactory",
                "setIO",
                "modifyThread",
                "stopThread",
                "modifyThreadGroup",
                //"getProtectionDomain",
                //"getFileSystemAttributes",
                //"readFileDescriptor",
                //"writeFileDescriptor",
                //"loadLibrary.*",
                "accessClassInPackage.*",
                "defineClassInPackage.*",
                "accessDeclaredMembers",
                //"queuePrintJob",
                "getStackTrace",
                "selectorProvider",
                "charsetProvider",
                "setDefaultUncaughtExceptionHandler"//,
                //"preferences",
                //"usePolicy"
                ));
        for (String p : safeRuntimePerms) {
            lperms.add(new RuntimePermission(p));
        }
        
        Set<String> safeAwtPerms = new HashSet<>(Arrays.asList(new String[]{
            //"accessClipboard",
            "accessEventQueue",
            //"accessSystemTray",
            //"createRobot",
            "fullScreenExclusive",
            "listenToAllAWTEvents",
            //"readDisplayPixels",
            "replaceKeyboardFocusManager",
            "setAppletStub",
            "setWindowsAlwaysOnTop",
            "showWindowWithoutWarningBanner",
            "toolkitModality",
            "watchMousePointer"
        }));
        for (String p : safeAwtPerms) {
            lperms.add(new java.awt.AWTPermission(p));
        }
        
        
        
        Set<String> safeFXPermissions = new HashSet<>(Arrays.asList(new String[]{
            "accessWindowList",
            "createTransparentWindow",
            "loadFont",
            "setWindowAlwaysOnTop"
        }));
        for (String p : safeFXPermissions) {
            // For jdk8 compatibility we use reflection to create the 
            // fx permissions.
            PermissionRequest req = new PermissionRequest();
            req.setClassName("javafx.util.FXPermission");
            req.setName(p);
            Permission perm = req.createPermission();
            if (perm != null) {
                lperms.add(perm);
            }
        }
        
        Set<String> safeSerializablePerms = new HashSet<>(Arrays.asList("enableSubclassImplementation", "enableSubstitution"));
        for (String p : safeSerializablePerms) {
            lperms.add(new java.io.SerializablePermission(p));
        }
        //Set<String> safeManagerPerms = new HashSet<>(Arrays.asList("control", "monitor"));
        
        Set<String> safeReflectPerms = new HashSet<>(Arrays.asList("suppressAccessChecks", "newProxyInPackage.*"));
        for (String p : safeReflectPerms) {
            lperms.add(new java.lang.reflect.ReflectPermission(p));
        }
        Set<String> safeNetPerms = new HashSet<>(Arrays.asList("setDefaultAuthenticator",
                "requestPasswordAuthentication",
                "specifyStreamHandler",
                "setProxySelector",
                "getProxySelector",
                "setCookieHandler",
                "getCookieHandler",
                "setResponseCache",
                "getResponseCache"));
        
        for (String p : safeNetPerms) {
            lperms.add(new java.net.NetPermission(p));
        }
        //System.out.println("Allowed permissions: "+lperms);
        return lperms.toArray(new Permission[lperms.size()]);
    }
    private static final Class sqlPermission = java.sql.SQLPermission.class;
    private static final Class loggingPermission = java.util.logging.LoggingPermission.class;
    private static final Class sslPermission = javax.net.ssl.SSLPermission.class;
    private static Permission[] perms=initPerms();
    
    public C4JSandboxPermission(String name) {
        super(name);
    }
    
    public C4JSandboxPermission(String name, String actions) {
        super(name);
    }
    
    
    
    @Override
    public boolean implies(Permission permission) {
        if (permission instanceof C4JSandboxPermission) {
            return true;
        }
        //System.out.println("Checking sandbox against permission "+permission);
        for (Permission perm : perms) {
            /*
            if (perm instanceof FilePermission && permission instanceof FilePermission && permission.getName().contains("popover-arrow")) {
                try {
                    System.out.println(new File(permission.getName()).getCanonicalPath());
                } catch (Throwable t) {
                    System.err.println(t.getMessage());
                }
                System.out.println("Checking perm.implies. "+perm+" -> "+permission+" => "+perm.implies(permission));
            }
            */
            if (perm.implies(permission)) {
                return true;
            }
        }
        if (sqlPermission.isAssignableFrom(permission.getClass())) {
            return true;
        }
        if (loggingPermission.isAssignableFrom(permission.getClass())) {
            return true;
        }
        if (sslPermission.isAssignableFrom(permission.getClass())) {
            return true;
        }
        
        return false;
        
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof C4JSandboxPermission;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

}
