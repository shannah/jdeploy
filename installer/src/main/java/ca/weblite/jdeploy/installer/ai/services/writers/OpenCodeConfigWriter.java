package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration writer for OpenCode.
 *
 * OpenCode uses a different structure:
 * - Key is "mcp" (not "mcpServers")
 * - Uses "type": "local"
 * - Command and args are combined into a single "command" array
 * - Has "enabled": true field
 *
 * Config location: ~/.config/opencode/opencode.json
 *
 * Example:
 * {
 *   "mcp": {
 *     "a1b2c3d4.myapp": {
 *       "type": "local",
 *       "command": ["myapp", "--jdeploy:command=mcp-server", "--verbose"],
 *       "enabled": true
 *     }
 *   }
 * }
 */
public class OpenCodeConfigWriter extends AbstractJsonConfigWriter {

    public OpenCodeConfigWriter() {
        super(AIToolType.OPENCODE);
    }

    public OpenCodeConfigWriter(AiToolConfigLocator configLocator) {
        super(AIToolType.OPENCODE, configLocator);
    }

    @Override
    protected String getMcpServersKey() {
        return "mcp";
    }

    @Override
    protected JSONObject createServerEntry(String command, List<String> args, String packageFqn, String appDisplayName) {
        JSONObject entry = new JSONObject();

        entry.put("type", "local");

        // Combine command and args into a single array
        List<String> commandArray = new ArrayList<>();
        commandArray.add(command);
        if (args != null) {
            commandArray.addAll(args);
        }
        entry.put("command", new JSONArray(commandArray));

        entry.put("enabled", true);

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

    @Override
    public String getClipboardConfig(String serverName, String command, List<String> args) {
        JSONObject serverEntry = createServerEntry(command, args, null, null);
        JSONObject wrapper = new JSONObject();
        JSONObject mcp = new JSONObject();
        mcp.put(serverName, serverEntry);
        wrapper.put("mcp", mcp);
        return wrapper.toString(2);
    }
}
