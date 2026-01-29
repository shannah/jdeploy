package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;

/**
 * Configuration writer for Windsurf (by Codeium).
 *
 * Config location: ~/.codeium/windsurf/mcp_config.json
 */
public class WindsurfConfigWriter extends AbstractJsonConfigWriter {

    public WindsurfConfigWriter() {
        super(AIToolType.WINDSURF);
    }

    public WindsurfConfigWriter(AiToolConfigLocator configLocator) {
        super(AIToolType.WINDSURF, configLocator);
    }
}
