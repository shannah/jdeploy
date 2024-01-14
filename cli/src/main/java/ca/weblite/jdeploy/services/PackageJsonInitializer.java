package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.helpers.FileHelper;
import ca.weblite.jdeploy.helpers.JSONHelper;
import ca.weblite.jdeploy.helpers.StringUtils;
import ca.weblite.jdeploy.records.JarFinderContext;
import ca.weblite.jdeploy.records.PackageJsonInitializerContext;
import ca.weblite.jdeploy.records.PackageJsonValidationResult;
import com.codename1.io.JSONParser;
import com.codename1.processing.Result;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.*;

@Singleton
public class PackageJsonInitializer {

    private final JavaVersionExtractor javaVersionExtractor;

    private final FileHelper fileHelper;

    private final StringUtils stringUtils;

    private final JSONHelper jsonHelper;

    private final PomParser pomParser;

    private final JarFinder jarFinder;

    private final JarFinderContext.Factory jarFinderContextFactory;

    private final PackageJsonValidator packageJsonValidator;

    private final PackageJsonValidationResult.Factory packageJsonValidationResultFactory;

    @Inject
    public PackageJsonInitializer(
            JavaVersionExtractor javaVersionExtractor,
            FileHelper fileHelper,
            StringUtils stringUtils,
            JSONHelper jsonHelper,
            PomParser pomParser,
            JarFinder jarFinder,
            JarFinderContext.Factory jarFinderContextFactory,
            PackageJsonValidator packageJsonValidator,
            PackageJsonValidationResult.Factory packageJsonValidationResultFactory) {
        this.javaVersionExtractor = javaVersionExtractor;
        this.fileHelper = fileHelper;
        this.stringUtils = stringUtils;
        this.jsonHelper = jsonHelper;
        this.pomParser = pomParser;
        this.jarFinder = jarFinder;
        this.jarFinderContextFactory = jarFinderContextFactory;
        this.packageJsonValidator = packageJsonValidator;
        this.packageJsonValidationResultFactory = packageJsonValidationResultFactory;
    }

    public PreparationResult prepare(PackageJsonInitializerContext context) throws IOException {
        boolean packageJsonExists = false, packageJsonIsValid = false;
        PackageJsonValidationResult validationResult = null;

        File packageJsonFile = new File(context.getDirectory(), "package.json");
        packageJsonExists = packageJsonFile.exists();
        JSONObject proposedPackageJsonToWrite = null;
        if (packageJsonExists) {
            try {
                validationResult = packageJsonValidator.validate(packageJsonFile);
                packageJsonIsValid = validationResult.isValid();
            } catch (IOException ex) {
                validationResult = packageJsonValidationResultFactory.fromException(ex);
            }
            proposedPackageJsonToWrite = getPatchedPackageJson(
                    context,
                    jarFinder.findBestCandidate(
                            jarFinderContextFactory.fromPackageJsonInitializerContext(context)
                    )
            );
        } else {
            proposedPackageJsonToWrite = getDefaultPackageJson(
                    context,
                    jarFinder.findBestCandidate(
                            jarFinderContextFactory.fromPackageJsonInitializerContext(context)
                    )
            );
        }


        try {
            validationResult = packageJsonValidator.validate(packageJsonFile);
            packageJsonIsValid = validationResult.isValid();
        } catch (IOException ex) {
            validationResult = packageJsonValidationResultFactory.fromException(ex);
        }

        return new PreparationResult(
                packageJsonExists,
                packageJsonIsValid,
                validationResult,
                proposedPackageJsonToWrite
        );
    }


    public InitializePackageJsonResult initializePackageJson(PackageJsonInitializerContext context) throws IOException {
        context = new ContextWithResult(context, new InitializePackageJsonResultBuilder());
        File directory = context.getDirectory();
        File packageJson = new File(directory, "package.json");
        getResultBuilder(context).setPackageJsonFile(packageJson);
        if (packageJson.exists()) {
            if (context.shouldUpdateExistingPackageJson()) {
                JSONObject packageJsonContent = context.getPackageJSONContent() == null
                        ? getPatchedPackageJson(context, null) : context.getPackageJSONContent();
                final String jsonStr = packageJsonContent.toString(4);
                FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
                getResultBuilder(context).addMessage("Updated package.json at " + packageJson.getAbsolutePath());
            } else {
                getResultBuilder(context).addMessage("package.json already exists at " + packageJson.getAbsolutePath());
            }
        } else {
            JSONObject packageJsonContent = context.getPackageJSONContent() == null
                    ? getDefaultPackageJson(context, null) : context.getPackageJSONContent();
            final String jsonStr = packageJsonContent.toString(4);
            FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
            getResultBuilder(context).addMessage("Created package.json at " + packageJson.getAbsolutePath());

        }

        return getResultBuilder(context).createInitializePackageJsonResult();
    }

    private JSONObject getDefaultPackageJson(PackageJsonInitializerContext context, File jarFile) throws IOException {
        File directory = context.getDirectory();
        File candidate = jarFile == null ? jarFinder.findBestCandidate(new JarFinderContext(directory)) : jarFile;
        String commandName = createCommandName(context, candidate);
        return new JSONObject(patchRootNode(context, new HashMap<String,Object>(), commandName, candidate));
    }

    private JSONObject getPatchedPackageJson(PackageJsonInitializerContext context, File jarFile) throws IOException {
        File directory = context.getDirectory();
        File candidate = jarFile == null ? jarFinder.findBestCandidate(new JarFinderContext(directory)) : jarFile;
        String commandName = createCommandName(context, candidate);
        File packageJson = new File(directory, "package.json");
        JSONParser p = new JSONParser();
        String str = FileUtils.readFileToString(packageJson, "UTF-8");
        Map<String,Object> pj = (Map<String,Object>)p.parseJSON(new StringReader(str));
        return new JSONObject(patchRootNode(context, pj, commandName, candidate));
    }

    private String getBinDir() {
        return "jdeploy-bundle";
    }

    private String getRelativePath(PackageJsonInitializerContext context, File f) {
        return fileHelper.getRelativePath(context.getDirectory(), f);
    }

    private Map<String,Object> patchDependencies(Map<String,Object> deps) {
        deps.put("shelljs", "^0.8.4");
        deps.put("command-exists-promise", "^2.0.2");
        deps.put("node-fetch", "2.6.7");
        deps.put("tar", "^4.4.8");
        deps.put("yauzl", "^2.10.0");

        return deps;
    }

    private Map<String, Object> patchJdeploy(
            PackageJsonInitializerContext context,
            Map<String, Object> jdeploy,
            File jarFile,
            String commandName
    ) throws IOException {
        if (!jdeploy.containsKey("war") && !jdeploy.containsKey("jar")) {
            if (jarFile == null) {
                jdeploy.put("jar", "path/to/my-app.jar");
            } else if (jarFile.getName().endsWith(".jar")) {
                jdeploy.put("jar", getRelativePath(context, jarFile));
            } else {
                jdeploy.put("war", getRelativePath(context, jarFile));
            }
        }
        if (!jdeploy.containsKey("javaVersion")) {
            final int javaVersionInt = javaVersionExtractor.extractJavaVersionFromSystemProperties(11);
            jdeploy.put("javaVersion", String.valueOf(javaVersionInt));
        }

        if (!jdeploy.containsKey("javafx")) {
            jdeploy.put("javafx", false);
        }

        if (!jdeploy.containsKey("jdk")) {
            jdeploy.put("jdk", false);
        }
        if (!jdeploy.containsKey("title")) {
            jdeploy.put("title", createTitle(context, commandName));
        }

        return jdeploy;
    }

    private String createCommandName(PackageJsonInitializerContext context, File jarFile) throws IOException {
        File directory = context.getDirectory();
        String commandName = getCommandNameFromPom(context);
        if (commandName != null) {
            return commandName;
        }
        commandName = directory.getAbsoluteFile().getName().toLowerCase();
        if (".".equals(commandName)) {
            commandName = directory.getAbsoluteFile().getParentFile().getName().toLowerCase();
        }
        File candidate = jarFile == null
            ? DIContext.get(JarFinder.class).findBestCandidate(new JarFinderContext(directory)) : jarFile;
        if (candidate != null) {
            commandName = candidate.getName();
            if (commandName.endsWith(".jar") || commandName.endsWith(".war")) {
                commandName = commandName.substring(0, commandName.lastIndexOf("."));
            }
            commandName = commandName.replaceAll("[^a-zA-Z0-9_\\-]", "-");
        }

        return commandName;
    }

    private String createTitle(PackageJsonInitializerContext context, String commandName) throws IOException {
        String title = null;

        if (getPomFile(context) != null) {
            title = pomParser.getProjectName(getPomFile(context).getPath());
        }

        if (title == null) {
            title = stringUtils.toTitleCase(commandName);
        }

        return title;
    }

    private String getCommandNameFromPom(PackageJsonInitializerContext context) throws IOException {
        String commandName = null;
        File pom = getPomFile(context);
        if (pom != null && pom.exists()) {
            commandName = pomParser.getArtifactId(pom.getPath());
        }
        return commandName;
    }

    private File getPomFile(PackageJsonInitializerContext context) {
        File pom = new File(context.getDirectory(), "pom.xml");
        if (pom.exists()) {
            return pom;
        }
        return null;
    }

    private List<String> patchFiles(List<String> files) {
        if (!files.contains("jdeploy-bundle")) {
            files.add("jdeploy-bundle");
        }

        return files;
    }

    private Map<String,Object> patchRootNode(
            PackageJsonInitializerContext context,
            Map<String, Object> m,
            String commandName,
            File jarFile
            ) throws IOException {

        if (!m.containsKey("name")) {
            m.put("name", commandName);
        }
        if (!m.containsKey("version")) {
            m.put("version", "1.0.0");
        }
        if (!m.containsKey("description")) {
            m.put("description", "");
        }
        if (!m.containsKey("repository")) {
            m.put("repository", "");
        }
        if (!m.containsKey("main")) {
            m.put("main", "index.js");
        }

        if (!m.containsKey("author")) {
            m.put("author", "");
        }
        if (!m.containsKey("license")) {
            m.put("license", "ISC");
        }

        Map<String, Object> bin = jsonHelper.getAs(m, "bin", Map.class, new HashMap<String, Object>());
        if (bin.isEmpty()) {
            bin.put(commandName, getBinDir() + "/jdeploy.js");
        }
        m.put("bin", bin);

        if (!m.containsKey("preferGlobal")) {
            m.put("preferGlobal", true);
        }

        Map<String, Object> scripts = jsonHelper.getAs(m, "scripts", Map.class, new HashMap<String, Object>());
        if (!scripts.containsKey("test")) {
            scripts.put("test", "echo \"Error: no test specified\" && exit 1");
        }

        m.put("scripts", scripts);

        m.put("dependencies", patchDependencies(
                jsonHelper.getAs(m, "dependencies", Map.class, new HashMap<String, Object>())
        ));
        m.put("files", patchFiles(jsonHelper.getAs(m, "files", List.class, new ArrayList<String>())));
        m.put("jdeploy", patchJdeploy(
                context,
                jsonHelper.getAs(m, "jdeploy", Map.class, new HashMap<String,Object>()),
                jarFile,
                commandName
        ));

        return m;
    }


    public static class PreparationResult {
        private final boolean exists, isValid;

        private final JSONObject proposedPackageJsonToWrite;

        private final PackageJsonValidationResult validationResult;

        private PreparationResult(
                boolean exists,
                boolean isValid,
                PackageJsonValidationResult validationResult,
                JSONObject proposedPackageJsonToWrite
        ) {
            this.exists = exists;
            this.isValid = isValid;
            this.validationResult = validationResult;
            this.proposedPackageJsonToWrite = proposedPackageJsonToWrite;
        }

        public boolean exists() {
            return exists;
        }

        public boolean isValid() {
            return isValid;
        }

        public PackageJsonValidationResult getValidationResult() {
            return validationResult;
        }

        public JSONObject getProposedPackageJsonToWrite() {
            return proposedPackageJsonToWrite;
        }
    }

    public static class InitializePackageJsonResult {
        private final String[] messages;

        private final File packageJsonFile;

        public InitializePackageJsonResult(String[] messages, File packageJsonFile) {
            this.messages = messages;
            this.packageJsonFile = packageJsonFile;
        }

        public String[] getMessages() {
            return messages;
        }

        public File getPackageJsonFile() {
            return packageJsonFile;
        }
    }

    public static class InitializePackageJsonResultBuilder {
        private List<String> messages;
        private File packageJsonFile;

        public InitializePackageJsonResultBuilder setMessages(String[] messages) {
            this.messages = new ArrayList<String>(Arrays.asList(messages));
            return this;
        }

        public InitializePackageJsonResultBuilder addMessage(String message) {
            if (messages == null) {
                messages = new ArrayList<String>();
            }
            messages.add(message);
            return this;
        }

        public InitializePackageJsonResultBuilder setPackageJsonFile(File packageJsonFile) {
            this.packageJsonFile = packageJsonFile;
            return this;
        }

        public InitializePackageJsonResult createInitializePackageJsonResult() {
            return new InitializePackageJsonResult(messages.toArray(new String[0]), packageJsonFile);
        }
    }

    private static class ContextWithResult extends PackageJsonInitializerContext {
        private final InitializePackageJsonResultBuilder resultBuilder;

        private ContextWithResult(PackageJsonInitializerContext context, InitializePackageJsonResultBuilder resultBuilder) {
            super(context.getDirectory(), context.getPackageJSONContent(), context.shouldUpdateExistingPackageJson());
            this.resultBuilder = resultBuilder;
        }
    }

    private InitializePackageJsonResultBuilder getResultBuilder(PackageJsonInitializerContext context) {
        if (context instanceof ContextWithResult) {
            return ((ContextWithResult) context).resultBuilder;
        }
        throw new IllegalArgumentException("Context is not a ContextWithResult");
    }
}
