package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import javax.swing.JFrame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DetailsPanel")
class DetailsPanelTest {
    
    private DetailsPanel panel;
    private AtomicInteger changeListenerCallCount;
    
    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI test in headless environment");
        panel = new DetailsPanel();
        changeListenerCallCount = new AtomicInteger(0);
    }
    
    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
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
    @DisplayName("Should save homepage to root packageJSON")
    void testSaveHomepage() {
        JSONObject packageJSON = new JSONObject();
        panel.getHomepage().setText("https://example.com");
        
        panel.save(packageJSON);
        
        assertEquals("https://example.com", packageJSON.getString("homepage"));
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
        // Must set JDK Provider to JBR first, otherwise jbrVariant is not saved
        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");
        panel.getJbrVariant().setSelectedItem("JCEF");
        
        panel.save(packageJSON);
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertEquals("jcef", jdeploy.getString("jbrVariant"));
    }
    
    
    @Test
    @DisplayName("Should handle load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        // Create original package.json with DetailsPanel fields only
        JSONObject original = new JSONObject();
        original.put("homepage", "https://example.com");
        
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        original.put("repository", (Object) repo);
        
        JSONObject jdeploy = new JSONObject();
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
        assertEquals(original.getString("homepage"), saved.getString("homepage"));
        
        JSONObject savedRepo = saved.getJSONObject("repository");
        assertEquals(repo.getString("url"), savedRepo.getString("url"));
        assertEquals(repo.getString("directory"), savedRepo.getString("directory"));
        
        JSONObject savedJdeploy = saved.getJSONObject("jdeploy");
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
        panel.getHomepage().setText("https://new-example.com");
        
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
    
    // ========== INTEGRATION TESTS ==========
    
    @Test
    @DisplayName("Integration: Load complete packageJSON, modify all fields, save, and verify persistence")
    void testCompleteLoadModifySaveRoundTrip() {
        // Create a comprehensive package.json with DetailsPanel fields only
        JSONObject original = createCompletePackageJSON();
        
        // Load into panel
        panel.load(original);
        
        // Verify all fields are loaded correctly
        assertEquals("https://example.com", panel.getHomepage().getText());
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
        assertEquals("src", panel.getRepositoryDirectory().getText());
        assertEquals("build/libs/app.jar", panel.getJarFile().getText());
        assertTrue(panel.getRequiresJavaFX().isSelected());
        assertTrue(panel.getRequiresFullJDK().isSelected());
        assertEquals("21", panel.getJavaVersion().getSelectedItem());
        assertEquals("JetBrains Runtime (JBR)", panel.getJdkProvider().getSelectedItem());
        assertEquals("JCEF", panel.getJbrVariant().getSelectedItem());
        
        // Now modify all fields
        panel.getHomepage().setText("https://modified.com");
        panel.getRepositoryUrl().setText("https://github.com/other/repo");
        panel.getRepositoryDirectory().setText("docs");
        panel.getJarFile().setText("target/modified.jar");
        panel.getRequiresJavaFX().setSelected(false);
        panel.getRequiresFullJDK().setSelected(false);
        panel.getJavaVersion().setSelectedItem("17");
        panel.getJdkProvider().setSelectedItem("Auto (Recommended)");
        
        // Save to new JSON object
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Verify all modifications are persisted
        assertEquals("https://modified.com", saved.getString("homepage"));
        
        JSONObject savedRepo = saved.getJSONObject("repository");
        assertEquals("https://github.com/other/repo", savedRepo.getString("url"));
        assertEquals("docs", savedRepo.getString("directory"));
        
        JSONObject savedJdeploy = saved.getJSONObject("jdeploy");
        assertEquals("target/modified.jar", savedJdeploy.getString("jar"));
        assertFalse(savedJdeploy.has("javafx"));
        assertFalse(savedJdeploy.has("jdk"));
        assertEquals("17", savedJdeploy.getString("javaVersion"));
        assertFalse(savedJdeploy.has("jdkProvider"));
    }
    
    @Test
    @DisplayName("Edge case: Missing jdeploy object should be created on save")
    void testMissingJdeployObjectCreatedOnSave() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("version", "1.0.0");
        // No jdeploy object
        
        panel.load(packageJSON);
        panel.getJarFile().setText("build/libs/app.jar");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        assertTrue(saved.has("jdeploy"));
        JSONObject jdeploy = saved.getJSONObject("jdeploy");
        assertEquals("build/libs/app.jar", jdeploy.getString("jar"));
    }
    
    @Test
    @DisplayName("Edge case: Empty repository fields should not be saved")
    void testEmptyRepositoryNotSaved() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        
        panel.load(packageJSON);
        
        // Leave repository fields empty
        panel.getRepositoryUrl().setText("");
        panel.getRepositoryDirectory().setText("");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        assertFalse(saved.has("repository"));
    }
    
    @Test
    @DisplayName("Edge case: Repository URL only (no directory) should be saved")
    void testRepositoryUrlOnlyWithoutDirectory() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        
        panel.load(packageJSON);
        
        panel.getRepositoryUrl().setText("https://github.com/user/repo");
        panel.getRepositoryDirectory().setText("");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        assertTrue(saved.has("repository"));
        JSONObject repo = saved.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", repo.getString("url"));
        assertFalse(repo.has("directory"));
    }
    
    @Test
    @DisplayName("Edge case: Repository directory only (no URL) should be saved")
    void testRepositoryDirectoryOnlyWithoutUrl() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        
        panel.load(packageJSON);
        
        panel.getRepositoryUrl().setText("");
        panel.getRepositoryDirectory().setText("src");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        assertTrue(saved.has("repository"));
        JSONObject repo = saved.getJSONObject("repository");
        assertFalse(repo.has("url"));
        assertEquals("src", repo.getString("directory"));
    }
    
    @Test
    @DisplayName("Edge case: Load repository as string, save as object")
    void testRepositoryStringLoadedAsObject() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("repository", "https://github.com/user/repo");
        
        panel.load(packageJSON);
        
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
        assertEquals("", panel.getRepositoryDirectory().getText());
        
        // Add directory
        panel.getRepositoryDirectory().setText("docs");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Should be saved as object format
        JSONObject repo = saved.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", repo.getString("url"));
        assertEquals("docs", repo.getString("directory"));
    }
    
    
    @Test
    @DisplayName("Edge case: Loading with null jdeploy should not fail")
    void testLoadWithNullJdeployField() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("homepage", "https://example.com");
        
        // Don't add jdeploy object at all
        panel.load(packageJSON);
        
        assertEquals("https://example.com", panel.getHomepage().getText());
    }
    
    @Test
    @DisplayName("Edge case: Selective field modification and save")
    void testSelectiveFieldModificationAndSave() {
        JSONObject original = createCompletePackageJSON();
        panel.load(original);
        
        // Only modify specific fields
        panel.getHomepage().setText("https://new-homepage.com");
        panel.getRequiresJavaFX().setSelected(false);
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Modified fields should have new values
        assertEquals("https://new-homepage.com", saved.getString("homepage"));
        assertFalse(saved.getJSONObject("jdeploy").has("javafx"));
        
        // Unmodified fields should still be present
        assertEquals("https://github.com/user/repo", saved.getJSONObject("repository").getString("url"));
        assertEquals("build/libs/app.jar", saved.getJSONObject("jdeploy").getString("jar"));
    }
    
    @Test
    @DisplayName("Edge case: Whitespace trimming during save")
    void testWhitespaceTrimming() {
        JSONObject packageJSON = new JSONObject();
        
        panel.getHomepage().setText("  https://example.com  ");
        panel.getRepositoryUrl().setText("  https://github.com/user/repo  ");
        
        panel.save(packageJSON);
        
        assertEquals("https://example.com", packageJSON.getString("homepage"));
        assertEquals("https://github.com/user/repo", packageJSON.getJSONObject("repository").getString("url"));
    }
    
    @Test
    @DisplayName("Edge case: Boolean fields should only be saved when true")
    void testBooleanFieldsSavedOnlyWhenTrue() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        
        panel.load(packageJSON);
        
        // Both false
        panel.getRequiresJavaFX().setSelected(false);
        panel.getRequiresFullJDK().setSelected(false);
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        JSONObject jdeploy = saved.getJSONObject("jdeploy");
        assertFalse(jdeploy.has("javafx"));
        assertFalse(jdeploy.has("jdk"));
        
        // Now set to true
        panel.getRequiresJavaFX().setSelected(true);
        panel.getRequiresFullJDK().setSelected(true);
        
        JSONObject saved2 = new JSONObject();
        panel.save(saved2);
        
        JSONObject jdeploy2 = saved2.getJSONObject("jdeploy");
        assertTrue(jdeploy2.getBoolean("javafx"));
        assertTrue(jdeploy2.getBoolean("jdk"));
    }
    
    @Test
    @DisplayName("Edge case: JDK Provider switch from JBR to Auto should remove jdkProvider")
    void testJdkProviderAutoRemovesField() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdkProvider", "jbr");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        panel.load(packageJSON);
        assertEquals("JetBrains Runtime (JBR)", panel.getJdkProvider().getSelectedItem());
        
        // Switch to Auto
        panel.getJdkProvider().setSelectedItem("Auto (Recommended)");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        JSONObject savedJdeploy = saved.getJSONObject("jdeploy");
        assertFalse(savedJdeploy.has("jdkProvider"));
    }
    
    @Test
    @DisplayName("Edge case: JBR Variant should only be saved when JDK Provider is JBR")
    void testJbrVariantOnlySavedWithJbr() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        
        panel.load(packageJSON);
        
        // First part: Auto selected with variant set - variant should NOT be saved
        panel.getJdkProvider().setSelectedItem("Auto (Recommended)");
        // Variant is already set to something (Default or JCEF from previous test)
        // but it should not be saved because provider is Auto
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        JSONObject jdeploy = saved.getJSONObject("jdeploy");
        assertFalse(jdeploy.has("jbrVariant"), "jbrVariant should not be saved when provider is Auto");
        assertFalse(jdeploy.has("jdkProvider"), "jdkProvider should not be saved when set to Auto");
        
        // Second part: Switch to JBR - variant should be saved
        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");
        panel.getJbrVariant().setSelectedItem("JCEF");
        
        JSONObject saved2 = new JSONObject();
        panel.save(saved2);
        
        JSONObject jdeploy2 = saved2.getJSONObject("jdeploy");
        assertEquals("jbr", jdeploy2.getString("jdkProvider"));
        assertEquals("jcef", jdeploy2.getString("jbrVariant"));
    }
    
    // ========== HELPER METHOD ==========
    
    /**
     * Creates a complete package.json with all supported DetailsPanel fields for testing.
     */
    private JSONObject createCompletePackageJSON() {
        JSONObject packageJSON = new JSONObject();
        
        // Root level fields
        packageJSON.put("homepage", "https://example.com");
        
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        packageJSON.put("repository", (Object) repo);
        
        // jdeploy object
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "build/libs/app.jar");
        jdeploy.put("javafx", true);
        jdeploy.put("jdk", true);
        jdeploy.put("javaVersion", "21");
        jdeploy.put("jdkProvider", "jbr");
        jdeploy.put("jbrVariant", "jcef");
        packageJSON.put("jdeploy", (Object) jdeploy);
        
        return packageJSON;
    }
    
}
