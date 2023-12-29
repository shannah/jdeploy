package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.dtos.GitHubRepositoryIntializationRequest;
import ca.weblite.jdeploy.dtos.GithubRepositoryDto;
import ca.weblite.jdeploy.factories.GitHubRepositoryDtoFactory;
import ca.weblite.jdeploy.models.JDeployProject;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.*;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;


@Singleton
public class GitHubRepositoryInitializer {

    private static final String GITHUB_API_URL = "https://api.github.com/user/repos";

    private final GithubTokenService githubTokenService;

    private final GitHubUsernameService gitHubUsernameService;

    private final GitHubRepositoryDtoFactory githubRepositoryDtoFactory;

    private final JDeployProjectCache jDeployProjectCache;

    @Inject
    public GitHubRepositoryInitializer(
            GithubTokenService githubTokenService,
            GitHubUsernameService gitHubUsernameService,
            GitHubRepositoryDtoFactory gitHubRepositoryDtoFactory,
            JDeployProjectCache jDeployProjectCache
    ) {
        this.githubTokenService = githubTokenService;
        this.gitHubUsernameService = gitHubUsernameService;
        this.githubRepositoryDtoFactory = gitHubRepositoryDtoFactory;
        this.jDeployProjectCache = jDeployProjectCache;
    }


    public void initAndPublish(GitHubRepositoryIntializationRequest request)
            throws GitAPIException, URISyntaxException, IOException {
        createGitHubRepository(request);
        setupAndPushRemote(request);
    }

    public void createGitHubRepository(GitHubRepositoryIntializationRequest request) throws IOException {
        GithubRepositoryDto githubRepositoryDto = githubRepositoryDtoFactory.newGithubRepository(
                request.getRepoName(),
                request.isPrivate()
        );
        String githubToken = githubTokenService.getToken();
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // Set up the HTTP POST request
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + githubToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Create a JSON request body with the repository name and visibility (private/public)
            String json = "{\"name\": \"" + githubRepositoryDto.getRepositoryName() + "\", " +
                    "\"private\": " + githubRepositoryDto.isPrivate() + "}";
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check the response status
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                System.out.println(
                        "Repository '" + githubRepositoryDto.getFullRepositoryName() + "' created successfully."
                );
            } else {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))
                ) {
                    StringBuilder errorMessage = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorMessage.append(line);
                    }
                    throw new IOException("Failed to create the repository. " +
                            "Response code: " + responseCode + ", Error message: " + errorMessage);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    public void setupAndPushRemote(GitHubRepositoryIntializationRequest request)
            throws GitAPIException, URISyntaxException, IOException {
        GithubRepositoryDto githubRepositoryDto = githubRepositoryDtoFactory.newGithubRepository(
                request.getRepoName(),
                request.isPrivate()
        );
        File localPath = getLocalPath(request);
        Git git;
        try (Repository repository = Git.init().setDirectory(localPath).call().getRepository()) {

            // Add the GitHub repository as the origin (if not already added)
            String githubRepositoryUrl = "https://github.com/"
                    + githubRepositoryDto.getRepositoryUsername(gitHubUsernameService)
                    + "/" + githubRepositoryDto.getRepositoryName() + ".git";
            boolean originExists = false;

            RemoteConfig remoteConfig = new RemoteConfig(repository.getConfig(), "origin");
            List<URIish> uriList = remoteConfig.getURIs();

            for (URIish uri : uriList) {
                if (uri.toString().equals(githubRepositoryUrl)) {
                    originExists = true;
                    break;
                }
            }

            if (!originExists) {
                remoteConfig.addURI(new URIish(githubRepositoryUrl));
                remoteConfig.update(repository.getConfig());
                repository.getConfig().save();
            }

            git = new Git(repository);
        }

        // Add files to the staging area
        AddCommand add = git.add();
        add.addFilepattern(".").call(); // Add all files in the current directory

        // Commit the changes
        CommitCommand commit = git.commit();
        commit.setMessage("Commit message goes here");
        commit.call();

        // Push the repository to the origin
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(
                githubTokenService.getToken(),
                ""
        );
        Iterable<PushResult> results = git.push()
                .setRemote("origin")
                .setCredentialsProvider(credentialsProvider)
                .setPushAll()
                .call();

        for (PushResult result : results) {
            System.out.println(result.getMessages());
        }

        git.close();

    }

    private File getLocalPath(GitHubRepositoryIntializationRequest request) {
        try {
            JDeployProject project = jDeployProjectCache.findByPath(request.getProjectPath());
            return project.getPackageJSONFile().toFile().getParentFile();
        } catch (IOException e) {
            return new File(request.getProjectPath());
        }
    }
}
