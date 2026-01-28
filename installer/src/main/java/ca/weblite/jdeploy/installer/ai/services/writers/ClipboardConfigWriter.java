package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;
import ca.weblite.jdeploy.installer.ai.services.AiToolConfigWriter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Configuration writer for tools that require manual setup via clipboard.
 * This includes Warp and JetBrains IDEs.
 *
 * This writer doesn't actually write to any file - it only generates
 * configuration strings that can be copied to the clipboard for the user
 * to paste manually.
 */
public class ClipboardConfigWriter implements AiToolConfigWriter {

    private final AIToolType toolType;
    private final AiToolConfigLocator configLocator;

    public ClipboardConfigWriter(AIToolType toolType) {
        this.toolType = toolType;
        this.configLocator = new AiToolConfigLocator();
    }

    public ClipboardConfigWriter(AIToolType toolType, AiToolConfigLocator configLocator) {
        this.toolType = toolType;
        this.configLocator = configLocator;
    }

    /**
     * Creates a ClipboardConfigWriter for Warp terminal.
     */
    public static ClipboardConfigWriter forWarp() {
        return new ClipboardConfigWriter(AIToolType.WARP);
    }

    /**
     * Creates a ClipboardConfigWriter for JetBrains IDEs.
     */
    public static ClipboardConfigWriter forJetBrains() {
        return new ClipboardConfigWriter(AIToolType.JETBRAINS);
    }

    @Override
    public AIToolType getToolType() {
        return toolType;
    }

    @Override
    public File getConfigPath() {
        // No file path - manual setup only
        return null;
    }

    @Override
    @Deprecated
    public void addMcpServer(String serverName, String command, List<String> args, String comment) throws IOException {
        throw new UnsupportedOperationException(
            toolType.getDisplayName() + " requires manual configuration. " +
            "Use getClipboardConfig() to get the configuration string."
        );
    }

    @Override
    public void addMcpServer(String serverName, String command, List<String> args, String packageFqn, String appDisplayName) throws IOException {
        throw new UnsupportedOperationException(
            toolType.getDisplayName() + " requires manual configuration. " +
            "Use getClipboardConfig() to get the configuration string."
        );
    }

    @Override
    public void removeMcpServer(String serverName) throws IOException {
        throw new UnsupportedOperationException(
            toolType.getDisplayName() + " requires manual configuration removal."
        );
    }

    @Override
    public boolean serverExists(String serverName) throws IOException {
        // Cannot check - manual setup only
        return false;
    }

    @Override
    public void backupConfig() throws IOException {
        // No-op - no file to backup
    }

    @Override
    public String getClipboardConfig(String serverName, String command, List<String> args) {
        if (toolType == AIToolType.WARP) {
            return getWarpConfig(serverName, command, args);
        } else if (toolType == AIToolType.JETBRAINS) {
            return getJetBrainsConfig(serverName, command, args);
        }
        return getGenericConfig(serverName, command, args);
    }

    /**
     * Generates Warp terminal configuration format.
     */
    private String getWarpConfig(String serverName, String command, List<String> args) {
        JSONObject serverEntry = new JSONObject();
        serverEntry.put("command", command);
        if (args != null && !args.isEmpty()) {
            serverEntry.put("args", new JSONArray(args));
        }
        serverEntry.put("start_on_launch", true);

        JSONObject wrapper = new JSONObject();
        wrapper.put(serverName, serverEntry);

        return wrapper.toString(2);
    }

    /**
     * Generates JetBrains IDE configuration format.
     */
    private String getJetBrainsConfig(String serverName, String command, List<String> args) {
        JSONObject serverEntry = new JSONObject();
        serverEntry.put("command", command);
        if (args != null && !args.isEmpty()) {
            serverEntry.put("args", new JSONArray(args));
        }

        JSONObject mcpServers = new JSONObject();
        mcpServers.put(serverName, serverEntry);

        JSONObject wrapper = new JSONObject();
        wrapper.put("mcpServers", mcpServers);

        return wrapper.toString(2);
    }

    /**
     * Generates a generic JSON configuration format.
     */
    private String getGenericConfig(String serverName, String command, List<String> args) {
        JSONObject serverEntry = new JSONObject();
        serverEntry.put("command", command);
        if (args != null && !args.isEmpty()) {
            serverEntry.put("args", new JSONArray(args));
        }

        JSONObject mcpServers = new JSONObject();
        mcpServers.put(serverName, serverEntry);

        JSONObject wrapper = new JSONObject();
        wrapper.put("mcpServers", mcpServers);

        return wrapper.toString(2);
    }
}
