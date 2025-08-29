package ca.weblite.jdeploy.packaging;

import ca.weblite.jdeploy.BundleConstants;
import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.ProjectBuilderService;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PackageServiceIntegrationTest {

    private File tempDir;
    private File jarFile;
    private PackageService packageService;
    private PackagingContext context;
    
    @Before
    public void setup() throws Exception {
        // Create a temporary directory for testing
        tempDir = Files.createTempDirectory("package-service-test").toFile();
        
        // Create a mock JAR file similar to TextEditor project
        File targetDir = new File(tempDir, "target");
        targetDir.mkdirs();
        jarFile = new File(targetDir, "test-app-1.0-SNAPSHOT.jar");
        
        // Create a simple JAR file with a manifest
        java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
            new java.io.FileOutputStream(jarFile)
        );
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, "com.example.TestApp");
        jos.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
        manifest.write(jos);
        jos.closeEntry();
        jos.close();
        
        // Create icon.png
        FileUtils.copyInputStreamToFile(
            JDeploy.class.getResourceAsStream("icon.png"), 
            new File(targetDir, "icon.png")
        );
        
        // Create basic PackageService with mocked dependencies
        Environment environment = mock(Environment.class);
        JarFinder jarFinder = mock(JarFinder.class);
        ClassPathFinder classPathFinder = mock(ClassPathFinder.class);
        CompressionService compressionService = mock(CompressionService.class);
        BundleCodeService bundleCodeService = mock(BundleCodeService.class);
        CopyJarRuleBuilder copyJarRuleBuilder = mock(CopyJarRuleBuilder.class);
        ProjectBuilderService projectBuilderService = mock(ProjectBuilderService.class);
        PackagingConfig packagingConfig = mock(PackagingConfig.class);
        
        // Configure mocks
        when(jarFinder.findJarFile(any())).thenReturn(jarFile);
        when(copyJarRuleBuilder.build(any(), any())).thenReturn(java.util.Collections.emptyList());
        when(projectBuilderService.isBuildSupported(any())).thenReturn(false);
        when(packagingConfig.getJdeployRegistry()).thenReturn("https://npm.jdeploy.com");
        when(compressionService.compress(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        
        packageService = new PackageService(
            environment,
            jarFinder,
            classPathFinder,
            compressionService,
            bundleCodeService,
            copyJarRuleBuilder,
            projectBuilderService,
            packagingConfig
        );
    }
    
    @After
    public void cleanup() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
    }
    
    private PackagingContext createPackagingContext(boolean generateLegacyBundles) throws Exception {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("version", "1.0.0");
        
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);
        
        jdeploy.put("jar", "target/test-app-1.0-SNAPSHOT.jar");
        jdeploy.put("javaVersion", "11");
        jdeploy.put("javafx", true);
        jdeploy.put("title", "Test App");
        jdeploy.put("generateLegacyBundles", generateLegacyBundles);
        
        JSONObject installers = new JSONObject();
        installers.put("win", true);
        installers.put("linux", true);
        jdeploy.put("installers", new String[]{"win", "linux"});
        
        File packageJsonFile = new File(tempDir, "package.json");
        FileUtils.write(packageJsonFile, packageJSON.toString(), "UTF-8");
        
        return new PackagingContext.Builder()
                .directory(tempDir)
                .packageJsonFile(packageJsonFile)
                .out(System.out)
                .err(System.err)
                .build();
    }
    
    private PackagingContext createPackagingContextWithoutGenerateLegacyBundles() throws Exception {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("version", "1.0.0");
        
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);
        
        jdeploy.put("jar", "target/test-app-1.0-SNAPSHOT.jar");
        jdeploy.put("javaVersion", "11");
        jdeploy.put("javafx", true);
        jdeploy.put("title", "Test App");
        // generateLegacyBundles not specified - should default to true
        
        jdeploy.put("installers", new String[]{"win", "linux"});
        
        File packageJsonFile = new File(tempDir, "package.json");
        FileUtils.write(packageJsonFile, packageJSON.toString(), "UTF-8");
        
        return new PackagingContext.Builder()
                .directory(tempDir)
                .packageJsonFile(packageJsonFile)
                .out(System.out)
                .err(System.err)
                .build();
    }
    
    @Test
    public void testGenerateLegacyBundlesEnabled() throws Exception {
        context = createPackagingContext(true);
        
        // Verify that generateLegacyBundles is enabled
        assertTrue("generateLegacyBundles should be enabled", context.isGenerateLegacyBundles());
        
        // Create installers - this should generate both new-style and legacy installers
        packageService.allInstallers(context, new BundlerSettings());
        
        File installersDir = context.getInstallersDir();
        assertTrue("Installers directory should exist", installersDir.exists());
        
        // Check for Windows installers - both win-x64 and win (legacy) should exist
        File[] winInstallers = installersDir.listFiles((dir, name) -> 
            name.contains("win-x64") || (name.contains("win") && !name.contains("win-x64"))
        );
        
        // Check for Linux installers - both linux-x64 and linux (legacy) should exist  
        File[] linuxInstallers = installersDir.listFiles((dir, name) ->
            name.contains("linux-x64") || (name.contains("linux") && !name.contains("linux-x64"))
        );
        
        assertTrue("Should have Windows installers when legacy bundles enabled", 
            winInstallers != null && winInstallers.length > 0);
        assertTrue("Should have Linux installers when legacy bundles enabled", 
            linuxInstallers != null && linuxInstallers.length > 0);
    }
    
    @Test
    public void testGenerateLegacyBundlesDisabled() throws Exception {
        context = createPackagingContext(false);
        
        // Verify that generateLegacyBundles is disabled
        assertFalse("generateLegacyBundles should be disabled", context.isGenerateLegacyBundles());
        
        // Create installers - this should generate only new-style installers
        packageService.allInstallers(context, new BundlerSettings());
        
        File installersDir = context.getInstallersDir();
        assertTrue("Installers directory should exist", installersDir.exists());
        
        // Check for Windows installers - only win-x64 should exist, not win (legacy)
        File[] winX64Installers = installersDir.listFiles((dir, name) -> name.contains("win-x64"));
        File[] winLegacyInstallers = installersDir.listFiles((dir, name) -> 
            name.contains("win") && !name.contains("win-x64") && !name.contains("win-arm64")
        );
        
        // Check for Linux installers - only linux-x64 should exist, not linux (legacy)
        File[] linuxX64Installers = installersDir.listFiles((dir, name) -> name.contains("linux-x64"));
        File[] linuxLegacyInstallers = installersDir.listFiles((dir, name) ->
            name.contains("linux") && !name.contains("linux-x64") && !name.contains("linux-arm64")
        );
        
        assertTrue("Should have win-x64 installers when legacy bundles disabled", 
            winX64Installers != null && winX64Installers.length > 0);
        assertTrue("Should NOT have win legacy installers when legacy bundles disabled", 
            winLegacyInstallers == null || winLegacyInstallers.length == 0);
        
        assertTrue("Should have linux-x64 installers when legacy bundles disabled", 
            linuxX64Installers != null && linuxX64Installers.length > 0);
        assertTrue("Should NOT have linux legacy installers when legacy bundles disabled", 
            linuxLegacyInstallers == null || linuxLegacyInstallers.length == 0);
    }
    
    @Test
    public void testGenerateLegacyBundlesDefaultValue() throws Exception {
        context = createPackagingContextWithoutGenerateLegacyBundles();
        
        // Verify that generateLegacyBundles defaults to true when not specified
        assertTrue("generateLegacyBundles should default to true", context.isGenerateLegacyBundles());
        
        // Create installers - this should generate both new-style and legacy installers (same as enabled test)
        packageService.allInstallers(context, new BundlerSettings());
        
        File installersDir = context.getInstallersDir();
        assertTrue("Installers directory should exist", installersDir.exists());
        
        // Check for Windows installers - both win-x64 and win (legacy) should exist
        File[] winInstallers = installersDir.listFiles((dir, name) -> 
            name.contains("win-x64") || (name.contains("win") && !name.contains("win-x64"))
        );
        
        // Check for Linux installers - both linux-x64 and linux (legacy) should exist  
        File[] linuxInstallers = installersDir.listFiles((dir, name) ->
            name.contains("linux-x64") || (name.contains("linux") && !name.contains("linux-x64"))
        );
        
        assertTrue("Should have Windows installers when legacy bundles default to enabled", 
            winInstallers != null && winInstallers.length > 0);
        assertTrue("Should have Linux installers when legacy bundles default to enabled", 
            linuxInstallers != null && linuxInstallers.length > 0);
    }
}