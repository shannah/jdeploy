package ca.weblite.jdeploy.services;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class ProjectTemplateCatalog {
    private String githubRepository;
    private String localPath;

    public ProjectTemplateCatalog() {
        localPath = System.getProperty("user.home") + "/.jdeploy/templates";
        githubRepository = "https://github.com/shannah/jdeploy-project-templates.git";
    }

    public void updateOld() throws Exception {
        if ( !new java.io.File(localPath).exists() ){
            System.out.println("Cloning "+githubRepository+" to "+localPath);
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", githubRepository, localPath);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } else {
            System.out.println("Updating "+localPath);
            ProcessBuilder pb = new ProcessBuilder("git", "pull");
            pb.directory(new java.io.File(localPath));
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        }
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
            System.out.println("Cloning " + githubRepository + " to " + localPath);

            Git.cloneRepository()
                    .setURI(githubRepository)
                    .setDirectory(repoDir)
                    .setCloneAllBranches(false)  // Only master branch
                    .setCloneSubmodules(false)   // No submodules
                    .call();
        } else {
            System.out.println("Updating " + localPath);

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

    public File getTemplate(String name) throws Exception {
        File f = new File(localPath, name);
        if ( !f.exists() ){
            throw new Exception("Template "+name+" not found");
        }
        return f;
    }
}
