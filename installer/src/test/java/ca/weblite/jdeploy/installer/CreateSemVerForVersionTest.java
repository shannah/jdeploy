package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Main#createSemVerForVersion(String, AutoUpdateSettings)}.
 *
 * <p>This is the value written into the launcher's app.xml {@code version} attribute
 * and persisted into preferences.properties — what determines whether an installed
 * app stays locked to a concrete version or follows future releases.</p>
 */
public class CreateSemVerForVersionTest {

    @Test
    public void stable_returnsLatestLiteral() {
        assertEquals("latest", Main.createSemVerForVersion("1.2.3", AutoUpdateSettings.Stable));
    }

    @Test
    public void stable_returnsLatestEvenForPrereleaseInput() {
        assertEquals("latest", Main.createSemVerForVersion("1.2.3-beta", AutoUpdateSettings.Stable));
    }

    @Test
    public void off_returnsExactVersion() {
        assertEquals("1.2.3", Main.createSemVerForVersion("1.2.3", AutoUpdateSettings.Off));
    }

    @Test
    public void off_preservesPrereleaseSuffix() {
        assertEquals("1.2.3-beta.4", Main.createSemVerForVersion("1.2.3-beta.4", AutoUpdateSettings.Off));
    }

    @Test
    public void minorOnly_returnsCaretMajor() {
        assertEquals("^1", Main.createSemVerForVersion("1.2.3", AutoUpdateSettings.MinorOnly));
    }

    @Test
    public void minorOnly_stripsPrereleaseSuffix() {
        assertEquals("^1", Main.createSemVerForVersion("1.2.3-beta", AutoUpdateSettings.MinorOnly));
    }

    @Test
    public void minorOnly_handlesBareMajor() {
        assertEquals("^1", Main.createSemVerForVersion("1", AutoUpdateSettings.MinorOnly));
    }

    @Test
    public void minorOnly_handlesMultiDigitMajor() {
        assertEquals("^42", Main.createSemVerForVersion("42.0.7", AutoUpdateSettings.MinorOnly));
    }

    @Test
    public void patchesOnly_returnsTildeMajorMinor() {
        assertEquals("~1.2", Main.createSemVerForVersion("1.2.3", AutoUpdateSettings.PatchesOnly));
    }

    @Test
    public void patchesOnly_stripsPrereleaseSuffix() {
        assertEquals("~1.2", Main.createSemVerForVersion("1.2.3-beta", AutoUpdateSettings.PatchesOnly));
    }

    @Test
    public void patchesOnly_synthesizesMinorWhenMissing() {
        assertEquals("~1.0", Main.createSemVerForVersion("1", AutoUpdateSettings.PatchesOnly));
    }

    @Test
    public void patchesOnly_handlesMajorMinorWithoutPatch() {
        assertEquals("~1.2", Main.createSemVerForVersion("1.2", AutoUpdateSettings.PatchesOnly));
    }

    @Test
    public void branchInstallation_returnsVersionUnchanged_forStable() {
        assertEquals("0.0.0-main", Main.createSemVerForVersion("0.0.0-main", AutoUpdateSettings.Stable));
    }

    @Test
    public void branchInstallation_returnsVersionUnchanged_forOff() {
        assertEquals("0.0.0-staging", Main.createSemVerForVersion("0.0.0-staging", AutoUpdateSettings.Off));
    }

    @Test
    public void branchInstallation_returnsVersionUnchanged_forMinorOnly() {
        assertEquals("0.0.0-main", Main.createSemVerForVersion("0.0.0-main", AutoUpdateSettings.MinorOnly));
    }

    @Test
    public void branchInstallation_returnsVersionUnchanged_forPatchesOnly() {
        assertEquals("0.0.0-main", Main.createSemVerForVersion("0.0.0-main", AutoUpdateSettings.PatchesOnly));
    }
}
