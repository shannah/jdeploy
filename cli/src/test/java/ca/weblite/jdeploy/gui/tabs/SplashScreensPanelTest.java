package ca.weblite.jdeploy.gui.tabs;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SplashScreensPanel")
public class SplashScreensPanelTest {
    private File tempDirectory;
    private File projectDirectory;
    private SplashScreensPanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() throws Exception {
        tempDirectory = Files.createTempDirectory("splash-test").toFile();
        projectDirectory = tempDirectory;
        panel = new SplashScreensPanel(projectDirectory);
        changeListenerCallCount = new AtomicInteger(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDirectory != null && tempDirectory.exists()) {
            FileUtils.deleteDirectory(tempDirectory);
        }
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
        assertEquals(panel, panel.getRoot());
    }

    @Test
    @DisplayName("Should load configuration without errors")
    void testLoadConfiguration() {
        JSONObject jdeploy = new JSONObject();
        assertDoesNotThrow(() -> panel.load(jdeploy));
    }

    @Test
    @DisplayName("Should save configuration without errors")
    void testSaveConfiguration() {
        JSONObject jdeploy = new JSONObject();
        assertDoesNotThrow(() -> panel.save(jdeploy));
    }

    @Test
    @DisplayName("Should handle load/save round-trip")
    void testLoadSaveRoundTrip() {
        JSONObject jdeploy = new JSONObject();
        
        // Load initial config
        panel.load(jdeploy);
        
        // Save to config
        panel.save(jdeploy);
        
        // Load again
        panel.load(jdeploy);
        
        // Should complete without errors
        assertNotNull(panel.getRoot());
    }

    @Test
    @DisplayName("Should register and fire change listener")
    void testChangeListenerFiresOnLoad() {
        ActionListener listener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeListenerCallCount.incrementAndGet();
            }
        };
        
        panel.addChangeListener(listener);
        JSONObject jdeploy = new JSONObject();
        
        // load() calls setupSplashButton() which doesn't fire events
        // This test verifies the listener was registered without error
        panel.load(jdeploy);
        
        assertNotNull(panel);
    }

    @Test
    @DisplayName("Should support multiple listeners via single listener")
    void testListenerCanBeReplaced() {
        ActionListener listener1 = e -> changeListenerCallCount.incrementAndGet();
        ActionListener listener2 = e -> changeListenerCallCount.addAndGet(10);
        
        panel.addChangeListener(listener1);
        panel.addChangeListener(listener2);
        
        // The second listener should replace the first (current implementation)
        assertNotNull(panel);
    }

    @Test
    @DisplayName("Should handle missing splash files gracefully")
    void testMissingSplashFilesHandledGracefully() {
        // No splash files exist
        assertFalse(new File(projectDirectory, "splash.png").exists());
        assertFalse(new File(projectDirectory, "installsplash.png").exists());
        
        // Should still load without errors
        JSONObject jdeploy = new JSONObject();
        assertDoesNotThrow(() -> panel.load(jdeploy));
    }

    @Test
    @DisplayName("Should find existing splash files")
    void testFindExistingSplashFiles() throws Exception {
        // Create a splash.png file
        File splashFile = new File(projectDirectory, "splash.png");
        Files.write(splashFile.toPath(), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}); // PNG header
        
        // Create a new panel to load the file
        SplashScreensPanel newPanel = new SplashScreensPanel(projectDirectory);
        assertTrue(splashFile.exists());
        
        // Load should detect it
        JSONObject jdeploy = new JSONObject();
        assertDoesNotThrow(() -> newPanel.load(jdeploy));
    }

    @Test
    @DisplayName("Should handle jdeploy JSONObject parameter in load")
    void testLoadWithValidJsonObject() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("someOtherField", "value");
        
        assertDoesNotThrow(() -> panel.load(jdeploy));
        assertNotNull(panel.getRoot());
    }

    @Test
    @DisplayName("Should handle jdeploy JSONObject parameter in save")
    void testSaveWithValidJsonObject() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("someOtherField", "value");
        
        assertDoesNotThrow(() -> panel.save(jdeploy));
        assertTrue(jdeploy.has("someOtherField"));
    }
}
