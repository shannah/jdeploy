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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepositorySettingsPanel")
class RepositorySettingsPanelTest {
    
    private RepositorySettingsPanel panel;
    private AtomicInteger changeListenerCallCount;
    private AtomicReference<String> verifyCalledWith;
    
    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI test in headless environment");
        changeListenerCallCount = new AtomicInteger(0);
        verifyCalledWith = new AtomicReference<>(null);
        
        HomepageVerifier verifier = homepage -> verifyCalledWith.set(homepage);
        panel = new RepositorySettingsPanel(new JFrame("Test"), verifier);
    }
    
    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
    }
    
    // ========== LOAD TESTS ==========
    
    @Test
    @DisplayName("Should load homepage from root packageJSON")
    void testLoadHomepage() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("homepage", "https://example.com");
        
        panel.load(packageJSON);
        
        assertEquals("https://example.com", panel.getHomepage().getText());
    }
    
    @Test
    @DisplayName("Should load repository URL as string (legacy format)")
    void testLoadRepositoryAsString() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("repository", "https://github.com/user/repo");
        
        panel.load(packageJSON);
        
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
        assertEquals("", panel.getRepositoryDirectory().getText());
    }
    
    @Test
    @DisplayName("Should load repository from JSONObject format")
    void testLoadRepositoryAsJsonObject() {
        JSONObject packageJSON = new JSONObject();
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        packageJSON.put("repository", repo);
        
        panel.load(packageJSON);
        
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
        assertEquals("src", panel.getRepositoryDirectory().getText());
    }
    
    @Test
    @DisplayName("Should load repository JSONObject with only URL")
    void testLoadRepositoryJsonObjectUrlOnly() {
        JSONObject packageJSON = new JSONObject();
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        packageJSON.put("repository", repo);
        
        panel.load(packageJSON);
        
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
        assertEquals("", panel.getRepositoryDirectory().getText());
    }
    
    @Test
    @DisplayName("Should load repository JSONObject with only directory")
    void testLoadRepositoryJsonObjectDirectoryOnly() {
        JSONObject packageJSON = new JSONObject();
        JSONObject repo = new JSONObject();
        repo.put("directory", "src");
        packageJSON.put("repository", repo);
        
        panel.load(packageJSON);
        
        assertEquals("", panel.getRepositoryUrl().getText());
        assertEquals("src", panel.getRepositoryDirectory().getText());
    }
    
    @Test
    @DisplayName("Should handle null packageJSON gracefully")
    void testLoadNullPackageJSON() {
        panel.load(null);
        // Should not throw exception
        assertEquals("", panel.getHomepage().getText());
    }
    
    @Test
    @DisplayName("Should handle missing repository field")
    void testLoadMissingRepository() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("homepage", "https://example.com");
        
        panel.load(packageJSON);
        
        assertEquals("https://example.com", panel.getHomepage().getText());
        assertEquals("", panel.getRepositoryUrl().getText());
        assertEquals("", panel.getRepositoryDirectory().getText());
    }
    
    @Test
    @DisplayName("Should handle missing homepage field")
    void testLoadMissingHomepage() {
        JSONObject packageJSON = new JSONObject();
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        packageJSON.put("repository", repo);
        
        panel.load(packageJSON);
        
        assertEquals("", panel.getHomepage().getText());
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
    }
    
    // ========== SAVE TESTS ==========
    
    @Test
    @DisplayName("Should save homepage to root packageJSON")
    void testSaveHomepage() {
        JSONObject packageJSON = new JSONObject();
        panel.getHomepage().setText("https://example.com");
        
        panel.save(packageJSON);
        
        assertEquals("https://example.com", packageJSON.getString("homepage"));
    }
    
    @Test
    @DisplayName("Should save repository as JSONObject")
    void testSaveRepositoryAsJsonObject() {
        JSONObject packageJSON = new JSONObject();
        panel.getRepositoryUrl().setText("https://github.com/user/repo");
        panel.getRepositoryDirectory().setText("src");
        
        panel.save(packageJSON);
        
        JSONObject repo = packageJSON.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", repo.getString("url"));
        assertEquals("src", repo.getString("directory"));
    }
    
    @Test
    @DisplayName("Should save repository with only URL")
    void testSaveRepositoryUrlOnly() {
        JSONObject packageJSON = new JSONObject();
        panel.getRepositoryUrl().setText("https://github.com/user/repo");
        panel.getRepositoryDirectory().setText("");
        
        panel.save(packageJSON);
        
        JSONObject repo = packageJSON.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", repo.getString("url"));
        assertFalse(repo.has("directory"));
    }
    
    @Test
    @DisplayName("Should save repository with only directory")
    void testSaveRepositoryDirectoryOnly() {
        JSONObject packageJSON = new JSONObject();
        panel.getRepositoryUrl().setText("");
        panel.getRepositoryDirectory().setText("src");
        
        panel.save(packageJSON);
        
        JSONObject repo = packageJSON.getJSONObject("repository");
        assertFalse(repo.has("url"));
        assertEquals("src", repo.getString("directory"));
    }
    
    @Test
    @DisplayName("Should not save empty repository field")
    void testSaveEmptyRepository() {
        JSONObject packageJSON = new JSONObject();
        panel.getRepositoryUrl().setText("");
        panel.getRepositoryDirectory().setText("");
        
        panel.save(packageJSON);
        
        assertFalse(packageJSON.has("repository"));
    }
    
    @Test
    @DisplayName("Should not save empty homepage field")
    void testSaveEmptyHomepage() {
        JSONObject packageJSON = new JSONObject();
        panel.getHomepage().setText("");
        
        panel.save(packageJSON);
        
        assertFalse(packageJSON.has("homepage"));
    }
    
    @Test
    @DisplayName("Should handle null packageJSON gracefully")
    void testSaveNullPackageJSON() {
        panel.save(null);
        // Should not throw exception
    }
    
    @Test
    @DisplayName("Should trim whitespace from fields during save")
    void testSaveTrimWhitespace() {
        JSONObject packageJSON = new JSONObject();
        panel.getHomepage().setText("  https://example.com  ");
        panel.getRepositoryUrl().setText("  https://github.com/user/repo  ");
        panel.getRepositoryDirectory().setText("  src  ");
        
        panel.save(packageJSON);
        
        assertEquals("https://example.com", packageJSON.getString("homepage"));
        JSONObject repo = packageJSON.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", repo.getString("url"));
        assertEquals("src", repo.getString("directory"));
    }
    
    // ========== LOAD/SAVE ROUND-TRIP TESTS ==========
    
    @Test
    @DisplayName("Should handle load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        JSONObject original = new JSONObject();
        original.put("homepage", "https://example.com");
        
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        original.put("repository", repo);
        
        // Load
        panel.load(original);
        
        // Save to new object
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Verify
        assertEquals("https://example.com", saved.getString("homepage"));
        JSONObject savedRepo = saved.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", savedRepo.getString("url"));
        assertEquals("src", savedRepo.getString("directory"));
    }
    
    @Test
    @DisplayName("Should convert repository string format to JSONObject on save")
    void testRepositoryStringConvertedToJsonObject() {
        JSONObject original = new JSONObject();
        original.put("repository", "https://github.com/user/repo");
        
        // Load string format
        panel.load(original);
        
        // Modify and save
        panel.getRepositoryDirectory().setText("docs");
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Verify it's now saved as JSONObject
        JSONObject repo = saved.getJSONObject("repository");
        assertEquals("https://github.com/user/repo", repo.getString("url"));
        assertEquals("docs", repo.getString("directory"));
    }
    
    // ========== CHANGE LISTENER TESTS ==========
    
    @Test
    @DisplayName("Should register and fire change listener on homepage changes")
    void testChangeListenerFiresOnHomepage() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        panel.getHomepage().setText("https://new-example.com");
        
        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called");
    }
    
    @Test
    @DisplayName("Should register and fire change listener on repository URL changes")
    void testChangeListenerFiresOnRepositoryUrl() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        panel.getRepositoryUrl().setText("https://github.com/user/repo");
        
        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called");
    }
    
    @Test
    @DisplayName("Should register and fire change listener on repository directory changes")
    void testChangeListenerFiresOnRepositoryDirectory() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        panel.getRepositoryDirectory().setText("src");
        
        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called");
    }
    
    @Test
    @DisplayName("Should handle multiple field changes triggering listener")
    void testChangeListenerMultipleFields() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        panel.getHomepage().setText("https://example.com");
        int firstCount = changeListenerCallCount.get();
        
        panel.getRepositoryUrl().setText("https://github.com/user/repo");
        int secondCount = changeListenerCallCount.get();
        
        panel.getRepositoryDirectory().setText("src");
        int thirdCount = changeListenerCallCount.get();
        
        assertTrue(firstCount > 0, "First change should trigger listener");
        assertTrue(secondCount > firstCount, "Second change should trigger listener again");
        assertTrue(thirdCount > secondCount, "Third change should trigger listener again");
    }
    
    // ========== VERIFY BUTTON TESTS ==========
    
    @Test
    @DisplayName("Should call HomepageVerifier when verify button is clicked")
    void testVerifyButtonCallsCallback() {
        panel.getHomepage().setText("https://example.com");
        
        panel.getVerifyButton().doClick();
        
        assertEquals("https://example.com", verifyCalledWith.get());
    }
    
    @Test
    @DisplayName("Should trim homepage when calling verifier")
    void testVerifyButtonTrimsHomepage() {
        panel.getHomepage().setText("  https://example.com  ");
        
        panel.getVerifyButton().doClick();
        
        assertEquals("https://example.com", verifyCalledWith.get());
    }
    
    @Test
    @DisplayName("Should verify empty homepage")
    void testVerifyButtonEmptyHomepage() {
        panel.getHomepage().setText("");
        
        panel.getVerifyButton().doClick();
        
        assertEquals("", verifyCalledWith.get());
    }
    
    // ========== VISIBILITY TESTS ==========
    
    @Test
    @DisplayName("Should set verify button visibility")
    void testSetVerifyButtonVisible() {
        assertTrue(panel.getVerifyButton().isVisible(), "Verify button should be visible by default");
        
        panel.setVerifyButtonVisible(false);
        assertFalse(panel.getVerifyButton().isVisible());
        
        panel.setVerifyButtonVisible(true);
        assertTrue(panel.getVerifyButton().isVisible());
    }
    
    // ========== INTEGRATION TESTS ==========
    
    @Test
    @DisplayName("Integration: Complete load, modify, save cycle")
    void testCompleteLoadModifySaveCycle() {
        // Create original package.json
        JSONObject original = new JSONObject();
        original.put("homepage", "https://example.com");
        
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        original.put("repository", repo);
        
        // Load
        panel.load(original);
        assertEquals("https://example.com", panel.getHomepage().getText());
        assertEquals("https://github.com/user/repo", panel.getRepositoryUrl().getText());
        assertEquals("src", panel.getRepositoryDirectory().getText());
        
        // Modify all fields
        panel.getHomepage().setText("https://modified.com");
        panel.getRepositoryUrl().setText("https://github.com/other/repo");
        panel.getRepositoryDirectory().setText("docs");
        
        // Save
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Verify modifications persisted
        assertEquals("https://modified.com", saved.getString("homepage"));
        JSONObject savedRepo = saved.getJSONObject("repository");
        assertEquals("https://github.com/other/repo", savedRepo.getString("url"));
        assertEquals("docs", savedRepo.getString("directory"));
    }
    
    @Test
    @DisplayName("Integration: Selective field modification and save")
    void testSelectiveFieldModification() {
        JSONObject original = new JSONObject();
        original.put("homepage", "https://example.com");
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        repo.put("directory", "src");
        original.put("repository", repo);
        
        panel.load(original);
        
        // Only modify homepage
        panel.getHomepage().setText("https://new-homepage.com");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Modified field should have new value
        assertEquals("https://new-homepage.com", saved.getString("homepage"));
        
        // Unmodified fields should still be present
        assertEquals("https://github.com/user/repo", saved.getJSONObject("repository").getString("url"));
        assertEquals("src", saved.getJSONObject("repository").getString("directory"));
    }
    
    @Test
    @DisplayName("Edge case: Clearing all fields after load")
    void testClearingAllFields() {
        JSONObject original = new JSONObject();
        original.put("homepage", "https://example.com");
        JSONObject repo = new JSONObject();
        repo.put("url", "https://github.com/user/repo");
        original.put("repository", repo);
        
        panel.load(original);
        
        // Clear all fields
        panel.getHomepage().setText("");
        panel.getRepositoryUrl().setText("");
        panel.getRepositoryDirectory().setText("");
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // All fields should be removed
        assertFalse(saved.has("homepage"));
        assertFalse(saved.has("repository"));
    }
}
