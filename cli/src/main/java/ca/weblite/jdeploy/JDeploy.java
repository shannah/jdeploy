/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.cli.controllers.*;
import ca.weblite.jdeploy.factories.PublishTargetFactory;
import ca.weblite.jdeploy.gui.JDeployMainMenu;
import ca.weblite.jdeploy.gui.JDeployProjectEditor;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.npm.TerminalLoginLauncher;
import ca.weblite.jdeploy.packaging.JarFinder;
import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.PublishService;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.publishing.ResourceUploader;
import ca.weblite.jdeploy.services.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
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

    private PrintStream out = System.out;
    private PrintStream err = System.err;

    private boolean useManagedNode = false;

    private NPM npm = null;

    private String npmToken = null;

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

    public void setUseManagedNode(boolean useManagedNode) {
        if (this.useManagedNode != useManagedNode) {
            this.npm = null;
        }
        this.useManagedNode = useManagedNode;
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
    private void fail(String message, int code, boolean exitOnFail) {
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
    private void init(
            File packageJSON,
            String commandName,
            boolean prompt,
            boolean generateGithubWorkflow
    ) throws IOException {
        final File directory = packageJSON.getParentFile();
        ProjectInitializer projectInitializer = DIContext.getInstance().getInstance(ProjectInitializer.class);
        boolean dryRun = prompt; // If prompting, then we should do dry run first
        ProjectInitializer.Response plan = null;
        try {
            plan = projectInitializer.decorate(
                    new ProjectInitializer.Request(
                        packageJSON.getParentFile().getAbsolutePath(),
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
        getNPM().link(context.exitOnFail);
    }

    private void uploadResources(PackagingContext packagingContext) throws IOException {
        PublishingContext context = PublishingContext.builder()
                .setPackagingContext(packagingContext)
                .setNPM(getNPM())
                .build();
        DIContext.get(ResourceUploader.class).uploadResources(context);
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

    public int updateReleaseLinks(PackagingContext context, String[] args) {
        return new UpdateReleaseLinksCLIController(context.directory, context.err, context.out).run(args);
    }

    /**
     * Prepares a self-publish release.
     * @throws IOException
     */
    public void prepareGithubRelease(PackagingContext context, BundlerSettings bundlerSettings) throws IOException {
        if (bundlerSettings.getSource() == null) {
            String repoName = System.getenv("GITHUB_REPOSITORY");
            if (repoName == null) {
                throw new IllegalArgumentException("prepare-github-release requires the GITHUB_REPOSITORY environment variable to be set.");
            }
            bundlerSettings.setSource("https://github.com/" + repoName);
        }
        PublishTargetInterface target = DIContext.get(PublishTargetFactory.class)
                .createWithUrlAndName(
                        bundlerSettings.getSource(),
                        context.getString("name", "jdeploy-app")
                );

        if (target.getType() != PublishTargetType.GITHUB) {
            throw new IllegalArgumentException("prepare-github-release requires the source to be a github repository.");
        }

        bundlerSettings.setCompressBundles(true);
        bundlerSettings.setDoNotZipExeInstaller(true);
        _package(context.withInstallers(BUNDLE_MAC_X64, BUNDLE_MAC_ARM64, BUNDLE_WIN, BUNDLE_LINUX), bundlerSettings);
        PublishingContext publishingContext = PublishingContext.builder()
                .setPackagingContext(context)
                .setNPM(getNPM())
                .build();
        DIContext.get(PublishService.class).prepublish(publishingContext, bundlerSettings, target);

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
            File packageJSON = new File("package.json");
            JDeploy prog = new JDeploy(new File(".").getAbsoluteFile());
            PackagingContext context = packageJSON.exists()
                    ? PackagingContext.builder()
                    .directory(new File(".").getAbsoluteFile())
                    .build()
                    : null;
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
                prog.githubInit(packageJSON, githubInitArgs, true);
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
                prog.init(packageJSON, commandName, prompt, generateGithubWorkflow);
            } else if ("install".equals(args[0])) {
                prog.install(context);
            } else if ("publish".equals(args[0])) {
                prog.publish(context);
            } else if ("github-prepare-release".equals(args[0])) {
                prog.prepareGithubRelease(context, new BundlerSettings());
            } else if ("github-build-release-body".equals(args[0])) {
                String oldBody = System.getenv("GITHUB_RELEASE_BODY");
                String jdeployReleaseNotes = System.getenv("JDEPLOY_RELEASE_NOTES");
                if (oldBody == null || jdeployReleaseNotes == null) {
                    System.err.println("The github-build-release-body action requires both the GITHUB_RELEASE_BODY and JDEPLOY_RELEASE_NOTES environment variables to be set");
                    System.exit(1);
                }
                System.out.println(prog.injectGithubReleaseNotes(oldBody, jdeployReleaseNotes));
            } else if ("github-update-release-links".equals(args[0])) {
                int result = prog.updateReleaseLinks(context, subArray(args,1 ));
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

    private void githubInit(File packageJsonFile, String[] githubInitArgs, boolean exitOnFail) {
        GitHubRepositoryInitializerCLIController controller = new GitHubRepositoryInitializerCLIController(
                packageJsonFile,
                githubInitArgs
        );
        controller.run();
        if (controller.getExitCode() != 0) {
            fail("Failed to initialize github repository", controller.getExitCode(), exitOnFail);
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
            try {
                init(packageJSON, null, false, addGithubWorkflow);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,  new JLabel(
                                "<html><p style='width:400px'>Failed to create package.json file. "+ex.getMessage()+"</p></html>"),
                                    "JDeploy init error",
                        JOptionPane.ERROR_MESSAGE
                        );
                return;

            }
            try {
                JSONObject packageJSONObject = new JSONObject(FileUtils.readFileToString(packageJSON, "UTF-8"));
                new JDeployProjectEditor(packageJSON, packageJSONObject).show();
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
