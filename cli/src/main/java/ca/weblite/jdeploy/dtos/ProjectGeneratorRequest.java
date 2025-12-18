package ca.weblite.jdeploy.dtos;

import java.io.File;
import ca.weblite.jdeploy.cli.util.CommandLineParser;

public class ProjectGeneratorRequest {

    private final File parentDirectory;
    private final String projectName;
    private final String appTitle;
    private final String templateDirectory;
    private final String templateName;
    private final String groupId;
    private final String artifactId;

    private final String packageName;

    private final String mainClassName;

    private final String[] extensions;

    private final String githubRepository;

    private final boolean privateRepository;

    private final boolean useExistingDirectory;

    public File getParentDirectory() {
        return parentDirectory;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getTemplateDirectory() {
        return templateDirectory;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getAppTitle() {
        return appTitle;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String[] getExtensions() {
        return extensions;
    }

    public String getGithubRepository() {
        return githubRepository;
    }

    public boolean isPrivateRepository() {
        return privateRepository;
    }

    public boolean isUseExistingDirectory() {
        return useExistingDirectory;
    }

    public interface Params {

        @CommandLineParser.Help("The github repository to use.  E.g. \"username/repo\".  If not specified, will be inferred from the package name and project name.")
        String getGithubRepository();

        @CommandLineParser.Help("Whether the repository should be private")
        @CommandLineParser.Alias("p")
        boolean isPrivateRepository();

        @CommandLineParser.Help("Use existing directory")
        @CommandLineParser.Alias("e")
        boolean isUseExistingDirectory();

        @CommandLineParser.PositionalArg(1)
        @CommandLineParser.Help("The fully-qualified main class name.  If not specified, will be inferred from the package name and project name.")
        String getMagicArg();

        @CommandLineParser.Alias("d")
        @CommandLineParser.Help("Parent directory to create project in.  Defaults to current directory.")
        File getParentDirectory();

        @CommandLineParser.Alias("n")
        @CommandLineParser.Help("The name of the project. Defaults to the main class name")
        String getProjectName();

        @CommandLineParser.Help("The title of the application.  Defaults to the project name.")
        String getAppTitle();

        @CommandLineParser.Help("The directory containing the template to use for the project.  Defaults to the built-in templates.")
        @CommandLineParser.Alias("td")
        String getTemplateDirectory();

        @CommandLineParser.Help("The name of the template to use.  E.g. \"swing\" or \"javafx\".  Defaults to \"swing\".")
        @CommandLineParser.Alias("t")
        String getTemplateName();

        @CommandLineParser.Help("The group ID for the project. If omitted, will be derived from the package name.")
        @CommandLineParser.Alias("g")
        String getGroupId();

        @CommandLineParser.Help("The artifact ID for the project.  If omitted, will be derived from the package name.")
        @CommandLineParser.Alias("a")
        String getArtifactId();

        @CommandLineParser.Help("The package name for the project.  If omitted, will be derived from fully-qualified main class name")
        @CommandLineParser.Alias("pkg")
        String getPackageName();

        @CommandLineParser.Help("The main class name (not fully-qualified)")
        String getMainClassName();

        @CommandLineParser.Help("The extensions to include in the project. This is a comma-separated list of extensions. ")
        String[] getExtensions();

        @CommandLineParser.Help("Whether to include cheerpj deployment to github actions")
        boolean isWithCheerpj();


        Params setTemplateName(String templateName);

        Params setParentDirectory(File parentDirectory);

        Params setProjectName(String projectName);


        Params setAppTitle(String appTitle);

        Params setTemplateDirectory(String templateDirectory);

        Params setGroupId(String groupId);


        Params setArtifactId(String artifactId);

        Params setMagicArg(String magicArg);

        Params setPackageName(String packageName);

        Params setMainClassName(String mainClassName);

        Params setGithubRepository(String githubRepository);

        Params setPrivateRepository(boolean privateRepository);

        Params setUseExistingDirectory(boolean useExistingDirectory);

        Params setExtensions(String[] extensions);

        Params setWithCheerpj(boolean withCheerpj);

    }

    public ProjectGeneratorRequest(Params params) {
        this.parentDirectory = params.getParentDirectory();
        this.projectName = params.getProjectName();
        this.appTitle = params.getAppTitle();
        this.templateDirectory = params.getTemplateDirectory();
        this.templateName = params.getTemplateName();
        this.groupId = params.getGroupId();
        this.artifactId = params.getArtifactId();
        this.mainClassName = params.getMainClassName();
        this.packageName = params.getPackageName();
        this.extensions = params.getExtensions();
        this.githubRepository = params.getGithubRepository();
        this.privateRepository = params.isPrivateRepository();
        this.useExistingDirectory = params.isUseExistingDirectory();
    }
}

