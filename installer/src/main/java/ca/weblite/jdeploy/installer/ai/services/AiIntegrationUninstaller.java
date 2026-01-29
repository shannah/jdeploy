package ca.weblite.jdeploy.installer.ai.services;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service for uninstalling AI integrations.
 *
 * Handles removal of:
 * - MCP server entries from AI tool configuration files
 * - Skill directories
 * - Agent directories
 * - AI integration state files
 */
public class AiIntegrationUninstaller {

    private static final Logger LOGGER = Logger.getLogger(AiIntegrationUninstaller.class.getName());

    private final AiToolConfigLocator configLocator;
    private final AiToolConfigWriterFactory configWriterFactory;

    public AiIntegrationUninstaller() {
        this.configLocator = new AiToolConfigLocator();
        this.configWriterFactory = new AiToolConfigWriterFactory();
    }

    /**
     * Uninstalls AI integrations based on the manifest.
     *
     * @param aiIntegrations The AI integrations section from the uninstall manifest
     * @param packageName The package name
     * @param source The package source
     * @return Result containing success/failure information
     */
    public UninstallResult uninstall(
            UninstallManifest.AiIntegrations aiIntegrations,
            String packageName,
            String source) {

        UninstallResult result = new UninstallResult();

        if (aiIntegrations == null) {
            LOGGER.fine("No AI integrations to uninstall");
            return result;
        }

        // Phase 1: Remove MCP server entries
        removeMcpServers(aiIntegrations.getMcpServers(), result);

        // Phase 2: Delete skills
        deleteSkills(aiIntegrations.getSkills(), result);

        // Phase 3: Delete agents
        deleteAgents(aiIntegrations.getAgents(), result);

        // Phase 4: Clean up state directory
        cleanupStateDirectory(packageName, source, result);

        return result;
    }

    /**
     * Removes MCP server entries from AI tool configuration files.
     */
    private void removeMcpServers(List<UninstallManifest.McpServerEntry> mcpServers, UninstallResult result) {
        if (mcpServers == null || mcpServers.isEmpty()) {
            return;
        }

        for (UninstallManifest.McpServerEntry entry : mcpServers) {
            try {
                File configFile = new File(entry.getConfigFile());
                if (!configFile.exists()) {
                    LOGGER.fine("Config file does not exist, skipping: " + entry.getConfigFile());
                    continue;
                }

                // Try to get the tool type from the entry
                AIToolType toolType = parseToolType(entry.getToolName());
                if (toolType == null) {
                    LOGGER.warning("Unknown tool type: " + entry.getToolName());
                    result.addError("Unknown tool type: " + entry.getToolName());
                    result.incrementFailureCount();
                    continue;
                }

                // Get the config writer for this tool
                AiToolConfigWriter writer = configWriterFactory.getWriter(toolType);
                if (writer == null) {
                    LOGGER.warning("No config writer available for tool: " + toolType);
                    result.addError("No config writer available for tool: " + toolType);
                    result.incrementFailureCount();
                    continue;
                }

                // Remove the MCP server entry
                writer.removeMcpServer(entry.getEntryKey());
                result.incrementSuccessCount();
                LOGGER.info("Removed MCP server entry: " + entry.getEntryKey() + " from " + toolType);

            } catch (Exception e) {
                String errorMsg = "Failed to remove MCP server entry: " + entry.getEntryKey() +
                                " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Deletes skill directories.
     */
    private void deleteSkills(List<UninstallManifest.SkillEntry> skills, UninstallResult result) {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        for (UninstallManifest.SkillEntry entry : skills) {
            try {
                File skillDir = new File(entry.getPath());
                if (skillDir.exists()) {
                    FileUtils.deleteDirectory(skillDir);
                    result.incrementSuccessCount();
                    LOGGER.info("Deleted skill directory: " + entry.getPath());
                } else {
                    LOGGER.fine("Skill directory does not exist, skipping: " + entry.getPath());
                }
            } catch (IOException e) {
                String errorMsg = "Failed to delete skill directory: " + entry.getPath() +
                                " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Deletes agent directories.
     */
    private void deleteAgents(List<UninstallManifest.AgentEntry> agents, UninstallResult result) {
        if (agents == null || agents.isEmpty()) {
            return;
        }

        for (UninstallManifest.AgentEntry entry : agents) {
            try {
                File agentDir = new File(entry.getPath());
                if (agentDir.exists()) {
                    FileUtils.deleteDirectory(agentDir);
                    result.incrementSuccessCount();
                    LOGGER.info("Deleted agent directory: " + entry.getPath());
                } else {
                    LOGGER.fine("Agent directory does not exist, skipping: " + entry.getPath());
                }
            } catch (IOException e) {
                String errorMsg = "Failed to delete agent directory: " + entry.getPath() +
                                " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Cleans up the AI integration state directory.
     */
    private void cleanupStateDirectory(String packageName, String source, UninstallResult result) {
        try {
            String fqpn = computeFullyQualifiedPackageName(packageName, source);
            String architecture = ArchitectureUtil.getArchitecture();

            AiIntegrationStateService stateService = new AiIntegrationStateService(fqpn, architecture);
            stateService.deleteState();

            LOGGER.info("Cleaned up AI integration state directory");
        } catch (Exception e) {
            String errorMsg = "Failed to clean up AI integration state: " + e.getMessage();
            LOGGER.warning(errorMsg);
            result.addError(errorMsg);
            result.incrementFailureCount();
        }
    }

    /**
     * Parses a tool name string to an AIToolType.
     */
    private AIToolType parseToolType(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return null;
        }
        try {
            return AIToolType.valueOf(toolName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Computes the fully qualified package name.
     */
    private String computeFullyQualifiedPackageName(String packageName, String source) {
        String sanitized = sanitizeName(packageName);
        if (source != null && !source.isEmpty()) {
            String sourceHash = ca.weblite.tools.io.MD5.getMd5(source);
            return sourceHash + "." + sanitized;
        }
        return sanitized;
    }

    /**
     * Sanitizes a name to make it filesystem-safe.
     */
    private String sanitizeName(String name) {
        return name.toLowerCase()
                .replace(" ", "-")
                .replace("@", "")
                .replace("/", "-")
                .replace("\\", "-")
                .replace(":", "-")
                .replace("*", "-")
                .replace("?", "-")
                .replace("\"", "-")
                .replace("<", "-")
                .replace(">", "-")
                .replace("|", "-");
    }

    /**
     * Result object tracking uninstall operation success/failure counts and errors.
     */
    public static final class UninstallResult {
        private int successCount = 0;
        private int failureCount = 0;
        private final List<String> errors = new ArrayList<>();

        public void incrementSuccessCount() {
            this.successCount++;
        }

        public void incrementFailureCount() {
            this.failureCount++;
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public boolean isSuccess() {
            return failureCount == 0;
        }

        @Override
        public String toString() {
            return "UninstallResult{" +
                   "successCount=" + successCount +
                   ", failureCount=" + failureCount +
                   ", errors=" + errors +
                   '}';
        }
    }
}
