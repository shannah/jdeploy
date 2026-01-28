package ca.weblite.jdeploy.installer.ai.models;

import ca.weblite.jdeploy.ai.models.AIToolType;

/**
 * Represents an AI integration entry for the uninstall manifest.
 * This tracks what was installed so it can be removed during uninstallation.
 */
public class AiIntegrationManifestEntry {

    public enum EntryType {
        MCP_SERVER,
        SKILL,
        AGENT
    }

    private final EntryType type;
    private final AIToolType toolType;
    private final String configFilePath;
    private final String entryKey;
    private final String path;
    private final String name;

    private AiIntegrationManifestEntry(EntryType type, AIToolType toolType, String configFilePath,
                                        String entryKey, String path, String name) {
        this.type = type;
        this.toolType = toolType;
        this.configFilePath = configFilePath;
        this.entryKey = entryKey;
        this.path = path;
        this.name = name;
    }

    /**
     * Creates a manifest entry for an MCP server installation.
     *
     * @param toolType the AI tool type
     * @param configFilePath the path to the config file that was modified
     * @param entryKey the key/name of the MCP server entry
     */
    public static AiIntegrationManifestEntry forMcpServer(AIToolType toolType, String configFilePath, String entryKey) {
        return new AiIntegrationManifestEntry(EntryType.MCP_SERVER, toolType, configFilePath, entryKey, null, null);
    }

    /**
     * Creates a manifest entry for a skill installation.
     *
     * @param path the path to the installed skill directory
     * @param name the skill name
     */
    public static AiIntegrationManifestEntry forSkill(String path, String name) {
        return new AiIntegrationManifestEntry(EntryType.SKILL, null, null, null, path, name);
    }

    /**
     * Creates a manifest entry for an agent installation.
     *
     * @param path the path to the installed agent directory
     * @param name the agent name
     */
    public static AiIntegrationManifestEntry forAgent(String path, String name) {
        return new AiIntegrationManifestEntry(EntryType.AGENT, null, null, null, path, name);
    }

    public EntryType getType() {
        return type;
    }

    public AIToolType getToolType() {
        return toolType;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public String getEntryKey() {
        return entryKey;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public boolean isMcpServer() {
        return type == EntryType.MCP_SERVER;
    }

    public boolean isSkill() {
        return type == EntryType.SKILL;
    }

    public boolean isAgent() {
        return type == EntryType.AGENT;
    }

    @Override
    public String toString() {
        switch (type) {
            case MCP_SERVER:
                return "MCP Server[tool=" + toolType + ", key=" + entryKey + ", config=" + configFilePath + "]";
            case SKILL:
                return "Skill[name=" + name + ", path=" + path + "]";
            case AGENT:
                return "Agent[name=" + name + ", path=" + path + "]";
            default:
                return "AiIntegrationManifestEntry[type=" + type + "]";
        }
    }
}
