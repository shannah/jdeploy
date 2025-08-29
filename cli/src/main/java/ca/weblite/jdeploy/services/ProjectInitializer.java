package ca.weblite.jdeploy.services;

import com.codename1.io.JSONParser;
import com.codename1.processing.Result;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

@Singleton
public class ProjectInitializer {

    private final ProjectJarFinder projectJarFinder;

    @Inject
    public ProjectInitializer(ProjectJarFinder projectJarFinder) {
        this.projectJarFinder = projectJarFinder;
    }
    public static class Request {
        public final String projectDirectory;
        public final String jarFilePath;


        public final boolean dryRun;

        public final boolean generateGithubWorkflow;

        public final Delegate delegate;

        public Request(
                String projectDirectory,
                String jarFilePath,
                boolean dryRun,
                boolean generateGithubWorkflow,
                Delegate delegate
        ) {
            this.projectDirectory = projectDirectory;
            this.jarFilePath = jarFilePath;
            this.dryRun = dryRun;
            this.generateGithubWorkflow = generateGithubWorkflow;
            this.delegate = delegate;

        }
    }

    public static class Response {
        public final String packageJsonContents;

        public final String projectDirectory;
        public final String jarFilePath;

        public final boolean generatedGithubWorkflow;

        public final boolean githubWorkflowExists;

        public Response(
                String packageJsonContents,
                String projectDirectory,
                String jarFilePath,
                boolean generatedGithubWorkflow,
                boolean githubWorkflowExists
        ) {
            this.packageJsonContents = packageJsonContents;
            this.projectDirectory = projectDirectory;
            this.jarFilePath = jarFilePath;
            this.generatedGithubWorkflow = generatedGithubWorkflow;
            this.githubWorkflowExists = githubWorkflowExists;
        }
    }

    public interface Delegate {
        void onBeforeWritePackageJson(String path, String contents);
        void onAfterWritePackageJson(String path, String contents);

        void onBeforeWriteGithubWorkflow(String path);

        void onAfterWriteGithubWorkflow(String path);
    }

    public static class ValidationFailedException extends Exception {
        public ValidationFailedException(String message) {
            super(message);
        }
    }


    public Response decorate(Request request)
            throws ValidationFailedException, IOException {
        validate(request);
        return init(
                new File(request.projectDirectory),
                request.dryRun,
                request.generateGithubWorkflow,
                request.delegate,
                request.jarFilePath
        );
    }

    private void validate(Request request)
            throws ValidationFailedException {
        if (!packageJsonExists(request.projectDirectory)) {
            return;
        }
        JSONObject packageJson;
        try {
            packageJson = readPackageJson(request.projectDirectory);
        } catch (IOException e) {
            throw new ValidationFailedException("package.json file exists, but failed to parse");
        }

        if (packageJson.has("jdeploy")) {
            throw new ValidationFailedException("package.json already contains jdeploy configuration");
        }

    }

    private boolean packageJsonExists(String projectDirectory) {
        return getPackageJsonFile(projectDirectory).exists();
    }

    private JSONObject readPackageJson(String projectDirectory) throws IOException {
        return parseJsonFileToObject(getPackageJsonFile(projectDirectory));
    }

    private File getPackageJsonFile(String projectDirectory) {
        return new File(projectDirectory, "package.json");
    }
    private JSONObject parseJsonFileToObject(File file) throws IOException {
        // Use try-with-resources to ensure FileReader is closed automatically
        try (FileReader reader = new FileReader(file)) {
            // Create a JSONTokener from the file reader
            JSONTokener tokener = new JSONTokener(reader);
            // Parse the tokener into a JSONObject
            return new JSONObject(tokener);
        }
    }

    private Response init(
            File directory,
            boolean dryRun,
            boolean generateGithubWorkflow,
            Delegate delegate,
            String jarFilePath
    ) throws IOException {
        final int javaVersionInt = new JavaVersionExtractor().extractJavaVersionFromSystemProperties(11);

        String commandName = directory.getAbsoluteFile().getName().toLowerCase();
        if (".".equals(commandName)) {
            commandName = directory.getAbsoluteFile().getParentFile().getName().toLowerCase();
        }
        File packageJson = new File(directory, "package.json");
        if (packageJson.exists()) {
            return updatePackageJson(directory, commandName, dryRun, delegate, jarFilePath);
        }
        File candidate = jarFilePath == null
                ? projectJarFinder.findBestCandidate(directory)
                : new File(jarFilePath);
        if (candidate != null) {
            commandName = candidate.getName();
            if (commandName.endsWith(".jar") || commandName.endsWith(".war")) {
                commandName = commandName.substring(0, commandName.lastIndexOf("."));
            }
            if (commandName.endsWith("-SNAPSHOT")) {
                commandName = commandName.substring(0, commandName.lastIndexOf("-SNAPSHOT"));
            }
            // Strip version suffixes like -1.0.0, 1.0, 1.0-rc1, etc..
            commandName = commandName.replaceAll("-[0-9]+(\\.[0-9]+)*(-[a-zA-Z0-9]+)?$", "");

            commandName = commandName.replaceAll("[^a-zA-Z0-9_\\-]", "-");
        }

        Map m = new HashMap(); // for package.json
        m.put("name", commandName);
        m.put("version", "1.0.0");
        m.put("repository", "");
        m.put("description", "");
        m.put("main", "index.js");
        Map bin = new HashMap();
        bin.put(commandName, getBinDir()+"/jdeploy.js");
        m.put("bin", bin);
        m.put("preferGlobal", true);
        m.put("author", "");

        Map scripts = new HashMap();
        scripts.put("test", "echo \"Error: no test specified\" && exit 1");


        m.put("scripts", scripts);
        m.put("license", "ISC");

        Map dependencies = new HashMap();
        dependencies.put("shelljs", "^0.8.4");
        dependencies.put("command-exists-promise", "^2.0.2");
        dependencies.put("node-fetch", "2.6.7");
        dependencies.put("tar", "^4.4.8");
        dependencies.put("yauzl", "^2.10.0");
        m.put("dependencies", dependencies);

        List files = new ArrayList();
        files.add("jdeploy-bundle");

        m.put("files", files);

        Map jdeploy = new HashMap();
        if (candidate == null) {
        } else if (candidate.getName().endsWith(".jar")) {
            jdeploy.put("jar", getRelativePath(directory, candidate));
        } else {
            jdeploy.put("war", getRelativePath(directory, candidate));
        }

        jdeploy.put("javaVersion", String.valueOf(javaVersionInt));
        jdeploy.put("javafx", false);
        jdeploy.put("jdk", false);

        // Necessary to avoid duplicate x64 bundles on Windows and Linux without the architecture suffix.
        // Default is true to not break legacy automations, but in the future we will change the default.
        // For now, just make sure that new projects have this set to false, since they don't have legacy bundles to worry about.
        jdeploy.put("generateLegacyBundles", false);

        String title = toTitleCase(commandName);

        jdeploy.put("title", title);

        m.put("jdeploy", jdeploy);

        final Result res = Result.fromContent(m);
        final String jsonStr = res.toString();
        final GithubWorkflowGenerator githubWorkflowGenerator = new GithubWorkflowGenerator(directory);
        boolean githubWorkflowExists = githubWorkflowGenerator.getGithubWorkflowFile().exists();
        if (!dryRun) {
            if (delegate != null) {
                delegate.onBeforeWritePackageJson(packageJson.getAbsolutePath(), jsonStr);
            }
            FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
            if (delegate != null) {
                delegate.onAfterWritePackageJson(packageJson.getAbsolutePath(), jsonStr);
            }
            if (generateGithubWorkflow) {

                if (!githubWorkflowExists) {
                    if (delegate != null) {
                        delegate.onBeforeWriteGithubWorkflow(githubWorkflowGenerator.getGithubWorkflowFile().getAbsolutePath());
                    }
                    githubWorkflowGenerator.generateGithubWorkflow(javaVersionInt, "master");
                    if (delegate != null) {
                        delegate.onAfterWriteGithubWorkflow(githubWorkflowGenerator.getGithubWorkflowFile().getAbsolutePath());
                    }
                }
            }
        }

        return new Response(
                jsonStr,
                directory.getAbsolutePath(),
                candidate != null ? candidate.getAbsolutePath() : null,
                generateGithubWorkflow,
                githubWorkflowExists
        );
    }

    private Response updatePackageJson(
            File directory,
            String commandName,
            boolean dryRun,
            Delegate delegate,
            String jarFilePath
    ) throws IOException {
        File candidate = jarFilePath == null
                ? projectJarFinder.findBestCandidate(directory)
                : new File(jarFilePath);
        if (commandName == null) {
            if (candidate == null) {

            } else if (candidate.getName().endsWith(".jar") || candidate.getName().endsWith(".war")) {
                commandName = candidate.getName().substring(0, candidate.getName().lastIndexOf(".")).toLowerCase();
            } else {
                commandName = candidate.getName().toLowerCase();
            }
        }
        File packageJson = new File(directory, "package.json");
        JSONParser p = new JSONParser();
        String str = FileUtils.readFileToString(packageJson, "UTF-8");
        Map pj = (Map)p.parseJSON(new StringReader(str));
        if (!pj.containsKey("bin")) {
            pj.put("bin", new HashMap());
        }
        Map bin = (Map)pj.get("bin");
        if (bin.isEmpty()) {
            bin.put(commandName, getBinDir() + "/jdeploy.js");
        }

        if (!pj.containsKey("dependencies")) {
            pj.put("dependencies", new HashMap());
        }
        Map deps = (Map)pj.get("dependencies");
        deps.put("shelljs", "^0.8.4");
        deps.put("command-exists-promise", "^2.0.2");
        deps.put("node-fetch", "2.6.7");
        deps.put("tar", "^4.4.8");
        deps.put("yauzl", "^2.10.0");

        if (!pj.containsKey("jdeploy")) {
            pj.put("jdeploy", new HashMap());
        }
        Map jdeploy = (Map)pj.get("jdeploy");
        if (candidate != null && !jdeploy.containsKey("war") && !jdeploy.containsKey("jar")) {
            if (candidate.getName().endsWith(".jar")) {
                jdeploy.put("jar", getRelativePath(directory, candidate));
            } else {
                jdeploy.put("war", getRelativePath(directory, candidate));
            }
        }

        String jsonStr = Result.fromContent(pj).toString();
        if (!dryRun) {
            if (delegate != null) {
                delegate.onBeforeWritePackageJson(packageJson.getAbsolutePath(), jsonStr);
            }
            FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
            if (delegate != null) {
                delegate.onAfterWritePackageJson(packageJson.getAbsolutePath(), jsonStr);
            }
        }

        final GithubWorkflowGenerator githubWorkflowGenerator = new GithubWorkflowGenerator(directory);
        boolean githubWorkflowExists = githubWorkflowGenerator.getGithubWorkflowFile().exists();
        return new Response(
                jsonStr,
                directory.getAbsolutePath(),
                candidate != null ? candidate.getAbsolutePath() : null,
                false,
                githubWorkflowExists

        );

    }

    public String getBinDir() {
        return "jdeploy-bundle";
    }

    private String getRelativePath(File directory, File f) {

        return directory.toURI().relativize(f.toURI()).getPath();
    }

    private static String toTitleCase(String str) {
        StringBuilder out = new StringBuilder();
        char[] chars = str.toCharArray();
        boolean nextUpper = true;
        for (int i=0; i<chars.length; i++) {
            char c = chars[i];
            if (c == '_' || c == '-') {
                out.append(" ");
                nextUpper = true;
            } else if (nextUpper) {
                out.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                out.append(c);
                nextUpper = false;
            }
        }
        return out.toString();
    }
}
