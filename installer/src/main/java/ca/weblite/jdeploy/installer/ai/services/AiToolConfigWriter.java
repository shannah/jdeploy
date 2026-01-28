package ca.weblite.jdeploy.installer.ai.services;

import ca.weblite.jdeploy.ai.models.AIToolType;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for writing MCP server configuration to AI tool config files.
 */
public interface AiToolConfigWriter {

    /**
     * Gets the AI tool type this writer handles.
     */
    AIToolType getToolType();

    /**
     * Adds an MCP server entry to the tool's configuration.
     *
     * @param serverName unique name for the MCP server (e.g., "weather")
     * @param command the command to execute
     * @param args arguments to pass to the command
     * @param comment optional comment to include in the config
     * @throws IOException if an I/O error occurs
     * @deprecated Use {@link #addMcpServer(String, String, List, String, String)} instead
     */
    @Deprecated
    void addMcpServer(String serverName, String command, List<String> args, String comment) throws IOException;

    /**
     * Adds an MCP server entry to the tool's configuration with jDeploy metadata.
     *
     * @param serverName user-friendly name for the MCP server (e.g., "weather")
     * @param command the command to execute
     * @param args arguments to pass to the command
     * @param packageFqn the fully-qualified package name for identification (e.g., "a1b2c3d4.myapp")
     * @param appDisplayName the application display name
     * @throws IOException if an I/O error occurs
     */
    void addMcpServer(String serverName, String command, List<String> args, String packageFqn, String appDisplayName) throws IOException;

    /**
     * Removes an MCP server entry from the tool's configuration.
     *
     * @param serverName the name of the MCP server to remove
     * @throws IOException if an I/O error occurs
     */
    void removeMcpServer(String serverName) throws IOException;

    /**
     * Checks if an MCP server entry exists in the tool's configuration.
     *
     * @param serverName the name of the MCP server to check
     * @return true if the server exists
     * @throws IOException if an I/O error occurs
     */
    boolean serverExists(String serverName) throws IOException;

    /**
     * Gets the configuration file path for this tool.
     *
     * @return the config file path, or null if not applicable
     */
    java.io.File getConfigPath();

    /**
     * Returns the JSON/TOML configuration string suitable for clipboard copying.
     * This is used for tools that require manual setup (Warp, JetBrains).
     *
     * @param serverName unique name for the MCP server
     * @param command the command to execute
     * @param args arguments to pass to the command
     * @return the configuration string
     */
    String getClipboardConfig(String serverName, String command, List<String> args);

    /**
     * Creates a backup of the configuration file before modification.
     *
     * @throws IOException if an I/O error occurs
     */
    void backupConfig() throws IOException;
}
