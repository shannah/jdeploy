package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.tools.io.MD5;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class InstallWindowsRegistry {
    private OutputStream backupLog;
    private PrintStream backupLogOut;

    private AppInfo appInfo;
    private File icon, exe;
    private RegistryOperations registryOps;
    private boolean skipWinRegistryOperations = false;
    private static final Set<String> contactExtensions = new HashSet<>();
    private static final Set<String> mediaExtensions = new HashSet<>();

    private static final Set<String> internetURLSchemes = new HashSet<>();
    private static final Set<String> mailSchemes = new HashSet<>();
    static {
        for (String extension : new String[]{"contact", "group", "p7c", "vcf"}) {
            contactExtensions.add(extension);
        }
        for (String extension : new String[]{"3ga",
                "669",
                "a52",
                "aac",
                "ac3",
                "adt",
                "adts",
                "aif",
                "aifc",
                "aiff",
                "au",
                "amr",
                "aob",
                "ape",
                "caf",
                "cda",
                "dts",
                "flac",
                "it",
                "m4a",
                "m4p",
                "mid",
                "mka",
                "mlp",
                "mod",
                "mp1",
                "mp2",
                "mp3",
                "mpc",
                "mpga",
                "oga",
                "oma",
                "opus",
                "qcp",
                "ra",
                "rmi",
                "snd",
                "s3m",
                "spx",
                "tta",
                "voc",
                "vqf",
                "w64",
                "wav",
                "wma",
                "wv",
                "xa",
                "xm",
                "3g2",
                "3gp",
                "3gp2",
                "3gpp",
                "amv",
                "asf",
                "avi",
                "bik",
                "divx",
                "drc",
                "dv",
                "dvr-ms",
                "evo",
                "f4v",
                "flv",
                "gvi",
                "gxf",
                "m1v",
                "m2t",
                "m2v",
                "m2ts",
                "m4v",
                "mkv",
                "mov",
                "mp2v",
                "mp4",
                "mp4v",
                "mpa",
                "mpe",
                "mpeg",
                "mpeg1",
                "mpeg2",
                "mpeg4",
                "mpg",
                "mpv2",
                "mts",
                "mtv",
                "mxf",
                "nsv",
                "nuv",
                "ogg",
                "ogm",
                "ogx",
                "ogv",
                "rec",
                "rm",
                "rmvb",
                "rpl",
                "thp",
                "tod",
                "tp",
                "ts",
                "tts",
                "vob",
                "vro",
                "webm",
                "wmv",
                "wtv",
                "xesc",
                "asx",
                "b4s",
                "cue",
                "ifo",
                "m3u",
                "m3u8",
                "pls",
                "ram",
                "sdp",
                "vlc",
                "wvx",
                "xspf",
                "wpl",
                "zpl",
                "iso",
                "zip",
                "rar",
                "vlt",
                "wsz"}) {
            mediaExtensions.add(extension);
        }

        for (String scheme : new String[]{"http", "https"}) {
            internetURLSchemes.add(scheme);
        }

        for (String scheme : new String[]{"mailto"}) {
            mailSchemes.add(scheme);
        }
    }

    private static final String REGISTRY_KEY_SOFTWARE = "Software";
    private static final String REGISTRY_KEY_CLIENTS = REGISTRY_KEY_SOFTWARE + "\\Clients";

    public enum AppType {
        Mail,
        Media,
        Contact,
        StartMenuInternet,
        Other;

        public String getFullRegistryPath() {
            return REGISTRY_KEY_CLIENTS + "\\" + this.name();
        }
    }

    public InstallWindowsRegistry(
            AppInfo appInfo,
            File exe,
            File icon,
            OutputStream backupLog
    ) {
        this(appInfo, exe, icon, backupLog, new JnaRegistryOperations());
    }

    public InstallWindowsRegistry(
            AppInfo appInfo,
            File exe,
            File icon,
            OutputStream backupLog,
            RegistryOperations registryOps
    ) {
        this.appInfo = appInfo;
        this.exe = exe;
        this.icon = icon;
        this.registryOps = registryOps;
        if (backupLog != null) {
            this.backupLog = backupLog;
            this.backupLogOut = new PrintStream(backupLog);
        }
    }

    /**
     * Sets whether to skip WinRegistry operations (exportKey, notifyFileAssociationsChanged, regImport).
     * This is useful when testing with in-memory registry implementations.
     */
    public void setSkipWinRegistryOperations(boolean skip) {
        this.skipWinRegistryOperations = skip;
    }

    public File getUninstallerPath() {
        String suffix = "";
        if (appInfo.getNpmVersion() != null && appInfo.getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo.getNpmVersion();
            if (v.contains("-")) {
                suffix = "-" + v.substring(v.indexOf("-") + 1);
            }
        }
        return new File(System.getProperty("user.home") + File.separator +
                ".jdeploy" + File.separator +
                "uninstallers" + File.separator +
                getFullyQualifiedPackageName() + File.separator +
                appInfo.getNpmPackage()+suffix+"-uninstall.exe");

    }

    public String getFullyQualifiedPackageName() {
        return getHashedName(null);
    }

    private Set<AppType> getAppTypes() {
        HashSet<AppType> out = new HashSet<>();
        for (String extension : appInfo.getExtensions()) {
            if (contactExtensions.contains(extension)) out.add(AppType.Contact);
            if (mediaExtensions.contains(extension)) out.add(AppType.Media);
        }
        for (String scheme : appInfo.getUrlSchemes()) {
            if (internetURLSchemes.contains(scheme)) out.add(AppType.StartMenuInternet);
            if (mailSchemes.contains(scheme)) out.add(AppType.Mail);
        }
        if (out.isEmpty()) out.add(AppType.Other);
        return out;
    }

    private AppType getAppType() {
        Set<AppType> types = getAppTypes();
        if (types.size() == 1) return types.iterator().next();
        else if (types.contains(AppType.StartMenuInternet)) return AppType.StartMenuInternet;
        else if (types.contains(AppType.Media)) return AppType.Media;
        else if (types.contains(AppType.Mail)) return AppType.Mail;
        else if (types.contains(AppType.Contact)) return AppType.Contact;
        return AppType.Other;
    }


    public String getRegisteredAppName() {
        return getHashedName("jdeploy");
    }

    private String getSourceHash() {
        if (appInfo.getNpmSource() == null || appInfo.getNpmSource().isEmpty()) {
            return null;
        }
        return MD5.getMd5(appInfo.getNpmSource());
    }

    private String getUninstallKey() {
        return "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\"+getRegisteredAppName();
    }

    /**
     * The registry key for the app information such as capabilities, file associations, url associations, etc...
     * @return
     */
    private String getRegistryPath() {
        return getAppType().getFullRegistryPath() + "\\" + getRegisteredAppName();
    }

    /**
     * The registry key for the app capabilities key.
     * @return
     */
    private String getCapabilitiesPath() {
        return getRegistryPath() + "\\Capabilities";
    }

    /**
     * The registry key for app's file associations key.
     * @return
     */
    private String getFileAssociationsPath() {
        return getCapabilitiesPath() + "\\FileAssociations";
    }

    /**
     * The registry key for the app's url associations key.
     * @return
     */
    private String getURLAssociationsPath() {
        return getCapabilitiesPath() + "\\URLAssociations";
    }

    /**
     * Gets the prog ID for the app which may be referenced in various parts of the registry.
     * At the very least, this is used in {@link #getProgBaseKey()} to form the registry path
     * to the associated file type details.
     * @return
     */
    private String getProgId() {
        return getHashedName("jdeploy") + ".file";
    }

    private String getHashedName(String prefix) {
        String sourceHash = getSourceHash();
        String suffix = "";
        if (appInfo.getNpmVersion() != null && appInfo.getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo.getNpmVersion();
            if (v.contains("-")) {
                suffix = "." + v.substring(v.indexOf("-") + 1);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix).append(".");
        }
        if (sourceHash != null && !sourceHash.isEmpty()) {
            sb.append(sourceHash).append(".");
        }
        sb.append(appInfo.getNpmPackage());
        
        if (suffix != null && !suffix.isEmpty()) {
            // Suffix logic above ensures it starts with a dot (e.g. ".alpha")
            // if it was derived from a 0.0.0- prerelease version.
            if (!suffix.startsWith(".")) {
                sb.append(".");
            }
            sb.append(suffix.startsWith(".") ? suffix : suffix);
        }

        return sb.toString();
    }

    /**
     * Gets the program's base Key under which the associated file type is stored.
     * @return
     */
    private String getProgBaseKey() {
        return "Software\\Classes\\"+getProgId();
    }

    /**
     * Gets the human readable file type for files associated with the app.  All associated
     * file types are mapped to this single file type.  May need to make this more fine-grained
     * later, but for now, this seems sufficient.
     * @return
     */
    private String getHumanReadableFileType() {
        return appInfo.getTitle() + "  File";
    }

    /**
     * Registers a file extension
     * @param ext The file extension (with no leading dot).
     */
    /**
     * Creates a registry key and all its parent keys if they don't exist.
     * Uses the injected RegistryOperations to support testing with in-memory implementations.
     */
    private void createKeyRecursive(String key) {
        if (key.contains("\\")) {
            String parent = key.substring(0, key.lastIndexOf("\\"));
            createKeyRecursive(parent);
        }
        if (!registryOps.keyExists(key)) {
            registryOps.createKey(key);
        }
    }

    private void registerFileExtension(String ext) {
        // NOTE: Not backing up to the backup log, because this can be reverted cleanly by
        // simply removing ourself from the OpenWithProgIds key.
        String mimetype = appInfo.getMimetype(ext);
        if (!ext.startsWith(".")) {
            ext = "." + ext;

        }
        String key = "Software\\Classes\\"+ext;
        if (!registryOps.keyExists(key)) {
            backupLogOut.println(";CREATE "+key);
            backupLogOut.flush();
            createKeyRecursive(key);
            registryOps.setStringValue(key, null, getProgId());
            if (mimetype != null) {
                registryOps.setStringValue(key, "ContentType", mimetype);
                if (mimetype.contains("/")) {
                    registryOps.setStringValue(
                            key,
                            "PerceivedType",
                            mimetype.substring(0, mimetype.indexOf("/")
                            )
                    );
                }
            }
        }

        // Add ourselves to the OpenWithProgIds list so that we will be able to open files of this type.
        String progIdsKey = key + "\\OpenWithProgIds";
        if (!registryOps.keyExists(progIdsKey)) {
            registryOps.createKey(progIdsKey);
        }
        registryOps.setStringValue(progIdsKey, getProgId(), "");
    }

    /**
     * Registers all app file extensions.
     */
    private void registerFileExtensions() {
        for (String ext : appInfo.getExtensions()) {
            registerFileExtension(ext);
        }
        if (registryOps.keyExists(getFileAssociationsPath())) {
            deleteKeyRecursive(getFileAssociationsPath());
        }
        createKeyRecursive(getFileAssociationsPath());
        for (String ext : appInfo.getExtensions()) {
            if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            registryOps.setStringValue(getFileAssociationsPath(), ext, getProgId());

        }
    }

    /**
     * Gets key (withing HKEY_CURRENT_USERS) for a URL scheme entry.
     * @param scheme
     * @return
     */
    private String getURLSchemeRegistryKey(String scheme) {
        return "Software\\Classes\\" + scheme;
    }



    /**
     * Heuristic to figure out whether we should modify the given URL scheme
     * in the registry.  E.g. Standard schemes like http we should leave alone.
     * But custom schemes created for our app, we want to change.
     * @param scheme
     */
    private boolean canChangeURLSchemeEntry(String scheme) {
        if (skipWinRegistryOperations) {
            return true;
        }

        // Note: HKEY_CLASSES_ROOT checks are done via static JNA calls for system-wide checks
        // This method doesn't use registryOps as it checks HKEY_CLASSES_ROOT, not HKEY_CURRENT_USER
        if (!com.sun.jna.platform.win32.Advapi32Util.registryKeyExists(com.sun.jna.platform.win32.WinReg.HKEY_CLASSES_ROOT, scheme)) {
            // If the key doesn't exist yet at all
            // then we can change it.
            return true;
        }

        if (!com.sun.jna.platform.win32.Advapi32Util.registryKeyExists(com.sun.jna.platform.win32.WinReg.HKEY_CLASSES_ROOT, scheme + "\\shell\\open\\command")) {
            // If there is no shell setting yet
            // then we can't screw stuff up - so we'll proceed
            return true;
        }

        String currentExe = com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(com.sun.jna.platform.win32.WinReg.HKEY_CLASSES_ROOT, scheme + "\\shell\\open\\command", null);
        if (currentExe == null || currentExe.contains(".jdeploy")) {
            // The current exe is a .jdeploy one, so we'll commandeer it.
            return true;
        }

        return false;
    }

    /**
     * Rolls back changes in a .reg file.
     * @param backupLog The log file in .reg file format
     * @throws IOException If there is a problem.
     */
    public static void rollback(File backupLog) throws IOException {
        RegistryOperations registryOps = new JnaRegistryOperations();
        Scanner scanner = new Scanner(backupLog, "UTF-8");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // If we added a key, we wrote them to the log file as comments of the form
            // ;CREATE key where key is like Software\Classes\...
            if (line.startsWith(";CREATE ")) {
                deleteKeyRecursiveStatic(line.substring(line.indexOf(" ")+1), registryOps);
            }
        }
        new WinRegistry().regImport(backupLog);

    }

    /**
     * Static helper for deleting registry keys recursively.
     * Used by the static rollback method and the instance deleteKeyRecursive method.
     */
    private static void deleteKeyRecursiveStatic(String key, RegistryOperations registryOps) {
        if (!registryOps.keyExists(key)) {
            return;
        }
        for (String subkey : registryOps.getKeys(key)) {
            deleteKeyRecursiveStatic(key + "\\" + subkey, registryOps);
        }
        for (String valueKey : registryOps.getValues(key).keySet()) {
            registryOps.deleteValue(key, valueKey);
        }
        registryOps.deleteKey(key);
    }

    /**
     * Registers a URL scheme in HKCU\Classes.  This will try to avoid obliterating settings for
     * public/common schemes like http by only performing this step if the key doesn't exist yet
     * or if it exists, but was created by jdeploy
     * @param scheme The scheme to add. E.g. http, mailto, etc..
     * @throws IOException If there was a problem writing to the registry or backup log.
     */
    private void registerCustomScheme(String scheme) throws IOException {
        if (!canChangeURLSchemeEntry(scheme)) {
            return;
        }
        String schemeKey = getURLSchemeRegistryKey(scheme);

        if (!skipWinRegistryOperations && registryOps.keyExists(schemeKey)) {
            // NOTE: We back up the old key to the backup log so that we can revert on uninstall.
            new WinRegistry().exportKey(schemeKey, backupLog);
        }

        if (!registryOps.keyExists(schemeKey)) {
            backupLogOut.println(";CREATE "+schemeKey);
            backupLogOut.flush();
            createKeyRecursive(schemeKey);
        }
        registryOps.setStringValue(schemeKey, null, "URL:"+scheme);
        registryOps.setStringValue(schemeKey, "URL Protocol", "");
        String shellKey = schemeKey + "\\shell";
        if (!registryOps.keyExists(shellKey)) {
            registryOps.createKey(shellKey);
        }
        String openKey = shellKey + "\\open";
        if (!registryOps.keyExists(openKey)) {
            registryOps.createKey(openKey);
        }

        if (icon != null && icon.exists() && !registryOps.valueExists(openKey, "Icon")) {
            registryOps.setStringValue(openKey, "Icon", icon.getAbsolutePath()+",0");
        }

        String commandKey = openKey + "\\command";
        if (!registryOps.keyExists(commandKey)) {
            registryOps.createKey(commandKey);
        }

        registryOps.setStringValue(commandKey, null, "\""+exe.getAbsolutePath()+"\" \"%1\"");

    }

    /**
     * Creates the single "File type" entry to be associated with the app.
     */
    private void registerFileTypeEntry() {
        String basekey = getProgBaseKey();

        if (!registryOps.keyExists(basekey)) {
            registryOps.createKey(basekey);
        }
        registryOps.setStringValue(basekey, null, getHumanReadableFileType());
        if (icon != null && icon.exists()) {
            String iconKey = basekey + "\\DefaultIcon";
            if (!registryOps.keyExists(iconKey)) {
                registryOps.createKey(iconKey);
            }
            registryOps.setStringValue(iconKey, null, icon.getAbsolutePath()+",0");
        }

        String shellKey = basekey + "\\shell";
        if (!registryOps.keyExists(shellKey)) {
            registryOps.createKey(shellKey);
        }
        String openKey = shellKey + "\\open";
        if (!registryOps.keyExists(openKey)) {
            registryOps.createKey(openKey);
        }
        if (icon != null && icon.exists()) {
            registryOps.setStringValue(openKey, "Icon", icon.getAbsolutePath()+",0");
        }
        String commandKey = openKey + "\\command";
        if (!registryOps.keyExists(commandKey)) {
            registryOps.createKey(commandKey);
        }

        registryOps.setStringValue(commandKey, null, "\""+exe.getAbsolutePath()+"\" \"%1\"");
    }



    private void registerUrlSchemes() throws IOException {
        String urlAssocPath = getURLAssociationsPath();
        if (registryOps.keyExists(urlAssocPath)) {
            deleteKeyRecursive(urlAssocPath);
        }
        createKeyRecursive(urlAssocPath);
        for (String scheme : appInfo.getUrlSchemes()) {
            registryOps.setStringValue(urlAssocPath, scheme, getProgId());
            registerCustomScheme(scheme);
        }
    }

    /**
     * Unregisters URL schemes associated with the application.
     * Removes entries from both the application's capabilities and the global URL scheme registration in HKCU\Software\Classes.
     */
    private void unregisterUrlSchemes() {
        // Remove from capabilities first
        String urlAssocPath = getURLAssociationsPath();
        if (registryOps.keyExists(urlAssocPath)) {
            deleteKeyRecursive(urlAssocPath);
        }
        
        // Remove from HKCU\Software\Classes
        for (String scheme : appInfo.getUrlSchemes()) {
            // Only delete the scheme class registration if we have permission to change/delete it
            // (e.g. we don't want to delete the 'http' scheme if it was associated)
            if (canChangeURLSchemeEntry(scheme)) {
                String schemeKey = getURLSchemeRegistryKey(scheme);
                if (registryOps.keyExists(schemeKey)) {
                    deleteKeyRecursive(schemeKey);
                }
            }
        }
    }

    /**
     * Registers directory associations in the Windows registry.
     * Creates context menu entries for "Open with {AppName}" on directories.
     */
    private void registerDirectoryAssociations() throws IOException {
        if (!appInfo.hasDirectoryAssociation()) {
            return;
        }

        String progId = getProgId();
        String appTitle = appInfo.getTitle();
        String description = appInfo.getDirectoryDescription();

        // Determine menu text
        String menuText = description != null ? description : "Open with " + appTitle;

        // Register in HKEY_CURRENT_USER\Software\Classes\Directory\shell
        String directoryShellKey = "Software\\Classes\\Directory\\shell\\" + progId;

        if (!skipWinRegistryOperations && registryOps.keyExists(directoryShellKey)) {
            // Backup existing key
            new WinRegistry().exportKey(directoryShellKey, backupLog);
        } else if (skipWinRegistryOperations || !registryOps.keyExists(directoryShellKey)) {
            backupLogOut.println(";CREATE " + directoryShellKey);
            backupLogOut.flush();
        }

        if (!registryOps.keyExists(directoryShellKey)) {
            registryOps.createKey(directoryShellKey);
        }

        // Set the display name for the context menu
        registryOps.setStringValue(directoryShellKey, null, menuText);

        // Set icon if available
        if (icon != null && icon.exists()) {
            registryOps.setStringValue(directoryShellKey, "Icon",
                icon.getAbsolutePath() + ",0");
        }

        // Create command key
        String commandKey = directoryShellKey + "\\command";
        if (!registryOps.keyExists(commandKey)) {
            registryOps.createKey(commandKey);
        }

        // Set command to launch app with directory path
        registryOps.setStringValue(commandKey, null,
            "\"" + exe.getAbsolutePath() + "\" \"%1\"");

        // Also register for Directory\Background\shell for "Open folder with..." in empty space
        String backgroundShellKey = "Software\\Classes\\Directory\\Background\\shell\\" + progId;

        if (!skipWinRegistryOperations && registryOps.keyExists(backgroundShellKey)) {
            new WinRegistry().exportKey(backgroundShellKey, backupLog);
        } else if (skipWinRegistryOperations || !registryOps.keyExists(backgroundShellKey)) {
            backupLogOut.println(";CREATE " + backgroundShellKey);
            backupLogOut.flush();
        }

        if (!registryOps.keyExists(backgroundShellKey)) {
            registryOps.createKey(backgroundShellKey);
        }

        registryOps.setStringValue(backgroundShellKey, null, menuText);

        if (icon != null && icon.exists()) {
            registryOps.setStringValue(backgroundShellKey, "Icon",
                icon.getAbsolutePath() + ",0");
        }

        String backgroundCommandKey = backgroundShellKey + "\\command";
        if (!registryOps.keyExists(backgroundCommandKey)) {
            registryOps.createKey(backgroundCommandKey);
        }

        // Use %V for background shell - represents the current directory
        registryOps.setStringValue(backgroundCommandKey, null,
            "\"" + exe.getAbsolutePath() + "\" \"%V\"");
    }

    /**
     * Unregisters directory associations from Windows registry.
     */
    private void unregisterDirectoryAssociations() {
        if (!appInfo.hasDirectoryAssociation()) {
            return;
        }

        String progId = getProgId();

        String directoryShellKey = "Software\\Classes\\Directory\\shell\\" + progId;
        deleteKeyRecursive(directoryShellKey);

        String backgroundShellKey = "Software\\Classes\\Directory\\Background\\shell\\" + progId;
        deleteKeyRecursive(backgroundShellKey);
    }

    public void unregister(File backupLogFile) throws IOException {

        if (appInfo.hasDocumentTypes()) {
            unregisterFileExtensions();
        }

        if (appInfo.hasUrlSchemes()) {
            unregisterUrlSchemes();
        }

        if (appInfo.hasDirectoryAssociation()) {
            unregisterDirectoryAssociations();
        }

        deleteUninstallEntry();
        deleteRegistryKey();

        if (!skipWinRegistryOperations && backupLogFile != null && backupLogFile.exists()) {
            new WinRegistry().regImport(backupLogFile);
        }

    }

    public void deleteRegistryKey() {
        String registryPath = getRegistryPath();
        if (registryOps.keyExists(registryPath)) {
            System.out.println("Deleting registry key " + registryPath);
            deleteKeyRecursive(registryPath);
        }

        // Also remove from RegisteredApplications
        String registeredAppsKey = "Software\\RegisteredApplications";
        if (registryOps.keyExists(registeredAppsKey)) {
            registryOps.deleteValue(registeredAppsKey, getRegisteredAppName());
        }
    }


    private void unregisterFileExtensions() {
        for (String ext : appInfo.getExtensions()) {
            unregisterFileExtension(ext);
        }
        deleteFileTypeEntry();

    }

    private void deleteUninstallEntry() {
        if (registryOps.keyExists(getUninstallKey())) {
            System.out.println("Deleting uninstall key: "+getUninstallKey());
            deleteKeyRecursive(getUninstallKey());
        }
    }

    private void unregisterFileExtension(String ext) {
        System.out.println("Unregistering file extension "+ext);
        WinRegistry registry = new WinRegistry();
        String mimetype = appInfo.getMimetype(ext);
        if (!ext.startsWith(".")) {
            ext = "." + ext;

        }
        String key = "Software\\Classes\\"+ext+"\\OpenWithProgIds";

        if (!registryOps.keyExists(key)) {
            return;
        }
        System.out.println("Deleting registry value for key "+key+" vaue="+getProgId());

        deleteKeyRecursive(getProgId());
    }

    private void deleteFileTypeEntry() {
        String basekey = getProgBaseKey();
        System.out.println("Deleting file type entry "+basekey);
        if (!registryOps.keyExists(basekey)) {
            return;
        }
        System.out.println("Deleting registry key: "+basekey);
        deleteKeyRecursive(basekey);

    }

    public void register() throws IOException {
        WinRegistry registry = new WinRegistry();
        String registryPath = getRegistryPath();
        String capabilitiesPath = getCapabilitiesPath();
        if (!skipWinRegistryOperations && registryOps.keyExists(registryPath)) {
            registry.exportKey(registryPath, backupLog);
        }

        // Ensure the base registry path exists for the application
        createKeyRecursive(registryPath);

        createKeyRecursive(capabilitiesPath);
        registryOps.setStringValue(capabilitiesPath, "ApplicationName", appInfo.getTitle());
        registryOps.setStringValue(capabilitiesPath, "ApplicationDescription", appInfo.getDescription());
        if (icon != null && icon.exists()) {
            registryOps.setStringValue(capabilitiesPath, "ApplicationIcon", icon.getAbsolutePath()+",0");
        }

        if (appInfo.hasDocumentTypes() || appInfo.hasUrlSchemes() || appInfo.hasDirectoryAssociation()) {
            // Register application file type entry which is referenced.
            registerFileTypeEntry();

            // Register file extensions individually in Classes
            if (appInfo.hasDocumentTypes()) {
                registerFileExtensions();
            }
        }

        if (appInfo.hasUrlSchemes()) {
            registerUrlSchemes();
        }

        if (appInfo.hasDirectoryAssociation()) {
            registerDirectoryAssociations();
        }

        // Register the application
        String registeredAppsKey = "Software\\RegisteredApplications";
        if (!registryOps.keyExists(registeredAppsKey)) {
            createKeyRecursive(registeredAppsKey);
        }
        registryOps.setStringValue(
                registeredAppsKey,
                getRegisteredAppName(),
                getCapabilitiesPath()
        );

        // Now to register the uninstaller

        createKeyRecursive(getUninstallKey());
        if (icon != null && icon.exists()) {
            registryOps.setStringValue(getUninstallKey(), "DisplayIcon", icon.getAbsolutePath() + ",0");
        }
        registryOps.setStringValue(getUninstallKey(), "DisplayName", appInfo.getTitle());
        registryOps.setStringValue(getUninstallKey(), "DisplayVersion", appInfo.getVersion());
        registryOps.setLongValue(getUninstallKey(), 1);
        registryOps.setStringValue(getUninstallKey(), "Publisher", appInfo.getVendor());
        registryOps.setStringValue(
                getUninstallKey(),
                "UninstallString",
                "\"" + getUninstallerPath().getAbsolutePath() + "\" uninstall"
        );

        if (!skipWinRegistryOperations) {
            registry.notifyFileAssociationsChanged();
        }



    }


    private void deleteKeyRecursive(String key) {
        deleteKeyRecursiveStatic(key, registryOps);
    }

    /**
     * Compute a new PATH string that contains binPath. If binPath is already present,
     * the original value is returned unchanged.
     *
     * This is pure utility (no registry access) to make logic testable.
     */
    public static String computePathWithAdded(String currentPath, String binPath) {
        if (binPath == null || binPath.isEmpty()) return currentPath;
        if (currentPath == null || currentPath.isEmpty()) return binPath;
        String[] parts = currentPath.split(";");
        for (String p : parts) {
            if (p.equalsIgnoreCase(binPath)) {
                return currentPath;
            }
        }
        // Append to end to avoid interfering with other entries
        return currentPath + ";" + binPath;
    }

    /**
     * Compute a new PATH string with all occurrences of binPath removed.
     * Returns empty string if no entries remain. Returns the original string
     * if binPath is not present.
     *
     * Pure utility for testing.
     */
    public static String computePathWithRemoved(String currentPath, String binPath) {
        if (currentPath == null || currentPath.isEmpty()) return currentPath;
        if (binPath == null || binPath.isEmpty()) return currentPath;
        String[] parts = currentPath.split(";");
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (p.equalsIgnoreCase(binPath)) {
                changed = true;
                continue;
            }
            if (sb.length() > 0) sb.append(";");
            sb.append(p);
        }
        return changed ? sb.toString() : currentPath;
    }

    /**
     * Adds the provided binDir to the user's HKCU\Environment\Path if not already present.
     * Returns true if the registry value was changed, false if the directory was already present.
     * Uses file-based locking to prevent race conditions when multiple processes update PATH simultaneously.
     */
    public boolean addToUserPath(File binDir) {
        if (binDir == null) return false;

        try (PathUpdateLock lock = new PathUpdateLock()) {
            lock.acquire(30000); // 30 second timeout

            String binPath = binDir.getAbsolutePath();
            String key = "Environment";
            String curr = null;
            if (registryOps.keyExists(key) && registryOps.valueExists(key, "Path")) {
                curr = registryOps.getStringValue(key, "Path");
            } else {
                curr = System.getenv("PATH");
            }

            // Check if binPath is already at the end - if so, no change needed (idempotent)
            if (curr != null) {
                if (curr.equalsIgnoreCase(binPath) ||
                    curr.toLowerCase().endsWith(";" + binPath.toLowerCase())) {
                    return false;
                }
            }

            // First remove any existing entry for this binPath, then add at the end
            String withoutOld = computePathWithRemoved(curr, binPath);
            String newPath = computePathWithAdded(withoutOld, binPath);

            if (newPath == null) newPath = binPath;
            if (curr != null && curr.equals(newPath)) {
                return false;
            }
            if (!registryOps.keyExists(key)) {
                registryOps.createKey(key);
            }
            registryOps.setStringValue(key, "Path", newPath);
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to acquire PATH update lock", e);
        }
    }

    /**
     * Removes the provided binDir from the user's HKCU\Environment\Path.
     * Returns true if the registry value was changed (or set) and false if no change was needed.
     * Uses file-based locking to prevent race conditions when multiple processes update PATH simultaneously.
     */
    public boolean removeFromUserPath(File binDir) {
        if (binDir == null) return false;
        
        try (PathUpdateLock lock = new PathUpdateLock()) {
            lock.acquire(30000); // 30 second timeout
            
            String binPath = binDir.getAbsolutePath();
            String key = "Environment";
            String curr = null;
            boolean hadRegistryValue = false;
            if (registryOps.keyExists(key) && registryOps.valueExists(key, "Path")) {
                curr = registryOps.getStringValue(key, "Path");
                hadRegistryValue = true;
            } else {
                curr = System.getenv("PATH");
            }
            String newPath = computePathWithRemoved(curr, binPath);
            if (newPath == null) newPath = "";
            if (curr != null && curr.equals(newPath)) {
                return false;
            }
            if (!registryOps.keyExists(key)) {
                registryOps.createKey(key);
            }
            registryOps.setStringValue(key, "Path", newPath);
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to acquire PATH update lock", e);
        }
    }

    /**
     * Helper class to manage file-based locking for PATH updates.
     * Uses an exclusive lock on %USERPROFILE%\.jdeploy\path.lock to coordinate
     * read-modify-write operations across multiple processes.
     */
    static class PathUpdateLock implements AutoCloseable {
        private File lockFile;
        private RandomAccessFile lockFileHandle;
        private FileLock fileLock;

        PathUpdateLock() throws IOException {
            String userHome = System.getProperty("user.home");
            File jdeployDir = new File(userHome, ".jdeploy");
            if (!jdeployDir.exists()) {
                jdeployDir.mkdirs();
            }
            this.lockFile = new File(jdeployDir, "path.lock");
            this.lockFileHandle = new RandomAccessFile(lockFile, "rw");
        }

        /**
         * Acquires an exclusive lock on the PATH lock file.
         *
         * @param timeoutMs Maximum time to wait for the lock in milliseconds.
         * @throws IOException If the lock cannot be acquired within the timeout.
         */
        void acquire(long timeoutMs) throws IOException {
            long startTime = System.currentTimeMillis();
            while (true) {
                try {
                    // Try to acquire an exclusive lock (second parameter: shared=false)
                    fileLock = lockFileHandle.getChannel().tryLock();
                    if (fileLock != null) {
                        // Lock acquired successfully
                        return;
                    }
                } catch (IOException e) {
                    // tryLock() threw an exception; may be due to unsupported operation
                    // In that case, we'll retry or timeout
                }

                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime >= timeoutMs) {
                    throw new IOException(
                        "Failed to acquire PATH update lock within " + timeoutMs + "ms. " +
                        "Lock file: " + lockFile.getAbsolutePath()
                    );
                }

                // Back off briefly before retrying
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for PATH update lock", e);
                }
            }
        }

        /**
         * Releases the lock and closes the lock file handle.
         */
        @Override
        public void close() {
            if (fileLock != null) {
                try {
                    fileLock.release();
                } catch (IOException e) {
                    // Log or handle, but don't throw from close()
                    System.err.println("Warning: Failed to release PATH update lock: " + e.getMessage());
                }
                fileLock = null;
            }
            if (lockFileHandle != null) {
                try {
                    lockFileHandle.close();
                } catch (IOException e) {
                    // Log or handle, but don't throw from close()
                    System.err.println("Warning: Failed to close PATH update lock file: " + e.getMessage());
                }
                lockFileHandle = null;
            }
        }
    }

}
