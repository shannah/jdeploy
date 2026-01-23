package ca.weblite.jdeploy.models;

/**
 * Represents a helper action that can be triggered from the tray menu or service management panel.
 *
 * Helper actions are defined in package.json under jdeploy.helper.actions and allow users to
 * quickly access URLs, custom protocol handlers, or files related to the application.
 *
 * @author Steve Hannah
 */
public class HelperAction {

    private final String label;
    private final String description;
    private final String url;

    /**
     * Creates a new helper action.
     *
     * @param label The label to display in the menu (required)
     * @param description Optional tooltip description
     * @param url The URL, custom protocol, or file path to open (required)
     * @throws IllegalArgumentException if label or url is null or empty
     */
    public HelperAction(String label, String description, String url) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("label cannot be null or empty");
        }
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url cannot be null or empty");
        }
        this.label = label.trim();
        this.description = description != null ? description.trim() : null;
        this.url = url.trim();
    }

    /**
     * Gets the label to display in the menu.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the optional description for tooltip.
     *
     * @return the description, or null if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the URL, custom protocol, or file path to open.
     *
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Checks if this helper action has a description.
     *
     * @return true if description is not null and not empty
     */
    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }

    @Override
    public String toString() {
        return "HelperAction{" +
                "label='" + label + '\'' +
                ", description='" + description + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
