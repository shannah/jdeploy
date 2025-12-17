package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.tools.io.MD5;
import org.apache.commons.io.FileUtils;

import static com.sun.jna.platform.win32.Advapi32Util.*;
import static com.sun.jna.platform.win32.WinReg.*;

import java.io.*;
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

    public static enum AppType {
        Mail,
        Media,
        Contact,
        StartMenuInternet,
        Other;



        public String getFullRegistryPath() {
            switch (this) {
                case Other:
                    return REGISTRY_KEY_SOFTWARE;
                default:
                    return REGISTRY_KEY_CLIENTS + "\\" +this.name();
            }
        }

    }

    public InstallWindowsRegistry(
            AppInfo appInfo,
            File exe,
            File icon,
            OutputStream backupLog
    ) {
        this.appInfo = appInfo;
        this.exe = exe;
        this.icon = icon;
        if (backupLog != null) {
            this.backupLog = backupLog;
            this.backupLogOut = new PrintStream(backupLog);
        }
    }

    public File getUninstallerPath() {
        String suffix = "";
        if (appInfo.getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo.getNpmVersion();
            suffix = "-" + v.substring(v.indexOf("-")+1);
        }
        return new File(System.getProperty("user.home") + File.separator +
                ".jdeploy" + File.separator +
                "uninstallers" + File.separator +
                getFullyQualifiedPackageName() + File.separator +
                appInfo.getNpmPackage()+suffix+"-uninstall.exe");

    }

    public String getFullyQualifiedPackageName() {
        String sourceHash = getSourceHash();
        if (sourceHash == null) {
            return appInfo.getNpmPackage();
        }
        String suffix = "";
        if (appInfo.getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo.getNpmVersion();
            suffix = "." + v.substring(v.indexOf("-")+1);
        }
        return sourceHash + "." + appInfo.getNpmPackage() + suffix;
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
        String sourceHash = getSourceHash();
        if (sourceHash == null) {
            return "jdeploy." + appInfo.getNpmPackage();
        }

        String suffix = "";
        if (appInfo.getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo.getNpmVersion();
            suffix = "." + v.substring(v.indexOf("-")+1);
        }
        return "jdeploy." + sourceHash + "." + appInfo.getNpmPackage() + suffix;
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
        String sourceHash = getSourceHash();
        if (sourceHash == null) {
            return "jdeploy." + appInfo.getNpmPackage() + ".file";
        }

        String suffix = "";
        if (appInfo.getNpmVersion().startsWith("0.0.0-")) {
            String v = appInfo.getNpmVersion();
            suffix = "." + v.substring(v.indexOf("-")+1);
        }

        return "jdeploy." + sourceHash + "." + appInfo.getNpmPackage() + suffix + ".file";
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
    private void registerFileExtension(String ext) {
        // NOTE: Not backing up to the backup log, because this can be reverted cleanly by
        // simply removing ourself from the OpenWithProgIds key.
        WinRegistry registry = new WinRegistry();
        String mimetype = appInfo.getMimetype(ext);
        if (!ext.startsWith(".")) {
            ext = "." + ext;

        }
        String key = "Software\\Classes\\"+ext;
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            backupLogOut.println(";CREATE "+key);
            backupLogOut.flush();
            registry.createRegistryKeyRecursive(key);
            registrySetStringValue(HKEY_CURRENT_USER, key, null, getProgId());
            if (mimetype != null) {
                registrySetStringValue(HKEY_CURRENT_USER, key, "ContentType", mimetype);
                if (mimetype.contains("/")) {
                    registrySetStringValue(
                            HKEY_CURRENT_USER,
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
        if (!registryKeyExists(HKEY_CURRENT_USER, progIdsKey)) {
            registryCreateKey(HKEY_CURRENT_USER, progIdsKey);
        }
        registrySetStringValue(HKEY_CURRENT_USER, progIdsKey, getProgId(), "");
    }

    /**
     * Registers all app file extensions.
     */
    private void registerFileExtensions() {
        for (String ext : appInfo.getExtensions()) {
            registerFileExtension(ext);
        }
        if (registryKeyExists(HKEY_CURRENT_USER, getFileAssociationsPath())) {
            deleteKeyRecursive(getFileAssociationsPath());
        }
        new WinRegistry().createRegistryKeyRecursive(getFileAssociationsPath());
        for (String ext : appInfo.getExtensions()) {
            if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            registrySetStringValue(HKEY_CURRENT_USER, getFileAssociationsPath(), ext, getProgId());

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
        if (!registryKeyExists(HKEY_CLASSES_ROOT, scheme)) {
            // If the key doesn't exist yet at all
            // then we can change it.
            return true;
        }

        if (!registryKeyExists(HKEY_CLASSES_ROOT, scheme + "\\shell\\open\\command")) {
            // If there is no shell setting yet
            // then we can't screw stuff up - so we'll proceed
            return true;
        }

        String currentExe = registryGetStringValue(HKEY_CLASSES_ROOT, scheme + "\\shell\\open\\command", null);
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
        Scanner scanner = new Scanner(backupLog, "UTF-8");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // If we added a key, we wrote them to the log file as comments of the form
            // ;CREATE key where key is like Software\Classes\...
            if (line.startsWith(";CREATE ")) {
                deleteKeyRecursive(line.substring(line.indexOf(" ")+1));
            }
        }
        new WinRegistry().regImport(backupLog);

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

        if (registryKeyExists(HKEY_CURRENT_USER, schemeKey)) {
            // NOTE: We back up the old key to the backup log so that we can revert on uninstall.
            new WinRegistry().exportKey(schemeKey, backupLog);
        }

        if (!registryKeyExists(HKEY_CURRENT_USER, schemeKey)) {
            backupLogOut.println(";CREATE "+schemeKey);
            backupLogOut.flush();
            registryCreateKey(HKEY_CURRENT_USER, schemeKey);
        }
        registrySetStringValue(HKEY_CURRENT_USER, schemeKey, null, "URL:"+scheme);
        registrySetStringValue(HKEY_CURRENT_USER, schemeKey, "URL Protocol", "");
        String shellKey = schemeKey + "\\shell";
        if (!registryKeyExists(HKEY_CURRENT_USER, shellKey)) {
            registryCreateKey(HKEY_CURRENT_USER, shellKey);
        }
        String openKey = shellKey + "\\open";
        if (!registryKeyExists(HKEY_CURRENT_USER, openKey)) {
            registryCreateKey(HKEY_CURRENT_USER, openKey);
        }

        if (icon != null && icon.exists() && !registryValueExists(HKEY_CURRENT_USER, openKey, "Icon")) {
            registrySetStringValue(HKEY_CURRENT_USER, openKey, "Icon", icon.getAbsolutePath()+",0");
        }

        String commandKey = openKey + "\\command";
        if (!registryKeyExists(HKEY_CURRENT_USER, commandKey)) {
            registryCreateKey(HKEY_CURRENT_USER, commandKey);
        }

        registrySetStringValue(HKEY_CURRENT_USER, commandKey, null, "\""+exe.getAbsolutePath()+"\" \"%1\"");

    }

    /**
     * Creates the single "File type" entry to be associated with the app.
     */
    private void registerFileTypeEntry() {
        String basekey = getProgBaseKey();

        if (!registryKeyExists(HKEY_CURRENT_USER, basekey)) {
            registryCreateKey(HKEY_CURRENT_USER, basekey);
        }
        registrySetStringValue(HKEY_CURRENT_USER, basekey, null, getHumanReadableFileType());
        if (icon != null && icon.exists()) {
            String iconKey = basekey + "\\DefaultIcon";
            if (!registryKeyExists(HKEY_CURRENT_USER, iconKey)) {
                registryCreateKey(HKEY_CURRENT_USER, iconKey);
            }
            registrySetStringValue(HKEY_CURRENT_USER, iconKey, null, icon.getAbsolutePath()+",0");
        }

        String shellKey = basekey + "\\shell";
        if (!registryKeyExists(HKEY_CURRENT_USER, shellKey)) {
            registryCreateKey(HKEY_CURRENT_USER, shellKey);
        }
        String openKey = shellKey + "\\open";
        if (!registryKeyExists(HKEY_CURRENT_USER, openKey)) {
            registryCreateKey(HKEY_CURRENT_USER, openKey);
        }
        if (icon != null && icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, openKey, "Icon", icon.getAbsolutePath()+",0");
        }
        String commandKey = openKey + "\\command";
        if (!registryKeyExists(HKEY_CURRENT_USER, commandKey)) {
            registryCreateKey(HKEY_CURRENT_USER, commandKey);
        }

        registrySetStringValue(HKEY_CURRENT_USER, commandKey, null, "\""+exe.getAbsolutePath()+"\" \"%1\"");
    }



    private void registerUrlSchemes() throws IOException {
        if (registryKeyExists(HKEY_CURRENT_USER, getURLAssociationsPath())) {
            deleteKeyRecursive(getURLAssociationsPath());
        }
        new WinRegistry().createRegistryKeyRecursive(getURLAssociationsPath());
        for (String scheme : appInfo.getUrlSchemes()) {
            registrySetStringValue(HKEY_CURRENT_USER, getURLAssociationsPath(), scheme, getProgId());
            registerCustomScheme(scheme);
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

        if (registryKeyExists(HKEY_CURRENT_USER, directoryShellKey)) {
            // Backup existing key
            new WinRegistry().exportKey(directoryShellKey, backupLog);
        } else {
            backupLogOut.println(";CREATE " + directoryShellKey);
            backupLogOut.flush();
        }

        if (!registryKeyExists(HKEY_CURRENT_USER, directoryShellKey)) {
            registryCreateKey(HKEY_CURRENT_USER, directoryShellKey);
        }

        // Set the display name for the context menu
        registrySetStringValue(HKEY_CURRENT_USER, directoryShellKey, null, menuText);

        // Set icon if available
        if (icon != null && icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, directoryShellKey, "Icon",
                icon.getAbsolutePath() + ",0");
        }

        // Create command key
        String commandKey = directoryShellKey + "\\command";
        if (!registryKeyExists(HKEY_CURRENT_USER, commandKey)) {
            registryCreateKey(HKEY_CURRENT_USER, commandKey);
        }

        // Set command to launch app with directory path
        registrySetStringValue(HKEY_CURRENT_USER, commandKey, null,
            "\"" + exe.getAbsolutePath() + "\" \"%1\"");

        // Also register for Directory\Background\shell for "Open folder with..." in empty space
        String backgroundShellKey = "Software\\Classes\\Directory\\Background\\shell\\" + progId;

        if (registryKeyExists(HKEY_CURRENT_USER, backgroundShellKey)) {
            new WinRegistry().exportKey(backgroundShellKey, backupLog);
        } else {
            backupLogOut.println(";CREATE " + backgroundShellKey);
            backupLogOut.flush();
        }

        if (!registryKeyExists(HKEY_CURRENT_USER, backgroundShellKey)) {
            registryCreateKey(HKEY_CURRENT_USER, backgroundShellKey);
        }

        registrySetStringValue(HKEY_CURRENT_USER, backgroundShellKey, null, menuText);

        if (icon != null && icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, backgroundShellKey, "Icon",
                icon.getAbsolutePath() + ",0");
        }

        String backgroundCommandKey = backgroundShellKey + "\\command";
        if (!registryKeyExists(HKEY_CURRENT_USER, backgroundCommandKey)) {
            registryCreateKey(HKEY_CURRENT_USER, backgroundCommandKey);
        }

        // Use %V for background shell - represents the current directory
        registrySetStringValue(HKEY_CURRENT_USER, backgroundCommandKey, null,
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
        if (registryKeyExists(HKEY_CURRENT_USER, directoryShellKey)) {
            System.out.println("Deleting directory association key: " + directoryShellKey);
            deleteKeyRecursive(directoryShellKey);
        }

        String backgroundShellKey = "Software\\Classes\\Directory\\Background\\shell\\" + progId;
        if (registryKeyExists(HKEY_CURRENT_USER, backgroundShellKey)) {
            System.out.println("Deleting directory background association key: " + backgroundShellKey);
            deleteKeyRecursive(backgroundShellKey);
        }
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

        if (backupLogFile != null && backupLogFile.exists()) {
            new WinRegistry().regImport(backupLogFile);
        }

    }

    public void deleteRegistryKey() {
        if (registryKeyExists(HKEY_CURRENT_USER, getRegistryPath())) {
            System.out.println("Deleting registry key "+getRegistryPath());
            deleteKeyRecursive(getRegistryPath());
        }
    }


    private void unregisterUrlSchemes() {

    }

    private void unregisterFileExtensions() {
        for (String ext : appInfo.getExtensions()) {
            unregisterFileExtension(ext);
        }
        deleteFileTypeEntry();

    }

    private void deleteUninstallEntry() {
        if (registryKeyExists(HKEY_CURRENT_USER, getUninstallKey())) {
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

        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            return;
        }
        System.out.println("Deleting registry value for key "+key+" vaue="+getProgId());

        deleteKeyRecursive(getProgId());
    }

    private void deleteFileTypeEntry() {
        String basekey = getProgBaseKey();
        System.out.println("Deleting file type entry "+basekey);
        if (!registryKeyExists(HKEY_CURRENT_USER, basekey)) {
            return;
        }
        System.out.println("Deleting registry key: "+basekey);
        deleteKeyRecursive(basekey);

    }

    public void register() throws IOException {
        WinRegistry registry = new WinRegistry();
        String capabilitiesPath = getCapabilitiesPath();
        if (registryKeyExists(HKEY_CURRENT_USER, getRegistryPath())) {

            registry.exportKey(getRegistryPath(), backupLog);
        }
        registry.createRegistryKeyRecursive(capabilitiesPath);
        registrySetStringValue(HKEY_CURRENT_USER, capabilitiesPath, "ApplicationName", appInfo.getTitle());
        registrySetStringValue(HKEY_CURRENT_USER, capabilitiesPath, "ApplicationDescription", appInfo.getDescription());
        if (icon != null && icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, capabilitiesPath, "ApplicationIcon", icon.getAbsolutePath()+",0");
        }

        if (appInfo.hasDocumentTypes() || appInfo.hasUrlSchemes()) {
            // Register file extensions individually in Classes
            registerFileExtensions();

            // Register application file type entry which is referenced.
            registerFileTypeEntry();

        }

        if (appInfo.hasUrlSchemes()) {
            registerUrlSchemes();
        }

        if (appInfo.hasDirectoryAssociation()) {
            registerDirectoryAssociations();
        }

        // Register the application
        registrySetStringValue(
                HKEY_CURRENT_USER,
                "Software\\RegisteredApplications",
                getRegisteredAppName(),
                getCapabilitiesPath()
        );

        // Now to register the uninstaller

        registry.createRegistryKeyRecursive(getUninstallKey());
        if (icon != null && icon.exists()) {
            registrySetStringValue(HKEY_CURRENT_USER, getUninstallKey(), "DisplayIcon", icon.getAbsolutePath() + ",0");
        }
        registrySetStringValue(HKEY_CURRENT_USER, getUninstallKey(), "DisplayName", appInfo.getTitle());
        registrySetStringValue(HKEY_CURRENT_USER, getUninstallKey(), "DisplayVersion", appInfo.getVersion());
        registrySetLongValue(HKEY_CURRENT_USER, getUninstallKey(), 1);
        registrySetStringValue(HKEY_CURRENT_USER, getUninstallKey(), "Publisher", appInfo.getVendor());
        registrySetStringValue(
                HKEY_CURRENT_USER,
                getUninstallKey(),
                "UninstallString",
                "\"" + getUninstallerPath().getAbsolutePath() + "\" uninstall"
        );

        String installerPath = System.getProperty("client4j.launcher.path");
        if (installerPath == null) {
            throw new RuntimeException("No client4j.launcher.path property found");
        }
        File installerExe = new File(installerPath);
        if (!installerExe.exists()) {
            throw new RuntimeException("Cannot find installer path.");
        }


        registry.notifyFileAssociationsChanged();



    }


    private static void deleteKeyRecursive(String key) {
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            return;
        }
        for (String subkey : registryGetKeys(HKEY_CURRENT_USER, key)) {
            deleteKeyRecursive(key + "\\" + subkey);
        }
        for (String valueKey : registryGetValues(HKEY_CURRENT_USER, key).keySet()) {
            registryDeleteValue(HKEY_CURRENT_USER, key, valueKey);
        }
        registryDeleteKey(HKEY_CURRENT_USER, key);
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
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (p.equalsIgnoreCase(binPath)) continue;
            if (sb.length() > 0) sb.append(";");
            sb.append(p);
        }
        return sb.toString();
    }

    /**
     * Adds the provided binDir to the user's HKCU\Environment\Path if not already present.
     * Returns true if the registry value was changed, false if the directory was already present.
     */
    public boolean addToUserPath(File binDir) {
        if (binDir == null) return false;
        String binPath = binDir.getAbsolutePath();
        String key = "Environment";
        String curr = null;
        boolean hadRegistryValue = false;
        if (registryKeyExists(HKEY_CURRENT_USER, key) && registryValueExists(HKEY_CURRENT_USER, key, "Path")) {
            curr = registryGetStringValue(HKEY_CURRENT_USER, key, "Path");
            hadRegistryValue = true;
        } else {
            curr = System.getenv("PATH");
        }
        String newPath = computePathWithAdded(curr, binPath);
        if (newPath == null) newPath = binPath;
        if (curr != null && curr.equals(newPath)) {
            return false;
        }
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }
        registrySetStringValue(HKEY_CURRENT_USER, key, "Path", newPath);
        return true;
    }

    /**
     * Removes the provided binDir from the user's HKCU\Environment\Path.
     * Returns true if the registry value was changed (or set) and false if no change was needed.
     */
    public boolean removeFromUserPath(File binDir) {
        if (binDir == null) return false;
        String binPath = binDir.getAbsolutePath();
        String key = "Environment";
        String curr = null;
        boolean hadRegistryValue = false;
        if (registryKeyExists(HKEY_CURRENT_USER, key) && registryValueExists(HKEY_CURRENT_USER, key, "Path")) {
            curr = registryGetStringValue(HKEY_CURRENT_USER, key, "Path");
            hadRegistryValue = true;
        } else {
            curr = System.getenv("PATH");
        }
        String newPath = computePathWithRemoved(curr, binPath);
        if (newPath == null) newPath = "";
        if (curr != null && curr.equals(newPath)) {
            return false;
        }
        if (!registryKeyExists(HKEY_CURRENT_USER, key)) {
            registryCreateKey(HKEY_CURRENT_USER, key);
        }
        registrySetStringValue(HKEY_CURRENT_USER, key, "Path", newPath);
        return true;
    }

}
