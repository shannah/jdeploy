package ca.weblite.jdeploy.installer.win;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import static com.sun.jna.platform.win32.Advapi32Util.*;
import static com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;
import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;

public class WinRegistry {



    public void removeURLScheme(String scheme, File exe, File icon) {
        String key = "Software\\Classes\\" + scheme;
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            return;
        }


        key = key + "\\shell";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            return;
        }
        key = key + "\\open";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            return;
        }

        if (icon != null && icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, key, "Icon", icon.getAbsolutePath());
        }

        key = key + "\\command";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }

        registrySetStringValue(HKEY_CURRENT_USER, key, null, "\""+exe.getAbsolutePath()+"\" \"%1\"");


    }

    public String getCapabilitiesPath(String packageName) {
        return "Software\\jDeploy\\"+packageName+"\\Capabilities";
    }

    public String getFileAssociationsPath(String packageName) {
        return getCapabilitiesPath(packageName) + "\\FileAssociations";
    }

    public String getURLAssociationsPath(String packageName) {
        return getCapabilitiesPath(packageName) + "\\URLAssociations";
    }




    public void createRegistryKeyRecursive(String key) {
        if (key.contains("\\")) {
            String parent = key.substring(0, key.lastIndexOf("\\"));
            createRegistryKeyRecursive(parent);

        }
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }

    }



    public void registerApplication(String packageName, String appTitle, String appDescription, File icon) {
        String key = "Software\\RegisteredApplications";
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }
        String currVal = registryGetStringValue(HKEY_CURRENT_USER, key, appTitle);

        String capabilitiesPath = getCapabilitiesPath(packageName);
        registrySetStringValue(HKEY_CURRENT_USER, key, appTitle, capabilitiesPath);

        if (!registryKeyExists(HKEY_CURRENT_USER, capabilitiesPath)) {
            createRegistryKeyRecursive(capabilitiesPath);
        }

        registrySetStringValue(HKEY_CURRENT_USER, capabilitiesPath, "ApplicationName", appTitle);
        registrySetStringValue(HKEY_CURRENT_USER, capabilitiesPath, "ApplicationDescription", appDescription);
        if (icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, capabilitiesPath, "ApplicationIcon", icon.getAbsolutePath());
        }
    }

    public void addURLScheme(String packageName, String scheme, File exe, File icon) {
        String key = "Software\\Classes\\" + scheme;

        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
            registrySetStringValue(HKEY_CURRENT_USER, key, null, "URL:"+scheme);
            registrySetStringValue(HKEY_CURRENT_USER, key, "URL Protocol", "");
            key = key + "\\shell";
            if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
                registryCreateKey(HKEY_CURRENT_USER, key);
            }
            key = key + "\\open";
            if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
                registryCreateKey(HKEY_CURRENT_USER, key);
            }

            if (icon != null && icon.exists() && !registryValueExists(HKEY_CURRENT_USER, key, "Icon")) {
                registrySetStringValue(HKEY_CURRENT_USER, key, "Icon", icon.getAbsolutePath());
            }

            key = key + "\\command";
            if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
                registryCreateKey(HKEY_CURRENT_USER, key);
            }

            registrySetStringValue(HKEY_CURRENT_USER, key, null, "\""+exe.getAbsolutePath()+"\" \"%1\"");

            String capabilitiesPath = getCapabilitiesPath(packageName);
            createRegistryKeyRecursive(capabilitiesPath);

        }




    }


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

    public void exportKey(String key, OutputStream output) throws IOException {
        File tmpOut = File.createTempFile("exportKey", ".reg");
        try {
            exportKey(key, tmpOut);
            FileUtils.copyFile(tmpOut, output);
            output.flush();
        } finally {
            tmpOut.delete();
        }
    }

    public void exportKey(String key, File dest) throws IOException {
        if (!key.startsWith("HKEY_")) {
            key = "HKEY_CURRENT_USER" + "\\" + key;
        }
        File bat = File.createTempFile("exportKey", ".bat");
        try {
            FileUtils.writeStringToFile(bat, "REG EXPORT \"" + key + "\" \"" + dest.getAbsolutePath() + "\"\r\n", "UTF-8");
            bat.setExecutable(true, false);
            String[] command = {"cmd.exe", "/C", "Start", "/B", bat.getAbsolutePath()};

            Process p = Runtime.getRuntime().exec(command);
            try {
                int result = p.waitFor();
                if (result != 0) {
                    throw new IOException("Failed to export key "+key+" from registry. Exit code " + result);
                }
            } catch (InterruptedException ex) {
                throw new IOException("Failed to export key " + key + ".  Batch file was interrupted.", ex);
            }
        } finally {
            bat.delete();
        }

    }

    public void regImport(File src) throws IOException {

        File bat = File.createTempFile("regImport", ".bat");
        try {
            FileUtils.writeStringToFile(bat, "REG IMPORT  \""+ src.getAbsolutePath() + "\"\r\n", "UTF-8");
            bat.setExecutable(true, false);
            String[] command = {"cmd.exe", "/C", "Start", "/B", bat.getAbsolutePath()};

            Process p = Runtime.getRuntime().exec(command);
            try {
                int result = p.waitFor();
                if (result != 0) {
                    throw new IOException("Failed to import registry file to registry. Exit code " + result);
                }
            } catch (InterruptedException ex) {
                throw new IOException("Failed to import registry file.  Batch file was interrupted.", ex);
            }
        } finally {
            bat.delete();
        }
    }
}
