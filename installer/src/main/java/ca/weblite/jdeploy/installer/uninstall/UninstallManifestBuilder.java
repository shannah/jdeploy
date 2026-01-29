package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for constructing UninstallManifest instances.
 * Supports path variable substitution and accumulation of installation artifacts.
 * 
 * Path variables supported:
 * - ${USER_HOME}: User home directory
 * - ${JDEPLOY_HOME}: jDeploy installation directory
 * - ${APP_DIR}: Application-specific directory
 */
public class UninstallManifestBuilder {
    private static final String MANIFEST_VERSION = "1.0";
    
    private String packageName;
    private String packageSource;
    private String packageVersion;
    private String architecture;
    private String installerVersion;
    private Instant installedAt;
    private String fullyQualifiedName;
    
    private List<UninstallManifest.InstalledFile> files;
    private List<UninstallManifest.InstalledDirectory> directories;
    private List<UninstallManifest.RegistryKey> registryKeys;
    private List<UninstallManifest.ModifiedRegistryValue> modifiedRegistryValues;
    private List<UninstallManifest.WindowsPathEntry> windowsPathEntries;
    private List<UninstallManifest.ShellProfileEntry> shellProfileEntries;
    private List<UninstallManifest.GitBashProfileEntry> gitBashProfileEntries;
    private List<UninstallManifest.McpServerEntry> mcpServerEntries;
    private List<UninstallManifest.SkillEntry> skillEntries;
    private List<UninstallManifest.AgentEntry> agentEntries;

    private Map<String, String> variableSubstitutions;
    
    /**
     * Creates a new UninstallManifestBuilder with default substitution variables.
     */
    public UninstallManifestBuilder() {
        this.files = new ArrayList<>();
        this.directories = new ArrayList<>();
        this.registryKeys = new ArrayList<>();
        this.modifiedRegistryValues = new ArrayList<>();
        this.windowsPathEntries = new ArrayList<>();
        this.shellProfileEntries = new ArrayList<>();
        this.gitBashProfileEntries = new ArrayList<>();
        this.mcpServerEntries = new ArrayList<>();
        this.skillEntries = new ArrayList<>();
        this.agentEntries = new ArrayList<>();
        this.variableSubstitutions = new HashMap<>();
        this.installedAt = Instant.now();
        initializeDefaultVariables();
    }
    
    /**
     * Initialize default path variable substitutions.
     */
    private void initializeDefaultVariables() {
        String userHome = System.getProperty("user.home");
        variableSubstitutions.put("USER_HOME", userHome);
        
        String jdeployHome = System.getProperty("user.home") + File.separator + ".jdeploy";
        variableSubstitutions.put("JDEPLOY_HOME", jdeployHome);
    }
    
    /**
     * Set package information in one call.
     * Computes fully qualified package name from name and source.
     * 
     * @param name         Package name
     * @param source       Package source (e.g., npm registry URL or GitHub URL)
     * @param version      Package version
     * @param architecture Target architecture (e.g., x64, arm64)
     * @return this builder instance
     * @throws IllegalStateException if name, version, or architecture are empty or null
     */
    public UninstallManifestBuilder withPackageInfo(String name, String source, String version, String architecture) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(architecture, "architecture cannot be null");
        
        // Validate non-empty
        if (name.isEmpty()) {
            throw new IllegalStateException("Package name cannot be empty");
        }
        if (version.isEmpty()) {
            throw new IllegalStateException("Package version cannot be empty");
        }
        if (architecture.isEmpty()) {
            throw new IllegalStateException("Package architecture cannot be empty");
        }
        
        this.packageName = name;
        this.packageSource = source;
        this.packageVersion = version;
        this.architecture = architecture;
        
        // Compute fully qualified package name using the CLI command bin dir resolver
        try {
            this.fullyQualifiedName = CliCommandBinDirResolver.computeFullyQualifiedPackageName(name, source);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to compute fully qualified package name: " + e.getMessage(), e);
        }
        
        // Set APP_DIR substitution based on the computed FQPN
        String jdeployHome = variableSubstitutions.get("JDEPLOY_HOME");
        String appDir = jdeployHome + File.separator + "apps" + File.separator + fullyQualifiedName;
        variableSubstitutions.put("APP_DIR", appDir);
        
        return this;
    }
    
    /**
     * Override the APP_DIR substitution to use a custom Windows app directory.
     * Must be called after {@link #withPackageInfo} since it depends on the fully qualified name.
     *
     * @param winAppDir The winAppDir value (relative to user home), or null to keep default
     * @return this builder instance
     */
    public UninstallManifestBuilder withWinAppDir(String winAppDir) {
        if (winAppDir != null && !winAppDir.isEmpty() && fullyQualifiedName != null) {
            String userHome = variableSubstitutions.get("USER_HOME");
            String appDir = userHome + File.separator + winAppDir + File.separator + fullyQualifiedName;
            variableSubstitutions.put("APP_DIR", appDir);
        }
        return this;
    }

    /**
     * Set the installer version.
     *
     * @param version Installer version string
     * @return this builder instance
     */
    public UninstallManifestBuilder withInstallerVersion(String version) {
        Objects.requireNonNull(version, "installer version cannot be null");
        this.installerVersion = version;
        return this;
    }
    
    /**
     * Set the installation timestamp.
     * 
     * @param instant Installation timestamp
     * @return this builder instance
     */
    public UninstallManifestBuilder withInstalledAt(Instant instant) {
        Objects.requireNonNull(instant, "installedAt cannot be null");
        this.installedAt = instant;
        return this;
    }
    
    /**
     * Add a custom path variable substitution.
     * 
     * @param variable Variable name (e.g., "MY_PATH")
     * @param value    Variable value
     * @return this builder instance
     */
    public UninstallManifestBuilder withVariable(String variable, String value) {
        Objects.requireNonNull(variable, "variable cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        variableSubstitutions.put(variable, value);
        return this;
    }
    
    /**
     * Add an installed file to the manifest.
     * 
     * @param path        File path (may contain variable substitutions)
     * @param type        File type classification
     * @param description Optional description
     * @return this builder instance
     */
    public UninstallManifestBuilder addFile(String path, UninstallManifest.FileType type, String description) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        
        String substitutedPath = substituteVariables(path);
        UninstallManifest.InstalledFile file = UninstallManifest.InstalledFile.builder()
            .path(substitutedPath)
            .type(type)
            .description(description)
            .build();
        files.add(file);
        return this;
    }
    
    /**
     * Add an installed directory to the manifest.
     * 
     * @param path            Directory path (may contain variable substitutions)
     * @param cleanupStrategy Cleanup strategy for the directory
     * @param description     Optional description
     * @return this builder instance
     */
    public UninstallManifestBuilder addDirectory(String path, UninstallManifest.CleanupStrategy cleanupStrategy, String description) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(cleanupStrategy, "cleanupStrategy cannot be null");
        
        String substitutedPath = substituteVariables(path);
        UninstallManifest.InstalledDirectory directory = UninstallManifest.InstalledDirectory.builder()
            .path(substitutedPath)
            .cleanup(cleanupStrategy)
            .description(description)
            .build();
        directories.add(directory);
        return this;
    }
    
    /**
     * Add a Windows registry key created during installation.
     * 
     * @param root Registry root hive
     * @param path Registry key path
     * @return this builder instance
     */
    public UninstallManifestBuilder addCreatedRegistryKey(UninstallManifest.RegistryRoot root, String path) {
        return addCreatedRegistryKey(root, path, null);
    }
    
    /**
     * Add a Windows registry key created during installation.
     * 
     * @param root        Registry root hive
     * @param path        Registry key path
     * @param description Optional description
     * @return this builder instance
     */
    public UninstallManifestBuilder addCreatedRegistryKey(UninstallManifest.RegistryRoot root, String path, String description) {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        
        UninstallManifest.RegistryKey key = UninstallManifest.RegistryKey.builder()
            .root(root)
            .path(path)
            .description(description)
            .build();
        registryKeys.add(key);
        return this;
    }
    
    /**
     * Add a Windows registry value that was modified during installation.
     * 
     * @param root         Registry root hive
     * @param path         Registry key path
     * @param name         Registry value name
     * @param previousValue Previous value (null if it did not exist)
     * @param previousType  Previous value type
     * @return this builder instance
     */
    public UninstallManifestBuilder addModifiedRegistryValue(
            UninstallManifest.RegistryRoot root,
            String path,
            String name,
            String previousValue,
            UninstallManifest.RegistryValueType previousType) {
        return addModifiedRegistryValue(root, path, name, previousValue, previousType, null);
    }
    
    /**
     * Add a Windows registry value that was modified during installation.
     * 
     * @param root         Registry root hive
     * @param path         Registry key path
     * @param name         Registry value name
     * @param previousValue Previous value (null if it did not exist)
     * @param previousType  Previous value type
     * @param description   Optional description
     * @return this builder instance
     */
    public UninstallManifestBuilder addModifiedRegistryValue(
            UninstallManifest.RegistryRoot root,
            String path,
            String name,
            String previousValue,
            UninstallManifest.RegistryValueType previousType,
            String description) {
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(previousType, "previousType cannot be null");
        
        UninstallManifest.ModifiedRegistryValue value = UninstallManifest.ModifiedRegistryValue.builder()
            .root(root)
            .path(path)
            .name(name)
            .previousValue(previousValue)
            .previousType(previousType)
            .description(description)
            .build();
        modifiedRegistryValues.add(value);
        return this;
    }
    
    /**
     * Add a directory path to Windows user PATH environment variable.
     * 
     * @param path Directory path
     * @return this builder instance
     */
    public UninstallManifestBuilder addWindowsPathEntry(String path) {
        return addWindowsPathEntry(path, null);
    }
    
    /**
     * Add a directory path to Windows user PATH environment variable.
     * 
     * @param path        Directory path (may contain variable substitutions)
     * @param description Optional description
     * @return this builder instance
     */
    public UninstallManifestBuilder addWindowsPathEntry(String path, String description) {
        Objects.requireNonNull(path, "path cannot be null");
        
        String substitutedPath = substituteVariables(path);
        UninstallManifest.WindowsPathEntry entry = UninstallManifest.WindowsPathEntry.builder()
            .addedEntry(substitutedPath)
            .description(description)
            .build();
        windowsPathEntries.add(entry);
        return this;
    }
    
    /**
     * Add a shell profile entry (e.g., for ~/.bashrc or ~/.zprofile).
     * 
     * @param file       Shell profile file path (may contain variable substitutions)
     * @param exportLine Export line to add (may contain variable substitutions)
     * @return this builder instance
     */
    public UninstallManifestBuilder addShellProfileEntry(String file, String exportLine) {
        return addShellProfileEntry(file, exportLine, null);
    }
    
    /**
     * Add a shell profile entry (e.g., for ~/.bashrc or ~/.zprofile).
     * 
     * @param file        Shell profile file path (may contain variable substitutions)
     * @param exportLine   Export line to add (may contain variable substitutions)
     * @param description Optional description
     * @return this builder instance
     */
    public UninstallManifestBuilder addShellProfileEntry(String file, String exportLine, String description) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(exportLine, "exportLine cannot be null");
        
        String substitutedFile = substituteVariables(file);
        String substitutedExportLine = substituteVariables(exportLine);
        
        UninstallManifest.ShellProfileEntry entry = UninstallManifest.ShellProfileEntry.builder()
            .file(substitutedFile)
            .exportLine(substitutedExportLine)
            .description(description)
            .build();
        shellProfileEntries.add(entry);
        return this;
    }
    
    /**
     * Add a Git Bash profile entry (e.g., for ~/.bash_profile under Git Bash).
     * 
     * @param file       Git Bash profile file path (may contain variable substitutions)
     * @param exportLine Export line to add (may contain variable substitutions)
     * @return this builder instance
     */
    public UninstallManifestBuilder addGitBashProfileEntry(String file, String exportLine) {
        return addGitBashProfileEntry(file, exportLine, null);
    }
    
    /**
     * Add a Git Bash profile entry (e.g., for ~/.bash_profile under Git Bash).
     * 
     * @param file        Git Bash profile file path (may contain variable substitutions)
     * @param exportLine   Export line to add (may contain variable substitutions)
     * @param description Optional description
     * @return this builder instance
     */
    public UninstallManifestBuilder addGitBashProfileEntry(String file, String exportLine, String description) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(exportLine, "exportLine cannot be null");
        
        String substitutedFile = substituteVariables(file);
        String substitutedExportLine = substituteVariables(exportLine);
        
        UninstallManifest.GitBashProfileEntry entry = UninstallManifest.GitBashProfileEntry.builder()
            .file(substitutedFile)
            .exportLine(substitutedExportLine)
            .description(description)
            .build();
        gitBashProfileEntries.add(entry);
        return this;
    }

    /**
     * Add an MCP server entry installed to an AI tool.
     *
     * @param configFile Path to the AI tool's config file that was modified
     * @param entryKey   The key/name of the MCP server entry
     * @param toolName   Name of the AI tool (e.g., "CLAUDE_DESKTOP")
     * @return this builder instance
     */
    public UninstallManifestBuilder addMcpServerEntry(String configFile, String entryKey, String toolName) {
        Objects.requireNonNull(configFile, "configFile cannot be null");
        Objects.requireNonNull(entryKey, "entryKey cannot be null");
        Objects.requireNonNull(toolName, "toolName cannot be null");

        String substitutedPath = substituteVariables(configFile);
        UninstallManifest.McpServerEntry entry = UninstallManifest.McpServerEntry.builder()
            .configFile(substitutedPath)
            .entryKey(entryKey)
            .toolName(toolName)
            .build();
        mcpServerEntries.add(entry);
        return this;
    }

    /**
     * Add a skill entry installed to an AI tool.
     *
     * @param path Path to the installed skill directory
     * @param name The skill name
     * @return this builder instance
     */
    public UninstallManifestBuilder addSkillEntry(String path, String name) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        String substitutedPath = substituteVariables(path);
        UninstallManifest.SkillEntry entry = UninstallManifest.SkillEntry.builder()
            .path(substitutedPath)
            .name(name)
            .build();
        skillEntries.add(entry);
        return this;
    }

    /**
     * Add an agent entry installed to an AI tool.
     *
     * @param path Path to the installed agent directory
     * @param name The agent name
     * @return this builder instance
     */
    public UninstallManifestBuilder addAgentEntry(String path, String name) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        String substitutedPath = substituteVariables(path);
        UninstallManifest.AgentEntry entry = UninstallManifest.AgentEntry.builder()
            .path(substitutedPath)
            .name(name)
            .build();
        agentEntries.add(entry);
        return this;
    }

    /**
     * Build the UninstallManifest instance.
     * Validates that required fields have been set.
     * 
     * @return immutable UninstallManifest instance
     * @throws IllegalStateException if required fields are missing
     */
    public UninstallManifest build() {
        validateRequiredFields();
        
        UninstallManifest.PackageInfo packageInfo = UninstallManifest.PackageInfo.builder()
            .name(packageName)
            .source(packageSource)
            .version(packageVersion)
            .fullyQualifiedName(fullyQualifiedName)
            .architecture(architecture)
            .installedAt(installedAt)
            .installerVersion(installerVersion != null ? installerVersion : "1.0")
            .build();
        
        UninstallManifest.RegistryInfo registryInfo = null;
        if (!registryKeys.isEmpty() || !modifiedRegistryValues.isEmpty()) {
            registryInfo = UninstallManifest.RegistryInfo.builder()
                .createdKeys(registryKeys)
                .modifiedValues(modifiedRegistryValues)
                .build();
        }
        
        UninstallManifest.PathModifications pathModifications = null;
        if (!windowsPathEntries.isEmpty() || !shellProfileEntries.isEmpty() || !gitBashProfileEntries.isEmpty()) {
            pathModifications = UninstallManifest.PathModifications.builder()
                .windowsPaths(windowsPathEntries)
                .shellProfiles(shellProfileEntries)
                .gitBashProfiles(gitBashProfileEntries)
                .build();
        }

        UninstallManifest.AiIntegrations aiIntegrations = null;
        if (!mcpServerEntries.isEmpty() || !skillEntries.isEmpty() || !agentEntries.isEmpty()) {
            aiIntegrations = UninstallManifest.AiIntegrations.builder()
                .mcpServers(mcpServerEntries)
                .skills(skillEntries)
                .agents(agentEntries)
                .build();
        }

        return UninstallManifest.builder()
            .version(MANIFEST_VERSION)
            .packageInfo(packageInfo)
            .files(files)
            .directories(directories)
            .registry(registryInfo)
            .pathModifications(pathModifications)
            .aiIntegrations(aiIntegrations)
            .build();
    }
    
    /**
     * Substitute all known path variables in the given string.
     * Variables are replaced in the format ${VARIABLE_NAME}.
     * 
     * @param input String potentially containing variable references
     * @return String with all variables substituted
     */
    private String substituteVariables(String input) {
        if (input == null) {
            return null;
        }
        
        String result = input;
        for (Map.Entry<String, String> entry : variableSubstitutions.entrySet()) {
            String variable = "${" + entry.getKey() + "}";
            result = result.replace(variable, entry.getValue());
        }
        return result;
    }
    
    /**
     * Validate that all required fields have been set.
     * 
     * @throws IllegalStateException if required fields are missing
     */
    private void validateRequiredFields() {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalStateException("Package name is required; call withPackageInfo() first");
        }
        if (packageVersion == null || packageVersion.isEmpty()) {
            throw new IllegalStateException("Package version is required; call withPackageInfo() first");
        }
        if (architecture == null || architecture.isEmpty()) {
            throw new IllegalStateException("Architecture is required; call withPackageInfo() first");
        }
        if (fullyQualifiedName == null || fullyQualifiedName.isEmpty()) {
            throw new IllegalStateException("Fully qualified name is required; call withPackageInfo() first");
        }
    }
}
