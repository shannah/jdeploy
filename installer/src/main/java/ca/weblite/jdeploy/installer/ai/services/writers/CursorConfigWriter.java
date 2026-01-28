package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;

/**
 * Configuration writer for Cursor.
 *
 * Config location:
 * - macOS/Linux: ~/.cursor/mcp.json
 * - Windows: %USERPROFILE%\.cursor\mcp.json
 */
public class CursorConfigWriter extends AbstractJsonConfigWriter {

    public CursorConfigWriter() {
        super(AIToolType.CURSOR);
    }

    public CursorConfigWriter(AiToolConfigLocator configLocator) {
        super(AIToolType.CURSOR, configLocator);
    }
}
