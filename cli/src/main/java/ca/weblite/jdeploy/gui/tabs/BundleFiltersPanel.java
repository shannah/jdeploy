package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.models.Platform;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel for editing .jdpignore files for bundle filtering.
 * Provides a tabbed interface for editing global and platform-specific ignore patterns.
 */
public class BundleFiltersPanel extends JPanel {
    
    private final File projectDirectory;
    private final JTabbedPane tabbedPane;
    private final Map<Platform, JTextArea> textAreas;
    private final Map<Platform, JScrollPane> scrollPanes;
    private DocumentListener changeListener;
    
    // Callback to notify parent when changes occur
    private Runnable onChangeCallback;
    
    /**
     * Creates a new BundleFiltersPanel.
     * 
     * @param projectDirectory the project directory where .jdpignore files are located
     */
    public BundleFiltersPanel(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        this.tabbedPane = new JTabbedPane();
        this.textAreas = new HashMap<>();
        this.scrollPanes = new HashMap<>();
        
        initializeUI();
        loadExistingFiles();
    }
    
    /**
     * Sets the callback to be invoked when content changes.
     * 
     * @param callback the callback to invoke on changes
     */
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }
    
    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setLayout(new BorderLayout());
        
        // Create change listener that will be used for all text areas
        changeListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { notifyChange(); }
            @Override
            public void removeUpdate(DocumentEvent e) { notifyChange(); }
            @Override
            public void changedUpdate(DocumentEvent e) { notifyChange(); }
            
            private void notifyChange() {
                if (onChangeCallback != null) {
                    onChangeCallback.run();
                }
            }
        };
        
        // Create tabs for each platform
        createGlobalTab();
        createPlatformTabs();
        
        add(tabbedPane, BorderLayout.CENTER);
        add(createHelpPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * Creates the global .jdpignore tab.
     */
    private void createGlobalTab() {
        JPanel globalPanel = createTabPanel(Platform.DEFAULT, "Global patterns applied to all platform bundles", 
            "# Example global ignore patterns:\n" +
            "# com.testing.mocklibs.native\n" +
            "# com.development.debugging.native\n" +
            "# \n" +
            "# Example keep patterns (override ignore):\n" +
            "# !com.testing.mocklibs.native.required\n");
        
        tabbedPane.addTab("Global", globalPanel);
    }
    
    /**
     * Creates tabs for platform-specific ignore files.
     */
    private void createPlatformTabs() {
        // Skip DEFAULT platform as it's handled by Global tab
        for (Platform platform : Platform.values()) {
            if (platform == Platform.DEFAULT) {
                continue;
            }
            
            String tabName = getPlatformDisplayName(platform);
            String helpText = "Patterns specific to " + tabName + " bundles";
            String exampleText = getExampleText(platform);
            
            JPanel platformPanel = createTabPanel(platform, helpText, exampleText);
            tabbedPane.addTab(tabName, platformPanel);
        }
    }
    
    /**
     * Creates a tab panel for a specific platform.
     * 
     * @param platform the platform this tab is for
     * @param helpText the help text to display
     * @param exampleText the example text to show in placeholder
     * @return the created panel
     */
    private JPanel createTabPanel(Platform platform, String helpText, String exampleText) {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Help text at top
        JLabel helpLabel = new JLabel("<html><div style='margin: 8px;'>" + helpText + "</div></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC));
        panel.add(helpLabel, BorderLayout.NORTH);
        
        // Text area for patterns
        JTextArea textArea = new JTextArea(15, 60);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setTabSize(4);
        textArea.getDocument().addDocumentListener(changeListener);
        
        // Add placeholder text as a tooltip
        textArea.setToolTipText("<html>" + exampleText.replace("\n", "<br>") + "</html>");
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Store references for later use
        textAreas.put(platform, textArea);
        scrollPanes.put(platform, scrollPane);
        
        return panel;
    }
    
    /**
     * Creates the help panel at the bottom of the UI.
     */
    private JPanel createHelpPanel() {
        JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.setBorder(BorderFactory.createTitledBorder("Pattern Syntax"));
        
        String helpText = "<html><div style='margin: 4px;'>" +
            "<b>Ignore patterns:</b> com.example.native or /path/to/file<br>" +
            "<b>Keep patterns:</b> !com.example.native.required (overrides ignore patterns)<br>" +
            "<b>Comments:</b> # This is a comment<br>" +
            "<b>Wildcards:</b> com.example.* or *.dll" +
            "</div></html>";
        
        JLabel helpLabel = new JLabel(helpText);
        helpPanel.add(helpLabel, BorderLayout.CENTER);
        
        // Add quick action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton clearButton = new JButton("Clear Current Tab");
        clearButton.addActionListener(e -> clearCurrentTab());
        buttonPanel.add(clearButton);
        
        JButton addCommonButton = new JButton("Add Common Patterns");
        addCommonButton.addActionListener(e -> addCommonPatterns());
        buttonPanel.add(addCommonButton);
        
        helpPanel.add(buttonPanel, BorderLayout.EAST);
        
        return helpPanel;
    }
    
    /**
     * Gets the display name for a platform.
     */
    private String getPlatformDisplayName(Platform platform) {
        switch (platform) {
            case MAC_X64: return "macOS Intel";
            case MAC_ARM64: return "macOS Silicon";
            case WIN_X64: return "Windows x64";
            case WIN_ARM64: return "Windows ARM";
            case LINUX_X64: return "Linux x64";
            case LINUX_ARM64: return "Linux ARM";
            default: return platform.getIdentifier();
        }
    }
    
    /**
     * Gets example text for a platform.
     */
    private String getExampleText(Platform platform) {
        String platformId = platform.getIdentifier();
        return "# Keep " + getPlatformDisplayName(platform) + " native libraries:\n" +
               "!ca.weblite.native." + platformId + "\n" +
               "!com.thirdparty.foo.bar.native." + platformId + "\n" +
               "!/*.dll\n" +
               "!/native/" + platformId.split("-")[0] + "/";
    }
    
    /**
     * Gets the platform for a given tab index.
     */
    private Platform getPlatformForTabIndex(int tabIndex) {
        if (tabIndex == 0) {
            return Platform.DEFAULT; // Global tab
        }
        
        // Skip DEFAULT platform in the values array
        Platform[] platforms = Platform.values();
        int nonDefaultIndex = 1; // Start from 1 since 0 is Global (DEFAULT)
        for (Platform platform : platforms) {
            if (platform == Platform.DEFAULT) {
                continue;
            }
            if (nonDefaultIndex == tabIndex) {
                return platform;
            }
            nonDefaultIndex++;
        }
        
        return Platform.DEFAULT; // Fallback
    }
    
    /**
     * Gets the ignore file for a platform.
     */
    private File getIgnoreFile(Platform platform) {
        if (platform == Platform.DEFAULT) {
            return new File(projectDirectory, ".jdpignore");
        } else {
            return new File(projectDirectory, ".jdpignore." + platform.getIdentifier());
        }
    }
    
    /**
     * Loads existing .jdpignore files into the text areas.
     */
    private void loadExistingFiles() {
        for (Map.Entry<Platform, JTextArea> entry : textAreas.entrySet()) {
            Platform platform = entry.getKey();
            JTextArea textArea = entry.getValue();
            File ignoreFile = getIgnoreFile(platform);
            
            if (ignoreFile.exists() && ignoreFile.isFile()) {
                try {
                    String content = FileUtils.readFileToString(ignoreFile, StandardCharsets.UTF_8);
                    textArea.setText(content);
                } catch (IOException e) {
                    System.err.println("Failed to read " + ignoreFile.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Saves all .jdpignore files. Empty files are deleted.
     */
    public void saveAllFiles() {
        for (Map.Entry<Platform, JTextArea> entry : textAreas.entrySet()) {
            Platform platform = entry.getKey();
            JTextArea textArea = entry.getValue();
            File ignoreFile = getIgnoreFile(platform);
            
            String content = textArea.getText().trim();
            
            try {
                if (content.isEmpty()) {
                    // Delete empty files
                    if (ignoreFile.exists()) {
                        ignoreFile.delete();
                    }
                } else {
                    // Write non-empty content
                    FileUtils.writeStringToFile(ignoreFile, content, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                System.err.println("Failed to save " + ignoreFile.getAbsolutePath() + ": " + e.getMessage());
                // Show error dialog
                JOptionPane.showMessageDialog(this,
                    "Failed to save " + ignoreFile.getName() + ":\n" + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Checks if any content has been modified.
     */
    public boolean hasUnsavedChanges() {
        for (Map.Entry<Platform, JTextArea> entry : textAreas.entrySet()) {
            Platform platform = entry.getKey();
            JTextArea textArea = entry.getValue();
            File ignoreFile = getIgnoreFile(platform);
            
            String currentContent = textArea.getText().trim();
            
            // Check if file exists and compare content
            if (ignoreFile.exists() && ignoreFile.isFile()) {
                try {
                    String fileContent = FileUtils.readFileToString(ignoreFile, StandardCharsets.UTF_8).trim();
                    if (!currentContent.equals(fileContent)) {
                        return true;
                    }
                } catch (IOException e) {
                    // If we can't read the file, assume it's changed
                    return true;
                }
            } else {
                // File doesn't exist, check if there's content to save
                if (!currentContent.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Reloads all files from disk, discarding any unsaved changes.
     */
    public void reloadAllFiles() {
        loadExistingFiles();
    }
    
    /**
     * Clears the content of the currently selected tab.
     */
    private void clearCurrentTab() {
        int selectedTab = tabbedPane.getSelectedIndex();
        Platform platform = getPlatformForTabIndex(selectedTab);
        JTextArea textArea = textAreas.get(platform);
        if (textArea != null) {
            int result = JOptionPane.showConfirmDialog(this,
                "Clear all content in the current tab?",
                "Clear Tab",
                JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                textArea.setText("");
            }
        }
    }
    
    /**
     * Adds common patterns to the currently selected tab.
     */
    private void addCommonPatterns() {
        int selectedTab = tabbedPane.getSelectedIndex();
        Platform platform = getPlatformForTabIndex(selectedTab);
        JTextArea textArea = textAreas.get(platform);
        
        if (textArea != null) {
            String commonPatterns;
            if (platform == Platform.DEFAULT) {
                commonPatterns = "\n# Common ignore patterns\n" +
                               "com.testing.mocklibs.native\n" +
                               "com.development.debugging.native\n" +
                               "debug\n" +
                               "test\n\n" +
                               "# Keep required patterns\n" +
                               "!com.testing.mocklibs.native.required\n";
            } else {
                commonPatterns = "\n# Keep " + getPlatformDisplayName(platform) + " native libraries\n" +
                               "!ca.weblite.native." + platform.getIdentifier() + "\n" +
                               "!com.myapp.native." + platform.getIdentifier() + "\n";
            }
            
            textArea.append(commonPatterns);
        }
    }
    
    
    /**
     * Gets the root component for embedding in the main editor.
     */
    public JComponent getRoot() {
        return this;
    }
}