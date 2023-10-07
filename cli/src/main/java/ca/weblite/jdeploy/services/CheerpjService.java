package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.cheerpj.services.BuildCheerpjAppService;
import org.json.JSONObject;
import java.io.*;

public class CheerpjService extends BaseService {
    private BuildCheerpjAppService buildCheerpjAppService;

    public CheerpjService(File packageJSONFile, JSONObject packageJSON) throws IOException {
        super(packageJSONFile, packageJSON);
        buildCheerpjAppService = new BuildCheerpjAppService();
    }

    public boolean isEnabled() {
        return getJDeployObject().has("cheerpj");
    }

    protected File getDestDirectory() throws IOException {
        File dest = new File(super.getDestDirectory(), "cheerpj");
        dest.mkdirs();
        return dest;
    }

    private void run() throws IOException {
        File mainJar = this.getMainJarFile();
        File dest = this.getDestDirectory();
        buildCheerpjAppService.build(
                new BuildCheerpjAppService.Params()
                        .setAppName(getAppName())
                        .setAppJar(mainJar)
                        .setOutputDir(dest)
        );
        copyIconToDirectory(dest);
        if (isGithubPagesEnabled()) {
            try {
                publishToGithubPages();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }
    }

    public void execute() throws IOException {
        this.run();
    }

    private JSONObject getGithubPagesConfig() {
        JSONObject config = new JSONObject();
        config.put("enabled", false);
        if (getJDeployObject().has("cheerpj")) {
            JSONObject cheerpj = getJDeployObject().getJSONObject("cheerpj");
            if (cheerpj.has("githubPages")) {
                JSONObject githubPages = cheerpj.getJSONObject("githubPages");
                if (githubPages.has("enabled")) {
                    config.put("enabled", githubPages.getBoolean("enabled"));
                }
                if (githubPages.has("branch")) {
                    config.put("branch", githubPages.getString("branch"));
                }
                if (githubPages.has("tagPath")) {
                    // THe path where to publish in gh-pages for tags
                    config.put("tagPath", githubPages.getString("tagPath"));
                }
                if (githubPages.has("branchPath")) {
                    // The path where to publish in gh-pages for branches
                    config.put("branchPath", githubPages.getString("branchPath"));
                }
                if (githubPages.has("path")) {
                    // The path where to publish in gh-pages for branches
                    config.put("path", githubPages.getString("path"));
                }
            }
        }

        return config;
    }

    private String getCurrentTag() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("git", "describe", "--tags", "--exact-match");
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String tag = reader.readLine();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            return null;
        }

        return tag;
    }

    private String getGithubPagesTagPath(String tagName) {
        String path = null;
        if (getGithubPagesConfig().has("tagPath")) {
            path = getGithubPagesConfig().getString("tagPath");
        } else if (getGithubPagesConfig().has("path")) {
            path = getGithubPagesConfig().getString("path");
        }
        if (path == null) {
            return path;
        }

        return path.replace("{{ tag }}", tagName);
    }

    private String getGithubPagesBranchPath(String branchName) {
        String path = null;
        if (getGithubPagesConfig().has("branchPath")) {
            path = getGithubPagesConfig().getString("branchPath");
        } else if (getGithubPagesConfig().has("path")) {
            path = getGithubPagesConfig().getString("path");
        }

        if (path == null) {
            return path;
        }

        return path.replace("{{ branch }}", branchName);
    }

    private String getGithubPagesPublishPath() throws IOException, InterruptedException {
        String tag = null;
        try {
            tag = getCurrentTag();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (tag != null) {
            return getGithubPagesTagPath(tag);
        } else {
            return getGithubPagesBranchPath(getCurrentGitBranch());
        }
    }

    public boolean isGithubPagesEnabled() {
        if (!getGithubPagesConfig().has("enabled") || "true".equals(System.getenv("JDEPLOY_CHEERPJ_SKIP_DEPLOY"))) {
            return false;
        }
        return getGithubPagesConfig().getBoolean("enabled");
    }

    private String getGithubPagesBranch() {
        if (!getGithubPagesConfig().has("branch")) {
            return null;
        }
        return getGithubPagesConfig().getString("branch");
    }

    private String getCurrentGitBranch() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("git", "rev-parse", "--abbrev-ref", "HEAD");
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String branchName = reader.readLine();
        int exitCode = process.waitFor();
        reader.close();

        if (exitCode != 0) {
            throw new IOException("Error while getting current git branch.");
        }

        return branchName;
    }

    public static void generateGitignoreFile(String directoryPath) throws IOException {
        File gitignore = new File(directoryPath, ".gitignore");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(gitignore))) {
            // Ignore all files
            writer.write("*\n");
            // Do not ignore .jar and .html files
            writer.write("!*.jar\n");
            writer.write("!*.html\n");
            // Do not ignore directories, so we can traverse them
            writer.write("\n");
        }
    }

    private void publishToGithubPages() throws IOException, InterruptedException {
        if (getCurrentGitBranch() != null && getCurrentGitBranch().equals(getGithubPagesBranch())) {
            System.out.println("Cannot publish to github pages from the same branch");
            return;
        }
        GithubPagesPublisher publisher = new GithubPagesPublisher();
        //generateGitignoreFile(getDestDirectory().getAbsolutePath());
        System.out.println("Publishing to github pages");
        publisher.publishToGithubPages(getDestDirectory(), null, getGithubPagesBranch(), getGithubPagesPublishPath());

    }


}
