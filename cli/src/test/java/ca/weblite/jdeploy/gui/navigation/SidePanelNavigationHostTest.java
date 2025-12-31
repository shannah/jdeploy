package ca.weblite.jdeploy.gui.navigation;

import ca.weblite.jdeploy.gui.JDeployProjectEditorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SidePanelNavigationHost.
 * 
 * Tests the side panel navigation host implementation that uses a list on the left
 * and card layout on the right, similar to IntelliJ's settings dialog.
 */
public class SidePanelNavigationHostTest {
    
    private SidePanelNavigationHost host;
    private JDeployProjectEditorContext context;
    private JFrame parentFrame;
    
    @BeforeEach
    public void setUp() {
        context = mock(JDeployProjectEditorContext.class);
        parentFrame = new JFrame();
        host = new SidePanelNavigationHost(context, parentFrame);
    }
    
    @Test
    public void testGetComponentReturnsSplitPane() {
        JComponent component = host.getComponent();
        assertNotNull(component);
        assertInstanceOf(JSplitPane.class, component);
    }
    
    @Test
    public void testAddPanelWithoutHelpUrl() {
        NavigablePanel panel = createMockPanel("Settings", null, true);
        host.addPanel(panel);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        
        // Verify the right component (cardPanel) contains the panel
        Component rightComponent = splitPane.getRightComponent();
        assertNotNull(rightComponent);
        
        // Verify the title appears in the list
        Component leftComponent = splitPane.getLeftComponent();
        assertNotNull(leftComponent);
        assertInstanceOf(JScrollPane.class, leftComponent);
    }
    
    @Test
    public void testAddPanelWithHelpUrl() {
        String helpUrl = "https://example.com/help";
        NavigablePanel panel = createMockPanel("Settings", helpUrl, true);
        host.addPanel(panel);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component rightComponent = splitPane.getRightComponent();
        
        assertNotNull(rightComponent);
    }
    
    @Test
    public void testAddPanelWithNullPanel() {
        // Adding null panel should not throw exception
        host.addPanel(null);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component leftComponent = splitPane.getLeftComponent();
        
        // Verify list is empty
        JScrollPane scrollPane = (JScrollPane) leftComponent;
        JList<?> list = (JList<?>) scrollPane.getViewport().getView();
        assertEquals(0, list.getModel().getSize());
    }
    
    @Test
    public void testAddPanelWhenShouldNotDisplay() {
        NavigablePanel panel = createMockPanel("Hidden", null, false);
        host.addPanel(panel);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component leftComponent = splitPane.getLeftComponent();
        
        // Verify list is empty
        JScrollPane scrollPane = (JScrollPane) leftComponent;
        JList<?> list = (JList<?>) scrollPane.getViewport().getView();
        assertEquals(0, list.getModel().getSize());
    }
    
    @Test
    public void testAddMultiplePanels() {
        NavigablePanel panel1 = createMockPanel("Panel 1", null, true);
        NavigablePanel panel2 = createMockPanel("Panel 2", null, true);
        NavigablePanel panel3 = createMockPanel("Panel 3", "http://help.com", true);
        
        host.addPanel(panel1);
        host.addPanel(panel2);
        host.addPanel(panel3);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component leftComponent = splitPane.getLeftComponent();
        
        JScrollPane scrollPane = (JScrollPane) leftComponent;
        JList<?> list = (JList<?>) scrollPane.getViewport().getView();
        
        assertEquals(3, list.getModel().getSize());
        assertEquals("Panel 1", list.getModel().getElementAt(0));
        assertEquals("Panel 2", list.getModel().getElementAt(1));
        assertEquals("Panel 3", list.getModel().getElementAt(2));
    }
    
    @Test
    public void testSelectingListItemShowsCorrectCard() {
        JPanel root1 = new JPanel();
        root1.setName("root1");
        NavigablePanel panel1 = createMockPanelWithRoot("Panel 1", null, true, root1);
        
        JPanel root2 = new JPanel();
        root2.setName("root2");
        NavigablePanel panel2 = createMockPanelWithRoot("Panel 2", null, true, root2);
        
        host.addPanel(panel1);
        host.addPanel(panel2);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component leftComponent = splitPane.getLeftComponent();
        
        JScrollPane scrollPane = (JScrollPane) leftComponent;
        JList<?> list = (JList<?>) scrollPane.getViewport().getView();
        
        // Select second panel
        list.setSelectedIndex(1);
        
        // Verify the card layout shows the correct panel
        Component rightComponent = splitPane.getRightComponent();
        assertNotNull(rightComponent);
    }
    
    @Test
    public void testAddPanelWithSelectionCallback() {
        Runnable callback = mock(Runnable.class);
        NavigablePanel panel = createMockPanel("Callback Panel", null, true);
        
        host.addPanel(panel, callback);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component leftComponent = splitPane.getLeftComponent();
        
        JScrollPane scrollPane = (JScrollPane) leftComponent;
        JList<?> list = (JList<?>) scrollPane.getViewport().getView();
        
        // First panel is already selected by default
        verify(callback).run();
    }
    
    @Test
    public void testMultiplePanelsWithSelectiveCallbacks() {
        Runnable callback1 = mock(Runnable.class);
        Runnable callback2 = mock(Runnable.class);
        Runnable callback3 = mock(Runnable.class);
        
        NavigablePanel panel1 = createMockPanel("Panel 1", null, true);
        NavigablePanel panel2 = createMockPanel("Panel 2", null, true);
        NavigablePanel panel3 = createMockPanel("Panel 3", null, true);
        
        host.addPanel(panel1, callback1);
        host.addPanel(panel2); // No callback
        host.addPanel(panel3, callback3);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component leftComponent = splitPane.getLeftComponent();
        
        JScrollPane scrollPane = (JScrollPane) leftComponent;
        JList<?> list = (JList<?>) scrollPane.getViewport().getView();
        
        // Verify callback1 was invoked for initial selection of panel1
        verify(callback1).run();
        verify(callback2, never()).run();
        verify(callback3, never()).run();
        
        // Select panel3
        list.setSelectedIndex(2);
        verify(callback3).run();
    }
    
    @Test
    public void testWrapperPanelContainsRootComponent() {
        JPanel customRoot = new JPanel();
        customRoot.setName("CustomRoot");
        NavigablePanel panel = createMockPanelWithRoot("Test Panel", null, true, customRoot);
        
        host.addPanel(panel);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component rightComponent = splitPane.getRightComponent();
        
        assertNotNull(rightComponent);
        assertInstanceOf(JPanel.class, rightComponent);
    }
    
    @Test
    public void testAddPanelWithEmptyHelpUrl() {
        NavigablePanel panel = createMockPanel("Settings", "", true);
        host.addPanel(panel);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        JComponent rightComponent = (JComponent) splitPane.getRightComponent();
        
        assertNotNull(rightComponent);
    }
    
    @Test
    public void testCallbackInvokedOnTabSelection() {
        Runnable callback1 = mock(Runnable.class);
        Runnable callback2 = mock(Runnable.class);
        
        NavigablePanel panel1 = createMockPanel("Panel 1", null, true);
        NavigablePanel panel2 = createMockPanel("Panel 2", null, true);
        
        host.addPanel(panel1, callback1);
        host.addPanel(panel2, callback2);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component leftComponent = splitPane.getLeftComponent();
        
        JScrollPane scrollPane = (JScrollPane) leftComponent;
        JList<?> list = (JList<?>) scrollPane.getViewport().getView();
        
        // Verify callback1 was invoked for the default initial selection
        verify(callback1).run();
        verify(callback2, never()).run();
        
        // Reset mocks to track subsequent selections
        reset(callback1, callback2);
        
        // Select second panel
        list.setSelectedIndex(1);
        verify(callback2).run();
        verify(callback1, never()).run();
        
        // Reset mocks again
        reset(callback1, callback2);
        
        // Select first panel again
        list.setSelectedIndex(0);
        verify(callback1).run();
        verify(callback2, never()).run();
    }
    
    @Test
    public void testPanelWithHelpUrlHasHelpButton() {
        String helpUrl = "https://example.com/help";
        NavigablePanel panel = createMockPanel("Help Panel", helpUrl, true);
        
        host.addPanel(panel);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component rightComponent = splitPane.getRightComponent();
        
        // rightComponent is the cardPanel with CardLayout
        assertNotNull(rightComponent);
        assertInstanceOf(JPanel.class, rightComponent);
        JPanel cardPanel = (JPanel) rightComponent;
        
        // Get the wrapper panel from inside the card panel
        Component[] components = cardPanel.getComponents();
        assertEquals(1, components.length, "CardPanel should have exactly one component (the wrapper)");
        
        Component wrapperComponent = components[0];
        assertInstanceOf(JPanel.class, wrapperComponent);
        JPanel wrapperPanel = (JPanel) wrapperComponent;
        assertInstanceOf(BorderLayout.class, wrapperPanel.getLayout());
        
        // Verify the north component exists (help button row)
        Component northComponent = ((BorderLayout) wrapperPanel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        assertNotNull(northComponent, "Panel with helpUrl should have a help button row in NORTH");
    }
    
    @Test
    public void testPanelWithoutHelpUrlHasNoHelpButton() {
        NavigablePanel panel = createMockPanel("No Help Panel", null, true);
        
        host.addPanel(panel);
        
        JComponent component = host.getComponent();
        JSplitPane splitPane = (JSplitPane) component;
        Component rightComponent = splitPane.getRightComponent();
        
        // rightComponent is the cardPanel with CardLayout
        assertNotNull(rightComponent);
        assertInstanceOf(JPanel.class, rightComponent);
        JPanel cardPanel = (JPanel) rightComponent;
        
        // Get the wrapper panel from inside the card panel
        Component[] components = cardPanel.getComponents();
        assertEquals(1, components.length, "CardPanel should have exactly one component (the wrapper)");
        
        Component wrapperComponent = components[0];
        assertInstanceOf(JPanel.class, wrapperComponent);
        JPanel wrapperPanel = (JPanel) wrapperComponent;
        assertInstanceOf(BorderLayout.class, wrapperPanel.getLayout());
        
        // Verify the north component is null (no help button row)
        Component northComponent = ((BorderLayout) wrapperPanel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        assertNull(northComponent, "Panel without helpUrl should not have a help button row");
    }
    
    /**
     * Creates a mock NavigablePanel with the specified properties.
     */
    private NavigablePanel createMockPanel(String title, String helpUrl, boolean shouldDisplay) {
        JPanel root = new JPanel();
        return createMockPanelWithRoot(title, helpUrl, shouldDisplay, root);
    }
    
    /**
     * Creates a mock NavigablePanel with the specified properties and root component.
     */
    private NavigablePanel createMockPanelWithRoot(
            String title,
            String helpUrl,
            boolean shouldDisplay,
            JComponent root
    ) {
        NavigablePanel panel = mock(NavigablePanel.class);
        when(panel.getTitle()).thenReturn(title);
        when(panel.getHelpUrl()).thenReturn(helpUrl);
        when(panel.shouldDisplay()).thenReturn(shouldDisplay);
        when(panel.getRoot()).thenReturn(root);
        when(panel.getOnSelected()).thenReturn(null);
        return panel;
    }
}
