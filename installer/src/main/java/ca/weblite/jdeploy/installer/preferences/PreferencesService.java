package ca.weblite.jdeploy.installer.preferences;

import ca.weblite.tools.io.MD5;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Service for managing application preferences stored externally.
 *
 * This service handles:
 * 1. Calculating the fully-qualified package name (FQN) for preferences path
 * 2. Reading/writing preferences.xml files
 * 3. Managing the preferences directory structure
 *
 * Preferences are stored at: ~/.jdeploy/preferences/{fqn}/preferences.xml
 */
public class PreferencesService {

    private static final String JDEPLOY_HOME = ".jdeploy";
    private static final String PREFERENCES_DIR = "preferences";
    private static final String PREFERENCES_FILE = "preferences.xml";

    private final PrintStream out;
    private final PrintStream err;

    public PreferencesService() {
        this(System.out, System.err);
    }

    public PreferencesService(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Calculates the fully-qualified package name (FQN) for a package.
     *
     * If a source URL is provided, the FQN is: {md5-hash}.{packageName}
     * Otherwise, the FQN is just: {packageName}
     *
     * @param packageName the npm package name
     * @param source the source URL (e.g., GitHub URL), or null
     * @return the fully-qualified package name
     */
    public String calculateFqn(String packageName, String source) {
        if (source != null && !source.isEmpty()) {
            String sourceHash = MD5.getMd5(source);
            return sourceHash + "." + packageName;
        }
        return packageName;
    }

    /**
     * Gets the preferences directory for a package.
     *
     * @param fqn the fully-qualified package name
     * @return the preferences directory path
     */
    public File getPreferencesDir(String fqn) {
        return new File(
                System.getProperty("user.home"),
                JDEPLOY_HOME + File.separator + PREFERENCES_DIR + File.separator + fqn
        );
    }

    /**
     * Gets the preferences file for a package.
     *
     * @param fqn the fully-qualified package name
     * @return the preferences file path
     */
    public File getPreferencesFile(String fqn) {
        return new File(getPreferencesDir(fqn), PREFERENCES_FILE);
    }

    /**
     * Checks if preferences exist for a package.
     *
     * @param fqn the fully-qualified package name
     * @return true if preferences.xml exists
     */
    public boolean preferencesExist(String fqn) {
        return getPreferencesFile(fqn).exists();
    }

    /**
     * Reads preferences from the preferences.xml file.
     *
     * @param fqn the fully-qualified package name
     * @return the preferences, or null if not found or corrupted
     */
    public AppPreferences readPreferences(String fqn) {
        File prefsFile = getPreferencesFile(fqn);
        if (!prefsFile.exists()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(prefsFile);
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (!"preferences".equals(root.getTagName())) {
                err.println("Warning: Invalid preferences file format");
                return null;
            }

            AppPreferences prefs = new AppPreferences();
            prefs.setSchemaVersion(getElementText(root, "schemaVersion", "1.0"));
            prefs.setVersion(getElementText(root, "version", null));
            prefs.setPrereleaseChannel(getElementText(root, "prereleaseChannel", "stable"));
            prefs.setAutoUpdate(getElementText(root, "autoUpdate", "minor"));
            prefs.setJvmArgs(getElementText(root, "jvmArgs", null));
            prefs.setPrebuiltInstallation(
                    "true".equalsIgnoreCase(getElementText(root, "prebuiltInstallation", "false"))
            );

            return prefs;

        } catch (ParserConfigurationException | SAXException | IOException e) {
            err.println("Warning: Failed to read preferences file: " + e.getMessage());
            // Handle corrupted file by renaming it
            handleCorruptedPreferences(prefsFile);
            return null;
        }
    }

    /**
     * Writes preferences to the preferences.xml file.
     *
     * @param fqn the fully-qualified package name
     * @param prefs the preferences to write
     * @throws IOException if writing fails
     */
    public void writePreferences(String fqn, AppPreferences prefs) throws IOException {
        File prefsDir = getPreferencesDir(fqn);
        if (!prefsDir.exists()) {
            if (!prefsDir.mkdirs()) {
                throw new IOException("Failed to create preferences directory: " + prefsDir);
            }
        }

        File prefsFile = getPreferencesFile(fqn);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Create root element
            Element root = doc.createElement("preferences");
            doc.appendChild(root);

            // Add child elements
            appendElement(doc, root, "schemaVersion", prefs.getSchemaVersion());

            if (prefs.getVersion() != null) {
                appendElement(doc, root, "version", prefs.getVersion());
            }

            appendElement(doc, root, "prereleaseChannel", prefs.getPrereleaseChannel());
            appendElement(doc, root, "autoUpdate", prefs.getAutoUpdate());

            if (prefs.getJvmArgs() != null && !prefs.getJvmArgs().isEmpty()) {
                appendElement(doc, root, "jvmArgs", prefs.getJvmArgs());
            }

            appendElement(doc, root, "prebuiltInstallation",
                    String.valueOf(prefs.isPrebuiltInstallation()));

            // Write to file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(prefsFile);
            transformer.transform(source, result);

            out.println("Wrote preferences to: " + prefsFile.getAbsolutePath());

        } catch (ParserConfigurationException | TransformerException e) {
            throw new IOException("Failed to write preferences file", e);
        }
    }

    /**
     * Deletes preferences for a package.
     *
     * @param fqn the fully-qualified package name
     * @return true if preferences were deleted
     */
    public boolean deletePreferences(String fqn) {
        File prefsFile = getPreferencesFile(fqn);
        if (prefsFile.exists()) {
            return prefsFile.delete();
        }
        return true;
    }

    /**
     * Creates initial preferences during installation.
     *
     * @param fqn the fully-qualified package name
     * @param version the app version being installed
     * @param isPrerelease whether prerelease channel is enabled
     * @param autoUpdate the auto-update setting (all, minor, patch, none)
     * @param isPrebuilt whether this is a prebuilt app installation
     * @throws IOException if writing fails
     */
    public void createInitialPreferences(
            String fqn,
            String version,
            boolean isPrerelease,
            String autoUpdate,
            boolean isPrebuilt) throws IOException {

        AppPreferences prefs = AppPreferences.builder()
                .version(version)
                .prereleaseChannel(isPrerelease ? "prerelease" : "stable")
                .autoUpdate(autoUpdate != null ? autoUpdate : "minor")
                .prebuiltInstallation(isPrebuilt)
                .build();

        writePreferences(fqn, prefs);
    }

    // ==================== Private Helper Methods ====================

    private String getElementText(Element parent, String tagName, String defaultValue) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return text != null && !text.isEmpty() ? text : defaultValue;
        }
        return defaultValue;
    }

    private void appendElement(Document doc, Element parent, String tagName, String value) {
        Element element = doc.createElement(tagName);
        element.setTextContent(value);
        parent.appendChild(element);
    }

    private void handleCorruptedPreferences(File prefsFile) {
        try {
            File backup = new File(prefsFile.getParentFile(),
                    prefsFile.getName() + ".corrupted." + System.currentTimeMillis());
            if (prefsFile.renameTo(backup)) {
                err.println("Renamed corrupted preferences file to: " + backup.getName());
            }
        } catch (Exception e) {
            err.println("Failed to rename corrupted preferences file: " + e.getMessage());
        }
    }
}
