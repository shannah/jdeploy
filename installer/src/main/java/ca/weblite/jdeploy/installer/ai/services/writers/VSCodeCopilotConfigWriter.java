package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Configuration writer for VS Code (GitHub Copilot).
 *
 * VS Code uses a slightly different structure for MCP configuration:
 * - The key is "servers" (not "mcpServers")
 * - Can be in .vscode/mcp.json (workspace) or settings.json (user)
 *
 * Config location: ~/.vscode/mcp.json (for global installation)
 */
public class VSCodeCopilotConfigWriter extends AbstractJsonConfigWriter {

    public VSCodeCopilotConfigWriter() {
        super(AIToolType.VSCODE_COPILOT);
    }

    public VSCodeCopilotConfigWriter(AiToolConfigLocator configLocator) {
        super(AIToolType.VSCODE_COPILOT, configLocator);
    }

    @Override
    protected String getMcpServersKey() {
        return "servers";
    }

    @Override
    public String getClipboardConfig(String serverName, String command, List<String> args) {
        JSONObject serverEntry = createServerEntry(command, args, null);
        JSONObject wrapper = new JSONObject();
        JSONObject servers = new JSONObject();
        servers.put(serverName, serverEntry);
        wrapper.put("servers", servers);
        return wrapper.toString(2);
    }
}
