package ca.weblite.jdeploy.installer.uninstall.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable domain model representing a jDeploy uninstall manifest.
 * Tracks all installation artifacts (files, directories, registry entries, PATH modifications)
 * needed for clean uninstallation.
 *
 * Root element of the manifest XML structure.
 */
public final class UninstallManifest {
    private final String version;
    private final PackageInfo packageInfo;
    private final List<InstalledFile> files;
    private final List<InstalledDirectory> directories;
    private final RegistryInfo registry;
    private final PathModifications pathModifications;
    private final AiIntegrations aiIntegrations;

    private UninstallManifest(Builder builder) {
        this.version = Objects.requireNonNull(builder.version, "version cannot be null");
        this.packageInfo = Objects.requireNonNull(builder.packageInfo, "packageInfo cannot be null");
        this.files = Collections.unmodifiableList(
            Objects.requireNonNull(builder.files, "files cannot be null")
        );
        this.directories = Collections.unmodifiableList(
            Objects.requireNonNull(builder.directories, "directories cannot be null")
        );
        this.registry = builder.registry;
        this.pathModifications = builder.pathModifications;
        this.aiIntegrations = builder.aiIntegrations;
    }

    public String getVersion() {
        return version;
    }

    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public List<InstalledFile> getFiles() {
        return files;
    }

    public List<InstalledDirectory> getDirectories() {
        return directories;
    }

    public RegistryInfo getRegistry() {
        return registry;
    }

    public PathModifications getPathModifications() {
        return pathModifications;
    }

    public AiIntegrations getAiIntegrations() {
        return aiIntegrations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UninstallManifest that = (UninstallManifest) o;
        return Objects.equals(version, that.version) &&
               Objects.equals(packageInfo, that.packageInfo) &&
               Objects.equals(files, that.files) &&
               Objects.equals(directories, that.directories) &&
               Objects.equals(registry, that.registry) &&
               Objects.equals(pathModifications, that.pathModifications) &&
               Objects.equals(aiIntegrations, that.aiIntegrations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, packageInfo, files, directories, registry, pathModifications, aiIntegrations);
    }

    @Override
    public String toString() {
        return "UninstallManifest{" +
               "version='" + version + '\'' +
               ", packageInfo=" + packageInfo +
               ", files=" + files +
               ", directories=" + directories +
               ", registry=" + registry +
               ", pathModifications=" + pathModifications +
               ", aiIntegrations=" + aiIntegrations +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String version;
        private PackageInfo packageInfo;
        private List<InstalledFile> files = Collections.emptyList();
        private List<InstalledDirectory> directories = Collections.emptyList();
        private RegistryInfo registry;
        private PathModifications pathModifications;
        private AiIntegrations aiIntegrations;

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder packageInfo(PackageInfo packageInfo) {
            this.packageInfo = packageInfo;
            return this;
        }

        public Builder files(List<InstalledFile> files) {
            this.files = files != null ? files : Collections.emptyList();
            return this;
        }

        public Builder directories(List<InstalledDirectory> directories) {
            this.directories = directories != null ? directories : Collections.emptyList();
            return this;
        }

        public Builder registry(RegistryInfo registry) {
            this.registry = registry;
            return this;
        }

        public Builder pathModifications(PathModifications pathModifications) {
            this.pathModifications = pathModifications;
            return this;
        }

        public Builder aiIntegrations(AiIntegrations aiIntegrations) {
            this.aiIntegrations = aiIntegrations;
            return this;
        }

        public UninstallManifest build() {
            return new UninstallManifest(this);
        }
    }

    /**
     * Metadata about the installed package.
     */
    public static final class PackageInfo {
        private final String name;
        private final String source;
        private final String version;
        private final String fullyQualifiedName;
        private final String architecture;
        private final Instant installedAt;
        private final String installerVersion;

        private PackageInfo(Builder builder) {
            this.name = Objects.requireNonNull(builder.name, "name cannot be null");
            this.source = builder.source;
            this.version = Objects.requireNonNull(builder.version, "version cannot be null");
            this.fullyQualifiedName = Objects.requireNonNull(builder.fullyQualifiedName, "fullyQualifiedName cannot be null");
            this.architecture = Objects.requireNonNull(builder.architecture, "architecture cannot be null");
            this.installedAt = Objects.requireNonNull(builder.installedAt, "installedAt cannot be null");
            this.installerVersion = Objects.requireNonNull(builder.installerVersion, "installerVersion cannot be null");
        }

        public String getName() {
            return name;
        }

        public String getSource() {
            return source;
        }

        public String getVersion() {
            return version;
        }

        public String getFullyQualifiedName() {
            return fullyQualifiedName;
        }

        public String getArchitecture() {
            return architecture;
        }

        public Instant getInstalledAt() {
            return installedAt;
        }

        public String getInstallerVersion() {
            return installerVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PackageInfo that = (PackageInfo) o;
            return Objects.equals(name, that.name) &&
                   Objects.equals(source, that.source) &&
                   Objects.equals(version, that.version) &&
                   Objects.equals(fullyQualifiedName, that.fullyQualifiedName) &&
                   Objects.equals(architecture, that.architecture) &&
                   Objects.equals(installedAt, that.installedAt) &&
                   Objects.equals(installerVersion, that.installerVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, source, version, fullyQualifiedName, architecture, installedAt, installerVersion);
        }

        @Override
        public String toString() {
            return "PackageInfo{" +
                   "name='" + name + '\'' +
                   ", source='" + source + '\'' +
                   ", version='" + version + '\'' +
                   ", fullyQualifiedName='" + fullyQualifiedName + '\'' +
                   ", architecture='" + architecture + '\'' +
                   ", installedAt=" + installedAt +
                   ", installerVersion='" + installerVersion + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String name;
            private String source;
            private String version;
            private String fullyQualifiedName;
            private String architecture;
            private Instant installedAt;
            private String installerVersion;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder source(String source) {
                this.source = source;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public Builder fullyQualifiedName(String fullyQualifiedName) {
                this.fullyQualifiedName = fullyQualifiedName;
                return this;
            }

            public Builder architecture(String architecture) {
                this.architecture = architecture;
                return this;
            }

            public Builder installedAt(Instant installedAt) {
                this.installedAt = installedAt;
                return this;
            }

            public Builder installerVersion(String installerVersion) {
                this.installerVersion = installerVersion;
                return this;
            }

            public PackageInfo build() {
                return new PackageInfo(this);
            }
        }
    }

    /**
     * A file created during installation to be removed during uninstall.
     */
    public static final class InstalledFile {
        private final String path;
        private final FileType type;
        private final String description;

        private InstalledFile(Builder builder) {
            this.path = Objects.requireNonNull(builder.path, "path cannot be null");
            this.type = Objects.requireNonNull(builder.type, "type cannot be null");
            this.description = builder.description;
        }

        public String getPath() {
            return path;
        }

        public FileType getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InstalledFile that = (InstalledFile) o;
            return Objects.equals(path, that.path) &&
                   type == that.type &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, type, description);
        }

        @Override
        public String toString() {
            return "InstalledFile{" +
                   "path='" + path + '\'' +
                   ", type=" + type +
                   ", description='" + description + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String path;
            private FileType type;
            private String description;

            public Builder path(String path) {
                this.path = path;
                return this;
            }

            public Builder type(FileType type) {
                this.type = type;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public InstalledFile build() {
                return new InstalledFile(this);
            }
        }
    }

    /**
     * File type classification.
     */
    public enum FileType {
        BINARY("binary", "Executable binary or library file"),
        SCRIPT("script", "Shell script or command wrapper"),
        LINK("link", "Symbolic link or shortcut"),
        CONFIG("config", "Configuration file"),
        ICON("icon", "Icon image file"),
        METADATA("metadata", "Manifest, backup log, or other metadata");

        private final String value;
        private final String description;

        FileType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static FileType fromValue(String value) {
            for (FileType type : FileType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown FileType: " + value);
        }
    }

    /**
     * A directory created during installation to be cleaned up during uninstall.
     */
    public static final class InstalledDirectory {
        private final String path;
        private final CleanupStrategy cleanup;
        private final String description;

        private InstalledDirectory(Builder builder) {
            this.path = Objects.requireNonNull(builder.path, "path cannot be null");
            this.cleanup = Objects.requireNonNull(builder.cleanup, "cleanup cannot be null");
            this.description = builder.description;
        }

        public String getPath() {
            return path;
        }

        public CleanupStrategy getCleanup() {
            return cleanup;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InstalledDirectory that = (InstalledDirectory) o;
            return Objects.equals(path, that.path) &&
                   cleanup == that.cleanup &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, cleanup, description);
        }

        @Override
        public String toString() {
            return "InstalledDirectory{" +
                   "path='" + path + '\'' +
                   ", cleanup=" + cleanup +
                   ", description='" + description + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String path;
            private CleanupStrategy cleanup;
            private String description;

            public Builder path(String path) {
                this.path = path;
                return this;
            }

            public Builder cleanup(CleanupStrategy cleanup) {
                this.cleanup = cleanup;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public InstalledDirectory build() {
                return new InstalledDirectory(this);
            }
        }
    }

    /**
     * Cleanup strategy for directories.
     */
    public enum CleanupStrategy {
        ALWAYS("always", "Delete directory and all contents unconditionally"),
        IF_EMPTY("ifEmpty", "Delete only if empty after file removal"),
        CONTENTS_ONLY("contentsOnly", "Delete contents but keep the directory itself");

        private final String value;
        private final String description;

        CleanupStrategy(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static CleanupStrategy fromValue(String value) {
            for (CleanupStrategy strategy : CleanupStrategy.values()) {
                if (strategy.value.equals(value)) {
                    return strategy;
                }
            }
            throw new IllegalArgumentException("Unknown CleanupStrategy: " + value);
        }
    }

    /**
     * Windows registry entries created or modified during installation.
     */
    public static final class RegistryInfo {
        private final List<RegistryKey> createdKeys;
        private final List<ModifiedRegistryValue> modifiedValues;

        private RegistryInfo(Builder builder) {
            this.createdKeys = Collections.unmodifiableList(
                Objects.requireNonNull(builder.createdKeys, "createdKeys cannot be null")
            );
            this.modifiedValues = Collections.unmodifiableList(
                Objects.requireNonNull(builder.modifiedValues, "modifiedValues cannot be null")
            );
        }

        public List<RegistryKey> getCreatedKeys() {
            return createdKeys;
        }

        public List<ModifiedRegistryValue> getModifiedValues() {
            return modifiedValues;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegistryInfo that = (RegistryInfo) o;
            return Objects.equals(createdKeys, that.createdKeys) &&
                   Objects.equals(modifiedValues, that.modifiedValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(createdKeys, modifiedValues);
        }

        @Override
        public String toString() {
            return "RegistryInfo{" +
                   "createdKeys=" + createdKeys +
                   ", modifiedValues=" + modifiedValues +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private List<RegistryKey> createdKeys = Collections.emptyList();
            private List<ModifiedRegistryValue> modifiedValues = Collections.emptyList();

            public Builder createdKeys(List<RegistryKey> createdKeys) {
                this.createdKeys = createdKeys != null ? createdKeys : Collections.emptyList();
                return this;
            }

            public Builder modifiedValues(List<ModifiedRegistryValue> modifiedValues) {
                this.modifiedValues = modifiedValues != null ? modifiedValues : Collections.emptyList();
                return this;
            }

            public RegistryInfo build() {
                return new RegistryInfo(this);
            }
        }
    }

    /**
     * A registry key created during installation, to be deleted during uninstall.
     */
    public static final class RegistryKey {
        private final RegistryRoot root;
        private final String path;
        private final String description;

        private RegistryKey(Builder builder) {
            this.root = Objects.requireNonNull(builder.root, "root cannot be null");
            this.path = Objects.requireNonNull(builder.path, "path cannot be null");
            this.description = builder.description;
        }

        public RegistryRoot getRoot() {
            return root;
        }

        public String getPath() {
            return path;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegistryKey that = (RegistryKey) o;
            return root == that.root &&
                   Objects.equals(path, that.path) &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, path, description);
        }

        @Override
        public String toString() {
            return "RegistryKey{" +
                   "root=" + root +
                   ", path='" + path + '\'' +
                   ", description='" + description + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private RegistryRoot root;
            private String path;
            private String description;

            public Builder root(RegistryRoot root) {
                this.root = root;
                return this;
            }

            public Builder path(String path) {
                this.path = path;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public RegistryKey build() {
                return new RegistryKey(this);
            }
        }
    }

    /**
     * Windows registry root hive.
     */
    public enum RegistryRoot {
        HKEY_CURRENT_USER("HKEY_CURRENT_USER", "Per-user registry (HKCU)"),
        HKEY_LOCAL_MACHINE("HKEY_LOCAL_MACHINE", "System-wide registry (HKLM, requires admin)");

        private final String value;
        private final String description;

        RegistryRoot(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static RegistryRoot fromValue(String value) {
            for (RegistryRoot root : RegistryRoot.values()) {
                if (root.value.equals(value)) {
                    return root;
                }
            }
            throw new IllegalArgumentException("Unknown RegistryRoot: " + value);
        }
    }

    /**
     * A registry value modified during installation. On uninstall, restore to previousValue if it exists,
     * otherwise delete.
     */
    public static final class ModifiedRegistryValue {
        private final RegistryRoot root;
        private final String path;
        private final String name;
        private final String previousValue;
        private final RegistryValueType previousType;
        private final String description;

        private ModifiedRegistryValue(Builder builder) {
            this.root = Objects.requireNonNull(builder.root, "root cannot be null");
            this.path = Objects.requireNonNull(builder.path, "path cannot be null");
            this.name = Objects.requireNonNull(builder.name, "name cannot be null");
            this.previousValue = builder.previousValue;
            this.previousType = Objects.requireNonNull(builder.previousType, "previousType cannot be null");
            this.description = builder.description;
        }

        public RegistryRoot getRoot() {
            return root;
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }

        public String getPreviousValue() {
            return previousValue;
        }

        public RegistryValueType getPreviousType() {
            return previousType;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ModifiedRegistryValue that = (ModifiedRegistryValue) o;
            return root == that.root &&
                   Objects.equals(path, that.path) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(previousValue, that.previousValue) &&
                   previousType == that.previousType &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, path, name, previousValue, previousType, description);
        }

        @Override
        public String toString() {
            return "ModifiedRegistryValue{" +
                   "root=" + root +
                   ", path='" + path + '\'' +
                   ", name='" + name + '\'' +
                   ", previousValue='" + previousValue + '\'' +
                   ", previousType=" + previousType +
                   ", description='" + description + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private RegistryRoot root;
            private String path;
            private String name;
            private String previousValue;
            private RegistryValueType previousType;
            private String description;

            public Builder root(RegistryRoot root) {
                this.root = root;
                return this;
            }

            public Builder path(String path) {
                this.path = path;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder previousValue(String previousValue) {
                this.previousValue = previousValue;
                return this;
            }

            public Builder previousType(RegistryValueType previousType) {
                this.previousType = previousType;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public ModifiedRegistryValue build() {
                return new ModifiedRegistryValue(this);
            }
        }
    }

    /**
     * Windows registry value types.
     */
    public enum RegistryValueType {
        REG_SZ("REG_SZ", "String value"),
        REG_EXPAND_SZ("REG_EXPAND_SZ", "Expandable string value"),
        REG_DWORD("REG_DWORD", "32-bit integer value"),
        REG_QWORD("REG_QWORD", "64-bit integer value"),
        REG_BINARY("REG_BINARY", "Binary data"),
        REG_MULTI_SZ("REG_MULTI_SZ", "Multiple string values");

        private final String value;
        private final String description;

        RegistryValueType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static RegistryValueType fromValue(String value) {
            for (RegistryValueType type : RegistryValueType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown RegistryValueType: " + value);
        }
    }

    /**
     * PATH modifications across different platforms and shells.
     */
    public static final class PathModifications {
        private final List<WindowsPathEntry> windowsPaths;
        private final List<ShellProfileEntry> shellProfiles;
        private final List<GitBashProfileEntry> gitBashProfiles;

        private PathModifications(Builder builder) {
            this.windowsPaths = Collections.unmodifiableList(
                Objects.requireNonNull(builder.windowsPaths, "windowsPaths cannot be null")
            );
            this.shellProfiles = Collections.unmodifiableList(
                Objects.requireNonNull(builder.shellProfiles, "shellProfiles cannot be null")
            );
            this.gitBashProfiles = Collections.unmodifiableList(
                Objects.requireNonNull(builder.gitBashProfiles, "gitBashProfiles cannot be null")
            );
        }

        public List<WindowsPathEntry> getWindowsPaths() {
            return windowsPaths;
        }

        public List<ShellProfileEntry> getShellProfiles() {
            return shellProfiles;
        }

        public List<GitBashProfileEntry> getGitBashProfiles() {
            return gitBashProfiles;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PathModifications that = (PathModifications) o;
            return Objects.equals(windowsPaths, that.windowsPaths) &&
                   Objects.equals(shellProfiles, that.shellProfiles) &&
                   Objects.equals(gitBashProfiles, that.gitBashProfiles);
        }

        @Override
        public int hashCode() {
            return Objects.hash(windowsPaths, shellProfiles, gitBashProfiles);
        }

        @Override
        public String toString() {
            return "PathModifications{" +
                   "windowsPaths=" + windowsPaths +
                   ", shellProfiles=" + shellProfiles +
                   ", gitBashProfiles=" + gitBashProfiles +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private List<WindowsPathEntry> windowsPaths = Collections.emptyList();
            private List<ShellProfileEntry> shellProfiles = Collections.emptyList();
            private List<GitBashProfileEntry> gitBashProfiles = Collections.emptyList();

            public Builder windowsPaths(List<WindowsPathEntry> windowsPaths) {
                this.windowsPaths = windowsPaths != null ? windowsPaths : Collections.emptyList();
                return this;
            }

            public Builder shellProfiles(List<ShellProfileEntry> shellProfiles) {
                this.shellProfiles = shellProfiles != null ? shellProfiles : Collections.emptyList();
                return this;
            }

            public Builder gitBashProfiles(List<GitBashProfileEntry> gitBashProfiles) {
                this.gitBashProfiles = gitBashProfiles != null ? gitBashProfiles : Collections.emptyList();
                return this;
            }

            public PathModifications build() {
                return new PathModifications(this);
            }
        }
    }

    /**
     * A directory path added to Windows user PATH via HKCU\Environment\Path registry value.
     */
    public static final class WindowsPathEntry {
        private final String addedEntry;
        private final String description;

        private WindowsPathEntry(Builder builder) {
            this.addedEntry = Objects.requireNonNull(builder.addedEntry, "addedEntry cannot be null");
            this.description = builder.description;
        }

        public String getAddedEntry() {
            return addedEntry;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WindowsPathEntry that = (WindowsPathEntry) o;
            return Objects.equals(addedEntry, that.addedEntry) &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(addedEntry, description);
        }

        @Override
        public String toString() {
            return "WindowsPathEntry{" +
                   "addedEntry='" + addedEntry + '\'' +
                   ", description='" + description + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String addedEntry;
            private String description;

            public Builder addedEntry(String addedEntry) {
                this.addedEntry = addedEntry;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public WindowsPathEntry build() {
                return new WindowsPathEntry(this);
            }
        }
    }

    /**
     * A line added to a shell configuration file (~/.bashrc, ~/.zprofile, etc.).
     */
    public static final class ShellProfileEntry {
        private final String file;
        private final String exportLine;
        private final String description;

        private ShellProfileEntry(Builder builder) {
            this.file = Objects.requireNonNull(builder.file, "file cannot be null");
            this.exportLine = Objects.requireNonNull(builder.exportLine, "exportLine cannot be null");
            this.description = builder.description;
        }

        public String getFile() {
            return file;
        }

        public String getExportLine() {
            return exportLine;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShellProfileEntry that = (ShellProfileEntry) o;
            return Objects.equals(file, that.file) &&
                   Objects.equals(exportLine, that.exportLine) &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, exportLine, description);
        }

        @Override
        public String toString() {
            return "ShellProfileEntry{" +
                   "file='" + file + '\'' +
                   ", exportLine='" + exportLine + '\'' +
                   ", description='" + description + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String file;
            private String exportLine;
            private String description;

            public Builder file(String file) {
                this.file = file;
                return this;
            }

            public Builder exportLine(String exportLine) {
                this.exportLine = exportLine;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public ShellProfileEntry build() {
                return new ShellProfileEntry(this);
            }
        }
    }

    /**
     * A line added to a Git Bash configuration file.
     */
    public static final class GitBashProfileEntry {
        private final String file;
        private final String exportLine;
        private final String description;

        private GitBashProfileEntry(Builder builder) {
            this.file = Objects.requireNonNull(builder.file, "file cannot be null");
            this.exportLine = Objects.requireNonNull(builder.exportLine, "exportLine cannot be null");
            this.description = builder.description;
        }

        public String getFile() {
            return file;
        }

        public String getExportLine() {
            return exportLine;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GitBashProfileEntry that = (GitBashProfileEntry) o;
            return Objects.equals(file, that.file) &&
                   Objects.equals(exportLine, that.exportLine) &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, exportLine, description);
        }

        @Override
        public String toString() {
            return "GitBashProfileEntry{" +
                   "file='" + file + '\'' +
                   ", exportLine='" + exportLine + '\'' +
                   ", description='" + description + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String file;
            private String exportLine;
            private String description;

            public Builder file(String file) {
                this.file = file;
                return this;
            }

            public Builder exportLine(String exportLine) {
                this.exportLine = exportLine;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public GitBashProfileEntry build() {
                return new GitBashProfileEntry(this);
            }
        }
    }

    /**
     * AI integrations (MCP servers, skills, agents) installed to AI tools.
     */
    public static final class AiIntegrations {
        private final List<McpServerEntry> mcpServers;
        private final List<SkillEntry> skills;
        private final List<AgentEntry> agents;

        private AiIntegrations(Builder builder) {
            this.mcpServers = Collections.unmodifiableList(
                Objects.requireNonNull(builder.mcpServers, "mcpServers cannot be null")
            );
            this.skills = Collections.unmodifiableList(
                Objects.requireNonNull(builder.skills, "skills cannot be null")
            );
            this.agents = Collections.unmodifiableList(
                Objects.requireNonNull(builder.agents, "agents cannot be null")
            );
        }

        public List<McpServerEntry> getMcpServers() {
            return mcpServers;
        }

        public List<SkillEntry> getSkills() {
            return skills;
        }

        public List<AgentEntry> getAgents() {
            return agents;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AiIntegrations that = (AiIntegrations) o;
            return Objects.equals(mcpServers, that.mcpServers) &&
                   Objects.equals(skills, that.skills) &&
                   Objects.equals(agents, that.agents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mcpServers, skills, agents);
        }

        @Override
        public String toString() {
            return "AiIntegrations{" +
                   "mcpServers=" + mcpServers +
                   ", skills=" + skills +
                   ", agents=" + agents +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private List<McpServerEntry> mcpServers = Collections.emptyList();
            private List<SkillEntry> skills = Collections.emptyList();
            private List<AgentEntry> agents = Collections.emptyList();

            public Builder mcpServers(List<McpServerEntry> mcpServers) {
                this.mcpServers = mcpServers != null ? mcpServers : Collections.emptyList();
                return this;
            }

            public Builder skills(List<SkillEntry> skills) {
                this.skills = skills != null ? skills : Collections.emptyList();
                return this;
            }

            public Builder agents(List<AgentEntry> agents) {
                this.agents = agents != null ? agents : Collections.emptyList();
                return this;
            }

            public AiIntegrations build() {
                return new AiIntegrations(this);
            }
        }
    }

    /**
     * An MCP server entry added to an AI tool's configuration file.
     */
    public static final class McpServerEntry {
        private final String configFile;
        private final String entryKey;
        private final String toolName;

        private McpServerEntry(Builder builder) {
            this.configFile = Objects.requireNonNull(builder.configFile, "configFile cannot be null");
            this.entryKey = Objects.requireNonNull(builder.entryKey, "entryKey cannot be null");
            this.toolName = Objects.requireNonNull(builder.toolName, "toolName cannot be null");
        }

        public String getConfigFile() {
            return configFile;
        }

        public String getEntryKey() {
            return entryKey;
        }

        public String getToolName() {
            return toolName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            McpServerEntry that = (McpServerEntry) o;
            return Objects.equals(configFile, that.configFile) &&
                   Objects.equals(entryKey, that.entryKey) &&
                   Objects.equals(toolName, that.toolName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configFile, entryKey, toolName);
        }

        @Override
        public String toString() {
            return "McpServerEntry{" +
                   "configFile='" + configFile + '\'' +
                   ", entryKey='" + entryKey + '\'' +
                   ", toolName='" + toolName + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String configFile;
            private String entryKey;
            private String toolName;

            public Builder configFile(String configFile) {
                this.configFile = configFile;
                return this;
            }

            public Builder entryKey(String entryKey) {
                this.entryKey = entryKey;
                return this;
            }

            public Builder toolName(String toolName) {
                this.toolName = toolName;
                return this;
            }

            public McpServerEntry build() {
                return new McpServerEntry(this);
            }
        }
    }

    /**
     * A skill installed to an AI tool's skills directory.
     */
    public static final class SkillEntry {
        private final String path;
        private final String name;

        private SkillEntry(Builder builder) {
            this.path = Objects.requireNonNull(builder.path, "path cannot be null");
            this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SkillEntry that = (SkillEntry) o;
            return Objects.equals(path, that.path) &&
                   Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name);
        }

        @Override
        public String toString() {
            return "SkillEntry{" +
                   "path='" + path + '\'' +
                   ", name='" + name + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String path;
            private String name;

            public Builder path(String path) {
                this.path = path;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public SkillEntry build() {
                return new SkillEntry(this);
            }
        }
    }

    /**
     * An agent installed to an AI tool's agents directory.
     */
    public static final class AgentEntry {
        private final String path;
        private final String name;

        private AgentEntry(Builder builder) {
            this.path = Objects.requireNonNull(builder.path, "path cannot be null");
            this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgentEntry that = (AgentEntry) o;
            return Objects.equals(path, that.path) &&
                   Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name);
        }

        @Override
        public String toString() {
            return "AgentEntry{" +
                   "path='" + path + '\'' +
                   ", name='" + name + '\'' +
                   '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String path;
            private String name;

            public Builder path(String path) {
                this.path = path;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public AgentEntry build() {
                return new AgentEntry(this);
            }
        }
    }
}
