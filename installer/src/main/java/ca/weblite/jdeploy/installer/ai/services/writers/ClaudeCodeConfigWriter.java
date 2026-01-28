package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Configuration writer for Claude Code.
 *
 * Claude Code uses:
 * - MCP config: ~/.claude.json
 * - Skills: ~/.claude/skills/
 * - Agents: ~/.claude/agents/
 */
public class ClaudeCodeConfigWriter extends AbstractJsonConfigWriter {

    public ClaudeCodeConfigWriter() {
        super(AIToolType.CLAUDE_CODE);
    }

    public ClaudeCodeConfigWriter(AiToolConfigLocator configLocator) {
        super(AIToolType.CLAUDE_CODE, configLocator);
    }

    /**
     * Installs a skill to Claude Code's skills directory.
     *
     * @param skillSourceDir the source skill directory
     * @param skillName the skill name (used in destination path)
     * @param packagePrefix the package prefix (e.g., "a1b2c3d4.myapp")
     * @throws IOException if an I/O error occurs
     */
    public void installSkill(File skillSourceDir, String skillName, String packagePrefix) throws IOException {
        File skillsDir = configLocator.getSkillsPath(AIToolType.CLAUDE_CODE);
        if (skillsDir == null) {
            throw new IOException("Skills path not available for Claude Code");
        }

        String destName = packagePrefix + "." + skillName;
        File destDir = new File(skillsDir, destName);
        destDir.mkdirs();

        FileUtils.copyDirectory(skillSourceDir, destDir);
    }

    /**
     * Uninstalls a skill from Claude Code.
     *
     * @param skillName the skill name
     * @param packagePrefix the package prefix
     * @throws IOException if an I/O error occurs
     */
    public void uninstallSkill(String skillName, String packagePrefix) throws IOException {
        File skillsDir = configLocator.getSkillsPath(AIToolType.CLAUDE_CODE);
        if (skillsDir == null) {
            return;
        }

        String destName = packagePrefix + "." + skillName;
        File skillDir = new File(skillsDir, destName);
        if (skillDir.exists()) {
            FileUtils.deleteDirectory(skillDir);
        }
    }

    /**
     * Installs an agent to Claude Code's agents directory.
     *
     * @param agentSourceDir the source agent directory
     * @param agentName the agent name (used in destination path)
     * @param packagePrefix the package prefix (e.g., "a1b2c3d4.myapp")
     * @throws IOException if an I/O error occurs
     */
    public void installAgent(File agentSourceDir, String agentName, String packagePrefix) throws IOException {
        File agentsDir = configLocator.getAgentsPath(AIToolType.CLAUDE_CODE);
        if (agentsDir == null) {
            throw new IOException("Agents path not available for Claude Code");
        }

        String destName = packagePrefix + "." + agentName;
        File destDir = new File(agentsDir, destName);
        destDir.mkdirs();

        FileUtils.copyDirectory(agentSourceDir, destDir);
    }

    /**
     * Uninstalls an agent from Claude Code.
     *
     * @param agentName the agent name
     * @param packagePrefix the package prefix
     * @throws IOException if an I/O error occurs
     */
    public void uninstallAgent(String agentName, String packagePrefix) throws IOException {
        File agentsDir = configLocator.getAgentsPath(AIToolType.CLAUDE_CODE);
        if (agentsDir == null) {
            return;
        }

        String destName = packagePrefix + "." + agentName;
        File agentDir = new File(agentsDir, destName);
        if (agentDir.exists()) {
            FileUtils.deleteDirectory(agentDir);
        }
    }

    /**
     * Gets the full path to an installed skill directory.
     */
    public File getSkillPath(String skillName, String packagePrefix) {
        File skillsDir = configLocator.getSkillsPath(AIToolType.CLAUDE_CODE);
        if (skillsDir == null) {
            return null;
        }
        return new File(skillsDir, packagePrefix + "." + skillName);
    }

    /**
     * Gets the full path to an installed agent directory.
     */
    public File getAgentPath(String agentName, String packagePrefix) {
        File agentsDir = configLocator.getAgentsPath(AIToolType.CLAUDE_CODE);
        if (agentsDir == null) {
            return null;
        }
        return new File(agentsDir, packagePrefix + "." + agentName);
    }
}
