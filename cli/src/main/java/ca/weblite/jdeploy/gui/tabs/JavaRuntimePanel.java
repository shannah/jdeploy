package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.util.SwingUtils;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.File;

import static ca.weblite.jdeploy.PathUtil.fromNativePath;

public class JavaRuntimePanel extends JPanel {

    /**
     * Interface for validating JAR files.
     */
    @FunctionalInterface
    public interface JarValidator {
        ValidationResult validate(File jarFile);
    }

    /**
     * Result of JAR validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private final JFrame parentFrame;
    private final File projectDirectory;
    private final JarValidator jarValidator;

    private JPanel root;
    private JTextField jarFile;
    private JButton selectJarFile;
    private JComboBox<String> javaVersion;
    private JCheckBox requiresJavaFX;
    private JCheckBox requiresFullJDK;
    private JCheckBox singleton;
    private JComboBox<String> jdkProvider;
    private JComboBox<String> jbrVariant;
    private JLabel jbrVariantLabel;

    private ActionListener changeListener;

    public JavaRuntimePanel(JFrame parentFrame, File projectDirectory, JarValidator jarValidator) {
        this.parentFrame = parentFrame;
        this.projectDirectory = projectDirectory;
        this.jarValidator = jarValidator;

        initializeUI();
        initJdkProviderListener();
        initSelectJarFileListener();
        updateJbrVariantVisibility();
    }

    private void initializeUI() {
        root = new JPanel();
        root.setLayout(new FormLayout(
                "fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:d:grow,left:4dlu:noGrow,fill:max(d;4px):noGrow",
                "center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow," +
                "center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow," +
                "center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow," +
                "center:max(d;4px):noGrow"
        ));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        CellConstraints cc = new CellConstraints();

        // Row 1: JAR File
        JLabel jarLabel = new JLabel("JAR File");
        root.add(jarLabel, cc.xy(1, 1));

        jarFile = new JTextField();
        root.add(jarFile, cc.xy(3, 1, CellConstraints.FILL, CellConstraints.DEFAULT));

        selectJarFile = new JButton("Select...");
        root.add(selectJarFile, cc.xy(5, 1));

        // Row 3: Java Version
        JLabel javaVersionLabel = new JLabel("Java Version");
        root.add(javaVersionLabel, cc.xy(1, 3));

        javaVersion = new JComboBox<>(new String[]{"25", "24", "21", "20", "17", "11", "8"});
        root.add(javaVersion, cc.xy(3, 3));

        // Row 5: Requires JavaFX
        requiresJavaFX = new JCheckBox("Requires JavaFX");
        root.add(requiresJavaFX, cc.xy(3, 5));

        // Row 7: Requires Full JDK
        requiresFullJDK = new JCheckBox("Requires Full JDK");
        root.add(requiresFullJDK, cc.xy(3, 7));

        // Row 9: JDK Provider
        JLabel jdkProviderLabel = new JLabel("JDK Provider");
        root.add(jdkProviderLabel, cc.xy(1, 9));

        jdkProvider = new JComboBox<>(new String[]{"Auto (Recommended)", "JetBrains Runtime (JBR)"});
        jdkProvider.setToolTipText("Auto: Automatically selects the best JDK provider for your platform (Zulu, Adoptium, or Liberica). JBR: Use JetBrains Runtime for applications requiring JCEF or enhanced rendering.");
        root.add(jdkProvider, cc.xy(3, 9));

        // Row 11: JBR Variant
        jbrVariantLabel = new JLabel("JBR Variant");
        root.add(jbrVariantLabel, cc.xy(1, 11));

        jbrVariant = new JComboBox<>(new String[]{"Default", "JCEF"});
        jbrVariant.setToolTipText("JBR variant to use. Default uses standard or standard+SDK based on whether JDK is required. JCEF includes Chromium Embedded Framework for embedded browsers.");
        root.add(jbrVariant, cc.xy(3, 11));

        // Row 13: Singleton Application
        singleton = new JCheckBox("Singleton Application");
        singleton.setToolTipText("Only allow one instance of this application to run at a time. " +
            "Additional launches will forward files to the existing instance.");
        root.add(singleton, cc.xy(3, 13));
    }

    private void initJdkProviderListener() {
        jdkProvider.addItemListener(evt -> {
            if (evt.getStateChange() == ItemEvent.SELECTED) {
                updateJbrVariantVisibility();
            }
        });
    }

    private void initSelectJarFileListener() {
        selectJarFile.addActionListener(evt -> {
            FileDialog dlg = new FileDialog(parentFrame, "Select jar file", FileDialog.LOAD);

            // Set initial directory
            if (jarFile.getText().isEmpty()) {
                if (projectDirectory != null) {
                    dlg.setDirectory(projectDirectory.getAbsolutePath());
                }
            } else {
                File currJar = new File(jarFile.getText());
                if (currJar.exists()) {
                    dlg.setDirectory(currJar.getAbsoluteFile().getParentFile().getAbsolutePath());
                } else if (projectDirectory != null) {
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

            File selectedJarFile = selected[0].getAbsoluteFile();

            // Validate that jar is within project directory
            if (projectDirectory != null) {
                File absDirectory = projectDirectory.getAbsoluteFile();
                if (!selectedJarFile.getAbsolutePath().startsWith(absDirectory.getAbsolutePath())) {
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            "Jar file must be in the same directory as the package.json file, or a subdirectory thereof",
                            "Invalid JAR Location",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
            }

            // Validate jar has Main-Class manifest entry
            if (jarValidator != null) {
                ValidationResult jarValidation = jarValidator.validate(selectedJarFile);
                if (!jarValidation.isValid()) {
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            jarValidation.getErrorMessage(),
                            "Invalid JAR File",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
            }

            // Update jarFile field with relative path
            if (projectDirectory != null) {
                File absDirectory = projectDirectory.getAbsoluteFile();
                String relativePath = selectedJarFile.getAbsolutePath().substring(absDirectory.getAbsolutePath().length() + 1);
                jarFile.setText(fromNativePath(relativePath));
            } else {
                jarFile.setText(selectedJarFile.getAbsolutePath());
            }

            // Fire change event
            fireChangeEvent();
        });
    }

    private void updateJbrVariantVisibility() {
        String selected = (String) jdkProvider.getSelectedItem();
        boolean isJbr = "JetBrains Runtime (JBR)".equals(selected);

        jbrVariant.setVisible(isJbr);
        jbrVariantLabel.setVisible(isJbr);

        Container parent = root.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    public JPanel getRoot() {
        return root;
    }

    public void load(JSONObject jdeploy) {
        if (jdeploy == null) {
            return;
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

        singleton.setSelected(jdeploy.optBoolean("singleton", false));

        updateJbrVariantVisibility();
    }

    public void save(JSONObject jdeploy) {
        if (jdeploy == null) {
            return;
        }

        // Save jar
        String jarText = jarFile.getText().trim();
        if (!jarText.isEmpty()) {
            jdeploy.put("jar", jarText);
        } else {
            jdeploy.remove("jar");
        }

        // Save javafx (only if true)
        if (requiresJavaFX.isSelected()) {
            jdeploy.put("javafx", true);
        } else {
            jdeploy.remove("javafx");
        }

        // Save jdk (only if true)
        if (requiresFullJDK.isSelected()) {
            jdeploy.put("jdk", true);
        } else {
            jdeploy.remove("jdk");
        }

        // Save javaVersion
        Object selectedVersion = javaVersion.getSelectedItem();
        if (selectedVersion != null && !selectedVersion.toString().isEmpty()) {
            jdeploy.put("javaVersion", selectedVersion.toString());
        }

        // Save jdkProvider and jbrVariant
        Object selectedProvider = jdkProvider.getSelectedItem();
        boolean isJbrProvider = "JetBrains Runtime (JBR)".equals(selectedProvider);

        if (isJbrProvider) {
            jdeploy.put("jdkProvider", "jbr");
            // Only save jbrVariant if JDK Provider is JBR
            Object selectedVariant = jbrVariant.getSelectedItem();
            if ("JCEF".equals(selectedVariant)) {
                jdeploy.put("jbrVariant", "jcef");
            } else {
                jdeploy.remove("jbrVariant");
            }
        } else {
            jdeploy.remove("jdkProvider");
            jdeploy.remove("jbrVariant");
        }

        // Save singleton (only if true)
        if (singleton.isSelected()) {
            jdeploy.put("singleton", true);
        } else {
            jdeploy.remove("singleton");
        }
    }

    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;

        // Wire change listener to all fields
        SwingUtils.addChangeListenerTo(jarFile, this::fireChangeEvent);

        requiresJavaFX.addItemListener(evt -> fireChangeEvent());
        requiresFullJDK.addItemListener(evt -> fireChangeEvent());
        singleton.addItemListener(evt -> fireChangeEvent());
        javaVersion.addItemListener(evt -> fireChangeEvent());
        jdkProvider.addItemListener(evt -> {
            if (evt.getStateChange() == ItemEvent.SELECTED) {
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

    // Getters for testing
    public JTextField getJarFile() {
        return jarFile;
    }

    public JComboBox<String> getJavaVersion() {
        return javaVersion;
    }

    public JCheckBox getRequiresJavaFX() {
        return requiresJavaFX;
    }

    public JCheckBox getRequiresFullJDK() {
        return requiresFullJDK;
    }

    public JCheckBox getSingleton() {
        return singleton;
    }

    public JComboBox<String> getJdkProvider() {
        return jdkProvider;
    }

    public JComboBox<String> getJbrVariant() {
        return jbrVariant;
    }

    public JButton getSelectJarFileButton() {
        return selectJarFile;
    }
}
