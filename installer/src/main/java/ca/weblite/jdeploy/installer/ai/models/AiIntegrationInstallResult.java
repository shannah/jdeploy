package ca.weblite.jdeploy.installer.ai.models;

import ca.weblite.jdeploy.ai.models.AIToolType;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of installing AI integrations.
 * Contains the manifest entries for uninstall tracking and any conflicts encountered.
 */
public class AiIntegrationInstallResult {

    private final List<AiIntegrationManifestEntry> manifestEntries;
    private final List<McpServerConflict> conflicts;

    public AiIntegrationInstallResult() {
        this.manifestEntries = new ArrayList<>();
        this.conflicts = new ArrayList<>();
    }

    public List<AiIntegrationManifestEntry> getManifestEntries() {
        return manifestEntries;
    }

    public List<McpServerConflict> getConflicts() {
        return conflicts;
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    public void addManifestEntry(AiIntegrationManifestEntry entry) {
        manifestEntries.add(entry);
    }

    public void addManifestEntries(List<AiIntegrationManifestEntry> entries) {
        manifestEntries.addAll(entries);
    }

    public void addConflict(McpServerConflict conflict) {
        conflicts.add(conflict);
    }

    /**
     * Represents a naming conflict with an existing MCP server.
     */
    public static class McpServerConflict {
        private final AIToolType toolType;
        private final String serverName;
        private final String message;

        public McpServerConflict(AIToolType toolType, String serverName, String message) {
            this.toolType = toolType;
            this.serverName = serverName;
            this.message = message;
        }

        public AIToolType getToolType() {
            return toolType;
        }

        public String getServerName() {
            return serverName;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return toolType.getDisplayName() + ": " + serverName + " - " + message;
        }
    }
}
