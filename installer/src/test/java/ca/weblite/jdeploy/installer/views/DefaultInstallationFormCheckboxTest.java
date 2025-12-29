package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.npm.NPMPackage;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
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
 * Unit tests for DefaultInstallationForm checkbox independence.
 * Verifies that installCliCommands and installCliLauncher flags are controlled independently.
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
    public void testCliCommandsCheckboxOnlyAffectsInstallCliCommands() {
        // Set initial state
        settings.setInstallCliCommands(false);
        settings.setInstallCliLauncher(true);

        // Simulate checking the CLI commands checkbox
        triggerCliCommandsCheckbox(true);

        // Verify only installCliCommands changed
        assertTrue(settings.isInstallCliCommands(), "installCliCommands should be true");
        assertTrue(settings.isInstallCliLauncher(), "installCliLauncher should remain true (independent)");
    }

    @Test
    public void testCliCommandsCheckboxUncheckedOnlyAffectsInstallCliCommands() {
        // Set initial state
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(true);

        // Simulate unchecking the CLI commands checkbox
        triggerCliCommandsCheckbox(false);

        // Verify only installCliCommands changed
        assertFalse(settings.isInstallCliCommands(), "installCliCommands should be false");
        assertTrue(settings.isInstallCliLauncher(), "installCliLauncher should remain true (independent)");
    }

    @Test
    public void testCliCommandsAndCliLauncherAreIndependent() {
        // Test all combinations
        testCombination(true, true);
        testCombination(true, false);
        testCombination(false, true);
        testCombination(false, false);
    }

    private void testCombination(boolean cliCommands, boolean cliLauncher) {
        settings.setInstallCliCommands(cliCommands);
        settings.setInstallCliLauncher(cliLauncher);

        // Toggle CLI commands and verify only that flag changes
        boolean newCmdValue = !cliCommands;
        triggerCliCommandsCheckbox(newCmdValue);

        assertEquals(newCmdValue, settings.isInstallCliCommands(),
            "installCliCommands should toggle");
        assertEquals(cliLauncher, settings.isInstallCliLauncher(),
            "installCliLauncher should remain unchanged");
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
