package ca.weblite.jdeploy.gui.navigation;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NavigablePanelAdapter}.
 * 
 * <p>These tests verify that the adapter correctly delegates to its wrapped components
 * and properly handles load/save operations with different JSONObject configurations.</p>
 */
class NavigablePanelAdapterTest {
    
    private NavigablePanelAdapter adapter;
    private JComponent rootComponent;
    private AtomicInteger loaderCallCount;
    private AtomicInteger saverCallCount;
    private List<ActionListener> registeredListeners;
    private AtomicBoolean displayConditionValue;
    
    @BeforeEach
    void setUp() {
        rootComponent = new JPanel();
        loaderCallCount = new AtomicInteger(0);
        saverCallCount = new AtomicInteger(0);
        registeredListeners = new ArrayList<>();
        displayConditionValue = new AtomicBoolean(true);
    }
    
    @Test
    void testGetTitle() {
        adapter = new NavigablePanelAdapter(
            "Test Title",
            "http://example.com/help",
            rootComponent,
            json -> loaderCallCount.incrementAndGet(),
            json -> saverCallCount.incrementAndGet(),
            registeredListeners::add,
            displayConditionValue::get
        );
        
        assertEquals("Test Title", adapter.getTitle());
    }
    
    @Test
    void testGetHelpUrl() {
        adapter = new NavigablePanelAdapter(
            "Test Title",
            "http://example.com/help",
            rootComponent,
            json -> loaderCallCount.incrementAndGet(),
            json -> saverCallCount.incrementAndGet(),
            registeredListeners::add,
            displayConditionValue::get
        );
        
        assertEquals("http://example.com/help", adapter.getHelpUrl());
    }
    
    @Test
    void testGetHelpUrlNull() {
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> loaderCallCount.incrementAndGet(),
            json -> saverCallCount.incrementAndGet(),
            registeredListeners::add,
            displayConditionValue::get
        );
        
        assertNull(adapter.getHelpUrl());
    }
    
    @Test
    void testGetRoot() {
        adapter = new NavigablePanelAdapter(
            "Test Title",
            "http://example.com/help",
            rootComponent,
            json -> loaderCallCount.incrementAndGet(),
            json -> saverCallCount.incrementAndGet(),
            registeredListeners::add,
            displayConditionValue::get
        );
        
        assertSame(rootComponent, adapter.getRoot());
    }
    
    @Test
    void testLoadWithJdeployObject() {
        List<JSONObject> loadedObjects = new ArrayList<>();
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            loadedObjects::add,
            json -> {},
            registeredListeners::add,
            displayConditionValue::get
        );
        
        JSONObject jdeployObj = new JSONObject().put("key", "value");
        JSONObject packageJsonObj = new JSONObject().put("name", "test");
        
        adapter.load(packageJsonObj, jdeployObj);
        
        assertEquals(1, loadedObjects.size());
        assertEquals("value", loadedObjects.get(0).getString("key"));
    }
    
    @Test
    void testLoadWithPackageJsonObject() {
        List<JSONObject> loadedObjects = new ArrayList<>();
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            loadedObjects::add,
            json -> {},
            registeredListeners::add,
            displayConditionValue::get
        );
        
        JSONObject packageJsonObj = new JSONObject().put("name", "test");
        
        adapter.load(packageJsonObj, null);
        
        assertEquals(1, loadedObjects.size());
        assertEquals("test", loadedObjects.get(0).getString("name"));
    }
    
    @Test
    void testLoadWithNullObjects() {
        List<JSONObject> loadedObjects = new ArrayList<>();
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            loadedObjects::add,
            json -> {},
            registeredListeners::add,
            displayConditionValue::get
        );
        
        adapter.load(null, null);
        
        assertEquals(0, loadedObjects.size());
    }
    
    @Test
    void testSaveWithJdeployObject() {
        List<JSONObject> savedObjects = new ArrayList<>();
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            savedObjects::add,
            registeredListeners::add,
            displayConditionValue::get
        );
        
        JSONObject jdeployObj = new JSONObject().put("key", "value");
        JSONObject packageJsonObj = new JSONObject().put("name", "test");
        
        adapter.save(packageJsonObj, jdeployObj);
        
        assertEquals(1, savedObjects.size());
        assertEquals("value", savedObjects.get(0).getString("key"));
    }
    
    @Test
    void testSaveWithPackageJsonObject() {
        List<JSONObject> savedObjects = new ArrayList<>();
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            savedObjects::add,
            registeredListeners::add,
            displayConditionValue::get
        );
        
        JSONObject packageJsonObj = new JSONObject().put("name", "test");
        
        adapter.save(packageJsonObj, null);
        
        assertEquals(1, savedObjects.size());
        assertEquals("test", savedObjects.get(0).getString("name"));
    }
    
    @Test
    void testSaveWithNullObjects() {
        List<JSONObject> savedObjects = new ArrayList<>();
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            savedObjects::add,
            registeredListeners::add,
            displayConditionValue::get
        );
        
        adapter.save(null, null);
        
        assertEquals(0, savedObjects.size());
    }
    
    @Test
    void testAddChangeListener() {
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            json -> {},
            registeredListeners::add,
            displayConditionValue::get
        );
        
        ActionListener listener = e -> {};
        adapter.addChangeListener(listener);
        
        assertEquals(1, registeredListeners.size());
        assertSame(listener, registeredListeners.get(0));
    }
    
    @Test
    void testAddMultipleChangeListeners() {
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            json -> {},
            registeredListeners::add,
            displayConditionValue::get
        );
        
        ActionListener listener1 = e -> {};
        ActionListener listener2 = e -> {};
        
        adapter.addChangeListener(listener1);
        adapter.addChangeListener(listener2);
        
        assertEquals(2, registeredListeners.size());
        assertSame(listener1, registeredListeners.get(0));
        assertSame(listener2, registeredListeners.get(1));
    }
    
    @Test
    void testShouldDisplayTrue() {
        displayConditionValue.set(true);
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            json -> {},
            registeredListeners::add,
            displayConditionValue::get
        );
        
        assertTrue(adapter.shouldDisplay());
    }
    
    @Test
    void testShouldDisplayFalse() {
        displayConditionValue.set(false);
        
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            json -> {},
            registeredListeners::add,
            displayConditionValue::get
        );
        
        assertFalse(adapter.shouldDisplay());
    }
    
    @Test
    void testForJdeployPanelFactory() {
        List<JSONObject> loadedObjects = new ArrayList<>();
        List<JSONObject> savedObjects = new ArrayList<>();
        
        adapter = NavigablePanelAdapter.forJdeployPanel(
            "Jdeploy Panel",
            "http://example.com/help",
            rootComponent,
            loadedObjects::add,
            savedObjects::add,
            registeredListeners::add
        );
        
        assertEquals("Jdeploy Panel", adapter.getTitle());
        assertEquals("http://example.com/help", adapter.getHelpUrl());
        assertTrue(adapter.shouldDisplay());
        
        JSONObject jdeployObj = new JSONObject().put("setting", "value");
        adapter.load(null, jdeployObj);
        
        assertEquals(1, loadedObjects.size());
        assertEquals("value", loadedObjects.get(0).getString("setting"));
    }
    
    @Test
    void testForJdeployPanelFactoryWithDisplayCondition() {
        List<JSONObject> loadedObjects = new ArrayList<>();
        
        adapter = NavigablePanelAdapter.forJdeployPanel(
            "Jdeploy Panel",
            null,
            rootComponent,
            loadedObjects::add,
            json -> {},
            registeredListeners::add,
            () -> false
        );
        
        assertFalse(adapter.shouldDisplay());
    }
    
    @Test
    void testForPackageJsonPanelFactory() {
        List<JSONObject> loadedObjects = new ArrayList<>();
        List<JSONObject> savedObjects = new ArrayList<>();
        
        adapter = NavigablePanelAdapter.forPackageJsonPanel(
            "Package.json Panel",
            "http://example.com/help",
            rootComponent,
            loadedObjects::add,
            savedObjects::add,
            registeredListeners::add
        );
        
        assertEquals("Package.json Panel", adapter.getTitle());
        assertEquals("http://example.com/help", adapter.getHelpUrl());
        assertTrue(adapter.shouldDisplay());
        
        JSONObject packageJsonObj = new JSONObject().put("name", "my-package");
        adapter.load(packageJsonObj, null);
        
        assertEquals(1, loadedObjects.size());
        assertEquals("my-package", loadedObjects.get(0).getString("name"));
    }
    
    @Test
    void testForPackageJsonPanelFactoryPrefersPackageJson() {
        List<JSONObject> loadedObjects = new ArrayList<>();
        
        adapter = NavigablePanelAdapter.forPackageJsonPanel(
            "Package.json Panel",
            null,
            rootComponent,
            loadedObjects::add,
            json -> {},
            registeredListeners::add
        );
        
        JSONObject packageJsonObj = new JSONObject().put("name", "package");
        JSONObject jdeployObj = new JSONObject().put("setting", "jdeploy-value");
        
        adapter.load(packageJsonObj, jdeployObj);
        
        // Should prefer packageJSON even if jdeploy is provided
        assertEquals(1, loadedObjects.size());
        assertEquals("package", loadedObjects.get(0).getString("name"));
        assertFalse(loadedObjects.get(0).has("setting"));
    }
    
    @Test
    void testForPackageJsonPanelFactorySavePrefersPackageJson() {
        List<JSONObject> savedObjects = new ArrayList<>();
        
        adapter = NavigablePanelAdapter.forPackageJsonPanel(
            "Package.json Panel",
            null,
            rootComponent,
            json -> {},
            savedObjects::add,
            registeredListeners::add
        );
        
        JSONObject packageJsonObj = new JSONObject().put("name", "package");
        JSONObject jdeployObj = new JSONObject().put("setting", "jdeploy-value");
        
        adapter.save(packageJsonObj, jdeployObj);
        
        // Should prefer packageJSON even if jdeploy is provided
        assertEquals(1, savedObjects.size());
        assertEquals("package", savedObjects.get(0).getString("name"));
    }
    
    @Test
    void testForPackageJsonPanelFactoryWithDisplayCondition() {
        adapter = NavigablePanelAdapter.forPackageJsonPanel(
            "Package.json Panel",
            null,
            rootComponent,
            json -> {},
            json -> {},
            registeredListeners::add,
            () -> true
        );
        
        assertTrue(adapter.shouldDisplay());
    }
    
    @Test
    void testImplementsNavigablePanel() {
        adapter = NavigablePanelAdapter.forJdeployPanel(
            "Test",
            null,
            rootComponent,
            json -> {},
            json -> {},
            registeredListeners::add
        );
        
        assertTrue(adapter instanceof NavigablePanel);
    }
    
    @Test
    void testChangeListenerCanBeNull() {
        adapter = new NavigablePanelAdapter(
            "Test Title",
            null,
            rootComponent,
            json -> {},
            json -> {},
            listener -> {
                // Can handle null listeners
            },
            displayConditionValue::get
        );
        
        // Should not throw
        adapter.addChangeListener(null);
    }
}
