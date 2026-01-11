package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceManagementPanelTest {

    private ServiceManagementPanel panel;

    @BeforeEach
    public void setUp() {
        // Create mock installation settings
        InstallationSettings settings = new InstallationSettings();
        settings.setPackageName("test-package");
        settings.setSource(null);

        panel = new ServiceManagementPanel(settings);
    }

    @AfterEach
    public void tearDown() {
        if (panel != null) {
            panel.stopRefresh();
        }
    }

    @Test
    public void testPanelCreation() {
        assertNotNull(panel);
    }

    @Test
    public void testPanelLayout() {
        assertTrue(panel.getLayout() instanceof BorderLayout);
    }

    @Test
    public void testNoServicesMessage() {
        // When no services are found, panel should display a message
        Component[] components = panel.getComponents();
        assertTrue(components.length > 0);

        // Look for the "no services" label
        boolean foundNoServicesLabel = false;
        for (Component component : components) {
            if (component instanceof JLabel) {
                JLabel label = (JLabel) component;
                if (label.getText().contains("does not include any services")) {
                    foundNoServicesLabel = true;
                    break;
                }
            }
        }
        assertTrue(foundNoServicesLabel, "Should display 'no services' message when no services exist");
    }

    @Test
    public void testStopRefresh() {
        // Should not throw
        panel.stopRefresh();
    }

    @Test
    public void testStartRefreshWithNoServices() {
        // Should not throw even with no services
        panel.startRefresh();
    }

    @Test
    public void testNullPackageName() {
        InstallationSettings settings = new InstallationSettings();
        settings.setPackageName(null);
        settings.setSource(null);

        // Should not throw, just show no services message
        ServiceManagementPanel panelWithNullPackage = new ServiceManagementPanel(settings);
        assertNotNull(panelWithNullPackage);
        panelWithNullPackage.stopRefresh();
    }

    @Test
    public void testEmptyPackageName() {
        InstallationSettings settings = new InstallationSettings();
        settings.setPackageName("");
        settings.setSource(null);

        // Should not throw, just show no services message
        ServiceManagementPanel panelWithEmptyPackage = new ServiceManagementPanel(settings);
        assertNotNull(panelWithEmptyPackage);
        panelWithEmptyPackage.stopRefresh();
    }
}
