package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.models.CommandSpec;

import java.util.Objects;

/**
 * Immutable descriptor for an installed service.
 *
 * Service descriptors track CLI commands that implement service_controller,
 * allowing the installer to manage service lifecycle during updates and uninstalls.
 *
 * @author Steve Hannah
 */
public class ServiceDescriptor {
    private final CommandSpec commandSpec;
    private final String packageName;
    private final String version;
    private final String source; // nullable - GitHub URL for GitHub packages, null for NPM packages
    private final String branchName; // nullable for non-branch installations
    private final long installedTimestamp;
    private final long lastModified;

    /**
     * Creates a new service descriptor.
     *
     * @param commandSpec The command specification (must implement service_controller)
     * @param packageName The package name
     * @param version The package version
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @param installedTimestamp The timestamp when the service was installed
     * @param lastModified The timestamp when the descriptor was last modified
     * @throws IllegalArgumentException if commandSpec is null or doesn't implement service_controller
     */
    public ServiceDescriptor(
            CommandSpec commandSpec,
            String packageName,
            String version,
            String source,
            String branchName,
            long installedTimestamp,
            long lastModified) {

        if (commandSpec == null) {
            throw new IllegalArgumentException("commandSpec cannot be null");
        }
        if (!commandSpec.implements_("service_controller")) {
            throw new IllegalArgumentException(
                "Command '" + commandSpec.getName() + "' does not implement service_controller");
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("version cannot be null or empty");
        }

        this.commandSpec = commandSpec;
        this.packageName = packageName;
        this.version = version;
        this.source = source;
        this.branchName = branchName;
        this.installedTimestamp = installedTimestamp;
        this.lastModified = lastModified;
    }

    /**
     * Creates a new service descriptor with current timestamps.
     *
     * @param commandSpec The command specification
     * @param packageName The package name
     * @param version The package version
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     */
    public ServiceDescriptor(
            CommandSpec commandSpec,
            String packageName,
            String version,
            String source,
            String branchName) {
        this(commandSpec, packageName, version, source, branchName,
             System.currentTimeMillis(), System.currentTimeMillis());
    }

    /**
     * Creates a new service descriptor with current timestamps (NPM package, no source).
     *
     * @param commandSpec The command specification
     * @param packageName The package name
     * @param version The package version
     * @param branchName The branch name, or null for non-branch installations
     * @deprecated Use the constructor with source parameter instead
     */
    public ServiceDescriptor(
            CommandSpec commandSpec,
            String packageName,
            String version,
            String branchName) {
        this(commandSpec, packageName, version, null, branchName,
             System.currentTimeMillis(), System.currentTimeMillis());
    }

    public CommandSpec getCommandSpec() {
        return commandSpec;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersion() {
        return version;
    }

    /**
     * @return The source URL (GitHub URL for GitHub packages, null for NPM packages)
     */
    public String getSource() {
        return source;
    }

    /**
     * @return The branch name, or null if this is not a branch installation
     */
    public String getBranchName() {
        return branchName;
    }

    /**
     * @return true if this is a branch installation
     */
    public boolean isBranchInstallation() {
        return branchName != null && !branchName.trim().isEmpty();
    }

    public long getInstalledTimestamp() {
        return installedTimestamp;
    }

    public long getLastModified() {
        return lastModified;
    }

    /**
     * @return The command name from the command spec
     */
    public String getCommandName() {
        return commandSpec.getName();
    }

    /**
     * Creates a new descriptor with an updated lastModified timestamp.
     *
     * @return A new ServiceDescriptor with the current time as lastModified
     */
    public ServiceDescriptor withUpdatedTimestamp() {
        return new ServiceDescriptor(
            commandSpec,
            packageName,
            version,
            source,
            branchName,
            installedTimestamp,
            System.currentTimeMillis()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceDescriptor that = (ServiceDescriptor) o;
        return installedTimestamp == that.installedTimestamp &&
               lastModified == that.lastModified &&
               Objects.equals(commandSpec, that.commandSpec) &&
               Objects.equals(packageName, that.packageName) &&
               Objects.equals(version, that.version) &&
               Objects.equals(source, that.source) &&
               Objects.equals(branchName, that.branchName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandSpec, packageName, version, source, branchName,
                          installedTimestamp, lastModified);
    }

    @Override
    public String toString() {
        return "ServiceDescriptor{" +
               "commandName='" + getCommandName() + '\'' +
               ", packageName='" + packageName + '\'' +
               ", version='" + version + '\'' +
               ", source='" + source + '\'' +
               ", branchName='" + branchName + '\'' +
               ", installedTimestamp=" + installedTimestamp +
               ", lastModified=" + lastModified +
               '}';
    }
}
