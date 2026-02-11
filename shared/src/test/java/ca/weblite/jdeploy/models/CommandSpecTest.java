package ca.weblite.jdeploy.models;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CommandSpecTest {

    @Test
    public void testArgsDefaultToEmptyWhenNull() {
        CommandSpec cs = new CommandSpec("cmd", null, null);
        assertNotNull(cs.getArgs());
        assertTrue(cs.getArgs().isEmpty());
    }

    @Test
    public void testEqualsAndHashCode() {
        CommandSpec a1 = new CommandSpec("cmd", null, Arrays.asList("a", "b"));
        CommandSpec a2 = new CommandSpec("cmd", null, Arrays.asList("a", "b"));
        CommandSpec b = new CommandSpec("cmd", null, Collections.singletonList("x"));
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
    }

    @Test
    public void testToStringContainsName() {
        CommandSpec cs = new CommandSpec("mycmd", null, Arrays.asList("--flag"));
        String s = cs.toString();
        assertTrue(s.contains("mycmd"));
        assertTrue(s.contains("--flag"));
    }

    @Test
    public void testDescriptionCanBeNull() {
        CommandSpec cs = new CommandSpec("cmd", null, Arrays.asList("a"));
        assertNull(cs.getDescription());
    }

    @Test
    public void testDescriptionCanBeNonNull() {
        CommandSpec cs = new CommandSpec("cmd", "A helpful description", Arrays.asList("a"));
        assertEquals("A helpful description", cs.getDescription());
    }

    @Test
    public void testDifferentDescriptionsNotEqual() {
        CommandSpec cs1 = new CommandSpec("cmd", "Description 1", Arrays.asList("a", "b"));
        CommandSpec cs2 = new CommandSpec("cmd", "Description 2", Arrays.asList("a", "b"));
        assertNotEquals(cs1, cs2);
    }

    @Test
    public void testSameDescriptionsEqual() {
        CommandSpec cs1 = new CommandSpec("cmd", "Same description", Arrays.asList("a", "b"));
        CommandSpec cs2 = new CommandSpec("cmd", "Same description", Arrays.asList("a", "b"));
        assertEquals(cs1, cs2);
        assertEquals(cs1.hashCode(), cs2.hashCode());
    }

    @Test
    public void testToStringIncludesDescription() {
        CommandSpec cs = new CommandSpec("mycmd", "My description", Arrays.asList("--flag"));
        String s = cs.toString();
        assertTrue(s.contains("mycmd"));
        assertTrue(s.contains("My description"));
        assertTrue(s.contains("--flag"));
    }

    @Test
    public void testHashCodeDiffersWithDifferentDescription() {
        CommandSpec cs1 = new CommandSpec("cmd", "Desc1", Arrays.asList("a", "b"));
        CommandSpec cs2 = new CommandSpec("cmd", "Desc2", Arrays.asList("a", "b"));
        assertNotEquals(cs1.hashCode(), cs2.hashCode());
    }

    // Parser tests for description extraction

    @Test
    public void testParserExtractsDescriptionFromCommand() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("description", "This is a test command");
        cmdSpec.put("args", new JSONArray());
        commands.put("test-cmd", cmdSpec);
        config.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(config);
        assertEquals(1, result.size());
        CommandSpec cmd = result.get(0);
        assertEquals("test-cmd", cmd.getName());
        assertEquals("This is a test command", cmd.getDescription());
    }

    @Test
    public void testParserHandlesCommandWithoutDescription() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("args", new JSONArray());
        commands.put("test-cmd", cmdSpec);
        config.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(config);
        assertEquals(1, result.size());
        CommandSpec cmd = result.get(0);
        assertEquals("test-cmd", cmd.getName());
        assertNull(cmd.getDescription());
    }

    @Test
    public void testParserHandlesMixedCommandsWithAndWithoutDescriptions() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();

        JSONObject cmd1Spec = new JSONObject();
        cmd1Spec.put("description", "First command");
        cmd1Spec.put("args", new JSONArray());
        commands.put("cmd1", cmd1Spec);

        JSONObject cmd2Spec = new JSONObject();
        cmd2Spec.put("args", new JSONArray());
        commands.put("cmd2", cmd2Spec);

        JSONObject cmd3Spec = new JSONObject();
        cmd3Spec.put("description", "Third command");
        cmd3Spec.put("args", new JSONArray());
        commands.put("cmd3", cmd3Spec);

        config.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(config);
        assertEquals(3, result.size());

        // Results are sorted by name
        CommandSpec c1 = findByName(result, "cmd1");
        CommandSpec c2 = findByName(result, "cmd2");
        CommandSpec c3 = findByName(result, "cmd3");

        assertEquals("First command", c1.getDescription());
        assertNull(c2.getDescription());
        assertEquals("Third command", c3.getDescription());
    }

    @Test
    public void testParserPreservesDescriptionInRoundTrip() {
        String originalDescription = "Execute the main application";
        CommandSpec original = new CommandSpec("app", originalDescription, Arrays.asList("--verbose"));

        // Simulate serialization to JSON (as would happen in JDeployProject)
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("description", original.getDescription());
        JSONArray argsArray = new JSONArray(original.getArgs());
        cmdSpec.put("args", argsArray);

        JSONObject commands = new JSONObject();
        commands.put(original.getName(), cmdSpec);
        JSONObject config = new JSONObject();
        config.put("commands", commands);

        // Parse back from JSON
        List<CommandSpec> parsed = CommandSpecParser.parseCommands(config);
        assertEquals(1, parsed.size());
        CommandSpec roundTripped = parsed.get(0);

        // Verify all fields preserved
        assertEquals(original.getName(), roundTripped.getName());
        assertEquals(original.getDescription(), roundTripped.getDescription());
        assertEquals(original.getArgs(), roundTripped.getArgs());
        assertEquals(original, roundTripped);
    }

    @Test
    public void testParserRejectsNonStringDescription() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("description", 123); // Invalid: numeric instead of string
        cmdSpec.put("args", new JSONArray());
        commands.put("test-cmd", cmdSpec);
        config.put("commands", commands);

        assertThrows(IllegalArgumentException.class, () -> CommandSpecParser.parseCommands(config),
                "Should reject non-string description");
    }

    @Test
    public void testParserIgnoresNullDescriptionValue() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("description", JSONObject.NULL);
        cmdSpec.put("args", new JSONArray());
        commands.put("test-cmd", cmdSpec);
        config.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(config);
        assertEquals(1, result.size());
        CommandSpec cmd = result.get(0);
        assertNull(cmd.getDescription());
    }

    @Test
    public void testParserPreservesEmptyDescription() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("description", ""); // Empty string is valid
        cmdSpec.put("args", new JSONArray());
        commands.put("test-cmd", cmdSpec);
        config.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(config);
        assertEquals(1, result.size());
        CommandSpec cmd = result.get(0);
        assertEquals("", cmd.getDescription());
    }

    private CommandSpec findByName(List<CommandSpec> specs, String name) {
        return specs.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Command not found: " + name));
    }

    // ===== updateTrigger tests =====

    @Test
    public void testUpdateTriggerDefaultsToUpdate() {
        CommandSpec cs = new CommandSpec("cmd", null, null);
        assertEquals(CommandSpec.DEFAULT_UPDATE_TRIGGER, cs.getUpdateTrigger());
        assertEquals("update", cs.getUpdateTrigger());
    }

    @Test
    public void testUpdateTriggerCanBeCustomized() {
        CommandSpec cs = new CommandSpec("cmd", null, null, null, null, "upgrade");
        assertEquals("upgrade", cs.getUpdateTrigger());
    }

    @Test
    public void testUpdateTriggerNullDefaultsToUpdate() {
        CommandSpec cs = new CommandSpec("cmd", null, null, null, null, null);
        assertEquals("update", cs.getUpdateTrigger());
    }

    @Test
    public void testUpdateTriggerEmptyDefaultsToUpdate() {
        CommandSpec cs = new CommandSpec("cmd", null, null, null, null, "");
        assertEquals("update", cs.getUpdateTrigger());
    }

    @Test
    public void testUpdateTriggerIncludedInEquals() {
        CommandSpec cs1 = new CommandSpec("cmd", null, null, null, null, "upgrade");
        CommandSpec cs2 = new CommandSpec("cmd", null, null, null, null, "upgrade");
        CommandSpec cs3 = new CommandSpec("cmd", null, null, null, null, "refresh");
        assertEquals(cs1, cs2);
        assertNotEquals(cs1, cs3);
    }

    @Test
    public void testUpdateTriggerIncludedInHashCode() {
        CommandSpec cs1 = new CommandSpec("cmd", null, null, null, null, "upgrade");
        CommandSpec cs2 = new CommandSpec("cmd", null, null, null, null, "upgrade");
        CommandSpec cs3 = new CommandSpec("cmd", null, null, null, null, "refresh");
        assertEquals(cs1.hashCode(), cs2.hashCode());
        assertNotEquals(cs1.hashCode(), cs3.hashCode());
    }

    @Test
    public void testUpdateTriggerIncludedInToString() {
        CommandSpec cs = new CommandSpec("cmd", null, null, null, null, "upgrade");
        String s = cs.toString();
        assertTrue(s.contains("upgrade"));
        assertTrue(s.contains("updateTrigger"));
    }

    @Test
    public void testParserExtractsUpdateTrigger() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("implements", new JSONArray().put("updater"));
        cmdSpec.put("updateTrigger", "upgrade");
        commands.put("mycmd", cmdSpec);
        config.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(config);
        assertEquals(1, result.size());
        CommandSpec cmd = result.get(0);
        assertEquals("upgrade", cmd.getUpdateTrigger());
    }

    @Test
    public void testParserDefaultsUpdateTriggerWhenNotSpecified() {
        JSONObject config = new JSONObject();
        JSONObject commands = new JSONObject();
        JSONObject cmdSpec = new JSONObject();
        cmdSpec.put("implements", new JSONArray().put("updater"));
        commands.put("mycmd", cmdSpec);
        config.put("commands", commands);

        List<CommandSpec> result = CommandSpecParser.parseCommands(config);
        assertEquals(1, result.size());
        CommandSpec cmd = result.get(0);
        assertEquals("update", cmd.getUpdateTrigger());
    }
}
