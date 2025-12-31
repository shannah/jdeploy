package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DetailsPanel")
class DetailsPanelTest {
    
    private DetailsPanel panel;
    private AtomicInteger changeListenerCallCount;
    
    @BeforeEach
    void setUp() {
        panel = new DetailsPanel();
        changeListenerCallCount = new AtomicInteger(0);
    }
    
    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
    }
    
    @Test
    @DisplayName("Should load name from root packageJSON")
    void testLoadName() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        
        panel.load(packageJSON);
        
        assertEquals("test-app", panel.getName().getText());
    }
    
    @Test
    @DisplayName("Should load version from root packageJSON")
    void testLoadVersion() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("version", "1.2.3");
        
        panel.load(packageJSON);
        
        assertEquals("1.2.3", panel.getVersion().getText());
    }
    
    @Test
    @DisplayName("Should load author from root packageJSON")
    void testLoadAuthor() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("author", "John Doe");
        
        panel.load(packageJSON);
        
        assertEquals("John Doe", panel.getAuthor().getText());
    }
    
    @Test
    @DisplayName("Should load author from object format")
    void testLoadAuthorObject() {
        JSONObject packageJSON = new JSONObject();
        JSONObject author = new JSONObject();
        author.put("name", "Jane Doe");
        author.put("email", "jane@example.com");
        author.put("url", "https://example.com");
        packageJSON.put("author", (Object) author);
        
        panel.load(packageJSON);
        
        String text = panel.getAuthor().getText();
        assertTrue(text.contains("Jane Doe"));
        assertTrue(text.contains("jane@example.com"));
        assertTrue(text.contains("https://example.com"));
    }
    
    @Test
    @DisplayName("Should load description from root packageJSON")
    void testLoadDescription() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("description", "A test application");
        
        panel.load(packageJSON);
        
        assertEquals("A test application", panel.getDescription().getText());
    }
    
    @Test
    @DisplayName("Should load homepage from root packageJSON")
    void testLoadHomepage() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("homepage", "https://example.com");
        
        panel.load(packageJSON);
        
        assertEquals("https://example.com", panel.getHomepage().getText());
    }
    
    @Test
    @DisplayName("Should load license from root packageJSON")
    void testLoadLicense() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("license", "MIT");
        
        panel.load(packageJSON);
        
        assertEquals("MIT", panel.getLicense().getText());
    }
    
    @Test
    @DisplayName("Should load repository URL as string")
    void testLoadRepositoryString() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("repository", "https://github.com/user/repo");
        
        panel.load(packageJSON);
        
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
    }
    
    @Test
    @DisplayName("Should load repository from object format")
    void testLoadRepositoryObject() {
        JSONObject packageJSON = new JSONObject();
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        packageJSON.put("repository", (Object) repo);
        
        panel.load(packageJSON);
        
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
        assertEquals("src", panel.getRepositoryDirectory().getText());
    }
    
    @Test
    @DisplayName("Should load title from jdeploy object")
    void testLoadTitle() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("title", "Test Application");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertEquals("Test Application", panel.getTitle().getText());
    }
    
    @Test
    @DisplayName("Should load jar from jdeploy object")
    void testLoadJar() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "build/libs/app.jar");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertEquals("build/libs/app.jar", panel.getJarFile().getText());
    }
    
    @Test
    @DisplayName("Should load javafx flag from jdeploy object")
    void testLoadJavaFX() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("javafx", true);
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertTrue(panel.getRequiresJavaFX().isSelected());
    }
    
    @Test
    @DisplayName("Should load jdk flag from jdeploy object")
    void testLoadJdk() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdk", true);
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertTrue(panel.getRequiresFullJDK().isSelected());
    }
    
    @Test
    @DisplayName("Should load javaVersion from jdeploy object")
    void testLoadJavaVersion() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("javaVersion", "21");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertEquals("21", panel.getJavaVersion().getSelectedItem());
    }
    
    @Test
    @DisplayName("Should load jdkProvider from jdeploy object")
    void testLoadJdkProvider() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdkProvider", "jbr");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertEquals("JetBrains Runtime (JBR)", panel.getJdkProvider().getSelectedItem());
    }
    
    @Test
    @DisplayName("Should load jbrVariant from jdeploy object")
    void testLoadJbrVariant() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jbrVariant", "jcef");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertEquals("JCEF", panel.getJbrVariant().getSelectedItem());
    }
    
    @Test
    @DisplayName("Should handle null packageJSON gracefully")
    void testLoadNullPackageJSON() {
        panel.load(null);
        // Should not throw exception
    }
    
    @Test
    @DisplayName("Should save name to root packageJSON")
    void testSaveName() {
        JSONObject packageJSON = new JSONObject();
        panel.getName().setText("test-app");
        
        panel.save(packageJSON);
        
        assertEquals("test-app", packageJSON.getString("name"));
    }
    
    @Test
    @DisplayName("Should save version to root packageJSON")
    void testSaveVersion() {
        JSONObject packageJSON = new JSONObject();
        panel.getVersion().setText("1.2.3");
        
        panel.save(packageJSON);
        
        assertEquals("1.2.3", packageJSON.getString("version"));
    }
    
    @Test
    @DisplayName("Should save author to root packageJSON")
    void testSaveAuthor() {
        JSONObject packageJSON = new JSONObject();
        panel.getAuthor().setText("John Doe");
        
        panel.save(packageJSON);
        
        assertEquals("John Doe", packageJSON.getString("author"));
    }
    
    @Test
    @DisplayName("Should save description to root packageJSON")
    void testSaveDescription() {
        JSONObject packageJSON = new JSONObject();
        panel.getDescription().setText("A test application");
        
        panel.save(packageJSON);
        
        assertEquals("A test application", packageJSON.getString("description"));
    }
    
    @Test
    @DisplayName("Should save homepage to root packageJSON")
    void testSaveHomepage() {
        JSONObject packageJSON = new JSONObject();
        panel.getHomepage().setText("https://example.com");
        
        panel.save(packageJSON);
        
        assertEquals("https://example.com", packageJSON.getString("homepage"));
    }
    
    @Test
    @DisplayName("Should save license to root packageJSON")
    void testSaveLicense() {
        JSONObject packageJSON = new JSONObject();
        panel.getLicense().setText("MIT");
        
        panel.save(packageJSON);
        
        assertEquals("MIT", packageJSON.getString("license"));
    }
    
    @Test
    @DisplayName("Should save repository as object")
    void testSaveRepository() {
        JSONObject packageJSON = new JSONObject();
        panel.getRepositoryUrl().setText("https://github.com/user/repo");
        panel.getRepositoryDirectory().setText("src");
        
        panel.save(packageJSON);
        
        JSONObject repo = packageJSON.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", repo.getString("url"));
        assertEquals("src", repo.getString("directory"));
    }
    
    @Test
    @DisplayName("Should save title to jdeploy object")
    void testSaveTitle() {
        JSONObject packageJSON = new JSONObject();
        panel.getTitle().setText("Test Application");
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertEquals("Test Application", jdeploy.getString("title"));
    }
    
    @Test
    @DisplayName("Should save jar to jdeploy object")
    void testSaveJar() {
        JSONObject packageJSON = new JSONObject();
        panel.getJarFile().setText("build/libs/app.jar");
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertEquals("build/libs/app.jar", jdeploy.getString("jar"));
    }
    
    @Test
    @DisplayName("Should save javafx flag to jdeploy object")
    void testSaveJavaFX() {
        JSONObject packageJSON = new JSONObject();
        panel.getRequiresJavaFX().setSelected(true);
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertTrue(jdeploy.getBoolean("javafx"));
    }
    
    @Test
    @DisplayName("Should save jdk flag to jdeploy object")
    void testSaveJdk() {
        JSONObject packageJSON = new JSONObject();
        panel.getRequiresFullJDK().setSelected(true);
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertTrue(jdeploy.getBoolean("jdk"));
    }
    
    @Test
    @DisplayName("Should save javaVersion to jdeploy object")
    void testSaveJavaVersion() {
        JSONObject packageJSON = new JSONObject();
        panel.getJavaVersion().setSelectedItem("21");
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertEquals("21", jdeploy.getString("javaVersion"));
    }
    
    @Test
    @DisplayName("Should save jdkProvider to jdeploy object")
    void testSaveJdkProvider() {
        JSONObject packageJSON = new JSONObject();
        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertEquals("jbr", jdeploy.getString("jdkProvider"));
    }
    
    @Test
    @DisplayName("Should save jbrVariant to jdeploy object")
    void testSaveJbrVariant() {
        JSONObject packageJSON = new JSONObject();
        panel.getJbrVariant().setSelectedItem("JCEF");
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertEquals("jcef", jdeploy.getString("jbrVariant"));
    }
    
    @Test
    @DisplayName("Should remove empty fields from packageJSON")
    void testSaveRemovesEmpty() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("author", "John");
        packageJSON.put("license", "MIT");
        
        // Leave fields empty
        panel.getAuthor().setText("");
        panel.getLicense().setText("");
        
        panel.save(packageJSON);
        
        assertFalse(packageJSON.has("author"));
        assertFalse(packageJSON.has("license"));
    }
    
    @Test
    @DisplayName("Should handle load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        // Create original package.json
        JSONObject original = new JSONObject();
        original.put("name", "test-app");
        original.put("version", "1.0.0");
        original.put("author", "Jane Doe");
        original.put("description", "Test application");
        original.put("homepage", "https://example.com");
        original.put("license", "Apache-2.0");
        
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        original.put("repository", (Object) repo);
        
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("title", "Test App");
        jdeploy.put("jar", "build/libs/app.jar");
        jdeploy.put("javafx", true);
        jdeploy.put("jdk", true);
        jdeploy.put("javaVersion", "21");
        jdeploy.put("jdkProvider", "jbr");
        jdeploy.put("jbrVariant", "jcef");
        original.put("jdeploy", (Object) jdeploy);
        
        // Load into panel
        panel.load(original);
        
        // Save back to new object
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Verify all fields match
        assertEquals(original.getString("name"), saved.getString("name"));
        assertEquals(original.getString("version"), saved.getString("version"));
        assertEquals(original.getString("author"), saved.getString("author"));
        assertEquals(original.getString("description"), saved.getString("description"));
        assertEquals(original.getString("homepage"), saved.getString("homepage"));
        assertEquals(original.getString("license"), saved.getString("license"));
        
        JSONObject savedRepo = saved.getJSONObject("repository");
        assertEquals(repo.getString("url"), savedRepo.getString("url"));
        assertEquals(repo.getString("directory"), savedRepo.getString("directory"));
        
        JSONObject savedJdeploy = saved.getJSONObject("jdeploy");
        assertEquals(jdeploy.getString("title"), savedJdeploy.getString("title"));
        assertEquals(jdeploy.getString("jar"), savedJdeploy.getString("jar"));
        assertEquals(jdeploy.getBoolean("javafx"), savedJdeploy.getBoolean("javafx"));
        assertEquals(jdeploy.getBoolean("jdk"), savedJdeploy.getBoolean("jdk"));
        assertEquals(jdeploy.getString("javaVersion"), savedJdeploy.getString("javaVersion"));
        assertEquals(jdeploy.getString("jdkProvider"), savedJdeploy.getString("jdkProvider"));
        assertEquals(jdeploy.getString("jbrVariant"), savedJdeploy.getString("jbrVariant"));
    }
    
    @Test
    @DisplayName("Should register and fire change listener on text field changes")
    void testChangeListenerFires() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        // Simulate user typing in a text field
        panel.getName().setText("new-app");
        
        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called");
    }

    @Test
    @DisplayName("Should register and fire change listener on checkbox changes")
    void testChangeListenerFiresOnCheckbox() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        // Simulate user clicking a checkbox
        panel.getRequiresJavaFX().setSelected(true);
        
        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called on checkbox change");
    }

    @Test
    @DisplayName("Should register and fire change listener on combobox changes")
    void testChangeListenerFiresOnComboBox() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        // Simulate user selecting from combobox
        panel.getJavaVersion().setSelectedItem("17");
        
        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called on combobox change");
    }
    
    @Test
    @DisplayName("Should handle null packageJSON in save gracefully")
    void testSaveNullPackageJSON() {
        panel.save(null);
        // Should not throw exception
    }
    
    @Test
    @DisplayName("Should trim whitespace from fields")
    void testTrimWhitespace() {
        JSONObject packageJSON = new JSONObject();
        panel.getName().setText("  test-app  ");
        panel.getAuthor().setText("  John Doe  ");
        
        panel.save(packageJSON);
        
        assertEquals("test-app", packageJSON.getString("name"));
        assertEquals("John Doe", packageJSON.getString("author"));
    }
    
    @Test
    @DisplayName("Should hide JBR Variant field when JDK Provider is Auto")
    void testJbrVariantVisibilityWhenAuto() {
        // Create a test package.json with Auto provider
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdkProvider", (Object) null); // Will default to Auto
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertFalse(panel.getJbrVariant().isVisible());
    }
    
    @Test
    @DisplayName("Should show JBR Variant field when JDK Provider is JBR")
    void testJbrVariantVisibilityWhenJbr() {
        // Create a test package.json with JBR provider
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdkProvider", "jbr");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        assertTrue(panel.getJbrVariant().isVisible());
    }
    
    @Test
    @DisplayName("Should toggle JBR Variant visibility when switching providers")
    void testJbrVariantVisibilityToggling() {
        // Create a test package.json with Auto provider
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        // Start with Auto - should be hidden
        assertEquals("Auto (Recommended)", panel.getJdkProvider().getSelectedItem());
        assertFalse(panel.getJbrVariant().isVisible());
        
        // Switch to JBR - should be visible
        panel.getJdkProvider().setSelectedIndex(1); // JetBrains Runtime (JBR) is at index 1
        assertTrue(panel.getJbrVariant().isVisible());
        
        // Switch back to Auto - should be hidden
        panel.getJdkProvider().setSelectedIndex(0); // Auto (Recommended) is at index 0
        assertFalse(panel.getJbrVariant().isVisible());
    }
    
    @Test
    @DisplayName("Should initialize JBR Variant field as hidden by default")
    void testJbrVariantInitiallyHidden() {
        // Create a test package.json with Auto provider
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        
        // Default state should be Auto (Recommended) with hidden JBR Variant
        assertEquals("Auto (Recommended)", panel.getJdkProvider().getSelectedItem());
        assertFalse(panel.getJbrVariant().isVisible());
    }
    
    @Test
    @DisplayName("Should accept setParentFrame call without error")
    void testSetParentFrame() {
        JFrame testFrame = new JFrame("Test");
        panel.setParentFrame(testFrame);
        // Should not throw exception
        testFrame.dispose();
    }
    
    @Test
    @DisplayName("Should accept setProjectDirectory call without error")
    void testSetProjectDirectory() {
        File testDir = new File(System.getProperty("java.io.tmpdir"));
        panel.setProjectDirectory(testDir);
        // Should not throw exception
    }
}
