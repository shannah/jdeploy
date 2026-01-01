package ca.weblite.jdeploy.installer.cli;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Model class representing a manifest of installed CLI commands for a package.
 * 
 * This manifest records metadata about CLI command installations, including:
 * - Which package the commands belong to
 * - The source (GitHub URL or null for NPM packages)
 * - The binary directory where command wrappers are installed
 * - The list of command names that were installed
 * - Whether the user's PATH was modified
 * - The timestamp of the installation
 */
public class CliCommandManifest {
    private final String packageName;
    private final String source;
    private final File binDir;
    private final List<String> commandNames;
    private final boolean pathUpdated;
    private final long timestamp;

    /**
     * Constructs a new CliCommandManifest.
     *
     * @param packageName the name of the package (e.g., "my-app")
     * @param source the GitHub source URL (null for NPM packages)
     * @param binDir the directory where command wrappers are installed
     * @param commandNames list of command names that were installed
     * @param pathUpdated whether the user's PATH was modified during installation
     * @param timestamp the timestamp of the installation (in milliseconds)
     */
    public CliCommandManifest(String packageName, String source, File binDir, 
                              List<String> commandNames, boolean pathUpdated, long timestamp) {
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
        this.source = source;
        this.binDir = Objects.requireNonNull(binDir, "binDir cannot be null");
        this.commandNames = Objects.requireNonNull(commandNames, "commandNames cannot be null");
        this.pathUpdated = pathUpdated;
        this.timestamp = timestamp;
    }

    /**
     * Gets the package name.
     *
     * @return the package name
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Gets the GitHub source URL (null for NPM packages).
     *
     * @return the source URL or null
     */
    public String getSource() {
        return source;
    }

    /**
     * Gets the binary directory where command wrappers are installed.
     *
     * @return the bin directory
     */
    public File getBinDir() {
        return binDir;
    }

    /**
     * Gets the list of installed command names.
     *
     * @return list of command names
     */
    public List<String> getCommandNames() {
        return commandNames;
    }

    /**
     * Checks if the user's PATH was modified during installation.
     *
     * @return true if PATH was modified, false otherwise
     */
    public boolean isPathUpdated() {
        return pathUpdated;
    }

    /**
     * Gets the installation timestamp.
     *
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CliCommandManifest that = (CliCommandManifest) o;
        return pathUpdated == that.pathUpdated &&
               timestamp == that.timestamp &&
               packageName.equals(that.packageName) &&
               Objects.equals(source, that.source) &&
               binDir.equals(that.binDir) &&
               commandNames.equals(that.commandNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, source, binDir, commandNames, pathUpdated, timestamp);
    }

    @Override
    public String toString() {
        return "CliCommandManifest{" +
               "packageName='" + packageName + '\'' +
               ", source='" + source + '\'' +
               ", binDir=" + binDir +
               ", commandNames=" + commandNames +
               ", pathUpdated=" + pathUpdated +
               ", timestamp=" + timestamp +
               '}';
    }
}
