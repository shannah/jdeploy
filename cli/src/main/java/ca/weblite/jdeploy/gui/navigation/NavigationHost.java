package ca.weblite.jdeploy.gui.navigation;

import javax.swing.JComponent;

/**
 * Interface for navigation UI containers that host {@link NavigablePanel}s.
 * 
 * <p>This interface abstracts the navigation UI implementation, allowing different
 * navigation strategies to be swapped without changing the code that adds panels.
 * Current implementations include tabbed pane navigation, but the abstraction supports
 * future implementations such as side navigation, wizard-style navigation, or other
 * multi-panel UIs.</p>
 * 
 * <p>The NavigationHost is responsible for:</p>
 * <ul>
 *   <li>Managing a collection of {@link NavigablePanel}s</li>
 *   <li>Displaying panels in an appropriate UI component (tabs, sidebar, etc.)</li>
 *   <li>Providing the root Swing component for embedding in parent UIs</li>
 *   <li>Invoking selection callbacks when panels are selected by the user</li>
 * </ul>
 * 
 * <p><b>Usage example:</b></p>
 * <pre>
 * NavigationHost host = new TabbedPaneNavigationHost();
 * host.addPanel(detailsPanel);
 * host.addPanel(publishPanel, () -> System.out.println("Publish tab selected"));
 * parentComponent.add(host.getComponent());
 * </pre>
 * 
 * <p>Implementations should handle {@code null} panel parameters gracefully and may
 * apply filtering based on panel {@link NavigablePanel#shouldDisplay()} to control
 * visibility in the UI.</p>
 */
public interface NavigationHost {
    
    /**
     * Adds a panel to this navigation host.
     * 
     * <p>The panel will be displayed in the navigation UI according to the implementation
     * strategy (as a tab, sidebar entry, etc.). If the panel's {@link NavigablePanel#shouldDisplay()}
     * returns false, the implementation may choose to hide or exclude the panel.</p>
     * 
     * @param panel the panel to add, typically not null
     */
    void addPanel(NavigablePanel panel);
    
    /**
     * Adds a panel to this navigation host with a callback for when the panel is selected.
     * 
     * <p>The provided {@code onSelected} callback will be invoked when the user selects
     * this panel in the navigation UI. This allows callers to react to panel selection
     * (e.g., to refresh data, validate input, or trigger side effects).</p>
     * 
     * <p>If the panel's {@link NavigablePanel#shouldDisplay()} returns false, the implementation
     * may choose to hide or exclude the panel, and the callback may never be invoked.</p>
     * 
     * @param panel the panel to add, typically not null
     * @param onSelected the callback to invoke when the panel is selected, may be null
     */
    void addPanel(NavigablePanel panel, Runnable onSelected);
    
    /**
     * Gets the root Swing component for this navigation host.
     * 
     * <p>This component encapsulates the entire navigation UI and all hosted panels.
     * It can be embedded in parent UIs (e.g., a dialog, frame, or larger panel).
     * The component is typically immutable and can be accessed multiple times.</p>
     * 
     * @return the root component, never null
     */
    JComponent getComponent();
}
