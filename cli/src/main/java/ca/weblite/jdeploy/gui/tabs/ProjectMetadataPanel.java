package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.util.SwingUtils;
import ca.weblite.jdeploy.interop.FileChooserInterop;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class ProjectMetadataPanel {
    private final JFrame parentFrame;
    private final File projectDirectory;
    private final FileChooserInterop fileChooserInterop;
    
    private JPanel root;
    private JTextField name;
    private JTextField version;
    private JTextField title;
    private JTextField author;
    private JTextArea description;
    private JTextField license;
    private JButton icon;
    private JLabel projectPath;
    private JButton copyPath;
    
    private ActionListener changeListener;
    
    public ProjectMetadataPanel(JFrame parentFrame, File projectDirectory, FileChooserInterop fileChooserInterop) {
        this.parentFrame = parentFrame;
        this.projectDirectory = projectDirectory;
        this.fileChooserInterop = fileChooserInterop;
        initializeUI();
    }
    
    private void initializeUI() {
        root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create form panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Name field
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Name:"), gbc);
        name = new JTextField(30);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(name, gbc);
        gbc.weightx = 0;
        
        // Version field
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Version:"), gbc);
        version = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(version, gbc);
        
        // Title field
        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(new JLabel("Title:"), gbc);
        title = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(title, gbc);
        
        // Author field
        gbc.gridx = 0;
        gbc.gridy = 3;
        formPanel.add(new JLabel("Author:"), gbc);
        author = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(author, gbc);
        
        // License field
        gbc.gridx = 0;
        gbc.gridy = 4;
        formPanel.add(new JLabel("License:"), gbc);
        license = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(license, gbc);
        
        // Description field
        gbc.gridx = 0;
        gbc.gridy = 5;
        formPanel.add(new JLabel("Description:"), gbc);
        description = new JTextArea(4, 30);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JScrollPane descriptionScroll = new JScrollPane(description);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        formPanel.add(descriptionScroll, gbc);
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Project Path field
        gbc.gridx = 0;
        gbc.gridy = 6;
        formPanel.add(new JLabel("Path:"), gbc);
        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        projectPath = new JLabel();
        projectPath.setFont(projectPath.getFont().deriveFont(10f));
        pathPanel.add(projectPath, BorderLayout.CENTER);
        copyPath = new JButton("Copy");
        copyPath.addActionListener(evt -> copyProjectPathToClipboard());
        pathPanel.add(copyPath, BorderLayout.EAST);
        gbc.gridx = 1;
        formPanel.add(pathPanel, gbc);
        
        root.add(formPanel);
        root.add(Box.createVerticalStrut(10));
        
        // Icon button panel
        JPanel iconPanel = new JPanel();
        iconPanel.setBorder(BorderFactory.createTitledBorder("Icon"));
        icon = new JButton();
        icon.setPreferredSize(new Dimension(128, 128));
        initializeIconButton();
        iconPanel.add(icon);
        root.add(iconPanel);
        
        // Add change listeners to all fields
        SwingUtils.addChangeListenerTo(name, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(version, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(title, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(author, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(license, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(description, this::fireChangeEvent);
    }
    
    private void initializeIconButton() {
        File iconFile = getIconFile();
        if (iconFile.exists()) {
            try {
                icon.setIcon(new ImageIcon(Thumbnails.of(iconFile).size(128, 128).asBufferedImage()));
                icon.setText("");
            } catch (Exception ex) {
                System.err.println("Failed to load icon image: " + ex.getMessage());
                icon.setText("Select icon...");
            }
        } else {
            icon.setText("Select icon...");
        }
        
        icon.addActionListener(evt -> handleIconSelection());
    }
    
    private void handleIconSelection() {
        Set<String> extensions = new HashSet<>();
        extensions.add("png");
        File selected = fileChooserInterop.showFileChooser(parentFrame, "Select Icon Image", extensions);
        if (selected == null) {
            return;
        }
        
        try {
            FileUtils.copyFile(selected, getIconFile());
            icon.setIcon(new ImageIcon(Thumbnails.of(getIconFile()).size(128, 128).asBufferedImage()));
            icon.setText("");
            fireChangeEvent();
        } catch (Exception ex) {
            System.err.println("Error while copying icon file: " + ex.getMessage());
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(
                parentFrame,
                "Failed to select icon: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    private void copyProjectPathToClipboard() {
        StringSelection stringSelection = new StringSelection(projectPath.getText());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }
    
    private File getIconFile() {
        return new File(projectDirectory, "icon.png");
    }
    
    public JPanel getRoot() {
        return root;
    }
    
    public void load(JSONObject packageJSON) {
        if (packageJSON == null) {
            return;
        }
        
        // Load root-level fields
        if (packageJSON.has("name")) {
            name.setText(packageJSON.getString("name"));
        }
        
        if (packageJSON.has("version")) {
            version.setText(packageJSON.getString("version"));
        }
        
        if (packageJSON.has("author")) {
            Object authorObj = packageJSON.get("author");
            String authorString = "";
            if (authorObj instanceof JSONObject) {
                JSONObject authorJson = (JSONObject) authorObj;
                if (authorJson.has("name")) {
                    authorString += authorJson.getString("name");
                }
                if (authorJson.has("email")) {
                    authorString += " <" + authorJson.getString("email") + ">";
                }
                if (authorJson.has("url")) {
                    authorString += " (" + authorJson.getString("url") + ")";
                }
            } else if (authorObj instanceof String) {
                authorString = (String) authorObj;
            }
            author.setText(authorString);
        }
        
        if (packageJSON.has("description")) {
            description.setText(packageJSON.getString("description"));
        }
        
        if (packageJSON.has("license")) {
            license.setText(packageJSON.getString("license"));
        }
        
        // Load jdeploy.title
        if (packageJSON.has("jdeploy")) {
            JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
            if (jdeploy.has("title")) {
                title.setText(jdeploy.getString("title"));
            }
        }
        
        // Update project path display
        projectPath.setText(projectDirectory.getAbsolutePath());
    }
    
    public void save(JSONObject packageJSON) {
        if (packageJSON == null) {
            return;
        }
        
        // Save root-level fields
        if (!name.getText().trim().isEmpty()) {
            packageJSON.put("name", name.getText().trim());
        }
        
        if (!version.getText().trim().isEmpty()) {
            packageJSON.put("version", version.getText().trim());
        }
        
        if (!author.getText().trim().isEmpty()) {
            packageJSON.put("author", author.getText().trim());
        } else {
            packageJSON.remove("author");
        }
        
        if (!description.getText().trim().isEmpty()) {
            packageJSON.put("description", description.getText().trim());
        } else {
            packageJSON.remove("description");
        }
        
        if (!license.getText().trim().isEmpty()) {
            packageJSON.put("license", license.getText().trim());
        } else {
            packageJSON.remove("license");
        }
        
        // Ensure jdeploy object exists and save title
        if (!packageJSON.has("jdeploy")) {
            packageJSON.put("jdeploy", new JSONObject());
        }
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        if (!title.getText().trim().isEmpty()) {
            jdeploy.put("title", title.getText().trim());
        } else {
            jdeploy.remove("title");
        }
    }
    
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }
    
    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }
}
