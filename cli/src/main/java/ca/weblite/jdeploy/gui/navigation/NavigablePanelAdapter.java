package ca.weblite.jdeploy.gui.navigation;

import javax.swing.Icon;
import javax.swing.JComponent;
import org.json.JSONObject;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Adapter that wraps existing panels to implement the {@link NavigablePanel} interface.
 * 
 * <p>This adapter allows legacy or specialized panels to be integrated into the NavigablePanel
 * ecosystem without modification. It delegates all operations to the wrapped components while
 * providing a consistent interface for the navigation UI.</p>
 * 
 * <p>The adapter supports flexible load/save patterns:</p>
 * <ul>
 *   <li>Load/save to jdeploy object only (for panels focused on jdeploy-specific settings)</li>
 *   <li>Load/save to package.json object only (for panels focused on package.json settings)</li>
 *   <li>Load/save to both (for panels that manage cross-object configuration)</li>
 * </ul>
 */
public class NavigablePanelAdapter implements NavigablePanel {
    
    private final String title;
    private final String helpUrl;
    private final Icon icon;
    private final JComponent root;
    private final Consumer<JSONObject> loader;
    private final Consumer<JSONObject> saver;
    private final Consumer<ActionListener> changeListenerRegistrar;
    private final Runnable onSelected;
    private final Supplier<Boolean> displayCondition;
    
    /**
     * Creates a new NavigablePanelAdapter with all components specified, excluding icon.
     * 
     * @param title the panel title, must not be null
     * @param helpUrl the help URL, may be null
     * @param root the root component, must not be null
     * @param loader consumer that receives a JSONObject for loading configuration, must not be null
     * @param saver consumer that receives a JSONObject for saving configuration, must not be null
     * @param changeListenerRegistrar consumer that receives an ActionListener for change notifications, must not be null
     * @param onSelected callback to invoke when this panel is selected, may be null
     * @param displayCondition supplier that determines whether this panel should be displayed, must not be null
     */
    public NavigablePanelAdapter(String title,
                                  String helpUrl,
                                  JComponent root,
                                  Consumer<JSONObject> loader,
                                  Consumer<JSONObject> saver,
                                  Consumer<ActionListener> changeListenerRegistrar,
                                  Runnable onSelected,
                                  Supplier<Boolean> displayCondition) {
        this(title, helpUrl, null, root, loader, saver, changeListenerRegistrar, onSelected, displayCondition);
    }
    
    /**
     * Creates a new NavigablePanelAdapter with all components specified.
     * 
     * @param title the panel title, must not be null
     * @param helpUrl the help URL, may be null
     * @param icon the icon for display in navigation UI, may be null (org.kordamp.ikonli.swing.FontIcon recommended)
     * @param root the root component, must not be null
     * @param loader consumer that receives a JSONObject for loading configuration, must not be null
     * @param saver consumer that receives a JSONObject for saving configuration, must not be null
     * @param changeListenerRegistrar consumer that receives an ActionListener for change notifications, must not be null
     * @param onSelected callback to invoke when this panel is selected, may be null
     * @param displayCondition supplier that determines whether this panel should be displayed, must not be null
     */
    public NavigablePanelAdapter(String title,
                                  String helpUrl,
                                  Icon icon,
                                  JComponent root,
                                  Consumer<JSONObject> loader,
                                  Consumer<JSONObject> saver,
                                  Consumer<ActionListener> changeListenerRegistrar,
                                  Runnable onSelected,
                                  Supplier<Boolean> displayCondition) {
        this.title = title;
        this.helpUrl = helpUrl;
        this.icon = icon;
        this.root = root;
        this.loader = loader;
        this.saver = saver;
        this.changeListenerRegistrar = changeListenerRegistrar;
        this.onSelected = onSelected;
        this.displayCondition = displayCondition;
    }
    
    @Override
    public String getTitle() {
        return title;
    }
    
    @Override
    public String getHelpUrl() {
        return helpUrl;
    }
    
    @Override
    public Icon getIcon() {
        return icon;
    }
    
    @Override
    public JComponent getRoot() {
        return root;
    }
    
    @Override
    public void load(JSONObject packageJSON, JSONObject jdeploy) {
        // Determine which object to pass based on what was configured
        // If loader was set up to expect jdeploy, pass jdeploy; if package.json, pass that
        // The factory methods will clarify this intent
        JSONObject objectToLoad = jdeploy != null ? jdeploy : packageJSON;
        if (objectToLoad != null) {
            loader.accept(objectToLoad);
        }
    }
    
    @Override
    public void save(JSONObject packageJSON, JSONObject jdeploy) {
        // Determine which object to pass based on what was configured
        JSONObject objectToSave = jdeploy != null ? jdeploy : packageJSON;
        if (objectToSave != null) {
            saver.accept(objectToSave);
        }
    }
    
    @Override
    public void addChangeListener(ActionListener listener) {
        changeListenerRegistrar.accept(listener);
    }
    
    @Override
    public boolean shouldDisplay() {
        return displayCondition.get();
    }
    
    @Override
    public Runnable getOnSelected() {
        return onSelected;
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the jdeploy configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param root the root component
     * @param loader consumer that receives the jdeploy JSONObject
     * @param saver consumer that receives the jdeploy JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @return a new NavigablePanelAdapter configured for jdeploy-focused panels
     */
    public static NavigablePanelAdapter forJdeployPanel(String title,
                                                         String helpUrl,
                                                         JComponent root,
                                                         Consumer<JSONObject> loader,
                                                         Consumer<JSONObject> saver,
                                                         Consumer<ActionListener> changeListenerRegistrar) {
        return forJdeployPanel(title, helpUrl, null, root, loader, saver, changeListenerRegistrar, null, () -> true);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the jdeploy configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param icon the icon for display in navigation UI, may be null (org.kordamp.ikonli.swing.FontIcon recommended)
     * @param root the root component
     * @param loader consumer that receives the jdeploy JSONObject
     * @param saver consumer that receives the jdeploy JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @return a new NavigablePanelAdapter configured for jdeploy-focused panels
     */
    public static NavigablePanelAdapter forJdeployPanel(String title,
                                                         String helpUrl,
                                                         Icon icon,
                                                         JComponent root,
                                                         Consumer<JSONObject> loader,
                                                         Consumer<JSONObject> saver,
                                                         Consumer<ActionListener> changeListenerRegistrar) {
        return forJdeployPanel(title, helpUrl, icon, root, loader, saver, changeListenerRegistrar, null, () -> true);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the jdeploy configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param root the root component
     * @param loader consumer that receives the jdeploy JSONObject
     * @param saver consumer that receives the jdeploy JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for jdeploy-focused panels
     */
    public static NavigablePanelAdapter forJdeployPanel(String title,
                                                         String helpUrl,
                                                         JComponent root,
                                                         Consumer<JSONObject> loader,
                                                         Consumer<JSONObject> saver,
                                                         Consumer<ActionListener> changeListenerRegistrar,
                                                         Supplier<Boolean> displayCondition) {
        return forJdeployPanel(title, helpUrl, null, root, loader, saver, changeListenerRegistrar, null, displayCondition);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the jdeploy configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param icon the icon for display in navigation UI, may be null (org.kordamp.ikonli.swing.FontIcon recommended)
     * @param root the root component
     * @param loader consumer that receives the jdeploy JSONObject
     * @param saver consumer that receives the jdeploy JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for jdeploy-focused panels
     */
    public static NavigablePanelAdapter forJdeployPanel(String title,
                                                         String helpUrl,
                                                         Icon icon,
                                                         JComponent root,
                                                         Consumer<JSONObject> loader,
                                                         Consumer<JSONObject> saver,
                                                         Consumer<ActionListener> changeListenerRegistrar,
                                                         Supplier<Boolean> displayCondition) {
        return forJdeployPanel(title, helpUrl, icon, root, loader, saver, changeListenerRegistrar, null, displayCondition);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the jdeploy configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param root the root component
     * @param loader consumer that receives the jdeploy JSONObject
     * @param saver consumer that receives the jdeploy JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param onSelected callback to invoke when this panel is selected, may be null
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for jdeploy-focused panels
     */
    public static NavigablePanelAdapter forJdeployPanel(String title,
                                                         String helpUrl,
                                                         JComponent root,
                                                         Consumer<JSONObject> loader,
                                                         Consumer<JSONObject> saver,
                                                         Consumer<ActionListener> changeListenerRegistrar,
                                                         Runnable onSelected,
                                                         Supplier<Boolean> displayCondition) {
        return forJdeployPanel(title, helpUrl, null, root, loader, saver, changeListenerRegistrar, onSelected, displayCondition);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the jdeploy configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param icon the icon for display in navigation UI, may be null (org.kordamp.ikonli.swing.FontIcon recommended)
     * @param root the root component
     * @param loader consumer that receives the jdeploy JSONObject
     * @param saver consumer that receives the jdeploy JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param onSelected callback to invoke when this panel is selected, may be null
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for jdeploy-focused panels
     */
    public static NavigablePanelAdapter forJdeployPanel(String title,
                                                         String helpUrl,
                                                         Icon icon,
                                                         JComponent root,
                                                         Consumer<JSONObject> loader,
                                                         Consumer<JSONObject> saver,
                                                         Consumer<ActionListener> changeListenerRegistrar,
                                                         Runnable onSelected,
                                                         Supplier<Boolean> displayCondition) {
        return new NavigablePanelAdapter(title, helpUrl, icon, root, loader, saver, changeListenerRegistrar, onSelected, displayCondition);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the package.json configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param root the root component
     * @param loader consumer that receives the package.json JSONObject
     * @param saver consumer that receives the package.json JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @return a new NavigablePanelAdapter configured for package.json-focused panels
     */
    public static NavigablePanelAdapter forPackageJsonPanel(String title,
                                                             String helpUrl,
                                                             JComponent root,
                                                             Consumer<JSONObject> loader,
                                                             Consumer<JSONObject> saver,
                                                             Consumer<ActionListener> changeListenerRegistrar) {
        return forPackageJsonPanel(title, helpUrl, null, root, loader, saver, changeListenerRegistrar, null, () -> true);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the package.json configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param icon the icon for display in navigation UI, may be null (org.kordamp.ikonli.swing.FontIcon recommended)
     * @param root the root component
     * @param loader consumer that receives the package.json JSONObject
     * @param saver consumer that receives the package.json JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @return a new NavigablePanelAdapter configured for package.json-focused panels
     */
    public static NavigablePanelAdapter forPackageJsonPanel(String title,
                                                             String helpUrl,
                                                             Icon icon,
                                                             JComponent root,
                                                             Consumer<JSONObject> loader,
                                                             Consumer<JSONObject> saver,
                                                             Consumer<ActionListener> changeListenerRegistrar) {
        return forPackageJsonPanel(title, helpUrl, icon, root, loader, saver, changeListenerRegistrar, null, () -> true);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the package.json configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param root the root component
     * @param loader consumer that receives the package.json JSONObject
     * @param saver consumer that receives the package.json JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for package.json-focused panels
     */
    public static NavigablePanelAdapter forPackageJsonPanel(String title,
                                                             String helpUrl,
                                                             JComponent root,
                                                             Consumer<JSONObject> loader,
                                                             Consumer<JSONObject> saver,
                                                             Consumer<ActionListener> changeListenerRegistrar,
                                                             Supplier<Boolean> displayCondition) {
        return forPackageJsonPanel(title, helpUrl, null, root, loader, saver, changeListenerRegistrar, null, displayCondition);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the package.json configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param icon the icon for display in navigation UI, may be null (org.kordamp.ikonli.swing.FontIcon recommended)
     * @param root the root component
     * @param loader consumer that receives the package.json JSONObject
     * @param saver consumer that receives the package.json JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for package.json-focused panels
     */
    public static NavigablePanelAdapter forPackageJsonPanel(String title,
                                                             String helpUrl,
                                                             Icon icon,
                                                             JComponent root,
                                                             Consumer<JSONObject> loader,
                                                             Consumer<JSONObject> saver,
                                                             Consumer<ActionListener> changeListenerRegistrar,
                                                             Supplier<Boolean> displayCondition) {
        return forPackageJsonPanel(title, helpUrl, icon, root, loader, saver, changeListenerRegistrar, null, displayCondition);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the package.json configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param root the root component
     * @param loader consumer that receives the package.json JSONObject
     * @param saver consumer that receives the package.json JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param onSelected callback to invoke when this panel is selected, may be null
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for package.json-focused panels
     */
    public static NavigablePanelAdapter forPackageJsonPanel(String title,
                                                             String helpUrl,
                                                             JComponent root,
                                                             Consumer<JSONObject> loader,
                                                             Consumer<JSONObject> saver,
                                                             Consumer<ActionListener> changeListenerRegistrar,
                                                             Runnable onSelected,
                                                             Supplier<Boolean> displayCondition) {
        return forPackageJsonPanel(title, helpUrl, null, root, loader, saver, changeListenerRegistrar, onSelected, displayCondition);
    }
    
    /**
     * Creates an adapter for a panel that works primarily with the package.json configuration object.
     * 
     * @param title the panel title
     * @param helpUrl the help URL, may be null
     * @param icon the icon for display in navigation UI, may be null (org.kordamp.ikonli.swing.FontIcon recommended)
     * @param root the root component
     * @param loader consumer that receives the package.json JSONObject
     * @param saver consumer that receives the package.json JSONObject
     * @param changeListenerRegistrar consumer that registers change listeners
     * @param onSelected callback to invoke when this panel is selected, may be null
     * @param displayCondition supplier that determines whether this panel should be displayed
     * @return a new NavigablePanelAdapter configured for package.json-focused panels
     */
    public static NavigablePanelAdapter forPackageJsonPanel(String title,
                                                             String helpUrl,
                                                             Icon icon,
                                                             JComponent root,
                                                             Consumer<JSONObject> loader,
                                                             Consumer<JSONObject> saver,
                                                             Consumer<ActionListener> changeListenerRegistrar,
                                                             Runnable onSelected,
                                                             Supplier<Boolean> displayCondition) {
        // For package.json panels, we wrap the loader/saver to prefer packageJSON parameter
        Consumer<JSONObject> wrappedLoader = loader;
        Consumer<JSONObject> wrappedSaver = saver;
        
        return new NavigablePanelAdapter(title, helpUrl, icon, root, wrappedLoader, wrappedSaver, changeListenerRegistrar, onSelected, displayCondition) {
            @Override
            public void load(JSONObject packageJSON, JSONObject jdeploy) {
                if (packageJSON != null) {
                    wrappedLoader.accept(packageJSON);
                }
            }
            
            @Override
            public void save(JSONObject packageJSON, JSONObject jdeploy) {
                if (packageJSON != null) {
                    wrappedSaver.accept(packageJSON);
                }
            }
        };
    }
}
