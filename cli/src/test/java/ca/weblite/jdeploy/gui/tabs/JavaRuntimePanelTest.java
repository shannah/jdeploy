package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaRuntimePanel")
class JavaRuntimePanelTest {

    private JavaRuntimePanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() {
        panel = new JavaRuntimePanel(null, null, null);
        changeListenerCallCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
    }

    // ========== LOAD TESTS ==========

    @Test
    @DisplayName("Should load jar from jdeploy object")
    void testLoadJar() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "build/libs/app.jar");

        panel.load(jdeploy);

        assertEquals("build/libs/app.jar", panel.getJarFile().getText());
    }

    @Test
    @DisplayName("Should load javafx flag from jdeploy object")
    void testLoadJavaFX() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("javafx", true);

        panel.load(jdeploy);

        assertTrue(panel.getRequiresJavaFX().isSelected());
    }

    @Test
    @DisplayName("Should load jdk flag from jdeploy object")
    void testLoadJdk() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdk", true);

        panel.load(jdeploy);

        assertTrue(panel.getRequiresFullJDK().isSelected());
    }

    @Test
    @DisplayName("Should load javaVersion from jdeploy object")
    void testLoadJavaVersion() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("javaVersion", "21");

        panel.load(jdeploy);

        assertEquals("21", panel.getJavaVersion().getSelectedItem());
    }

    @Test
    @DisplayName("Should load jdkProvider as JBR from jdeploy object")
    void testLoadJdkProviderJbr() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdkProvider", "jbr");

        panel.load(jdeploy);

        assertEquals("JetBrains Runtime (JBR)", panel.getJdkProvider().getSelectedItem());
    }

    @Test
    @DisplayName("Should load jdkProvider as Auto when not jbr")
    void testLoadJdkProviderAuto() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdkProvider", "zulu");

        panel.load(jdeploy);

        assertEquals("Auto (Recommended)", panel.getJdkProvider().getSelectedItem());
    }

    @Test
    @DisplayName("Should default jdkProvider to Auto when missing")
    void testLoadJdkProviderMissing() {
        JSONObject jdeploy = new JSONObject();

        panel.load(jdeploy);

        assertEquals("Auto (Recommended)", panel.getJdkProvider().getSelectedItem());
    }

    @Test
    @DisplayName("Should load jbrVariant as JCEF from jdeploy object")
    void testLoadJbrVariantJcef() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jbrVariant", "jcef");

        panel.load(jdeploy);

        assertEquals("JCEF", panel.getJbrVariant().getSelectedItem());
    }

    @Test
    @DisplayName("Should load jbrVariant as Default when not jcef")
    void testLoadJbrVariantDefault() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jbrVariant", "standard");

        panel.load(jdeploy);

        assertEquals("Default", panel.getJbrVariant().getSelectedItem());
    }

    @Test
    @DisplayName("Should handle null jdeploy gracefully")
    void testLoadNullJdeploy() {
        panel.load(null);
        // Should not throw exception
    }

    // ========== SAVE TESTS ==========

    @Test
    @DisplayName("Should save jar to jdeploy object")
    void testSaveJar() {
        JSONObject jdeploy = new JSONObject();
        panel.getJarFile().setText("build/libs/app.jar");

        panel.save(jdeploy);

        assertEquals("build/libs/app.jar", jdeploy.getString("jar"));
    }

    @Test
    @DisplayName("Should not save empty jar")
    void testSaveEmptyJar() {
        JSONObject jdeploy = new JSONObject();
        panel.getJarFile().setText("");

        panel.save(jdeploy);

        assertFalse(jdeploy.has("jar"));
    }

    @Test
    @DisplayName("Should save javafx flag when true")
    void testSaveJavaFXTrue() {
        JSONObject jdeploy = new JSONObject();
        panel.getRequiresJavaFX().setSelected(true);

        panel.save(jdeploy);

        assertTrue(jdeploy.getBoolean("javafx"));
    }

    @Test
    @DisplayName("Should not save javafx flag when false")
    void testSaveJavaFXFalse() {
        JSONObject jdeploy = new JSONObject();
        panel.getRequiresJavaFX().setSelected(false);

        panel.save(jdeploy);

        assertFalse(jdeploy.has("javafx"));
    }

    @Test
    @DisplayName("Should save jdk flag when true")
    void testSaveJdkTrue() {
        JSONObject jdeploy = new JSONObject();
        panel.getRequiresFullJDK().setSelected(true);

        panel.save(jdeploy);

        assertTrue(jdeploy.getBoolean("jdk"));
    }

    @Test
    @DisplayName("Should not save jdk flag when false")
    void testSaveJdkFalse() {
        JSONObject jdeploy = new JSONObject();
        panel.getRequiresFullJDK().setSelected(false);

        panel.save(jdeploy);

        assertFalse(jdeploy.has("jdk"));
    }

    @Test
    @DisplayName("Should save javaVersion to jdeploy object")
    void testSaveJavaVersion() {
        JSONObject jdeploy = new JSONObject();
        panel.getJavaVersion().setSelectedItem("21");

        panel.save(jdeploy);

        assertEquals("21", jdeploy.getString("javaVersion"));
    }

    @Test
    @DisplayName("Should save jdkProvider as jbr when JBR selected")
    void testSaveJdkProviderJbr() {
        JSONObject jdeploy = new JSONObject();
        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");

        panel.save(jdeploy);

        assertEquals("jbr", jdeploy.getString("jdkProvider"));
    }

    @Test
    @DisplayName("Should not save jdkProvider when Auto selected")
    void testSaveJdkProviderAuto() {
        JSONObject jdeploy = new JSONObject();
        panel.getJdkProvider().setSelectedItem("Auto (Recommended)");

        panel.save(jdeploy);

        assertFalse(jdeploy.has("jdkProvider"));
    }

    @Test
    @DisplayName("Should save jbrVariant as jcef when JCEF selected and provider is JBR")
    void testSaveJbrVariantJcef() {
        JSONObject jdeploy = new JSONObject();
        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");
        panel.getJbrVariant().setSelectedItem("JCEF");

        panel.save(jdeploy);

        assertEquals("jcef", jdeploy.getString("jbrVariant"));
    }

    @Test
    @DisplayName("Should not save jbrVariant when Default selected")
    void testSaveJbrVariantDefault() {
        JSONObject jdeploy = new JSONObject();
        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");
        panel.getJbrVariant().setSelectedItem("Default");

        panel.save(jdeploy);

        assertFalse(jdeploy.has("jbrVariant"));
    }

    @Test
    @DisplayName("Should not save jbrVariant when provider is Auto")
    void testSaveJbrVariantNotSavedWhenAuto() {
        JSONObject jdeploy = new JSONObject();
        panel.getJdkProvider().setSelectedItem("Auto (Recommended)");
        panel.getJbrVariant().setSelectedItem("JCEF");

        panel.save(jdeploy);

        assertFalse(jdeploy.has("jbrVariant"));
    }

    @Test
    @DisplayName("Should handle null jdeploy in save gracefully")
    void testSaveNullJdeploy() {
        panel.save(null);
        // Should not throw exception
    }

    // ========== ROUND TRIP TESTS ==========

    @Test
    @DisplayName("Should handle load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        JSONObject original = new JSONObject();
        original.put("jar", "build/libs/app.jar");
        original.put("javafx", true);
        original.put("jdk", true);
        original.put("javaVersion", "21");
        original.put("jdkProvider", "jbr");
        original.put("jbrVariant", "jcef");

        panel.load(original);

        JSONObject saved = new JSONObject();
        panel.save(saved);

        assertEquals(original.getString("jar"), saved.getString("jar"));
        assertEquals(original.getBoolean("javafx"), saved.getBoolean("javafx"));
        assertEquals(original.getBoolean("jdk"), saved.getBoolean("jdk"));
        assertEquals(original.getString("javaVersion"), saved.getString("javaVersion"));
        assertEquals(original.getString("jdkProvider"), saved.getString("jdkProvider"));
        assertEquals(original.getString("jbrVariant"), saved.getString("jbrVariant"));
    }

    // ========== JBR VARIANT VISIBILITY TESTS ==========

    @Test
    @DisplayName("Should hide JBR Variant field when JDK Provider is Auto")
    void testJbrVariantHiddenWhenAuto() {
        JSONObject jdeploy = new JSONObject();
        // Don't set jdkProvider - defaults to Auto

        panel.load(jdeploy);

        assertFalse(panel.getJbrVariant().isVisible());
    }

    @Test
    @DisplayName("Should show JBR Variant field when JDK Provider is JBR")
    void testJbrVariantVisibleWhenJbr() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jdkProvider", "jbr");

        panel.load(jdeploy);

        assertTrue(panel.getJbrVariant().isVisible());
    }

    @Test
    @DisplayName("Should toggle JBR Variant visibility when switching providers")
    void testJbrVariantVisibilityToggling() {
        JSONObject jdeploy = new JSONObject();
        panel.load(jdeploy);

        // Start with Auto - should be hidden
        assertEquals("Auto (Recommended)", panel.getJdkProvider().getSelectedItem());
        assertFalse(panel.getJbrVariant().isVisible());

        // Switch to JBR - should be visible
        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");
        assertTrue(panel.getJbrVariant().isVisible());

        // Switch back to Auto - should be hidden
        panel.getJdkProvider().setSelectedItem("Auto (Recommended)");
        assertFalse(panel.getJbrVariant().isVisible());
    }

    // ========== CHANGE LISTENER TESTS ==========

    @Test
    @DisplayName("Should fire change listener on text field changes")
    void testChangeListenerFiresOnTextField() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);

        panel.getJarFile().setText("new-value.jar");

        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called");
    }

    @Test
    @DisplayName("Should fire change listener on checkbox changes")
    void testChangeListenerFiresOnCheckbox() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);

        panel.getRequiresJavaFX().setSelected(true);

        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called on checkbox change");
    }

    @Test
    @DisplayName("Should fire change listener on combobox changes")
    void testChangeListenerFiresOnComboBox() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);

        panel.getJavaVersion().setSelectedItem("17");

        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called on combobox change");
    }

    @Test
    @DisplayName("Should fire change listener on jdkProvider changes")
    void testChangeListenerFiresOnJdkProvider() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);

        panel.getJdkProvider().setSelectedItem("JetBrains Runtime (JBR)");

        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called on jdkProvider change");
    }

    // ========== TOOLTIP TESTS ==========

    @Test
    @DisplayName("Should have tooltip on jdkProvider")
    void testJdkProviderTooltip() {
        assertNotNull(panel.getJdkProvider().getToolTipText());
        assertTrue(panel.getJdkProvider().getToolTipText().contains("Auto"));
    }

    @Test
    @DisplayName("Should have tooltip on jbrVariant")
    void testJbrVariantTooltip() {
        assertNotNull(panel.getJbrVariant().getToolTipText());
        assertTrue(panel.getJbrVariant().getToolTipText().contains("JCEF"));
    }

    // ========== VALIDATION RESULT TESTS ==========

    @Test
    @DisplayName("ValidationResult success should be valid")
    void testValidationResultSuccess() {
        JavaRuntimePanel.ValidationResult result = JavaRuntimePanel.ValidationResult.success();
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("ValidationResult failure should be invalid with message")
    void testValidationResultFailure() {
        JavaRuntimePanel.ValidationResult result = JavaRuntimePanel.ValidationResult.failure("Test error");
        assertFalse(result.isValid());
        assertEquals("Test error", result.getErrorMessage());
    }

    // ========== SINGLETON TESTS ==========

    @Test
    @DisplayName("Should load singleton flag from jdeploy object")
    void testLoadSingleton() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("singleton", true);

        panel.load(jdeploy);

        assertTrue(panel.getSingleton().isSelected());
    }

    @Test
    @DisplayName("Should default singleton to false when not present")
    void testLoadSingletonDefault() {
        JSONObject jdeploy = new JSONObject();

        panel.load(jdeploy);

        assertFalse(panel.getSingleton().isSelected());
    }

    @Test
    @DisplayName("Should save singleton flag to jdeploy object when selected")
    void testSaveSingletonSelected() {
        panel.getSingleton().setSelected(true);
        JSONObject jdeploy = new JSONObject();

        panel.save(jdeploy);

        assertTrue(jdeploy.optBoolean("singleton", false));
    }

    @Test
    @DisplayName("Should remove singleton flag from jdeploy object when not selected")
    void testSaveSingletonNotSelected() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("singleton", true);
        panel.load(jdeploy);
        panel.getSingleton().setSelected(false);

        panel.save(jdeploy);

        assertFalse(jdeploy.has("singleton"));
    }

    @Test
    @DisplayName("Should fire change event when singleton checkbox changes")
    void testSingletonChangeEvent() {
        ActionListener listener = evt -> changeListenerCallCount.incrementAndGet();
        panel.addChangeListener(listener);

        panel.getSingleton().setSelected(true);

        assertTrue(changeListenerCallCount.get() > 0, "Change listener should have been called on singleton change");
    }

    @Test
    @DisplayName("Should have tooltip on singleton checkbox")
    void testSingletonTooltip() {
        assertNotNull(panel.getSingleton().getToolTipText());
        assertTrue(panel.getSingleton().getToolTipText().contains("instance"));
    }
}
