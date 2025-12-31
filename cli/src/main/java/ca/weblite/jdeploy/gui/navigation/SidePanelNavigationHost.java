package ca.weblite.jdeploy.gui.navigation;

import ca.weblite.jdeploy.gui.JDeployProjectEditorContext;
import ca.weblite.jdeploy.gui.MenuBarBuilder;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * A NavigationHost implementation that uses a side panel with a list on the left
 * and card layout on the right, similar to IntelliJ's settings dialog.
 */
public class SidePanelNavigationHost implements NavigationHost {
    
    private final JDeployProjectEditorContext context;
    private final JFrame parentFrame;
    private final JSplitPane splitPane;
    private final JList<String> panelList;
    private final DefaultListModel<String> listModel;
    private final JPanel cardPanel;
    private final CardLayout cardLayout;
    private final Map<String, Runnable> onSelectedCallbacks;
    private boolean isUpdatingSelection = false;

    public SidePanelNavigationHost(JDeployProjectEditorContext context, JFrame parentFrame) {
        this.context = context;
        this.parentFrame = parentFrame;
        this.onSelectedCallbacks = new HashMap<>();
        
        // Create list model and list
        this.listModel = new DefaultListModel<>();
        this.panelList = new JList<>(listModel);
        this.panelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.panelList.setCellRenderer(new IntelliJStyleListCellRenderer());
        
        // Create card layout panel
        this.cardLayout = new CardLayout();
        this.cardPanel = new JPanel(cardLayout);
        
        // Create split pane
        JScrollPane listScroller = new JScrollPane(panelList);
        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroller, cardPanel);
        this.splitPane.setDividerLocation(200);
        this.splitPane.setResizeWeight(0.0);
        
        // Add selection listener to list
        panelList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!isUpdatingSelection && !e.getValueIsAdjusting()) {
                    handlePanelSelection();
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
        // Skip if panel is null or should not be displayed
        if (panel == null || !panel.shouldDisplay()) {
            return;
        }
        
        String title = panel.getTitle();
        
        // Create wrapper panel with BorderLayout
        JPanel wrapper = new JPanel(new BorderLayout());
        
        // Add help button row if help URL is available
        String helpUrl = panel.getHelpUrl();
        if (helpUrl != null && !helpUrl.isEmpty()) {
            JPanel helpButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton helpButton = MenuBarBuilder.createHelpButton(
                    helpUrl,
                    "Help",
                    "View help for this panel",
                    context,
                    parentFrame
            );
            helpButtonRow.add(helpButton);
            wrapper.add(helpButtonRow, BorderLayout.NORTH);
        }
        
        // Add panel content at CENTER
        wrapper.add(panel.getRoot(), BorderLayout.CENTER);
        
        // Add wrapper to card layout using title as card name
        cardPanel.add(wrapper, title);
        
        // Add title to list model
        listModel.addElement(title);
        
        // Store onSelected callback if provided
        if (onSelected != null) {
            onSelectedCallbacks.put(title, onSelected);
        }
        
        // Select first panel by default
        if (listModel.getSize() == 1) {
            panelList.setSelectedIndex(0);
        }
    }

    @Override
    public JComponent getComponent() {
        return splitPane;
    }

    /**
     * Handle when a panel is selected in the list.
     */
    private void handlePanelSelection() {
        int selectedIndex = panelList.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < listModel.getSize()) {
            String selectedTitle = listModel.getElementAt(selectedIndex);
            
            // Show the corresponding card
            cardLayout.show(cardPanel, selectedTitle);
            
            // Invoke the onSelected callback if present
            Runnable callback = onSelectedCallbacks.get(selectedTitle);
            if (callback != null) {
                callback.run();
            }
        }
    }

    /**
     * Custom cell renderer to style the list like IntelliJ's settings sidebar.
     */
    private static class IntelliJStyleListCellRenderer extends DefaultListCellRenderer {
        private static final Font REGULAR_FONT = new Font("Dialog", Font.PLAIN, 12);
        private static final Font BOLD_FONT = new Font("Dialog", Font.BOLD, 12);

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            Component component = super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
            );
            
            if (component instanceof JLabel) {
                JLabel label = (JLabel) component;
                // Bold the selected item
                if (isSelected) {
                    label.setFont(BOLD_FONT);
                } else {
                    label.setFont(REGULAR_FONT);
                }
                // Add some padding
                label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            }
            
            return component;
        }
    }
}
