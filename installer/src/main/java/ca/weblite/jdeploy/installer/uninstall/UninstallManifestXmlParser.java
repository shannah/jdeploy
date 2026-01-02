package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser for UninstallManifest XML documents.
 * 
 * Converts XML DOM documents back into UninstallManifest POJOs.
 */
public class UninstallManifestXmlParser {

    private static final String NAMESPACE = "http://jdeploy.ca/uninstall-manifest/1.0";

    /**
     * Parses an XML document into an UninstallManifest.
     *
     * @param document the XML document to parse
     * @return the parsed UninstallManifest
     */
    public UninstallManifest parse(Document document) {
        Element root = document.getDocumentElement();
        
        String version = root.getAttribute("version");
        
        Element packageInfoElem = getElementByTagName(root, "package-info");
        UninstallManifest.PackageInfo packageInfo = parsePackageInfo(packageInfoElem);
        
        List<UninstallManifest.InstalledFile> files = parseFiles(root);
        List<UninstallManifest.InstalledDirectory> directories = parseDirectories(root);
        
        UninstallManifest.RegistryInfo registry = null;
        Element registryElem = getElementByTagName(root, "registry");
        if (registryElem != null) {
            registry = parseRegistry(registryElem);
        }
        
        UninstallManifest.PathModifications pathMods = null;
        Element pathElem = getElementByTagName(root, "path-modifications");
        if (pathElem != null) {
            pathMods = parsePathModifications(pathElem);
        }
        
        return UninstallManifest.builder()
            .version(version)
            .packageInfo(packageInfo)
            .files(files)
            .directories(directories)
            .registry(registry)
            .pathModifications(pathMods)
            .build();
    }

    private UninstallManifest.PackageInfo parsePackageInfo(Element elem) {
        String name = getTextContent(elem, "name");
        String source = getTextContent(elem, "source");
        String version = getTextContent(elem, "version");
        String fqn = getTextContent(elem, "fully-qualified-name");
        String arch = getTextContent(elem, "architecture");
        String installedAtStr = getTextContent(elem, "installed-at");
        String installerVersion = getTextContent(elem, "installer-version");
        
        return UninstallManifest.PackageInfo.builder()
            .name(name)
            .source(source.isEmpty() ? null : source)
            .version(version)
            .fullyQualifiedName(fqn)
            .architecture(arch)
            .installedAt(Instant.parse(installedAtStr))
            .installerVersion(installerVersion)
            .build();
    }

    private List<UninstallManifest.InstalledFile> parseFiles(Element root) {
        List<UninstallManifest.InstalledFile> files = new ArrayList<>();
        Element filesElem = getElementByTagName(root, "files");
        
        if (filesElem != null) {
            NodeList fileElems = filesElem.getElementsByTagName("file");
            for (int i = 0; i < fileElems.getLength(); i++) {
                Element fileElem = (Element) fileElems.item(i);
                String path = getTextContent(fileElem, "path");
                String typeStr = fileElem.getAttribute("type");
                String description = getTextContent(fileElem, "description");
                
                files.add(UninstallManifest.InstalledFile.builder()
                    .path(path)
                    .type(UninstallManifest.FileType.fromValue(typeStr))
                    .description(description.isEmpty() ? null : description)
                    .build());
            }
        }
        
        return files;
    }

    private List<UninstallManifest.InstalledDirectory> parseDirectories(Element root) {
        List<UninstallManifest.InstalledDirectory> directories = new ArrayList<>();
        Element dirsElem = getElementByTagName(root, "directories");
        
        if (dirsElem != null) {
            NodeList dirElems = dirsElem.getElementsByTagName("directory");
            for (int i = 0; i < dirElems.getLength(); i++) {
                Element dirElem = (Element) dirElems.item(i);
                String path = getTextContent(dirElem, "path");
                String cleanupStr = dirElem.getAttribute("cleanup");
                String description = getTextContent(dirElem, "description");
                
                directories.add(UninstallManifest.InstalledDirectory.builder()
                    .path(path)
                    .cleanup(UninstallManifest.CleanupStrategy.fromValue(cleanupStr))
                    .description(description.isEmpty() ? null : description)
                    .build());
            }
        }
        
        return directories;
    }

    private UninstallManifest.RegistryInfo parseRegistry(Element elem) {
        List<UninstallManifest.RegistryKey> createdKeys = new ArrayList<>();
        Element keysElem = getElementByTagName(elem, "created-keys");
        if (keysElem != null) {
            NodeList keyElems = keysElem.getElementsByTagName("key");
            for (int i = 0; i < keyElems.getLength(); i++) {
                Element keyElem = (Element) keyElems.item(i);
                String rootStr = keyElem.getAttribute("root");
                String path = getTextContent(keyElem, "path");
                String description = getTextContent(keyElem, "description");
                
                createdKeys.add(UninstallManifest.RegistryKey.builder()
                    .root(UninstallManifest.RegistryRoot.fromValue(rootStr))
                    .path(path)
                    .description(description.isEmpty() ? null : description)
                    .build());
            }
        }
        
        List<UninstallManifest.ModifiedRegistryValue> modifiedValues = new ArrayList<>();
        Element valuesElem = getElementByTagName(elem, "modified-values");
        if (valuesElem != null) {
            NodeList valueElems = valuesElem.getElementsByTagName("value");
            for (int i = 0; i < valueElems.getLength(); i++) {
                Element valueElem = (Element) valueElems.item(i);
                String rootStr = valueElem.getAttribute("root");
                String path = getTextContent(valueElem, "path");
                String name = getTextContent(valueElem, "name");
                String previousValue = getTextContent(valueElem, "previous-value");
                String typeStr = valueElem.getAttribute("previous-type");
                String description = getTextContent(valueElem, "description");
                
                modifiedValues.add(UninstallManifest.ModifiedRegistryValue.builder()
                    .root(UninstallManifest.RegistryRoot.fromValue(rootStr))
                    .path(path)
                    .name(name)
                    .previousValue(previousValue.isEmpty() ? null : previousValue)
                    .previousType(UninstallManifest.RegistryValueType.fromValue(typeStr))
                    .description(description.isEmpty() ? null : description)
                    .build());
            }
        }
        
        return UninstallManifest.RegistryInfo.builder()
            .createdKeys(createdKeys)
            .modifiedValues(modifiedValues)
            .build();
    }

    private UninstallManifest.PathModifications parsePathModifications(Element elem) {
        List<UninstallManifest.WindowsPathEntry> windowsPaths = new ArrayList<>();
        Element windowsElem = getElementByTagName(elem, "windows-paths");
        if (windowsElem != null) {
            NodeList entryElems = windowsElem.getElementsByTagName("entry");
            for (int i = 0; i < entryElems.getLength(); i++) {
                Element entryElem = (Element) entryElems.item(i);
                String entry = getTextContent(entryElem, "added-entry");
                String description = getTextContent(entryElem, "description");
                
                windowsPaths.add(UninstallManifest.WindowsPathEntry.builder()
                    .addedEntry(entry)
                    .description(description.isEmpty() ? null : description)
                    .build());
            }
        }
        
        List<UninstallManifest.ShellProfileEntry> shellProfiles = new ArrayList<>();
        Element shellElem = getElementByTagName(elem, "shell-profiles");
        if (shellElem != null) {
            NodeList profileElems = shellElem.getElementsByTagName("entry");
            for (int i = 0; i < profileElems.getLength(); i++) {
                Element profileElem = (Element) profileElems.item(i);
                String file = getTextContent(profileElem, "file");
                String exportLine = getTextContent(profileElem, "export-line");
                String description = getTextContent(profileElem, "description");
                
                shellProfiles.add(UninstallManifest.ShellProfileEntry.builder()
                    .file(file)
                    .exportLine(exportLine)
                    .description(description.isEmpty() ? null : description)
                    .build());
            }
        }
        
        List<UninstallManifest.GitBashProfileEntry> gitBashProfiles = new ArrayList<>();
        Element gitBashElem = getElementByTagName(elem, "git-bash-profiles");
        if (gitBashElem != null) {
            NodeList profileElems = gitBashElem.getElementsByTagName("entry");
            for (int i = 0; i < profileElems.getLength(); i++) {
                Element profileElem = (Element) profileElems.item(i);
                String file = getTextContent(profileElem, "file");
                String exportLine = getTextContent(profileElem, "export-line");
                String description = getTextContent(profileElem, "description");
                
                gitBashProfiles.add(UninstallManifest.GitBashProfileEntry.builder()
                    .file(file)
                    .exportLine(exportLine)
                    .description(description.isEmpty() ? null : description)
                    .build());
            }
        }
        
        return UninstallManifest.PathModifications.builder()
            .windowsPaths(windowsPaths)
            .shellProfiles(shellProfiles)
            .gitBashProfiles(gitBashProfiles)
            .build();
    }

    /**
     * Gets the first child element with the given tag name, handling namespaces.
     */
    private Element getElementByTagName(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS(NAMESPACE, tagName);
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        return null;
    }

    /**
     * Gets the text content of a child element.
     */
    private String getTextContent(Element parent, String childTagName) {
        Element child = getElementByTagName(parent, childTagName);
        if (child != null) {
            String content = child.getTextContent();
            return content != null ? content : "";
        }
        return "";
    }
}
