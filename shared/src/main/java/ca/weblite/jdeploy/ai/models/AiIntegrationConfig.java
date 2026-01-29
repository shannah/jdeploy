package ca.weblite.jdeploy.ai.models;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Complete AI integration configuration for a jDeploy project.
 *
 * This includes:
 * - MCP server configuration from package.json (jdeploy.ai.mcp)
 * - Skills from .jdeploy/skills/ directory
 * - Agents from .jdeploy/agents/ directory
 */
public class AiIntegrationConfig {
    private final McpServerConfig mcpConfig;
    private final List<String> skillNames;
    private final List<String> agentNames;
    private final boolean hasSkills;
    private final boolean hasAgents;

    private AiIntegrationConfig(McpServerConfig mcpConfig, List<String> skillNames, List<String> agentNames) {
        this.mcpConfig = mcpConfig;
        this.skillNames = Collections.unmodifiableList(new ArrayList<>(skillNames));
        this.agentNames = Collections.unmodifiableList(new ArrayList<>(agentNames));
        this.hasSkills = !skillNames.isEmpty();
        this.hasAgents = !agentNames.isEmpty();
    }

    /**
     * Gets the MCP server configuration, or null if not configured.
     */
    public McpServerConfig getMcpConfig() {
        return mcpConfig;
    }

    /**
     * Returns true if this project has MCP server configuration.
     */
    public boolean hasMcpConfig() {
        return mcpConfig != null && mcpConfig.isValid();
    }

    /**
     * Returns true if this project has skills in .jdeploy/skills/.
     */
    public boolean hasSkills() {
        return hasSkills;
    }

    /**
     * Returns true if this project has agents in .jdeploy/agents/.
     */
    public boolean hasAgents() {
        return hasAgents;
    }

    /**
     * Gets the list of skill directory names found in .jdeploy/skills/.
     */
    public List<String> getSkillNames() {
        return skillNames;
    }

    /**
     * Gets the list of agent directory names found in .jdeploy/agents/.
     */
    public List<String> getAgentNames() {
        return agentNames;
    }

    /**
     * Returns true if this project has any AI integrations configured
     * (MCP, skills, or agents).
     */
    public boolean hasAnyIntegrations() {
        return hasMcpConfig() || hasSkills || hasAgents;
    }

    /**
     * Returns true if the "Install AI Integrations" checkbox should be checked
     * by default in the installer.
     */
    public boolean isDefaultEnabled() {
        if (mcpConfig != null) {
            return mcpConfig.isDefaultEnabled();
        }
        return true; // Default to enabled if only skills/agents are present
    }

    /**
     * Parses AI integration configuration from a jdeploy-bundle directory.
     * This is used during installation when reading from the bundle.
     *
     * @param packageJson the parsed package.json object
     * @param bundleDir the jdeploy-bundle directory
     * @return the parsed configuration
     */
    public static AiIntegrationConfig fromBundle(JSONObject packageJson, File bundleDir) {
        McpServerConfig mcpConfig = null;
        List<String> skillNames = new ArrayList<>();
        List<String> agentNames = new ArrayList<>();

        // Parse MCP config from jdeploy.ai.mcp
        if (packageJson != null && packageJson.has("jdeploy")) {
            JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
            if (jdeploy.has("ai")) {
                JSONObject ai = jdeploy.getJSONObject("ai");
                if (ai.has("mcp")) {
                    mcpConfig = McpServerConfig.fromJson(ai.getJSONObject("mcp"));
                }
            }
        }

        // Scan for skills in jdeploy-bundle/ai/skills/
        if (bundleDir != null) {
            File skillsDir = new File(bundleDir, "ai/skills");
            if (skillsDir.isDirectory()) {
                File[] skillDirs = skillsDir.listFiles(File::isDirectory);
                if (skillDirs != null) {
                    for (File skillDir : skillDirs) {
                        skillNames.add(skillDir.getName());
                    }
                }
            }

            // Scan for agents in jdeploy-bundle/ai/agents/
            File agentsDir = new File(bundleDir, "ai/agents");
            if (agentsDir.isDirectory()) {
                File[] agentDirs = agentsDir.listFiles(File::isDirectory);
                if (agentDirs != null) {
                    for (File agentDir : agentDirs) {
                        agentNames.add(agentDir.getName());
                    }
                }
            }
        }

        return new AiIntegrationConfig(mcpConfig, skillNames, agentNames);
    }
}
