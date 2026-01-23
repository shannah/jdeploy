package ca.weblite.jdeploy.installer.models;

import ca.weblite.jdeploy.installer.services.ServiceDescriptor;
import ca.weblite.jdeploy.models.CommandSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceRowModelTest {

    private ServiceDescriptor descriptor;
    private ServiceRowModel model;

    @BeforeEach
    public void setUp() {
        CommandSpec commandSpec = new CommandSpec(
                "my-service",
                "Test service description",
                Arrays.asList("--daemon"),
                Arrays.asList("service_controller")
        );
        descriptor = new ServiceDescriptor(
                commandSpec,
                "com.example.myapp",
                "1.0.0",
                null
        );
        model = new ServiceRowModel(descriptor);
    }

    @Test
    public void testGetDescriptor() {
        assertEquals(descriptor, model.getDescriptor());
    }

    @Test
    public void testGetServiceName() {
        assertEquals("my-service", model.getServiceName());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Test service description", model.getDescription());
    }

    @Test
    public void testGetCommandName() {
        assertEquals("my-service", model.getCommandName());
    }

    @Test
    public void testInitialStatus() {
        assertEquals(ServiceStatus.UNKNOWN, model.getStatus());
    }

    @Test
    public void testSetStatus() {
        model.setStatus(ServiceStatus.RUNNING);
        assertEquals(ServiceStatus.RUNNING, model.getStatus());

        model.setStatus(ServiceStatus.STOPPED);
        assertEquals(ServiceStatus.STOPPED, model.getStatus());
    }

    @Test
    public void testInitialErrorMessage() {
        assertNull(model.getErrorMessage());
    }

    @Test
    public void testSetErrorMessage() {
        model.setErrorMessage("Test error");
        assertEquals("Test error", model.getErrorMessage());
    }

    @Test
    public void testClearError() {
        model.setErrorMessage("Test error");
        model.clearError();
        assertNull(model.getErrorMessage());
    }

    @Test
    public void testInitialOperationInProgress() {
        assertFalse(model.isOperationInProgress());
    }

    @Test
    public void testSetOperationInProgress() {
        model.setOperationInProgress(true);
        assertTrue(model.isOperationInProgress());

        model.setOperationInProgress(false);
        assertFalse(model.isOperationInProgress());
    }

    @Test
    public void testCanStartWhenStopped() {
        model.setStatus(ServiceStatus.STOPPED);
        assertTrue(model.canStart());
    }

    @Test
    public void testCanStartWhenUninstalled() {
        model.setStatus(ServiceStatus.UNINSTALLED);
        assertTrue(model.canStart());
    }

    @Test
    public void testCannotStartWhenRunning() {
        model.setStatus(ServiceStatus.RUNNING);
        assertFalse(model.canStart());
    }

    @Test
    public void testCannotStartWhenOperationInProgress() {
        model.setStatus(ServiceStatus.STOPPED);
        model.setOperationInProgress(true);
        assertFalse(model.canStart());
    }

    @Test
    public void testCanStopWhenRunning() {
        model.setStatus(ServiceStatus.RUNNING);
        assertTrue(model.canStop());
    }

    @Test
    public void testCannotStopWhenStopped() {
        model.setStatus(ServiceStatus.STOPPED);
        assertFalse(model.canStop());
    }

    @Test
    public void testCannotStopWhenOperationInProgress() {
        model.setStatus(ServiceStatus.RUNNING);
        model.setOperationInProgress(true);
        assertFalse(model.canStop());
    }

    @Test
    public void testNeedsInstallWhenUninstalled() {
        model.setStatus(ServiceStatus.UNINSTALLED);
        assertTrue(model.needsInstall());
    }

    @Test
    public void testDoesNotNeedInstallWhenStopped() {
        model.setStatus(ServiceStatus.STOPPED);
        assertFalse(model.needsInstall());
    }

    @Test
    public void testDoesNotNeedInstallWhenRunning() {
        model.setStatus(ServiceStatus.RUNNING);
        assertFalse(model.needsInstall());
    }

    @Test
    public void testToString() {
        model.setStatus(ServiceStatus.RUNNING);
        String str = model.toString();
        assertTrue(str.contains("my-service"));
        assertTrue(str.contains("Running")); // Uses ServiceStatus.toString() which returns display name
    }

    @Test
    public void testNullDescription() {
        CommandSpec commandSpecNoDesc = new CommandSpec(
                "no-desc-service",
                null,
                null,
                Arrays.asList("service_controller")
        );
        ServiceDescriptor descriptorNoDesc = new ServiceDescriptor(
                commandSpecNoDesc,
                "com.example.myapp",
                "1.0.0",
                null
        );
        ServiceRowModel modelNoDesc = new ServiceRowModel(descriptorNoDesc);
        assertNull(modelNoDesc.getDescription());
    }
}
