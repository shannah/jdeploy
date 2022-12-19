package ca.weblite.jdeploy.maven.filesystem;

import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

public class PathUtil {
    private final MavenProject mavenProject;

    public PathUtil(MavenProject mavenProject) {
        this.mavenProject = mavenProject;
    }

    public String getRelativePath(File file) throws IOException {
        final String canonicalPath = file.getCanonicalPath();
        final String projectCanonicalPath = mavenProject.getBasedir().getCanonicalPath();
        if (canonicalPath.startsWith(projectCanonicalPath)) {
            return canonicalPath.substring(projectCanonicalPath.length()+1);
        }
        return canonicalPath;
    }
}
