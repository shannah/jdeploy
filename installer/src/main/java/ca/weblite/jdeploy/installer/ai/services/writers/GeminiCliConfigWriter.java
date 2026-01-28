package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;

/**
 * Configuration writer for Gemini CLI.
 *
 * Config location:
 * - macOS/Linux: ~/.gemini/settings.json
 * - Windows: %USERPROFILE%\.gemini\settings.json
 */
public class GeminiCliConfigWriter extends AbstractJsonConfigWriter {

    public GeminiCliConfigWriter() {
        super(AIToolType.GEMINI_CLI);
    }

    public GeminiCliConfigWriter(AiToolConfigLocator configLocator) {
        super(AIToolType.GEMINI_CLI, configLocator);
    }
}
