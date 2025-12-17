package ca.weblite.jdeploy.installer.linux;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class LinuxCliScriptWriterTest {

    @Test
    public void testGenerateContent() {
        String launcher = "/Applications/MyApp.app/Contents/MacOS/Client4JLauncher-cli";
        String cmd = "mycmd";
        String content = LinuxCliScriptWriter.generateContent(launcher, cmd);
        assertTrue(content.startsWith("#!/bin/sh"));
        assertTrue(content.contains(launcher));
        assertTrue(content.contains("--jdeploy:command=" + cmd));
        assertTrue(content.contains("-- \"$@\""));
    }

    @Test
    public void testWriteExecutableScript() throws Exception {
        File tmp = Files.createTempFile("jdeploy-script-", ".sh").toFile();
        tmp.delete();
        LinuxCliScriptWriter.writeExecutableScript(tmp, "/bin/true", "mycmd");
        assertTrue(tmp.exists());
        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue(content.contains("--jdeploy:command=mycmd"));
        assertTrue(tmp.canExecute());
        tmp.delete();
    }
}
