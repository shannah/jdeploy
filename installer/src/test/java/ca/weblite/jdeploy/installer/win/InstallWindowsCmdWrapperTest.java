package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.models.CommandSpec;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class InstallWindowsCmdWrapperTest {

    @Test
    public void testWriteCommandWrappers() throws Exception {
        File tmp = java.nio.file.Files.createTempDirectory("jdeploy-bin-test").toFile();
        tmp.deleteOnExit();
        File exe = java.nio.file.Files.createTempFile("launcher", ".exe").toFile();
        exe.deleteOnExit();

        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("hello", Arrays.asList("a", "b")),
                new CommandSpec("world", null)
        );

        List<java.io.File> created = InstallWindows.writeCommandWrappers(tmp, exe, commands);
        assertNotNull(created);
        assertEquals(2, created.size());

        File hello = new File(tmp, "hello.cmd");
        assertTrue(hello.exists());
        String content = new String(java.nio.file.Files.readAllBytes(hello.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("--jdeploy:command=hello"));

        File world = new File(tmp, "world.cmd");
        assertTrue(world.exists());
        String content2 = new String(java.nio.file.Files.readAllBytes(world.toPath()), StandardCharsets.UTF_8);
        assertTrue(content2.contains("--jdeploy:command=world"));
    }
}
