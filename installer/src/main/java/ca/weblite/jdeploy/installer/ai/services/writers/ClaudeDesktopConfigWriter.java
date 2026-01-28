package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;

/**
 * Configuration writer for Claude Desktop.
 *
 * Config location:
 * - macOS: ~/Library/Application Support/Claude/claude_desktop_config.json
 * - Windows: %APPDATA%\Claude\claude_desktop_config.json
 * - Linux: ~/.config/Claude/claude_desktop_config.json
 */
public class ClaudeDesktopConfigWriter extends AbstractJsonConfigWriter {

    public ClaudeDesktopConfigWriter() {
        super(AIToolType.CLAUDE_DESKTOP);
    }

    public ClaudeDesktopConfigWriter(AiToolConfigLocator configLocator) {
        super(AIToolType.CLAUDE_DESKTOP, configLocator);
    }
}
