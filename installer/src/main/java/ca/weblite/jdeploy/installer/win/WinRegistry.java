package ca.weblite.jdeploy.installer.win;
import java.io.File;

import static com.sun.jna.platform.win32.Advapi32Util.*;
import static com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;
import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;

public class WinRegistry {



    public void addFileAssociation(String ext, String progIdBase, String progName, String mimetype, File exe, File icon, String verb, String humanReadableFileType) {
        String defaultKey = null;
        if (verb == null) {
            verb = "open";
        }
        if (progName == null) {
            progName = exe.getName();
            if (progName.endsWith(".exe")) {
                progName = progName.substring(0, progName.lastIndexOf("."));
            }
        }
        if (humanReadableFileType == null) {
            humanReadableFileType = progName + " "+ ext + " File";
        }
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        String progId = progIdBase + ext;
        String key = "Software\\Classes\\"+ext;
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
            registrySetStringValue(HKEY_CURRENT_USER, key, defaultKey, progId);
            if (mimetype != null) {
                registrySetStringValue(HKEY_CURRENT_USER, key, "ContentType", mimetype);
                if (mimetype.contains("/")) {
                    registrySetStringValue(HKEY_CURRENT_USER, key, "PerceivedType", mimetype.substring(0, mimetype.indexOf("/")));
                }
            }

        }
        key = key + "\\OpenWithProgIds";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }
        registrySetStringValue(HKEY_CURRENT_USER, key, progId, "");

        key = "Software\\Classes\\"+progId;
        String basekey = key;
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }
        registrySetStringValue(HKEY_CURRENT_USER, key, defaultKey, humanReadableFileType);
        if (icon != null && icon.exists()) {
            key = basekey + "\\DefaultIcon";
            if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
                registryCreateKey(HKEY_CURRENT_USER, key);
            }
            registrySetStringValue(HKEY_CURRENT_USER, key, defaultKey, icon.getAbsolutePath());
        }

        key = basekey + "\\shell";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }
        key = key + "\\open";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }
        if (icon != null && icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, key, "Icon", icon.getAbsolutePath());
        }
        key = key + "\\command";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }

        registrySetStringValue(HKEY_CURRENT_USER, key, defaultKey, "\""+exe.getAbsolutePath()+"\" \"%1\"");




    }

    public void notifyFileAssociationsChanged() {
        Shell32.INSTANCE.SHChangeNotify(Shell32.SHCNE_ASSOCCHANGED, Shell32.SHCNF_IDLIST, null, null);
    }
}
