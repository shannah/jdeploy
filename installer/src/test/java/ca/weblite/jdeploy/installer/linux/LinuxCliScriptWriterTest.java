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

    @Test
    public void testGenerateContent_pathWithSpaces() {
        String launcher = "/Applications/My App Name.app/Contents/MacOS/Client4JLauncher-cli";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("exec \"/Applications/My App Name.app/Contents/MacOS/Client4JLauncher-cli\""));
    }

    @Test
    public void testGenerateContent_pathWithSingleQuotes() {
        String launcher = "/Applications/John's App/Contents/MacOS/Launcher";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("exec \"/Applications/John's App/Contents/MacOS/Launcher\""));
    }

    @Test
    public void testGenerateContent_pathWithDoubleQuotes() {
        String launcher = "/Applications/My \"Special\" App/Contents/MacOS/Launcher";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("exec \"/Applications/My \\\"Special\\\" App/Contents/MacOS/Launcher\""));
    }

    @Test
    public void testGenerateContent_pathWithBackticks() {
        String launcher = "/Applications/App`test`/Contents/MacOS/Launcher";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("exec \"/Applications/App\\`test\\`/Contents/MacOS/Launcher\""));
    }

    @Test
    public void testGenerateContent_pathWithDollarSign() {
        String launcher = "/Applications/$HOME/App/Contents/MacOS/Launcher";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("exec \"/Applications/\\$HOME/App/Contents/MacOS/Launcher\""));
    }

    @Test
    public void testGenerateContent_pathWithDollarAndParens() {
        String launcher = "/Applications/$(whoami)/App/Launcher";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("exec \"/Applications/\\$(whoami)/App/Launcher\""));
    }

    @Test
    public void testGenerateContent_pathWithBackslash() {
        String launcher = "/Applications/App\\Name/Contents/MacOS/Launcher";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("exec \"/Applications/App\\\\Name/Contents/MacOS/Launcher\""));
    }

    @Test
    public void testGenerateContent_pathWithMultipleSpecialChars() {
        String launcher = "/Applications/My \"App's\" $HOME/`test`/Launcher";
        String content = LinuxCliScriptWriter.generateContent(launcher, "mycmd");
        assertTrue(content.contains("\\\""));
        assertTrue(content.contains("\\$"));
        assertTrue(content.contains("\\`"));
        assertTrue(content.contains("'"));
    }

    @Test
    public void testGenerateContent_emptyPath() {
        String content = LinuxCliScriptWriter.generateContent("", "mycmd");
        assertTrue(content.startsWith("#!/bin/sh"));
        assertTrue(content.contains("exec \"\""));
    }

    @Test
    public void testGenerateContent_nullPath() {
        String content = LinuxCliScriptWriter.generateContent(null, "mycmd");
        assertTrue(content.startsWith("#!/bin/sh"));
        assertTrue(content.contains("exec \"\""));
    }
}
