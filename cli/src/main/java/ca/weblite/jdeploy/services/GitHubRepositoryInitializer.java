package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.dtos.GithubRepositoryDto;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONObject;

import java.io.*;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;


public class GitHubRepositoryInitializer extends BaseService {

    private static final String GITHUB_API_URL = "https://api.github.com/user/repos";

    private GithubTokenService githubTokenService;

    private GitHubUsernameService gitHubUsernameService;

    public GitHubRepositoryInitializer(
            File packageJSONFile,
            JSONObject packageJSON,
            GithubTokenService githubTokenService,
            GitHubUsernameService gitHubUsernameService) throws IOException {
        super(packageJSONFile, packageJSON);
        this.githubTokenService = githubTokenService;
        this.gitHubUsernameService = gitHubUsernameService;
    }

    public static class Params {
        @CommandLineParser.Help("The name of the repository to create")
        @CommandLineParser.PositionalArg(1)
        @CommandLineParser.Alias("n")
        String repoName;

        @CommandLineParser.Help("Whether the repository should be private")
        @CommandLineParser.Alias("p")
        boolean isPrivate;

        public Params setRepoName(String repoName) {
            this.repoName = repoName;
            return this;
        }

        public Params setPrivate(boolean aPrivate) {
            isPrivate = aPrivate;

            return this;
        }
    }

    public void initAndPublish(Params params) throws GitAPIException, URISyntaxException, IOException, InterruptedException {
        GithubRepositoryDto githubRepositoryDto = new GithubRepositoryDto(params.repoName, params.isPrivate);
        createGitHubRepository(githubRepositoryDto);
        setupAndPushRemote(githubRepositoryDto);
    }

    public void createGitHubRepository(GithubRepositoryDto githubRepositoryDto) throws IOException {
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
            String json = "{\"name\": \"" + githubRepositoryDto.getRepositoryName() + "\", \"private\": " + githubRepositoryDto.isPrivate() + "}";
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Check the response status
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                System.out.println("Repository '" + githubRepositoryDto.getFullRepositoryName() + "' created successfully.");
            } else {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorMessage = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorMessage.append(line);
                    }
                    throw new IOException("Failed to create the repository. Response code: " + responseCode + ", Error message: " + errorMessage.toString());
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private File getGitDirectory() {
        return new File(packageJSONFile.getParentFile(), ".git");
    }

    private void setupAndPushRemote(GithubRepositoryDto githubRepositoryDto) throws GitAPIException, URISyntaxException, IOException {
        String repoName = githubRepositoryDto.getFullRepositoryName();

        // Initialize the current directory as a Git repository (if not already)

        File localPath = packageJSONFile.getParentFile();
        Repository repository = Git.init().setDirectory(localPath).call().getRepository();

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

        Git git = new Git(repository);

        // Add files to the staging area
        AddCommand add = git.add();
        add.addFilepattern(".").call(); // Add all files in the current directory

        // Commit the changes
        CommitCommand commit = git.commit();
        commit.setMessage("Commit message goes here");
        RevCommit revCommit = commit.call();

        // Push the repository to the origin

        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubTokenService.getToken(), "");
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
}
