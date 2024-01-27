package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.cheerpj.services.BuildCheerpjAppService;
import net.coobird.thumbnailator.Thumbnails;
import net.sf.image4j.codec.ico.ICOEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CheerpjService extends BaseService {
    private BuildCheerpjAppService buildCheerpjAppService;

    private static final String DEFAULT_CHEERPJ_LOADER = "https://cjrtnc.leaningtech.com/3.0rc2/cj3loader.js";

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
                        .setCheerpjLoader(getCheerpjLoader(DEFAULT_CHEERPJ_LOADER))
                        .setAppName(getAppName())
                        .setAppJar(mainJar)
                        .setOutputDir(dest)
        );
        copyIconToDirectory(dest);
        createFavicon(new File(dest, "icon.png"), new File(dest, "favicon.ico"));
        createManifest(new File(dest, "manifest.json"));
        if (isGithubPagesEnabled()) {
            try {
                publishToGithubPages();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }
    }

    private File createFavicon(File srcPng, File destIco) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        List<Integer> bppList = new ArrayList<>();
        for (int i : new int[]{16, 24, 32, 48, 64, 128, 256, 512}) {
            BufferedImage img = Thumbnails.of(srcPng).size(i, i).asBufferedImage();
            images.add(img);
            bppList.add(32);
            if (i <= 48) {
                images.add(img);
                bppList.add(8);
                images.add(img);
                bppList.add(4);
            }
        }
        int[] bppArray = bppList.stream().mapToInt(i->i).toArray();
        try (FileOutputStream fileOutputStream = new FileOutputStream(destIco)) {
            ICOEncoder.write(images,bppArray, fileOutputStream);
        }
        return destIco;

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

    private JSONObject getCheerpjObject() {
        if (!isEnabled()) {
            return new JSONObject();
        }
        return getJDeployObject().getJSONObject("cheerpj");
    }

    private String getCheerpjLoader(String defaultValue) {
        if (!getCheerpjObject().has("loader")) {
            return defaultValue;
        }
        return getCheerpjObject().getString("loader");
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

    private void createManifest(File destFile) {
        JSONObject manifest = new JSONObject();
        manifest.put("name", getAppName());
        manifest.put("short_name", getAppName());
        manifest.put("start_url", ".");
        manifest.put("display", "standalone");
        manifest.put("background_color", "#fff");
        manifest.put("description", getDescription());
        JSONArray icons = new JSONArray();
        JSONObject icon512 = new JSONObject();
        icon512.put("src", "icon.png");
        icon512.put("sizes", "512x512");
        icon512.put("type", "image/png");
        icons.put(0, icon512);
        manifest.put("icons", icons);
        try (FileWriter file = new FileWriter(destFile)) {
            file.write(manifest.toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
