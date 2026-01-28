package ca.weblite.jdeploy.ai.services;

import ca.weblite.jdeploy.ai.models.AIToolType;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Service for detecting which AI tools are installed on the system.
 */
public class AiToolDetector {

    private final AiToolConfigLocator configLocator;

    public AiToolDetector() {
        this.configLocator = new AiToolConfigLocator();
    }

    public AiToolDetector(AiToolConfigLocator configLocator) {
        this.configLocator = configLocator;
    }

    /**
     * Returns true if the specified AI tool is detected on the system.
     *
     * @param toolType the AI tool type to check
     * @return true if the tool is detected
     */
    public boolean isToolInstalled(AIToolType toolType) {
        // Tools that require manual setup are always "available"
        if (toolType.requiresManualSetup()) {
            return true;
        }

        File detectionPath = configLocator.getDetectionPath(toolType);
        if (detectionPath == null) {
            return false;
        }

        return detectionPath.exists();
    }

    /**
     * Returns a list of all AI tools that are detected on the system.
     *
     * @return list of detected AI tools
     */
    public List<AIToolType> getInstalledTools() {
        List<AIToolType> installed = new ArrayList<>();

        for (AIToolType toolType : AIToolType.values()) {
            if (isToolInstalled(toolType)) {
                installed.add(toolType);
            }
        }

        return installed;
    }

    /**
     * Returns a set of all AI tools that are detected on the system
     * and support auto-installation (not requiring manual setup).
     *
     * @return set of auto-configurable AI tools
     */
    public Set<AIToolType> getAutoConfigurableTools() {
        Set<AIToolType> tools = EnumSet.noneOf(AIToolType.class);

        for (AIToolType toolType : AIToolType.values()) {
            if (toolType.supportsAutoInstall() && isToolInstalled(toolType)) {
                tools.add(toolType);
            }
        }

        return tools;
    }

    /**
     * Returns a set of all AI tools that require manual setup
     * and are always available as options.
     *
     * @return set of manual-setup AI tools
     */
    public Set<AIToolType> getManualSetupTools() {
        Set<AIToolType> tools = EnumSet.noneOf(AIToolType.class);

        for (AIToolType toolType : AIToolType.values()) {
            if (toolType.requiresManualSetup()) {
                tools.add(toolType);
            }
        }

        return tools;
    }

    /**
     * Returns true if at least one AI tool is detected on the system.
     *
     * @return true if any AI tool is available
     */
    public boolean hasAnyInstalledTools() {
        for (AIToolType toolType : AIToolType.values()) {
            if (isToolInstalled(toolType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if at least one auto-configurable AI tool is detected.
     * This is used to determine if the "Install AI Integrations" checkbox
     * should be shown in the installer.
     *
     * @return true if any auto-configurable AI tool is available
     */
    public boolean hasAutoConfigurableTools() {
        return !getAutoConfigurableTools().isEmpty();
    }

    /**
     * Returns a list of installed tools that support MCP servers.
     *
     * @return list of tools that support MCP
     */
    public List<AIToolType> getMcpCapableTools() {
        List<AIToolType> tools = new ArrayList<>();

        for (AIToolType toolType : getInstalledTools()) {
            if (toolType.supportsMcp()) {
                tools.add(toolType);
            }
        }

        return tools;
    }

    /**
     * Returns a list of installed tools that support skills.
     *
     * @return list of tools that support skills
     */
    public List<AIToolType> getSkillsCapableTools() {
        List<AIToolType> tools = new ArrayList<>();

        for (AIToolType toolType : getInstalledTools()) {
            if (toolType.supportsSkills()) {
                tools.add(toolType);
            }
        }

        return tools;
    }

    /**
     * Returns a list of installed tools that support agents.
     *
     * @return list of tools that support agents
     */
    public List<AIToolType> getAgentsCapableTools() {
        List<AIToolType> tools = new ArrayList<>();

        for (AIToolType toolType : getInstalledTools()) {
            if (toolType.supportsAgents()) {
                tools.add(toolType);
            }
        }

        return tools;
    }
}
