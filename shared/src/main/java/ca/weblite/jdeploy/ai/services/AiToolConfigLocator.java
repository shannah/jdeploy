package ca.weblite.jdeploy.ai.services;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.tools.platform.Platform;

import java.io.File;

/**
 * Service for locating configuration file paths for various AI tools.
 * Handles platform-specific path resolution.
 */
public class AiToolConfigLocator {

    private final Platform platform;

    public AiToolConfigLocator() {
        this.platform = Platform.getSystemPlatform();
    }

    /**
     * Constructor for testing with a specific platform.
     */
    public AiToolConfigLocator(Platform platform) {
        this.platform = platform;
    }

    /**
     * Gets the configuration file path for the specified AI tool.
     *
     * @param toolType the AI tool type
     * @return the configuration file path, or null if the tool doesn't have a config file
     */
    public File getConfigPath(AIToolType toolType) {
        switch (toolType) {
            case CLAUDE_DESKTOP:
                return getClaudeDesktopConfigPath();
            case CLAUDE_CODE:
                return getClaudeCodeConfigPath();
            case CODEX_CLI:
                return getCodexConfigPath();
            case VSCODE_COPILOT:
                return getVSCodeMcpConfigPath();
            case CURSOR:
                return getCursorConfigPath();
            case WINDSURF:
                return getWindsurfConfigPath();
            case GEMINI_CLI:
                return getGeminiCliConfigPath();
            case OPENCODE:
                return getOpenCodeConfigPath();
            case WARP:
            case JETBRAINS:
                // These tools don't have a file-based config path
                // Configuration is done via UI/clipboard
                return null;
            default:
                return null;
        }
    }

    /**
     * Gets the skills directory path for the specified AI tool.
     *
     * @param toolType the AI tool type
     * @return the skills directory path, or null if the tool doesn't support skills
     */
    public File getSkillsPath(AIToolType toolType) {
        if (!toolType.supportsSkills()) {
            return null;
        }

        switch (toolType) {
            case CLAUDE_CODE:
                return new File(getUserHome(), ".claude/skills");
            case CODEX_CLI:
                return new File(getUserHome(), ".codex/skills");
            default:
                return null;
        }
    }

    /**
     * Gets the agents directory path for the specified AI tool.
     *
     * @param toolType the AI tool type
     * @return the agents directory path, or null if the tool doesn't support agents
     */
    public File getAgentsPath(AIToolType toolType) {
        if (!toolType.supportsAgents()) {
            return null;
        }

        switch (toolType) {
            case CLAUDE_CODE:
                return new File(getUserHome(), ".claude/agents");
            default:
                return null;
        }
    }

    /**
     * Gets the detection directory for the specified AI tool.
     * This is the directory that indicates the tool is installed.
     *
     * @param toolType the AI tool type
     * @return the detection directory, or null if no directory-based detection
     */
    public File getDetectionPath(AIToolType toolType) {
        switch (toolType) {
            case CLAUDE_DESKTOP:
                return getClaudeDesktopDir();
            case CLAUDE_CODE:
                // Claude Code can be detected by ~/.claude.json OR ~/.claude/
                File claudeJson = getClaudeCodeConfigPath();
                File claudeDir = new File(getUserHome(), ".claude");
                if (claudeJson != null && claudeJson.exists()) {
                    return claudeJson;
                }
                return claudeDir;
            case CODEX_CLI:
                return new File(getUserHome(), ".codex");
            case CURSOR:
                return getCursorDir();
            case WINDSURF:
                return new File(getUserHome(), ".codeium/windsurf");
            case GEMINI_CLI:
                return getGeminiCliDir();
            case OPENCODE:
                return new File(getUserHome(), ".config/opencode");
            case VSCODE_COPILOT:
                // VS Code detection is more complex - check for .vscode in home or settings
                return new File(getUserHome(), ".vscode");
            case WARP:
            case JETBRAINS:
                // These are always "detected" since they require manual setup
                return null;
            default:
                return null;
        }
    }

    // Claude Desktop config paths

    private File getClaudeDesktopConfigPath() {
        File dir = getClaudeDesktopDir();
        if (dir != null) {
            return new File(dir, "claude_desktop_config.json");
        }
        return null;
    }

    private File getClaudeDesktopDir() {
        if (platform.isMac()) {
            return new File(getUserHome(), "Library/Application Support/Claude");
        } else if (platform.isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return new File(appData, "Claude");
            }
            return new File(getUserHome(), "AppData/Roaming/Claude");
        } else {
            // Linux
            return new File(getUserHome(), ".config/Claude");
        }
    }

    // Claude Code config paths

    private File getClaudeCodeConfigPath() {
        return new File(getUserHome(), ".claude.json");
    }

    // Codex CLI config paths

    private File getCodexConfigPath() {
        return new File(getUserHome(), ".codex/config.toml");
    }

    // VS Code config paths

    private File getVSCodeMcpConfigPath() {
        // For VS Code, we write to .vscode/mcp.json in the workspace
        // or to the user settings.json
        // For global installation, we use a user-level location
        return new File(getUserHome(), ".vscode/mcp.json");
    }

    // Cursor config paths

    private File getCursorConfigPath() {
        File dir = getCursorDir();
        if (dir != null) {
            return new File(dir, "mcp.json");
        }
        return null;
    }

    private File getCursorDir() {
        if (platform.isWindows()) {
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null) {
                return new File(userProfile, ".cursor");
            }
        }
        return new File(getUserHome(), ".cursor");
    }

    // Windsurf config paths

    private File getWindsurfConfigPath() {
        return new File(getUserHome(), ".codeium/windsurf/mcp_config.json");
    }

    // Gemini CLI config paths

    private File getGeminiCliConfigPath() {
        File dir = getGeminiCliDir();
        if (dir != null) {
            return new File(dir, "settings.json");
        }
        return null;
    }

    private File getGeminiCliDir() {
        if (platform.isWindows()) {
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null) {
                return new File(userProfile, ".gemini");
            }
        }
        return new File(getUserHome(), ".gemini");
    }

    // OpenCode config paths

    private File getOpenCodeConfigPath() {
        return new File(getUserHome(), ".config/opencode/opencode.json");
    }

    private String getUserHome() {
        return System.getProperty("user.home");
    }
}
