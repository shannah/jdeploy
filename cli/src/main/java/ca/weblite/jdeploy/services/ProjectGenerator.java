package ca.weblite.jdeploy.services;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Scanner;

import ca.weblite.jdeploy.builders.GitHubRepositoryInitializationRequestBuilder;
import ca.weblite.jdeploy.dtos.ProjectGeneratorRequest;
import ca.weblite.jdeploy.helpers.StringUtils;
import ca.weblite.jdeploy.helpers.filemergers.ProjectDirectoryExtensionMerger;
import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.inject.Inject;
import javax.inject.Singleton;

import static ca.weblite.jdeploy.cli.util.JavaVersionHelper.getJavaVersion;

@Singleton
public class ProjectGenerator {

    private static final String JDEPLOY_TOKEN_SECRET_NAME = "JDEPLOY_RELEASES_TOKEN";

    private static final String GITHUB_URL = "https://github.com/";

    private final StringUtils stringUtils;

    private final ProjectTemplateCatalog projectTemplateCatalog;

    private final ProjectDirectoryExtensionMerger directoryMerger;

    private final GitHubRepositoryInitializer gitHubRepositoryInitializer;

    private final MavenWrapperInjector mavenWrapperInjector;

    private final GithubSecretSetter githubSecretSetter;

    private final GithubTokenService githubTokenService;

    @Inject
    public ProjectGenerator(
            StringUtils stringUtils,
            ProjectTemplateCatalog projectTemplateCatalog,
            ProjectDirectoryExtensionMerger directoryMerger,
            GitHubRepositoryInitializer gitHubRepositoryInitializer,
            MavenWrapperInjector mavenWrapperInjector,
            GithubSecretSetter githubSecretSetter,
            GithubTokenService githubTokenService
    ) {
        this.stringUtils = stringUtils;
        this.projectTemplateCatalog = projectTemplateCatalog;
        this.directoryMerger = directoryMerger;
        this.gitHubRepositoryInitializer = gitHubRepositoryInitializer;
        this.mavenWrapperInjector = mavenWrapperInjector;
        this.githubSecretSetter = githubSecretSetter;
        this.githubTokenService = githubTokenService;
    }


    public File generate(ProjectGeneratorRequest request) throws Exception {
        File projectDir = new File(
                request.getParentDirectory(),
                stringUtils.camelCaseToLowerCaseWithSeparator(request.getProjectName(), "-")
        );
        if ( projectDir.exists() ) {
            throw new Exception("Project directory already exists: " + projectDir.getAbsolutePath());
        }
        projectDir.mkdirs();
        if (!projectDir.exists()) {
            throw new IOException("Failed to create project directory: " + projectDir.getAbsolutePath());
        }

        String templateDirectory = request.getTemplateDirectory();
        if (templateDirectory == null && request.getTemplateName() != null) {
            projectTemplateCatalog.update();
            templateDirectory = projectTemplateCatalog.getProjectTemplate(request.getTemplateName()).getAbsolutePath();
        }
        if (templateDirectory == null) {
            throw new Exception("Template directory is not set");
        }

        File templateDir = new File(templateDirectory);
        if ( !templateDir.exists() ) {
            throw new Exception("Template directory does not exist: " + templateDir.getAbsolutePath());
        }
        File[] files = templateDir.listFiles();
        if (files == null) {
            throw new IOException("Failed to get files in template directory: " + templateDir.getAbsolutePath());
        }
        for ( File file : files) {
            if ( file.isDirectory() ) {
                FileUtils.copyDirectory(file, new File(projectDir, file.getName()));
            } else {
                FileUtils.copyFileToDirectory(file, projectDir);
            }
        }
        if (request.getExtensions() != null) {
            for (String extension : request.getExtensions()) {
                File extensionDir = projectTemplateCatalog.getExtensionTemplate(extension);
                applyExtensionToProject(projectDir, extensionDir);

            }
        }

        updateFilesInDirectory(projectDir, request);

        if (mavenWrapperInjector.isMavenProject(projectDir.getPath())) {
            mavenWrapperInjector.installIntoProject(projectDir.getPath());
        }

        initializeAndPushGitRepository(projectDir, request);
        return projectDir;

    }

    private String updateApplicationProperties(String buildFileSource, ProjectGeneratorRequest request) {
        buildFileSource = buildFileSource.replace("{{ appName }}", request.getProjectName());
        buildFileSource = buildFileSource.replace("{{ appTitle }}", request.getAppTitle());
        buildFileSource = buildFileSource.replace("{{ groupId }}", request.getGroupId());
        buildFileSource = buildFileSource.replace("{{ artifactId }}", request.getArtifactId());
        buildFileSource = buildFileSource.replace("{{ mainClass }}", request.getMainClassName());
        buildFileSource = buildFileSource.replace("{{ mainClassName }}", request.getMainClassName());
        buildFileSource = buildFileSource.replace("{{ packageName }}", request.getPackageName());
        buildFileSource = buildFileSource.replace("{{ packagePath }}", request.getPackageName().replace(".", "/"));
        buildFileSource = buildFileSource.replace("{{ javaVersion }}", String.valueOf(getJavaVersion()));
        buildFileSource = buildFileSource.replace("{{ githubRepository }}", String.valueOf(request.getGithubRepository()));
        buildFileSource = buildFileSource.replace("{{ githubReleasesRepository }}", getReleasesRepository(request));
        buildFileSource = buildFileSource.replace("{{ releasesUrl }}", getReleasesUrl(request));
        return buildFileSource;
    }

    private File updateFilesInDirectory(File dir, ProjectGeneratorRequest request) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) {
            throw new IOException("Failed to get files in directory: " + dir.getAbsolutePath());
        }
        for ( File file : files) {
            if ( file.isDirectory() ) {
                updateFilesInDirectory(file, request);
            } else {
                String name = file.getName();
                String[] processExtensions = new String[]{
                        ".java",
                        ".properties",
                        ".xml",
                        ".gradle",
                        ".json",
                        ".yml",
                        ".yaml",
                        ".md",
                        ".adoc"
                };
                boolean shouldProcessFile = Arrays.asList(processExtensions).stream().filter(ext -> name.endsWith(ext)).count() > 0;
                if (shouldProcessFile) {
                    String source = FileUtils.readFileToString(file, "UTF-8");
                    String updated = updateApplicationProperties(source, request);
                    if ( !updated.equals(source) ) {
                        FileUtils.writeStringToFile(file, updated, "UTF-8");
                    }
                }
            }
            moveFile(file, request);
        }
        return dir;
    }


    private File initializeAndPushGitRepository(
            File projectDirectory,
            ProjectGeneratorRequest request
    ) throws Exception {
        if (request.getGithubRepository() == null) {
            return projectDirectory;
        }
        GitHubRepositoryInitializationRequestBuilder requestBuilder;
        if (request.isPrivateRepository()) {
            generateReleasesProject(projectDirectory, request);
        }

        requestBuilder = new GitHubRepositoryInitializationRequestBuilder();
        requestBuilder.setRepoName(request.getGithubRepository())
                .setProjectPath(projectDirectory.getAbsolutePath())
                .setPrivate(request.isPrivateRepository());
        gitHubRepositoryInitializer.createGitHubRepository(requestBuilder.build());

        if (request.isPrivateRepository()) {
            addSecretToWorkflow(request);
        }

        gitHubRepositoryInitializer.setupAndPushRemote(requestBuilder.build());


        return projectDirectory;
    }

    private File moveFile(File file, ProjectGeneratorRequest request) throws Exception {
        String name = updateApplicationProperties(file.getName(), request);
        if (!file.getName().equals(name)) {
            File destFile = new File(file.getParentFile(), name);
            if (file.isDirectory()) {
                FileUtils.moveDirectory(file, destFile);
            } else {
                FileUtils.moveFile(file, destFile);
            }

            return destFile;
        }

        return file;
    }

    private void applyExtensionToProject(
            File projectDirectory,
            File extensionDirectory
    ) throws Exception {
       directoryMerger.merge(projectDirectory, extensionDirectory);
    }

    private void setReleasesRepositoryInWorkflow(File projectDirectory, String releasesRepository) {
        File jdeployYml = new File(
                projectDirectory,
                ".github" + File.separator + "workflows" + File.separator + "jdeploy.yml"
        );
        if (jdeployYml.exists()) {
            try {
                String content = FileUtils.readFileToString(jdeployYml, "UTF-8");
                content = content.replace(
                        "${{ secrets.GITHUB_TOKEN }}",
                        "${{ secrets." + JDEPLOY_TOKEN_SECRET_NAME + " }}"
                );
                String indent = getIndentOfLineWith(content, "github_token:");
                String indentedGithubToken = indent + "github_token:";
                String indentedTargetRepository = indent + "target_repository: '" + releasesRepository + "'\n";

                content = content.replace(
                        indentedGithubToken,
                indentedTargetRepository + indentedGithubToken
                );
                FileUtils.writeStringToFile(jdeployYml, content, "UTF-8");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String getIndentOfLineWith(String haystack, String needle) {
        Scanner scanner = new Scanner(haystack);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains(needle)) {
                return line.substring(0, line.indexOf(needle));
            }
        }
        return "";
    }

    private void setupReleasesProject(
            File releasesProjectDir,
            ProjectGeneratorRequest request
    ) throws IOException {
        String readme = IOUtil.readToString(
                getClass().getResourceAsStream(
                        "/ca/weblite/jdeploy/github/Releases_Readme_Template.md"
                )
        );

        readme = readme.replace("{{ appName }}", request.getProjectName());
        readme = readme.replace("{{ appTitle }}", request.getAppTitle());
        readme = readme.replace("{{ githubRepository }}", request.getGithubRepository());
        readme = readme.replace("{{ githubReleasesRepository }}", getReleasesRepository(request));
        readme = readme.replace("{{ releasesUrl }}", getReleasesUrl(request));
        FileUtils.writeStringToFile(new File(releasesProjectDir, "README.md"), readme, "UTF-8");

    }

    private void initAndPublishReleasesProject(
            String releasesRepository,
            String releasesProjectPath
    ) throws IOException, GitAPIException, URISyntaxException {
        GitHubRepositoryInitializationRequestBuilder requestBuilder =
                new GitHubRepositoryInitializationRequestBuilder();
        requestBuilder.setRepoName(releasesRepository)
                .setProjectPath(releasesProjectPath)
                .setPrivate(false);
        gitHubRepositoryInitializer.initAndPublish(requestBuilder.build());
    }

    private void addSecretToWorkflow(ProjectGeneratorRequest request) throws Exception {
        String owner = request.getGithubRepository().split("/")[0];
        String repo = request.getGithubRepository().split("/")[1];
        githubSecretSetter.setSecret(
                owner,
                repo,
                githubTokenService.getToken(),
                JDEPLOY_TOKEN_SECRET_NAME,
                githubTokenService.getToken()
        );
    }

    private void generateReleasesProject(
            File projectDirectory,
            ProjectGeneratorRequest request
    ) throws Exception {
        String releasesRepository = getReleasesRepository(request);
        String releasesProjectPath = getReleasesProjetPath(projectDirectory, request);
        new File(releasesProjectPath).mkdirs();
        setupReleasesProject(new File(releasesProjectPath), request);
        initAndPublishReleasesProject(releasesRepository, releasesProjectPath);
        setReleasesRepositoryInWorkflow(projectDirectory, releasesRepository);
    }

    private String getReleasesRepository(ProjectGeneratorRequest request) {
        return request.getGithubRepository() + "-releases";
    }

    private String getReleasesProjetPath(File projectDirectory, ProjectGeneratorRequest request) {
        return projectDirectory.getAbsolutePath() + "-releases";
    }

    private String getReleasesUrl(ProjectGeneratorRequest request) {
        if (request.isPrivateRepository()) {
            return GITHUB_URL + getReleasesRepository(request) + "/releases";
        } else {
            return GITHUB_URL + request.getGithubRepository() + "/releases";
        }
    }
}

