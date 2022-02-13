package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.MD5;
import ca.weblite.tools.platform.Platform;
import com.sun.nio.file.SensitivityWatchEventModifier;
import io.codeworth.panelmatic.PanelBuilder;
import io.codeworth.panelmatic.PanelMatic;
import io.codeworth.panelmatic.util.Groupings;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import static ca.weblite.jdeploy.PathUtil.fromNativePath;
import static ca.weblite.jdeploy.PathUtil.toNativePath;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class JDeployProjectEditor {

    private boolean modified;
    private JSONObject packageJSON;
    private File packageJSONFile;
    private String packageJSONMD5;
    private MainFields mainFields;
    private ArrayList<DoctypeFields> doctypeFields;
    private ArrayList<LinkFields> linkFields;
    private JFrame frame;
    private WatchService watchService;
    private boolean pollWatchService = true;
    private boolean processingPackageJSONChange = false;

    private class MainFields {
        private JTextField name, title, version, iconUrl, jar, urlSchemes, author,
                repository, repositoryDirectory, command, license, homepage;
        private JTextArea description;

        private JCheckBox javafx, jdk;
        private JComboBox javaVersion;
        private JButton icon, installSplash, splash, selectJar;


    }




    private void handlePackageJSONChangedOnFileSystem()  {
        if (processingPackageJSONChange) {
            return;
        }
        processingPackageJSONChange = true;
        try {
            String hash = MD5.getMD5Checksum(packageJSONFile);
            if (hash.equals(packageJSONMD5)) {
                // file hasn't changed
                return;
            }
            if (!modified) {
                // We haven't made any changes yet, so just reload it.
                reloadPackageJSON();
                return;
            }
            int result = JOptionPane.showConfirmDialog(frame,
                    "The package.json file has been modified.  Would you like to reload it?  Unsaved changes will be lost",
                    "Reload package.json?",
                    JOptionPane.YES_NO_OPTION);

            if (result == JOptionPane.YES_OPTION) {
                reloadPackageJSON();
            }
        } catch (Exception ex) {
            System.err.println("A problem occurred while handling a file system change to the package.json file.");
            ex.printStackTrace(System.err);
        } finally {
            processingPackageJSONChange = false;
        }
    }

    private void reloadPackageJSON() throws IOException {
        packageJSON = new JSONObject(FileUtils.readFileToString(packageJSONFile, "UTF-8"));
        packageJSONMD5 = MD5.getMD5Checksum(packageJSONFile);
        clearModified();
        frame.getContentPane().removeAll();
        initMainFields(frame.getContentPane());
        frame.revalidate();

    }



    private void watchPackageJSONForChanges() throws IOException, InterruptedException {
        watchService = FileSystems.getDefault().newWatchService();

        Path path = packageJSONFile.getAbsoluteFile().getParentFile().toPath();

        path.register(watchService,new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_MODIFY},  SensitivityWatchEventModifier.HIGH);

        while (pollWatchService) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    String contextString = "" + event.context();
                    if ("package.json".equals(contextString)) {
                        EventQueue.invokeLater(() -> handlePackageJSONChangedOnFileSystem());
                    }
                }

                pollWatchService = key.reset();
            } catch (ClosedWatchServiceException cwse) {
                pollWatchService = false;
            }

        }
    }

    private class DoctypeFields {
        private JTextField extension, mimetype;
        private JCheckBox editor, custom;

    }

    private class LinkFields {
        private JTextField url, label;
    }



    private static void addChangeListenerTo(JTextComponent textField, Runnable r) {
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
        try {
            this.packageJSONMD5 = MD5.getMD5Checksum(this.packageJSONFile);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to get MD5 checksum for packageJSON file.  This is used for the watch service.");
        }
    }

    private void initFrame() {
        frame = new JFrame("jDeploy");
        Thread watchThread = new Thread(()->{
            try {
                watchPackageJSONForChanges();
            } catch (Exception ex) {
                System.err.println("A problem occurred while setting up watch service on package.json.  Disabling watch service.");
                ex.printStackTrace(System.err);
            }
        });
        watchThread.start();
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClosing();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                if (watchService != null) {
                    try {
                        watchService.close();
                        watchService = null;
                    } catch (Exception ex){}
                }
            }
        });

        initMainFields(frame.getContentPane());
        initMenu();

    }


    private void handleClosing() {
        if (modified) {
            int answer = showWarningUnsavedChangesMessage();
            switch (answer) {
                case JOptionPane.YES_OPTION:
                    //System.out.println("Save and Quit");
                    handleSave();
                    frame.dispose();
                    break;

                case JOptionPane.NO_OPTION:
                    //System.out.println("Don't Save and Quit");
                    frame.dispose();
                    break;

                case JOptionPane.CANCEL_OPTION:
                    //System.out.println("Don't Quit");
                    break;
            }
        }else {
            frame.dispose();
        }
    }

    private int showWarningUnsavedChangesMessage() {
        String[] buttonLabels = new String[] {"Yes", "No", "Cancel"};
        String defaultOption = buttonLabels[0];
        Icon icon = null;

        return JOptionPane.showOptionDialog(frame,
                "There's still something unsaved.\n" +
                        "Do you want to save before exiting?",
                "Warning",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                icon,
                buttonLabels,
                defaultOption);
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

    private void clearModified() {
        modified = false;
    }

    private void createDocTypeRow(JSONArray docTypes, int index, Box doctypesPanel) {
        DoctypeFields rowDocTypeFields = new DoctypeFields();
        JSONObject row = docTypes.getJSONObject(index);
        JPanel rowPanel = new JPanel();
        rowPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        rowPanel.setOpaque(false);
        initDoctypeFields(rowDocTypeFields, row, rowPanel);
        JPanel rowWrapper = new JPanel();
        rowWrapper.setOpaque(false);

        rowWrapper.setLayout(new BorderLayout());
        rowWrapper.add(rowPanel, BorderLayout.CENTER);

        JButton removeRow = new JButton(FontIcon.of(Material.DELETE));
        removeRow.setOpaque(false);

        removeRow.addActionListener(evt->{
            int rowIndex = -1;
            int l = docTypes.length();
            for (int j=0; j<l; j++) {
                if (docTypes.getJSONObject(j) == row) {
                    rowIndex = j;
                }
            }
            if (rowIndex >= 0) {
                docTypes.remove(rowIndex);
            }
            doctypesPanel.remove(rowWrapper);
            doctypesPanel.revalidate();
        });
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(removeRow);
        rowWrapper.add(buttons, BorderLayout.EAST);
        Component filler = null;
        if (doctypesPanel.getComponentCount() > 0) {
            filler = doctypesPanel.getComponent(doctypesPanel.getComponentCount()-1);
            doctypesPanel.remove(filler);
        } else {
            filler = new Box.Filler(
                    new Dimension(0, 0),
                    new Dimension(0, 0),
                    new Dimension(1000, 1000)
            );
            ((JComponent)filler).setOpaque(false);
            ((JComponent)filler).setBackground(new Color(255, 0, 0, 255));
        }
        rowWrapper.setMaximumSize(new Dimension(1000, rowWrapper.getPreferredSize().height));
        rowWrapper.setBorder(new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        doctypesPanel.add(rowWrapper);
        doctypesPanel.add(filler);


    }



    private void initDoctypeFields(DoctypeFields fields, JSONObject docTypeRow, Container cnt) {
        fields.extension = new JTextField();
        fields.extension.setColumns(8);
        fields.extension.setMaximumSize(new Dimension(fields.extension.getPreferredSize().width, fields.extension.getPreferredSize().height));
        fields.extension.setToolTipText("Enter the file extension.  E.g. txt");
        if (docTypeRow.has("extension")) {
            fields.extension.setText(docTypeRow.getString("extension"));
        }
        addChangeListenerTo(fields.extension, ()->{
            String extVal = fields.extension.getText();
            if (extVal.startsWith(".")) {
                extVal = extVal.substring(1);
                fields.extension.setText(extVal);
                docTypeRow.put("extension", fields.extension.getText());
                setModified();
            } else {
                docTypeRow.put("extension", fields.extension.getText());
                setModified();
            }
        });

        fields.mimetype = new JTextField();
        if (docTypeRow.has("mimetype")) {
            fields.mimetype.setText(docTypeRow.getString("mimetype"));
        }
        addChangeListenerTo(fields.mimetype, ()->{
            docTypeRow.put("mimetype", fields.mimetype.getText());
            setModified();
        });
        fields.editor = new JCheckBox("Editor");
        fields.editor.setToolTipText("Check this box if the app can edit this document type.  Leave unchecked if it can only view documents of this type");
        if (docTypeRow.has("editor") && docTypeRow.getBoolean("editor")) {
            fields.editor.setSelected(true);
        }
        fields.editor.addActionListener(evt->{
            docTypeRow.put("editor", fields.editor.isSelected());
            setModified();
        });

        fields.custom = new JCheckBox("Custom");
        fields.custom.setToolTipText("Check this box if this is a custom extension for your app, and should be added to the system mimetypes registry.");
        if (docTypeRow.has("custom") && docTypeRow.getBoolean("custom")) {
            fields.custom.setSelected(true);
        }
        fields.custom.addActionListener(evt->{
            docTypeRow.put("custom", fields.custom.isSelected());
            setModified();
        });


        cnt.removeAll();
        cnt.setLayout(new BoxLayout(cnt, BoxLayout.Y_AXIS));
        JComponent pmPane = PanelMatic.begin()
                .add(Groupings.lineGroup(
                        new JLabel("Extension:"), fields.extension,
                        (JComponent)Box.createHorizontalStrut(10),
                        new JLabel("Mimetype:"), fields.mimetype))
                .add(Groupings.lineGroup(fields.editor, fields.custom)).get();
        setOpaqueRecursive(pmPane, false);


        cnt.add(pmPane);
    }

    private void setOpaqueRecursive(JComponent cnt, boolean opaque) {
        cnt.setOpaque(opaque);
        if (cnt.getComponentCount() > 0) {
            int len = cnt.getComponentCount();
            for (int i=0; i<len; i++) {
                Component cmp = cnt.getComponent(i);
                if (cmp instanceof JComponent) {
                    setOpaqueRecursive((JComponent)cmp, opaque);
                }
            }
        }
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

        mainFields.author = new JTextField();
        if (packageJSON.has("author")) {
            Object authorO = packageJSON.get("author");
            String authorString = "";
            if (authorO instanceof JSONObject) {
                JSONObject authorObj = (JSONObject)authorO;
                if (authorObj.has("name")) {
                    authorString += authorObj.getString("name");
                }
                if (authorObj.has("email")) {
                    authorString += " <" + authorObj.getString("email")+">";
                }
                if (authorObj.has("url")) {
                    authorString += " ("+authorObj.getString("url")+")";
                }
            } else if (authorO instanceof String){
                authorString = (String)authorO;
            }
            mainFields.author.setText(authorString);
        }
        addChangeListenerTo(mainFields.author, ()->{
            packageJSON.put("author", mainFields.author.getText());
            setModified();
        });

        mainFields.description = new JTextArea();
        mainFields.description.setLineWrap(true);
        mainFields.description.setWrapStyleWord(true);
        mainFields.description.setRows(4);
        if (packageJSON.has("description")) {
            mainFields.description.setText(packageJSON.getString("description"));
        }
        addChangeListenerTo(mainFields.description, ()->{
            packageJSON.put("description", mainFields.description.getText());
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
        addChangeListenerTo(mainFields.title, () -> {
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

        mainFields.repository = new JTextField();
        mainFields.repository.setColumns(30);
        mainFields.repository.setMinimumSize(new Dimension(100, mainFields.repository.getPreferredSize().height));
        mainFields.repositoryDirectory = new JTextField();
        mainFields.repositoryDirectory.setMinimumSize(new Dimension(100, mainFields.repositoryDirectory.getPreferredSize().height));
        mainFields.repositoryDirectory.setColumns(20);
        if (packageJSON.has("repository")) {
            Object repoVal = packageJSON.get("repository");
            if (repoVal instanceof JSONObject) {
                JSONObject repoObj = (JSONObject)repoVal;
                String type = repoObj.has("type") ? repoObj.getString("type") : "git";
                String url = repoObj.has("url") ? repoObj.getString("url") : "";
                String directory = repoObj.has("directory") ? repoObj.getString("directory") : "";
                mainFields.repository.setText(url);
                mainFields.repositoryDirectory.setText(directory);

            } else if (repoVal instanceof String) {
                mainFields.repository.setText((String)repoVal);
                mainFields.repositoryDirectory.setText("");
            }
        }
        Runnable onRepoChange = ()-> {
            Object repoVal = packageJSON.get("repository");
            JSONObject repoObj = (repoVal instanceof JSONObject) ? (JSONObject) repoVal : new JSONObject();
            repoObj.put("url", mainFields.repository.getText());
            repoObj.put("directory", mainFields.repositoryDirectory.getText());
            setModified();
        };
        addChangeListenerTo(mainFields.repository, onRepoChange);
        addChangeListenerTo(mainFields.repositoryDirectory, onRepoChange);

        mainFields.license = new JTextField();
        if (packageJSON.has("license")) {
            mainFields.license.setText(packageJSON.getString("license"));
        }
        addChangeListenerTo(mainFields.license, ()->{
            packageJSON.put("license", mainFields.license.getText());
            setModified();
        });

        boolean includeCommandField = true;
        mainFields.command = new JTextField();
        if (packageJSON.has("bin")) {
            JSONObject bin = packageJSON.getJSONObject("bin");
            if (bin.keySet().size() > 1) {
                includeCommandField = false;
            } else if (bin.keySet().size() == 1){
                mainFields.command.setText(bin.keySet().iterator().next());
            }
        }
        addChangeListenerTo(mainFields.command, ()->{
            JSONObject bin = new JSONObject();
            bin.put(mainFields.command.getText(), "jdeploy-bundle/jdeploy.js");
            packageJSON.put("bin", bin);
            setModified();
        });

        mainFields.homepage = new JTextField();
        if (packageJSON.has("homepage")) {
            mainFields.homepage.setText(packageJSON.getString("homepage"));
        }
        addChangeListenerTo(mainFields.homepage, ()->{
            packageJSON.put("homepage", mainFields.homepage.getText());
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

        } else {
            mainFields.splash.setText("Select splashscreen image...");
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
                mainFields.splash.setText("");
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

        } else {
            mainFields.installSplash.setText("Select install splashscreen image...");
        }
        mainFields.installSplash.addActionListener(evt->{
            File selected = showFileChooser("Select Install Splash Image", "png");
            if (selected == null) return;

            try {

                FileUtils.copyFile(selected, getInstallSplashFile());
                mainFields.installSplash.setText("");
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

        } else {
            mainFields.icon.setText("Select icon...");
        }
        mainFields.icon.addActionListener(evt->{
            File selected = showFileChooser("Select Icon Image", "png");
            if (selected == null) return;

            try {

                FileUtils.copyFile(selected, getIconFile());
                mainFields.icon.setText("");
                mainFields.icon.setIcon(new ImageIcon(Thumbnails.of(getIconFile()).size(128, 128).asBufferedImage()));
            } catch (Exception ex) {
                System.err.println("Error while copying icon file");
                ex.printStackTrace(System.err);
                showError("Failed to select icon", ex);

            }
        });

        mainFields.jar = new JTextField();
        mainFields.jar.setColumns(30);
        if (jdeploy.has("jar")) {
            mainFields.jar.setText(jdeploy.getString("jar"));
        }
        addChangeListenerTo(mainFields.jar, ()->{
            jdeploy.put("jar", mainFields.jar.getText());
            setModified();
        });

        mainFields.selectJar = new JButton("Select...");
        mainFields.selectJar.addActionListener(evt->{
            FileDialog dlg = new FileDialog(frame, "Select jar file", FileDialog.LOAD);
            if (mainFields.jar.getText().isEmpty()) {
                dlg.setDirectory(packageJSONFile.getAbsoluteFile().getParentFile().getAbsolutePath());
            } else {
                File currJar = new File(mainFields.jar.getText());
                if (currJar.exists()) {
                    dlg.setDirectory(currJar.getAbsoluteFile().getParentFile().getAbsolutePath());
                } else {
                    dlg.setDirectory(packageJSONFile.getAbsoluteFile().getParentFile().getAbsolutePath());
                }
            }
            dlg.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jar");
                }
            });
            dlg.setVisible(true);
            File[] selected = dlg.getFiles();
            if (selected.length == 0) return;
            File absDirectory = packageJSONFile.getAbsoluteFile().getParentFile();
            File jarFile = selected[0].getAbsoluteFile();
            if (!jarFile.getAbsolutePath().startsWith(absDirectory.getAbsolutePath())) {
                showError("Jar file must be in same directory as the package.json file, or a subdirectory thereof", null);
                return;
            }
            try {
                validateJar(jarFile);
            } catch (ValidationException ex) {
                showError(ex.getMessage(), ex);
                return;
            }
            mainFields.jar.setText(fromNativePath(jarFile.getAbsolutePath().substring(absDirectory.getAbsolutePath().length()+1)));
            jdeploy.put("jar", mainFields.jar.getText());
            setModified();

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

        mainFields.urlSchemes = new JTextField();
        mainFields.urlSchemes.setToolTipText("Comma-delimited list of URL schemes to associate with your application.");
        if (jdeploy.has("urlSchemes")) {
            JSONArray schemes = jdeploy.getJSONArray("urlSchemes");
            int len = schemes.length();
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<len; i++) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(schemes.getString(i).trim());
            }
            mainFields.urlSchemes.setText(sb.toString());
        }
        addChangeListenerTo(mainFields.urlSchemes, ()->{
            String[] schemesArr = mainFields.urlSchemes.getText().split(",");
            int len = schemesArr.length;
            JSONArray arr = new JSONArray();
            int idx = 0;
            for (int i=0; i<len; i++) {
                if (schemesArr[i].trim().isEmpty()) {
                    continue;
                }
                arr.put(idx++, schemesArr[i].trim());
            }
            jdeploy.put("urlSchemes", arr);
            setModified();
        });

        Box doctypesPanel = Box.createVerticalBox();

        doctypesPanel.setOpaque(false);
        //doctypesPanel.setLayout(new BoxLayout(doctypesPanel, BoxLayout.Y_AXIS));
        JScrollPane doctypesPanelScroller = new JScrollPane(doctypesPanel);
        doctypesPanelScroller.setOpaque(false);
        doctypesPanelScroller.getViewport().setOpaque(false);

        doctypesPanelScroller.setBorder(new EmptyBorder(0, 0, 0, 0));


        if (jdeploy.has("documentTypes")) {
            JSONArray docTypes = jdeploy.getJSONArray("documentTypes");
            int len = docTypes.length();
            for (int i=0; i<len; i++) {
                createDocTypeRow(docTypes, i, doctypesPanel);
            }
        }
        //doctypesPanel.setAlignmentY(Component.TOP_ALIGNMENT);


        JButton addDocType = new JButton(FontIcon.of(Material.ADD));
        addDocType.setToolTipText("Add document type association");
        addDocType.addActionListener(evt->{
            JSONArray docTypes = jdeploy.getJSONArray("documentTypes");
            JSONObject row = new JSONObject();
            docTypes.put(docTypes.length(), row);
            createDocTypeRow(docTypes, docTypes.length()-1, doctypesPanel);
            doctypesPanel.revalidate();
        });

        JPanel doctypesPanelWrapper = new JPanel();
        doctypesPanelWrapper.setOpaque(false);
        doctypesPanelWrapper.setLayout(new BorderLayout());
        doctypesPanelWrapper.add(doctypesPanelScroller, BorderLayout.CENTER);
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setOpaque(false);
        tb.add(addDocType);
        JPanel doctypesTop = new JPanel();
        doctypesTop.setOpaque(false);
        doctypesTop.setLayout(new BorderLayout());
        doctypesTop.add(tb, BorderLayout.CENTER);
        doctypesTop.add(createHelpButton("https://www.jdeploy.com/docs/help/#filetypes", "", "Learn more about file associations in jDeploy."), BorderLayout.EAST);
        doctypesTop.setMaximumSize(new Dimension(doctypesTop.getPreferredSize()));
        doctypesPanelWrapper.add(doctypesTop, BorderLayout.NORTH);



        JTabbedPane tabs = new JTabbedPane();


        JComponent detailsPanel = PanelMatic.begin()
                .add(Groupings.lineGroup(mainFields.icon,
                        (JComponent)Box.createRigidArea(new Dimension(10, 10)),
                        PanelMatic.begin()
                                .add("Name", mainFields.name)
                                .add("Version", mainFields.version)
                                .add("Title", mainFields.title)
                                .add("Author", mainFields.author)
                                .add("Description", mainFields.description)
                                .add("License", mainFields.license)
                                .get()
                        ))
                .add((JComponent) Box.createRigidArea(new Dimension(10, 10)))
                .add("JAR file", Groupings.lineGroup(mainFields.jar, mainFields.selectJar))
                .addHeader(PanelBuilder.HeaderLevel.H5, "Runtime Environment")
                .add("Java Version", mainFields.javaVersion)
                .add("", mainFields.javafx)
                .add("", mainFields.jdk)
                .addHeader(PanelBuilder.HeaderLevel.H5, "Links")
                .add("Homepage", mainFields.homepage)
                .add("Repository", Groupings.lineGroup(new JLabel("URL:"), mainFields.repository, new JLabel("Directory:"), mainFields.repositoryDirectory))
                .get();
        JPanel detailWrapper = new JPanel();
        detailWrapper.setLayout(new BoxLayout(detailWrapper, BoxLayout.Y_AXIS));


        detailWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
        detailWrapper.setPreferredSize(new Dimension(640, 480));
        detailWrapper.setOpaque(false);
        detailsPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        detailsPanel.setMaximumSize(new Dimension(10000, detailsPanel.getPreferredSize().height));
        Box.Filler filler = new Box.Filler(
                new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(1000, 1000)
        );
        filler.setOpaque(false);
        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        helpPanel.setOpaque(false);
        helpPanel.add(createHelpButton("https://www.jdeploy.com/docs/help/#_the_details_tab", "", "Learn about what these fields do."));
        //helpPanel.setMaximumSize(new Dimension(helpPanel.getPreferredSize()));
        detailWrapper.add(helpPanel);
        detailWrapper.add(detailsPanel);
        detailWrapper.add(filler);

        JScrollPane detailsScroller = new JScrollPane(detailWrapper);
        detailsScroller.setBorder(new EmptyBorder(0,0,0,0));
        detailsScroller.setOpaque(false);
        detailsScroller.getViewport().setOpaque(false);


        tabs.addTab("Details", detailsScroller);

        JComponent imagesPanel = PanelMatic.begin()

                .add("Install Splash Screen", mainFields.installSplash)
                .add("Splash Screen", mainFields.splash)
                .get();
        JPanel imagesPanelWrapper = new JPanel();
        imagesPanelWrapper.setLayout(new BorderLayout());
        imagesPanelWrapper.setOpaque(false);
        imagesPanelWrapper.add(imagesPanel, BorderLayout.CENTER);
        JPanel imagesHelpPanel = new JPanel();
        imagesHelpPanel.setOpaque(false);
        imagesHelpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        imagesHelpPanel.add(createHelpButton("https://www.jdeploy.com/docs/help/#splashscreens", "", "Learn more about this panel, and how splash screen images are used in jDeploy."));
        //imagesHelpPanel.setMaximumSize(new Dimension(imagesHelpPanel.getPreferredSize()));
        imagesPanelWrapper.add(imagesHelpPanel, BorderLayout.NORTH);
        tabs.add("Splash Screens", imagesPanelWrapper);


        tabs.add("Filetypes", doctypesPanelWrapper);

        JPanel urlsPanel = new JPanel();
        urlsPanel.setOpaque(false);

        JTextArea urlSchemesHelp = new JTextArea();
        urlSchemesHelp.setEditable(false);
        urlSchemesHelp.setOpaque(false);
        urlSchemesHelp.setLineWrap(true);
        urlSchemesHelp.setWrapStyleWord(true);
        urlSchemesHelp.setText("Create one or more custom URL schemes that will trigger your app to launch when users try to open a link in their web browser with one of them.\nEnter one or more URL schemes separated by commas in the field below." +
                "\n\nFor example, if you want links like mynews:foobarfoo and mymusic:fuzzbazz to launch your app, then " +
                "add 'mynews, mymusic' to the field below.");
        urlSchemesHelp.setMaximumSize(new Dimension(600, 150));
        urlsPanel.setLayout(new BoxLayout(urlsPanel, BoxLayout.Y_AXIS));

        JPanel urlsHelpPanelWrapper = new JPanel();
        urlsHelpPanelWrapper.setLayout(new FlowLayout(FlowLayout.RIGHT));
        urlsHelpPanelWrapper.setOpaque(false);
        urlsHelpPanelWrapper.add(createHelpButton("https://www.jdeploy.com/docs/help/#_the_urls_tab", "", "Learn more about custom URL schemes in jDeploy"));
        urlsHelpPanelWrapper.setMaximumSize(new Dimension(10000, urlsHelpPanelWrapper.getPreferredSize().height));
        urlsPanel.add(urlsHelpPanelWrapper);
        urlsPanel.add(urlSchemesHelp);
        urlsPanel.add(mainFields.urlSchemes);
        mainFields.urlSchemes.setMaximumSize(new Dimension(400, mainFields.urlSchemes.getPreferredSize().height));
        Box.Filler urlSchemesFiller = new Box.Filler(
                new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(100, 1000)
        );
        urlSchemesFiller.setOpaque(false);
        urlsPanel.add(urlSchemesFiller);

        //urlsHelpPanelWrapper.add(urlsPanel, BorderLayout.CENTER);
        tabs.add("URLs", urlsPanel);

        JPanel cliPanel = new JPanel();
        cliPanel.setOpaque(false);
        cliPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        cliPanel.setLayout(new BoxLayout(cliPanel, BoxLayout.Y_AXIS));

        JPanel cliHelpPanel = new JPanel();
        cliHelpPanel.setOpaque(false);
        cliHelpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        cliHelpPanel.add(createHelpButton("https://www.jdeploy.com/docs/help/#cli", "", "Learn more about this tab in the help guide."));
        cliHelpPanel.setMaximumSize(new Dimension(10000, cliHelpPanel.getPreferredSize().height));
        cliPanel.add(cliHelpPanel);
        cliPanel.add(new JLabel("<html><p style='width:400px'>Your app will also be installable and runnable as a command-line app using npm/npx.  See the CLI tutorialfor more details</p></html>"));
        JButton viewCLITutorial = new JButton("Open CLI Tutorial");
        viewCLITutorial.addActionListener(evt->{
            try {
                Desktop.getDesktop().browse(new URI("https://www.jdeploy.com/docs/getting-started-tutorial-cli/"));
            } catch (Exception ex) {
                System.err.println("Failed to open cli tutorial.");
                ex.printStackTrace(System.err);
                JOptionPane.showMessageDialog(frame,
                        new JLabel("<html><p style='width:400px'>Failed to open the CLI tutorial.  Try opening https://www.jdeploy.com/docs/getting-started-tutorial-cli/ manually in your browser."),
                        "Failed to Open",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        viewCLITutorial.setMaximumSize(viewCLITutorial.getPreferredSize());
        cliPanel.add(Box.createVerticalStrut(10));
        cliPanel.add(viewCLITutorial);
        cliPanel.add(Box.createVerticalStrut(10));
        cliPanel.add(new JLabel("<html><p style='width:400px'>The following field allows you to specify the command name for your app.  Users will launch your app by entering this name in the command-line. </p></html>"));
        cliPanel.add(mainFields.command);
        mainFields.command.setMaximumSize(new Dimension(1000, mainFields.command.getPreferredSize().height));
        //cliPanel.add(Box.createVerticalGlue());
        cliPanel.add(new Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(10, 1000)));
        tabs.add("CLI", cliPanel);

        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(tabs, BorderLayout.CENTER);

        JPanel bottomButtons = new JPanel();
        bottomButtons.setLayout(new FlowLayout(FlowLayout.RIGHT));


        JButton viewDownloadPage = new JButton("View Download Page");
        viewDownloadPage.addActionListener(evt->{
            String packageName = packageJSON.getString("name");
            try {
                JSONObject packageInfo = new NPM(System.out, System.err).fetchPackageInfoFromNpm(packageName);

            } catch (Exception ex) {
                showError("Unable to load your package details from NPM.  Either you haven't published your app yet, or there was a network error.", ex);
                return;
            }
            try {
                Desktop.getDesktop().browse(new URI("https://www.jdeploy.com/~" + packageName));
            } catch (Exception ex) {
                showError("Failed to open download page.  "+ex.getMessage(), ex);
            }
        });


        JButton publish = new JButton("Publish");
        publish.setDefaultCapable(true);

        publish.addActionListener(evt->{

            int result = JOptionPane.showConfirmDialog(frame, new JLabel("<html><p style='width:400px'>Are you sure you want to publish your app to npm?  " +
                    "Once published, users will be able to download your app at <a href='https://www.jdeploy.com/~"+packageJSON.getString("name")+"'>https://www.jdeploy.com/~"+packageJSON.getString("name")+"</a>.<br/>Do you wish to proceed?</p></html>"),
                    "Publish to NPM?",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.NO_OPTION) {
                return;
            }



            new Thread(()->{
                handlePublish();
            }).start();
        });
        bottomButtons.add(viewDownloadPage);
        bottomButtons.add(publish);
        cnt.add(bottomButtons, BorderLayout.SOUTH);


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

        JMenuItem openInTextEditor = new JMenuItem("Open with Text Editor");
        openInTextEditor.setToolTipText("Open the package.json file for editing in your system text editor");

        openInTextEditor.addActionListener(evt-> handleOpenInTextEditor());
        file.addSeparator();
        file.add(openInTextEditor);



        if (!Platform.getSystemPlatform().isMac()) {
            file.addSeparator();
            JMenuItem quit = new JMenuItem("Exit");
            quit.setAccelerator(KeyStroke.getKeyStroke('Q', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            quit.addActionListener(evt -> handleClosing());
            file.add(quit);
        }



        JMenu help = new JMenu("Help");
        JMenuItem jdeployHelp = createLinkItem("https://www.jdeploy.com/docs/help", "jDeploy Help", "Open jDeploy application help in your web browser");
        help.add(jdeployHelp);

        help.addSeparator();
        help.add(createLinkItem("https://www.jdeploy.com/", "jDeploy Website", "Open the jDeploy website in your web browser."));
        help.add(createLinkItem("https://www.jdeploy.com/docs/manual", "jDeploy Developers Guide", "Open the jDeploy developers guide in your web browser."));






        jmb.add(file);
        jmb.add(help);
        frame.setJMenuBar(jmb);
    }

    private JButton createHelpButton(String url, String label, String tooltipText) {
        JButton btn = new JButton(FontIcon.of(Material.HELP));
        btn.setText(label);
        btn.setToolTipText(tooltipText);
        btn.addActionListener(evt->{
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    showError("Failed to open web browser to "+url, ex);
                }
            } else {
                showError("Attempt to open web browser failed.  Not supported on this platform", null);
            }
        });
        btn.setMaximumSize(new Dimension(btn.getPreferredSize()));
        return btn;
    }

    private JMenuItem createLinkItem(String url, String label, String tooltipText) {
        JMenuItem btn = new JMenuItem();
        btn.setText(label);
        btn.setToolTipText(tooltipText);
        btn.addActionListener(evt->{
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    showError("Failed to open web browser to "+url, ex);
                }
            } else {
                showError("Attempt to open web browser failed.  Not supported on this platform", null);
            }
        });
        return btn;
    }

    private void handleOpenInTextEditor() {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().edit(packageJSONFile);
            } catch (Exception ex) {
                showError("Failed to open package.json in a text editor. "+ex.getMessage(), ex);
            }
        } else {
            showError("That feature isn't supported on this platform.", null);
        }
    }



    private void handleSave() {
        try {
            FileUtil.writeStringToFile(packageJSON.toString(4), packageJSONFile);
            packageJSONMD5 = MD5.getMD5Checksum(packageJSONFile);
            clearModified();
        } catch (Exception ex) {
            System.err.println("Failed to save "+packageJSONFile+": "+ex.getMessage());
            ex.printStackTrace(System.err);
            showError("Failed to save package.json file. "+ex.getMessage(), ex);
        }
    }

    private void showError(String message, Throwable exception) {
        JOptionPane.showMessageDialog(frame, new JLabel("<html><p style='width:400px'>"+message+"</p></html>"), "Error", JOptionPane.ERROR_MESSAGE);
        exception.printStackTrace(System.err);

    }

    private static final int NOT_LOGGED_IN = 1;

    private class ValidationException extends Exception {
        private int type;

        ValidationException(String msg) {
            super(msg);
        }

        ValidationException(String msg, int type) {
            super(msg);
            this.type = type;
        }

        ValidationException(String msg, Throwable cause) {
            super(msg, cause);
        }

        ValidationException(String msg, Throwable cause, int type) {
            super(msg, cause);
            this.type = type;
        }

        int getType() {
            return type;
        }
    }



    private void validateJar(File jar) throws ValidationException {
        try {
            JarFile jarFile = new JarFile(jar);
            if(jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS) != null) {
                return;
            }
            throw new ValidationException("Selected jar file is not an executable Jar file.  \nPlease see https://www.jdeploy.com/docs/manual/#_appendix_building_executable_jar_file");
        } catch (IOException ex) {
            throw new ValidationException("Failed to load jar file", ex);
        }
    }

    private boolean publishInProgress = false;

    private void handlePublish() {
        if (publishInProgress) return;
        publishInProgress = true;

        try {
            handlePublish0();
        } catch (ValidationException ex) {
            if (ex.type == NOT_LOGGED_IN) {
                LoginDialog dlg = new LoginDialog();
                dlg.onLogin(()->{
                    new Thread(()->{
                        publishInProgress = false;
                        handlePublish();
                    }).start();
                });
                dlg.show(frame);
            } else {
                showError(ex.getMessage(), ex);
            }
        } finally {
            publishInProgress = false;
        }
    }





    private void handlePublish0() throws ValidationException {


        File absDirectory = packageJSONFile.getAbsoluteFile().getParentFile();

        String[] requiredFields = new String[]{
                "name",
                "author",
                "description",
                "version"
        };
        for (String field : requiredFields) {
            if (!packageJSON.has(field) || packageJSON.getString(field).isEmpty()) {
                throw new ValidationException("The " + field + " field is required for publishing.");
            }
        }


        if (!packageJSON.has("jdeploy")) {
            throw new ValidationException("This package.json is missing the jdeploy object which is required.");
        }
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        if (!jdeploy.has("jar")) {
            throw new ValidationException("Please select a jar file before publishing.");
        }
        File jarFile = new File(absDirectory, toNativePath(jdeploy.getString("jar")));
        if (!jarFile.getName().endsWith(".jar")) {
            throw new ValidationException("The selected jar file is not a jar file.  Jar files must have the .jar extension");
        }
        if (!jarFile.exists()) {
            throw new ValidationException("The selected jar file does not exist.  Please check the selected jar file and try again.");
        }
        // This validates that the jar file is an executable jar file.
        validateJar(jarFile);

        // Now let's make sure that this version isn't already published.
        String version = packageJSON.getString("version");
        String packageName = packageJSON.getString("name");
        if (new NPM(System.out, System.err).isVersionPublished(packageName, version)) {
            throw new ValidationException("The package " + packageName + " already has a published version " + version + ".  Please increment the version number and try to publish again.");
        }


        // Let's check to see if we're logged into

        if (!new NPM(System.out, System.err).isLoggedIn()) {
            throw new ValidationException("You must be logged into NPM in order to publish", NOT_LOGGED_IN);
        }

        ProgressDialog progressDialog = new ProgressDialog(packageJSON.getString("name"));
        JDeploy jdeployObject = new JDeploy(packageJSONFile.getAbsoluteFile().getParentFile(), false);
        jdeployObject.setOut(new PrintStream(progressDialog.createOutputStream()));
        jdeployObject.setErr(new PrintStream(progressDialog.createOutputStream()));
        EventQueue.invokeLater(()->{
            progressDialog.show(frame, "Publishing in Progress...");
            progressDialog.setMessage1("Publishing "+packageJSON.get("name")+" to npm.  Please wait...");
            progressDialog.setMessage2("");

        });
        try {
            handleSave();
            jdeployObject.publish();
            EventQueue.invokeLater(()->{
                progressDialog.setComplete();

            });
        } catch (Exception ex) {
            System.err.println("An error occurred during publishing");
            ex.printStackTrace(System.err);
            EventQueue.invokeLater(() -> {
                progressDialog.setFailed();
            });
        }












    }


}
