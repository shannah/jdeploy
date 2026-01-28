package ca.weblite.jdeploy.installer.ai.services;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.models.AiIntegrationConfig;
import ca.weblite.jdeploy.ai.models.McpServerConfig;
import ca.weblite.jdeploy.ai.services.AiToolDetector;
import ca.weblite.jdeploy.installer.ai.models.AiIntegrationManifestEntry;
import ca.weblite.jdeploy.installer.ai.services.writers.ClaudeCodeConfigWriter;
import ca.weblite.jdeploy.installer.ai.services.writers.CodexConfigWriter;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for installing AI integrations (MCP servers, skills, agents) to AI tools.
 */
public class AiIntegrationInstaller {

    private final AiToolConfigWriterFactory writerFactory;
    private final AiToolDetector toolDetector;
    private PrintStream out;

    public AiIntegrationInstaller() {
        this.writerFactory = new AiToolConfigWriterFactory();
        this.toolDetector = new AiToolDetector();
        this.out = System.out;
    }

    public AiIntegrationInstaller(AiToolConfigWriterFactory writerFactory, AiToolDetector toolDetector) {
        this.writerFactory = writerFactory;
        this.toolDetector = toolDetector;
        this.out = System.out;
    }

    public void setOutput(PrintStream out) {
        this.out = out;
    }

    /**
     * Installs AI integrations to the specified tools.
     *
     * @param config the AI integration configuration from the bundle
     * @param selectedTools the set of AI tools to install to
     * @param binaryCommand the command to execute the installed binary
     * @param mcpCommandName the MCP command name from jdeploy.commands
     * @param packagePrefix the package prefix (e.g., "a1b2c3d4.myapp")
     * @param appDisplayName the application display name for comments
     * @param bundleAiDir the bundle's ai/ directory containing skills and agents
     * @return list of manifest entries for uninstall tracking
     * @throws IOException if an I/O error occurs
     */
    public List<AiIntegrationManifestEntry> install(
            AiIntegrationConfig config,
            Set<AIToolType> selectedTools,
            String binaryCommand,
            String mcpCommandName,
            String packagePrefix,
            String appDisplayName,
            File bundleAiDir
    ) throws IOException {

        List<AiIntegrationManifestEntry> manifestEntries = new ArrayList<>();

        // Build args for MCP invocation
        List<String> mcpArgs = new ArrayList<>();
        mcpArgs.add("--jdeploy:command=" + mcpCommandName);
        if (config.getMcpConfig() != null && config.getMcpConfig().getArgs() != null) {
            mcpArgs.addAll(config.getMcpConfig().getArgs());
        }

        String comment = "MCP server for " + appDisplayName + " - installed by jDeploy";

        // Install MCP server to each selected tool
        for (AIToolType toolType : selectedTools) {
            if (!toolType.supportsMcp()) {
                continue;
            }

            AiToolConfigWriter writer = writerFactory.getWriter(toolType);
            if (writer == null) {
                log("Warning: No config writer for " + toolType);
                continue;
            }

            try {
                if (toolType.supportsAutoInstall()) {
                    // Backup existing config
                    writer.backupConfig();

                    // Add MCP server
                    writer.addMcpServer(packagePrefix, binaryCommand, mcpArgs, comment);

                    // Record in manifest
                    File configPath = writer.getConfigPath();
                    if (configPath != null) {
                        manifestEntries.add(AiIntegrationManifestEntry.forMcpServer(
                                toolType,
                                configPath.getAbsolutePath(),
                                packagePrefix
                        ));
                    }

                    log("Installed MCP server to " + toolType.getDisplayName());
                } else {
                    // Manual setup - clipboard config is available but not auto-installed
                    log("Note: " + toolType.getDisplayName() + " requires manual configuration.");
                }
            } catch (Exception e) {
                log("Warning: Failed to install MCP server to " + toolType.getDisplayName() + ": " + e.getMessage());
            }
        }

        // Install skills to tools that support them
        if (config.hasSkills() && bundleAiDir != null) {
            File skillsDir = new File(bundleAiDir, "skills");
            if (skillsDir.isDirectory()) {
                for (AIToolType toolType : selectedTools) {
                    if (toolType.supportsSkills()) {
                        try {
                            List<AiIntegrationManifestEntry> skillEntries = installSkillsToTool(
                                    toolType, skillsDir, packagePrefix
                            );
                            manifestEntries.addAll(skillEntries);
                        } catch (Exception e) {
                            log("Warning: Failed to install skills to " + toolType.getDisplayName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        // Install agents to tools that support them
        if (config.hasAgents() && bundleAiDir != null) {
            File agentsDir = new File(bundleAiDir, "agents");
            if (agentsDir.isDirectory()) {
                for (AIToolType toolType : selectedTools) {
                    if (toolType.supportsAgents()) {
                        try {
                            List<AiIntegrationManifestEntry> agentEntries = installAgentsToTool(
                                    toolType, agentsDir, packagePrefix
                            );
                            manifestEntries.addAll(agentEntries);
                        } catch (Exception e) {
                            log("Warning: Failed to install agents to " + toolType.getDisplayName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        return manifestEntries;
    }

    /**
     * Installs skills to a specific AI tool.
     */
    private List<AiIntegrationManifestEntry> installSkillsToTool(
            AIToolType toolType,
            File skillsSourceDir,
            String packagePrefix
    ) throws IOException {

        List<AiIntegrationManifestEntry> entries = new ArrayList<>();
        File[] skillDirs = skillsSourceDir.listFiles(File::isDirectory);

        if (skillDirs == null) {
            return entries;
        }

        for (File skillDir : skillDirs) {
            String skillName = skillDir.getName();

            if (toolType == AIToolType.CLAUDE_CODE) {
                ClaudeCodeConfigWriter writer = writerFactory.getClaudeCodeWriter();
                writer.installSkill(skillDir, skillName, packagePrefix);
                File installedPath = writer.getSkillPath(skillName, packagePrefix);
                if (installedPath != null) {
                    entries.add(AiIntegrationManifestEntry.forSkill(
                            installedPath.getAbsolutePath(),
                            skillName
                    ));
                }
                log("Installed skill '" + skillName + "' to Claude Code");

            } else if (toolType == AIToolType.CODEX_CLI) {
                CodexConfigWriter writer = writerFactory.getCodexWriter();
                writer.installSkill(skillDir, skillName, packagePrefix);
                // Note: Codex skills path would need to be tracked similarly
                log("Installed skill '" + skillName + "' to Codex CLI");
            }
        }

        return entries;
    }

    /**
     * Installs agents to a specific AI tool.
     */
    private List<AiIntegrationManifestEntry> installAgentsToTool(
            AIToolType toolType,
            File agentsSourceDir,
            String packagePrefix
    ) throws IOException {

        List<AiIntegrationManifestEntry> entries = new ArrayList<>();
        File[] agentDirs = agentsSourceDir.listFiles(File::isDirectory);

        if (agentDirs == null) {
            return entries;
        }

        for (File agentDir : agentDirs) {
            String agentName = agentDir.getName();

            if (toolType == AIToolType.CLAUDE_CODE) {
                ClaudeCodeConfigWriter writer = writerFactory.getClaudeCodeWriter();
                writer.installAgent(agentDir, agentName, packagePrefix);
                File installedPath = writer.getAgentPath(agentName, packagePrefix);
                if (installedPath != null) {
                    entries.add(AiIntegrationManifestEntry.forAgent(
                            installedPath.getAbsolutePath(),
                            agentName
                    ));
                }
                log("Installed agent '" + agentName + "' to Claude Code");
            }
        }

        return entries;
    }

    /**
     * Gets the clipboard configuration for tools that require manual setup.
     */
    public String getClipboardConfig(
            AIToolType toolType,
            String binaryCommand,
            String mcpCommandName,
            String packagePrefix,
            List<String> additionalArgs
    ) {
        AiToolConfigWriter writer = writerFactory.getWriter(toolType);
        if (writer == null) {
            return null;
        }

        List<String> mcpArgs = new ArrayList<>();
        mcpArgs.add("--jdeploy:command=" + mcpCommandName);
        if (additionalArgs != null) {
            mcpArgs.addAll(additionalArgs);
        }

        return writer.getClipboardConfig(packagePrefix, binaryCommand, mcpArgs);
    }

    private void log(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
