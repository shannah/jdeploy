package ca.weblite.jdeploy.gui.navigation;

import ca.weblite.jdeploy.gui.JDeployProjectEditorContext;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TabbedPaneNavigationHost}.
 */
public class TabbedPaneNavigationHostTest {
    
    private TabbedPaneNavigationHost host;
    private JDeployProjectEditorContext context;
    private JFrame parentFrame;
    
    @BeforeEach
    public void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI test in headless environment");
        context = mock(JDeployProjectEditorContext.class);
        parentFrame = new JFrame();
        host = new TabbedPaneNavigationHost(context, parentFrame);
    }
    
    @Test
    public void testGetComponentReturnsTabbedPane() {
        JComponent component = host.getComponent();
        assertNotNull(component);
        assertInstanceOf(JTabbedPane.class, component);
    }
    
    @Test
    public void testAddPanelWithoutHelpUrl() {
        NavigablePanel panel = createMockPanel("Test Panel", null, true);
        
        host.addPanel(panel);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        assertEquals(1, tabbedPane.getTabCount());
        assertEquals("Test Panel", tabbedPane.getTitleAt(0));
    }
    
    @Test
    public void testAddPanelWithHelpUrl() {
        NavigablePanel panel = createMockPanel("Help Panel", "http://example.com/help", true);
        
        host.addPanel(panel);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        assertEquals(1, tabbedPane.getTabCount());
        assertEquals("Help Panel", tabbedPane.getTitleAt(0));
        
        // Verify wrapper panel structure
        Component tabComponent = tabbedPane.getComponentAt(0);
        assertInstanceOf(JPanel.class, tabComponent);
        JPanel wrapperPanel = (JPanel) tabComponent;
        assertEquals(BorderLayout.class, wrapperPanel.getLayout().getClass());
    }
    
    @Test
    public void testAddPanelWithNullPanel() {
        host.addPanel(null);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        assertEquals(0, tabbedPane.getTabCount());
    }
    
    @Test
    public void testAddPanelWhenShouldNotDisplay() {
        NavigablePanel panel = createMockPanel("Hidden Panel", null, false);
        
        host.addPanel(panel);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        assertEquals(0, tabbedPane.getTabCount());
    }
    
    @Test
    public void testAddMultiplePanels() {
        NavigablePanel panel1 = createMockPanel("Panel 1", null, true);
        NavigablePanel panel2 = createMockPanel("Panel 2", "http://example.com/help2", true);
        NavigablePanel panel3 = createMockPanel("Panel 3", null, true);
        
        host.addPanel(panel1);
        host.addPanel(panel2);
        host.addPanel(panel3);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        assertEquals(3, tabbedPane.getTabCount());
        assertEquals("Panel 1", tabbedPane.getTitleAt(0));
        assertEquals("Panel 2", tabbedPane.getTitleAt(1));
        assertEquals("Panel 3", tabbedPane.getTitleAt(2));
    }
    
    @Test
    public void testAddPanelWithSelectionCallback() {
        NavigablePanel panel = createMockPanel("Callback Panel", null, true);
        
        boolean[] callbackInvoked = {false};
        Runnable callback = () -> {
            callbackInvoked[0] = true;
        };
        
        host.addPanel(panel, callback);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        assertEquals(1, tabbedPane.getTabCount());
        
        // Select the tab to trigger the callback
        tabbedPane.setSelectedIndex(0);
        
        assertTrue(callbackInvoked[0], "Callback should have been invoked");
    }
    
    @Test
    public void testMultiplePanelsWithSelectiveCallbacks() {
        NavigablePanel panel1 = createMockPanel("Panel 1", null, true);
        NavigablePanel panel2 = createMockPanel("Panel 2", null, true);
        NavigablePanel panel3 = createMockPanel("Panel 3", null, true);
        
        boolean[] callback1Invoked = {false};
        boolean[] callback2Invoked = {false};
        boolean[] callback3Invoked = {false};
        
        host.addPanel(panel1, () -> callback1Invoked[0] = true);
        host.addPanel(panel2); // No callback
        host.addPanel(panel3, () -> callback3Invoked[0] = true);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        
        // Select panel 1
        tabbedPane.setSelectedIndex(0);
        assertTrue(callback1Invoked[0]);
        
        // Select panel 2 (no callback, should not fail)
        tabbedPane.setSelectedIndex(1);
        assertFalse(callback2Invoked[0]);
        
        // Select panel 3
        tabbedPane.setSelectedIndex(2);
        assertTrue(callback3Invoked[0]);
    }
    
    @Test
    public void testWrapperPanelContainsRootComponent() {
        JComponent rootComponent = new JLabel("Test Content");
        NavigablePanel panel = createMockPanelWithRoot("Panel", null, true, rootComponent);
        
        host.addPanel(panel);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        Component tabComponent = tabbedPane.getComponentAt(0);
        
        assertInstanceOf(JPanel.class, tabComponent);
        JPanel wrapperPanel = (JPanel) tabComponent;
        
        // Find the root component in the wrapper
        Component centerComponent = ((BorderLayout) wrapperPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        assertSame(rootComponent, centerComponent);
    }
    
    @Test
    public void testAddPanelWithEmptyHelpUrl() {
        NavigablePanel panel = createMockPanel("Empty Help Panel", "", true);
        
        host.addPanel(panel);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        assertEquals(1, tabbedPane.getTabCount());
        
        // Wrapper should be added without help button since helpUrl is empty
        Component tabComponent = tabbedPane.getComponentAt(0);
        assertInstanceOf(JPanel.class, tabComponent);
    }
    
    @Test
    public void testCallbackInvokedOnTabSelection() {
        NavigablePanel panel = createMockPanel("Selectable Panel", null, true);
        
        int[] callbackCount = {0};
        Runnable callback = () -> callbackCount[0]++;
        
        host.addPanel(panel, callback);
        
        JTabbedPane tabbedPane = (JTabbedPane) host.getComponent();
        
        // Initial selection - callback fires via ChangeListener when tab is added and selected
        // The count may already be 1 from the initial tab addition triggering ChangeListener
        int initialCount = callbackCount[0];
        
        // Selecting a different tab then back should invoke callback
        // Since we only have one tab, add another to test selection changes
        NavigablePanel panel2 = createMockPanel("Panel 2", null, true);
        host.addPanel(panel2);
        
        // Select second tab
        tabbedPane.setSelectedIndex(1);
        
        // Select first tab again - should invoke callback
        tabbedPane.setSelectedIndex(0);
        assertTrue(callbackCount[0] > initialCount, "Callback should have been invoked on tab selection");
    }
    
    // Helper methods
    
    private NavigablePanel createMockPanel(String title, String helpUrl, boolean shouldDisplay) {
        NavigablePanel panel = mock(NavigablePanel.class);
        when(panel.getTitle()).thenReturn(title);
        when(panel.getHelpUrl()).thenReturn(helpUrl);
        when(panel.getRoot()).thenReturn(new JPanel());
        when(panel.shouldDisplay()).thenReturn(shouldDisplay);
        return panel;
    }
    
    private NavigablePanel createMockPanelWithRoot(String title, String helpUrl, boolean shouldDisplay, JComponent root) {
        NavigablePanel panel = mock(NavigablePanel.class);
        when(panel.getTitle()).thenReturn(title);
        when(panel.getHelpUrl()).thenReturn(helpUrl);
        when(panel.getRoot()).thenReturn(root);
        when(panel.shouldDisplay()).thenReturn(shouldDisplay);
        return panel;
    }
}
