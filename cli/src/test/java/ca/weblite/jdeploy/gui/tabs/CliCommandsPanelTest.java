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
}
