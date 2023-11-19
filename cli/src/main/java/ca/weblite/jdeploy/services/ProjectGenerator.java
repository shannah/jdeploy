package ca.weblite.jdeploy.services;

import java.io.File;
import java.io.IOException;

import ca.weblite.jdeploy.builders.GitHubRepositoryInitializationRequestBuilder;
import ca.weblite.jdeploy.dtos.ProjectGeneratorRequest;
import ca.weblite.jdeploy.helpers.StringUtils;
import ca.weblite.jdeploy.helpers.filemergers.ProjectDirectoryExtensionMerger;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import static ca.weblite.jdeploy.cli.util.JavaVersionHelper.getJavaVersion;

@Singleton
public class ProjectGenerator {

    private final StringUtils stringUtils;

    private final ProjectTemplateCatalog projectTemplateCatalog;

    private final ProjectDirectoryExtensionMerger directoryMerger;

    private final GitHubRepositoryInitializer gitHubRepositoryInitializer;

    private final MavenWrapperInjector mavenWrapperInjector;

    @Inject
    public ProjectGenerator(
            StringUtils stringUtils,
            ProjectTemplateCatalog projectTemplateCatalog,
            ProjectDirectoryExtensionMerger directoryMerger,
            GitHubRepositoryInitializer gitHubRepositoryInitializer,
            MavenWrapperInjector mavenWrapperInjector
    ) {
        this.stringUtils = stringUtils;
        this.projectTemplateCatalog = projectTemplateCatalog;
        this.directoryMerger = directoryMerger;
        this.gitHubRepositoryInitializer = gitHubRepositoryInitializer;
        this.mavenWrapperInjector = mavenWrapperInjector;
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
        buildFileSource = buildFileSource.replace("{{ packageName }}", request.getPackageName());
        buildFileSource = buildFileSource.replace("{{ packagePath }}", request.getPackageName().replace(".", "/"));
        buildFileSource = buildFileSource.replace("{{ javaVersion }}", String.valueOf(getJavaVersion()));
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
                if ( name.endsWith(".java") || name.endsWith(".properties") || name.endsWith(".xml") || name.endsWith(".gradle") || name.endsWith(".json") ) {
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

        GitHubRepositoryInitializationRequestBuilder requestBuilder = new GitHubRepositoryInitializationRequestBuilder();
        requestBuilder.setRepoName(request.getGithubRepository())
                .setProjectPath(projectDirectory.getAbsolutePath());

        gitHubRepositoryInitializer.initAndPublish(requestBuilder.build());

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
}

