package ca.weblite.jdeploy.gui.tabs;

/**
 * Callback interface for homepage verification actions.
 */
public interface HomepageVerifier {
    /**
     * Called when the verify button is clicked.
     *
     * @param homepage the homepage URL to verify
     */
    void verify(String homepage);
}
