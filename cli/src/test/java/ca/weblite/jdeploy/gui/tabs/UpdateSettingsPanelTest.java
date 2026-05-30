package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UpdateSettingsPanel")
public class UpdateSettingsPanelTest implements ActionListener {
    private UpdateSettingsPanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() {
        panel = new UpdateSettingsPanel();
        changeListenerCallCount = new AtomicInteger(0);
        panel.addChangeListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        changeListenerCallCount.incrementAndGet();
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
        assertEquals(panel, panel.getRoot());
    }

    // --- Auto-update mode ---

    @Test
    @DisplayName("Should default to auto mode when jdeploy is null")
    void testLoadNullDefaultsToAuto() {
        panel.load(null);
        JSONObject out = new JSONObject();
        panel.save(out);
        assertFalse(out.has(UpdateSettingsPanel.KEY_APP_UPDATE_MODE),
                "auto is the default and should not be written");
    }

    @Test
    @DisplayName("Should default to auto mode when appUpdateMode is absent")
    void testLoadMissingDefaultsToAuto() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("other", "value");
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertFalse(out.has(UpdateSettingsPanel.KEY_APP_UPDATE_MODE));
    }

    @Test
    @DisplayName("Should load prompt mode")
    void testLoadPromptMode() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_APP_UPDATE_MODE, "prompt");
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertEquals("prompt", out.getString(UpdateSettingsPanel.KEY_APP_UPDATE_MODE));
    }

    @Test
    @DisplayName("Should remove appUpdateMode when reverted to auto")
    void testSaveAutoRemovesKey() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_APP_UPDATE_MODE, "prompt");
        panel.load(jdeploy);
        // Switch back to auto
        panel.load(new JSONObject());

        JSONObject out = new JSONObject();
        out.put(UpdateSettingsPanel.KEY_APP_UPDATE_MODE, "prompt");
        panel.save(out);
        assertFalse(out.has(UpdateSettingsPanel.KEY_APP_UPDATE_MODE));
    }

    // --- Minimum initial app version ---

    @Test
    @DisplayName("Should default to no minimum")
    void testLoadNoMinimum() {
        panel.load(new JSONObject());
        JSONObject out = new JSONObject();
        panel.save(out);
        assertFalse(out.has(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION));
        assertFalse(out.has(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION_MODE));
    }

    @Test
    @DisplayName("Should load and round-trip an explicit minimum version")
    void testLoadExplicitMinimum() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION, "1.4.0");
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertEquals("1.4.0", out.getString(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION));
        assertFalse(out.has(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION_MODE));
    }

    @Test
    @DisplayName("Should load and round-trip the latest sentinel mode")
    void testLoadLatestSentinel() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION_MODE, "latest");
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertEquals("latest", out.getString(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION_MODE));
        assertFalse(out.has(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION));
    }

    @Test
    @DisplayName("Latest sentinel should take precedence over explicit version on load")
    void testLoadLatestSentinelPrecedence() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION, "1.0.0");
        jdeploy.put(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION_MODE, "latest");
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertEquals("latest", out.getString(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION_MODE));
        assertFalse(out.has(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION));
    }

    @Test
    @DisplayName("Should trim whitespace from explicit version on save")
    void testSaveTrimsExplicitVersion() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION, "  2.0.1  ");
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertEquals("2.0.1", out.getString(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION));
    }

    // --- Require launcher update ---

    @Test
    @DisplayName("Should persist requireLauncherUpdate only when a minimum is set")
    void testRequireLauncherUpdateNeedsMinimum() {
        JSONObject jdeploy = new JSONObject();
        // requireLauncherUpdate true but no minimum -> should be dropped
        jdeploy.put(UpdateSettingsPanel.KEY_REQUIRE_LAUNCHER_UPDATE, true);
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertFalse(out.has(UpdateSettingsPanel.KEY_REQUIRE_LAUNCHER_UPDATE),
                "requireLauncherUpdate is meaningless without a minimum and should be removed");
    }

    @Test
    @DisplayName("Should persist requireLauncherUpdate with an explicit minimum")
    void testRequireLauncherUpdateWithMinimum() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION, "1.4.0");
        jdeploy.put(UpdateSettingsPanel.KEY_REQUIRE_LAUNCHER_UPDATE, true);
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertTrue(out.getBoolean(UpdateSettingsPanel.KEY_REQUIRE_LAUNCHER_UPDATE));
        assertEquals("1.4.0", out.getString(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION));
    }

    // --- Round trip & isolation ---

    @Test
    @DisplayName("Should round-trip a fully populated config without data loss")
    void testFullRoundTrip() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_APP_UPDATE_MODE, "prompt");
        jdeploy.put(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION, "3.2.1");
        jdeploy.put(UpdateSettingsPanel.KEY_REQUIRE_LAUNCHER_UPDATE, true);
        panel.load(jdeploy);

        JSONObject out = new JSONObject();
        panel.save(out);
        assertEquals("prompt", out.getString(UpdateSettingsPanel.KEY_APP_UPDATE_MODE));
        assertEquals("3.2.1", out.getString(UpdateSettingsPanel.KEY_MIN_INITIAL_VERSION));
        assertTrue(out.getBoolean(UpdateSettingsPanel.KEY_REQUIRE_LAUNCHER_UPDATE));
    }

    @Test
    @DisplayName("Should not affect unrelated jdeploy fields on save")
    void testSaveDoesNotAffectOtherFields() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("name", "my-app");
        jdeploy.put(UpdateSettingsPanel.KEY_APP_UPDATE_MODE, "prompt");
        panel.load(jdeploy);

        panel.save(jdeploy);
        assertEquals("my-app", jdeploy.getString("name"));
    }

    @Test
    @DisplayName("Should handle null jdeploy on save without throwing")
    void testSaveNullJdeploy() {
        panel.load(new JSONObject());
        panel.save(null);
    }

    // --- Change listener ---

    @Test
    @DisplayName("Should fire change listener when toggling prompt mode")
    void testChangeListenerFires() {
        int initial = changeListenerCallCount.get();
        panel.load(new JSONObject());
        JSONObject jdeploy = new JSONObject();
        jdeploy.put(UpdateSettingsPanel.KEY_APP_UPDATE_MODE, "prompt");
        panel.load(jdeploy);
        assertTrue(changeListenerCallCount.get() > initial);
    }
}
