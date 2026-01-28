package ca.weblite.jdeploy.installer.ai.services;

import ca.weblite.jdeploy.ai.models.AIToolType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for persisting and managing AI integration state.
 *
 * Stores state in ~/.jdeploy/ai-integrations/{arch}/{fqpn}/state.json
 * Allows toggling MCP server, skills, and agents on/off.
 */
public class AiIntegrationStateService {

    private static final Logger logger = Logger.getLogger(AiIntegrationStateService.class.getName());
    private static final String STATE_FILE_NAME = "state.json";

    private final String fullyQualifiedPackageName;
    private final String architecture;
    private final File stateDir;
    private final File stateFile;

    /**
     * Creates a new AiIntegrationStateService.
     *
     * @param fullyQualifiedPackageName The FQPN of the application
     * @param architecture The architecture (e.g., "x64", "aarch64")
     */
    public AiIntegrationStateService(String fullyQualifiedPackageName, String architecture) {
        this.fullyQualifiedPackageName = fullyQualifiedPackageName;
        this.architecture = architecture;

        String userHome = System.getProperty("user.home");
        this.stateDir = new File(userHome, ".jdeploy/ai-integrations/" + architecture + "/" + fullyQualifiedPackageName);
        this.stateFile = new File(stateDir, STATE_FILE_NAME);
    }

    /**
     * Loads the current AI integration state.
     *
     * @return The current state, or a default state if not found
     */
    public AiIntegrationState loadState() {
        if (!stateFile.exists()) {
            return createDefaultState();
        }

        try {
            String content = new String(Files.readAllBytes(stateFile.toPath()));
            JSONObject json = new JSONObject(content);
            return parseState(json);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load AI integration state", e);
            return createDefaultState();
        }
    }

    /**
     * Saves the AI integration state.
     *
     * @param state The state to save
     * @throws IOException If the state cannot be saved
     */
    public void saveState(AiIntegrationState state) throws IOException {
        // Ensure directory exists
        if (!stateDir.exists() && !stateDir.mkdirs()) {
            throw new IOException("Failed to create state directory: " + stateDir);
        }

        JSONObject json = serializeState(state);
        Files.write(stateFile.toPath(), json.toString(2).getBytes());
    }

    /**
     * Deletes the state file and directory.
     * Used during uninstallation.
     */
    public void deleteState() {
        if (stateFile.exists()) {
            stateFile.delete();
        }
        if (stateDir.exists() && stateDir.list().length == 0) {
            stateDir.delete();
        }
    }

    /**
     * Creates a default state with everything enabled.
     */
    private AiIntegrationState createDefaultState() {
        return new AiIntegrationState.Builder()
            .mcpEnabled(true)
            .skillsEnabled(true)
            .agentsEnabled(true)
            .installedTools(new HashSet<>())
            .build();
    }

    /**
     * Parses state from JSON.
     */
    private AiIntegrationState parseState(JSONObject json) {
        Set<AIToolType> installedTools = new HashSet<>();
        if (json.has("installedTools")) {
            JSONArray toolsArray = json.getJSONArray("installedTools");
            for (int i = 0; i < toolsArray.length(); i++) {
                try {
                    installedTools.add(AIToolType.valueOf(toolsArray.getString(i)));
                } catch (IllegalArgumentException e) {
                    // Ignore unknown tool types
                }
            }
        }

        return new AiIntegrationState.Builder()
            .mcpEnabled(json.optBoolean("mcpEnabled", true))
            .skillsEnabled(json.optBoolean("skillsEnabled", true))
            .agentsEnabled(json.optBoolean("agentsEnabled", true))
            .installedTools(installedTools)
            .lastModified(json.optLong("lastModified", System.currentTimeMillis()))
            .build();
    }

    /**
     * Serializes state to JSON.
     */
    private JSONObject serializeState(AiIntegrationState state) {
        JSONObject json = new JSONObject();
        json.put("mcpEnabled", state.isMcpEnabled());
        json.put("skillsEnabled", state.isSkillsEnabled());
        json.put("agentsEnabled", state.isAgentsEnabled());

        JSONArray toolsArray = new JSONArray();
        for (AIToolType tool : state.getInstalledTools()) {
            toolsArray.put(tool.name());
        }
        json.put("installedTools", toolsArray);
        json.put("lastModified", state.getLastModified());

        return json;
    }

    /**
     * Gets the state directory.
     */
    public File getStateDir() {
        return stateDir;
    }

    /**
     * Gets the state file.
     */
    public File getStateFile() {
        return stateFile;
    }

    /**
     * Immutable state object for AI integration settings.
     */
    public static class AiIntegrationState {
        private final boolean mcpEnabled;
        private final boolean skillsEnabled;
        private final boolean agentsEnabled;
        private final Set<AIToolType> installedTools;
        private final long lastModified;

        private AiIntegrationState(Builder builder) {
            this.mcpEnabled = builder.mcpEnabled;
            this.skillsEnabled = builder.skillsEnabled;
            this.agentsEnabled = builder.agentsEnabled;
            this.installedTools = new HashSet<>(builder.installedTools);
            this.lastModified = builder.lastModified;
        }

        public boolean isMcpEnabled() {
            return mcpEnabled;
        }

        public boolean isSkillsEnabled() {
            return skillsEnabled;
        }

        public boolean isAgentsEnabled() {
            return agentsEnabled;
        }

        public Set<AIToolType> getInstalledTools() {
            return new HashSet<>(installedTools);
        }

        public long getLastModified() {
            return lastModified;
        }

        /**
         * Creates a new Builder initialized with this state's values.
         */
        public Builder toBuilder() {
            return new Builder()
                .mcpEnabled(mcpEnabled)
                .skillsEnabled(skillsEnabled)
                .agentsEnabled(agentsEnabled)
                .installedTools(installedTools)
                .lastModified(lastModified);
        }

        public static class Builder {
            private boolean mcpEnabled = true;
            private boolean skillsEnabled = true;
            private boolean agentsEnabled = true;
            private Set<AIToolType> installedTools = new HashSet<>();
            private long lastModified = System.currentTimeMillis();

            public Builder mcpEnabled(boolean mcpEnabled) {
                this.mcpEnabled = mcpEnabled;
                return this;
            }

            public Builder skillsEnabled(boolean skillsEnabled) {
                this.skillsEnabled = skillsEnabled;
                return this;
            }

            public Builder agentsEnabled(boolean agentsEnabled) {
                this.agentsEnabled = agentsEnabled;
                return this;
            }

            public Builder installedTools(Set<AIToolType> installedTools) {
                this.installedTools = new HashSet<>(installedTools);
                return this;
            }

            public Builder addInstalledTool(AIToolType tool) {
                this.installedTools.add(tool);
                return this;
            }

            public Builder lastModified(long lastModified) {
                this.lastModified = lastModified;
                return this;
            }

            public AiIntegrationState build() {
                return new AiIntegrationState(this);
            }
        }
    }
}
