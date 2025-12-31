package ca.weblite.jdeploy.gui.navigation;

import org.json.JSONObject;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry for managing {@link NavigablePanel}s in the jDeploy project editor.
 * 
 * <p>This class provides a central point for managing editor panels, supporting:
 * <ul>
 *   <li>Panel registration and storage</li>
 *   <li>Bulk loading of configuration from package.json and jdeploy objects</li>
 *   <li>Bulk saving of configuration to package.json and jdeploy objects</li>
 *   <li>Centralized change listener attachment across all panels</li>
 *   <li>Population of navigation hosts with all displayable panels</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>
 * EditorPanelRegistry registry = new EditorPanelRegistry();
 * registry.register(detailsPanel);
 * registry.register(publishPanel);
 * registry.loadAll(packageJSON, jdeployConfig);
 * registry.attachChangeListeners(() -> System.out.println("Modified!"));
 * registry.populateHost(navigationHost);
 * </pre>
 */
public class EditorPanelRegistry {
    
    private final List<NavigablePanel> panels;
    
    /**
     * Creates a new EditorPanelRegistry with an empty panel list.
     */
    public EditorPanelRegistry() {
        this.panels = new ArrayList<>();
    }
    
    /**
     * Registers a panel with this registry.
     * 
     * <p>The panel will be stored and subsequently included in load/save/listener operations.
     * Null panels are silently ignored.</p>
     * 
     * @param panel the panel to register, may be null
     */
    public void register(NavigablePanel panel) {
        if (panel != null) {
            panels.add(panel);
        }
    }
    
    /**
     * Loads configuration from the provided JSONObjects into all registered panels.
     * 
     * <p>Each panel's {@link NavigablePanel#load(JSONObject, JSONObject)} method is called
     * with the provided objects. Panels should handle null parameters gracefully.</p>
     * 
     * @param packageJSON the package.json root object, may be null
     * @param jdeploy the jdeploy configuration object, may be null
     */
    public void loadAll(JSONObject packageJSON, JSONObject jdeploy) {
        for (NavigablePanel panel : panels) {
            panel.load(packageJSON, jdeploy);
        }
    }
    
    /**
     * Loads configuration from the provided packageJSON object into all registered panels.
     * 
     * <p>This is a convenience method that calls {@link #loadAll(JSONObject, JSONObject)}
     * with null jdeploy parameter.</p>
     * 
     * @param packageJSON the package.json root object, may be null
     */
    public void loadAll(JSONObject packageJSON) {
        loadAll(packageJSON, null);
    }
    
    /**
     * Saves configuration from all registered panels to the provided JSONObjects.
     * 
     * <p>Each panel's {@link NavigablePanel#save(JSONObject, JSONObject)} method is called
     * with the provided objects. Panels should handle null parameters gracefully.</p>
     * 
     * @param packageJSON the package.json root object to update, may be null
     * @param jdeploy the jdeploy configuration object to update, may be null
     */
    public void saveAll(JSONObject packageJSON, JSONObject jdeploy) {
        for (NavigablePanel panel : panels) {
            panel.save(packageJSON, jdeploy);
        }
    }
    
    /**
     * Saves configuration from all registered panels to the provided packageJSON object.
     * 
     * <p>This is a convenience method that calls {@link #saveAll(JSONObject, JSONObject)}
     * with null jdeploy parameter.</p>
     * 
     * @param packageJSON the package.json root object to update, may be null
     */
    public void saveAll(JSONObject packageJSON) {
        saveAll(packageJSON, null);
    }
    
    /**
     * Attaches a change listener callback to all registered panels.
     * 
     * <p>The provided callback will be invoked whenever any panel reports a modification.
     * This is useful for enabling/disabling save buttons or displaying unsaved changes indicators.
     * If onModified is null, this method does nothing.</p>
     * 
     * @param onModified the callback to invoke on panel modifications, may be null
     */
    public void attachChangeListeners(Runnable onModified) {
        if (onModified == null) {
            return;
        }
        
        ActionListener actionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onModified.run();
            }
        };
        
        for (NavigablePanel panel : panels) {
            panel.addChangeListener(actionListener);
        }
    }
    
    /**
     * Populates the provided navigation host with all displayable panels from this registry.
     * 
     * <p>Only panels where {@link NavigablePanel#shouldDisplay()} returns true will be added.
     * If host is null, this method does nothing.</p>
     * 
     * @param host the navigation host to populate, may be null
     */
    public void populateHost(NavigationHost host) {
        if (host == null) {
            return;
        }
        
        for (NavigablePanel panel : panels) {
            if (panel.shouldDisplay()) {
                host.addPanel(panel);
            }
        }
    }
    
    /**
     * Gets the number of registered panels.
     * 
     * @return the number of panels in this registry
     */
    public int getPanelCount() {
        return panels.size();
    }
    
    /**
     * Gets a copy of the registered panels list.
     * 
     * @return a list copy of all registered panels
     */
    public List<NavigablePanel> getPanels() {
        return new ArrayList<>(panels);
    }
}
