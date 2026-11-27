package ca.weblite.jdeploy.installer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MainBundleVersionTest {

    @Test
    public void testExactVersion() {
        assertEquals("1.0.138", Main.createBundleVersion("1.0.138"));
    }

    @Test
    public void testPatchesOnlyRange() {
        assertEquals("1.0", Main.createBundleVersion("~1.0"));
    }

    @Test
    public void testMinorOnlyRange() {
        assertEquals("1", Main.createBundleVersion("^1"));
    }

    @Test
    public void testPrereleaseAndBuildSuffixesAreStripped() {
        assertEquals("1.2.3", Main.createBundleVersion("1.2.3-beta.1"));
        assertEquals("1.2.3", Main.createBundleVersion("1.2.3+build.5"));
    }

    @Test
    public void testLatestHasNoFixedVersion() {
        assertNull(Main.createBundleVersion("latest"));
    }

    @Test
    public void testBranchBuildHasNoFixedVersion() {
        assertNull(Main.createBundleVersion("0.0.0-main"));
    }

    @Test
    public void testNullRange() {
        assertNull(Main.createBundleVersion(null));
    }
}
