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

        Element root = document.createElementNS(NAMESPACE, "uninstallManifest");
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

        if (manifest.getAiIntegrations() != null) {
            root.appendChild(createAiIntegrationsElement(manifest.getAiIntegrations()));
        }

        return document;
    }

    /**
     * Creates the package-info XML element.
     */
    private Element createPackageInfoElement(UninstallManifest.PackageInfo packageInfo) {
        Element element = document.createElementNS(NAMESPACE, "packageInfo");

        appendTextElement(element, "name", packageInfo.getName());
        if (packageInfo.getSource() != null) {
            appendTextElement(element, "source", packageInfo.getSource());
        }
        appendTextElement(element, "version", packageInfo.getVersion());
        appendTextElement(element, "fullyQualifiedName", packageInfo.getFullyQualifiedName());
        appendTextElement(element, "architecture", packageInfo.getArchitecture());
        appendTextElement(element, "installedAt", packageInfo.getInstalledAt().toString());
        appendTextElement(element, "installerVersion", packageInfo.getInstallerVersion());

        return element;
    }

    /**
     * Creates the files XML element containing all installed files.
     */
    private Element createFilesElement(List<UninstallManifest.InstalledFile> files) {
        Element element = document.createElementNS(NAMESPACE, "files");

        for (UninstallManifest.InstalledFile file : files) {
            Element fileElement = document.createElementNS(NAMESPACE, "file");
            appendTextElement(fileElement, "path", file.getPath());
            appendTextElement(fileElement, "type", file.getType().getValue());
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
            appendTextElement(dirElement, "path", directory.getPath());
            appendTextElement(dirElement, "cleanup", directory.getCleanup().getValue());
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
        Element createdKeysElement = document.createElementNS(NAMESPACE, "createdKeys");
        for (UninstallManifest.RegistryKey key : registry.getCreatedKeys()) {
            Element keyElement = document.createElementNS(NAMESPACE, "createdKey");
            appendTextElement(keyElement, "root", key.getRoot().getValue());
            appendTextElement(keyElement, "path", key.getPath());
            if (key.getDescription() != null) {
                appendTextElement(keyElement, "description", key.getDescription());
            }
            createdKeysElement.appendChild(keyElement);
        }
        element.appendChild(createdKeysElement);

        // Modified values
        Element modifiedValuesElement = document.createElementNS(NAMESPACE, "modifiedValues");
        for (UninstallManifest.ModifiedRegistryValue value : registry.getModifiedValues()) {
            Element valueElement = document.createElementNS(NAMESPACE, "modifiedValue");
            appendTextElement(valueElement, "root", value.getRoot().getValue());
            appendTextElement(valueElement, "path", value.getPath());
            appendTextElement(valueElement, "name", value.getName());
            if (value.getPreviousValue() != null) {
                appendTextElement(valueElement, "previousValue", value.getPreviousValue());
            }
            appendTextElement(valueElement, "previousType", value.getPreviousType().getValue());
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
        Element element = document.createElementNS(NAMESPACE, "pathModifications");

        // Windows PATH entries
        if (!pathMods.getWindowsPaths().isEmpty()) {
            Element windowsElement = document.createElementNS(NAMESPACE, "windowsPaths");
            for (UninstallManifest.WindowsPathEntry entry : pathMods.getWindowsPaths()) {
                Element entryElement = document.createElementNS(NAMESPACE, "windowsPath");
                appendTextElement(entryElement, "addedEntry", entry.getAddedEntry());
                if (entry.getDescription() != null) {
                    appendTextElement(entryElement, "description", entry.getDescription());
                }
                windowsElement.appendChild(entryElement);
            }
            element.appendChild(windowsElement);
        }

        // Shell profile entries
        if (!pathMods.getShellProfiles().isEmpty()) {
            Element shellElement = document.createElementNS(NAMESPACE, "shellProfiles");
            for (UninstallManifest.ShellProfileEntry entry : pathMods.getShellProfiles()) {
                Element entryElement = document.createElementNS(NAMESPACE, "shellProfile");
                appendTextElement(entryElement, "file", entry.getFile());
                appendTextElement(entryElement, "exportLine", entry.getExportLine());
                if (entry.getDescription() != null) {
                    appendTextElement(entryElement, "description", entry.getDescription());
                }
                shellElement.appendChild(entryElement);
            }
            element.appendChild(shellElement);
        }

        // Git Bash profile entries
        if (!pathMods.getGitBashProfiles().isEmpty()) {
            Element gitBashElement = document.createElementNS(NAMESPACE, "gitBashProfiles");
            for (UninstallManifest.GitBashProfileEntry entry : pathMods.getGitBashProfiles()) {
                Element entryElement = document.createElementNS(NAMESPACE, "gitBashProfile");
                appendTextElement(entryElement, "file", entry.getFile());
                appendTextElement(entryElement, "exportLine", entry.getExportLine());
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
     * Creates the aiIntegrations XML element containing AI tool integration entries.
     */
    private Element createAiIntegrationsElement(UninstallManifest.AiIntegrations aiIntegrations) {
        Element element = document.createElementNS(NAMESPACE, "aiIntegrations");

        // MCP servers
        if (!aiIntegrations.getMcpServers().isEmpty()) {
            Element mcpServersElement = document.createElementNS(NAMESPACE, "mcpServers");
            for (UninstallManifest.McpServerEntry entry : aiIntegrations.getMcpServers()) {
                Element entryElement = document.createElementNS(NAMESPACE, "mcpServer");
                appendTextElement(entryElement, "configFile", entry.getConfigFile());
                appendTextElement(entryElement, "entryKey", entry.getEntryKey());
                appendTextElement(entryElement, "toolName", entry.getToolName());
                mcpServersElement.appendChild(entryElement);
            }
            element.appendChild(mcpServersElement);
        }

        // Skills
        if (!aiIntegrations.getSkills().isEmpty()) {
            Element skillsElement = document.createElementNS(NAMESPACE, "skills");
            for (UninstallManifest.SkillEntry entry : aiIntegrations.getSkills()) {
                Element entryElement = document.createElementNS(NAMESPACE, "skill");
                appendTextElement(entryElement, "path", entry.getPath());
                appendTextElement(entryElement, "name", entry.getName());
                skillsElement.appendChild(entryElement);
            }
            element.appendChild(skillsElement);
        }

        // Agents
        if (!aiIntegrations.getAgents().isEmpty()) {
            Element agentsElement = document.createElementNS(NAMESPACE, "agents");
            for (UninstallManifest.AgentEntry entry : aiIntegrations.getAgents()) {
                Element entryElement = document.createElementNS(NAMESPACE, "agent");
                appendTextElement(entryElement, "path", entry.getPath());
                appendTextElement(entryElement, "name", entry.getName());
                agentsElement.appendChild(entryElement);
            }
            element.appendChild(agentsElement);
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
