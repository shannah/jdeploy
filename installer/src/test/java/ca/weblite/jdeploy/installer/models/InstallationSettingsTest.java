package ca.weblite.jdeploy.installer.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InstallationSettingsTest {
    private InstallationSettings settings;

    @BeforeEach
    public void setUp() {
        settings = new InstallationSettings();
    }

    @Test
    public void testPackageNameGetterSetterNull() {
        assertNull(settings.getPackageName());
        settings.setPackageName("test-package");
        assertEquals("test-package", settings.getPackageName());
    }

    @Test
    public void testPackageNameGetterSetterWithValue() {
        settings.setPackageName("my-awesome-app");
        assertEquals("my-awesome-app", settings.getPackageName());
    }

    @Test
    public void testPackageNameGetterSetterOverwrite() {
        settings.setPackageName("first-name");
        assertEquals("first-name", settings.getPackageName());
        settings.setPackageName("second-name");
        assertEquals("second-name", settings.getPackageName());
    }

    @Test
    public void testSourceGetterSetterNull() {
        assertNull(settings.getSource());
        settings.setSource("https://github.com/user/repo");
        assertEquals("https://github.com/user/repo", settings.getSource());
    }

    @Test
    public void testSourceGetterSetterWithValue() {
        settings.setSource("https://github.com/my-org/my-project");
        assertEquals("https://github.com/my-org/my-project", settings.getSource());
    }

    @Test
    public void testSourceGetterSetterOverwrite() {
        settings.setSource("https://github.com/first/repo");
        assertEquals("https://github.com/first/repo", settings.getSource());
        settings.setSource("https://github.com/second/repo");
        assertEquals("https://github.com/second/repo", settings.getSource());
    }

    @Test
    public void testSourceGetterSetterEmpty() {
        settings.setSource("");
        assertEquals("", settings.getSource());
    }

    @Test
    public void testPackageNameAndSourceIndependent() {
        settings.setPackageName("test-app");
        settings.setSource("https://github.com/user/test-app");
        assertEquals("test-app", settings.getPackageName());
        assertEquals("https://github.com/user/test-app", settings.getSource());
    }

    @Test
    public void testPackageNameAndSourceCanBeSetTogether() {
        settings.setPackageName("my-package");
        settings.setSource("https://github.com/owner/repo");
        assertEquals("my-package", settings.getPackageName());
        assertEquals("https://github.com/owner/repo", settings.getSource());
    }

    @Test
    public void testMultipleSetCalls() {
        settings.setPackageName("app1");
        settings.setSource("source1");
        settings.setPackageName("app2");
        settings.setSource("source2");
        settings.setPackageName("app3");
        
        assertEquals("app3", settings.getPackageName());
        assertEquals("source2", settings.getSource());
    }
}
