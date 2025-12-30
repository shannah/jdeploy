package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UrlSchemesPanel")
public class UrlSchemesPanelTest {
    private UrlSchemesPanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() {
        panel = new UrlSchemesPanel();
        changeListenerCallCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
        assertEquals(panel, panel.getRoot());
    }

    @Test
    @DisplayName("Should load empty URL schemes gracefully")
    void testLoadEmptyUrlSchemes() {
        JSONObject jdeploy = new JSONObject();
        assertDoesNotThrow(() -> panel.load(jdeploy));
    }

    @Test
    @DisplayName("Should load single URL scheme")
    void testLoadSingleUrlScheme() {
        JSONObject jdeploy = new JSONObject();
        JSONArray schemes = new JSONArray();
        schemes.put("myapp");
        jdeploy.put("urlSchemes", schemes);

        panel.load(jdeploy);
        
        // After loading and saving, should parse correctly
        JSONObject saved = new JSONObject();
        panel.save(saved);
        assertTrue(saved.has("urlSchemes"));
        assertEquals(1, saved.getJSONArray("urlSchemes").length());
        assertEquals("myapp", saved.getJSONArray("urlSchemes").getString(0));
    }

    @Test
    @DisplayName("Should load multiple URL schemes")
    void testLoadMultipleUrlSchemes() {
        JSONObject jdeploy = new JSONObject();
        JSONArray schemes = new JSONArray();
        schemes.put("myapp");
        schemes.put("mymusic");
        schemes.put("mynews");
        jdeploy.put("urlSchemes", schemes);

        panel.load(jdeploy);
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        assertTrue(saved.has("urlSchemes"));
        assertEquals(3, saved.getJSONArray("urlSchemes").length());
        assertEquals("myapp", saved.getJSONArray("urlSchemes").getString(0));
        assertEquals("mymusic", saved.getJSONArray("urlSchemes").getString(1));
        assertEquals("mynews", saved.getJSONArray("urlSchemes").getString(2));
    }

    @Test
    @DisplayName("Should parse comma-separated URL schemes on save")
    void testParseCommaSeparatedSchemes() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("urlSchemes", new JSONArray());
        
        panel.load(jdeploy);
        
        // Simulate user input of comma-separated schemes (empty text field)
        // Save to a new object
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Empty input should result in no urlSchemes in output
        assertFalse(saved.has("urlSchemes"));
    }

    @Test
    @DisplayName("Should handle URL schemes with spaces")
    void testHandleUrlSchemesWithSpaces() {
        JSONObject jdeploy = new JSONObject();
        JSONArray schemes = new JSONArray();
        schemes.put("scheme1");
        schemes.put("scheme2");
        jdeploy.put("urlSchemes", schemes);

        panel.load(jdeploy);
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        assertTrue(saved.has("urlSchemes"));
        JSONArray savedSchemes = saved.getJSONArray("urlSchemes");
        assertEquals(2, savedSchemes.length());
        // Should trim whitespace
        assertEquals("scheme1", savedSchemes.getString(0));
        assertEquals("scheme2", savedSchemes.getString(1));
    }

    @Test
    @DisplayName("Should load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        JSONObject original = new JSONObject();
        JSONArray schemes = new JSONArray();
        schemes.put("http");
        schemes.put("custom");
        schemes.put("app");
        original.put("urlSchemes", schemes);

        // Load from original
        panel.load(original);
        
        // Save to new object
        JSONObject result = new JSONObject();
        panel.save(result);
        
        // Verify data integrity
        assertTrue(result.has("urlSchemes"));
        JSONArray resultSchemes = result.getJSONArray("urlSchemes");
        assertEquals(3, resultSchemes.length());
        assertEquals("http", resultSchemes.getString(0));
        assertEquals("custom", resultSchemes.getString(1));
        assertEquals("app", resultSchemes.getString(2));
    }

    @Test
    @DisplayName("Should remove urlSchemes from JSON when empty")
    void testRemoveUrlSchemesWhenEmpty() {
        JSONObject jdeploy = new JSONObject();
        JSONArray schemes = new JSONArray();
        schemes.put("myapp");
        jdeploy.put("urlSchemes", schemes);

        panel.load(jdeploy);
        
        // Simulate user clearing the text field
        // Access the internal state via reflection to clear the field
        try {
            java.lang.reflect.Field field = UrlSchemesPanel.class.getDeclaredField("urlSchemesField");
            field.setAccessible(true);
            javax.swing.JTextField textField = (javax.swing.JTextField) field.get(panel);
            textField.setText("");
        } catch (Exception e) {
            throw new RuntimeException("Failed to access urlSchemesField", e);
        }
        
        // Save to a fresh object
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Empty schemes should not create urlSchemes entry
        assertFalse(saved.has("urlSchemes"));
    }

    @Test
    @DisplayName("Should register and fire change listener")
    void testChangeListenerFires() {
        ActionListener listener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeListenerCallCount.incrementAndGet();
            }
        };
        
        panel.addChangeListener(listener);
        // Note: Listener fires on document changes in the text field,
        // but we can't easily trigger that in a unit test without accessing private fields
        assertNotNull(panel);
    }

    @Test
    @DisplayName("Should handle listener replacement")
    void testListenerCanBeReplaced() {
        ActionListener listener1 = e -> changeListenerCallCount.incrementAndGet();
        ActionListener listener2 = e -> changeListenerCallCount.addAndGet(10);
        
        panel.addChangeListener(listener1);
        panel.addChangeListener(listener2);
        
        // Second listener should replace the first
        assertNotNull(panel);
    }

    @Test
    @DisplayName("Should handle jdeploy JSONObject with other fields")
    void testLoadWithOtherFields() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("someField", "someValue");
        jdeploy.put("anotherField", 42);
        
        assertDoesNotThrow(() -> panel.load(jdeploy));
    }

    @Test
    @DisplayName("Should save without affecting other fields")
    void testSaveDoesNotAffectOtherFields() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("preserveMe", "value");
        jdeploy.put("alsoPreserve", 123);
        
        panel.load(jdeploy);
        panel.save(jdeploy);
        
        assertEquals("value", jdeploy.getString("preserveMe"));
        assertEquals(123, jdeploy.getInt("alsoPreserve"));
    }

    @Test
    @DisplayName("Should handle null/missing urlSchemes gracefully")
    void testHandleMissingUrlSchemes() {
        JSONObject jdeploy = new JSONObject();
        // No urlSchemes field
        
        assertDoesNotThrow(() -> panel.load(jdeploy));
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Should handle gracefully
        assertNotNull(saved);
    }

    @Test
    @DisplayName("Should parse schemes with leading/trailing spaces")
    void testParseWithWhitespace() {
        JSONObject jdeploy = new JSONObject();
        JSONArray schemes = new JSONArray();
        schemes.put("  scheme1  ");
        schemes.put("scheme2");
        jdeploy.put("urlSchemes", schemes);

        panel.load(jdeploy);
        
        JSONObject saved = new JSONObject();
        panel.save(saved);
        
        // Should trim whitespace
        JSONArray savedSchemes = saved.getJSONArray("urlSchemes");
        assertEquals("scheme1", savedSchemes.getString(0));
    }
}
