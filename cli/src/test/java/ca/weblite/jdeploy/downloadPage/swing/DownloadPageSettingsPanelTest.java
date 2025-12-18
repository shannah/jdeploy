package ca.weblite.jdeploy.downloadPage.swing;

import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings.BundlePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Component;
import java.awt.Container;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DownloadPageSettingsPanelTest {

    private DownloadPageSettingsPanel panel;
    private DownloadPageSettings settings;

    @BeforeEach
    void setUp() {
        settings = new DownloadPageSettings();
        panel = new DownloadPageSettingsPanel(settings);
    }

    @Test
    @DisplayName("Should initialize with default constructor")
    void shouldInitializeWithDefaultConstructor() {
        DownloadPageSettingsPanel defaultPanel = new DownloadPageSettingsPanel();
        
        assertNotNull(defaultPanel);
        assertNotNull(defaultPanel.getSettings());
        assertTrue(defaultPanel.getSettings().getEnabledPlatforms().contains(BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should initialize with provided settings")
    void shouldInitializeWithProvidedSettings() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.WindowsX64);
        settings.setEnabledPlatforms(platforms);
        
        DownloadPageSettingsPanel customPanel = new DownloadPageSettingsPanel(settings);
        
        assertNotNull(customPanel);
        assertEquals(settings, customPanel.getSettings());
        assertTrue(customPanel.getSettings().getEnabledPlatforms().contains(BundlePlatform.WindowsX64));
    }

    @Test
    @DisplayName("Should handle null settings in constructor")
    void shouldHandleNullSettingsInConstructor() {
        DownloadPageSettingsPanel nullPanel = new DownloadPageSettingsPanel(null);
        
        assertNotNull(nullPanel);
        assertNotNull(nullPanel.getSettings());
        assertTrue(nullPanel.getSettings().getEnabledPlatforms().contains(BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should return current settings")
    void shouldReturnCurrentSettings() {
        assertEquals(settings, panel.getSettings());
    }

    @Test
    @DisplayName("Should set new settings")
    void shouldSetNewSettings() {
        DownloadPageSettings newSettings = new DownloadPageSettings();
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.MacArm64);
        newSettings.setEnabledPlatforms(platforms);
        
        panel.setSettings(newSettings);
        
        assertEquals(newSettings, panel.getSettings());
        assertTrue(panel.getSettings().getEnabledPlatforms().contains(BundlePlatform.MacArm64));
    }

    @Test
    @DisplayName("Should handle null when setting settings")
    void shouldHandleNullWhenSettingSettings() {
        panel.setSettings(null);
        
        assertNotNull(panel.getSettings());
        assertTrue(panel.getSettings().getEnabledPlatforms().contains(BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should add change listener")
    void shouldAddChangeListener() {
        AtomicInteger changeCount = new AtomicInteger(0);
        ChangeListener listener = e -> changeCount.incrementAndGet();
        
        panel.addChangeListener(listener);
        
        JRadioButton allRadioButton = findRadioButton(panel, "All Platforms");
        assertNotNull(allRadioButton);
        
        allRadioButton.doClick();
        
        assertTrue(changeCount.get() > 0);
    }

    @Test
    @DisplayName("Should remove change listener")
    void shouldRemoveChangeListener() {
        AtomicInteger changeCount = new AtomicInteger(0);
        ChangeListener listener = e -> changeCount.incrementAndGet();
        
        panel.addChangeListener(listener);
        panel.removeChangeListener(listener);
        
        JRadioButton allRadioButton = findRadioButton(panel, "All Platforms");
        assertNotNull(allRadioButton);
        
        allRadioButton.doClick();
        
        assertEquals(0, changeCount.get());
    }

    @Test
    @DisplayName("Should handle null change listener")
    void shouldHandleNullChangeListener() {
        assertDoesNotThrow(() -> panel.addChangeListener(null));
        assertDoesNotThrow(() -> panel.removeChangeListener(null));
    }

    @Test
    @DisplayName("Should have all required radio buttons")
    void shouldHaveAllRequiredRadioButtons() {
        JRadioButton allRadioButton = findRadioButton(panel, "All Platforms");
        JRadioButton defaultRadioButton = findRadioButton(panel, "Default Platforms");
        JRadioButton customRadioButton = findRadioButton(panel, "Custom Platforms");
        
        assertNotNull(allRadioButton, "All Platforms radio button should exist");
        assertNotNull(defaultRadioButton, "Default Platforms radio button should exist");
        assertNotNull(customRadioButton, "Custom Platforms radio button should exist");
    }

    @Test
    @DisplayName("Should have correct initial radio button selection for default settings")
    void shouldHaveCorrectInitialRadioButtonSelectionForDefault() {
        JRadioButton defaultRadioButton = findRadioButton(panel, "Default Platforms");
        assertNotNull(defaultRadioButton);
        assertTrue(defaultRadioButton.isSelected());
    }

    @Test
    @DisplayName("Should select All radio button when All platform is enabled")
    void shouldSelectAllRadioButtonWhenAllPlatformEnabled() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.All);
        settings.setEnabledPlatforms(platforms);
        panel.setSettings(settings);
        
        JRadioButton allRadioButton = findRadioButton(panel, "All Platforms");
        assertNotNull(allRadioButton);
        assertTrue(allRadioButton.isSelected());
    }

    @Test
    @DisplayName("Should select Custom radio button when specific platforms are enabled")
    void shouldSelectCustomRadioButtonWhenSpecificPlatformsEnabled() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.WindowsX64);
        platforms.add(BundlePlatform.MacArm64);
        settings.setEnabledPlatforms(platforms);
        panel.setSettings(settings);
        
        JRadioButton customRadioButton = findRadioButton(panel, "Custom Platforms");
        assertNotNull(customRadioButton);
        assertTrue(customRadioButton.isSelected());
    }

    @Test
    @DisplayName("Should have platform checkboxes for all supported platforms")
    void shouldHavePlatformCheckboxesForAllSupportedPlatforms() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.WindowsX64);
        settings.setEnabledPlatforms(platforms);
        panel.setSettings(settings);
        
        JCheckBox windowsArm64 = findCheckBoxForPlatform(panel, BundlePlatform.WindowsArm64);
        JCheckBox windowsX64 = findCheckBoxForPlatform(panel, BundlePlatform.WindowsX64);
        JCheckBox macArm64 = findCheckBoxForPlatform(panel, BundlePlatform.MacArm64);
        JCheckBox macX64 = findCheckBoxForPlatform(panel, BundlePlatform.MacX64);
        JCheckBox macHighSierra = findCheckBoxForPlatform(panel, BundlePlatform.MacHighSierra);
        JCheckBox linuxArm64 = findCheckBoxForPlatform(panel, BundlePlatform.LinuxArm64);
        JCheckBox linuxX64 = findCheckBoxForPlatform(panel, BundlePlatform.LinuxX64);
        JCheckBox debianArm64 = findCheckBoxForPlatform(panel, BundlePlatform.DebianArm64);
        JCheckBox debianX64 = findCheckBoxForPlatform(panel, BundlePlatform.DebianX64);
        
        assertNotNull(windowsArm64, "Windows ARM64 checkbox should exist");
        assertNotNull(windowsX64, "Windows X64 checkbox should exist");
        assertNotNull(macArm64, "Mac ARM64 checkbox should exist");
        assertNotNull(macX64, "Mac X64 checkbox should exist");
        assertNotNull(macHighSierra, "Mac High Sierra checkbox should exist");
        assertNotNull(linuxArm64, "Linux ARM64 checkbox should exist");
        assertNotNull(linuxX64, "Linux X64 checkbox should exist");
        assertNotNull(debianArm64, "Debian ARM64 checkbox should exist");
        assertNotNull(debianX64, "Debian X64 checkbox should exist");
    }

    @Test
    @DisplayName("Should throw exception when creating checkbox for All or Default platform")
    void shouldThrowExceptionWhenCreatingCheckboxForAllOrDefault() {
        assertThrows(IllegalArgumentException.class, () -> 
            invokeCreateCheckbox(panel, BundlePlatform.All)
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            invokeCreateCheckbox(panel, BundlePlatform.Default)
        );
    }

    @Test
    @DisplayName("Should update settings when radio button selection changes")
    void shouldUpdateSettingsWhenRadioButtonSelectionChanges() {
        JRadioButton allRadioButton = findRadioButton(panel, "All Platforms");
        assertNotNull(allRadioButton);
        
        allRadioButton.doClick();
        
        assertTrue(panel.getSettings().getEnabledPlatforms().contains(BundlePlatform.All));
    }

    @Test
    @DisplayName("Should update settings when checkbox selection changes")
    void shouldUpdateSettingsWhenCheckboxSelectionChanges() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.WindowsX64);
        settings.setEnabledPlatforms(platforms);
        panel.setSettings(settings);
        
        JCheckBox macArm64 = findCheckBoxForPlatform(panel, BundlePlatform.MacArm64);
        assertNotNull(macArm64);
        
        macArm64.doClick();
        
        Set<BundlePlatform> enabledPlatforms = panel.getSettings().getEnabledPlatforms();
        assertTrue(enabledPlatforms.contains(BundlePlatform.MacArm64));
    }

    @Test
    @DisplayName("Should fire change events when settings change")
    void shouldFireChangeEventsWhenSettingsChange() {
        AtomicInteger changeCount = new AtomicInteger(0);
        ChangeListener listener = e -> changeCount.incrementAndGet();
        panel.addChangeListener(listener);
        
        JRadioButton allRadioButton = findRadioButton(panel, "All Platforms");
        assertNotNull(allRadioButton);
        
        allRadioButton.doClick();
        
        assertTrue(changeCount.get() > 0);
    }

    @Test
    @DisplayName("Should hide custom platform panel when All is selected")
    void shouldHideCustomPlatformPanelWhenAllSelected() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.All);
        settings.setEnabledPlatforms(platforms);
        panel.setSettings(settings);
        
        JPanel customPlatformPanel = findCustomPlatformPanel(panel);
        assertNotNull(customPlatformPanel);
        assertFalse(customPlatformPanel.isVisible());
    }

    @Test
    @DisplayName("Should hide custom platform panel when Default is selected")
    void shouldHideCustomPlatformPanelWhenDefaultSelected() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.Default);
        settings.setEnabledPlatforms(platforms);
        panel.setSettings(settings);
        
        JPanel customPlatformPanel = findCustomPlatformPanel(panel);
        assertNotNull(customPlatformPanel);
        assertFalse(customPlatformPanel.isVisible());
    }

    @Test
    @DisplayName("Should show custom platform panel when Custom is selected")
    void shouldShowCustomPlatformPanelWhenCustomSelected() {
        Set<BundlePlatform> platforms = new HashSet<>();
        platforms.add(BundlePlatform.WindowsX64);
        settings.setEnabledPlatforms(platforms);
        panel.setSettings(settings);
        
        JPanel customPlatformPanel = findCustomPlatformPanel(panel);
        assertNotNull(customPlatformPanel);
        assertTrue(customPlatformPanel.isVisible());
    }

    private JRadioButton findRadioButton(Container container, String text) {
        for (Component component : getAllComponents(container)) {
            if (component instanceof JRadioButton) {
                JRadioButton radioButton = (JRadioButton) component;
                if (text.equals(radioButton.getText())) {
                    return radioButton;
                }
            }
        }
        return null;
    }

    private JCheckBox findCheckBoxForPlatform(Container container, BundlePlatform platform) {
        for (Component component : getAllComponents(container)) {
            if (component instanceof JCheckBox) {
                JCheckBox checkBox = (JCheckBox) component;
                if (isPlatformCheckBox(checkBox, platform)) {
                    return checkBox;
                }
            }
        }
        return null;
    }

    private boolean isPlatformCheckBox(JCheckBox checkBox, BundlePlatform platform) {
        try {
            java.lang.reflect.Field field = panel.getClass().getDeclaredField("platformCheckboxes");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<BundlePlatform, JCheckBox> platformCheckboxes = 
                (java.util.Map<BundlePlatform, JCheckBox>) field.get(panel);
            return platformCheckboxes.get(platform) == checkBox;
        } catch (Exception e) {
            return false;
        }
    }

    private JPanel findCustomPlatformPanel(Container container) {
        try {
            java.lang.reflect.Field field = panel.getClass().getDeclaredField("customPlatformPanel");
            field.setAccessible(true);
            return (JPanel) field.get(panel);
        } catch (Exception e) {
            return null;
        }
    }

    private java.util.List<Component> getAllComponents(Container container) {
        java.util.List<Component> components = new java.util.ArrayList<>();
        components.add(container);
        for (Component component : container.getComponents()) {
            if (component instanceof Container) {
                components.addAll(getAllComponents((Container) component));
            } else {
                components.add(component);
            }
        }
        return components;
    }

    private JCheckBox invokeCreateCheckbox(DownloadPageSettingsPanel panel, BundlePlatform platform) {
        try {
            java.lang.reflect.Method method = panel.getClass().getDeclaredMethod("createCheckbox", BundlePlatform.class);
            method.setAccessible(true);
            return (JCheckBox) method.invoke(panel, platform);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }
}