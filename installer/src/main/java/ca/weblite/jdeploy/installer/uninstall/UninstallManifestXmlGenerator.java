package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.time.Instant;
import java.util.List;

/**
 * Converts an UninstallManifest POJO to an XML Document using standard JAXP DOM API.
 * The generated XML conforms to the uninstall-manifest schema with namespace
 * http://jdeploy.ca/uninstall-manifest/1.0 and version 1.0.
 */
public class UninstallManifestXmlGenerator {
    private static final String NAMESPACE = "http://jdeploy.ca/uninstall-manifest/1.0";
    private static final String MANIFEST_VERSION = "1.0";

    private Document document;

    /**
     * Generates an XML Document from an UninstallManifest.
     *
     * @param manifest the UninstallManifest to convert
     * @return an XML Document representation of the manifest
     * @throws ParserConfigurationException if XML document creation fails
     */
    public Document generate(UninstallManifest manifest) throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        this.document = builder.newDocument();

        Element root = document.createElementNS(NAMESPACE, "uninstall-manifest");
        root.setAttribute("version", MANIFEST_VERSION);
        root.setAttribute("xmlns", NAMESPACE);
        document.appendChild(root);

        root.appendChild(createPackageInfoElement(manifest.getPackageInfo()));
        root.appendChild(createFilesElement(manifest.getFiles()));
        root.appendChild(createDirectoriesElement(manifest.getDirectories()));

        if (manifest.getRegistry() != null) {
            root.appendChild(createRegistryElement(manifest.getRegistry()));
        }

        if (manifest.getPathModifications() != null) {
            root.appendChild(createPathModificationsElement(manifest.getPathModifications()));
        }

        return document;
    }

    /**
     * Creates the package-info XML element.
     */
    private Element createPackageInfoElement(UninstallManifest.PackageInfo packageInfo) {
        Element element = document.createElementNS(NAMESPACE, "package-info");

        appendTextElement(element, "name", packageInfo.getName());
        if (packageInfo.getSource() != null) {
            appendTextElement(element, "source", packageInfo.getSource());
        }
        appendTextElement(element, "version", packageInfo.getVersion());
        appendTextElement(element, "fully-qualified-name", packageInfo.getFullyQualifiedName());
        appendTextElement(element, "architecture", packageInfo.getArchitecture());
        appendTextElement(element, "installed-at", packageInfo.getInstalledAt().toString());
        appendTextElement(element, "installer-version", packageInfo.getInstallerVersion());

        return element;
    }

    /**
     * Creates the files XML element containing all installed files.
     */
    private Element createFilesElement(List<UninstallManifest.InstalledFile> files) {
        Element element = document.createElementNS(NAMESPACE, "files");

        for (UninstallManifest.InstalledFile file : files) {
            Element fileElement = document.createElementNS(NAMESPACE, "file");
            fileElement.setAttribute("type", file.getType().getValue());
            appendTextElement(fileElement, "path", file.getPath());
            if (file.getDescription() != null) {
                appendTextElement(fileElement, "description", file.getDescription());
            }
            element.appendChild(fileElement);
        }

        return element;
    }

    /**
     * Creates the directories XML element containing all installed directories.
     */
    private Element createDirectoriesElement(List<UninstallManifest.InstalledDirectory> directories) {
        Element element = document.createElementNS(NAMESPACE, "directories");

        for (UninstallManifest.InstalledDirectory directory : directories) {
            Element dirElement = document.createElementNS(NAMESPACE, "directory");
            dirElement.setAttribute("cleanup", directory.getCleanup().getValue());
            appendTextElement(dirElement, "path", directory.getPath());
            if (directory.getDescription() != null) {
                appendTextElement(dirElement, "description", directory.getDescription());
            }
            element.appendChild(dirElement);
        }

        return element;
    }

    /**
     * Creates the registry XML element containing Windows registry operations.
     */
    private Element createRegistryElement(UninstallManifest.RegistryInfo registry) {
        Element element = document.createElementNS(NAMESPACE, "registry");

        // Created keys
        Element createdKeysElement = document.createElementNS(NAMESPACE, "created-keys");
        for (UninstallManifest.RegistryKey key : registry.getCreatedKeys()) {
            Element keyElement = document.createElementNS(NAMESPACE, "key");
            keyElement.setAttribute("root", key.getRoot().getValue());
            appendTextElement(keyElement, "path", key.getPath());
            if (key.getDescription() != null) {
                appendTextElement(keyElement, "description", key.getDescription());
            }
            createdKeysElement.appendChild(keyElement);
        }
        element.appendChild(createdKeysElement);

        // Modified values
        Element modifiedValuesElement = document.createElementNS(NAMESPACE, "modified-values");
        for (UninstallManifest.ModifiedRegistryValue value : registry.getModifiedValues()) {
            Element valueElement = document.createElementNS(NAMESPACE, "value");
            valueElement.setAttribute("root", value.getRoot().getValue());
            valueElement.setAttribute("previous-type", value.getPreviousType().getValue());
            appendTextElement(valueElement, "path", value.getPath());
            appendTextElement(valueElement, "name", value.getName());
            if (value.getPreviousValue() != null) {
                appendTextElement(valueElement, "previous-value", value.getPreviousValue());
            }
            if (value.getDescription() != null) {
                appendTextElement(valueElement, "description", value.getDescription());
            }
            modifiedValuesElement.appendChild(valueElement);
        }
        element.appendChild(modifiedValuesElement);

        return element;
    }

    /**
     * Creates the path-modifications XML element containing PATH and shell profile modifications.
     */
    private Element createPathModificationsElement(UninstallManifest.PathModifications pathMods) {
        Element element = document.createElementNS(NAMESPACE, "path-modifications");

        // Windows PATH entries
        if (!pathMods.getWindowsPaths().isEmpty()) {
            Element windowsElement = document.createElementNS(NAMESPACE, "windows-paths");
            for (UninstallManifest.WindowsPathEntry entry : pathMods.getWindowsPaths()) {
                Element entryElement = document.createElementNS(NAMESPACE, "entry");
                appendTextElement(entryElement, "added-entry", entry.getAddedEntry());
                if (entry.getDescription() != null) {
                    appendTextElement(entryElement, "description", entry.getDescription());
                }
                windowsElement.appendChild(entryElement);
            }
            element.appendChild(windowsElement);
        }

        // Shell profile entries
        if (!pathMods.getShellProfiles().isEmpty()) {
            Element shellElement = document.createElementNS(NAMESPACE, "shell-profiles");
            for (UninstallManifest.ShellProfileEntry entry : pathMods.getShellProfiles()) {
                Element entryElement = document.createElementNS(NAMESPACE, "entry");
                appendTextElement(entryElement, "file", entry.getFile());
                appendTextElement(entryElement, "export-line", entry.getExportLine());
                if (entry.getDescription() != null) {
                    appendTextElement(entryElement, "description", entry.getDescription());
                }
                shellElement.appendChild(entryElement);
            }
            element.appendChild(shellElement);
        }

        // Git Bash profile entries
        if (!pathMods.getGitBashProfiles().isEmpty()) {
            Element gitBashElement = document.createElementNS(NAMESPACE, "git-bash-profiles");
            for (UninstallManifest.GitBashProfileEntry entry : pathMods.getGitBashProfiles()) {
                Element entryElement = document.createElementNS(NAMESPACE, "entry");
                appendTextElement(entryElement, "file", entry.getFile());
                appendTextElement(entryElement, "export-line", entry.getExportLine());
                if (entry.getDescription() != null) {
                    appendTextElement(entryElement, "description", entry.getDescription());
                }
                gitBashElement.appendChild(entryElement);
            }
            element.appendChild(gitBashElement);
        }

        return element;
    }

    /**
     * Helper method to append a text element to a parent element.
     */
    private void appendTextElement(Element parent, String elementName, String textContent) {
        Element child = document.createElementNS(NAMESPACE, elementName);
        child.setTextContent(textContent);
        parent.appendChild(child);
    }
}
