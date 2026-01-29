package ca.weblite.jdeploy.ai.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for an MCP (Model Context Protocol) server defined in a jDeploy project.
 *
 * This configuration is stored in package.json under jdeploy.ai.mcp:
 * <pre>
 * {
 *   "jdeploy": {
 *     "ai": {
 *       "mcp": {
 *         "command": "mcp-server",
 *         "args": ["--verbose"],
 *         "defaultEnabled": true
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * The "command" must reference a command defined in jdeploy.commands.
 */
public class McpServerConfig {
    private final String command;
    private final List<String> args;
    private final boolean defaultEnabled;

    private McpServerConfig(Builder builder) {
        this.command = builder.command;
        this.args = Collections.unmodifiableList(new ArrayList<>(builder.args));
        this.defaultEnabled = builder.defaultEnabled;
    }

    /**
     * Gets the command name that must correspond to a command defined in jdeploy.commands.
     * This is NOT a path to an executable - it's a logical command name that jDeploy
     * will invoke via --jdeploy:command=&lt;command-name&gt;.
     */
    public String getCommand() {
        return command;
    }

    /**
     * Gets additional command-line arguments to pass after the command invocation.
     */
    public List<String> getArgs() {
        return args;
    }

    /**
     * Returns whether the "Install AI Integrations" checkbox should be checked
     * by default in the installer.
     */
    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    /**
     * Parses MCP server configuration from a JSON object.
     *
     * @param mcpJson the jdeploy.ai.mcp JSON object
     * @return the parsed configuration, or null if mcpJson is null
     */
    public static McpServerConfig fromJson(JSONObject mcpJson) {
        if (mcpJson == null) {
            return null;
        }

        Builder builder = new Builder();

        if (mcpJson.has("command")) {
            builder.command(mcpJson.getString("command"));
        }

        if (mcpJson.has("args")) {
            JSONArray argsArray = mcpJson.getJSONArray("args");
            List<String> args = new ArrayList<>();
            for (int i = 0; i < argsArray.length(); i++) {
                args.add(argsArray.getString(i));
            }
            builder.args(args);
        }

        if (mcpJson.has("defaultEnabled")) {
            builder.defaultEnabled(mcpJson.getBoolean("defaultEnabled"));
        }

        return builder.build();
    }

    /**
     * Converts this configuration to a JSON object for storage in package.json.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        if (command != null && !command.isEmpty()) {
            json.put("command", command);
        }

        if (args != null && !args.isEmpty()) {
            json.put("args", new JSONArray(args));
        }

        // Only include defaultEnabled if it's false (true is the default)
        if (!defaultEnabled) {
            json.put("defaultEnabled", false);
        }

        return json;
    }

    /**
     * Returns true if this configuration has a valid command specified.
     */
    public boolean isValid() {
        return command != null && !command.trim().isEmpty();
    }

    public static class Builder {
        private String command;
        private List<String> args = new ArrayList<>();
        private boolean defaultEnabled = true;

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder args(List<String> args) {
            this.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
            return this;
        }

        public Builder addArg(String arg) {
            this.args.add(arg);
            return this;
        }

        public Builder defaultEnabled(boolean defaultEnabled) {
            this.defaultEnabled = defaultEnabled;
            return this;
        }

        public McpServerConfig build() {
            return new McpServerConfig(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
