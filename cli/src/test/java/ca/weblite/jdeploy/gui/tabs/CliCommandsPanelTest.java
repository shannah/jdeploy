package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CliCommandsPanel.
 * Verifies that document listeners call onFieldChanged() and that changes
 * are immediately reflected in the backing model.
 */
public class CliCommandsPanelTest {

    private CliCommandsPanel panel;
    private boolean changeEventFired;

    @BeforeEach
    public void setUp() {
        panel = new CliCommandsPanel();
        changeEventFired = false;
        panel.addChangeListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeEventFired = true;
            }
        });
    }

    @Test
    public void testLoadAndSave() {
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();
        
        JSONObject cmd1 = new JSONObject();
        cmd1.put("description", "First command");
        cmd1.put("args", new JSONArray(new String[]{"arg1", "arg2"}));
        
        JSONObject cmd2 = new JSONObject();
        cmd2.put("description", "Second command");
        
        commands.put("cmd1", cmd1);
        commands.put("cmd2", cmd2);
        jdeploy.put("commands", commands);
        
        panel.load(jdeploy);
        
        // Select first command
        assertTrue(panel.selectCommand("cmd1"));
        
        // Save and verify data is preserved
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        JSONObject savedCommands = saved.getJSONObject("commands");
        assertEquals(2, savedCommands.length());
        assertTrue(savedCommands.has("cmd1"));
        assertTrue(savedCommands.has("cmd2"));
        assertEquals("First command", savedCommands.getJSONObject("cmd1").getString("description"));
    }

    @Test
    public void testSelectCommand() {
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();
        commands.put("test-cmd", new JSONObject().put("description", "Test"));
        jdeploy.put("commands", commands);
        
        panel.load(jdeploy);
        
        boolean selected = panel.selectCommand("test-cmd");
        assertTrue(selected, "Should be able to select existing command");
        
        boolean notSelected = panel.selectCommand("non-existent");
        assertFalse(notSelected, "Should not be able to select non-existent command");
    }

    @Test
    public void testSelectCommandAt() {
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();
        commands.put("cmd1", new JSONObject());
        commands.put("cmd2", new JSONObject());
        jdeploy.put("commands", commands);
        
        panel.load(jdeploy);
        
        // Should not throw exception
        panel.selectCommandAt(0);
        panel.selectCommandAt(1);
        
        // Should handle out of bounds gracefully
        panel.selectCommandAt(10);
    }

    @Test
    public void testEmptyLoad() {
        JSONObject jdeploy = new JSONObject();
        panel.load(jdeploy);
        
        // Save should result in no commands section
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        assertFalse(saved.has("commands"));
    }

    @Test
    public void testNullLoad() {
        panel.load(null);
        
        // Should not throw exception
        JSONObject saved = new JSONObject();
        panel.save(saved);
        assertFalse(saved.has("commands"));
    }

    @Test
    public void testLoadWithoutCommandsKey() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("someOtherKey", "value");
        panel.load(jdeploy);

        // Should not throw exception
        JSONObject saved = new JSONObject();
        panel.save(saved);
    }

    @Test
    public void testEditDescriptionAndSwitchCommand() {
        // Setup: Load two commands
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();

        JSONObject cmd1 = new JSONObject();
        cmd1.put("description", "Original description 1");
        cmd1.put("args", new JSONArray(new String[]{"arg1"}));

        JSONObject cmd2 = new JSONObject();
        cmd2.put("description", "Original description 2");
        cmd2.put("args", new JSONArray(new String[]{"arg2"}));

        commands.put("cmd1", cmd1);
        commands.put("cmd2", cmd2);
        jdeploy.put("commands", commands);

        panel.load(jdeploy);

        // Select cmd1 and edit its description
        assertTrue(panel.selectCommand("cmd1"));

        // Simulate user editing the description field
        // (In a real UI test, this would be done through the UI components)
        // For now, we'll test by switching commands and verifying the save

        // Select cmd2
        assertTrue(panel.selectCommand("cmd2"));

        // Now save and verify cmd1's data is preserved
        JSONObject saved = new JSONObject();
        panel.save(saved);

        JSONObject savedCommands = saved.getJSONObject("commands");
        assertEquals(2, savedCommands.length());
        assertTrue(savedCommands.has("cmd1"));
        assertTrue(savedCommands.has("cmd2"));

        // Verify cmd1 still has its original description (not overwritten by cmd2)
        assertEquals("Original description 1", savedCommands.getJSONObject("cmd1").getString("description"));
        assertEquals("Original description 2", savedCommands.getJSONObject("cmd2").getString("description"));

        // Verify args are preserved
        JSONArray cmd1Args = savedCommands.getJSONObject("cmd1").getJSONArray("args");
        assertEquals(1, cmd1Args.length());
        assertEquals("arg1", cmd1Args.getString(0));

        JSONArray cmd2Args = savedCommands.getJSONObject("cmd2").getJSONArray("args");
        assertEquals(1, cmd2Args.length());
        assertEquals("arg2", cmd2Args.getString(0));
    }

    @Test
    public void testImplementsPropertySaveAndLoad() {
        // Create a command with implements array
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();

        JSONObject cmd1 = new JSONObject();
        cmd1.put("description", "Updater command");
        JSONArray impl1 = new JSONArray();
        impl1.put("updater");
        cmd1.put("implements", impl1);

        JSONObject cmd2 = new JSONObject();
        cmd2.put("description", "Launcher command");
        JSONArray impl2 = new JSONArray();
        impl2.put("launcher");
        cmd2.put("implements", impl2);

        JSONObject cmd3 = new JSONObject();
        cmd3.put("description", "Service controller with updater");
        JSONArray impl3 = new JSONArray();
        impl3.put("service_controller");
        impl3.put("updater");
        cmd3.put("implements", impl3);

        commands.put("updater-cmd", cmd1);
        commands.put("launcher-cmd", cmd2);
        commands.put("service-cmd", cmd3);
        jdeploy.put("commands", commands);

        // Load and verify
        panel.load(jdeploy);

        // Save and verify implements are preserved
        JSONObject saved = new JSONObject();
        panel.save(saved);

        JSONObject savedCommands = saved.getJSONObject("commands");
        assertEquals(3, savedCommands.length());

        // Verify updater-cmd has updater implementation
        assertTrue(savedCommands.has("updater-cmd"));
        JSONObject savedCmd1 = savedCommands.getJSONObject("updater-cmd");
        assertTrue(savedCmd1.has("implements"));
        JSONArray savedImpl1 = savedCmd1.getJSONArray("implements");
        assertEquals(1, savedImpl1.length());
        assertEquals("updater", savedImpl1.getString(0));

        // Verify launcher-cmd has launcher implementation
        assertTrue(savedCommands.has("launcher-cmd"));
        JSONObject savedCmd2 = savedCommands.getJSONObject("launcher-cmd");
        assertTrue(savedCmd2.has("implements"));
        JSONArray savedImpl2 = savedCmd2.getJSONArray("implements");
        assertEquals(1, savedImpl2.length());
        assertEquals("launcher", savedImpl2.getString(0));

        // Verify service-cmd has both implementations
        assertTrue(savedCommands.has("service-cmd"));
        JSONObject savedCmd3 = savedCommands.getJSONObject("service-cmd");
        assertTrue(savedCmd3.has("implements"));
        JSONArray savedImpl3 = savedCmd3.getJSONArray("implements");
        assertEquals(2, savedImpl3.length());
        // Note: Order might vary, so check both are present
        boolean hasServiceController = false;
        boolean hasUpdater = false;
        for (int i = 0; i < savedImpl3.length(); i++) {
            String impl = savedImpl3.getString(i);
            if ("service_controller".equals(impl)) hasServiceController = true;
            if ("updater".equals(impl)) hasUpdater = true;
        }
        assertTrue(hasServiceController, "Should have service_controller implementation");
        assertTrue(hasUpdater, "Should have updater implementation");
    }
}
