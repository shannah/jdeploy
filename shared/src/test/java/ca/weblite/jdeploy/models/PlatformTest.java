package ca.weblite.jdeploy.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlatformTest {

    @Test
    public void testGetIdentifier() {
        assertEquals("default", Platform.DEFAULT.getIdentifier());
        assertEquals("mac-x64", Platform.MAC_X64.getIdentifier());
        assertEquals("mac-arm64", Platform.MAC_ARM64.getIdentifier());
        assertEquals("win-x64", Platform.WIN_X64.getIdentifier());
        assertEquals("win-arm64", Platform.WIN_ARM64.getIdentifier());
        assertEquals("linux-x64", Platform.LINUX_X64.getIdentifier());
        assertEquals("linux-arm64", Platform.LINUX_ARM64.getIdentifier());
    }

    @Test
    public void testGetPackagePropertyName() {
        assertEquals("package", Platform.DEFAULT.getPackagePropertyName());
        assertEquals("packageMacX64", Platform.MAC_X64.getPackagePropertyName());
        assertEquals("packageMacArm64", Platform.MAC_ARM64.getPackagePropertyName());
        assertEquals("packageWinX64", Platform.WIN_X64.getPackagePropertyName());
        assertEquals("packageWinArm64", Platform.WIN_ARM64.getPackagePropertyName());
        assertEquals("packageLinuxX64", Platform.LINUX_X64.getPackagePropertyName());
        assertEquals("packageLinuxArm64", Platform.LINUX_ARM64.getPackagePropertyName());
    }

    @Test
    public void testFromIdentifier() {
        assertEquals(Platform.DEFAULT, Platform.fromIdentifier("default"));
        assertEquals(Platform.MAC_X64, Platform.fromIdentifier("mac-x64"));
        assertEquals(Platform.MAC_ARM64, Platform.fromIdentifier("mac-arm64"));
        assertEquals(Platform.WIN_X64, Platform.fromIdentifier("win-x64"));
        assertEquals(Platform.WIN_ARM64, Platform.fromIdentifier("win-arm64"));
        assertEquals(Platform.LINUX_X64, Platform.fromIdentifier("linux-x64"));
        assertEquals(Platform.LINUX_ARM64, Platform.fromIdentifier("linux-arm64"));
    }

    @Test
    public void testFromIdentifierInvalid() {
        assertNull(Platform.fromIdentifier("invalid-platform"));
        assertNull(Platform.fromIdentifier(""));
        assertNull(Platform.fromIdentifier(null));
    }

    @Test
    public void testGetAllIdentifiers() {
        String[] identifiers = Platform.getAllIdentifiers();
        assertEquals(7, identifiers.length);
        
        // Check that all expected identifiers are present
        assertTrue(arrayContains(identifiers, "default"));
        assertTrue(arrayContains(identifiers, "mac-x64"));
        assertTrue(arrayContains(identifiers, "mac-arm64"));
        assertTrue(arrayContains(identifiers, "win-x64"));
        assertTrue(arrayContains(identifiers, "win-arm64"));
        assertTrue(arrayContains(identifiers, "linux-x64"));
        assertTrue(arrayContains(identifiers, "linux-arm64"));
    }

    private boolean arrayContains(String[] array, String value) {
        for (String item : array) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }
}