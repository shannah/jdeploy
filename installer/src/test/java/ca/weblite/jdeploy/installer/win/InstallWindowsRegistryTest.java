package ca.weblite.jdeploy.installer.win;

import org.junit.Test;

import static org.junit.Assert.*;

public class InstallWindowsRegistryTest {

    @Test
    public void testComputePathWithAdded() {
        String current = "C:\\Windows;C:\\Program Files";
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        String result = InstallWindowsRegistry.computePathWithAdded(current, bin);
        assertTrue(result.contains(bin));
        // adding again should be idempotent
        String again = InstallWindowsRegistry.computePathWithAdded(result, bin);
        assertEquals(result, again);
    }

    @Test
    public void testComputePathWithRemoved() {
        String current = "A;B;C";
        String bin = "B";
        String result = InstallWindowsRegistry.computePathWithRemoved(current, bin);
        assertEquals("A;C", result);
        // removing when not present should leave unchanged
        String unchanged = InstallWindowsRegistry.computePathWithRemoved(result, bin);
        assertEquals("A;C", unchanged);
    }

    @Test
    public void testComputePathEdgeCases() {
        assertEquals("C:\\Users\\me\\.jdeploy\\bin", InstallWindowsRegistry.computePathWithAdded(null, "C:\\Users\\me\\.jdeploy\\bin"));
        assertEquals("", InstallWindowsRegistry.computePathWithRemoved("C:\\Users\\me\\.jdeploy\\bin", "C:\\Users\\me\\.jdeploy\\bin"));
    }
}
