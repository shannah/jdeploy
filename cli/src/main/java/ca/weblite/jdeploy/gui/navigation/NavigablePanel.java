package ca.weblite.jdeploy.gui.navigation;

import javax.swing.JComponent;
import java.awt.event.ActionListener;
import org.json.JSONObject;

/**
 * Interface for panels that can be hosted in tabbed or side-navigation UIs.
 * 
 * <p>This interface abstracts the common behavior of configuration panels in the jDeploy project editor,
 * allowing panels to be displayed in different navigation contexts (tabbed pane, side panel, wizard, etc.)
 * without the navigation host needing to know the specific panel implementation details.</p>
 * 
 * <p>Implementing panels should:</p>
 * <ul>
 *   <li>Load configuration from package.json and jdeploy objects via {@link #load(JSONObject, JSONObject)}</li>
 *   <li>Save configuration to package.json and jdeploy objects via {@link #save(JSONObject, JSONObject)}</li>
 *   <li>Notify listeners of changes via {@link #addChangeListener(ActionListener)}</li>
 *   <li>Provide a title and optional help URL for display in the navigation UI</li>
 *   <li>Indicate whether the panel should be displayed via {@link #shouldDisplay()}</li>
 * </ul>
 * 
 * <p>The interface supports both simple load/save patterns (single JSONObject parameter) and more complex
 * patterns (separate packageJSON and jdeploy parameters) by requiring both parameters and letting
 * implementations decide which to use based on their configuration schema.</p>
 */
public interface NavigablePanel {
    
    /**
     * Gets the title of this panel for display in the navigation UI.
     * 
     * @return the panel title, never null
     */
    String getTitle();
    
    /**
     * Gets the help URL for this panel, if available.
     * 
     * <p>This URL may be displayed as a help button or link in the navigation UI.
     * Implementations should return null if no help URL is available.</p>
     * 
     * @return the help URL, or null if no help is available
     */
    String getHelpUrl();
    
    /**
     * Gets the root component for this panel.
     * 
     * <p>This component will be embedded in the navigation UI (tabbed pane, side panel, etc.).
     * Implementations should return a fully initialized and configured JComponent.</p>
     * 
     * @return the root component, never null
     */
    JComponent getRoot();
    
    /**
     * Loads configuration from the package.json and jdeploy objects.
     * 
     * <p>Implementations should populate their UI components with values from these JSONObjects.
     * Both parameters may be null, and implementations should handle this gracefully.</p>
     * 
     * @param packageJSON the root package.json object, may be null
     * @param jdeploy the jdeploy configuration object, may be null
     */
    void load(JSONObject packageJSON, JSONObject jdeploy);
    
    /**
     * Saves configuration to the package.json and jdeploy objects.
     * 
     * <p>Implementations should extract values from their UI components and update the provided
     * JSONObjects. Both parameters may be null, and implementations should handle this gracefully.
     * Implementations should remove fields that are empty or contain only default values.</p>
     * 
     * @param packageJSON the root package.json object to update, may be null
     * @param jdeploy the jdeploy configuration object to update, may be null
     */
    void save(JSONObject packageJSON, JSONObject jdeploy);
    
    /**
     * Registers a listener to be notified when the user modifies panel contents.
     * 
     * <p>The provided ActionListener should be invoked whenever a modification occurs that
     * would require saving the configuration. The navigation UI may use this to display
     * unsaved changes indicators or enable/disable save buttons.</p>
     * 
     * @param listener the listener to register, may be null
     */
    void addChangeListener(ActionListener listener);
    
    /**
     * Determines whether this panel should be displayed in the navigation UI.
     * 
     * <p>This allows panels to conditionally hide themselves based on the current project
     * configuration (e.g., a panel might only be relevant if certain features are enabled).
     * The default implementation returns true.</p>
     * 
     * @return true if this panel should be displayed, false otherwise
     */
    default boolean shouldDisplay() {
        return true;
    }
    
    /**
     * Gets an optional callback to be invoked when this panel is selected in the navigation UI.
     * 
     * <p>This allows panels to perform initialization or refresh operations when the user
     * selects them in a tabbed interface or similar navigation control.
     * The default implementation returns null (no callback).</p>
     * 
     * @return the selection callback, or null if no callback is needed
     */
    default Runnable getOnSelected() {
        return null;
    }
}
