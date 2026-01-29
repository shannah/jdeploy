package ca.weblite.jdeploy.installer.ai.tray;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.installer.ai.services.AiIntegrationInstaller;
import ca.weblite.jdeploy.installer.ai.services.AiIntegrationStateService;
import ca.weblite.jdeploy.installer.ai.services.AiIntegrationStateService.AiIntegrationState;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a submenu for AI integrations toggle in the system tray.
 *
 * Allows users to enable/disable MCP server registration with AI tools.
 * Skills and agents can also be toggled if supported.
 */
public class AiIntegrationsTraySubmenu {

    private static final Logger logger = Logger.getLogger(AiIntegrationsTraySubmenu.class.getName());

    private final String fullyQualifiedPackageName;
    private final String architecture;
    private final File installDir;
    private final AiIntegrationStateService stateService;
    private final AiIntegrationToggleHandler toggleHandler;

    private Menu submenu;
    private CheckboxMenuItem mcpCheckbox;
    private CheckboxMenuItem skillsCheckbox;
    private CheckboxMenuItem agentsCheckbox;

    // Flags to track if skills/agents are available
    private boolean hasSkills;
    private boolean hasAgents;

    /**
     * Creates a new AI integrations tray submenu.
     *
     * @param fullyQualifiedPackageName The FQPN of the application
     * @param architecture The architecture (e.g., "x64", "aarch64")
     * @param installDir The installation directory
     * @param toggleHandler Handler for toggling AI integrations
     */
    public AiIntegrationsTraySubmenu(
            String fullyQualifiedPackageName,
            String architecture,
            File installDir,
            AiIntegrationToggleHandler toggleHandler) {
        this.fullyQualifiedPackageName = fullyQualifiedPackageName;
        this.architecture = architecture;
        this.installDir = installDir;
        this.stateService = new AiIntegrationStateService(fullyQualifiedPackageName, architecture);
        this.toggleHandler = toggleHandler;

        detectAvailableIntegrations();
    }

    /**
     * Detects which AI integrations are available (skills, agents).
     */
    private void detectAvailableIntegrations() {
        if (installDir == null) {
            hasSkills = false;
            hasAgents = false;
            return;
        }

        File aiDir = new File(installDir, "ai");
        File skillsDir = new File(aiDir, "skills");
        File agentsDir = new File(aiDir, "agents");

        hasSkills = skillsDir.exists() && skillsDir.isDirectory() && skillsDir.list().length > 0;
        hasAgents = agentsDir.exists() && agentsDir.isDirectory() && agentsDir.list().length > 0;
    }

    /**
     * Creates and returns the AI integrations submenu.
     *
     * @return The submenu, or null if no AI integrations are available
     */
    public Menu createSubmenu() {
        AiIntegrationState state = stateService.loadState();

        // Only show submenu if there are installed tools
        if (state.getInstalledTools().isEmpty()) {
            logger.info("No AI tools installed, skipping AI integrations submenu");
            return null;
        }

        submenu = new Menu("AI Integrations");

        // MCP Server toggle
        mcpCheckbox = new CheckboxMenuItem("MCP Server");
        mcpCheckbox.setState(state.isMcpEnabled());
        mcpCheckbox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                handleMcpToggle(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        submenu.add(mcpCheckbox);

        // Skills toggle (if available)
        if (hasSkills) {
            skillsCheckbox = new CheckboxMenuItem("Skills");
            skillsCheckbox.setState(state.isSkillsEnabled());
            skillsCheckbox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    handleSkillsToggle(e.getStateChange() == ItemEvent.SELECTED);
                }
            });
            submenu.add(skillsCheckbox);
        }

        // Agents toggle (if available)
        if (hasAgents) {
            agentsCheckbox = new CheckboxMenuItem("Agents");
            agentsCheckbox.setState(state.isAgentsEnabled());
            agentsCheckbox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    handleAgentsToggle(e.getStateChange() == ItemEvent.SELECTED);
                }
            });
            submenu.add(agentsCheckbox);
        }

        return submenu;
    }

    /**
     * Handles toggling MCP server.
     */
    private void handleMcpToggle(boolean enabled) {
        logger.info("MCP toggle: " + (enabled ? "enabling" : "disabling"));

        new Thread(() -> {
            try {
                AiIntegrationState currentState = stateService.loadState();

                if (toggleHandler != null) {
                    if (enabled) {
                        toggleHandler.enableMcp(currentState.getInstalledTools());
                    } else {
                        toggleHandler.disableMcp(currentState.getInstalledTools());
                    }
                }

                // Update state
                AiIntegrationState newState = currentState.toBuilder()
                    .mcpEnabled(enabled)
                    .lastModified(System.currentTimeMillis())
                    .build();
                stateService.saveState(newState);

                logger.info("MCP " + (enabled ? "enabled" : "disabled") + " successfully");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to toggle MCP", e);
                // Revert checkbox state on error
                EventQueue.invokeLater(() -> mcpCheckbox.setState(!enabled));
            }
        }).start();
    }

    /**
     * Handles toggling skills.
     */
    private void handleSkillsToggle(boolean enabled) {
        logger.info("Skills toggle: " + (enabled ? "enabling" : "disabling"));

        new Thread(() -> {
            try {
                AiIntegrationState currentState = stateService.loadState();

                if (toggleHandler != null) {
                    if (enabled) {
                        toggleHandler.enableSkills();
                    } else {
                        toggleHandler.disableSkills();
                    }
                }

                // Update state
                AiIntegrationState newState = currentState.toBuilder()
                    .skillsEnabled(enabled)
                    .lastModified(System.currentTimeMillis())
                    .build();
                stateService.saveState(newState);

                logger.info("Skills " + (enabled ? "enabled" : "disabled") + " successfully");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to toggle skills", e);
                // Revert checkbox state on error
                EventQueue.invokeLater(() -> skillsCheckbox.setState(!enabled));
            }
        }).start();
    }

    /**
     * Handles toggling agents.
     */
    private void handleAgentsToggle(boolean enabled) {
        logger.info("Agents toggle: " + (enabled ? "enabling" : "disabling"));

        new Thread(() -> {
            try {
                AiIntegrationState currentState = stateService.loadState();

                if (toggleHandler != null) {
                    if (enabled) {
                        toggleHandler.enableAgents();
                    } else {
                        toggleHandler.disableAgents();
                    }
                }

                // Update state
                AiIntegrationState newState = currentState.toBuilder()
                    .agentsEnabled(enabled)
                    .lastModified(System.currentTimeMillis())
                    .build();
                stateService.saveState(newState);

                logger.info("Agents " + (enabled ? "enabled" : "disabled") + " successfully");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to toggle agents", e);
                // Revert checkbox state on error
                EventQueue.invokeLater(() -> agentsCheckbox.setState(!enabled));
            }
        }).start();
    }

    /**
     * Returns the submenu.
     */
    public Menu getSubmenu() {
        return submenu;
    }

    /**
     * Interface for handling AI integration toggle events.
     */
    public interface AiIntegrationToggleHandler {
        /**
         * Enables MCP server registration for the specified tools.
         */
        void enableMcp(Set<AIToolType> tools) throws Exception;

        /**
         * Disables MCP server registration for the specified tools.
         */
        void disableMcp(Set<AIToolType> tools) throws Exception;

        /**
         * Enables skills.
         */
        void enableSkills() throws Exception;

        /**
         * Disables skills.
         */
        void disableSkills() throws Exception;

        /**
         * Enables agents.
         */
        void enableAgents() throws Exception;

        /**
         * Disables agents.
         */
        void disableAgents() throws Exception;
    }
}
