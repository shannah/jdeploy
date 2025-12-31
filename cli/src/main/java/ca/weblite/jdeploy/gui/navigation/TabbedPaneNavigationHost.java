package ca.weblite.jdeploy.gui.navigation;

import ca.weblite.jdeploy.gui.JDeployProjectEditorContext;
import ca.weblite.jdeploy.gui.MenuBarBuilder;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link NavigationHost} using a {@link JTabbedPane} for navigation.
 * 
 * <p>This implementation displays panels as tabs in a tabbed pane. Each panel may have
 * an associated help button that is displayed in a wrapper panel above the panel's content.</p>
 */
public class TabbedPaneNavigationHost implements NavigationHost {
    
    private final SelectionTrackingTabbedPane tabbedPane;
    private final JDeployProjectEditorContext context;
    private final JFrame parentFrame;
    private final Map<Integer, Runnable> selectionCallbacks;
    private int panelCount = 0;
    
    /**
     * Creates a new TabbedPaneNavigationHost.
     * 
     * @param context the editor context, used for help button creation
     * @param parentFrame the parent frame, used for help button creation
     */
    public TabbedPaneNavigationHost(JDeployProjectEditorContext context, JFrame parentFrame) {
        this.context = context;
        this.parentFrame = parentFrame;
        this.tabbedPane = new SelectionTrackingTabbedPane();
        this.selectionCallbacks = new HashMap<>();
        
        // Add listener to invoke callbacks when tabs are selected
        tabbedPane.addSelectionCallback(selectedIndex -> {
            if (selectedIndex >= 0 && selectionCallbacks.containsKey(selectedIndex)) {
                Runnable callback = selectionCallbacks.get(selectedIndex);
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }
    
    @Override
    public void addPanel(NavigablePanel panel) {
        addPanel(panel, null);
    }
    
    @Override
    public void addPanel(NavigablePanel panel, Runnable onSelected) {
        if (panel == null) {
            return;
        }
        
        if (!panel.shouldDisplay()) {
            return;
        }
        
        // Create wrapper panel with optional help button
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        
        String helpUrl = panel.getHelpUrl();
        if (helpUrl != null && !helpUrl.isEmpty()) {
            // Create help button panel
            JPanel helpButtonPanel = new JPanel();
            helpButtonPanel.setLayout(new javax.swing.BoxLayout(helpButtonPanel, javax.swing.BoxLayout.X_AXIS));
            
            JPanel spacer = new JPanel();
            helpButtonPanel.add(spacer);
            
            javax.swing.JButton helpButton = MenuBarBuilder.createHelpButton(
                    helpUrl,
                    "Help",
                    "Click for help about this panel",
                    context,
                    parentFrame
            );
            helpButtonPanel.add(helpButton);
            
            wrapperPanel.add(helpButtonPanel, BorderLayout.NORTH);
        }
        
        // Add panel's root component
        wrapperPanel.add(panel.getRoot(), BorderLayout.CENTER);
        
        // Add to tabbed pane
        int tabIndex = panelCount;
        tabbedPane.addTab(panel.getTitle(), wrapperPanel);
        
        // If this is the first panel, it will be auto-selected
        boolean isFirstPanel = panelCount == 0;
        panelCount++;
        
        // Register selection callback if provided
        if (onSelected != null) {
            selectionCallbacks.put(tabIndex, onSelected);
        }
    }
    
    @Override
    public JComponent getComponent() {
        return tabbedPane;
    }
    
    /**
     * Custom JTabbedPane that tracks all selection attempts, including when the same tab is selected again.
     */
    private static class SelectionTrackingTabbedPane extends JTabbedPane {
        private SelectionCallback selectionCallback;
        
        interface SelectionCallback {
            void onSelection(int index);
        }
        
        void addSelectionCallback(SelectionCallback callback) {
            this.selectionCallback = callback;
            // Also listen for programmatic changes
            addChangeListener(evt -> {
                if (selectionCallback != null) {
                    selectionCallback.onSelection(getSelectedIndex());
                }
            });
        }
        
        @Override
        public void setSelectedIndex(int index) {
            super.setSelectedIndex(index);
            // Fire callback for every setSelectedIndex call, even if index hasn't changed
            if (selectionCallback != null && index >= 0) {
                selectionCallback.onSelection(index);
            }
        }
    }
}
