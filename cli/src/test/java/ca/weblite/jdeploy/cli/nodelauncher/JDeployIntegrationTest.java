package ca.weblite.jdeploy.cli.nodelauncher;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static net.lingala.zip4j.util.FileUtils.isWindows;
import static org.junit.jupiter.api.Assertions.*;

class JDeployIntegrationTest {

    private static final String SCRIPT_PATH = "/ca/weblite/jdeploy/jdeploy.js"; // Use classpath resource path
    private static final String PACKAGE_JSON_PATH = "/test-package.json"; // Use classpath resource path
    private static final String TEMP_DIR_PREFIX = "jdeploy_test_";
    private static final String JAVA_APP = "HelloWorldApp.java"; // Your Java app file
    private static final String JAVA_CLASS = "HelloWorldApp"; // Your Java app class name
    private static final String JAR_NAME = "myapp.jar"; // Name of the JAR file
    private static final String MANIFEST_FILE = "MANIFEST.MF"; // Manifest file name

    private Path tempDir;
    private Path myAppDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary directory for JDEPLOY_HOME
        tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
        myAppDir = tempDir.resolve("myapp");

        // Create myapp directory and the jdeploy-bundle subdirectory
        Files.createDirectories(myAppDir.resolve("jdeploy-bundle"));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up the temporary directory after each test
        Files.walk(tempDir)
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JDEPLOY_TEST_CLI_LAUNCHER", matches = "true")
    void testJDeployScript() throws Exception {
        System.out.println("=== DEBUG: Starting JDeploy Integration Test ===");
        System.out.println("DEBUG: OS: " + System.getProperty("os.name"));
        System.out.println("DEBUG: OS Arch: " + System.getProperty("os.arch"));
        System.out.println("DEBUG: Java Version: " + System.getProperty("java.version"));
        System.out.println("DEBUG: JDEPLOY_HOME: " + (System.getenv("JDEPLOY_HOME") != null ? System.getenv("JDEPLOY_HOME") : "not set"));
        System.out.println("DEBUG: Temp directory: " + tempDir);
        
        // Step 1: Load the jdeploy.js script from classpath
        System.out.println("DEBUG: Step 1 - Loading jdeploy.js script from classpath: " + SCRIPT_PATH);
        String script = loadResource(SCRIPT_PATH);
        System.out.println("DEBUG: Script loaded successfully, length: " + script.length());

        // Step 2: Replace only the necessary placeholder values
        System.out.println("DEBUG: Step 2 - Replacing placeholders in script");
        System.out.println("DEBUG: JAR_NAME=" + JAR_NAME + ", MAIN_CLASS=" + JAVA_CLASS + ", JAVA_VERSION=17");
        script = script.replace("{{JAR_NAME}}", JAR_NAME)
                .replace("{{MAIN_CLASS}}", JAVA_CLASS)
                .replace("{{JAVA_VERSION}}", "17")  // Using JDK 17
                .replace("{{JAVAFX}}", "false")
                .replace("{{JDK}}", "false");
        System.out.println("DEBUG: Placeholders replaced successfully");

        // Step 3: Save the modified script to a temp file inside jdeploy-bundle
        System.out.println("DEBUG: Step 3 - Saving modified script to jdeploy-bundle");
        Path tempScriptFile = myAppDir.resolve("jdeploy-bundle/jdeploy.js");
        Files.write(tempScriptFile, script.getBytes());
        System.out.println("DEBUG: Script saved to: " + tempScriptFile.toAbsolutePath());

        // Step 4: Create a simple HelloWorld Java application
        System.out.println("DEBUG: Step 4 - Creating HelloWorld Java application");
        String javaAppCode =
                "public class HelloWorldApp {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        System.out.println(\"Hello, World!\");\n" +
                        "    }\n" +
                        "}\n";
        Path javaAppFile = tempDir.resolve(JAVA_APP);
        Files.write(javaAppFile, javaAppCode.getBytes());
        System.out.println("DEBUG: Java file created: " + javaAppFile.toAbsolutePath());

        // Step 5: Compile the Java application using the java running the test
        System.out.println("DEBUG: Step 5 - Compiling Java application");
        String javaHome = System.getProperty("java.home");
        System.out.println("DEBUG: Initial java.home: " + javaHome);
        // Check if the java.home points to a JRE (it should contain 'jre' in the path)
        if (javaHome.contains("jre")) {
            // Attempt to find the JDK directory
            javaHome = javaHome.replace("/jre", "");
            javaHome = javaHome.replace("\\jre", "");
            System.out.println("DEBUG: Adjusted java.home: " + javaHome);
        }
        String javacPath = javaHome + File.separator + "bin" + File.separator + "javac";

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            javacPath += ".exe";
        }
        System.out.println("DEBUG: javac path: " + javacPath);

        // Get the Java version being used to run the test
        String javaVersion = System.getProperty("java.version");
        System.out.println("Running Java version: " + javaVersion);

// Determine the target bytecode version based on the Java version
        String targetBytecode = "17";  // Default to Java 17 bytecode
        String sourceBytecode = "17";  // Set both source and target bytecode to 17

        if (javaVersion != null) {
            // Parse the major version from the Java version string
            int majorVersion = Integer.parseInt(javaVersion.split("\\.")[0]);

            // If the Java version is less than 17, don't use -target 17
            if (majorVersion < 17) {
                targetBytecode = null;
                sourceBytecode = null;
            }
        }

        // Ensure javac exists in the directory
        File javacFile = new File(javacPath);
        assertTrue(javacFile.exists(), "javac not found in " + javacPath);

        // Build the compile command with the target bytecode version
        List<String> compileCommand = new ArrayList<>();
        compileCommand.add(javacPath);
        if (targetBytecode != null) {
            compileCommand.add("-source");
            compileCommand.add(sourceBytecode);  // Use source bytecode version
            compileCommand.add("-target");
            compileCommand.add(targetBytecode);  // Use target bytecode version
        }
        compileCommand.add(JAVA_APP);

        System.out.println("DEBUG: Compile command: " + String.join(" ", compileCommand));
        ProcessBuilder compileProcessBuilder = new ProcessBuilder(compileCommand);
        compileProcessBuilder.directory(tempDir.toFile());
        compileProcessBuilder.redirectErrorStream(true);
        Process compileProcess = compileProcessBuilder.start();
        
        // Capture compilation output
        StringBuilder compileOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                compileOutput.append(line).append("\n");
            }
        }
        
        int compileExitCode = compileProcess.waitFor();
        System.out.println("DEBUG: Compilation output: " + compileOutput.toString());
        System.out.println("DEBUG: Compilation exit code: " + compileExitCode);
        assertEquals(0, compileExitCode, "Java compilation failed: " + compileOutput.toString());

        // Step 6: Create a MANIFEST.MF file specifying the Main-Class
        String manifestContent = "Main-Class: " + JAVA_CLASS + "\n";
        Path manifestFile = tempDir.resolve(MANIFEST_FILE);
        Files.write(manifestFile, manifestContent.getBytes());

        // Step 7: Create a JAR file containing the compiled class and the manifest
        System.out.println("DEBUG: Step 7 - Creating JAR file");
        ProcessBuilder jarProcessBuilder = new ProcessBuilder("jar", "cmf", MANIFEST_FILE, JAR_NAME, JAVA_CLASS + ".class");
        jarProcessBuilder.directory(tempDir.toFile());
        jarProcessBuilder.redirectErrorStream(true);
        Process jarProcess = jarProcessBuilder.start();
        
        // Capture JAR creation output
        StringBuilder jarOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(jarProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jarOutput.append(line).append("\n");
            }
        }
        
        int jarExitCode = jarProcess.waitFor();
        System.out.println("DEBUG: JAR creation output: " + jarOutput.toString());
        System.out.println("DEBUG: JAR creation exit code: " + jarExitCode);
        assertEquals(0, jarExitCode, "JAR creation failed: " + jarOutput.toString());

        // Step 8: Move the JAR file to the jdeploy-bundle directory
        System.out.println("DEBUG: Step 8 - Moving JAR to jdeploy-bundle");
        Path jarDestination = myAppDir.resolve("jdeploy-bundle/" + JAR_NAME);
        Files.move(tempDir.resolve(JAR_NAME), jarDestination);
        System.out.println("DEBUG: JAR moved to: " + jarDestination.toAbsolutePath());

        // Step 9: Load package.json from classpath
        System.out.println("DEBUG: Step 9 - Loading package.json from classpath");
        String packageJson = loadResource(PACKAGE_JSON_PATH);
        System.out.println("DEBUG: package.json loaded, length: " + packageJson.length());

        // Step 10: Save the package.json to the myapp directory
        System.out.println("DEBUG: Step 10 - Saving package.json to myapp directory");
        Path packageJsonFile = myAppDir.resolve("package.json");
        Files.write(packageJsonFile, packageJson.getBytes());
        System.out.println("DEBUG: package.json saved to: " + packageJsonFile.toAbsolutePath());

        String npmCommand = isWindows() ? "npm.cmd" : "npm"; // Use npm.cmd for Windows, npm for others
        System.out.println("DEBUG: Using npm command: " + npmCommand);

        // Step 11: Run npm install to install dependencies
        System.out.println("DEBUG: Step 11 - Running npm install in: " + myAppDir.toAbsolutePath());
        ProcessBuilder npmInstallProcessBuilder = new ProcessBuilder(npmCommand, "install");
        npmInstallProcessBuilder.directory(myAppDir.toFile());
        npmInstallProcessBuilder.redirectErrorStream(true);
        Process npmInstallProcess = npmInstallProcessBuilder.start();
        
        // Capture npm install output
        StringBuilder npmOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(npmInstallProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                npmOutput.append(line).append("\n");
            }
        }
        
        int npmExitCode = npmInstallProcess.waitFor();
        System.out.println("DEBUG: npm install output:\n" + npmOutput.toString());
        System.out.println("DEBUG: npm install exit code: " + npmExitCode);
        assertEquals(0, npmExitCode, "npm install failed: " + npmOutput.toString());

        // Step 12: Run the Node.js script with npm
        System.out.println("DEBUG: Step 12 - Running Node.js script");
        System.out.println("DEBUG: Command: node jdeploy-bundle" + File.separator + "jdeploy.js");
        System.out.println("DEBUG: Working directory: " + myAppDir.toAbsolutePath());
        ProcessBuilder runProcessBuilder = new ProcessBuilder("node", "jdeploy-bundle" + File.separator + "jdeploy.js");
        runProcessBuilder.directory(myAppDir.toFile());
        Process runProcess = runProcessBuilder.start();

        // Step 13: Capture and verify output
        System.out.println("DEBUG: Step 13 - Capturing script output");
        StringBuilder stdoutOutput = new StringBuilder();
        StringBuilder stderrOutput = new StringBuilder();
        
        // Read stderr in a separate thread
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrOutput.append(line).append("\n");
                    System.out.println("DEBUG: STDERR: " + line);
                }
            } catch (IOException e) {
                System.err.println("DEBUG: Error reading stderr: " + e.getMessage());
            }
        });
        stderrThread.start();
        
        boolean foundHelloWorld = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdoutOutput.append(line).append("\n");
                System.out.println("DEBUG: STDOUT: " + line);
                if (line.contains("Hello, World!")) {
                    foundHelloWorld = true;
                }
            }
        }
        
        // Wait for stderr thread to complete
        stderrThread.join();
        
        // Ensure the process exits successfully
        int runExitCode = runProcess.waitFor();
        System.out.println("DEBUG: Node.js script exit code: " + runExitCode);
        System.out.println("DEBUG: Complete STDOUT output:\n" + stdoutOutput.toString());
        System.out.println("DEBUG: Complete STDERR output:\n" + stderrOutput.toString());
        System.out.println("DEBUG: foundHelloWorld = " + foundHelloWorld);
        
        assertTrue(foundHelloWorld, "The Java app output was not as expected. STDOUT: " + stdoutOutput.toString() + ", STDERR: " + stderrOutput.toString());
        assertEquals(0, runExitCode, "Node.js script did not exit successfully. STDOUT: " + stdoutOutput.toString() + ", STDERR: " + stderrOutput.toString());
        
        System.out.println("=== DEBUG: JDeploy Integration Test Completed Successfully ===");
    }

    // Utility method to load a resource from the classpath
    private String loadResource(String resourcePath) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }
        return convertStreamToString(inputStream);
    }

    // Convert InputStream to String (JDK 8 compatible)
    private String convertStreamToString(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
