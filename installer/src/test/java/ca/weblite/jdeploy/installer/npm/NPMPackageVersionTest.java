package ca.weblite.jdeploy.installer.npm;

import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.jdeploy.models.HelperAction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NPMPackageVersion.getCommands().
 * Tests verify correct delegation to CommandSpecParser and edge case handling.
 */
public class NPMPackageVersionTest {

    @Test
    void testGetCommands_singleCommand() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        JSONArray args = new JSONArray();
        args.put("--flag1");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("mycommand", commandSpec);
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals("mycommand", spec.getName());
        assertEquals(1, spec.getArgs().size());
        assertEquals("--flag1", spec.getArgs().get(0));
    }

    @Test
    void testGetCommands_multipleCommands() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        
        JSONArray args1 = new JSONArray();
        args1.put("--arg1");
        JSONObject cmd1 = new JSONObject();
        cmd1.put("args", args1);
        commands.put("cmd1", cmd1);
        
        JSONArray args2 = new JSONArray();
        args2.put("--arg2");
        JSONObject cmd2 = new JSONObject();
        cmd2.put("args", args2);
        commands.put("cmd2", cmd2);
        
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();

        assertEquals(2, result.size());
    }

    @Test
    void testGetCommands_emptyCommandsObject() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        JSONObject commands = new JSONObject();
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();

        assertEquals(0, result.size());
    }

    @Test
    void testGetCommands_noCommandsKey() {
        JSONObject packageJson = createPackageJson();
        // Do not add commands key to jdeploy

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();

        assertEquals(0, result.size());
    }

    @Test
    void testGetCommands_commandWithMultipleArgs() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        JSONArray args = new JSONArray();
        args.put("--jvm-arg");
        args.put("-Xmx1024m");
        args.put("--main-class=com.example.Main");
        args.put("${args}");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("myapp", commandSpec);
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals("myapp", spec.getName());
        assertEquals(4, spec.getArgs().size());
        assertEquals("--jvm-arg", spec.getArgs().get(0));
        assertEquals("-Xmx1024m", spec.getArgs().get(1));
        assertEquals("--main-class=com.example.Main", spec.getArgs().get(2));
        assertEquals("${args}", spec.getArgs().get(3));
    }

    @Test
    void testGetCommands_commandWithEmptyArgsArray() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        JSONArray emptyArgs = new JSONArray();
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", emptyArgs);
        commands.put("mycmd", commandSpec);
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();

        assertEquals(1, result.size());
        CommandSpec spec = result.get(0);
        assertEquals("mycmd", spec.getName());
        assertEquals(0, spec.getArgs().size());
    }

    @Test
    void testGetCommands_deterministicSorting() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        // Add commands in non-alphabetical order
        commands.put("zebra", new JSONObject());
        commands.put("apple", new JSONObject());
        commands.put("banana", new JSONObject());
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result1 = npmPackageVersion.getCommands();
        
        // Re-create to test determinism
        NPMPackageVersion npmPackageVersion2 = createNPMPackageVersion(packageJson);
        List<CommandSpec> result2 = npmPackageVersion2.getCommands();

        assertEquals(3, result1.size());
        assertEquals(3, result2.size());
        
        // Both parses should produce same order
        for (int i = 0; i < result1.size(); i++) {
            assertEquals(result1.get(i).getName(), result2.get(i).getName());
        }

        // Commands should be alphabetically sorted
        assertEquals("apple", result1.get(0).getName());
        assertEquals("banana", result1.get(1).getName());
        assertEquals("zebra", result1.get(2).getName());
    }

    @Test
    void testGetCommands_invalidCommandNameRejected() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        // Invalid: starts with hyphen
        commands.put("-invalid", new JSONObject());
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        assertThrows(Exception.class, npmPackageVersion::getCommands);
    }

    @Test
    void testGetCommands_invalidCommandNameWithSpace() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        // Invalid: contains space
        commands.put("invalid command", new JSONObject());
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        assertThrows(Exception.class, npmPackageVersion::getCommands);
    }

    @Test
    void testGetCommands_invalidCommandNameWithSpecialCharacters() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        // Invalid: contains special characters like @
        commands.put("invalid@cmd", new JSONObject());
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        assertThrows(Exception.class, npmPackageVersion::getCommands);
    }

    @Test
    void testGetCommands_commandWithMissingArgs() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        // Command with null value (invalid)
        commands.put("mycmd", JSONObject.NULL);
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        assertThrows(IllegalArgumentException.class, npmPackageVersion::getCommands);
    }

    @Test
    void testGetCommands_immutabilityOfArgs() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");
        
        JSONObject commands = new JSONObject();
        JSONArray args = new JSONArray();
        args.put("--flag");
        JSONObject commandSpec = new JSONObject();
        commandSpec.put("args", args);
        commands.put("cmd", commandSpec);
        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();
        CommandSpec spec = result.get(0);

        // Verify args list is unmodifiable
        assertThrows(Exception.class, () -> spec.getArgs().add("--new-flag"));
    }

    @Test
    void testGetCommands_validCommandNamesWithAllowedCharacters() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");

        JSONObject commands = new JSONObject();
        // Test various valid command name patterns
        commands.put("my-command", new JSONObject());
        commands.put("my_command", new JSONObject());
        commands.put("MyCommand", new JSONObject());
        commands.put("cmd123", new JSONObject());
        commands.put("a", new JSONObject());
        commands.put("1test", new JSONObject());
        commands.put("test.cmd", new JSONObject());

        jdeployConfig.put("commands", commands);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<CommandSpec> result = npmPackageVersion.getCommands();

        assertEquals(7, result.size());
    }

    // Helper Actions tests

    @Test
    void testGetHelperActions_singleAction() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");

        JSONObject helper = new JSONObject();
        JSONArray actions = new JSONArray();
        JSONObject action = new JSONObject();
        action.put("label", "Open Dashboard");
        action.put("description", "Opens the dashboard");
        action.put("url", "https://example.com/dashboard");
        actions.put(action);
        helper.put("actions", actions);
        jdeployConfig.put("helper", helper);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<HelperAction> result = npmPackageVersion.getHelperActions();

        assertEquals(1, result.size());
        HelperAction helperAction = result.get(0);
        assertEquals("Open Dashboard", helperAction.getLabel());
        assertEquals("Opens the dashboard", helperAction.getDescription());
        assertEquals("https://example.com/dashboard", helperAction.getUrl());
    }

    @Test
    void testGetHelperActions_multipleActions() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");

        JSONObject helper = new JSONObject();
        JSONArray actions = new JSONArray();

        JSONObject action1 = new JSONObject();
        action1.put("label", "Action 1");
        action1.put("url", "https://example.com/1");
        actions.put(action1);

        JSONObject action2 = new JSONObject();
        action2.put("label", "Action 2");
        action2.put("url", "myapp://settings");
        actions.put(action2);

        JSONObject action3 = new JSONObject();
        action3.put("label", "Open Config");
        action3.put("url", "/path/to/config.json");
        actions.put(action3);

        helper.put("actions", actions);
        jdeployConfig.put("helper", helper);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<HelperAction> result = npmPackageVersion.getHelperActions();

        assertEquals(3, result.size());
        assertEquals("Action 1", result.get(0).getLabel());
        assertEquals("Action 2", result.get(1).getLabel());
        assertEquals("Open Config", result.get(2).getLabel());
    }

    @Test
    void testGetHelperActions_noHelperSection() {
        JSONObject packageJson = createPackageJson();
        // No helper section

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<HelperAction> result = npmPackageVersion.getHelperActions();

        assertEquals(0, result.size());
    }

    @Test
    void testGetHelperActions_emptyActionsArray() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");

        JSONObject helper = new JSONObject();
        helper.put("actions", new JSONArray());
        jdeployConfig.put("helper", helper);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<HelperAction> result = npmPackageVersion.getHelperActions();

        assertEquals(0, result.size());
    }

    @Test
    void testGetHelperActions_missingLabel() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");

        JSONObject helper = new JSONObject();
        JSONArray actions = new JSONArray();
        JSONObject action = new JSONObject();
        // Missing label
        action.put("url", "https://example.com");
        actions.put(action);
        helper.put("actions", actions);
        jdeployConfig.put("helper", helper);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<HelperAction> result = npmPackageVersion.getHelperActions();

        // Invalid action should be skipped
        assertEquals(0, result.size());
    }

    @Test
    void testGetHelperActions_missingUrl() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");

        JSONObject helper = new JSONObject();
        JSONArray actions = new JSONArray();
        JSONObject action = new JSONObject();
        action.put("label", "Test");
        // Missing url
        actions.put(action);
        helper.put("actions", actions);
        jdeployConfig.put("helper", helper);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<HelperAction> result = npmPackageVersion.getHelperActions();

        // Invalid action should be skipped
        assertEquals(0, result.size());
    }

    @Test
    void testGetHelperActions_optionalDescription() {
        JSONObject packageJson = createPackageJson();
        JSONObject jdeployConfig = packageJson.getJSONObject("jdeploy");

        JSONObject helper = new JSONObject();
        JSONArray actions = new JSONArray();
        JSONObject action = new JSONObject();
        action.put("label", "No Description");
        action.put("url", "https://example.com");
        // No description
        actions.put(action);
        helper.put("actions", actions);
        jdeployConfig.put("helper", helper);

        NPMPackageVersion npmPackageVersion = createNPMPackageVersion(packageJson);
        List<HelperAction> result = npmPackageVersion.getHelperActions();

        assertEquals(1, result.size());
        HelperAction helperAction = result.get(0);
        assertEquals("No Description", helperAction.getLabel());
        assertNull(helperAction.getDescription());
        assertEquals("https://example.com", helperAction.getUrl());
    }

    /**
     * Helper method to create a minimal NPMPackageVersion for testing.
     */
    private NPMPackageVersion createNPMPackageVersion(JSONObject packageJson) {
        NPMPackage npmPackage = new NPMPackage(packageJson);
        return new NPMPackageVersion(npmPackage, "1.0.0", packageJson);
    }

    /**
     * Helper method to create a package.json with a basic jdeploy section.
     */
    private JSONObject createPackageJson() {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        packageJson.put("description", "Test application");
        
        JSONObject jdeploy = new JSONObject();
        packageJson.put("jdeploy", jdeploy);
        
        return packageJson;
    }
}
