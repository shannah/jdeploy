package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.services.PublishingCoordinator;
import ca.weblite.jdeploy.gui.util.SwingUtils;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import org.json.JSONObject;

import static ca.weblite.jdeploy.PathUtil.fromNativePath;

public class DetailsPanel {
    
    private JFrame parentFrame;
    private File projectDirectory;
    
    public DetailsPanel() {
        initJdkProviderListener();
        updateJbrVariantVisibility();
    }
    
    private JPanel panel1;
    private JTextField name;
    private JTextField version;
    private JTextField title;
    private JTextField author;
    private JTextArea description;
    private JButton icon;
    private JButton helpButton;
    private JTextField jarFile;
    private JComboBox javaVersion;
    private JCheckBox requiresJavaFX;
    private JCheckBox requiresFullJDK;
    private JButton selectJarFile;
    private JTextField homepage;
    private JButton verifyButton;
    private JTextField repositoryUrl;
    private JTextField repositoryDirectory;
    private JTextField license;
    private JPanel root;
    private JLabel projectPath;
    private JButton copyPath;
    private JComboBox jdkProvider;
    private JComboBox jbrVariant;
    
    private ActionListener changeListener;
    private JLabel jbrVariantLabel;

    public void setParentFrame(JFrame frame) {
        this.parentFrame = frame;
        initSelectJarFileListener();
    }

    public void setProjectDirectory(File projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    public JPanel getRoot() {
        return root;
    }

    public JTextField getName() {
        return name;
    }

    public JTextField getVersion() {
        return version;
    }

    public JTextField getTitle() {
        return title;
    }

    public JTextField getAuthor() {
        return author;
    }

    public JTextArea getDescription() {
        return description;
    }

    public JButton getIcon() {
        return icon;
    }

    public JButton getHelpButton() {
        return helpButton;
    }

    public JTextField getJarFile() {
        return jarFile;
    }

    public JComboBox getJavaVersion() {
        return javaVersion;
    }

    public JCheckBox getRequiresJavaFX() {
        return requiresJavaFX;
    }

    public JCheckBox getRequiresFullJDK() {
        return requiresFullJDK;
    }

    public JButton getSelectJarFile() {
        return selectJarFile;
    }

    public JTextField getHomepage() {
        return homepage;
    }

    public JButton getVerifyButton() {
        return verifyButton;
    }

    public JTextField getRepositoryUrl() {
        return repositoryUrl;
    }

    public JTextField getRepositoryDirectory() {
        return repositoryDirectory;
    }

    public JTextField getLicense() {
        return license;
    }

    public JButton getCopyPath() {
        return copyPath;
    }

    public JLabel getProjectPath() {
        return projectPath;
    }

    public JComboBox getJdkProvider() {
        return jdkProvider;
    }

    public JComboBox getJbrVariant() {
        return jbrVariant;
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
                JSONObject author = (JSONObject) authorObj;
                if (author.has("name")) {
                    authorString += author.getString("name");
                }
                if (author.has("email")) {
                    authorString += " <" + author.getString("email") + ">";
                }
                if (author.has("url")) {
                    authorString += " (" + author.getString("url") + ")";
                }
            } else if (authorObj instanceof String) {
                authorString = (String) authorObj;
            }
            this.author.setText(authorString);
        }
        
        if (packageJSON.has("description")) {
            description.setText(packageJSON.getString("description"));
        }
        
        if (packageJSON.has("homepage")) {
            homepage.setText(packageJSON.getString("homepage"));
        }
        
        if (packageJSON.has("license")) {
            license.setText(packageJSON.getString("license"));
        }
        
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
        
        // Load jdeploy object fields
        if (packageJSON.has("jdeploy")) {
            JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
            
            if (jdeploy.has("title")) {
                title.setText(jdeploy.getString("title"));
            }
            
            if (jdeploy.has("jar")) {
                jarFile.setText(jdeploy.getString("jar"));
            }
            
            if (jdeploy.has("javafx")) {
                requiresJavaFX.setSelected(jdeploy.getBoolean("javafx"));
            }
            
            if (jdeploy.has("jdk")) {
                requiresFullJDK.setSelected(jdeploy.getBoolean("jdk"));
            }
            
            if (jdeploy.has("javaVersion")) {
                javaVersion.setSelectedItem(String.valueOf(jdeploy.get("javaVersion")));
            }
            
            if (jdeploy.has("jdkProvider")) {
                String provider = jdeploy.getString("jdkProvider");
                if ("jbr".equals(provider)) {
                    jdkProvider.setSelectedItem("JetBrains Runtime (JBR)");
                } else {
                    jdkProvider.setSelectedItem("Auto (Recommended)");
                }
            } else {
                jdkProvider.setSelectedItem("Auto (Recommended)");
            }
            
            if (jdeploy.has("jbrVariant")) {
                String variant = jdeploy.getString("jbrVariant");
                if ("jcef".equals(variant)) {
                    jbrVariant.setSelectedItem("JCEF");
                } else {
                    jbrVariant.setSelectedItem("Default");
                }
            } else {
                jbrVariant.setSelectedItem("Default");
            }
        }
        
        updateJbrVariantVisibility();
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
        
        if (!homepage.getText().trim().isEmpty()) {
            packageJSON.put("homepage", homepage.getText().trim());
        } else {
            packageJSON.remove("homepage");
        }
        
        if (!license.getText().trim().isEmpty()) {
            packageJSON.put("license", license.getText().trim());
        } else {
            packageJSON.remove("license");
        }
        
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
        
        // Ensure jdeploy object exists
        if (!packageJSON.has("jdeploy")) {
            packageJSON.put("jdeploy", new JSONObject());
        }
        
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        
        // Save jdeploy fields
        if (!title.getText().trim().isEmpty()) {
            jdeploy.put("title", title.getText().trim());
        } else {
            jdeploy.remove("title");
        }
        
        if (!jarFile.getText().trim().isEmpty()) {
            jdeploy.put("jar", jarFile.getText().trim());
        } else {
            jdeploy.remove("jar");
        }
        
        jdeploy.put("javafx", requiresJavaFX.isSelected());
        if (!requiresJavaFX.isSelected()) {
            jdeploy.remove("javafx");
        }
        
        jdeploy.put("jdk", requiresFullJDK.isSelected());
        if (!requiresFullJDK.isSelected()) {
            jdeploy.remove("jdk");
        }
        
        Object selectedVersion = javaVersion.getSelectedItem();
        if (selectedVersion != null && !selectedVersion.toString().isEmpty()) {
            jdeploy.put("javaVersion", selectedVersion.toString());
        }
        
        Object selectedProvider = jdkProvider.getSelectedItem();
        if ("JetBrains Runtime (JBR)".equals(selectedProvider)) {
            jdeploy.put("jdkProvider", "jbr");
        } else {
            jdeploy.remove("jdkProvider");
        }
        
        Object selectedVariant = jbrVariant.getSelectedItem();
        if ("JCEF".equals(selectedVariant)) {
            jdeploy.put("jbrVariant", "jcef");
        } else {
            jdeploy.remove("jbrVariant");
        }
    }

    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
        SwingUtils.addChangeListenerTo(name, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(version, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(author, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(description, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(title, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(homepage, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(license, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(repositoryUrl, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(repositoryDirectory, this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(jarFile, this::fireChangeEvent);
        
        requiresJavaFX.addActionListener(evt -> fireChangeEvent());
        requiresFullJDK.addActionListener(evt -> fireChangeEvent());
        javaVersion.addItemListener(evt -> fireChangeEvent());
        jdkProvider.addItemListener(evt -> {
            if (evt.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                fireChangeEvent();
            }
        });
        jbrVariant.addItemListener(evt -> fireChangeEvent());
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }

    private void updateJbrVariantVisibility() {
        if (jdkProvider == null || jbrVariant == null) {
            return;
        }

        String selected = (String) jdkProvider.getSelectedItem();
        boolean isJbr = "JetBrains Runtime (JBR)".equals(selected);

        jbrVariant.setVisible(isJbr);
        
        if (jbrVariantLabel != null) {
            jbrVariantLabel.setVisible(isJbr);
        }

        // Trigger parent layout update
        Container parent = jbrVariant.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void initJdkProviderListener() {
        if (jdkProvider != null) {
            jdkProvider.addItemListener(evt -> {
                if (evt.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                    updateJbrVariantVisibility();
                }
            });
        }
    }

    private void initSelectJarFileListener() {
        if (selectJarFile == null || parentFrame == null || projectDirectory == null) {
            return;
        }

        selectJarFile.addActionListener(evt -> {
            FileDialog dlg = new FileDialog(parentFrame, "Select jar file", FileDialog.LOAD);
            
            // Set initial directory
            if (jarFile.getText().isEmpty()) {
                dlg.setDirectory(projectDirectory.getAbsolutePath());
            } else {
                File currJar = new File(jarFile.getText());
                if (currJar.exists()) {
                    dlg.setDirectory(currJar.getAbsoluteFile().getParentFile().getAbsolutePath());
                } else {
                    dlg.setDirectory(projectDirectory.getAbsolutePath());
                }
            }
            
            // Filter for .jar files
            dlg.setFilenameFilter((dir, name) -> name.endsWith(".jar"));
            dlg.setVisible(true);
            
            File[] selected = dlg.getFiles();
            if (selected.length == 0) {
                return;
            }
            
            File absDirectory = projectDirectory.getAbsoluteFile();
            File selectedJarFile = selected[0].getAbsoluteFile();
            
            // Validate that jar is within project directory
            if (!selectedJarFile.getAbsolutePath().startsWith(absDirectory.getAbsolutePath())) {
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "Jar file must be in the same directory as the package.json file, or a subdirectory thereof",
                        "Invalid JAR Location",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            
            // Validate jar has Main-Class manifest entry
            PublishingCoordinator.ValidationResult jarValidation = new PublishingCoordinator(null, null).validateJar(selectedJarFile);
            if (!jarValidation.isValid()) {
                JOptionPane.showMessageDialog(
                        parentFrame,
                        jarValidation.getErrorMessage(),
                        "Invalid JAR File",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            
            // Update jarFile field with relative path
            String relativePath = selectedJarFile.getAbsolutePath().substring(absDirectory.getAbsolutePath().length() + 1);
            jarFile.setText(fromNativePath(relativePath));
            
            // Fire change event
            if (changeListener != null) {
                changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
            }
        });
    }

    {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        root = new JPanel();
        root.setLayout(new BorderLayout(0, 0));
        root.setPreferredSize(new Dimension(640, 500));
        root.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        panel1 = new JPanel();
        panel1.setLayout(new FormLayout("fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:d:grow", "center:d:noGrow,top:3dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
        root.add(panel1, BorderLayout.CENTER);
        final JLabel label1 = new JLabel();
        label1.setText("Name");
        CellConstraints cc = new CellConstraints();
        panel1.add(label1, cc.xy(1, 1));
        name = new JTextField();
        panel1.add(name, cc.xy(3, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JLabel label2 = new JLabel();
        label2.setText("Version");
        panel1.add(label2, cc.xy(1, 5));
        version = new JTextField();
        panel1.add(version, cc.xy(3, 5, CellConstraints.FILL, CellConstraints.DEFAULT));
        title = new JTextField();
        panel1.add(title, cc.xy(3, 7, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JLabel label3 = new JLabel();
        label3.setText("Title");
        panel1.add(label3, cc.xy(1, 7));
        final JLabel label4 = new JLabel();
        label4.setText("Author");
        panel1.add(label4, cc.xy(1, 9));
        author = new JTextField();
        panel1.add(author, cc.xy(3, 9, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JLabel label5 = new JLabel();
        label5.setText("Description");
        panel1.add(label5, cc.xy(1, 11));
        final JLabel label6 = new JLabel();
        label6.setText("License");
        panel1.add(label6, cc.xy(1, 13));
        license = new JTextField();
        panel1.add(license, cc.xy(3, 13, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, cc.xy(3, 11, CellConstraints.FILL, CellConstraints.FILL));
        description = new JTextArea();
        description.setRows(3);
        scrollPane1.setViewportView(description);
        final JLabel label7 = new JLabel();
        label7.setText("Path");
        panel1.add(label7, cc.xy(1, 3));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new BorderLayout(0, 0));
        panel1.add(panel2, cc.xy(3, 3));
        projectPath = new JLabel();
        projectPath.setText("...");
        panel2.add(projectPath, BorderLayout.CENTER);
        copyPath = new JButton();
        copyPath.setText("");
        copyPath.setToolTipText("Copy project path to clipboard");
        panel2.add(copyPath, BorderLayout.EAST);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new BorderLayout(0, 0));
        root.add(panel3, BorderLayout.NORTH);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new FormLayout("fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:d:grow,left:4dlu:noGrow,fill:max(d;4px):noGrow", "center:d:grow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
        root.add(panel4, BorderLayout.SOUTH);
        jarFile = new JTextField();
        panel4.add(jarFile, cc.xy(3, 3, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JLabel label8 = new JLabel();
        label8.setText("JAR File");
        panel4.add(label8, cc.xy(1, 3));
        final JLabel label9 = new JLabel();
        label9.setText("Java Version");
        panel4.add(label9, cc.xy(1, 5));
        javaVersion = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("25");
        defaultComboBoxModel1.addElement("24");
        defaultComboBoxModel1.addElement("21");
        defaultComboBoxModel1.addElement("20");
        defaultComboBoxModel1.addElement("17");
        defaultComboBoxModel1.addElement("11");
        defaultComboBoxModel1.addElement("8");
        javaVersion.setModel(defaultComboBoxModel1);
        panel4.add(javaVersion, cc.xy(3, 5));
        requiresJavaFX = new JCheckBox();
        requiresJavaFX.setText("Requires JavaFX");
        panel4.add(requiresJavaFX, cc.xy(3, 7));
        requiresFullJDK = new JCheckBox();
        requiresFullJDK.setText("Requires Full JDK");
        panel4.add(requiresFullJDK, cc.xy(3, 9));
        final JLabel label10 = new JLabel();
        label10.setText("JDK Provider");
        panel4.add(label10, cc.xy(1, 11));
        jdkProvider = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
        defaultComboBoxModel2.addElement("Auto (Recommended)");
        defaultComboBoxModel2.addElement("JetBrains Runtime (JBR)");
        jdkProvider.setModel(defaultComboBoxModel2);
        panel4.add(jdkProvider, cc.xy(3, 11));
        final JLabel label11 = new JLabel();
        label11.setText("JBR Variant");
        panel4.add(label11, cc.xy(1, 13));
        jbrVariant = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel3 = new DefaultComboBoxModel();
        defaultComboBoxModel3.addElement("Default");
        defaultComboBoxModel3.addElement("JCEF");
        jbrVariant.setModel(defaultComboBoxModel3);
        panel4.add(jbrVariant, cc.xy(3, 13));
        jbrVariantLabel = label11;
        final JLabel label12 = new JLabel();
        label12.setText("Homepage");
        panel4.add(label12, cc.xy(1, 15));
        selectJarFile = new JButton();
        selectJarFile.setText("Select...");
        panel4.add(selectJarFile, cc.xy(5, 3));
        homepage = new JTextField();
        panel4.add(homepage, cc.xy(3, 15, CellConstraints.FILL, CellConstraints.DEFAULT));
        verifyButton = new JButton();
        verifyButton.setText("Verify");
        panel4.add(verifyButton, cc.xy(5, 15));
        final JLabel label13 = new JLabel();
        label13.setText("Repository");
        panel4.add(label13, cc.xy(1, 17));
        repositoryUrl = new JTextField();
        panel4.add(repositoryUrl, cc.xy(3, 17, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JLabel label14 = new JLabel();
        label14.setText("Directory:");
        panel4.add(label14, cc.xy(3, 19));
        repositoryDirectory = new JTextField();
        panel4.add(repositoryDirectory, cc.xy(3, 21, CellConstraints.FILL, CellConstraints.DEFAULT));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new BorderLayout(10, 10));
        root.add(panel5, BorderLayout.WEST);
        panel5.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        icon = new JButton();
        icon.setText("");
        panel5.add(icon, BorderLayout.CENTER);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return root;
    }

}
