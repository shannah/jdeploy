package ca.weblite.jdeploy.gui.tabs;

import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Objects;

/**
 * Panel for managing splash screen images (splash.png and installsplash.png).
 * Self-contained UI construction, load/save, and change notification.
 * 
 * Follows the panel pattern: getRoot(), load(JSONObject), save(JSONObject), addChangeListener()
 */
public class SplashScreensPanel extends JPanel {
    private JButton splashButton;
    private JButton installSplashButton;
    private final File projectDirectory;
    private ActionListener changeListener;
    private JFrame parentFrame;

    public SplashScreensPanel(File projectDirectory) {
        this.projectDirectory = Objects.requireNonNull(projectDirectory, "projectDirectory");
        initializeUI();
    }

    public SplashScreensPanel(File projectDirectory, JFrame parentFrame) {
        this.projectDirectory = Objects.requireNonNull(projectDirectory, "projectDirectory");
        this.parentFrame = parentFrame;
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BorderLayout());
        
        JComponent imagesPanel = createImagesPanel();
        JPanel imagesPanelWrapper = new JPanel();
        imagesPanelWrapper.setLayout(new BorderLayout());
        imagesPanelWrapper.setOpaque(false);
        imagesPanelWrapper.add(imagesPanel, BorderLayout.CENTER);
        
        add(imagesPanelWrapper, BorderLayout.CENTER);
    }

    private JComponent createImagesPanel() {
        splashButton = new JButton();
        installSplashButton = new JButton();
        
        setupSplashButton();
        setupInstallSplashButton();
        
        // Simple vertical layout for the two image buttons
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        
        JPanel installSplashContainer = new JPanel();
        installSplashContainer.setOpaque(false);
        installSplashContainer.setLayout(new FlowLayout(FlowLayout.LEFT));
        installSplashContainer.add(new JLabel("Install Splash Screen"));
        installSplashContainer.add(installSplashButton);
        
        JPanel splashContainer = new JPanel();
        splashContainer.setOpaque(false);
        splashContainer.setLayout(new FlowLayout(FlowLayout.LEFT));
        splashContainer.add(new JLabel("Splash Screen"));
        splashContainer.add(splashButton);
        
        panel.add(installSplashContainer);
        panel.add(Box.createVerticalStrut(10));
        panel.add(splashContainer);
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }

    private void setupSplashButton() {
        File splashFile = getSplashFile(null);
        if (splashFile != null && splashFile.exists()) {
            try {
                splashButton.setIcon(
                        new ImageIcon(Thumbnails.of(splashFile).height(200).asBufferedImage())
                );
            } catch (Exception ex) {
                System.err.println("Failed to read splash image from " + splashFile);
                ex.printStackTrace(System.err);
                splashButton.setText("Select splash screen image...");
            }
        } else {
            splashButton.setText("Select splash screen image...");
        }
        
        splashButton.addActionListener(evt -> handleSelectSplashImage());
    }

    private void setupInstallSplashButton() {
        File installSplashFile = getInstallSplashFile();
        if (installSplashFile.exists()) {
            try {
                installSplashButton.setIcon(
                        new ImageIcon(Thumbnails.of(installSplashFile).height(200).asBufferedImage())
                );
            } catch (Exception ex) {
                System.err.println("Failed to read install splash image from " + installSplashFile);
                ex.printStackTrace(System.err);
                installSplashButton.setText("Select install splash screen image...");
            }
        } else {
            installSplashButton.setText("Select install splash screen image...");
        }
        
        installSplashButton.addActionListener(evt -> handleSelectInstallSplashImage());
    }

    private void handleSelectSplashImage() {
        File selected = showFileChooser("Select Splash Image", "png", "jpg", "gif");
        if (selected == null) return;
        
        String extension = getExtension(selected);
        if (extension == null) {
            return;
        }
        
        try {
            File targetFile = getSplashFile(extension);
            File oldSplashFile = getSplashFile(null);
            if (oldSplashFile != null && oldSplashFile.exists() && !oldSplashFile.equals(targetFile)) {
                oldSplashFile.delete();
            }
            FileUtils.copyFile(selected, targetFile);
            splashButton.setText("");
            splashButton.setIcon(
                    new ImageIcon(Thumbnails.of(targetFile).height(200).asBufferedImage())
            );
            fireChangeEvent();
        } catch (Exception ex) {
            System.err.println("Error while copying splash file");
            ex.printStackTrace(System.err);
            showError("Failed to select splash image", ex);
        }
    }

    private void handleSelectInstallSplashImage() {
        File selected = showFileChooser("Select Install Splash Image", "png");
        if (selected == null) return;
        
        try {
            File targetFile = getInstallSplashFile();
            FileUtils.copyFile(selected, targetFile);
            installSplashButton.setText("");
            installSplashButton.setIcon(
                    new ImageIcon(Thumbnails.of(targetFile).height(200).asBufferedImage())
            );
            fireChangeEvent();
        } catch (Exception ex) {
            System.err.println("Error while copying install splash file");
            ex.printStackTrace(System.err);
            showError("Failed to select install splash image", ex);
        }
    }

    private File getSplashFile(String extension) {
        File absRoot = projectDirectory;
        if (extension == null) {
            File out = new File(absRoot, "splash.png");
            if (out.exists()) return out;
            out = new File(absRoot, "splash.jpg");
            if (out.exists()) return out;
            out = new File(absRoot, "splash.gif");
            if (out.exists()) return out;
            return new File(absRoot, "splash.png");
        }
        if (extension.charAt(0) != '.') {
            extension = "." + extension;
        }
        return new File(absRoot, "splash" + extension);
    }

    private File getInstallSplashFile() {
        return new File(projectDirectory, "installsplash.png");
    }

    private static String getExtension(File f) {
        String name = f.getName();
        if (name.contains(".")) {
            return name.substring(name.lastIndexOf(".") + 1);
        }
        return null;
    }

    private File showFileChooser(String title, String... extensions) {
        JFileChooser chooser = new JFileChooser(projectDirectory);
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        javax.swing.filechooser.FileNameExtensionFilter filter =
                new javax.swing.filechooser.FileNameExtensionFilter(
                        "Image files (" + String.join(", ", extensions) + ")",
                        extensions
                );
        chooser.setFileFilter(filter);
        
        int result = chooser.showOpenDialog(parentFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private void showError(String message, Throwable exception) {
        JPanel dialogComponent = new JPanel();
        dialogComponent.setLayout(new BoxLayout(dialogComponent, BoxLayout.Y_AXIS));
        dialogComponent.setOpaque(false);
        dialogComponent.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        dialogComponent.add(new JLabel(
                "<html><p style='width:400px'>" + message + "</p></html>"
        ));
        
        JOptionPane.showMessageDialog(
                parentFrame,
                dialogComponent,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
        
        if (exception != null) {
            exception.printStackTrace(System.err);
        }
    }

    public JPanel getRoot() {
        return this;
    }

    /**
     * Loads splash screen configuration from the jdeploy JSONObject.
     * Currently a no-op as splash screens are stored as files, not JSON config,
     * but included for consistency with the panel pattern.
     */
    public void load(JSONObject jdeploy) {
        // Splash screens are file-based, not JSON-config based
        // Refresh UI to reflect current files
        setupSplashButton();
        setupInstallSplashButton();
    }

    /**
     * Saves splash screen configuration to the jdeploy JSONObject.
     * Currently a no-op as splash screens are stored as files, not JSON config,
     * but included for consistency with the panel pattern.
     */
    public void save(JSONObject jdeploy) {
        // Splash screens are file-based, not JSON-config based
        // No action needed
    }

    /**
     * Registers a change listener to be notified when splash screen images change.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }
}
