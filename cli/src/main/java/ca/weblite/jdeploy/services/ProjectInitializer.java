package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.records.PackageJsonInitializerContext;
import ca.weblite.jdeploy.records.PackageJsonValidationResult;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class ProjectInitializer {

    private final PackageJsonValidator packageJsonValidator;

    private final PackageJsonInitializer packageJsonInitializer;

    private final PackageJsonValidationResult.Factory packageJsonValidationResultFactory;

    private final PackageJsonInitializerContext.Factory packageJsonInitializerContextFactory;

    private final JavaVersionExtractor javaVersionExtractor;

    private static final int DEFAULT_JAVA_VERSION = 11;

    private static final String DEFAULT_GITHUB_WORKFLOW_JDEPLOY_VERSION = "master";

    @Inject
    public ProjectInitializer(
            PackageJsonValidator packageJsonValidator,
            PackageJsonInitializer packageJsonInitializer,
            PackageJsonValidationResult.Factory packageJsonValidationResultFactory,
            PackageJsonInitializerContext.Factory packageJsonInitializerContextFactory,
            JavaVersionExtractor javaVersionExtractor
    ) {
        this.packageJsonValidator = packageJsonValidator;
        this.packageJsonInitializer = packageJsonInitializer;
        this.packageJsonValidationResultFactory = packageJsonValidationResultFactory;
        this.packageJsonInitializerContextFactory = packageJsonInitializerContextFactory;
        this.javaVersionExtractor = javaVersionExtractor;
    }

    public PreparationResult prepare(Context context) throws IOException {
        boolean githubWorkflowExists = false;
        PackageJsonInitializer.PreparationResult packageJsonPreparationResult =
                packageJsonInitializer.prepare(
                        packageJsonInitializerContextFactory.fromProjectInitializerContext(context)
                );

        final boolean packageJsonExists = packageJsonPreparationResult.exists();
        final boolean packageJsonIsValid = packageJsonPreparationResult.isValid();
        PackageJsonValidationResult validationResult = packageJsonPreparationResult.getValidationResult();


        GithubWorkflowGenerator githubWorkflowGenerator = new GithubWorkflowGenerator(context.getDirectory());
        githubWorkflowExists = githubWorkflowGenerator.getGithubWorkflowFile().exists();

        return new PreparationResultBuilder()
                .setGithubWorkflowExists(githubWorkflowExists)
                .setPackageJsonExists(packageJsonExists)
                .setPackageJsonIsValid(packageJsonIsValid)
                .setPackageJsonValidationResult(validationResult)
                .setProposedPackageJsonContent(packageJsonPreparationResult.getProposedPackageJsonToWrite())
                .build();
    }

    public InitializeProjectResult initializeProject(Context context) throws IOException {
        context = new ContextWithResult(context, new InitializeProjectResultBuilder());

        if (context.isGeneratePackageJson() || context.isFixPackageJson()) {
            PackageJsonInitializer.InitializePackageJsonResult result = packageJsonInitializer.initializePackageJson(
                    packageJsonInitializerContextFactory.fromProjectInitializerContext(context)
            );
            getResultBuilder(context).setPackageJsonFile(result.getPackageJsonFile());
            for (String message : result.getMessages()) {
                getResultBuilder(context).addMessage(message);
            }
        }

        if (context.isGenerateGithubWorkflow()) {
            GithubWorkflowGenerator githubWorkflowGenerator = new GithubWorkflowGenerator(context.getDirectory());
            githubWorkflowGenerator.generateGithubWorkflow(
                    javaVersionExtractor.extractJavaVersionFromSystemProperties(DEFAULT_JAVA_VERSION),
                    DEFAULT_GITHUB_WORKFLOW_JDEPLOY_VERSION
            );
            getResultBuilder(context).setGithubWorkflowFile(githubWorkflowGenerator.getGithubWorkflowFile());
            getResultBuilder(context).addMessage("Generated Github workflow file: " + githubWorkflowGenerator.getGithubWorkflowFile().getAbsolutePath());
        }

        return getResultBuilder(context).createInitializePackageJsonResult();

    }

    public static class Context {
        private final File directory;
        private final boolean generateGithubWorkflow, generatePackageJson, fixPackageJson;

        public Context(File directory, boolean generateGithubWorkflow, boolean generatePackageJson, boolean fixPackageJson) {
            this.directory = directory;
            this.generateGithubWorkflow = generateGithubWorkflow;
            this.generatePackageJson = generatePackageJson;
            this.fixPackageJson = fixPackageJson;
        }

        public File getDirectory() {
            return directory;
        }

        public boolean isGenerateGithubWorkflow() {
            return generateGithubWorkflow;
        }

        public boolean isGeneratePackageJson() {
            return generatePackageJson;
        }

        public boolean isFixPackageJson() {
            return fixPackageJson;
        }

    }

    public static class ContextBuilder {
        private File directory;
        private boolean generateGithubWorkflow, generatePackageJson, fixPackageJson;

        public ContextBuilder setDirectory(File directory) {
            this.directory = directory;
            return this;
        }

        public ContextBuilder setGenerateGithubWorkflow(boolean generateGithubWorkflow) {
            this.generateGithubWorkflow = generateGithubWorkflow;
            return this;
        }

        public ContextBuilder setGeneratePackageJson(boolean generatePackageJson) {
            this.generatePackageJson = generatePackageJson;
            return this;
        }

        public ContextBuilder setFixPackageJson(boolean fixPackageJson) {
            this.fixPackageJson = fixPackageJson;
            return this;
        }

        public Context build() {
            return new Context(directory, generateGithubWorkflow, generatePackageJson, fixPackageJson);
        }
    }

    public static class PreparationResult {
        private final boolean githubWorkflowExists, packageJsonExists, packageJsonIsValid;
        private final PackageJsonValidationResult packageJsonValidationResult;

        private final JSONObject proposedPackageJsonContent;

        private PreparationResult(
                boolean githubWorkflowExists,
                boolean packageJsonExists,
                boolean packageJsonIsValid,
                PackageJsonValidationResult packageJsonValidationResult,
                JSONObject proposedPackageJsonContent
        ) {
            this.githubWorkflowExists = githubWorkflowExists;
            this.packageJsonExists = packageJsonExists;
            this.packageJsonIsValid = packageJsonIsValid;
            this.packageJsonValidationResult = packageJsonValidationResult;
            this.proposedPackageJsonContent = proposedPackageJsonContent;
        }

        public boolean isGithubWorkflowExists() {
            return githubWorkflowExists;
        }

        public boolean isPackageJsonExists() {
            return packageJsonExists;
        }

        public boolean isPackageJsonIsValid() {
            return packageJsonIsValid;
        }

        public PackageJsonValidationResult getPackageJsonValidationResult() {
            return packageJsonValidationResult;
        }

        public JSONObject getProposedPackageJsonContent() {
            return proposedPackageJsonContent;
        }

    }

    private static class PreparationResultBuilder {
        private boolean githubWorkflowExists, packageJsonExists, packageJsonIsValid;

        private PackageJsonValidationResult packageJsonValidationResult;

        private JSONObject proposedPackageJsonContent;

        public PreparationResultBuilder setGithubWorkflowExists(boolean githubWorkflowExists) {
            this.githubWorkflowExists = githubWorkflowExists;
            return this;
        }

        public PreparationResultBuilder setPackageJsonExists(boolean packageJsonExists) {
            this.packageJsonExists = packageJsonExists;
            return this;
        }

        public PreparationResultBuilder setPackageJsonIsValid(boolean packageJsonIsValid) {
            this.packageJsonIsValid = packageJsonIsValid;
            return this;
        }

        public PreparationResultBuilder setPackageJsonValidationResult(PackageJsonValidationResult packageJsonValidationResult) {
            this.packageJsonValidationResult = packageJsonValidationResult;
            return this;
        }

        public PreparationResultBuilder setProposedPackageJsonContent(JSONObject proposedPackageJsonContent) {
            this.proposedPackageJsonContent = proposedPackageJsonContent;
            return this;
        }

        public PreparationResult build() {
            return new PreparationResult(
                    githubWorkflowExists,
                    packageJsonExists,
                    packageJsonIsValid,
                    packageJsonValidationResult,
                    proposedPackageJsonContent
            );
        }
    }

    public static class InitializeProjectResult {
        private final String[] messages;

        private final File packageJsonFile;

        private final File githubWorkflowFile;

        public InitializeProjectResult(String[] messages, File packageJsonFile, File githubWorkflowFile) {
            this.messages = messages;
            this.packageJsonFile = packageJsonFile;
            this.githubWorkflowFile = githubWorkflowFile;
        }

        public String[] getMessages() {
            return messages;
        }

        public File getPackageJsonFile() {
            return packageJsonFile;
        }

        public File getGithubWorkflowFile() {
            return githubWorkflowFile;
        }
    }

    public static class InitializeProjectResultBuilder {
        private List<String> messages = new ArrayList<String>();
        private File packageJsonFile;

        private File githubWorkflowFile;

        private boolean success;

        public InitializeProjectResultBuilder setMessages(String[] messages) {
            this.messages = new ArrayList<String>(Arrays.asList(messages));
            return this;
        }

        public InitializeProjectResultBuilder addMessage(String message) {
            if (messages == null) {
                messages = new ArrayList<String>();
            }
            messages.add(message);
            return this;
        }

        public InitializeProjectResultBuilder setPackageJsonFile(File packageJsonFile) {
            this.packageJsonFile = packageJsonFile;
            return this;
        }

        public InitializeProjectResultBuilder setGithubWorkflowFile(File githubWorkflowFile) {
            this.githubWorkflowFile = githubWorkflowFile;
            return this;
        }

        public InitializeProjectResultBuilder setSuccess(boolean success) {
            this.success = success;
            return this;
        }

        public InitializeProjectResult createInitializePackageJsonResult() {
            return new InitializeProjectResult(
                    messages.toArray(new String[0]),
                    packageJsonFile,
                    githubWorkflowFile
            );
        }
    }

    private static class ContextWithResult extends Context {
        private final InitializeProjectResultBuilder resultBuilder;

        private ContextWithResult(Context context, InitializeProjectResultBuilder resultBuilder) {
            super(
                    context.getDirectory(),
                    context.isGenerateGithubWorkflow(),
                    context.isGeneratePackageJson(),
                    context.isFixPackageJson()
            );
            this.resultBuilder = resultBuilder;
        }
    }

    private InitializeProjectResultBuilder getResultBuilder(Context context) {
        if (context instanceof ContextWithResult) {
            return ((ContextWithResult) context).resultBuilder;
        }
        throw new IllegalArgumentException("Context is not a ContextWithResult");
    }

}
