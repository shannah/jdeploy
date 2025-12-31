package ca.weblite.jdeploy.gui.navigation;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EditorPanelRegistry}.
 */
public class EditorPanelRegistryTest {
    
    private EditorPanelRegistry registry;
    private MockNavigablePanel panel1;
    private MockNavigablePanel panel2;
    private MockNavigablePanel panel3;
    
    @BeforeEach
    public void setUp() {
        registry = new EditorPanelRegistry();
        panel1 = new MockNavigablePanel("Panel 1");
        panel2 = new MockNavigablePanel("Panel 2");
        panel3 = new MockNavigablePanel("Panel 3");
    }
    
    // ===== Registration Tests =====
    
    @Test
    public void testRegisterSinglePanel() {
        registry.register(panel1);
        
        assertEquals(1, registry.getPanelCount());
        assertTrue(registry.getPanels().contains(panel1));
    }
    
    @Test
    public void testRegisterMultiplePanels() {
        registry.register(panel1);
        registry.register(panel2);
        registry.register(panel3);
        
        assertEquals(3, registry.getPanelCount());
        assertTrue(registry.getPanels().contains(panel1));
        assertTrue(registry.getPanels().contains(panel2));
        assertTrue(registry.getPanels().contains(panel3));
    }
    
    @Test
    public void testRegisterNullPanel() {
        registry.register(null);
        
        assertEquals(0, registry.getPanelCount());
    }
    
    @Test
    public void testRegisterMixedNullAndValidPanels() {
        registry.register(panel1);
        registry.register(null);
        registry.register(panel2);
        registry.register(null);
        registry.register(panel3);
        
        assertEquals(3, registry.getPanelCount());
        assertTrue(registry.getPanels().contains(panel1));
        assertTrue(registry.getPanels().contains(panel2));
        assertTrue(registry.getPanels().contains(panel3));
    }
    
    // ===== Load Tests =====
    
    @Test
    public void testLoadAllWithBothParameters() {
        registry.register(panel1);
        registry.register(panel2);
        
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        
        registry.loadAll(packageJSON, jdeploy);
        
        assertTrue(panel1.wasLoadCalled);
        assertTrue(panel2.wasLoadCalled);
        assertSame(packageJSON, panel1.lastLoadPackageJSON);
        assertSame(jdeploy, panel1.lastLoadJdeploy);
        assertSame(packageJSON, panel2.lastLoadPackageJSON);
        assertSame(jdeploy, panel2.lastLoadJdeploy);
    }
    
    @Test
    public void testLoadAllWithSingleParameter() {
        registry.register(panel1);
        registry.register(panel2);
        
        JSONObject packageJSON = new JSONObject();
        
        registry.loadAll(packageJSON);
        
        assertTrue(panel1.wasLoadCalled);
        assertTrue(panel2.wasLoadCalled);
        assertSame(packageJSON, panel1.lastLoadPackageJSON);
        assertNull(panel1.lastLoadJdeploy);
        assertSame(packageJSON, panel2.lastLoadPackageJSON);
        assertNull(panel2.lastLoadJdeploy);
    }
    
    @Test
    public void testLoadAllWithNullParameters() {
        registry.register(panel1);
        registry.register(panel2);
        
        registry.loadAll(null, null);
        
        assertTrue(panel1.wasLoadCalled);
        assertTrue(panel2.wasLoadCalled);
        assertNull(panel1.lastLoadPackageJSON);
        assertNull(panel1.lastLoadJdeploy);
        assertNull(panel2.lastLoadPackageJSON);
        assertNull(panel2.lastLoadJdeploy);
    }
    
    @Test
    public void testLoadAllWithoutAnyPanels() {
        JSONObject packageJSON = new JSONObject();
        
        // Should not throw
        registry.loadAll(packageJSON);
        assertEquals(0, registry.getPanelCount());
    }
    
    // ===== Save Tests =====
    
    @Test
    public void testSaveAllWithBothParameters() {
        registry.register(panel1);
        registry.register(panel2);
        
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        
        registry.saveAll(packageJSON, jdeploy);
        
        assertTrue(panel1.wasSaveCalled);
        assertTrue(panel2.wasSaveCalled);
        assertSame(packageJSON, panel1.lastSavePackageJSON);
        assertSame(jdeploy, panel1.lastSaveJdeploy);
        assertSame(packageJSON, panel2.lastSavePackageJSON);
        assertSame(jdeploy, panel2.lastSaveJdeploy);
    }
    
    @Test
    public void testSaveAllWithSingleParameter() {
        registry.register(panel1);
        registry.register(panel2);
        
        JSONObject packageJSON = new JSONObject();
        
        registry.saveAll(packageJSON);
        
        assertTrue(panel1.wasSaveCalled);
        assertTrue(panel2.wasSaveCalled);
        assertSame(packageJSON, panel1.lastSavePackageJSON);
        assertNull(panel1.lastSaveJdeploy);
        assertSame(packageJSON, panel2.lastSavePackageJSON);
        assertNull(panel2.lastSaveJdeploy);
    }
    
    @Test
    public void testSaveAllWithNullParameters() {
        registry.register(panel1);
        registry.register(panel2);
        
        registry.saveAll(null, null);
        
        assertTrue(panel1.wasSaveCalled);
        assertTrue(panel2.wasSaveCalled);
        assertNull(panel1.lastSavePackageJSON);
        assertNull(panel1.lastSaveJdeploy);
        assertNull(panel2.lastSavePackageJSON);
        assertNull(panel2.lastSaveJdeploy);
    }
    
    @Test
    public void testSaveAllWithoutAnyPanels() {
        JSONObject packageJSON = new JSONObject();
        
        // Should not throw
        registry.saveAll(packageJSON);
        assertEquals(0, registry.getPanelCount());
    }
    
    // ===== Change Listener Tests =====
    
    @Test
    public void testAttachChangeListeners() {
        registry.register(panel1);
        registry.register(panel2);
        
        AtomicInteger callCount = new AtomicInteger(0);
        registry.attachChangeListeners(callCount::incrementAndGet);
        
        assertTrue(panel1.changeListenerAttached);
        assertTrue(panel2.changeListenerAttached);
    }
    
    @Test
    public void testAttachChangeListenersInvokesCallbackOnPanelChange() {
        registry.register(panel1);
        
        AtomicInteger callCount = new AtomicInteger(0);
        registry.attachChangeListeners(callCount::incrementAndGet);
        
        // Trigger the listener on panel1
        panel1.triggerChangeListener();
        
        assertEquals(1, callCount.get());
    }
    
    @Test
    public void testAttachChangeListenersMultiplePanelsAndChanges() {
        registry.register(panel1);
        registry.register(panel2);
        registry.register(panel3);
        
        AtomicInteger callCount = new AtomicInteger(0);
        registry.attachChangeListeners(callCount::incrementAndGet);
        
        panel1.triggerChangeListener();
        assertEquals(1, callCount.get());
        
        panel2.triggerChangeListener();
        assertEquals(2, callCount.get());
        
        panel3.triggerChangeListener();
        assertEquals(3, callCount.get());
    }
    
    @Test
    public void testAttachChangeListenersWithNullCallback() {
        registry.register(panel1);
        registry.register(panel2);
        
        // Should not throw
        registry.attachChangeListeners(null);
        
        assertFalse(panel1.changeListenerAttached);
        assertFalse(panel2.changeListenerAttached);
    }
    
    @Test
    public void testAttachChangeListenersWithoutAnyPanels() {
        AtomicInteger callCount = new AtomicInteger(0);
        
        // Should not throw
        registry.attachChangeListeners(callCount::incrementAndGet);
        
        assertEquals(0, callCount.get());
    }
    
    // ===== Populate Host Tests =====
    
    @Test
    public void testPopulateHostWithDisplayablePanels() {
        registry.register(panel1);
        registry.register(panel2);
        registry.register(panel3);
        
        MockNavigationHost host = new MockNavigationHost();
        registry.populateHost(host);
        
        assertEquals(3, host.addedPanels.size());
        assertTrue(host.addedPanels.contains(panel1));
        assertTrue(host.addedPanels.contains(panel2));
        assertTrue(host.addedPanels.contains(panel3));
    }
    
    @Test
    public void testPopulateHostFiltersHiddenPanels() {
        panel1.setShouldDisplay(true);
        panel2.setShouldDisplay(false);
        panel3.setShouldDisplay(true);
        
        registry.register(panel1);
        registry.register(panel2);
        registry.register(panel3);
        
        MockNavigationHost host = new MockNavigationHost();
        registry.populateHost(host);
        
        assertEquals(2, host.addedPanels.size());
        assertTrue(host.addedPanels.contains(panel1));
        assertFalse(host.addedPanels.contains(panel2));
        assertTrue(host.addedPanels.contains(panel3));
    }
    
    @Test
    public void testPopulateHostWithNullHost() {
        registry.register(panel1);
        registry.register(panel2);
        
        // Should not throw
        registry.populateHost(null);
    }
    
    @Test
    public void testPopulateHostWithoutAnyPanels() {
        MockNavigationHost host = new MockNavigationHost();
        
        // Should not throw
        registry.populateHost(host);
        
        assertEquals(0, host.addedPanels.size());
    }
    
    @Test
    public void testPopulateHostAllHiddenPanels() {
        panel1.setShouldDisplay(false);
        panel2.setShouldDisplay(false);
        panel3.setShouldDisplay(false);
        
        registry.register(panel1);
        registry.register(panel2);
        registry.register(panel3);
        
        MockNavigationHost host = new MockNavigationHost();
        registry.populateHost(host);
        
        assertEquals(0, host.addedPanels.size());
    }
    
    // ===== Integration Tests =====
    
    @Test
    public void testCompleteLifecycle() {
        // Register panels
        registry.register(panel1);
        registry.register(panel2);
        
        // Load configuration
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        registry.loadAll(packageJSON, jdeploy);
        
        assertTrue(panel1.wasLoadCalled);
        assertTrue(panel2.wasLoadCalled);
        
        // Attach listeners
        AtomicInteger changeCount = new AtomicInteger(0);
        registry.attachChangeListeners(changeCount::incrementAndGet);
        
        // Simulate user modifications
        panel1.triggerChangeListener();
        panel1.triggerChangeListener();
        panel2.triggerChangeListener();
        
        assertEquals(3, changeCount.get());
        
        // Save configuration
        registry.saveAll(packageJSON, jdeploy);
        
        assertTrue(panel1.wasSaveCalled);
        assertTrue(panel2.wasSaveCalled);
    }
    
    @Test
    public void testGetPanelsReturnsDefensiveCopy() {
        registry.register(panel1);
        registry.register(panel2);
        
        List<NavigablePanel> panelsCopy1 = registry.getPanels();
        List<NavigablePanel> panelsCopy2 = registry.getPanels();
        
        // Copies should be different objects
        assertNotSame(panelsCopy1, panelsCopy2);
        
        // But contain the same panels
        assertEquals(panelsCopy1, panelsCopy2);
        
        // Modifying a copy should not affect the registry
        panelsCopy1.remove(0);
        assertEquals(2, registry.getPanelCount());
    }
    
    // ===== Mock Classes =====
    
    /**
     * Mock implementation of NavigablePanel for testing.
     */
    private static class MockNavigablePanel implements NavigablePanel {
        private final String title;
        private final JComponent root = new JPanel();
        private boolean shouldDisplay = true;
        
        boolean wasLoadCalled = false;
        JSONObject lastLoadPackageJSON = null;
        JSONObject lastLoadJdeploy = null;
        
        boolean wasSaveCalled = false;
        JSONObject lastSavePackageJSON = null;
        JSONObject lastSaveJdeploy = null;
        
        boolean changeListenerAttached = false;
        private ActionListener changeListener = null;
        
        MockNavigablePanel(String title) {
            this.title = title;
        }
        
        @Override
        public String getTitle() {
            return title;
        }
        
        @Override
        public String getHelpUrl() {
            return null;
        }
        
        @Override
        public JComponent getRoot() {
            return root;
        }
        
        @Override
        public void load(JSONObject packageJSON, JSONObject jdeploy) {
            wasLoadCalled = true;
            lastLoadPackageJSON = packageJSON;
            lastLoadJdeploy = jdeploy;
        }
        
        @Override
        public void save(JSONObject packageJSON, JSONObject jdeploy) {
            wasSaveCalled = true;
            lastSavePackageJSON = packageJSON;
            lastSaveJdeploy = jdeploy;
        }
        
        @Override
        public void addChangeListener(ActionListener listener) {
            changeListenerAttached = true;
            this.changeListener = listener;
        }
        
        @Override
        public boolean shouldDisplay() {
            return shouldDisplay;
        }
        
        void setShouldDisplay(boolean display) {
            this.shouldDisplay = display;
        }
        
        void triggerChangeListener() {
            if (changeListener != null) {
                changeListener.actionPerformed(null);
            }
        }
    }
    
    /**
     * Mock implementation of NavigationHost for testing.
     */
    private static class MockNavigationHost implements NavigationHost {
        List<NavigablePanel> addedPanels = new ArrayList<>();
        
        @Override
        public void addPanel(NavigablePanel panel) {
            if (panel != null) {
                addedPanels.add(panel);
            }
        }
        
        @Override
        public void addPanel(NavigablePanel panel, Runnable onSelected) {
            addPanel(panel);
        }
        
        @Override
        public JComponent getComponent() {
            return new JPanel();
        }
    }
}
