package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.util.SwingUtils;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Panel for managing repository-related settings: homepage, repository URL, and directory.
 * Handles both String and JSONObject formats for the repository field in package.json.
 */
public class RepositorySettingsPanel {
    
    private final JFrame parentFrame;
    private final HomepageVerifier homepageVerifier;
    private ActionListener changeListener;
    
    private JPanel root;
    private JTextField homepage;
    private JTextField repositoryUrl;
    private JTextField repositoryDirectory;
    private JButton verifyButton;
    
    /**
     * Constructs a RepositorySettingsPanel.
     *
     * @param parentFrame the parent JFrame for dialogs
     * @param homepageVerifier callback for homepage verification
     */
    public RepositorySettingsPanel(JFrame parentFrame, HomepageVerifier homepageVerifier) {
        this.parentFrame = parentFrame;
        this.homepageVerifier = homepageVerifier;
        initializeUI();
        wireVerifyButton();
    }
    
    /**
     * Initializes the UI components for the repository settings panel.
     */
    private void initializeUI() {
        root = new JPanel();
        root.setLayout(new BorderLayout(0, 0));
        root.setPreferredSize(new Dimension(640, 300));
        root.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        root.add(formPanel, BorderLayout.CENTER);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Homepage row
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Homepage:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1;
        homepage = new JTextField();
        formPanel.add(homepage, gbc);
        
        gbc.gridx = 2;
        gbc.weightx = 0;
        verifyButton = new JButton("Verify");
        formPanel.add(verifyButton, gbc);
        
        // Repository URL row
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Repository URL:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        repositoryUrl = new JTextField();
        formPanel.add(repositoryUrl, gbc);
        gbc.gridwidth = 1;
        
        // Repository Directory row
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Repository Directory:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        repositoryDirectory = new JTextField();
        formPanel.add(repositoryDirectory, gbc);
        gbc.gridwidth = 1;
    }
    
    /**
     * Wires the verify button to the homepageVerifier callback.
     */
    private void wireVerifyButton() {
        verifyButton.addActionListener(evt -> {
            if (homepageVerifier != null) {
                homepageVerifier.verify(homepage.getText().trim());
            }
        });
    }
    
    /**
     * Loads repository settings from a package.json object.
     * Handles both String format (legacy) and JSONObject format (modern) for the repository field.
     *
     * @param packageJSON the package.json object, or null
     */
    public void load(JSONObject packageJSON) {
        if (packageJSON == null) {
            return;
        }
        
        // Load homepage from root level
        if (packageJSON.has("homepage")) {
            homepage.setText(packageJSON.getString("homepage"));
        }
        
        // Load repository info - handles both String and JSONObject formats
        if (packageJSON.has("repository")) {
            Object repoObj = packageJSON.get("repository");
            if (repoObj instanceof JSONObject) {
                JSONObject repo = (JSONObject) repoObj;
                if (repo.has("url")) {
                    repositoryUrl.setText(repo.getString("url"));
                }
                if (repo.has("directory")) {
                    repositoryDirectory.setText(repo.getString("directory"));
                }
            } else if (repoObj instanceof String) {
                repositoryUrl.setText((String) repoObj);
            }
        }
    }
    
    /**
     * Saves repository settings to a package.json object.
     * Creates a repository JSONObject when needed, and removes empty fields.
     *
     * @param packageJSON the package.json object, or null
     */
    public void save(JSONObject packageJSON) {
        if (packageJSON == null) {
            return;
        }
        
        // Save homepage at root level
        String homepageText = homepage.getText().trim();
        if (!homepageText.isEmpty()) {
            packageJSON.put("homepage", homepageText);
        } else {
            packageJSON.remove("homepage");
        }
        
        // Save repository info as JSONObject
        String repoUrl = repositoryUrl.getText().trim();
        String repoDir = repositoryDirectory.getText().trim();
        
        if (!repoUrl.isEmpty() || !repoDir.isEmpty()) {
            JSONObject repo = new JSONObject();
            if (!repoUrl.isEmpty()) {
                repo.put("url", repoUrl);
            }
            if (!repoDir.isEmpty()) {
                repo.put("directory", repoDir);
            }
            packageJSON.put("repository", repo);
        } else {
            packageJSON.remove("repository");
        }
    }
    
    /**
     * Registers a change listener to be notified of user edits.
     *
     * @param listener the ActionListener to call on changes
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
        SwingUtils.addChangeListenerTo(homepage, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(repositoryUrl, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(repositoryDirectory, this::fireChangeEvent);
    }
    
    /**
     * Fires a change event to registered listeners.
     */
    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new ActionEvent(this, 0, "changed"));
        }
    }
    
    /**
     * Gets the root JPanel component for this panel.
     *
     * @return the root panel
     */
    public JPanel getRoot() {
        return root;
    }
    
    /**
     * Sets the visibility of the verify button.
     *
     * @param visible true to show the button, false to hide it
     */
    public void setVerifyButtonVisible(boolean visible) {
        verifyButton.setVisible(visible);
    }
    
    // Accessors for testing
    
    /**
     * Gets the homepage text field.
     *
     * @return the homepage JTextField
     */
    public JTextField getHomepage() {
        return homepage;
    }
    
    /**
     * Gets the repository URL text field.
     *
     * @return the repository URL JTextField
     */
    public JTextField getRepositoryUrl() {
        return repositoryUrl;
    }
    
    /**
     * Gets the repository directory text field.
     *
     * @return the repository directory JTextField
     */
    public JTextField getRepositoryDirectory() {
        return repositoryDirectory;
    }
    
    /**
     * Gets the verify button.
     *
     * @return the verify JButton
     */
    public JButton getVerifyButton() {
        return verifyButton;
    }
}
