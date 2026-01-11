package ca.weblite.jdeploy.installer.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceStatusTest {

    @Test
    public void testFromExitCodeRunning() {
        assertEquals(ServiceStatus.RUNNING, ServiceStatus.fromExitCode(0));
    }

    @Test
    public void testFromExitCodeStopped() {
        assertEquals(ServiceStatus.STOPPED, ServiceStatus.fromExitCode(3));
    }

    @Test
    public void testFromExitCodeUninstalled() {
        assertEquals(ServiceStatus.UNINSTALLED, ServiceStatus.fromExitCode(4));
    }

    @Test
    public void testFromExitCodeUnknown() {
        assertEquals(ServiceStatus.UNKNOWN, ServiceStatus.fromExitCode(1));
        assertEquals(ServiceStatus.UNKNOWN, ServiceStatus.fromExitCode(2));
        assertEquals(ServiceStatus.UNKNOWN, ServiceStatus.fromExitCode(5));
        assertEquals(ServiceStatus.UNKNOWN, ServiceStatus.fromExitCode(-1));
        assertEquals(ServiceStatus.UNKNOWN, ServiceStatus.fromExitCode(100));
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("Running", ServiceStatus.RUNNING.getDisplayName());
        assertEquals("Stopped", ServiceStatus.STOPPED.getDisplayName());
        assertEquals("Uninstalled", ServiceStatus.UNINSTALLED.getDisplayName());
        assertEquals("Unknown", ServiceStatus.UNKNOWN.getDisplayName());
    }

    @Test
    public void testGetExitCode() {
        assertEquals(0, ServiceStatus.RUNNING.getExitCode());
        assertEquals(3, ServiceStatus.STOPPED.getExitCode());
        assertEquals(4, ServiceStatus.UNINSTALLED.getExitCode());
        assertEquals(-1, ServiceStatus.UNKNOWN.getExitCode());
    }

    @Test
    public void testCanStartRunning() {
        assertFalse(ServiceStatus.RUNNING.canStart());
    }

    @Test
    public void testCanStartStopped() {
        assertTrue(ServiceStatus.STOPPED.canStart());
    }

    @Test
    public void testCanStartUninstalled() {
        assertTrue(ServiceStatus.UNINSTALLED.canStart());
    }

    @Test
    public void testCanStartUnknown() {
        assertFalse(ServiceStatus.UNKNOWN.canStart());
    }

    @Test
    public void testCanStopRunning() {
        assertTrue(ServiceStatus.RUNNING.canStop());
    }

    @Test
    public void testCanStopStopped() {
        assertFalse(ServiceStatus.STOPPED.canStop());
    }

    @Test
    public void testCanStopUninstalled() {
        assertFalse(ServiceStatus.UNINSTALLED.canStop());
    }

    @Test
    public void testCanStopUnknown() {
        assertFalse(ServiceStatus.UNKNOWN.canStop());
    }

    @Test
    public void testNeedsInstallRunning() {
        assertFalse(ServiceStatus.RUNNING.needsInstall());
    }

    @Test
    public void testNeedsInstallStopped() {
        assertFalse(ServiceStatus.STOPPED.needsInstall());
    }

    @Test
    public void testNeedsInstallUninstalled() {
        assertTrue(ServiceStatus.UNINSTALLED.needsInstall());
    }

    @Test
    public void testNeedsInstallUnknown() {
        assertFalse(ServiceStatus.UNKNOWN.needsInstall());
    }

    @Test
    public void testToString() {
        assertEquals("Running", ServiceStatus.RUNNING.toString());
        assertEquals("Stopped", ServiceStatus.STOPPED.toString());
        assertEquals("Uninstalled", ServiceStatus.UNINSTALLED.toString());
        assertEquals("Unknown", ServiceStatus.UNKNOWN.toString());
    }
}
