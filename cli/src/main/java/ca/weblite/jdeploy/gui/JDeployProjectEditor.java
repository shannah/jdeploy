package ca.weblite.jdeploy.gui;

import ca.weblite.tools.io.FileUtil;
import io.codeworth.panelmatic.PanelBuilder;
import io.codeworth.panelmatic.PanelMatic;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JDeployProjectEditor {

    private boolean modified;
    private JSONObject packageJSON;
    private File packageJSONFile;
    private MainFields mainFields;
    private ArrayList<DoctypeFields> doctypeFields;
    private ArrayList<LinkFields> linkFields;
    private JFrame frame;

    private class MainFields {
        private JTextField name, title, version, iconUrl;

        private JCheckBox javafx, jdk;
        private JComboBox javaVersion;
        private JButton icon, installSplash, splash;


    }

    private class DoctypeFields {
        private JTextField extension, mimetype;
        private JCheckBox editor, custom;

    }

    private class LinkFields {
        private JTextField url, label;
    }

    private static void addChangeListenerTo(JTextField textField, Runnable r) {
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        });
    }

    private File getIconFile() {
        return new File(packageJSONFile.getAbsoluteFile().getParentFile(), "icon.png");
    }

    private File getInstallSplashFile() {
        return new File(packageJSONFile.getAbsoluteFile().getParentFile(), "installsplash.png");
    }

    private File getSplashFile(String extension) {
        File absRoot = packageJSONFile.getAbsoluteFile().getParentFile();
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
        return new File(absRoot, "splash"+extension);
    }

    private static String getExtension(File f) {
        String name = f.getName();
        if (name.contains(".")) {
            return name.substring(name.lastIndexOf(".")+1);
        }
        return null;
    }


    public JDeployProjectEditor(File packageJSONFile, JSONObject packageJSON) {
        this.packageJSONFile = packageJSONFile;
        this.packageJSON = packageJSON;
    }

    private void initFrame() {
        frame = new JFrame("jDeploy");
        initMainFields(frame.getContentPane());
        initMenu();

    }


    public void show() {
        initFrame();
        String title = packageJSON.getString("name");
        if (packageJSON.has("jdeploy")) {
            JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
            if (jdeploy.has("title")) {
                title = jdeploy.getString("title");
            }
        }
        frame.setTitle(title);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void setModified() {
        modified = true;
    }
    private void initMainFields(Container cnt) {
        mainFields = new MainFields();
        mainFields.name = new JTextField();
        if (packageJSON.has("name")) {
            mainFields.name.setText(packageJSON.getString("name"));
        }
        addChangeListenerTo(mainFields.name, ()->{
            packageJSON.put("name", mainFields.name.getText());
            setModified();
        });
        if (!packageJSON.has("jdeploy")) {
            packageJSON.put("jdeploy", new JSONObject());
            setModified();
        }
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        mainFields.title = new JTextField();
        if (jdeploy.has("title")) {
            mainFields.title.setText(jdeploy.getString("title"));
        }
        addChangeListenerTo(mainFields.name, () -> {
            jdeploy.put("title", mainFields.title.getText());

            if (mainFields.title.getText().isEmpty()) {
                jdeploy.remove("title");
            }
            setModified();
        });

        mainFields.version = new JTextField();
        if (packageJSON.has("version")) {
            mainFields.version.setText(packageJSON.getString("version"));
        }
        addChangeListenerTo(mainFields.version, () -> {
            packageJSON.put("version", mainFields.version.getText());
            setModified();
        });
        mainFields.iconUrl = new JTextField();
        if (jdeploy.has("iconUrl")) {
            mainFields.iconUrl.setText(jdeploy.getString("iconUrl"));

        }
        addChangeListenerTo(mainFields.iconUrl, ()->{
            jdeploy.put("iconUrl", mainFields.iconUrl.getText());
            setModified();
        });

        mainFields.splash = new JButton();
        if (getSplashFile(null) != null && getSplashFile(null).exists()) {
            try {
                mainFields.splash.setIcon(new ImageIcon(Thumbnails.of(getSplashFile(null)).height(200).asBufferedImage()));
            } catch (Exception ex) {
                System.err.println("Failed to read splash image from "+getSplashFile(null));
                ex.printStackTrace(System.err);
            }

        }
        mainFields.splash.addActionListener(evt->{
            File selected = showFileChooser("Select Splash Image", "png", "jpg", "gif");
            if (selected == null) return;
            String extension = getExtension(selected);
            if (extension == null) {
                return;
            }
            try {
                if (getSplashFile(null).exists()) {
                    getSplashFile(null).delete();
                }
                FileUtils.copyFile(selected, getSplashFile(extension));
                mainFields.splash.setIcon(new ImageIcon(Thumbnails.of(getSplashFile(extension)).height(200).asBufferedImage()));
            } catch (Exception ex) {
                System.err.println("Error while copying icon file");
                ex.printStackTrace(System.err);
                showError("Failed to select icon", ex);

            }
        });

        mainFields.installSplash = new JButton();
        if (getInstallSplashFile().exists()) {
            try {
                mainFields.installSplash.setIcon(new ImageIcon(Thumbnails.of(getInstallSplashFile()).height(200).asBufferedImage()));
            } catch (Exception ex) {
                System.err.println("Failed to read splash image from "+getInstallSplashFile());
                ex.printStackTrace(System.err);
            }

        }
        mainFields.installSplash.addActionListener(evt->{
            File selected = showFileChooser("Select Install Splash Image", "png");
            if (selected == null) return;

            try {

                FileUtils.copyFile(selected, getInstallSplashFile());
                mainFields.installSplash.setIcon(new ImageIcon(Thumbnails.of(getInstallSplashFile()).height(200).asBufferedImage()));
            } catch (Exception ex) {
                System.err.println("Error while copying icon file");
                ex.printStackTrace(System.err);
                showError("Failed to select icon", ex);

            }
        });
        mainFields.icon = new JButton();
        if (getIconFile().exists()) {
            try {
                mainFields.icon.setIcon(new ImageIcon(Thumbnails.of(getIconFile()).size(128, 128).asBufferedImage()));
            } catch (Exception ex) {
                System.err.println("Failed to read splash image from "+getIconFile());
                ex.printStackTrace(System.err);
            }

        }
        mainFields.icon.addActionListener(evt->{
            File selected = showFileChooser("Select Icon Image", "png");
            if (selected == null) return;

            try {

                FileUtils.copyFile(selected, getIconFile());
                mainFields.icon.setIcon(new ImageIcon(Thumbnails.of(getIconFile()).size(128, 128).asBufferedImage()));
            } catch (Exception ex) {
                System.err.println("Error while copying icon file");
                ex.printStackTrace(System.err);
                showError("Failed to select icon", ex);

            }
        });

        mainFields.javafx = new JCheckBox("Requires JavaFX");
        if (jdeploy.has("javafx") && jdeploy.getBoolean("javafx")) {
            mainFields.javafx.setSelected(true);
        }
        mainFields.javafx.addActionListener(evt->{
            jdeploy.put("javafx", mainFields.javafx.isSelected());
            setModified();
        });
        mainFields.jdk = new JCheckBox("Requires Full JDK");
        if (jdeploy.has("jdk") && jdeploy.getBoolean("jdk")) {
            mainFields.jdk.setSelected(true);
        }
        mainFields.jdk.addActionListener(evt->{
            jdeploy.put("jdk", mainFields.jdk.isSelected());
            setModified();
        });
        mainFields.javaVersion = new JComboBox<String>(new String[]{"8", "11", "17"});
        if (jdeploy.has("javaVersion")) {
            mainFields.javaVersion.setSelectedItem(jdeploy.getString("javaVersion"));
        } else {
            mainFields.javaVersion.setSelectedItem("11");
        }
        mainFields.javaVersion.addItemListener(evt -> {
            jdeploy.put("javaVersion", mainFields.javaVersion.getSelectedItem());
            setModified();
        });



        JTabbedPane tabs = new JTabbedPane();

        JComponent detailsPanel = PanelMatic.begin()
                .add("Name", mainFields.name)
                .add("Version", mainFields.version)
                .add("Title", mainFields.title)
                .addHeader(PanelBuilder.HeaderLevel.H5, "Runtime Environment")
                .add("Java Version", mainFields.javaVersion)
                .add("", mainFields.javafx)
                .add("", mainFields.jdk)
                .get();
        JPanel detailWrapper = new JPanel();
        detailWrapper.setLayout(new BoxLayout(detailWrapper, BoxLayout.Y_AXIS));


        detailWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
        detailWrapper.setPreferredSize(new Dimension(640, 480));
        detailWrapper.setOpaque(false);
        detailsPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        detailWrapper.add(detailsPanel);



        tabs.addTab("Details", detailWrapper);

        JComponent imagesPanel = PanelMatic.begin()
                .add("Icon", mainFields.icon)
                .add("Install Splash Screen", mainFields.installSplash)
                .add("Splash Screen", mainFields.splash)
                .get();
        tabs.add("Images", imagesPanel);
        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(tabs, BorderLayout.CENTER);


    }


    private File showFileChooser(String title, String... extensions) {
        return showFileChooser(title, new HashSet<String>(Arrays.asList(extensions)));
    }
    private File showFileChooser(String title, Set<String> extensions) {
        FileDialog fd = new FileDialog(frame, title, FileDialog.LOAD);
        fd.setFilenameFilter(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (extensions == null) {
                    return true;
                }
                int pos = name.lastIndexOf(".");
                if (pos >= 0) {
                    String extension = name.substring(pos+1);
                    return extensions.contains(extension);
                }
                return false;
            }
        });
        fd.setVisible(true);
        File[] files = fd.getFiles();
        if (files == null || files.length == 0) return null;
        return files[0];
    }

    private void initMenu() {
        JMenuBar jmb = new JMenuBar();
        JMenu file = new JMenu("File");

        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke('S', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        save.addActionListener(evt-> handleSave());
        file.add(save);

        jmb.add(file);
        frame.setJMenuBar(jmb);
    }

    private void handleSave() {
        try {
            FileUtil.writeStringToFile(packageJSON.toString(), packageJSONFile);
        } catch (Exception ex) {
            System.err.println("Failed to save "+packageJSONFile+": "+ex.getMessage());
            ex.printStackTrace(System.err);
            showError("Failed to save package.json file. "+ex.getMessage(), ex);
        }
    }

    private void showError(String message, Throwable exception) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);

    }


}
