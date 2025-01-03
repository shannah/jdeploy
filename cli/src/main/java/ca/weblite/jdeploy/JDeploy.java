/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.cli.controllers.*;
import ca.weblite.jdeploy.factories.JDeployKeyProviderFactory;
import ca.weblite.jdeploy.gui.JDeployMainMenu;
import ca.weblite.jdeploy.gui.JDeployProjectEditor;
import ca.weblite.jdeploy.helpers.GithubReleaseNotesMutator;
import ca.weblite.jdeploy.helpers.PackageInfoBuilder;
import ca.weblite.jdeploy.helpers.PrereleaseHelper;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.npm.TerminalLoginLauncher;
import ca.weblite.jdeploy.packaging.JarFinder;
import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishing.PublishService;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.publishing.ResourceUploader;
import ca.weblite.jdeploy.services.*;
import ca.weblite.tools.io.*;
import ca.weblite.tools.security.KeyProvider;
import com.codename1.io.JSONParser;
import com.codename1.processing.Result;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;


import org.apache.commons.io.FileUtils;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;


/**
 *
 * @author shannah
 */
public class JDeploy implements BundleConstants {
    static {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
    }

    private final File directory;
    private File packageJsonFile;
    private Map packageJsonMap;
    private Result packageJsonResult;
    private PrintStream out = System.out;
    private PrintStream err = System.err;

    private KeyProvider keyProvider;

    private PackageSigningService packageSigningService;

    private boolean useManagedNode = false;

    private NPM npm = null;

    private String npmToken = null;

    public static String JDEPLOY_REGISTRY = "https://www.jdeploy.com/";
    static {
        if (System.getenv("JDEPLOY_REGISTRY_URL") != null) {
            JDEPLOY_REGISTRY = System.getenv("JDEPLOY_REGISTRY_URL");
            if (!JDEPLOY_REGISTRY.startsWith("http://") && !JDEPLOY_REGISTRY.startsWith("https://")) {
                throw new RuntimeException("INVALID_JDEPLOY_REGISTRY_URL environment variable.  Expecting URL but found "+JDEPLOY_REGISTRY);
            }
            if (!JDEPLOY_REGISTRY.endsWith("/")) {
                JDEPLOY_REGISTRY += "/";
            }
        }
    }

    public void setNpmToken(String token) {
        if (!Objects.equals(token, this.npmToken)) {
            this.npm = null;
        }
        this.npmToken = token;
    }

    private NPM getNPM() {
        if (npm == null) {
            npm = new NPM(out, err, useManagedNode);
            npm.setNpmToken(npmToken);
        }

        return npm;
    }

    public PrintStream getOut() {
        return out;
    }

    public PrintStream getErr() {
        return err;
    }
    
    public JDeploy(File directory, boolean exitOnFail) {
        this.directory = directory;
        this.exitOnFail = exitOnFail;
    }

    public JDeploy(File directory) {
        this(directory, true);
    }

    public PrintStream setOut(PrintStream out) {
        PrintStream old = this.out;
        this.out = out;
        return old;
    }

    public PrintStream setErr(PrintStream err) {
        PrintStream old = this.err;
        this.err = err;
        return old;
    }

    public File getDirectory() {
        return directory;
    }
    
    public File getPackageJsonFile() {
        if (packageJsonFile == null) {
            packageJsonFile = new File(directory, "package.json");
        }
        return packageJsonFile;
    }

    public void setUseManagedNode(boolean useManagedNode) {
        if (this.useManagedNode != useManagedNode) {
            this.npm = null;
        }
        this.useManagedNode = useManagedNode;
    }
    
    public Map getPackageJsonMap()  {
        if (packageJsonMap == null) {
            try {
                JSONParser p = new JSONParser();
                packageJsonMap = (Map)p.parseJSON(
                        new StringReader(
                                FileUtils.readFileToString(getPackageJsonFile(),
                                        "UTF-8"
                                )
                        )
                );
                if (packageJsonMap.containsKey("jdeploy")) {
                    ((Map)packageJsonMap.get("jdeploy")).putAll(getJdeployConfigOverrides());
                } else {
                    packageJsonMap.put("jdeploy", getJdeployConfigOverrides());
                }

                setupPackageSigningConfig((Map)packageJsonMap.get("jdeploy"));
            } catch (IOException ex) {
                Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
        }
        return packageJsonMap;
    }

    private void setupPackageSigningConfig(Map jdeployConfig) {
        keyProvider = new JDeployKeyProviderFactory().createKeyProvider(createKeyProviderFactoryConfig(jdeployConfig));
        packageSigningService = new PackageSigningService(keyProvider);
    }

    private JDeployKeyProviderFactory.KeyConfig createKeyProviderFactoryConfig(final Map jdeployConfig) {

        String developerIdKey = "jdeployDeveloperId";
        String keystorePathKey = "keystorePath";

        final String developerId = jdeployConfig.containsKey(developerIdKey)
                ? (String)jdeployConfig.get(developerIdKey) : null;
        final String keystorePath = jdeployConfig.containsKey(keystorePathKey)
                ? (String)jdeployConfig.get(keystorePathKey) : null;
        class LocalConfig extends JDeployKeyProviderFactory.DefaultKeyConfig {
            @Override
            public String getKeystorePath() {
                return keystorePath == null ? super.getKeystorePath() : keystorePath;
            }

            @Override
            public String getDeveloperId() {
                return developerId == null ? super.getDeveloperId() : developerId;
            }

            @Override
            public char[] getKeystorePassword() {
                return super.getKeystorePassword();
            }
        }

        return new LocalConfig();
    }

    private Map<String,?> getJdeployConfigOverrides() {
        Map<String,?> overrides = new HashMap<String,Object>();
        if (System.getenv("JDEPLOY_CONFIG") != null) {
            System.out.println("Found JDEPLOY_CONFIG environment variable");
            System.out.println("Injecting jdeploy config overrides from environment variable");
            System.out.println(System.getenv("JDEPLOY_CONFIG"));
            try {
                JSONParser p = new JSONParser();
                Map m = (Map)p.parseJSON(new StringReader(System.getenv("JDEPLOY_CONFIG")));
                overrides.putAll(m);
            } catch (IOException ex) {
                Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return overrides;
    }

    /**
     * Get package.json root object as a map.
     */
    private Map m() {
        return getPackageJsonMap();
    }

    /**
     * Get jdeploy config object as a Map
     */
    private Map mj() {
        if (!m().containsKey("jdeploy")) {
            m().put("jdeploy", new HashMap());
        }
        return (Map)m().get("jdeploy");
    }

    /**
     * Get jdeploy config as a Result object
     */
    private Result rj() {
        return Result.fromContent(mj());
    }
    
    public Result getPackageJsonResult() {
        if (packageJsonResult == null) {
            packageJsonResult = Result.fromContent(getPackageJsonMap());
        }
        return packageJsonResult;
    }
    
    private Result r() {
        return getPackageJsonResult();
    }

    
    public void set(String property, String value) {
        mj().put(property, value);
    }
    
    public void set(String property, int value) {
        mj().put(property, value);
    }
    
    public void set(String property, List value) {
        mj().put(property, value);
    }
    
    public String getString(String property, String defaultValue) {
        if (mj().containsKey(property)) {
            return r().getAsString("jdeploy/"+property);
        }
        return defaultValue;
    }
    
    public int getInt(String property, int defaultValue) {
        if (mj().containsKey(property)) {
            return r().getAsInteger("jdeploy/"+property);
        }
        return defaultValue;
    }

    public int getJavaVersion(int defaultValue) {
        return getInt("javaVersion", defaultValue);
    }
    
    private File[] findJarCandidates(PackagingContext context) throws IOException {
        return DIContext.get(JarFinder.class).findJarCandidates(context);
    }
    
    private File[] findWarCandidates(PackagingContext context) {
        return DIContext.get(JarFinder.class).findWarCandidates(context);
    }
    
    private File[] findWebAppCandidates(PackagingContext context) {
        return DIContext.get(JarFinder.class).findWebAppCandidates(context);
    }

    private File shallowest(File[] files) {
        File out = null;
        int depth = -1;
        for (File f : files) {
            int fDepth = f.getPath().split(Pattern.quote("\\") + "|" + Pattern.quote("/")).length;
            if (out == null || fDepth < depth) {
                depth = fDepth;
                out = f;
            }
        }
        return out;
    }

    private boolean exitOnFail = true;

    public static class FailException extends RuntimeException {
        private int exitCode;
        public FailException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }
    private void fail(String message, int code) {
        if (exitOnFail) {
            err.println(message);
            System.exit(code);
        } else {
            throw new FailException(message, code);
        }

    }
    
    public void copyToBin(PackagingContext context) throws IOException {
        DIContext.get(PackageService.class).copyToBin(context);
    }


    public void initPackageJson() {
        
    }
    
    public void updatePackageJson() {
        
    }

    /**
     *
     * @param commandName The name of the command-line command that will be installed for CLI use of the app.
     * @param prompt If true, then the user will be prompted to confirm settings before actually writing the package.json
     * @param generateGithubWorkflow True if this should also generate a github workflow.
     * @throws IOException
     */
    private void init(String commandName, boolean prompt, boolean generateGithubWorkflow) throws IOException {
        ProjectInitializer projectInitializer = DIContext.getInstance().getInstance(ProjectInitializer.class);
        boolean dryRun = prompt; // If prompting, then we should do dry run first
        ProjectInitializer.Response plan = null;
        try {
            plan = projectInitializer.decorate(
                    new ProjectInitializer.Request(
                        directory.getAbsolutePath(),
                            null,
                            dryRun,
                            generateGithubWorkflow,
                            null
                    )
            );
        } catch (ProjectInitializer.ValidationFailedException e) {
            out.println("Validation failed: "+e.getMessage());
            return;
        }
        if (!prompt) {
            return;
        }

        out.println("Creating your package.json file with following content:\n ");
        out.println(plan.packageJsonContents);
        out.println("");
        out.print("Proceed? (y/N)");
        final Scanner reader = new Scanner(System.in);
        final String response = reader.next();

        if (!"y".equals(response.toLowerCase().trim())) {
            out.println("Cancelled");
            return;
        }
        if (generateGithubWorkflow && plan.githubWorkflowExists) {
            out.print("Would you like to generate a workflow run jDeploy with Github Actions? (y/N)");
            final String githubWorkflowResponse = reader.next();
            generateGithubWorkflow = "y".equalsIgnoreCase(githubWorkflowResponse.trim());
        }

        try {
            projectInitializer.decorate(new ProjectInitializer.Request(
                    directory.getAbsolutePath(),
                    plan.jarFilePath,
                    false,
                    generateGithubWorkflow,
                    new ProjectInitializer.Delegate() {
                        @Override
                        public void onBeforeWritePackageJson(String path, String contents) {
                            out.print("Writing " + path+"...");
                        }

                        @Override
                        public void onAfterWritePackageJson(String path, String contents) {
                            out.println("Done.");
                        }

                        @Override
                        public void onBeforeWriteGithubWorkflow(String path) {
                            out.print("Writing " + path+"...");
                        }

                        @Override
                        public void onAfterWriteGithubWorkflow(String path) {
                            out.println("Done.");
                        }
                    }
            ));
        } catch (ProjectInitializer.ValidationFailedException e) {
            out.println("Validation failed: "+e.getMessage());
        }
    }

    private void loadJdeployBundleCodeToCache(String fullPackageName) throws IOException {
        DIContext.get(BundleCodeService.class).fetchJdeployBundleCode(fullPackageName);
    }

    private File getInstallersDir() {
        return new File("jdeploy" + File.separator + "installers");
    }

    private void cheerpjCLI(PackagingContext context, String[] args) {
        CheerpjController cheerpjController = new CheerpjController(context.packageJsonFile, args);

        try {
            if (context.alwaysClean) {
                File jdeployBundleDirectory = new File(context.directory, "jdeploy-bundle");
                if (jdeployBundleDirectory.exists()) {
                    FileUtils.deleteDirectory(jdeployBundleDirectory);
                }
            }
            copyToBin(context);
        } catch (Exception ex) {
            err.println("Failed to copy files to jdeploy directory.");
            ex.printStackTrace(err);
            System.exit(1);
        }

        cheerpjController.run();
    }

    private void jpackageCLI(PackagingContext context, String[] args) {
        JPackageController jPackageController = new JPackageController(context.packageJsonFile, args);
        if (!jPackageController.doesJdkIncludeJavafx()) {
            context = context.withoutStrippingJavaFXFiles();
        }
        try {
            if (context.alwaysClean) {
                File jdeployBundleDirectory = new File(context.directory, "jdeploy-bundle");
                if (jdeployBundleDirectory.exists()) {
                    FileUtils.deleteDirectory(jdeployBundleDirectory);
                }
            }
            copyToBin(context);
        } catch (Exception ex) {
            err.println("Failed to copy files to jdeploy directory.");
            ex.printStackTrace(err);
            System.exit(1);
        }

        jPackageController.run();
    }

    private void _package(PackagingContext context) throws IOException {
        DIContext.get(PackageService.class).createJdeployBundle(context);
    }

    private void _verify(String[] args) throws Exception {
        String[] verifyArgs = new String[args.length-1];
        System.arraycopy(args, 1, verifyArgs, 0, verifyArgs.length);
        CLIVerifyPackageController verifyPackageController = new CLIVerifyPackageController(new VerifyPackageService());
        verifyPackageController.verifyPackage(verifyArgs);
    }
    
    private void _package(PackagingContext context, BundlerSettings bundlerSettings) throws IOException {
        DIContext.get(PackageService.class).createJdeployBundle(context, bundlerSettings);
    }
    
    private void install(PackagingContext context) throws IOException {
        _package(context);
        getNPM().link(exitOnFail);
    }
    
    private File getGithubReleaseFilesDir() {
        return new File(directory, "jdeploy" + File.separator + "github-release-files");
    }

    private String createGithubReleaseNotes() {
        return new GithubReleaseNotesMutator(directory, err).createGithubReleaseNotes();
    }

    private void saveGithubReleaseFiles() throws IOException {
        File icon = new File(directory, "icon.png");
        File installSplash = new File(directory,"installsplash.png");
        File releaseFilesDir = getGithubReleaseFilesDir();
        releaseFilesDir.mkdirs();
        if (icon.exists()) {
            FileUtils.copyFile(icon, new File(releaseFilesDir, icon.getName()));

        }
        if (installSplash.exists()) {
            FileUtils.copyFile(installSplash, new File(releaseFilesDir, installSplash.getName()));
        }

        File installerFiles = getInstallersDir();
        if (installerFiles.isDirectory()) {
            for (File installerFile : installerFiles.listFiles()) {
                FileUtils.copyFile(installerFile, new File(releaseFilesDir, installerFile.getName().replace(' ', '.')));

            }
        }

        final String releaseNotes = createGithubReleaseNotes();
        FileUtil.writeStringToFile(releaseNotes, new File(releaseFilesDir, "jdeploy-release-notes.md"));

        out.println("Assets copied to " + releaseFilesDir);
    }

    private void uploadResources(PackagingContext packagingContext) throws IOException {
        PublishingContext context = PublishingContext.builder()
                .setPackagingContext(packagingContext)
                .setNPM(getNPM())
                .build();
        DIContext.get(ResourceUploader.class).uploadResources(context);
    }

    private String getFullPackageName(String source, String packageName) {
        if (source == null || source.isEmpty()) {
            return packageName;
        }

        return source + "#" + packageName;
    }

    public String injectGithubReleaseNotes(String originalNotes, String jdeployReleaseNotes) {
        final String beginMarker = "<!-- JDEPLOY BEGIN -->";
        final String endMarker = "<!-- JDEPLOY END -->";
        final int beginMarkerPos = originalNotes.indexOf(beginMarker);
        final int endMarkerPos = beginMarkerPos >= 0 ? originalNotes.indexOf(endMarker, beginMarkerPos) : -1;
        final StringBuilder sb = new StringBuilder();
        if (beginMarkerPos >= 0 && endMarkerPos > 0) {
            sb.append(originalNotes, 0, beginMarkerPos);
            sb.append(beginMarker).append("\n");
            sb.append(jdeployReleaseNotes.trim()).append("\n");
            sb.append(endMarker).append("\n\n");
            sb.append(originalNotes.substring(endMarkerPos+endMarker.length()).trim());
        } else {
            sb.append(originalNotes.trim()).append("\n\n");
            sb.append(beginMarker).append("\n");
            sb.append(jdeployReleaseNotes).append("\n");
            sb.append(endMarker).append("\n");
        }
        return sb.toString();
    }

    public int updateReleaseLinks(String[] args) {
        return new UpdateReleaseLinksCLIController(directory, err, out).run(args);
    }

    /**
     * Prepares a self-publish release.
     * @throws IOException
     */
    public void prepareGithubRelease(PackagingContext context, BundlerSettings bundlerSettings, InputStream oldPackageInfo) throws IOException {
        if (bundlerSettings.getSource() == null) {
            String repoName = System.getenv("GITHUB_REPOSITORY");
            if (repoName == null) {
                throw new IllegalArgumentException("prepare-github-release requires the GITHUB_REPOSITORY environment variable to be set.");
            }
            bundlerSettings.setSource("https://github.com/" + repoName);
        }

        if (oldPackageInfo == null) {
            String packageInfoUrl = bundlerSettings.getSource() + "/releases/download/jdeploy/package-info.json";
            try {
                oldPackageInfo = URLUtil.openStream(new URL(packageInfoUrl));
            } catch (IOException ex) {
                out.println("Failed to open stream for existing package-info.json at " + packageInfoUrl + ". Perhaps it doesn't exist yet");
            }
        }

        bundlerSettings.setCompressBundles(true);
        bundlerSettings.setDoNotZipExeInstaller(true);
        _package(context.withInstallers(BUNDLE_MAC_X64, BUNDLE_MAC_ARM64, BUNDLE_WIN, BUNDLE_LINUX), bundlerSettings);
        PublishingContext publishingContext = PublishingContext.builder()
                .setPackagingContext(context)
                .setNPM(getNPM())
                .build();
        JSONObject packageJSON = DIContext.get(PublishService.class).prepublish(publishingContext, bundlerSettings);
        getGithubReleaseFilesDir().mkdirs();
        getNPM().pack(publishingContext.getPublishDir(), getGithubReleaseFilesDir(), context.exitOnFail);
        saveGithubReleaseFiles();
        PackageInfoBuilder builder = new PackageInfoBuilder();
        if (oldPackageInfo != null) {
            builder.load(oldPackageInfo);
        } else {
            builder.setCreatedTime();
        }
        builder.setModifiedTime();
        builder.setVersionTimestamp(packageJSON.getString("version"));
        builder.addVersion(packageJSON.getString("version"), new FileInputStream(publishingContext.getPublishPackageJsonFile()));
        if (!PrereleaseHelper.isPrereleaseVersion(packageJSON.getString("version"))) {
            builder.setLatestVersion(packageJSON.getString("version"));
        }
        builder.save(new FileOutputStream(new File(getGithubReleaseFilesDir(), "package-info.json")));
        // Trigger register of package name
        loadJdeployBundleCodeToCache(getFullPackageName(bundlerSettings.getSource(), packageJSON.getString("name")));
        out.println("Release files created in " + getGithubReleaseFilesDir());

        CheerpjController cheerpjController = new CheerpjController(context.packageJsonFile, new String[0]);
        if (cheerpjController.isEnabled()) {
            out.println("CheerpJ detected, uploading to CheerpJ CDN...");
            cheerpjController.run();
        }

    }

    public void publish(PackagingContext context) throws IOException {
        PublishingContext publishingContext = PublishingContext.builder()
                .setPackagingContext(context)
                .setNPM(getNPM())
                .build();
        DIContext.get(PublishService.class).publish(publishingContext);
    }

    public DeveloperIdentityKeyStore getKeyStore() throws IOException {
        DeveloperIdentityKeyStore out = new DeveloperIdentityKeyStore();
        String keyString = System.getProperty("jdeploy.identity", System.getenv("JDEPLOY_IDENTITY"));
        if (keyString != null) {
            if (keyString.contains("PRIVATE KEY")) {
                out.setPemString(keyString);
            } else if (new File(keyString).exists()) {
                // it's a file
                File f = new File(keyString);
                if (f.getName().endsWith(".pem") || f.getName().endsWith(".p12")) {
                    out.setPemFile(f);
                } else {
                    try {
                        String fileContents = FileUtils.readFileToString(new File(keyString), StandardCharsets.UTF_8);
                        if (fileContents.contains("PRIVATE KEY")) {
                            out.setPemString(fileContents);
                        } else {
                            out.setKeyStoreFile(f);
                        }
                    } catch (IOException ex) {
                        out.setKeyStoreFile(f);
                    }
                }

            } else {
                if (System.getProperty("jdeploy.identity") != null) {
                    throw new IOException("The jdeploy.identity system property is set but neither points to a file, nor contains a PEM-encoded string.  This property should point to either a keystore file, a PEM-encoded file, or contain a PEM-encoded string.");
                } else {
                    throw new IOException("The JDEPLOY_IDENTITY environment variable is set but neither points to a file, nor contains a PEM-encoded string.  This variable should point to either a keystore file, a PEM-encoded file, or contain a PEM-encoded string.");
                }
            }

        }

        return out;
    }

    private void scan(PackagingContext context) throws IOException {
        File[] jars = findJarCandidates(context);
        out.println("Found "+jars.length+" jars: "+Arrays.toString(jars));
        out.println("Best candidate: "+shallowest(jars));
        
        File[] wars = findWarCandidates(context);
        out.println("Found "+wars.length+" wars: "+Arrays.toString(wars));
        out.println("Best candidate: "+shallowest(jars));
        
        File[] webApps = findWebAppCandidates(context);
        out.println("Found "+webApps.length+" web apps: "+Arrays.toString(webApps));
        out.println("Best candidate: "+shallowest(jars));
        
        List<File> combined = new ArrayList<File>();
        combined.addAll(Arrays.asList(jars));
        combined.addAll(Arrays.asList(wars));
        combined.addAll(Arrays.asList(webApps));
        
        out.println("If jdeploy were to run on this directory without specifying the jar or war in the package.json, it would choose " + shallowest(combined.toArray(new File[combined.size()])));
    }

    private void help(Options opts) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("jdeploy [init|package|install|publish] [commandName]\n"
                + "\n"
                + "Commands:\n"
                + "  init : Initialize the project\n"
                + "  package : Prepare for install.  This copies necessary files into bin directory.\n"
                + "  install : Installs the app locally (links to PATH)\n"
                + "  publish : Publishes to NPM\n"
                + "  generate: Generates a new project\n"
                + "  github init -n <repo-name>:  Initializes commits, and pushes to github\n",
                opts);

    }
    
    private void _run() {
        out.println("run not implemented yet");
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            JDeploy prog = new JDeploy(new File(".").getAbsoluteFile());
            PackagingContext context = PackagingContext.builder()
                    .directory(new File(".").getAbsoluteFile())
                    .build();
            if (args.length > 0 && "generate".equals(args[0])) {
                String[] generateArgs = new String[args.length-1];
                System.arraycopy(args, 1, generateArgs, 0, generateArgs.length);
                prog.generate(generateArgs);
                return;
            }
            if (args.length > 0 && "verify-package".equals(args[0])) {
                prog._verify(args);
                return;
            }
            if (args.length > 0 && "github".equals(args[0]) && args.length> 1 && "init".equals(args[1])) {
                String[] githubInitArgs = new String[args.length-2];
                System.arraycopy(args, 2, githubInitArgs, 0, githubInitArgs.length);
                prog.githubInit(githubInitArgs);
                return;
            }

            Options opts = new Options();
            opts.addOption("y", "no-prompt", false,"Indicates not to prompt_ user ");
            opts.addOption("W", "no-workflow", false,"Indicates not to create a github workflow if true");
            boolean noPromptFlag = false;
            boolean noWorkflowFlag = false;
            if (args.length > 0 && !"jpackage".equals(args[0])) {
                CommandLineParser parser = new DefaultParser();
                CommandLine line = parser.parse(opts, args);
                args = line.getArgs();
                noPromptFlag = line.hasOption("no-prompt");
                noWorkflowFlag = line.hasOption("no-workflow");

            }
            if (args.length == 0 || "gui".equals(args[0])) {
                System.out.println("Launching jdeploy gui.  Use jdeploy help for help");
                File packageJSON = new File("package.json");
                if (packageJSON.exists()) {
                    JSONObject packageJSONObject = new JSONObject(FileUtils.readFileToString(packageJSON, "UTF-8"));
                    boolean hasAllExpectedProperties = packageJSONObject.has("name") &&
                            packageJSONObject.has("version") &&
                            packageJSONObject.has("dependencies") &&
                            packageJSONObject.has("bin") &&
                            packageJSONObject.has("jdeploy");
                    if (hasAllExpectedProperties) {
                        EventQueue.invokeLater(() -> {
                            JDeployProjectEditor editor = new JDeployProjectEditor(packageJSON, packageJSONObject);
                            editor.show();
                        });
                        return;
                    }
                }
                prog.guiCreateNew(packageJSON);
                return;

            }
            if ("gui-main".equals(args[0])) {
                EventQueue.invokeLater(()->{
                    JDeployMainMenu menu = new JDeployMainMenu();
                    menu.show();
                });
                return;

            }
            if ("upload-resources".equals(args[0])) {
                prog.uploadResources(context);
                System.exit(0);
            }

            if ("clean".equals(args[0])) {

                File jDeployDir = new File("jdeploy");
                if (jDeployDir.exists()) {
                    System.out.println("Deleting "+jDeployDir);
                    try {
                        makeWritable(jDeployDir.toPath());
                    } catch (IOException ex) {
                        System.err.println("Failed to make "+jDeployDir+" writable.  "+ex.getMessage());
                        ex.printStackTrace();
                    }
                    FileUtils.deleteDirectory(jDeployDir);
                }
                jDeployDir = new File("jdeploy-bundle");
                if (jDeployDir.exists()) {
                    System.out.println("Deleting "+jDeployDir);
                    try {
                        makeWritable(jDeployDir.toPath());
                    } catch (IOException ex) {
                        System.err.println("Failed to make "+jDeployDir+" writable.  "+ex.getMessage());
                        ex.printStackTrace();
                    }
                    FileUtils.deleteDirectory(jDeployDir);
                }
                if (args.length > 1) {
                    String[] args2 = new String[args.length-1];
                    System.arraycopy(args, 1, args2, 0, args2.length);
                    args = args2;
                } else {
                    System.exit(0);
                }
            }

            if ("login".equals(args[0])) {
                new NPMLoginController().run();
                System.exit(0);
            } else if ("launch-login".equals(args[0])) {
                TerminalLoginLauncher.launchLoginTerminal();
                System.exit(0);
            } else if ("dmg".equals(stripFlags(args)[0])) {
                BundlerSettings bundlerSettings = new BundlerSettings();
                bundlerSettings.setAutoUpdateEnabled(isWithAutoUpdateEnabled(args));
                DIContext.get(PackageService.class)
                        .allInstallers(
                                context.withInstallers(BUNDLE_MAC_X64_DMG, BUNDLE_MAC_ARM64_DMG),
                                bundlerSettings
                        );
            } else if ("cheerpj".equals(args[0])) {
                String[] cheerpjArgs = new String[args.length-1];
                System.arraycopy(args, 1, cheerpjArgs, 0, cheerpjArgs.length);
                prog.cheerpjCLI(context, cheerpjArgs);
            } else if ("jpackage".equals(args[0])) {
                String[] jpackageArgs = new String[args.length-1];
                System.arraycopy(args, 1, jpackageArgs, 0, jpackageArgs.length);
                prog.jpackageCLI(context, jpackageArgs);
            } else if ("package".equals(args[0])) {
                prog._package(context);
            } else if ("init".equals(args[0])) {
                String commandName = null;
                if (args.length > 1) {
                    commandName = args[1];
                }
                final boolean prompt = !noPromptFlag;
                final boolean generateGithubWorkflow = !noWorkflowFlag;
                prog.init(commandName, prompt, generateGithubWorkflow);
            } else if ("install".equals(args[0])) {
                prog.install(context);
            } else if ("publish".equals(args[0])) {
                prog.publish(context);
            } else if ("github-prepare-release".equals(args[0])) {
                prog.prepareGithubRelease(context, new BundlerSettings(), null);
            } else if ("github-build-release-body".equals(args[0])) {
                String oldBody = System.getenv("GITHUB_RELEASE_BODY");
                String jdeployReleaseNotes = System.getenv("JDEPLOY_RELEASE_NOTES");
                if (oldBody == null || jdeployReleaseNotes == null) {
                    System.err.println("The github-build-release-body action requires both the GITHUB_RELEASE_BODY and JDEPLOY_RELEASE_NOTES environment variables to be set");
                    System.exit(1);
                }
                System.out.println(prog.injectGithubReleaseNotes(oldBody, jdeployReleaseNotes));
            } else if ("github-update-release-links".equals(args[0])) {
                int result = prog.updateReleaseLinks(subArray(args,1 ));
                System.exit(result);
            } else if ("scan".equals(args[0])) {
                prog.scan(context);
            } else if ("run".equals(args[0])) {
                prog._run();
            } else if ("help".equals(args[0])) {
                prog.help(opts);
            } else {
                prog.help(opts);
                System.exit(1);
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void makeWritable(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Set<PosixFilePermission> perms = EnumSet.allOf(PosixFilePermission.class);
                Files.setPosixFilePermissions(file, perms);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Set<PosixFilePermission> perms = EnumSet.allOf(PosixFilePermission.class);
                Files.setPosixFilePermissions(dir, perms);
                dir.toFile().setWritable(true, false);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void githubInit(String[] githubInitArgs) {
        GitHubRepositoryInitializerCLIController controller = new GitHubRepositoryInitializerCLIController(getPackageJsonFile(), githubInitArgs);
        controller.run();
        if (controller.getExitCode() != 0) {
            fail("Failed to initialize github repository", controller.getExitCode());
        }
    }

    private void generate(String[] args) {
        ProjectGeneratorCLIController controller = new ProjectGeneratorCLIController(args);
        controller.run();
    }

    private void guiCreateNew(File packageJSON) {
        EventQueue.invokeLater(()->{
            final int result = JOptionPane.showConfirmDialog(null, new JLabel(
                    "<html><p style='width:400px'>No package.json file found in this directory.  Do you want to create one now?</p></html>"),
                    "Create package.json?",
                    JOptionPane.YES_NO_OPTION);


            if (result != JOptionPane.YES_OPTION) {
                System.out.println("result "+result);
                return;
            }

            final int addWorkflowResult = JOptionPane.showConfirmDialog(null, new JLabel(
                            "<html><p style='width:400px'>Would you like to also create a Github workflow to build installer bundles automatically using Github Actions?</p></html>"),
                    "Create Github Workflow?",
                    JOptionPane.YES_NO_OPTION);
            final boolean addGithubWorkflow = addWorkflowResult == JOptionPane.YES_OPTION;
            exitOnFail = false;
            try {
                init(null, false, addGithubWorkflow);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,  new JLabel(
                                "<html><p style='width:400px'>Failed to create package.json file. "+ex.getMessage()+"</p></html>"),
                                    "JDeploy init error",
                        JOptionPane.ERROR_MESSAGE
                        );
                return;

            }
            try {
                File packageJSONFile = new File(directory, "package.json");
                JSONObject packageJSONObject = new JSONObject(FileUtils.readFileToString(packageJSONFile, "UTF-8"));
                new JDeployProjectEditor(packageJSONFile, packageJSONObject).show();
            } catch (Exception ex) {
                ex.printStackTrace(err);
                JOptionPane.showMessageDialog(null,  new JLabel(
                                "<html><p style='width:400px'>There was a problem reading the package.json file. "+ex.getMessage()+"</p></html>"),
                        "JDeploy init error",
                        JOptionPane.ERROR_MESSAGE
                );
            }

        });
    }

    private static boolean isWithAutoUpdateEnabled(String[] args) {
        for (String arg : args) {
            if ("--disable-auto-update".equals(arg)) {
                return false;
            }
        }
        return true;
    }

    private static String[] stripFlags(String[] args) {
        List<String> out = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                continue;
            }
            out.add(arg);
        }
        return out.toArray(new String[out.size()]);
    }

    private static String[] subArray(String[] args, int offset, int length) {
        String[] out = new String[length];
        System.arraycopy(args, offset, out, 0, length);
        return out;
    }

    private static String[] subArray(String[] args, int offset) {
        return subArray(args, offset, args.length-offset);
    }
}
