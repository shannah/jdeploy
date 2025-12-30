package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.npm.NPMPackage;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.tools.platform.Platform;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultInstallationForm checkbox behavior.
 * Verifies that on Linux, the cliCommandsCheckBox controls both installCliCommands
 * and installCliLauncher flags to reduce UI clutter.
 */
public class DefaultInstallationFormCheckboxTest {

    private DefaultInstallationForm form;
    private InstallationSettings settings;

    @BeforeEach
    public void setUp() {
        Assumptions.assumeFalse(
                GraphicsEnvironment.isHeadless(),
                "Test requires a display environment"
        );
        settings = new InstallationSettings();

        // Setup minimal AppInfo
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("Test App");
        settings.setAppInfo(appInfo);

        // Setup NPMPackageVersion with commands using reflection
        NPMPackageVersion npmVersion = createNPMPackageVersionWithCommands();
        settings.setNpmPackageVersion(npmVersion);

        // Setup other required fields
        settings.setInstallFilesDir(new File(System.getProperty("java.io.tmpdir")));
        settings.setCommandLinePath("/usr/local/bin/mycommand");

        // Create form
        form = new DefaultInstallationForm(settings);
    }

    @Test
    public void testCliCommandsCheckboxAffectsBothFlagsOnLinux() {
        Assumptions.assumeTrue(Platform.getSystemPlatform().isLinux(), "Test only runs on Linux");
        
        // Set initial state
        settings.setInstallCliCommands(false);
        settings.setInstallCliLauncher(false);

        // Simulate checking the CLI commands checkbox
        triggerCliCommandsCheckbox(true);

        // Verify both flags changed because we are simulating Linux with a command line path
        assertTrue(settings.isInstallCliCommands(), "installCliCommands should be true");
        assertTrue(settings.isInstallCliLauncher(), "installCliLauncher should be true (coupled on Linux)");
    }

    @Test
    public void testCliCommandsCheckboxUncheckedAffectsBothFlagsOnLinux() {
        Assumptions.assumeTrue(Platform.getSystemPlatform().isLinux(), "Test only runs on Linux");
        
        // Set initial state
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(true);

        // Simulate unchecking the CLI commands checkbox
        triggerCliCommandsCheckbox(false);

        // Verify both flags changed
        assertFalse(settings.isInstallCliCommands(), "installCliCommands should be false");
        assertFalse(settings.isInstallCliLauncher(), "installCliLauncher should be false (coupled on Linux)");
    }

    @Test
    public void testCliCommandsCheckboxControlsBothFlagsOnLinux() {
        Assumptions.assumeTrue(Platform.getSystemPlatform().isLinux(), "Test only runs on Linux");
        
        // Test combinations to ensure coupling
        testCombination(true, true);
        testCombination(false, false);
    }

    private void testCombination(boolean initialCommands, boolean initialLauncher) {
        settings.setInstallCliCommands(initialCommands);
        settings.setInstallCliLauncher(initialLauncher);

        // Toggle CLI commands and verify both flags change together
        boolean newCmdValue = !initialCommands;
        triggerCliCommandsCheckbox(newCmdValue);

        assertEquals(newCmdValue, settings.isInstallCliCommands(),
            "installCliCommands should toggle");
        assertEquals(newCmdValue, settings.isInstallCliLauncher(),
            "installCliLauncher should toggle with cli commands on Linux");
    }

    /**
     * Simulates the user clicking the CLI commands checkbox.
     * Directly invokes the checkbox's action listener by accessing and triggering it.
     */
    private void triggerCliCommandsCheckbox(boolean selected) {
        // Get the cliCommandsCheckBox from the form using reflection
        try {
            Field checkboxField = DefaultInstallationForm.class.getDeclaredField("cliCommandsCheckBox");
            checkboxField.setAccessible(true);
            javax.swing.JCheckBox checkbox = (javax.swing.JCheckBox) checkboxField.get(form);

            if (checkbox != null) {
                // Set selected state and trigger all action listeners
                checkbox.setSelected(selected);
                for (java.awt.event.ActionListener listener : checkbox.getActionListeners()) {
                    listener.actionPerformed(new java.awt.event.ActionEvent(checkbox, 0, ""));
                }
            }
        } catch (Exception e) {
            fail("Could not access cliCommandsCheckBox: " + e.getMessage());
        }
    }

    /**
     * Creates an NPMPackageVersion with commands for testing.
     * Uses reflection to access the package-private constructor.
     */
    private NPMPackageVersion createNPMPackageVersionWithCommands() {
        try {
            // Create a package.json with jdeploy.commands
            JSONObject packageJson = new JSONObject();
            packageJson.put("name", "test-app");
            packageJson.put("version", "1.0.0");
            packageJson.put("description", "Test application");
            
            JSONObject jdeploy = new JSONObject();
            JSONObject commands = new JSONObject();
            
            // Add test commands
            JSONObject cmd1 = new JSONObject();
            cmd1.put("args", new JSONArray().put("--cmd1"));
            commands.put("cmd1", cmd1);
            
            JSONObject cmd2 = new JSONObject();
            cmd2.put("args", new JSONArray().put("--cmd2"));
            commands.put("cmd2", cmd2);
            
            jdeploy.put("commands", commands);
            packageJson.put("jdeploy", jdeploy);

            // Get the package-private constructor using reflection
            Constructor<NPMPackageVersion> constructor = NPMPackageVersion.class.getDeclaredConstructor(
                NPMPackage.class, String.class, JSONObject.class
            );
            constructor.setAccessible(true);
            
            // Create instance with null NPMPackage (not needed for this test)
            return constructor.newInstance(null, "1.0.0", packageJson);
        } catch (Exception e) {
            fail("Could not create NPMPackageVersion: " + e.getMessage());
            return null;
        }
    }
}
