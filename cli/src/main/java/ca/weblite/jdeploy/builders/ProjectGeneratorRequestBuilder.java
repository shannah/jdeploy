package ca.weblite.jdeploy.builders;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.dtos.ProjectGeneratorRequest;
import ca.weblite.jdeploy.helpers.StringUtils;
import ca.weblite.jdeploy.services.ProjectGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProjectGeneratorRequestBuilder implements ProjectGeneratorRequest.Params {

    @CommandLineParser.Help("The github repository to use.  E.g. \"username/repo\".  If not specified, will be inferred from the package name and project name.")
    private String githubRepository;

    @CommandLineParser.Help("Whether the repository should be private")
    private boolean privateRepository;

    @CommandLineParser.Help("Whether to use an existing directory")
    private boolean useExistingDirectory;

    @CommandLineParser.PositionalArg(1)
    @CommandLineParser.Help("The fully-qualified main class name.  If not specified, will be inferred from the package name and project name.")
    private String magicArg;

    @CommandLineParser.Alias("d")
    @CommandLineParser.Help("Parent directory to create project in.  Defaults to current directory.")
    private File parentDirectory;

    @CommandLineParser.Alias("n")
    @CommandLineParser.Help("The name of the project. Defaults to the main class name")
    private String projectName;

    @CommandLineParser.Help("The title of the application.  Defaults to the project name.")
    private String appTitle;

    @CommandLineParser.Help("The directory containing the template to use for the project.  Defaults to the built-in templates.")
    @CommandLineParser.Alias("td")
    private String templateDirectory;

    @CommandLineParser.Help("The name of the template to use.  E.g. \"swing\" or \"javafx\".  Defaults to \"swing\".")
    @CommandLineParser.Alias("t")
    private String templateName;

    @CommandLineParser.Help("The group ID for the project. If omitted, will be derived from the package name.")
    @CommandLineParser.Alias("g")
    private String groupId;

    @CommandLineParser.Help("The artifact ID for the project.  If omitted, will be derived from the package name.")
    @CommandLineParser.Alias("a")
    private String artifactId;

    @CommandLineParser.Help("The package name for the project.  If omitted, will be derived from fully-qualified main class name")
    @CommandLineParser.Alias("pkg")
    private String packageName;

    @CommandLineParser.Help("The main class name (not fully-qualified)")
    private String mainClassName;

    @CommandLineParser.Help("The extensions to include in the project. This is a comma-separated list of extensions. ")
    private String[] extensions;

    @CommandLineParser.Help("Whether to include cheerpj deployment to github actions")
    private boolean withCheerpj = false;


    public ProjectGeneratorRequest.Params setTemplateName(String templateName) {
        this.templateName = templateName;

        return this;
    }

    public ProjectGeneratorRequest.Params setParentDirectory(File parentDirectory) {
        this.parentDirectory = parentDirectory;
        return this;
    }

    public ProjectGeneratorRequest.Params setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public ProjectGeneratorRequest.Params setUseExistingDirectory(boolean useExistingDirectory) {
        this.useExistingDirectory = useExistingDirectory;
        return this;
    }

    public String getProjectName() {
        if (projectName != null) {
            return projectName;
        }
        StringUtils stringUtils = new StringUtils();

        String githubRepo = getGithubRepository();
        if (githubRepo != null) {
            return stringUtils.camelCaseToLowerCaseWithSeparator(getGithubRepositoryName(), "-");
        }

        if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
            if (stringUtils.countCharInstances(magicArg, '.') > 2) {
                String pkg = splitPackageIntoPackageAndClassName(magicArg)[0];
                return stringUtils.camelCaseToLowerCaseWithSeparator(
                        pkg.substring(pkg.lastIndexOf(".")+1),
                        "-"
                );
            }
            return  stringUtils.camelCaseToLowerCaseWithSeparator(
                    splitPackageIntoPackageAndClassName(magicArg)[1],
                    "-"
            );
        }

        if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
            if (magicArg.contains(".")) {
                return stringUtils.camelCaseToLowerCaseWithSeparator(magicArg.substring(magicArg.lastIndexOf(".")+1), "-");
            }
            return stringUtils.camelCaseToLowerCaseWithSeparator(magicArg, "-");
        }

        return "my-app";
    }

    public ProjectGeneratorRequest.Params setAppTitle(String appTitle) {
        this.appTitle = appTitle;
        return this;
    }

    public String getAppTitle() {
        if (appTitle != null) {
            return appTitle;
        }

        StringUtils stringUtils = new StringUtils();


        String githubRepo = getGithubRepository();
        if (githubRepo != null) {
            return stringUtils.ucWords(
                    getGithubRepositoryName().replaceAll("[\\-_\\.]", " ")
            );
        }

        if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
            return stringUtils.ucWords(
                    stringUtils.camelCaseToLowerCaseWithSeparator(
                            splitPackageIntoPackageAndClassName(magicArg)[1],
                            " "
                    )
            );
        }

        if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
            if (magicArg.contains(".")) {
                return stringUtils.ucWords(
                        stringUtils.camelCaseToLowerCaseWithSeparator(
                                magicArg.substring(magicArg.lastIndexOf(".")+1),
                                " "
                        )
                );
            }
            return stringUtils.camelCaseToLowerCaseWithSeparator(magicArg, " ");
        }

        return "My App";
    }

    @Override
    public String getTemplateDirectory() {
        return templateDirectory;
    }

    @Override
    public String getTemplateName() {
        return templateName;
    }

    public ProjectGeneratorRequest.Params setTemplateDirectory(String templateDirectory) {
        this.templateDirectory = templateDirectory;
        return this;
    }

    public ProjectGeneratorRequest.Params setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public String getGroupId() {
        if (groupId != null) {
            return groupId;
        }
        StringUtils stringUtils = DIContext.getInstance().getInstance(StringUtils.class);

        String githubRepository = getGithubRepository();
        if (githubRepository != null) {
            return "com.github." + getGithubUser();
        }

        if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {

            String out =  splitPackageIntoPackageAndClassName(magicArg)[0];
            if (stringUtils.countCharInstances(out, '.') > 1) {
                out = out.substring(0, out.lastIndexOf("."));
            }

            return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
        }

        if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
            String out =  magicArg;
            if (stringUtils.countCharInstances(out, '.') > 1) {
                out = out.substring(0, out.lastIndexOf("."));
            }

            return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
        }

        return "com.example";
    }

    public ProjectGeneratorRequest.Params setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    public String getArtifactId() {
        if (artifactId != null) {
            return artifactId;
        }
        String githubRepository = getGithubRepository();
        if (githubRepository != null) {
            return getGithubRepositoryName();
        }
        StringUtils stringUtils = new StringUtils();
        if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
            String pkg = splitPackageIntoPackageAndClassName(magicArg)[0];
            String out;
            if (stringUtils.countCharInstances(pkg, '.') > 1) {
                out = pkg.substring(pkg.lastIndexOf(".")+1);
            } else {
                out = splitPackageIntoPackageAndClassName(magicArg)[1];
            }

            return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
        }

        if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
            String pkg = magicArg;
            String out;
            if (stringUtils.countCharInstances(pkg, '.') > 0) {
                out = pkg.substring(pkg.lastIndexOf(".")+1);

            } else {
                out = magicArg;
            }

            return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
        }

        if (projectName != null) {
            return stringUtils.camelCaseToLowerCaseWithSeparator(projectName, "-");
        }

        return "my-app";
    }

    public String getMainClassName() {
        if (mainClassName != null) {
            return mainClassName;
        }

        StringUtils stringUtils = new StringUtils();
        String githubRepository = getGithubRepository();
        if (githubRepository != null) {
            return stringUtils.ucFirst(
                    stringUtils.lowerCaseWithSeparatorToCamelCase(getGithubRepositoryName(), "-")
            );
        }
        if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
            return splitPackageIntoPackageAndClassName(magicArg)[1];
        }

        return "Main";
    }

    public String getPackageName() {
        if (packageName != null) {
            return packageName;
        }
        String githubRepository = getGithubRepository();
        StringUtils stringUtils = DIContext.getInstance().getInstance(StringUtils.class);
        if (githubRepository != null) {
            String pkg = "com.github."
                    + stringUtils.lowerCaseWithSeparatorToCamelCase(getGithubUser(), "-")
                    + "." + stringUtils.lowerCaseWithSeparatorToCamelCase(getGithubRepositoryName(), "-");

            return pkg.toLowerCase();
        }

        if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
            return splitPackageIntoPackageAndClassName(magicArg)[0];
        }

        if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
            return magicArg;
        }

        if (groupId != null) {
            String pkg = stringUtils.lowerCaseWithSeparatorToCamelCase(groupId, "-");
            if (artifactId != null) {
                pkg += "." + stringUtils.lowerCaseWithSeparatorToCamelCase(artifactId, "-");
            }
            return pkg;
        }

        if (artifactId != null) {
            return "com.example." + stringUtils.lowerCaseWithSeparatorToCamelCase(artifactId, "-");
        }

        return "com.example.myapp";
    }

    public ProjectGeneratorRequest build() {
        return new ProjectGeneratorRequest(this);
    }

    public String getMagicArg() {
        return magicArg;
    }

    @Override
    public File getParentDirectory() {
        return parentDirectory;
    }

    public ProjectGeneratorRequest.Params setMagicArg(String magicArg) {
        this.magicArg = magicArg;
        return this;
    }

    @Override
    public ProjectGeneratorRequest.Params setPackageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    @Override
    public ProjectGeneratorRequest.Params setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;

        return this;
    }

    @Override
    public String[] getExtensions() {
        List<String> extensionsList = extensions == null
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(extensions));
        if (withCheerpj) {
            extensionsList.add("cheerpj");
        }
        return extensionsList.toArray(new String[extensionsList.size()]);
    }

    public ProjectGeneratorRequest.Params setExtensions(String[] extensions) {
        this.extensions = extensions;

        return this;
    }

    public boolean isWithCheerpj() {
        return withCheerpj;
    }

    public ProjectGeneratorRequest.Params setWithCheerpj(boolean withCheerpj) {
        this.withCheerpj = withCheerpj;

        return this;
    }

    @Override
    public ProjectGeneratorRequest.Params setGithubRepository(String githubRepository) {
        this.githubRepository = githubRepository;

        return this;
    }

    @Override
    public ProjectGeneratorRequest.Params setPrivateRepository(boolean privateRepository) {
        this.privateRepository = privateRepository;

        return this;
    }

    public String getGithubRepository() {
        if (githubRepository != null) {
            return githubRepository;
        }
        if (magicArg != null && magicArg.matches("^[a-zA-Z0-9\\-]+/[a-zA-Z0-9\\-]+$")) {
            return magicArg;
        }
        return null;
    }

    public boolean isPrivateRepository() {
        return privateRepository;
    }

    @Override
    public boolean isUseExistingDirectory() {
        return useExistingDirectory;
    }

    private String getGithubUser() {
        String repo = getGithubRepository();
        if (repo != null) {
            return repo.split("/")[0];
        }
        return null;
    }

    private String getGithubRepositoryName() {
        String repo = getGithubRepository();
        if (repo != null) {
            return repo.split("/")[1];
        }
        return null;
    }

    private static String[] splitPackageIntoPackageAndClassName(String packageName) {
        String[] parts = packageName.split("\\.");
        String className = parts[parts.length - 1];
        String[] packageParts = new String[parts.length - 1];
        System.arraycopy(parts, 0, packageParts, 0, parts.length - 1);
        return new String[]{String.join(".", packageParts), className};
    }

    private static boolean isPackageAndClassName(StringUtils stringUtils, String packageName) {
        if (!stringUtils.isValidJavaClassName(packageName)) {
            return false;
        }

        String[] parts = packageName.split("\\.");
        if (parts.length < 2) {
            return false;
        }

        if (parts[parts.length - 1].isEmpty()) {
            return false;
        }

        return (Character.isUpperCase(parts[parts.length - 1].charAt(0)));
    }

}
