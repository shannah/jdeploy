package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;

import java.io.File;
import java.io.IOException;

public class GithubWorkflowGenerator {
    private final File directory;

    public GithubWorkflowGenerator(File directory) {
        this.directory = directory;
    }


    private boolean isGradleProject() {
        return new File(directory, "build.gradle").exists();
    }

    private boolean isMavenProject() {
        return new File(directory, "pom.xml").exists();
    }

    private boolean isAntProject() {
        return new File(directory, "build.xml").exists();
    }

    private String getWorkflowTemplatePath() throws IOException {
        final String workflowTemplatesPath = "/ca/weblite/jdeploy/github/";
        if (isGradleProject()) {
            return workflowTemplatesPath + "gradle-workflow.yml";
        } else if (isMavenProject()) {
            return workflowTemplatesPath + "maven-workflow.yml";
        } else if (isAntProject()) {
            return workflowTemplatesPath + "ant-workflow.yml";
        } else {
            throw new IOException("Cannot generate github workflow because the project type could not be determined.  Only ant, gradle, and maven projects are supported");
        }
    }

    public File getGithubWorkflowFile() {
        final String sep = File.separator;
        return new File(directory,  ".github" + sep + "workflows" + sep + "jdeploy.yml");
    }
    public void generateGithubWorkflow(final int javaVersionInt, final String jdeployVersion) throws IOException {
        final String templateContent = IOUtil.readToString(JDeploy.class.getResourceAsStream(getWorkflowTemplatePath()));
        final String workflowFileContent = templateContent
                .replace("#{{ JAVA_VERSION }}", String.valueOf(javaVersionInt))
                .replace("#{{ JDEPLOY_VERSION }}", jdeployVersion);
        final File workflowFile = getGithubWorkflowFile();
        if (workflowFile.exists()) {
            throw new IOException("Workflow file " + workflowFile + " already exists.");
        }
        workflowFile.getParentFile().mkdirs();
        FileUtil.writeStringToFile(workflowFileContent, workflowFile);
    }
}
