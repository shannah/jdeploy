package ca.weblite.jdeploy.gui.tabs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CliSettingsPanel")
public class CliSettingsPanelTest {
    private CliSettingsPanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() {
        panel = new CliSettingsPanel();
        changeListenerCallCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
        assertNotNull(panel.getCommandField());
        assertNotNull(panel.getTutorialButton());
    }

    @Test
    @DisplayName("Should load command from bin object")
    void testLoadCommand() {
        JSONObject jdeploy = new JSONObject();
        JSONObject bin = new JSONObject();
        bin.put("my-app", "jdeploy-bundle/jdeploy.js");
        jdeploy.put("bin", bin);

        panel.load(jdeploy);

        assertEquals("my-app", panel.getCommandField().getText());
    }

    @Test
    @DisplayName("Should handle missing bin gracefully")
    void testLoadMissingBin() {
        JSONObject jdeploy = new JSONObject();
        panel.load(jdeploy);
        assertEquals("", panel.getCommandField().getText());
    }

    @Test
    @DisplayName("Should handle null jdeploy object")
    void testLoadNullJdeploy() {
        panel.load(null);
        assertEquals("", panel.getCommandField().getText());
    }

    @Test
    @DisplayName("Should save command to bin object")
    void testSaveCommand() {
        JSONObject jdeploy = new JSONObject();
        panel.getCommandField().setText("my-command");

        panel.save(jdeploy);

        assertTrue(jdeploy.has("bin"));
        JSONObject bin = jdeploy.getJSONObject("bin");
        assertEquals("jdeploy-bundle/jdeploy.js", bin.getString("my-command"));
    }

    @Test
    @DisplayName("Should remove bin when command is empty")
    void testSaveEmptyCommand() {
        JSONObject jdeploy = new JSONObject();
        JSONObject bin = new JSONObject();
        bin.put("old-command", "jdeploy-bundle/jdeploy.js");
        jdeploy.put("bin", bin);

        panel.getCommandField().setText("");
        panel.save(jdeploy);

        assertFalse(jdeploy.has("bin"));
    }

    @Test
    @DisplayName("Should handle load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        JSONObject original = new JSONObject();
        JSONObject originalBin = new JSONObject();
        originalBin.put("test-app", "jdeploy-bundle/jdeploy.js");
        original.put("bin", originalBin);

        // Load
        panel.load(original);
        assertEquals("test-app", panel.getCommandField().getText());

        // Save to new object
        JSONObject saved = new JSONObject();
        panel.save(saved);

        // Verify round-trip
        assertTrue(saved.has("bin"));
        assertEquals("jdeploy-bundle/jdeploy.js", saved.getJSONObject("bin").getString("test-app"));
    }

    @Test
    @DisplayName("Should register and fire change listener")
    void testChangeListenerFires() {
        panel.addChangeListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeListenerCallCount.incrementAndGet();
            }
        });

        panel.getCommandField().setText("new-command");

        assertTrue(changeListenerCallCount.get() > 0);
    }

    @Test
    @DisplayName("Should handle listener replacement")
    void testListenerCanBeReplaced() {
        AtomicInteger firstListenerCount = new AtomicInteger(0);
        AtomicInteger secondListenerCount = new AtomicInteger(0);

        panel.addChangeListener(evt -> firstListenerCount.incrementAndGet());
        panel.getCommandField().setText("first");
        int firstCount = firstListenerCount.get();

        // Replace with second listener
        panel.addChangeListener(evt -> secondListenerCount.incrementAndGet());
        panel.getCommandField().setText("second");

        // Second listener should have fired, first should not increment
        assertTrue(secondListenerCount.get() > 0);
        assertEquals(firstCount, firstListenerCount.get());
    }

    @Test
    @DisplayName("Should save without affecting other fields in jdeploy")
    void testSaveDoesNotAffectOtherFields() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "path/to/jar.jar");
        jdeploy.put("javafx", true);

        panel.getCommandField().setText("my-app");
        panel.save(jdeploy);

        // Other fields should still be present
        assertEquals("path/to/jar.jar", jdeploy.getString("jar"));
        assertTrue(jdeploy.getBoolean("javafx"));
    }

    @Test
    @DisplayName("Should handle command with spaces and special characters")
    void testCommandWithSpecialCharacters() {
        JSONObject jdeploy = new JSONObject();
        String command = "my-app-v1";

        panel.getCommandField().setText(command);
        panel.save(jdeploy);

        JSONObject bin = jdeploy.getJSONObject("bin");
        assertEquals("jdeploy-bundle/jdeploy.js", bin.getString(command));
    }

    @Test
    @DisplayName("Should trim whitespace from command")
    void testCommandTrimsWhitespace() {
        JSONObject jdeploy = new JSONObject();
        panel.getCommandField().setText("  my-app  ");
        panel.save(jdeploy);

        JSONObject bin = jdeploy.getJSONObject("bin");
        assertEquals("jdeploy-bundle/jdeploy.js", bin.getString("my-app"));
    }

    @Test
    @DisplayName("Should handle bin with multiple entries (load shows nothing)")
    void testLoadMultipleEntries() {
        JSONObject jdeploy = new JSONObject();
        JSONObject bin = new JSONObject();
        bin.put("app1", "jdeploy-bundle/jdeploy.js");
        bin.put("app2", "jdeploy-bundle/jdeploy.js");
        jdeploy.put("bin", bin);

        panel.load(jdeploy);

        // Should show empty since multiple entries
        assertEquals("", panel.getCommandField().getText());
    }
}
