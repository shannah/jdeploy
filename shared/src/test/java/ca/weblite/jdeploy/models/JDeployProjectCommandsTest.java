package ca.weblite.jdeploy.models;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JDeployProjectCommandsTest {

    @Test
    public void testMissingJDeployProducesEmptyCommands() {
        JSONObject pkg = new JSONObject();
        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        List<CommandSpec> cmds = project.getCommandSpecs();
        assertTrue(cmds.isEmpty());
    }

    @Test
    public void testMissingCommandsProducesEmpty() {
        JSONObject jdeploy = new JSONObject();
        JSONObject pkg = new JSONObject();
        pkg.put("jdeploy", jdeploy);
        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        List<CommandSpec> cmds = project.getCommandSpecs();
        assertTrue(cmds.isEmpty());
    }

    @Test
    public void testValidCommandsParses() {
        JSONObject commands = new JSONObject();
        JSONObject fooSpec = new JSONObject();
        JSONArray fooArgs = new JSONArray();
        fooArgs.put("--flag");
        fooArgs.put("value");
        fooSpec.put("args", fooArgs);
        JSONObject barSpec = new JSONObject(); // no args

        commands.put("foo", fooSpec);
        commands.put("bar", barSpec);

        JSONObject jdeploy = new JSONObject();
        jdeploy.put("commands", commands);
        JSONObject pkg = new JSONObject();
        pkg.put("jdeploy", jdeploy);

        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        List<CommandSpec> cmds = project.getCommandSpecs();
        // should be sorted by name: bar, foo
        assertEquals(2, cmds.size());
        assertEquals("bar", cmds.get(0).getName());
        assertEquals(0, cmds.get(0).getArgs().size());

        assertEquals("foo", cmds.get(1).getName());
        assertEquals(2, cmds.get(1).getArgs().size());
        assertEquals("--flag", cmds.get(1).getArgs().get(0));
        assertEquals("value", cmds.get(1).getArgs().get(1));

        Optional<CommandSpec> maybeFoo = project.getCommandSpec("foo");
        assertTrue(maybeFoo.isPresent());
        assertEquals("foo", maybeFoo.get().getName());
    }

    @Test
    public void testCommandDefaultsToEmptyArgsWhenMissing() {
        JSONObject commands = new JSONObject();
        JSONObject bazSpec = new JSONObject(); // missing args
        commands.put("baz", bazSpec);
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("commands", commands);
        JSONObject pkg = new JSONObject();
        pkg.put("jdeploy", jdeploy);

        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        List<CommandSpec> cmds = project.getCommandSpecs();
        assertEquals(1, cmds.size());
        assertEquals("baz", cmds.get(0).getName());
        assertNotNull(cmds.get(0).getArgs());
        assertTrue(cmds.get(0).getArgs().isEmpty());
    }

    @Test
    public void testInvalidNameThrows() {
        JSONObject commands = new JSONObject();
        JSONObject badSpec = new JSONObject();
        commands.put("bad name", badSpec); // space not allowed
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("commands", commands);
        JSONObject pkg = new JSONObject();
        pkg.put("jdeploy", jdeploy);

        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        assertThrows(IllegalArgumentException.class, project::getCommandSpecs);
    }

    @Test
    public void testArgsNotArrayThrows() {
        JSONObject commands = new JSONObject();
        JSONObject badSpec = new JSONObject();
        badSpec.put("args", "not-an-array");
        commands.put("cmd", badSpec);
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("commands", commands);
        JSONObject pkg = new JSONObject();
        pkg.put("jdeploy", jdeploy);

        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        assertThrows(IllegalArgumentException.class, project::getCommandSpecs);
    }

    @Test
    public void testArgsContainNonStringThrows() {
        JSONObject commands = new JSONObject();
        JSONObject badSpec = new JSONObject();
        org.json.JSONArray arr = new org.json.JSONArray();
        arr.put(1); // non-string
        badSpec.put("args", arr);
        commands.put("cmd", badSpec);
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("commands", commands);
        JSONObject pkg = new JSONObject();
        pkg.put("jdeploy", jdeploy);

        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        assertThrows(IllegalArgumentException.class, project::getCommandSpecs);
    }

    @Test
    public void testCommandValueNotObjectThrows() {
        JSONObject commands = new JSONObject();
        commands.put("cmd", "not-an-object");
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("commands", commands);
        JSONObject pkg = new JSONObject();
        pkg.put("jdeploy", jdeploy);

        JDeployProject project = new JDeployProject(Paths.get("package.json"), pkg);
        assertThrows(IllegalArgumentException.class, project::getCommandSpecs);
    }
}
