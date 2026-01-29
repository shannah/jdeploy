package ca.weblite.jdeploy.ai.models;

/**
 * Enumeration of supported AI tools that can be configured with MCP servers,
 * skills, and agents from jDeploy applications.
 */
public enum AIToolType {
    CLAUDE_DESKTOP("Claude Desktop", true, false, false, true),
    CLAUDE_CODE("Claude Code", true, true, true, true),
    CODEX_CLI("Codex CLI", true, true, false, true),
    VSCODE_COPILOT("VS Code (Copilot)", true, false, false, true),
    CURSOR("Cursor", true, false, false, true),
    WINDSURF("Windsurf", true, false, false, true),
    GEMINI_CLI("Gemini CLI", true, false, false, true),
    OPENCODE("OpenCode", true, false, false, true),
    WARP("Warp", true, false, false, false),
    JETBRAINS("JetBrains IDEs", true, false, false, false);

    private final String displayName;
    private final boolean supportsMcp;
    private final boolean supportsSkills;
    private final boolean supportsAgents;
    private final boolean supportsAutoInstall;

    AIToolType(String displayName, boolean supportsMcp, boolean supportsSkills,
               boolean supportsAgents, boolean supportsAutoInstall) {
        this.displayName = displayName;
        this.supportsMcp = supportsMcp;
        this.supportsSkills = supportsSkills;
        this.supportsAgents = supportsAgents;
        this.supportsAutoInstall = supportsAutoInstall;
    }

    /**
     * Gets the human-readable display name for this AI tool.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this tool supports MCP (Model Context Protocol) servers.
     */
    public boolean supportsMcp() {
        return supportsMcp;
    }

    /**
     * Returns true if this tool supports installing skills from the filesystem.
     * Currently only Claude Code and Codex CLI support this.
     */
    public boolean supportsSkills() {
        return supportsSkills;
    }

    /**
     * Returns true if this tool supports installing agents from the filesystem.
     * Currently only Claude Code supports this.
     */
    public boolean supportsAgents() {
        return supportsAgents;
    }

    /**
     * Returns true if jDeploy can automatically configure this tool by writing
     * to its config file. Returns false for tools that require manual setup
     * (configuration copied to clipboard).
     */
    public boolean supportsAutoInstall() {
        return supportsAutoInstall;
    }

    /**
     * Returns true if this tool requires manual configuration via clipboard.
     */
    public boolean requiresManualSetup() {
        return !supportsAutoInstall;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
