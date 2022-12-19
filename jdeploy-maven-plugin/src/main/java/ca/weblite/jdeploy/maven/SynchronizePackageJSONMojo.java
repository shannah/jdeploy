package ca.weblite.jdeploy.maven;

import ca.weblite.jdeploy.maven.filesystem.PathUtil;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

@Mojo(name = "sync-package-json", defaultPhase = LifecyclePhase.PACKAGE)
public class SynchronizePackageJSONMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(name = "jdeployVersion", property="jdeploy.version", defaultValue = "latest")
    private String jdeployVersion;

    @Parameter(name = "syncProjectVersion", property="jdeploy.sync-version", defaultValue = "true")
    private boolean syncProjectVersion;

    @Parameter(name = "syncProjectName", property="jdeploy.sync-name", defaultValue = "true")
    private boolean syncProjectName;

    @Parameter(name = "syncProjectDescription", property="jdeploy.sync-description", defaultValue = "true")
    private boolean syncProjectDescription;

    private PathUtil pathUtil;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        pathUtil = new PathUtil(project);
        final Properties properties = project.getProperties();
        final File baseDir = project.getBasedir();
        final File packageJSON = new File(baseDir, "package.json");
        if (!packageJSON.exists()) {
            try {
                createPackageJSON();
            } catch (IOException ex) {
                throw new MojoFailureException("jdeploy mojo failed to create package.json file", ex);
            }
        }

        NPMParamBuilder paramBuilder = new NPMParamBuilder(createJdeployClient());
        try {
            paramBuilder.add("jdeploy.jar", pathUtil.getRelativePath(getJarPath()));
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to get relative path to jar file", ex);
        }
        if (syncProjectVersion) {
            paramBuilder.add("version", project.getVersion());
        }
        if (syncProjectName) {
            paramBuilder.add("name", properties.getProperty("jdeploy.name", project.getArtifactId()));
            paramBuilder.add("jdeploy.title", properties.getProperty("jdeploy.title", project.getName()));
        }
        if (syncProjectDescription) {
            paramBuilder.add("description", project.getDescription());
        }
        paramBuilder.add("jdeploy.javaVersion", String.valueOf(getCompilerTarget(8)));
        paramBuilder.add("jdeploy.javafx", hasJavafxDependency());



        try {
            paramBuilder.apply();
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to apply jdeploy npm parameters", ex);
        }

    }

    private void createPackageJSON() throws IOException {
        createJdeployClient().execJdeploy("init", "--no-prompt", "--no-workflow");
    }

    private JDeployClient createJdeployClient() {
        return new JDeployClient(jdeployVersion);
    }

    private File getJarPath() {
        final File outputDir = new File(project.getBuild().getOutputDirectory()).getParentFile();
        return new File(outputDir, project.getBuild().getFinalName() + ".jar");
    }


    public String getJdeployVersion() {
        return jdeployVersion;
    }

    public void setJdeployVersion(String jdeployVersion) {
        this.jdeployVersion = jdeployVersion;
    }

    private boolean hasJavafxDependency() {
        if (project.getProperties().getProperty("jdeploy.javafx", null) != null) {
            return "true".equals(project.getProperties().getProperty("jdeploy.javafx"));
        }
        for (Dependency dep : project.getDependencies()) {
            if (dep.getArtifactId().contains("javafx")) {
                return true;
            }
        }

        return false;
    }

    private int convertJavaVersionToInt(String compilerTarget, int minLevel) {
        if (compilerTarget.startsWith("1.")) {
            return Math.max(8, minLevel);
        } else {
            if (compilerTarget.contains(".")) {
                return Math.max(Integer.parseInt(compilerTarget.substring(0, compilerTarget.indexOf("."))), minLevel);
            } else {
                return Math.max(Integer.parseInt(compilerTarget), minLevel);
            }
        }
    }

    private int getCompilerTarget(int minLevel) {
        final Properties props = project.getProperties();
        return convertJavaVersionToInt(
                props.getProperty("maven.compiler.target",
                    props.getProperty("java.version",
                            System.getProperty("java.version", "8")
                    )
        ), minLevel);
    }
}
