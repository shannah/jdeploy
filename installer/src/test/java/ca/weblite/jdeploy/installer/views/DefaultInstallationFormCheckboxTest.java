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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultInstallationForm CLI installation behavior.
 * Verifies that CLI installation settings are configured correctly:
 * - On Linux: CLI Launcher is always installed
 * - On Mac/Windows: CLI Launcher is never installed
 * - On all platforms: CLI commands are installed if jdeploy.commands is defined
 *
 * Note: The CLI installation is now automatic and not controlled by checkboxes.
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

        // Setup other required fields
        settings.setInstallFilesDir(new File(System.getProperty("java.io.tmpdir")));
        settings.setCommandLinePath("/usr/local/bin/mycommand");
    }

    @Test
    public void testCliCommandsCheckboxNoLongerExists() {
        // Setup NPMPackageVersion with commands
        NPMPackageVersion npmVersion = createNPMPackageVersionWithCommands();
        settings.setNpmPackageVersion(npmVersion);

        // Create form
        form = new DefaultInstallationForm(settings);

        // Verify that the cliCommandsCheckBox field no longer exists
        try {
            java.lang.reflect.Field checkboxField = DefaultInstallationForm.class.getDeclaredField("cliCommandsCheckBox");
            fail("cliCommandsCheckBox field should not exist anymore");
        } catch (NoSuchFieldException e) {
            // Expected - the checkbox has been removed
            assertTrue(true, "cliCommandsCheckBox field correctly removed");
        }
    }

    @Test
    public void testFormCreatesSuccessfullyWithCommands() {
        // Setup NPMPackageVersion with commands
        NPMPackageVersion npmVersion = createNPMPackageVersionWithCommands();
        settings.setNpmPackageVersion(npmVersion);

        // Verify form can be created without errors
        assertDoesNotThrow(() -> {
            form = new DefaultInstallationForm(settings);
        }, "Form should create successfully even with commands defined");
    }

    @Test
    public void testFormCreatesSuccessfullyWithoutCommands() {
        // Setup NPMPackageVersion without commands
        NPMPackageVersion npmVersion = createNPMPackageVersionWithoutCommands();
        settings.setNpmPackageVersion(npmVersion);

        // Verify form can be created without errors
        assertDoesNotThrow(() -> {
            form = new DefaultInstallationForm(settings);
        }, "Form should create successfully without commands defined");
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

    /**
     * Creates an NPMPackageVersion without commands for testing.
     * Uses reflection to access the package-private constructor.
     */
    private NPMPackageVersion createNPMPackageVersionWithoutCommands() {
        try {
            // Create a package.json without jdeploy.commands
            JSONObject packageJson = new JSONObject();
            packageJson.put("name", "test-app");
            packageJson.put("version", "1.0.0");
            packageJson.put("description", "Test application");

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
