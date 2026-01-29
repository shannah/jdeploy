package ca.weblite.jdeploy.packaging;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Bundles AI integration assets (skills and agents) from .jdeploy/ into the jdeploy-bundle.
 *
 * This class copies:
 * - .jdeploy/skills/ to jdeploy-bundle/ai/skills/
 * - .jdeploy/agents/ to jdeploy-bundle/ai/agents/
 */
public class AiAssetBundler {

    /**
     * Bundles AI assets from the project directory into the jdeploy-bundle.
     *
     * @param context the packaging context
     * @throws IOException if an I/O error occurs during copying
     */
    public void bundleAiAssets(PackagingContext context) throws IOException {
        File projectDir = context.directory;
        File bundleDir = context.getJdeployBundleDir();
        PrintStream out = context.out;

        // Copy skills
        File skillsDir = new File(projectDir, ".jdeploy/skills");
        if (skillsDir.isDirectory() && hasContent(skillsDir)) {
            File destSkills = new File(bundleDir, "ai/skills");
            destSkills.mkdirs();
            FileUtils.copyDirectory(skillsDir, destSkills);
            int count = countSkillOrAgentDirs(destSkills);
            if (out != null) {
                out.println("Bundled " + count + " AI skill" + (count == 1 ? "" : "s") + " from .jdeploy/skills/");
            }
        }

        // Copy agents
        File agentsDir = new File(projectDir, ".jdeploy/agents");
        if (agentsDir.isDirectory() && hasContent(agentsDir)) {
            File destAgents = new File(bundleDir, "ai/agents");
            destAgents.mkdirs();
            FileUtils.copyDirectory(agentsDir, destAgents);
            int count = countSkillOrAgentDirs(destAgents);
            if (out != null) {
                out.println("Bundled " + count + " AI agent" + (count == 1 ? "" : "s") + " from .jdeploy/agents/");
            }
        }
    }

    /**
     * Checks if the project has any AI assets to bundle.
     *
     * @param projectDir the project directory
     * @return true if the project has skills or agents to bundle
     */
    public boolean hasAiAssets(File projectDir) {
        File skillsDir = new File(projectDir, ".jdeploy/skills");
        File agentsDir = new File(projectDir, ".jdeploy/agents");

        return (skillsDir.isDirectory() && hasContent(skillsDir)) ||
               (agentsDir.isDirectory() && hasContent(agentsDir));
    }

    /**
     * Checks if a directory has any content (subdirectories or files).
     */
    private boolean hasContent(File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        File[] children = dir.listFiles();
        return children != null && children.length > 0;
    }

    /**
     * Counts the number of skill or agent directories (directories containing SKILL.md).
     */
    private int countSkillOrAgentDirs(File dir) {
        if (!dir.isDirectory()) {
            return 0;
        }
        int count = 0;
        File[] children = dir.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                // Count directories that contain SKILL.md (valid skills/agents)
                // or all directories if we're just copying
                count++;
            }
        }
        return count;
    }
}
