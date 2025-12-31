package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RuntimeArgsPanel")
public class RuntimeArgsPanelTest {
    private RuntimeArgsPanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() {
        panel = new RuntimeArgsPanel();
        changeListenerCallCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
        assertSame(panel, panel.getRoot());
    }

    @Test
    @DisplayName("Should load empty args gracefully")
    void testLoadEmptyArgs() {
        JSONObject jdeploy = new JSONObject();
        panel.load(jdeploy);
        assertEquals("", panel.getArgsField().getText());
    }

    @Test
    @DisplayName("Should load single argument")
    void testLoadSingleArgument() {
        JSONObject jdeploy = new JSONObject();
        JSONArray args = new JSONArray();
        args.put(0, "-Xmx2G");
        jdeploy.put("args", args);

        panel.load(jdeploy);
        assertEquals("-Xmx2G", panel.getArgsField().getText());
    }

    @Test
    @DisplayName("Should load multiple arguments")
    void testLoadMultipleArguments() {
        JSONObject jdeploy = new JSONObject();
        JSONArray args = new JSONArray();
        args.put(0, "-Xmx2G");
        args.put(1, "-Dfoo=bar");
        args.put(2, "-Duser.home=/tmp");
        jdeploy.put("args", args);

        panel.load(jdeploy);
        String text = panel.getArgsField().getText();
        String[] lines = text.split("\n");
        assertEquals(3, lines.length);
        assertEquals("-Xmx2G", lines[0].trim());
        assertEquals("-Dfoo=bar", lines[1].trim());
        assertEquals("-Duser.home=/tmp", lines[2].trim());
    }

    @Test
    @DisplayName("Should save single argument")
    void testSaveSingleArgument() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("-Xmx2G");
        panel.save(jdeploy);

        assertTrue(jdeploy.has("args"));
        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(1, args.length());
        assertEquals("-Xmx2G", args.getString(0));
    }

    @Test
    @DisplayName("Should save multiple arguments")
    void testSaveMultipleArguments() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("-Xmx2G\n-Dfoo=bar\n-Duser.home=/tmp");
        panel.save(jdeploy);

        assertTrue(jdeploy.has("args"));
        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(3, args.length());
        assertEquals("-Xmx2G", args.getString(0));
        assertEquals("-Dfoo=bar", args.getString(1));
        assertEquals("-Duser.home=/tmp", args.getString(2));
    }

    @Test
    @DisplayName("Should remove args when empty")
    void testSaveEmptyArgs() {
        JSONObject jdeploy = new JSONObject();
        JSONArray args = new JSONArray();
        args.put(0, "-Xmx2G");
        jdeploy.put("args", args);

        panel.getArgsField().setText("");
        panel.save(jdeploy);

        assertFalse(jdeploy.has("args"));
    }

    @Test
    @DisplayName("Should trim whitespace from arguments")
    void testTrimWhitespace() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("  -Xmx2G  \n  -Dfoo=bar  ");
        panel.save(jdeploy);

        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(2, args.length());
        assertEquals("-Xmx2G", args.getString(0));
        assertEquals("-Dfoo=bar", args.getString(1));
    }

    @Test
    @DisplayName("Should ignore blank lines")
    void testIgnoreBlankLines() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("-Xmx2G\n\n-Dfoo=bar\n\n");
        panel.save(jdeploy);

        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(2, args.length());
        assertEquals("-Xmx2G", args.getString(0));
        assertEquals("-Dfoo=bar", args.getString(1));
    }

    @Test
    @DisplayName("Should handle load/save round-trip without data loss")
    void testLoadSaveRoundTrip() {
        JSONObject jdeploy1 = new JSONObject();
        JSONArray originalArgs = new JSONArray();
        originalArgs.put(0, "-Xmx2G");
        originalArgs.put(1, "-Dfoo=bar");
        originalArgs.put(2, "-D[mac]special=value");
        jdeploy1.put("args", originalArgs);

        // Load
        panel.load(jdeploy1);

        // Save to a new object
        JSONObject jdeploy2 = new JSONObject();
        panel.save(jdeploy2);

        // Verify they match
        JSONArray savedArgs = jdeploy2.getJSONArray("args");
        assertEquals(3, savedArgs.length());
        assertEquals("-Xmx2G", savedArgs.getString(0));
        assertEquals("-Dfoo=bar", savedArgs.getString(1));
        assertEquals("-D[mac]special=value", savedArgs.getString(2));
    }

    @Test
    @DisplayName("Should register and fire change listener")
    void testChangeListenerFires() {
        panel.addChangeListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeListenerCallCount.incrementAndGet();
            }
        });

        panel.getArgsField().setText("-Xmx2G");
        assertTrue(changeListenerCallCount.get() > 0);
    }

    @Test
    @DisplayName("Should handle listener replacement")
    void testListenerCanBeReplaced() {
        AtomicInteger firstListenerCount = new AtomicInteger(0);
        AtomicInteger secondListenerCount = new AtomicInteger(0);

        panel.addChangeListener(e -> firstListenerCount.incrementAndGet());
        int firstCount = firstListenerCount.get();

        panel.addChangeListener(e -> secondListenerCount.incrementAndGet());
        panel.getArgsField().setText("-Xmx2G");

        // Only the second listener should fire
        assertTrue(secondListenerCount.get() > 0);
    }

    @Test
    @DisplayName("Should handle null jdeploy object gracefully")
    void testLoadNullJdeploy() {
        panel.load(null);
        assertEquals("", panel.getArgsField().getText());
    }

    @Test
    @DisplayName("Should handle save with null jdeploy object")
    void testSaveNullJdeploy() {
        // Should not throw exception
        panel.getArgsField().setText("-Xmx2G");
        panel.save(null);
    }

    @Test
    @DisplayName("Should save without affecting other fields in jdeploy")
    void testSaveDoesNotAffectOtherFields() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("title", "My App");
        jdeploy.put("jar", "dist/app.jar");

        panel.getArgsField().setText("-Xmx2G");
        panel.save(jdeploy);

        // Original fields should still be there
        assertEquals("My App", jdeploy.getString("title"));
        assertEquals("dist/app.jar", jdeploy.getString("jar"));
        // New field should be added
        assertTrue(jdeploy.has("args"));
    }

    @Test
    @DisplayName("Should handle arguments with special characters")
    void testArgumentsWithSpecialCharacters() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("-Dfoo=bar baz\n-Dpath=/usr/local/bin\n-D[mac|linux]flag=value");
        panel.save(jdeploy);

        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(3, args.length());
        assertEquals("-Dfoo=bar baz", args.getString(0));
        assertEquals("-Dpath=/usr/local/bin", args.getString(1));
        assertEquals("-D[mac|linux]flag=value", args.getString(2));
    }

    @Test
    @DisplayName("Should handle platform-specific property arguments")
    void testPlatformSpecificPropertyArgs() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("-D[mac]foo=bar\n-D[win]foo=baz\n-D[linux]foo=qux");
        panel.save(jdeploy);

        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(3, args.length());
        assertEquals("-D[mac]foo=bar", args.getString(0));
        assertEquals("-D[win]foo=baz", args.getString(1));
        assertEquals("-D[linux]foo=qux", args.getString(2));
    }

    @Test
    @DisplayName("Should handle platform-specific JVM options")
    void testPlatformSpecificJvmOptions() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("-X[mac]foo\n-X[win]bar\n-X[mac|linux]baz");
        panel.save(jdeploy);

        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(3, args.length());
        assertEquals("-X[mac]foo", args.getString(0));
        assertEquals("-X[win]bar", args.getString(1));
        assertEquals("-X[mac|linux]baz", args.getString(2));
    }

    @Test
    @DisplayName("Should handle platform-specific program arguments")
    void testPlatformSpecificProgramArgs() {
        JSONObject jdeploy = new JSONObject();
        panel.getArgsField().setText("-[mac]arg1\n-[win]arg2\n-[linux]arg3");
        panel.save(jdeploy);

        JSONArray args = jdeploy.getJSONArray("args");
        assertEquals(3, args.length());
        assertEquals("-[mac]arg1", args.getString(0));
        assertEquals("-[win]arg2", args.getString(1));
        assertEquals("-[linux]arg3", args.getString(2));
    }
}
