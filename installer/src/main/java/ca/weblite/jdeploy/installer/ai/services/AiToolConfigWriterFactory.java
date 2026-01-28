package ca.weblite.jdeploy.installer.ai.services;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.installer.ai.services.writers.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for creating AI tool configuration writers.
 */
public class AiToolConfigWriterFactory {

    private final Map<AIToolType, AiToolConfigWriter> writers = new EnumMap<>(AIToolType.class);

    public AiToolConfigWriterFactory() {
        // Register all writers
        register(new ClaudeDesktopConfigWriter());
        register(new ClaudeCodeConfigWriter());
        register(new CodexConfigWriter());
        register(new VSCodeCopilotConfigWriter());
        register(new CursorConfigWriter());
        register(new WindsurfConfigWriter());
        register(new GeminiCliConfigWriter());
        register(new OpenCodeConfigWriter());
        register(ClipboardConfigWriter.forWarp());
        register(ClipboardConfigWriter.forJetBrains());
    }

    private void register(AiToolConfigWriter writer) {
        writers.put(writer.getToolType(), writer);
    }

    /**
     * Gets the config writer for the specified AI tool type.
     *
     * @param toolType the AI tool type
     * @return the config writer, or null if not found
     */
    public AiToolConfigWriter getWriter(AIToolType toolType) {
        return writers.get(toolType);
    }

    /**
     * Gets the Claude Code config writer (which supports skills and agents).
     */
    public ClaudeCodeConfigWriter getClaudeCodeWriter() {
        return (ClaudeCodeConfigWriter) writers.get(AIToolType.CLAUDE_CODE);
    }

    /**
     * Gets the Codex CLI config writer (which supports skills).
     */
    public CodexConfigWriter getCodexWriter() {
        return (CodexConfigWriter) writers.get(AIToolType.CODEX_CLI);
    }
}
