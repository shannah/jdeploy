package ca.weblite.jdeploy.claude;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A service that installs the jDeploy setup skill for Claude Code.
 * It will download the latest version of the skill from
 * https://github.com/shannah/jdeploy-claude/blob/main/CLAUDE.md and install it
 * as a Claude Code skill at .claude/skills/setup-jdeploy.md in the project directory.
 */
@Singleton
public class SetupClaudeService {

    private static final String SKILL_CONTENT_URL = "https://raw.githubusercontent.com/shannah/jdeploy-claude/main/CLAUDE.md";
    private static final String SKILL_DIRECTORY = ".claude/skills";
    private static final String SKILL_FILENAME = "setup-jdeploy.md";

    public void setup(File projectDirectory) throws IOException {
        if (projectDirectory == null || !projectDirectory.exists() || !projectDirectory.isDirectory()) {
            throw new IllegalArgumentException("Project directory must be a valid existing directory");
        }

        File skillsDir = new File(projectDirectory, SKILL_DIRECTORY);
        if (!skillsDir.exists()) {
            skillsDir.mkdirs();
        }

        File skillFile = new File(skillsDir, SKILL_FILENAME);
        String skillContent = downloadSkillContent();

        FileUtils.writeStringToFile(skillFile, skillContent, StandardCharsets.UTF_8);
    }

    public boolean isInstalled(File projectDirectory) {
        if (projectDirectory == null || !projectDirectory.exists() || !projectDirectory.isDirectory()) {
            return false;
        }
        File skillFile = new File(projectDirectory, SKILL_DIRECTORY + "/" + SKILL_FILENAME);
        return skillFile.exists();
    }

    private String downloadSkillContent() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            HttpGet getRequest = new HttpGet(SKILL_CONTENT_URL);
            getRequest.setHeader("Accept", "text/plain");

            try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                } else {
                    throw new IOException("Failed to download jDeploy skill content. HTTP status: " + statusCode);
                }
            }
        } finally {
            httpClient.close();
        }
    }
}
