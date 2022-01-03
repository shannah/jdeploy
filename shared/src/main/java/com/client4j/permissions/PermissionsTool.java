/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions;

import ca.weblite.jdeploy.app.AppInfo.Permission;

/**
 *
 * @author shannah
 */
public class PermissionsTool {
    
    public static PermissionsSet createAWTPermissionsSet(PermissionWarningLevel level, boolean mergeWithLowerPermissionLevels) {
        switch (level) {
            case Low:
                return createLowRiskAWTPermissionsSet();
            case Normal:
                if (mergeWithLowerPermissionLevels) {
                    PermissionsSet out = new PermissionsSet();
                    out.addAll(createLowRiskAWTPermissionsSet());
                    out.addAll(createMediumRiskAWTPermissionsSet());
                    return out;
                } else {
                    return createMediumRiskAWTPermissionsSet();
                }
                
            case High:
                if (mergeWithLowerPermissionLevels) {
                    PermissionsSet out = new PermissionsSet();
                    out.addAll(createAWTPermissionsSet(PermissionWarningLevel.Normal, true));
                    out.addAll(createHighRiskAWTPermissionsSet());
                    return out;
                } else {
                    return createHighRiskAWTPermissionsSet();
                }
                
            case Severe:
                if (mergeWithLowerPermissionLevels) {
                    return createAWTPermissionsSet(PermissionWarningLevel.High, true);
                } else {
                    return new PermissionsSet();
                }
                
                
        }
        throw new IllegalArgumentException("Unsupported warning level "+level);
    }
    
    public static PermissionsSet createAllAWTPermissionsSet() {
        PermissionsSet out = new PermissionsSet();
        String[] perms = new String[]{
            "accessClipboard",
            "accessEventQueue",
            "accessSystemTray",
            "createRobot",
            "fullScreenExclusive",
            "listenToAllAWTEvents",
            "readDisplayPixels",
            "replaceKeyboardFocusManager",
            "setAppletStub",
            "setWindowsAlwaysOnTop",
            "showWindowWithoutWarningBanner",
            "toolkitModality",
            "watchMousePointer"
        };
        for (String perm : perms) {
            Permission p = new Permission("java.awt.AWTPermission", perm);
            out.add(p);
        }
        return out;
    }
    
    
    private static PermissionsSet createLowRiskAWTPermissionsSet() {
        PermissionsSet out = new PermissionsSet();
        String[] perms = new String[]{
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
        };
        for (String perm : perms) {
            Permission p = new Permission("java.awt.AWTPermission", perm);
            out.add(p);
        }
        return out;
    }
    
    private static PermissionsSet createMediumRiskAWTPermissionsSet() {
        PermissionsSet out = new PermissionsSet();
        String[] perms = new String[]{
            "accessClipboard",
            "accessSystemTray",
            "createRobot"
        };
        for (String perm : perms) {
            Permission p = new Permission("java.awt.AWTPermission", perm);
            out.add(p);
        }
        return out;
    }
    
    private static PermissionsSet createHighRiskAWTPermissionsSet() {
        PermissionsSet out = new PermissionsSet();
        String[] perms = new String[]{
            "readDisplayPixels"
        };
        for (String perm : perms) {
            Permission p = new Permission("java.awt.AWTPermission", perm);
            out.add(p);
        }
        return out;
    }
    
    public static PermissionsSet createSerializablePermissionsSet() {
        return createPermissionsSet("java.io.SerializablePermission", "enableSubclassImplementation", "enableSubstitution");
    }
    
    public static PermissionsSet createManagementPermissionsSet() {
        return createPermissionsSet("java.lang.management.ManagementPermission", "control", "monitor");
    }
    
    public static PermissionsSet createReflectPermissionsSet() {
        return createPermissionsSet("java.lang.reflect.ReflectPermission", "suppressAccessChecks", "newProxyInPackage.*");
    }
    
    private static PermissionsSet createAllRuntimePermissionsSet() {
        return createPermissionsSet("java.lang.RuntimePermission", 
                "createClassLoader",
                "getClassLoader",
                "setContextClassLoader",
                "enableContextClassLoaderOverride",
                "closeClassLoader",
                "setSecurityManager",
                "createSecurityManager",
                "getenv.*",
                "exitVM.*",
                "shutdownHooks",
                "setFactory",
                "setIO",
                "modifyThread",
                "stopThread",
                "modifyThreadGroup",
                "getProtectionDomain",
                "getFileSystemAttributes",
                "readFileDescriptor",
                "writeFileDescriptor",
                "loadLibrary.*",
                "accessClassInPackage.*",
                "defineClassInPackage.*",
                "accessDeclaredMembers",
                "queuePrintJob",
                "getStackTrace",
                "setDefaultUncaughtExceptionHandler",
                "preferences",
                "usePolicy",
                "selectorProvider",
                "charsetProvider"
                );
    }
    
    private static PermissionsSet createLowRiskRuntimePermissionsSet() {
        return createPermissionsSet("java.lang.RuntimePermission", 
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
                );
    }
    
    private static PermissionsSet createMediumRiskRuntimePermissionsSet() {
        return createPermissionsSet("java.lang.RuntimePermission", 
                "getenv.*",
                "getProtectionDomain",
                "getFileSystemAttributes",
                "readFileDescriptor",
                "queuePrintJob",
                "preferences",
                "writeFileDescriptor"
        );
    }
    
    private static PermissionsSet createHighRiskRuntimePermissionsSet() {
        return createPermissionsSet("java.lang.RuntimePermission", 
                "setSecurityManager",
                "createSecurityManager"
        );
    }
    
    private static PermissionsSet createSevereRiskRuntimePermissionsSet() {
        return createPermissionsSet("java.lang.RuntimePermission", 
                "usePolicy"
        );
    }
    
    public static PermissionsSet createRuntimePermissionsSet(PermissionWarningLevel level, boolean includeLowerPermissions) {
        switch (level) {
            case Low:
                return createLowRiskRuntimePermissionsSet();
            case Normal:
                if (includeLowerPermissions) {
                    return PermissionsSet.unionOf(createRuntimePermissionsSet(PermissionWarningLevel.Low, true), createMediumRiskRuntimePermissionsSet());
                } else {
                    return createMediumRiskRuntimePermissionsSet();
                }
            case High:
                if (includeLowerPermissions) {
                    return PermissionsSet.unionOf(createRuntimePermissionsSet(PermissionWarningLevel.Normal, true), createHighRiskRuntimePermissionsSet());
                } else {
                    return createHighRiskRuntimePermissionsSet();
                }
            case Severe:
                if (includeLowerPermissions) {
                    return PermissionsSet.unionOf(createRuntimePermissionsSet(PermissionWarningLevel.High, true), createSevereRiskRuntimePermissionsSet());
                } else {
                    return createSevereRiskRuntimePermissionsSet();
                }
        }
        throw new IllegalArgumentException("Unsupported permissions level "+level);
    }
    
    
    public static PermissionsSet createNetPermissionsSet() {
        return createPermissionsSet("java.net.NetPermission", 
                "setDefaultAuthenticator",
                "requestPasswordAuthentication",
                "specifyStreamHandler",
                "setProxySelector",
                "getProxySelector",
                "setCookieHandler",
                "getCookieHandler",
                "setResponseCache",
                "getResponseCache"
                );
    }
    
    public static PermissionsSet createSocketPermissionsSet(String actions, String... target) {
        int len = target.length;
        PermissionsSet out = new PermissionsSet();
        for (int i=0; i<len; i++) {
            Permission perm = new Permission();
            perm.setName("java.net.SocketPermissoon");
            perm.setTarget(target[i]);
            perm.setAction(actions);
            out.add(perm);
            
        }
        return out;
    }
    
    public static PermissionsSet createClientSocketPermissionsSet(PermissionWarningLevel level, boolean includeLowerPermissions) {
        switch (level) {
            case Low: return createSocketPermissionsSet("connect","${client4j.host}:${client4j.port}");
            case Normal: if (includeLowerPermissions) {
                return PermissionsSet.unionOf(createClientSocketPermissionsSet(PermissionWarningLevel.Low, true), createSocketPermissionsSet("connect", "*"));
            } else {
                return createSocketPermissionsSet("connect", "*");
            }
            case High: if (includeLowerPermissions) {
                return createClientSocketPermissionsSet(PermissionWarningLevel.Normal, true);
            } else {
                return new PermissionsSet();
            }
            case Severe: if (includeLowerPermissions) {
                return createClientSocketPermissionsSet(PermissionWarningLevel.High, true);
            } else {
                return new PermissionsSet();
            }
            
        }
        throw new IllegalArgumentException("Unsupported level "+level);
    }
    
    public static PermissionsSet createServerSocketPermissionsSet(PermissionWarningLevel level, boolean includeLowerPermissions) {
        switch (level) {
            case Low: return createSocketPermissionsSet("listen,accept","localhost", "127.0.0.1");
            case Normal: if (includeLowerPermissions) {
                return PermissionsSet.unionOf(createServerSocketPermissionsSet(PermissionWarningLevel.Low, true), createSocketPermissionsSet("listen,accept", "*"));
            } else {
                return createSocketPermissionsSet("listen,accept", "*");
            }
            case High: if (includeLowerPermissions) {
                return createServerSocketPermissionsSet(PermissionWarningLevel.Normal, true);
            } else {
                return new PermissionsSet();
            }
            case Severe: if (includeLowerPermissions) {
                return createServerSocketPermissionsSet(PermissionWarningLevel.High, true);
            } else {
                return new PermissionsSet();
            }
            
        }
        throw new IllegalArgumentException("Unsupported level "+level);
    }
    
    
    
    public static PermissionsSet createPermissionsSet(String name, String... targets) {
        PermissionsSet out = new PermissionsSet();
        for (String target : targets) {
            out.add(new Permission(name, target));
        }
        return out;
    }
    
    public static PermissionsSet createPermissionSet(Permission... perms) {
        PermissionsSet out = new PermissionsSet();
        for (Permission perm : perms) {
            out.add(perm);
        }
        return out;
    }
    
    /**
     * Does not include socket or file system permissions.  We handle these separately
     * 
     * @return 
     */
    public static PermissionsSet createLowRiskPermissionsSet() {
        PermissionsSet out = new PermissionsSet();
        out.addAll(createAWTPermissionsSet(PermissionWarningLevel.Low, true));
        out.addAll(createSerializablePermissionsSet());
        out.addAll(createManagementPermissionsSet());
        out.addAll(createReflectPermissionsSet());
        out.addAll(createRuntimePermissionsSet(PermissionWarningLevel.Low, true));
        out.addAll(createNetPermissionsSet());
        out.addAll(createPermissionsSet("java.sql.SQLPermission", "*"));
        out.addAll(createPermissionsSet("java.util.logging.LoggingPermission", "control"));
        out.addAll(createPermissionsSet("javax.net.ssl.SSLPermission", "*"));
        out.addAll(createPermissionsSet("javax.xml.bind.JAXBPermission", "setDatatypeConverter"));
        out.addAll(createPermissionsSet("javax.xml.ws.WebServicePermission", "publishEndpoint"));
        
        
        return out;
    }
    
    /**
     * Does not include socket or file system permissions.  We handle these separately
     * 
     * @return 
     */
    public static PermissionsSet createMediumRiskPermissionsSet(boolean includeLowerPermissions) {
        PermissionsSet out = new PermissionsSet();
        if (includeLowerPermissions) {
            out.addAll(createLowRiskPermissionsSet());
        }
        out.addAll(createAWTPermissionsSet(PermissionWarningLevel.Normal, false));
        out.addAll(createRuntimePermissionsSet(PermissionWarningLevel.Normal, false));
        return out;
    }
    
    /**
     * Does not include socket or file system permissions.  We handle these separately
     * 
     * @return 
     */
    public static PermissionsSet createHighRiskPermissionsSet(boolean includeLowerPermissions) {
        PermissionsSet out = new PermissionsSet();
        if (includeLowerPermissions) {
            out.addAll(createMediumRiskPermissionsSet(true));
        }
        out.addAll(createAWTPermissionsSet(PermissionWarningLevel.High, false));
        out.addAll(createRuntimePermissionsSet(PermissionWarningLevel.High, false));
        return out;
    }
}
