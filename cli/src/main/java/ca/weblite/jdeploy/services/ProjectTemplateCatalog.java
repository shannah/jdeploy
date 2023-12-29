package ca.weblite.jdeploy.services;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import javax.inject.Singleton;

@Singleton
public class ProjectTemplateCatalog {
    private String githubRepository;
    private String localPath;

    private static final String REPO_URL = "https://github.com/shannah/jdeploy-project-templates.git";

    public ProjectTemplateCatalog() {
        localPath = System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "templates";
        githubRepository = REPO_URL;
    }

    public void update() throws IOException {
        try {
            updateInternal();
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }

    private void updateInternal() throws IOException, GitAPIException {
        java.io.File repoDir = new java.io.File(localPath);
        if (!repoDir.exists()) {
            Git.cloneRepository()
                    .setURI(githubRepository)
                    .setDirectory(repoDir)
                    .setCloneAllBranches(false)  // Only master branch
                    .setCloneSubmodules(false)   // No submodules
                    .call();
        } else {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repo = builder.setGitDir(new java.io.File(repoDir, ".git"))
                    .readEnvironment() // Scan environment GIT_* variables
                    .findGitDir()     // Scan up the file system tree
                    .build();

            try (Git git = new Git(repo)) {
                git.pull().call();
            } finally {
                repo.close();
            }
        }
    }

    public File getProjectTemplate(String name) throws Exception {
        File f = new File(getProjectsDir(), name);
        if ( !f.exists() ){
            throw new Exception("Template "+name+" not found");
        }
        return f;
    }

    public File getExtensionTemplate(String name) throws Exception {
        File f = new File(getExtensionsDir(), name);
        if ( !f.exists() ){
            throw new Exception("Template "+name+" not found");
        }
        return f;
    }

    private File getProjectsDir() {
        return new File(localPath, "projects");
    }

    private File getExtensionsDir() {
        return new File(localPath, "extensions");
    }
}
