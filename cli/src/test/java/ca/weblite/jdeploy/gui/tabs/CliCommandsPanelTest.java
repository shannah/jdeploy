package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CliCommandsPanel.
 * Verifies that the description and args fields update correctly when selecting different commands.
 */
public class CliCommandsPanelTest {

    private CliCommandsPanel panel;

    @BeforeEach
    public void setUp() {
        panel = new CliCommandsPanel();
    }

    @Test
    public void testLoadAndSelectCommandUpdatesFields() throws Exception {
        // Create test data with two commands
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();

        JSONObject cmd1 = new JSONObject();
        cmd1.put("description", "First command description");
        cmd1.put("args", new JSONArray().put("--arg1").put("--arg2"));
        commands.put("command1", cmd1);

        JSONObject cmd2 = new JSONObject();
        cmd2.put("description", "Second command description");
        cmd2.put("args", new JSONArray().put("--other"));
        commands.put("command2", cmd2);

        jdeploy.put("commands", commands);

        // Load the commands into the panel
        panel.load(jdeploy);

        // Get references to private fields using reflection
        JList<String> commandList = getPrivateField(panel, "commandList");
        JTextField descriptionField = getPrivateField(panel, "descriptionField");
        JTextArea argsField = getPrivateField(panel, "argsField");

        assertNotNull(commandList, "commandList should not be null");
        assertNotNull(descriptionField, "descriptionField should not be null");
        assertNotNull(argsField, "argsField should not be null");

        // Verify that both commands are loaded in the list
        assertEquals(2, commandList.getModel().getSize(), "Should have 2 commands loaded");

        // Select command1 using the public API
        assertTrue(panel.selectCommand("command1"), "Should find and select command1");

        // Verify command1's fields
        assertEquals("First command description", descriptionField.getText(),
                "Description field should contain 'First command description'");
        assertEquals("--arg1\n--arg2", argsField.getText(),
                "Args field should contain '--arg1\\n--arg2'");

        // Select command2 using the public API
        assertTrue(panel.selectCommand("command2"), "Should find and select command2");

        // Verify command2's fields
        assertEquals("Second command description", descriptionField.getText(),
                "Description field should contain 'Second command description'");
        assertEquals("--other", argsField.getText(),
                "Args field should contain '--other'");
    }

    @Test
    public void testSelectingCommandWithoutArgsShowsEmptyArgsField() throws Exception {
        // Create test data with one command without args
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();

        JSONObject cmd1 = new JSONObject();
        cmd1.put("description", "Command without args");
        commands.put("command1", cmd1);

        jdeploy.put("commands", commands);

        // Load the commands into the panel
        panel.load(jdeploy);

        // Get references to private fields
        JTextArea argsField = getPrivateField(panel, "argsField");

        // Select command1 using the public API
        panel.selectCommand("command1");

        // Verify args field is empty
        assertEquals("", argsField.getText(),
                "Args field should be empty when command has no args");
    }

    @Test
    public void testSelectingCommandWithoutDescriptionShowsEmptyDescriptionField() throws Exception {
        // Create test data with one command without description
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();

        JSONObject cmd1 = new JSONObject();
        cmd1.put("args", new JSONArray().put("--arg1"));
        commands.put("command1", cmd1);

        jdeploy.put("commands", commands);

        // Load the commands into the panel
        panel.load(jdeploy);

        // Get references to private fields
        JTextField descriptionField = getPrivateField(panel, "descriptionField");

        // Select command1 using the public API
        panel.selectCommand("command1");

        // Verify description field is empty
        assertEquals("", descriptionField.getText(),
                "Description field should be empty when command has no description");
    }

    @Test
    public void testMultipleArgsDisplayedCorrectly() throws Exception {
        // Create test data with a command having multiple args
        JSONObject jdeploy = new JSONObject();
        JSONObject commands = new JSONObject();

        JSONObject cmd1 = new JSONObject();
        cmd1.put("description", "Multi-arg command");
        cmd1.put("args", new JSONArray()
                .put("--verbose")
                .put("--output")
                .put("--config")
                .put("--debug"));
        commands.put("command1", cmd1);

        jdeploy.put("commands", commands);

        // Load the commands into the panel
        panel.load(jdeploy);

        // Get references to private fields
        JTextArea argsField = getPrivateField(panel, "argsField");

        // Select command1 using the public API
        panel.selectCommand("command1");

        // Verify args field contains all arguments separated by newlines
        String expectedArgs = "--verbose\n--output\n--config\n--debug";
        assertEquals(expectedArgs, argsField.getText(),
                "Args field should contain all arguments separated by newlines");
    }

    /**
     * Helper method to access private fields via reflection.
     */
    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(obj);
    }

}
