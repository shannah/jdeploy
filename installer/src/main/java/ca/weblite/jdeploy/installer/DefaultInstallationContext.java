package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.services.WebsiteVerifier;
import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.URLUtil;
import ca.weblite.tools.io.XMLUtil;
import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultInstallationContext implements InstallationContext {
    public static String JDEPLOY_REGISTRY = "https://www.jdeploy.com/";
    static {
        if (System.getenv("JDEPLOY_REGISTRY_URL") != null) {
            JDEPLOY_REGISTRY = System.getenv("JDEPLOY_REGISTRY_URL");
            if (!JDEPLOY_REGISTRY.startsWith("http://") && !JDEPLOY_REGISTRY.startsWith("https://")) {
                throw new RuntimeException("INVALID_JDEPLOY_REGISTRY_URL environment variable.  Expecting URL but found "+JDEPLOY_REGISTRY);
            }
            if (!JDEPLOY_REGISTRY.endsWith("/")) {
                JDEPLOY_REGISTRY += "/";
            }
        }
    }

    private Document appXMLDocument;
    private File cachedInstallFilesDir;
    public File findInstallFilesDir() {
        if (cachedInstallFilesDir != null && cachedInstallFilesDir.exists()) return cachedInstallFilesDir;
        System.out.println("findInstallFilesDir():");
        if (System.getProperty("client4j.launcher.path") != null) {
            String launcherPath = System.getProperty("client4j.launcher.path");
            String launcherFileName = launcherPath;
            boolean isMac = Platform.getSystemPlatform().isMac();
            File appBundle = isMac ? findAppBundle() : null;
            File tmpBundleFile = new File(launcherPath);
            if (isMac && appBundle != null && appBundle.exists()) {
                launcherFileName = appBundle.getName();
                tmpBundleFile = appBundle;

            }

            String code = extractJDeployBundleCodeFromFileName(launcherFileName);
            String version = extractVersionFromFileName(launcherFileName);
            if (code != null && version != null) {
                try {
                    cachedInstallFilesDir = downloadJDeployBundleForCode(code, version, tmpBundleFile);
                    return cachedInstallFilesDir;
                } catch (IOException ex) {
                    System.err.println("Failed to download install files bundle: "+ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            }

            System.out.println("Found client4.launcher.path property: "+launcherPath);
            cachedInstallFilesDir = findInstallFilesDir(new File(launcherPath));
            return cachedInstallFilesDir;
        } else {
            System.out.println("client4j.launcher.path is not set");
        }
        System.out.println("User dir: "+new File(System.getProperty("user.dir")).getAbsolutePath());
        cachedInstallFilesDir = findInstallFilesDir(new File(System.getProperty("user.dir")));
        return cachedInstallFilesDir;
    }

    private static String extractJDeployBundleCodeFromFileName(String fileName) {
        int pos = fileName.lastIndexOf("_");
        if (pos < 0) return null;
        StringBuilder out = new StringBuilder();
        char[] chars = fileName.substring(pos+1).toCharArray();
        for (int i=0; i<chars.length; i++) {
            char c = chars[i];
            if (('0' <= c && '9' >= c) || ('A' <= c && 'Z' >= c)) {
                out.append(c);
            } else {
                break;
            }
        }
        if (out.length() == 0) return null;
        return out.toString();

    }

    private static URL getJDeployBundleURLForCode(String code, String version, File appBundle) {
        try {
            String prerelease = isPrerelease(appBundle) ? "&prerelease=true" : "";
            return new URL(JDEPLOY_REGISTRY + "download.php?code=" +
                    URLEncoder.encode(code, "UTF-8") +
                    "&version="+URLEncoder.encode(version, "UTF-8") +
                    "&jdeploy_files=true&platform=*" +
                    prerelease
            );
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException("UTF-8 Encoding doesn't seem to be supported on this platform.", ex);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Programming error.  Malformed URL for bundle. ", e);
        }
    }

    private static File findDirectoryByNameRecursive(File startDirectory, String name) {
        if (startDirectory.isDirectory()) {
            if (startDirectory.getName().equals(name)) return startDirectory;
            for (File child : startDirectory.listFiles()) {
                File result = findDirectoryByNameRecursive(child, name);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static File downloadJDeployBundleForCode(String code, String version, File appBundle) throws IOException {
        File destDirectory = File.createTempFile("jdeploy-files-download", ".tmp");
        destDirectory.delete();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try {
                FileUtils.deleteDirectory(destDirectory);
            } catch (Exception ex){}
        }));
        File destFile = new File(destDirectory, "jdeploy-files.zip");
        try (InputStream inputStream = URLUtil.openStream(getJDeployBundleURLForCode(code, version, appBundle))) {
            FileUtils.copyInputStreamToFile(inputStream, destFile);
        }

        ArchiveUtil.extract(destFile, destDirectory, "");
        return findDirectoryByNameRecursive(destDirectory, ".jdeploy-files");
    }

    private File findAppBundle() {
        File start = new File(System.getProperty("client4j.launcher.path"));
        while (start != null && !start.getName().endsWith(".app")) {
            start = start.getParentFile();
        }
        return start;
    }

    private static String extractVersionFromFileName(String fileName) {
        int pos = fileName.lastIndexOf("_");
        if (pos < 0) return null;

        fileName = fileName.substring(0, pos);
        Pattern p = Pattern.compile("^.*?-(\\d[a-zA-Z0-9\\.\\-_]*)$");
        Matcher m = p.matcher(fileName);
        if (m.matches()) {
            return m.group(1);
        }
        return null;

    }

    private static boolean isPrerelease(File appBundle)  {
        if ("true".equals(System.getProperty("jdeploy.prerelease", "false"))) {
            // This property is passed in by the launcher if the app.xml contained the prerelease
            // attribute set to true.  This is useful so that the installer knows whether it is a
            // prerelease - in which case it will be obtaining bundles for prerelease builds.
            return true;
        }
        if (!findBundleAppXml(appBundle).exists()) {
            return false;
        }
        try (InputStream inputStream = new FileInputStream(findBundleAppXml(appBundle))) {
            Document doc = XMLUtil.parse(inputStream);
            return "true".equals(doc.getDocumentElement().getAttribute("prerelease"));
        } catch (Exception ex) {
            return false;
        }

    }

    private static File findBundleAppXml(File appBundle) {
        return new File(appBundle, "Contents" + File.separator + "app.xml");
    }

    private File findInstallFilesDir(File startDir) {
        if (startDir == null) return null;
        if (Platform.getSystemPlatform().isMac() && "AppTranslocation".equals(startDir.getName())) {
            System.out.println("Detected that we are running inside Gatekeeper so we can't retrieve bundle info");
            System.out.println("Attempting to download bundle info from network");
            // Gatekeeper is running the app from a random location, so we won't be able to find the
            // app.xml file the normal way.
            // We need to be creative.
            // Using the name of the installer,
            // we can extract the package name and version
            File appBundle = findAppBundle();
            if (appBundle == null) {
                System.err.println("Failed to find app bundle");
                return null;
            }
            String code = extractJDeployBundleCodeFromFileName(appBundle.getName());
            if (code == null) {
                System.err.println("Cannot download bundle info from the network because no code was found in the app name: "+appBundle.getName());
                return null;
            }
            String version = extractVersionFromFileName(appBundle.getName());
            if (version == null) {
                System.err.println("Cannot download bundle info from network because the version string was not found in the app name: "+appBundle.getName());
                return null;
            }
            try {
                return downloadJDeployBundleForCode(code, version, appBundle);
            } catch (IOException ex) {
                System.err.println("Failed to download bundle from the network for code "+code+".");
                ex.printStackTrace(System.err);
                return null;
            }


        }
        File candidate = new File(startDir, ".jdeploy-files");
        if (candidate.exists() && candidate.isDirectory()) return candidate;
        return findInstallFilesDir(startDir.getParentFile());
    }

    public void applyContext(InstallationSettings settings) {
        settings.setInstallFilesDir(findInstallFilesDir());
        NPMApplication app = settings.getNpmPackageVersion().toNPMApplication();
        if (app.getHomepage() != null) {
            WebsiteVerifier verifier = new WebsiteVerifier();

            try {
                if (verifier.verifyHomepage(app)) {
                    settings.setWebsiteURL(new URL(app.getHomepage()));
                }
            } catch (Exception ex){
                System.err.println("Failed to verify homepage "+app.getHomepage());
                ex.printStackTrace(System.err);
            }
        }
        if (settings.getWebsiteURL() == null) {
            try {
                settings.setWebsiteURL(new URL("https://www.npmjs.com/package/" + app.getPackageName()));
            } catch (Exception ex){}
        }



    }



    public File findAppXml() {
        File installFilesDir = findInstallFilesDir();
        if (installFilesDir == null) {
            return null;
        }
        File appXml =  new File(installFilesDir, "app.xml");
        if (!appXml.exists()) {
            return null;
        }
        return appXml;

    }

    public Document getAppXMLDocument() throws IOException {
        if (appXMLDocument == null) {
            File appXml = findAppXml();
            if (appXml == null) {
                return null;
            }

            try (FileInputStream fis = new FileInputStream(appXml)) {
                appXMLDocument = parseXml(fis);
            } catch (Exception ex) {
                throw new IOException("Failed to parse app.xml: "+ex.getMessage(), ex);
            }
        }
        return appXMLDocument;
    }
    private static Document parseXml(InputStream input) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            return builder.parse(input);
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }


}
