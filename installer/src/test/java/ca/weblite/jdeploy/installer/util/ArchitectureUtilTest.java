package ca.weblite.jdeploy.installer.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ArchitectureUtil
 */
public class ArchitectureUtilTest {

    @Test
    public void testGetArchitecture() {
        // Test that getArchitecture returns a valid value
        String arch = ArchitectureUtil.getArchitecture();
        assertTrue("Architecture should be either 'arm64' or 'x64'",
                "arm64".equals(arch) || "x64".equals(arch));
    }

    @Test
    public void testGetArchitectureSuffix() {
        // Test that suffix has leading dash and matches architecture
        String suffix = ArchitectureUtil.getArchitectureSuffix();
        String arch = ArchitectureUtil.getArchitecture();

        assertEquals("Suffix should be architecture with leading dash",
                "-" + arch, suffix);
        assertTrue("Suffix should start with dash", suffix.startsWith("-"));
    }

    @Test
    public void testIsArm64() {
        // Test that exactly one of isArm64 or isX64 returns true
        boolean isArm = ArchitectureUtil.isArm64();
        boolean isX64 = ArchitectureUtil.isX64();

        assertTrue("Exactly one of isArm64 or isX64 should be true",
                isArm != isX64);
    }

    @Test
    public void testIsX64() {
        // Test that exactly one of isArm64 or isX64 returns true
        boolean isArm = ArchitectureUtil.isArm64();
        boolean isX64 = ArchitectureUtil.isX64();

        assertTrue("Exactly one of isArm64 or isX64 should be true",
                isArm != isX64);
    }

    @Test
    public void testConsistency() {
        // Test that all methods return consistent values
        String arch = ArchitectureUtil.getArchitecture();
        boolean isArm = ArchitectureUtil.isArm64();
        boolean isX64 = ArchitectureUtil.isX64();

        if ("arm64".equals(arch)) {
            assertTrue("isArm64 should return true when architecture is arm64", isArm);
            assertFalse("isX64 should return false when architecture is arm64", isX64);
        } else if ("x64".equals(arch)) {
            assertFalse("isArm64 should return false when architecture is x64", isArm);
            assertTrue("isX64 should return true when architecture is x64", isX64);
        }
    }
}
