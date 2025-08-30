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
 * A service that setups up the CLAUDE.md file for a proejct.
 * It will download the latest version of the CLAUDE.md file from
 * https://github.com/shannah/jdeploy-claude/blob/main/CLAUDE.md, and append it to the project's
 * CLAUDE.md file if it exists, or create a new CLAUDE.md file if it does not exist.
 */
@Singleton
public class SetupClaudeService {
    
    private static final String CLAUDE_MD_URL = "https://raw.githubusercontent.com/shannah/jdeploy-claude/main/CLAUDE.md";
    
    public void setup(File projectDirectory) throws IOException {
        if (projectDirectory == null || !projectDirectory.exists() || !projectDirectory.isDirectory()) {
            throw new IllegalArgumentException("Project directory must be a valid existing directory");
        }
        
        File claudeFile = new File(projectDirectory, "CLAUDE.md");
        String claudeContent = downloadClaudeTemplate();
        
        if (claudeFile.exists()) {
            String existingContent = FileUtils.readFileToString(claudeFile, StandardCharsets.UTF_8);
            if (!hasJDeploySection(existingContent)) {
                String combinedContent = existingContent + "\n\n" + claudeContent;
                FileUtils.writeStringToFile(claudeFile, combinedContent, StandardCharsets.UTF_8);
            }
        } else {
            FileUtils.writeStringToFile(claudeFile, claudeContent, StandardCharsets.UTF_8);
        }
    }
    
    private boolean hasJDeploySection(String content) {
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("Claude Instructions for jDeploy Setup".toLowerCase());
    }
    
    private String downloadClaudeTemplate() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        
        try {
            HttpGet getRequest = new HttpGet(CLAUDE_MD_URL);
            getRequest.setHeader("Accept", "text/plain");
            
            try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                } else {
                    throw new IOException("Failed to download CLAUDE.md template. HTTP status: " + statusCode);
                }
            }
        } finally {
            httpClient.close();
        }
    }
}
