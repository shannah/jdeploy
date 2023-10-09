package ca.weblite.jdeploy.services;

import java.io.File;

public class ProjectTemplateCatalog {
    private String githubRepository;
    private String localPath;

    public ProjectTemplateCatalog() {
        localPath = System.getProperty("user.home") + "/.jdeploy/templates";
        githubRepository = "https://github.com/shannah/jdeploy-project-templates.git";
    }

    public void update() throws Exception {
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

    public File getTemplate(String name) throws Exception {
        File f = new File(localPath, name);
        if ( !f.exists() ){
            throw new Exception("Template "+name+" not found");
        }
        return f;
    }
}
