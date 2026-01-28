package ca.weblite.jdeploy.installer.ai.services.writers;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.services.AiToolConfigLocator;
import ca.weblite.jdeploy.installer.ai.services.AiToolConfigWriter;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration writer for Codex CLI.
 *
 * Codex CLI uses TOML format for configuration.
 *
 * Config location: ~/.codex/config.toml
 *
 * Example:
 * # MCP server for My App - installed by jDeploy
 * [mcp_servers."a1b2c3d4.myapp"]
 * command = "myapp"
 * args = ["--jdeploy:command=mcp-server", "--verbose"]
 */
public class CodexConfigWriter implements AiToolConfigWriter {

    private final AIToolType toolType;
    private final AiToolConfigLocator configLocator;

    public CodexConfigWriter() {
        this.toolType = AIToolType.CODEX_CLI;
        this.configLocator = new AiToolConfigLocator();
    }

    public CodexConfigWriter(AiToolConfigLocator configLocator) {
        this.toolType = AIToolType.CODEX_CLI;
        this.configLocator = configLocator;
    }

    @Override
    public AIToolType getToolType() {
        return toolType;
    }

    @Override
    public File getConfigPath() {
        return configLocator.getConfigPath(toolType);
    }

    @Override
    public void addMcpServer(String serverName, String command, List<String> args, String comment) throws IOException {
        File configFile = getConfigPath();
        if (configFile == null) {
            throw new IOException("Config path not available for Codex CLI");
        }

        String content = "";
        if (configFile.exists()) {
            content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        }

        // Remove existing entry if present
        content = removeServerSection(content, serverName);

        // Add new entry at the end
        StringBuilder sb = new StringBuilder(content);
        if (!content.endsWith("\n") && !content.isEmpty()) {
            sb.append("\n");
        }
        if (!content.isEmpty()) {
            sb.append("\n");
        }

        // Add comment
        if (comment != null && !comment.isEmpty()) {
            sb.append("# ").append(comment).append("\n");
        }

        // Add section header
        sb.append("[mcp_servers.\"").append(escapeTomlKey(serverName)).append("\"]\n");

        // Add command
        sb.append("command = \"").append(escapeTomlString(command)).append("\"\n");

        // Add args
        if (args != null && !args.isEmpty()) {
            sb.append("args = [");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeTomlString(args.get(i))).append("\"");
            }
            sb.append("]\n");
        }

        // Ensure parent directory exists
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Files.write(configFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void removeMcpServer(String serverName) throws IOException {
        File configFile = getConfigPath();
        if (configFile == null || !configFile.exists()) {
            return;
        }

        String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        content = removeServerSection(content, serverName);
        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean serverExists(String serverName) throws IOException {
        File configFile = getConfigPath();
        if (configFile == null || !configFile.exists()) {
            return false;
        }

        String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
        String sectionPattern = "\\[mcp_servers\\.\"" + Pattern.quote(serverName) + "\"\\]";
        return Pattern.compile(sectionPattern).matcher(content).find();
    }

    @Override
    public void backupConfig() throws IOException {
        File configFile = getConfigPath();
        if (configFile == null || !configFile.exists()) {
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File backupFile = new File(configFile.getParent(), configFile.getName() + ".backup_" + timestamp);
        FileUtils.copyFile(configFile, backupFile);
    }

    @Override
    public String getClipboardConfig(String serverName, String command, List<String> args) {
        StringBuilder sb = new StringBuilder();

        sb.append("[mcp_servers.\"").append(escapeTomlKey(serverName)).append("\"]\n");
        sb.append("command = \"").append(escapeTomlString(command)).append("\"\n");

        if (args != null && !args.isEmpty()) {
            sb.append("args = [");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeTomlString(args.get(i))).append("\"");
            }
            sb.append("]\n");
        }

        return sb.toString();
    }

    /**
     * Installs a skill to Codex CLI's skills directory.
     */
    public void installSkill(File skillSourceDir, String skillName, String packagePrefix) throws IOException {
        File skillsDir = configLocator.getSkillsPath(AIToolType.CODEX_CLI);
        if (skillsDir == null) {
            throw new IOException("Skills path not available for Codex CLI");
        }

        String destName = packagePrefix + "." + skillName;
        File destDir = new File(skillsDir, destName);
        destDir.mkdirs();

        FileUtils.copyDirectory(skillSourceDir, destDir);
    }

    /**
     * Uninstalls a skill from Codex CLI.
     */
    public void uninstallSkill(String skillName, String packagePrefix) throws IOException {
        File skillsDir = configLocator.getSkillsPath(AIToolType.CODEX_CLI);
        if (skillsDir == null) {
            return;
        }

        String destName = packagePrefix + "." + skillName;
        File skillDir = new File(skillsDir, destName);
        if (skillDir.exists()) {
            FileUtils.deleteDirectory(skillDir);
        }
    }

    /**
     * Removes a server section from TOML content.
     */
    private String removeServerSection(String content, String serverName) {
        // Pattern to match the section header and all content until the next section or end
        String escapedName = Pattern.quote(serverName);
        String pattern = "(#[^\\n]*\\n)?\\[mcp_servers\\.\"" + escapedName + "\"\\][^\\[]*";
        return content.replaceAll(pattern, "").replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * Escapes a string for use as a TOML key.
     */
    private String escapeTomlKey(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Escapes a string for use as a TOML string value.
     */
    private String escapeTomlString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
