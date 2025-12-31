package ca.weblite.jdeploy.gui;

/**
 * Shared constants for jDeploy GUI components.
 */
public final class JDeployConstants {
    
    private JDeployConstants() {
        // Prevent instantiation
    }
    
    /**
     * The jDeploy website URL, configurable via system property.
     */
    public static final String JDEPLOY_WEBSITE_URL = System.getProperty("jdeploy.website.url", "https://www.jdeploy.com/");
    
    /**
     * The relative path to the jdeploy CLI entry point script within the bundle.
     */
    public static final String JDEPLOY_BUNDLE_SCRIPT_PATH = "jdeploy-bundle/jdeploy.js";
}
