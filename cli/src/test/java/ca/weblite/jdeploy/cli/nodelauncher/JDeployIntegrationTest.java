package ca.weblite.jdeploy.cli.nodelauncher;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.*;
import java.nio.file.*;

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
    @EnabledIfEnvironmentVariable(named = "JDEPLOY_TEST_JVM_DOWNLOADS", matches = "true")
    void testJDeployScript() throws Exception {
        // Step 1: Load the jdeploy.js script from classpath
        String script = loadResource(SCRIPT_PATH);

        // Step 2: Replace only the necessary placeholder values
        script = script.replace("{{JAR_NAME}}", JAR_NAME)
                .replace("{{MAIN_CLASS}}", JAVA_CLASS)
                .replace("{{JAVA_VERSION}}", "17")  // Using JDK 17
                .replace("{{JAVAFX}}", "false")
                .replace("{{JDK}}", "false");

        // Step 3: Save the modified script to a temp file inside jdeploy-bundle
        Path tempScriptFile = myAppDir.resolve("jdeploy-bundle/jdeploy.js");
        Files.write(tempScriptFile, script.getBytes());

        // Step 4: Create a simple HelloWorld Java application
        String javaAppCode =
                "public class HelloWorldApp {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        System.out.println(\"Hello, World!\");\n" +
                        "    }\n" +
                        "}\n";
        Path javaAppFile = tempDir.resolve(JAVA_APP);
        Files.write(javaAppFile, javaAppCode.getBytes());

        // Step 5: Compile the Java application using the java running the test
        String javaHome = System.getProperty("java.home");
        // Check if the java.home points to a JRE (it should contain 'jre' in the path)
        if (javaHome.contains("jre")) {
            // Attempt to find the JDK directory
            javaHome = javaHome.replace("/jre", "");
            javaHome = javaHome.replace("\\jre", "");
        }
        String javacPath = javaHome + File.separator + "bin" + File.separator + "javac";

        // Ensure javac exists in the directory
        File javacFile = new File(javacPath);
        assertTrue(javacFile.exists(), "javac not found in " + javacPath);

        ProcessBuilder compileProcessBuilder = new ProcessBuilder(javacPath, JAVA_APP);
        compileProcessBuilder.directory(tempDir.toFile());
        Process compileProcess = compileProcessBuilder.start();
        assertEquals(0, compileProcess.waitFor(), "Java compilation failed");

        // Step 6: Create a MANIFEST.MF file specifying the Main-Class
        String manifestContent = "Main-Class: " + JAVA_CLASS + "\n";
        Path manifestFile = tempDir.resolve(MANIFEST_FILE);
        Files.write(manifestFile, manifestContent.getBytes());

        // Step 7: Create a JAR file containing the compiled class and the manifest
        ProcessBuilder jarProcessBuilder = new ProcessBuilder("jar", "cmf", MANIFEST_FILE, JAR_NAME, JAVA_CLASS + ".class");
        jarProcessBuilder.directory(tempDir.toFile());
        Process jarProcess = jarProcessBuilder.start();
        assertEquals(0, jarProcess.waitFor(), "JAR creation failed");

        // Step 8: Move the JAR file to the jdeploy-bundle directory
        Files.move(tempDir.resolve(JAR_NAME), myAppDir.resolve("jdeploy-bundle/" + JAR_NAME));

        // Step 9: Load package.json from classpath
        String packageJson = loadResource(PACKAGE_JSON_PATH);

        // Step 10: Save the package.json to the myapp directory
        Path packageJsonFile = myAppDir.resolve("package.json");
        Files.write(packageJsonFile, packageJson.getBytes());

        // Step 11: Run npm install to install dependencies
        ProcessBuilder npmInstallProcessBuilder = new ProcessBuilder("npm", "install");
        npmInstallProcessBuilder.directory(myAppDir.toFile());
        Process npmInstallProcess = npmInstallProcessBuilder.start();
        assertEquals(0, npmInstallProcess.waitFor(), "npm install failed");

        // Step 12: Run the Node.js script with npm
        ProcessBuilder runProcessBuilder = new ProcessBuilder("node", "jdeploy-bundle" + File.separator + "jdeploy.js");
        runProcessBuilder.directory(myAppDir.toFile());
        Process runProcess = runProcessBuilder.start();

        // Step 13: Capture and verify output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
            String line;
            boolean foundHelloWorld = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Hello, World!")) {
                    foundHelloWorld = true;
                    break;
                }
            }
            assertTrue(foundHelloWorld, "The Java app output was not as expected.");
        }

        // Ensure the process exits successfully
        assertEquals(0, runProcess.waitFor(), "Node.js script did not exit successfully");
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
