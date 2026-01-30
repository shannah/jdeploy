package com.joshondesign.appbundler.mac;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the embedded LaunchAgent plist generation in MacBundler.
 *
 * Covers:
 * - maybeCreateLaunchAgentPlists(): directory creation, file generation, guard conditions
 * - canEmbedPlist(): static-args heuristic and explicit embedPlist flag
 * - writeLaunchAgentPlist(): plist XML content, label, BundleProgram, ProgramArguments, extra args, XML escaping
 */
public class MacBundlerLaunchAgentPlistTest {

    private File tmpDir;

    @AfterEach
    public void cleanup() throws IOException {
        if (tmpDir != null && tmpDir.exists()) {
            FileUtils.deleteDirectory(tmpDir);
        }
    }

    // -----------------------------------------------------------------------
    // canEmbedPlist tests
    // -----------------------------------------------------------------------

    @Test
    public void canEmbedPlist_staticArgs_returnsTrue() {
        CommandSpec cmd = serviceCommand("sync", Arrays.asList("-Xmx2g", "--verbose"));
        assertTrue(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_noArgs_returnsTrue() {
        CommandSpec cmd = serviceCommand("sync", Collections.emptyList());
        assertTrue(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_dollarSign_returnsFalse() {
        CommandSpec cmd = serviceCommand("sync", Arrays.asList("--home=$HOME"));
        assertFalse(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_dollarBrace_returnsFalse() {
        CommandSpec cmd = serviceCommand("sync", Arrays.asList("--home=${HOME}"));
        assertFalse(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_templateExpression_returnsFalse() {
        CommandSpec cmd = serviceCommand("sync", Arrays.asList("--path={{DATA_DIR}}"));
        assertFalse(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_mixedArgs_returnsFalse() {
        // One static, one dynamic — should reject
        CommandSpec cmd = serviceCommand("sync", Arrays.asList("-Xmx2g", "--user=$USER"));
        assertFalse(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_explicitTrue_overridesHeuristic() {
        // Has a $ in args, but embedPlist=true overrides
        CommandSpec cmd = new CommandSpec("sync", "desc",
                Arrays.asList("--home=$HOME"),
                Collections.singletonList("service_controller"),
                Boolean.TRUE);
        assertTrue(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_explicitFalse_overridesHeuristic() {
        // All static args, but embedPlist=false forces fallback
        CommandSpec cmd = new CommandSpec("sync", "desc",
                Arrays.asList("-Xmx2g"),
                Collections.singletonList("service_controller"),
                Boolean.FALSE);
        assertFalse(MacBundler.canEmbedPlist(cmd));
    }

    @Test
    public void canEmbedPlist_explicitNull_usesHeuristic() {
        CommandSpec cmd = new CommandSpec("sync", "desc",
                Arrays.asList("-Xmx2g"),
                Collections.singletonList("service_controller"),
                null);
        assertTrue(MacBundler.canEmbedPlist(cmd));
    }

    // -----------------------------------------------------------------------
    // writeLaunchAgentPlist tests
    // -----------------------------------------------------------------------

    @Test
    public void writeLaunchAgentPlist_basicContent() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File plistFile = new File(tmpDir, "sync.plist");

        CommandSpec cmd = serviceCommand("sync", Collections.emptyList());
        MacBundler.writeLaunchAgentPlist(plistFile, "com.example.app.sync", cmd);

        assertTrue(plistFile.exists(), "Plist file should be created");
        String content = FileUtils.readFileToString(plistFile, StandardCharsets.UTF_8);

        // XML header
        assertTrue(content.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                "Should start with XML declaration");
        assertTrue(content.contains("<!DOCTYPE plist"), "Should contain DOCTYPE");
        assertTrue(content.contains("<plist version=\"1.0\">"), "Should contain plist root");

        // Label
        assertTrue(content.contains("<key>Label</key>"), "Should contain Label key");
        assertTrue(content.contains("<string>com.example.app.sync</string>"), "Should contain label value");

        // BundleProgram
        String expectedPath = "Contents/MacOS/" + CliInstallerConstants.CLI_LAUNCHER_NAME;
        assertTrue(content.contains("<key>BundleProgram</key>"), "Should contain BundleProgram key");
        assertTrue(content.contains("<string>" + expectedPath + "</string>"),
                "BundleProgram should point to CLI launcher");

        // ProgramArguments
        assertTrue(content.contains("<key>ProgramArguments</key>"), "Should contain ProgramArguments key");
        assertTrue(content.contains("<string>" + expectedPath + "</string>"),
                "ProgramArguments should include CLI launcher path");
        assertTrue(content.contains("<string>--jdeploy:command=sync</string>"),
                "ProgramArguments should include --jdeploy:command=<name>");
        assertFalse(content.contains("<string>--jdeploy:service</string>"),
                "ProgramArguments should NOT include --jdeploy:service (launchd runs the process directly)");
        assertFalse(content.contains("<string>start</string>"),
                "ProgramArguments should NOT include start (launchd runs the process directly)");

        // RunAtLoad and KeepAlive
        assertTrue(content.contains("<key>RunAtLoad</key>"), "Should contain RunAtLoad");
        assertTrue(content.contains("<key>KeepAlive</key>"), "Should contain KeepAlive");

        // Both should be true
        int runAtLoadIdx = content.indexOf("<key>RunAtLoad</key>");
        int keepAliveIdx = content.indexOf("<key>KeepAlive</key>");
        String afterRunAtLoad = content.substring(runAtLoadIdx, keepAliveIdx);
        assertTrue(afterRunAtLoad.contains("<true/>"), "RunAtLoad should be true");
        String afterKeepAlive = content.substring(keepAliveIdx);
        assertTrue(afterKeepAlive.contains("<true/>"), "KeepAlive should be true");
    }

    @Test
    public void writeLaunchAgentPlist_includesExtraArgs() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File plistFile = new File(tmpDir, "sync.plist");

        CommandSpec cmd = serviceCommand("sync", Arrays.asList("-Xmx2g", "-Dfoo=bar"));
        MacBundler.writeLaunchAgentPlist(plistFile, "com.example.app.sync", cmd);

        String content = FileUtils.readFileToString(plistFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("<string>-Xmx2g</string>"), "Should include first extra arg");
        assertTrue(content.contains("<string>-Dfoo=bar</string>"), "Should include second extra arg");

        // Extra args should come after the --jdeploy:command arg
        int commandIdx = content.indexOf("<string>--jdeploy:command=sync</string>");
        int xmxIdx = content.indexOf("<string>-Xmx2g</string>");
        int dfooIdx = content.indexOf("<string>-Dfoo=bar</string>");
        assertTrue(xmxIdx > commandIdx, "Extra args should appear after --jdeploy:command");
        assertTrue(dfooIdx > xmxIdx, "Args should preserve order");
    }

    @Test
    public void writeLaunchAgentPlist_escapesXmlCharacters() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File plistFile = new File(tmpDir, "sync.plist");

        CommandSpec cmd = serviceCommand("sync", Arrays.asList("-Dvalue=a<b&c>d\"e'f"));
        MacBundler.writeLaunchAgentPlist(plistFile, "com.example.<special>&label", cmd);

        String content = FileUtils.readFileToString(plistFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("com.example.&lt;special&gt;&amp;label"),
                "Label should have XML-escaped characters");
        assertTrue(content.contains("-Dvalue=a&lt;b&amp;c&gt;d&quot;e&apos;f"),
                "Args should have XML-escaped characters");
    }

    @Test
    public void writeLaunchAgentPlist_noExtraArgs() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File plistFile = new File(tmpDir, "ctl.plist");

        CommandSpec cmd = serviceCommand("ctl", Collections.emptyList());
        MacBundler.writeLaunchAgentPlist(plistFile, "com.example.app.ctl", cmd);

        String content = FileUtils.readFileToString(plistFile, StandardCharsets.UTF_8);

        // Count <string> tags inside the <array> — should be exactly 2:
        // cli launcher, --jdeploy:command=ctl
        int arrayStart = content.indexOf("<array>");
        int arrayEnd = content.indexOf("</array>");
        String arrayContent = content.substring(arrayStart, arrayEnd);
        int stringCount = countOccurrences(arrayContent, "<string>");
        assertEquals(2, stringCount,
                "ProgramArguments should have exactly 2 entries when no extra args");
    }

    // -----------------------------------------------------------------------
    // maybeCreateLaunchAgentPlists integration tests
    // -----------------------------------------------------------------------

    @Test
    public void maybeCreate_generatesPlists_forServiceControllerCommands() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        settings.setCliCommandsEnabled(true);

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setMacBundleId("com.example.testapp");
        app.setCommands(Arrays.asList(
                serviceCommand("sync-service", Collections.emptyList()),
                serviceCommand("bg-worker", Arrays.asList("-Xmx512m"))
        ));

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File launchAgentsDir = new File(contentsDir, "Library/LaunchAgents");
        assertTrue(launchAgentsDir.isDirectory(), "LaunchAgents directory should exist");

        File syncPlist = new File(launchAgentsDir, "sync-service.plist");
        File bgPlist = new File(launchAgentsDir, "bg-worker.plist");
        assertTrue(syncPlist.exists(), "sync-service.plist should be created");
        assertTrue(bgPlist.exists(), "bg-worker.plist should be created");

        // Verify label includes bundle ID
        String syncContent = FileUtils.readFileToString(syncPlist, StandardCharsets.UTF_8);
        assertTrue(syncContent.contains("com.example.testapp.sync-service"),
                "Label should be bundleId.commandName");

        String bgContent = FileUtils.readFileToString(bgPlist, StandardCharsets.UTF_8);
        assertTrue(bgContent.contains("com.example.testapp.bg-worker"),
                "Label should be bundleId.commandName");
        assertTrue(bgContent.contains("<string>-Xmx512m</string>"),
                "Extra args should be included");
    }

    @Test
    public void maybeCreate_skipsNonServiceControllerCommands() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        settings.setCliCommandsEnabled(true);

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setMacBundleId("com.example.testapp");
        app.setCommands(Arrays.asList(
                // updater only — no service_controller
                new CommandSpec("update-tool", "Updater", Collections.emptyList(),
                        Collections.singletonList("updater")),
                // launcher only
                new CommandSpec("gui-launcher", "Launcher", Collections.emptyList(),
                        Collections.singletonList("launcher"))
        ));

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File launchAgentsDir = new File(contentsDir, "Library/LaunchAgents");
        assertFalse(launchAgentsDir.exists(),
                "LaunchAgents directory should not be created when no service_controller commands exist");
    }

    @Test
    public void maybeCreate_skipsDynamicArgCommands() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        settings.setCliCommandsEnabled(true);

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setMacBundleId("com.example.testapp");
        app.setCommands(Arrays.asList(
                // Dynamic — uses $HOME
                serviceCommand("dynamic-svc", Arrays.asList("--dir=$HOME/data")),
                // Static — should still be generated
                serviceCommand("static-svc", Arrays.asList("-Xmx1g"))
        ));

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File launchAgentsDir = new File(contentsDir, "Library/LaunchAgents");
        assertTrue(launchAgentsDir.isDirectory(), "LaunchAgents directory should exist");

        assertFalse(new File(launchAgentsDir, "dynamic-svc.plist").exists(),
                "Dynamic-arg command should NOT get a plist");
        assertTrue(new File(launchAgentsDir, "static-svc.plist").exists(),
                "Static-arg command should get a plist");
    }

    @Test
    public void maybeCreate_skipsWhenCliCommandsDisabled() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        // CLI commands NOT enabled (default)

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setMacBundleId("com.example.testapp");
        app.setCommands(Collections.singletonList(
                serviceCommand("sync", Collections.emptyList())
        ));

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File launchAgentsDir = new File(contentsDir, "Library/LaunchAgents");
        assertFalse(launchAgentsDir.exists(),
                "LaunchAgents directory should not be created when CLI commands are disabled");
    }

    @Test
    public void maybeCreate_skipsWhenNoBundleId() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        settings.setCliCommandsEnabled(true);

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        // No bundle ID set
        app.setCommands(Collections.singletonList(
                serviceCommand("sync", Collections.emptyList())
        ));

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File launchAgentsDir = new File(contentsDir, "Library/LaunchAgents");
        assertFalse(launchAgentsDir.exists(),
                "LaunchAgents directory should not be created when bundle ID is null");
    }

    @Test
    public void maybeCreate_skipsWhenNoCommands() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        settings.setCliCommandsEnabled(true);

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setMacBundleId("com.example.testapp");
        // No commands set (empty default)

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File launchAgentsDir = new File(contentsDir, "Library/LaunchAgents");
        assertFalse(launchAgentsDir.exists(),
                "LaunchAgents directory should not be created when no commands exist");
    }

    @Test
    public void maybeCreate_respectsEmbedPlistFalse() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        settings.setCliCommandsEnabled(true);

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setMacBundleId("com.example.testapp");
        app.setCommands(Collections.singletonList(
                new CommandSpec("sync", "desc", Collections.emptyList(),
                        Collections.singletonList("service_controller"),
                        Boolean.FALSE)  // Explicit opt-out
        ));

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File launchAgentsDir = new File(contentsDir, "Library/LaunchAgents");
        assertFalse(launchAgentsDir.exists(),
                "LaunchAgents directory should not be created when embedPlist=false");
    }

    @Test
    public void maybeCreate_respectsEmbedPlistTrue_despiteDynamicArgs() throws Exception {
        tmpDir = Files.createTempDirectory("plist-test").toFile();
        File contentsDir = new File(tmpDir, "TestApp.app/Contents");
        assertTrue(contentsDir.mkdirs());

        BundlerSettings settings = new BundlerSettings();
        settings.setCliCommandsEnabled(true);

        AppDescription app = new AppDescription();
        app.setName("TestApp");
        app.setMacBundleId("com.example.testapp");
        app.setCommands(Collections.singletonList(
                new CommandSpec("sync", "desc",
                        Arrays.asList("--path=$HOME/data"),
                        Collections.singletonList("service_controller"),
                        Boolean.TRUE)  // Force embed despite $ in args
        ));

        MacBundler.maybeCreateLaunchAgentPlists(app, settings, contentsDir);

        File syncPlist = new File(contentsDir, "Library/LaunchAgents/sync.plist");
        assertTrue(syncPlist.exists(),
                "Plist should be created when embedPlist=true overrides dynamic arg heuristic");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a CommandSpec that implements service_controller with the given args.
     */
    private static CommandSpec serviceCommand(String name, List<String> args) {
        return new CommandSpec(name, null, args, Collections.singletonList("service_controller"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
