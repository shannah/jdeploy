package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;
import ca.weblite.jdeploy.installer.ai.services.AiToolConfigWriter;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Abstract base class for JSON-based AI tool configuration writers.
 * Most AI tools use JSON configuration with an "mcpServers" object.
 */
public abstract class AbstractJsonConfigWriter implements AiToolConfigWriter {

    protected final AIToolType toolType;
    protected final AiToolConfigLocator configLocator;

    protected AbstractJsonConfigWriter(AIToolType toolType) {
        this.toolType = toolType;
        this.configLocator = new AiToolConfigLocator();
    }

    protected AbstractJsonConfigWriter(AIToolType toolType, AiToolConfigLocator configLocator) {
        this.toolType = toolType;
        this.configLocator = configLocator;
    }

    @Override
    public AIToolType getToolType() {
        return toolType;
    }

    @Override
    public File getConfigPath() {
        return configLocator.getConfigPath(toolType);
    }

    /**
     * Gets the key under which MCP servers are stored.
     * Default is "mcpServers" but some tools use different keys.
     */
    protected String getMcpServersKey() {
        return "mcpServers";
    }

    @Override
    @Deprecated
    public void addMcpServer(String serverName, String command, List<String> args, String comment) throws IOException {
        addMcpServer(serverName, command, args, null, null);
    }

    @Override
    public void addMcpServer(String serverName, String command, List<String> args, String packageFqn, String appDisplayName) throws IOException {
        File configFile = getConfigPath();
        if (configFile == null) {
            throw new IOException("Config path not available for " + toolType);
        }

        JSONObject config = loadOrCreateConfig(configFile);
        JSONObject mcpServers = config.optJSONObject(getMcpServersKey());
        if (mcpServers == null) {
            mcpServers = new JSONObject();
            config.put(getMcpServersKey(), mcpServers);
        }

        JSONObject serverEntry = createServerEntry(command, args, packageFqn, appDisplayName);
        mcpServers.put(serverName, serverEntry);

        saveConfig(configFile, config);
    }

    @Override
    public void removeMcpServer(String serverName) throws IOException {
        File configFile = getConfigPath();
        if (configFile == null || !configFile.exists()) {
            return; // Nothing to remove
        }

        JSONObject config = loadConfig(configFile);
        if (config == null) {
            return;
        }

        JSONObject mcpServers = config.optJSONObject(getMcpServersKey());
        if (mcpServers != null) {
            mcpServers.remove(serverName);
            saveConfig(configFile, config);
        }
    }

    @Override
    public boolean serverExists(String serverName) throws IOException {
        File configFile = getConfigPath();
        if (configFile == null || !configFile.exists()) {
            return false;
        }

        JSONObject config = loadConfig(configFile);
        if (config == null) {
            return false;
        }

        JSONObject mcpServers = config.optJSONObject(getMcpServersKey());
        return mcpServers != null && mcpServers.has(serverName);
    }

    @Override
    public boolean isOurServer(String serverName, String packageFqn) throws IOException {
        File configFile = getConfigPath();
        if (configFile == null || !configFile.exists()) {
            return false;
        }

        JSONObject config = loadConfig(configFile);
        if (config == null) {
            return false;
        }

        JSONObject mcpServers = config.optJSONObject(getMcpServersKey());
        if (mcpServers == null || !mcpServers.has(serverName)) {
            return false;
        }

        JSONObject serverEntry = mcpServers.optJSONObject(serverName);
        if (serverEntry == null) {
            return false;
        }

        JSONObject jdeployMeta = serverEntry.optJSONObject("_jdeploy");
        if (jdeployMeta == null) {
            return false;
        }

        String existingFqn = jdeployMeta.optString("fqn", null);
        return packageFqn != null && packageFqn.equals(existingFqn);
    }

    @Override
    public void backupConfig() throws IOException {
        File configFile = getConfigPath();
        if (configFile == null || !configFile.exists()) {
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File backupFile = new File(configFile.getParent(), configFile.getName() + ".backup_" + timestamp);
        FileUtils.copyFile(configFile, backupFile);
    }

    @Override
    public String getClipboardConfig(String serverName, String command, List<String> args) {
        JSONObject serverEntry = createServerEntry(command, args, null, null);
        JSONObject wrapper = new JSONObject();
        JSONObject mcpServers = new JSONObject();
        mcpServers.put(serverName, serverEntry);
        wrapper.put(getMcpServersKey(), mcpServers);
        return wrapper.toString(2);
    }

    /**
     * Creates the server entry JSON object.
     * Subclasses can override for custom formats.
     *
     * @param command the command to execute
     * @param args arguments to pass to the command
     * @param packageFqn the fully-qualified package name for jDeploy identification (optional)
     * @param appDisplayName the application display name (optional)
     */
    protected JSONObject createServerEntry(String command, List<String> args, String packageFqn, String appDisplayName) {
        JSONObject entry = new JSONObject();

        entry.put("command", command);

        if (args != null && !args.isEmpty()) {
            entry.put("args", new JSONArray(args));
        }

        // Add jDeploy metadata for identification during uninstall/update
        if (packageFqn != null || appDisplayName != null) {
            JSONObject jdeployMeta = new JSONObject();
            if (packageFqn != null) {
                jdeployMeta.put("fqn", packageFqn);
            }
            if (appDisplayName != null) {
                jdeployMeta.put("appName", appDisplayName);
            }
            entry.put("_jdeploy", jdeployMeta);
        }

        return entry;
    }

    /**
     * Loads the configuration file or creates an empty JSON object.
     */
    protected JSONObject loadOrCreateConfig(File configFile) throws IOException {
        if (configFile.exists()) {
            JSONObject config = loadConfig(configFile);
            return config != null ? config : new JSONObject();
        }
        return new JSONObject();
    }

    /**
     * Loads the configuration file.
     */
    protected JSONObject loadConfig(File configFile) throws IOException {
        if (!configFile.exists()) {
            return null;
        }
        String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        if (content.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(content);
        } catch (Exception e) {
            // If JSON is invalid, return null and let caller handle it
            return null;
        }
    }

    /**
     * Saves the configuration to file, creating parent directories if needed.
     */
    protected void saveConfig(File configFile, JSONObject config) throws IOException {
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        String content = config.toString(2);
        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
