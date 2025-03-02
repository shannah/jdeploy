package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.factories.PublishTargetFactory;
import ca.weblite.jdeploy.gui.controllers.EditGithubWorkflowController;
import ca.weblite.jdeploy.gui.controllers.GenerateGithubWorkflowController;
import ca.weblite.jdeploy.gui.controllers.VerifyWebsiteController;
import ca.weblite.jdeploy.gui.services.SwingOneTimePasswordProvider;
import ca.weblite.jdeploy.gui.tabs.CheerpJSettings;
import ca.weblite.jdeploy.gui.tabs.DetailsPanel;
import ca.weblite.jdeploy.gui.tabs.PublishSettingsPanel;
import ca.weblite.jdeploy.helpers.NPMApplicationHelper;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.npm.TerminalLoginLauncher;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.services.*;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.MD5;
import com.sun.nio.file.SensitivityWatchEventModifier;
import io.codeworth.panelmatic.PanelMatic;
import io.codeworth.panelmatic.util.Groupings;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.weblite.jdeploy.PathUtil.fromNativePath;
import static ca.weblite.jdeploy.PathUtil.toNativePath;

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

    private JDeployProjectEditorContext context = new JDeployProjectEditorContext();

    private JMenuItem generateGithubWorkflowMenuItem;
    private JMenuItem editGithubWorkflowMenuItem;

    private NPM npm = null;

    private NPM getNPM() {
        if (
                npm == null
                        || npm.isUseManagedNode() != context.useManagedNode()
                        ||!Objects.equals(npm.getNpmToken(), context.getNpmToken())
        ) {
            npm = new NPM(System.out, System.err, context.useManagedNode());
            npm.setNpmToken(context.getNpmToken());
        }

        return npm;
    }

    private class MainFields {
        private JTextField name, title, version, iconUrl, jar, urlSchemes, author,
                repository, repositoryDirectory, command, license, homepage;
        private JTextArea description, runArgs;

        private JCheckBox javafx, jdk;
        private JComboBox javaVersion;
        private JButton icon, installSplash, splash, selectJar;
        private JButton verifyHomepageButton;
        private JLabel homepageVerifiedLabel;


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
                    "The package.json file has been modified.  " +
                            "Would you like to reload it?  Unsaved changes will be lost",
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
        this(packageJSONFile, packageJSON, null);
    }

    public JDeployProjectEditor(
            File packageJSONFile,
            JSONObject packageJSON,
            JDeployProjectEditorContext context
    ) {
        if (context != null) {
            this.context = context;
        }
        this.packageJSONFile = packageJSONFile;
        this.packageJSON = packageJSON;
        try {
            this.packageJSONMD5 = MD5.getMD5Checksum(this.packageJSONFile);
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Failed to get MD5 checksum for packageJSON file.  This is used for the watch service."
            );
        }
    }

    private void initFrame() {
        frame = new JFrame("jDeploy");
        Thread watchThread = new Thread(()->{
            try {
                watchPackageJSONForChanges();
            } catch (Exception ex) {
                System.err.println(
                        "A problem occurred while setting up watch service on package.json.  Disabling watch service."
                );
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
                    handleSave();
                    frame.dispose();
                    break;

                case JOptionPane.NO_OPTION:
                    frame.dispose();
                    break;

                case JOptionPane.CANCEL_OPTION:
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
        String title = packageJSON.has("name") ? packageJSON.getString("name") : "";
        if (packageJSON.has("jdeploy")) {
            JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
            if (jdeploy.has("title")) {
                title = jdeploy.getString("title");
            }
        }

        frame.setTitle(title);
        frame.pack();
        frame.setLocationRelativeTo(context.getParentFrame());
        frame.setVisible(true);
    }

    public boolean isShowing() {
        return frame != null && frame.isVisible();
    }

    public boolean focus() {
        if (frame != null && frame.isVisible()) {
            frame.requestFocus();
            return true;
        }
        return false;
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
        fields.extension.setMaximumSize(
                new Dimension(fields.extension.getPreferredSize().width, fields.extension.getPreferredSize().height)
        );
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
        fields.editor.setToolTipText(
                "Check this box if the app can edit this document type.  " +
                        "Leave unchecked if it can only view documents of this type"
        );
        if (docTypeRow.has("editor") && docTypeRow.getBoolean("editor")) {
            fields.editor.setSelected(true);
        }
        fields.editor.addActionListener(evt->{
            docTypeRow.put("editor", fields.editor.isSelected());
            setModified();
        });

        fields.custom = new JCheckBox("Custom");
        fields.custom.setToolTipText(
                "Check this box if this is a custom extension for your app, and should be added to the system " +
                        "mimetypes registry."
        );
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


    private Timer verifyTimer;

    private void queueHomepageVerification() {
        if (verifyTimer != null) verifyTimer.stop();
        verifyTimer = new Timer(2000, evt->{
            verifyTimer = null;
            checkHomepageVerified();
        });
        verifyTimer.setRepeats(false);
        verifyTimer.start();
    }

    private boolean checkHomepageVerified() {
        if (EventQueue.isDispatchThread()) {
            SwingWorker worker = new SwingWorker() {
                private boolean verified;
                @Override
                protected Object doInBackground() throws Exception {
                    verified = checkHomepageVerified();
                    return null;
                }

                @Override
                protected void done() {
                    mainFields.homepageVerifiedLabel.setVisible(verified);
                    mainFields.verifyHomepageButton.setVisible(!verified);
                    frame.revalidate();
                }
            };
            worker.execute();
        }
        NPMApplication app = NPMApplicationHelper.createFromPackageJSON(packageJSON);
        WebsiteVerifier verifier = new WebsiteVerifier();
        try {
            verifier.verifyHomepage(app);
            return app.isHomepageVerified();
        } catch (Exception ex) {
            return false;
        }
    }

    private void initMainFields(Container cnt) {
        mainFields = new MainFields();
        DetailsPanel detailsPanel = new DetailsPanel();
        mainFields.name = detailsPanel.getName();
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

        mainFields.author = detailsPanel.getAuthor();
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



        mainFields.description = detailsPanel.getDescription();
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


        mainFields.runArgs = new JTextArea();
        if (jdeploy.has("args")) {
            JSONArray jarr = jdeploy.getJSONArray("args");
            int len = jarr.length();
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<len; i++) {
                if (sb.length() > 0) {
                    sb.append(System.lineSeparator());
                }
                sb.append(jarr.getString(i).trim());
            }
            mainFields.runArgs.setText(sb.toString());

        }
        addChangeListenerTo(mainFields.runArgs, ()->{
            String[] parts = mainFields.runArgs.getText().split("\n");
            JSONArray jarr = new JSONArray();
            int index = 0;
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                jarr.put(index++, part.trim());
            }
            jdeploy.put("args", jarr);
            setModified();
        });

        mainFields.title = detailsPanel.getTitle();
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

        mainFields.version = detailsPanel.getVersion();
        if (packageJSON.has("version")) {
            mainFields.version.setText(VersionCleaner.cleanVersion(packageJSON.getString("version")));
        }
        addChangeListenerTo(mainFields.version, () -> {
            String cleanVersion = VersionCleaner.cleanVersion(mainFields.version.getText());
            packageJSON.put("version", cleanVersion);
            setModified();
        });
        mainFields.version.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                mainFields.version.setText(VersionCleaner.cleanVersion(mainFields.version.getText()));
            }
        });
        mainFields.iconUrl = new JTextField();
        if (jdeploy.has("iconUrl")) {
            mainFields.iconUrl.setText(jdeploy.getString("iconUrl"));

        }
        addChangeListenerTo(mainFields.iconUrl, ()->{
            jdeploy.put("iconUrl", mainFields.iconUrl.getText());
            setModified();
        });

        mainFields.repository = detailsPanel.getRepositoryUrl();
        mainFields.repository.setColumns(30);
        mainFields.repository.setMinimumSize(new Dimension(100, mainFields.repository.getPreferredSize().height));
        mainFields.repositoryDirectory = detailsPanel.getRepositoryDirectory();
        mainFields.repositoryDirectory.setMinimumSize(
                new Dimension(100, mainFields.repositoryDirectory.getPreferredSize().height)
        );
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

        mainFields.license = detailsPanel.getLicense();
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

        mainFields.verifyHomepageButton = detailsPanel.getVerifyButton();
        mainFields.verifyHomepageButton.setToolTipText("Verify that you own this page");
        mainFields.verifyHomepageButton.addActionListener(evt->{
            handleVerifyHomepage();
        });

        mainFields.homepageVerifiedLabel = new JLabel(FontIcon.of(Material.DONE));
        mainFields.homepageVerifiedLabel.setForeground(Color.green);
        mainFields.homepageVerifiedLabel.setToolTipText("Homepage has been verified");
        mainFields.homepageVerifiedLabel.setVisible(false);
        queueHomepageVerification();


        mainFields.homepage = detailsPanel.getHomepage();
        if (packageJSON.has("homepage")) {
            mainFields.homepage.setText(packageJSON.getString("homepage"));
        }
        addChangeListenerTo(mainFields.homepage, ()->{
            packageJSON.put("homepage", mainFields.homepage.getText());
            setModified();
            queueHomepageVerification();
        });


        mainFields.splash = new JButton();
        if (getSplashFile(null) != null && getSplashFile(null).exists()) {
            try {
                mainFields.splash.setIcon(
                        new ImageIcon(Thumbnails.of(getSplashFile(null)).height(200).asBufferedImage())
                );
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
                mainFields.splash.setIcon(
                        new ImageIcon(Thumbnails.of(getSplashFile(extension)).height(200).asBufferedImage())
                );
            } catch (Exception ex) {
                System.err.println("Error while copying icon file");
                ex.printStackTrace(System.err);
                showError("Failed to select icon", ex);

            }
        });

        mainFields.installSplash = new JButton();
        if (getInstallSplashFile().exists()) {
            try {
                mainFields.installSplash.setIcon(
                        new ImageIcon(Thumbnails.of(getInstallSplashFile()).height(200).asBufferedImage())
                );
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
                mainFields.installSplash.setIcon(
                        new ImageIcon(Thumbnails.of(getInstallSplashFile()).height(200).asBufferedImage())
                );
            } catch (Exception ex) {
                System.err.println("Error while copying icon file");
                ex.printStackTrace(System.err);
                showError("Failed to select icon", ex);

            }
        });
        mainFields.icon = detailsPanel.getIcon();
        if (getIconFile().exists()) {
            try {
                mainFields.icon.setIcon(
                        new ImageIcon(Thumbnails.of(getIconFile()).size(128, 128).asBufferedImage())
                );
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
                mainFields.icon.setIcon(
                        new ImageIcon(Thumbnails.of(getIconFile()).size(128, 128).asBufferedImage())
                );
            } catch (Exception ex) {
                System.err.println("Error while copying icon file");
                ex.printStackTrace(System.err);
                showError("Failed to select icon", ex);

            }
        });

        mainFields.jar = detailsPanel.getJarFile();
        mainFields.jar.setColumns(30);
        if (jdeploy.has("jar")) {
            mainFields.jar.setText(jdeploy.getString("jar"));
        }
        addChangeListenerTo(mainFields.jar, ()->{
            jdeploy.put("jar", mainFields.jar.getText());
            setModified();
        });

        mainFields.selectJar = detailsPanel.getSelectJarFile();
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
                showError(
                        "Jar file must be in same directory as the package.json file, or a subdirectory thereof",
                        null
                );
                return;
            }
            try {
                validateJar(jarFile);
            } catch (ValidationException ex) {
                showError(ex.getMessage(), ex);
                return;
            }
            mainFields.jar.setText(
                    fromNativePath(
                            jarFile.getAbsolutePath().substring(absDirectory.getAbsolutePath().length()+1)
                    )
            );
            jdeploy.put("jar", mainFields.jar.getText());
            setModified();

        });

        mainFields.javafx = detailsPanel.getRequiresJavaFX();
        if (jdeploy.has("javafx") && jdeploy.getBoolean("javafx")) {
            mainFields.javafx.setSelected(true);
        }
        mainFields.javafx.addActionListener(evt->{
            jdeploy.put("javafx", mainFields.javafx.isSelected());
            setModified();
        });
        mainFields.jdk = detailsPanel.getRequiresFullJDK();
        if (jdeploy.has("jdk") && jdeploy.getBoolean("jdk")) {
            mainFields.jdk.setSelected(true);
        }
        mainFields.jdk.addActionListener(evt->{
            jdeploy.put("jdk", mainFields.jdk.isSelected());
            setModified();
        });

        mainFields.javaVersion = detailsPanel.getJavaVersion();
        mainFields.javaVersion.setEditable(true);
        if (jdeploy.has("javaVersion")) {
            mainFields.javaVersion.setSelectedItem(String.valueOf(jdeploy.get("javaVersion")));
        } else {
            mainFields.javaVersion.setSelectedItem("17");
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
            if (!jdeploy.has("documentTypes")) {
                jdeploy.put("documentTypes", new JSONArray());
            }
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
        doctypesTop.add(
                createHelpButton(
                        "https://www.jdeploy.com/docs/help/#filetypes",
                        "",
                        "Learn more about file associations in jDeploy."
                ),
                BorderLayout.EAST
        );
        doctypesTop.setMaximumSize(new Dimension(doctypesTop.getPreferredSize()));
        doctypesPanelWrapper.add(doctypesTop, BorderLayout.NORTH);
        JPanel cheerpjSettingsRoot = null;
        if (context.shouldDisplayCheerpJPanel()) {
            CheerpJSettings cheerpJSettings = new CheerpJSettings();
            cheerpJSettings.getButtons().add(
                    createHelpButton(
                            "https://www.jdeploy.com/docs/help/#cheerpj",
                            "",
                            "Learn more about CheerpJ support")
            );
            cheerpjSettingsRoot = cheerpJSettings.getRoot();
            cheerpJSettings.getEnableCheerpJ().setSelected(
                    jdeploy.has("cheerpj")
                            && jdeploy.getJSONObject("cheerpj").has("enabled")
                            && jdeploy.getJSONObject("cheerpj").getBoolean("enabled")
            );

            Runnable updateCheerpjUI = ()->{

                cheerpJSettings.getEnableCheerpJ().setSelected(
                        jdeploy.has("cheerpj")
                                && jdeploy.getJSONObject("cheerpj").has("enabled")
                                && jdeploy.getJSONObject("cheerpj").getBoolean("enabled")
                );
                boolean cheerpjEnabled = cheerpJSettings.getEnableCheerpJ().isSelected();
                cheerpJSettings.getGithubPagesBranch().setEnabled(cheerpjEnabled);
                cheerpJSettings.getGithubPagesBranchPath().setEnabled(cheerpjEnabled);
                cheerpJSettings.getGithubPagesTagPath().setEnabled(cheerpjEnabled);
                cheerpJSettings.getGithubPagesPath().setEnabled(cheerpjEnabled);
                if (!cheerpjEnabled) {
                    return;
                }

                boolean hasBranch = jdeploy.has("cheerpj")
                        && jdeploy
                        .getJSONObject("cheerpj")
                        .has("githubPages")
                        && jdeploy
                        .getJSONObject("cheerpj")
                        .getJSONObject("githubPages")
                        .has("branch");

                cheerpJSettings.getGithubPagesBranch().setText(
                        !hasBranch
                                ? ""
                                :  jdeploy
                                .getJSONObject("cheerpj")
                                .getJSONObject("githubPages")
                                .getString("branch")
                );

                boolean hasBranchPath = jdeploy.has("cheerpj")
                        && jdeploy
                        .getJSONObject("cheerpj")
                        .has("githubPages")
                        && jdeploy
                        .getJSONObject("cheerpj")
                        .getJSONObject("githubPages")
                        .has("branchPath");

                cheerpJSettings.getGithubPagesBranchPath().setText(
                        !hasBranchPath
                                ? ""
                                :  jdeploy
                                .getJSONObject("cheerpj")
                                .getJSONObject("githubPages")
                                .getString("branchPath")
                );

                boolean hasTagPath = jdeploy.has("cheerpj")
                        && jdeploy.getJSONObject("cheerpj").has("githubPages")
                        && jdeploy.getJSONObject("cheerpj").getJSONObject("githubPages").has("tagPath");

                cheerpJSettings.getGithubPagesTagPath().setText(
                        !hasTagPath
                                ? ""
                                :  jdeploy.getJSONObject("cheerpj").getJSONObject("githubPages").getString("tagPath")
                );

                boolean hasPath = jdeploy.has("cheerpj")
                        && jdeploy.getJSONObject("cheerpj").has("githubPages")
                        && jdeploy.getJSONObject("cheerpj").getJSONObject("githubPages").has("path");

                cheerpJSettings.getGithubPagesPath().setText(
                        !hasPath
                                ? ""
                                :  jdeploy
                                .getJSONObject("cheerpj")
                                .getJSONObject("githubPages")
                                .getString("path")
                );


            };
            cheerpJSettings.getEnableCheerpJ().addActionListener(evt->{
                if (cheerpJSettings.getEnableCheerpJ().isSelected()) {
                    if (jdeploy.has("cheerpj")) {
                        jdeploy.getJSONObject("cheerpj").put("enabled", true);
                        setModified();
                        return;
                    }
                }

                if (!cheerpJSettings.getEnableCheerpJ().isSelected()) {
                    if (!jdeploy.has("cheerpj")) {
                        return;
                    } else {
                        jdeploy.getJSONObject("cheerpj").put("enabled", false);
                        setModified();
                        return;
                    }
                }

                if (!jdeploy.has("cheerpj")) {
                    JSONObject initCheerpj = new JSONObject();
                    initCheerpj.put("enabled", true);
                    JSONObject initGithubPages = new JSONObject();
                    initGithubPages.put("enabled", true);
                    initGithubPages.put("branch", "gh-pages");
                    initGithubPages.put("branchPath", "{{ branch }}");
                    initGithubPages.put("tagPath", "app");
                    initCheerpj.put("githubPages", initGithubPages);

                    jdeploy.put("cheerpj",initCheerpj);
                    updateCheerpjUI.run();
                    setModified();
                    return;

                }
                jdeploy.getJSONObject("cheerpj").put("enabled", cheerpJSettings.getEnableCheerpJ().isSelected());
                setModified();
            });

            cheerpJSettings.getGithubPagesBranch().addActionListener(evt->{
                if (
                        jdeploy.has("cheerpj")
                                && jdeploy.getJSONObject("cheerpj").has("githubPages")
                ) {
                    jdeploy
                            .getJSONObject("cheerpj")
                            .getJSONObject("githubPages")
                            .put("branch", cheerpJSettings.getGithubPagesBranch().getText());
                    setModified();
                }
            });

            cheerpJSettings.getGithubPagesBranchPath().addActionListener(evt->{
                if (
                        jdeploy.has("cheerpj")
                                && jdeploy.getJSONObject("cheerpj").has("githubPages")
                ) {
                    jdeploy.getJSONObject("cheerpj")
                            .getJSONObject("githubPages")
                            .put("branchPath", cheerpJSettings.getGithubPagesBranchPath().getText());
                    setModified();
                }
            });

            cheerpJSettings.getGithubPagesTagPath().addActionListener(evt->{
                if (
                        jdeploy.has("cheerpj")
                                && jdeploy.getJSONObject("cheerpj").has("githubPages")
                ) {
                    jdeploy.getJSONObject("cheerpj")
                            .getJSONObject("githubPages")
                            .put("tagPath", cheerpJSettings.getGithubPagesTagPath().getText());
                    setModified();
                }
            });

            cheerpJSettings.getGithubPagesPath().addActionListener(evt->{
                if (jdeploy.has("cheerpj") && jdeploy.getJSONObject("cheerpj").has("githubPages")) {
                    jdeploy.getJSONObject("cheerpj").getJSONObject("githubPages").put("path", cheerpJSettings.getGithubPagesPath().getText());
                    setModified();
                }
            });
            updateCheerpjUI.run();
        }

        JTabbedPane tabs = new JTabbedPane();
        JPanel detailsPanelRoot = detailsPanel.getRoot();
        JPanel detailWrapper = new JPanel();
        detailWrapper.setLayout(new BorderLayout());
        detailWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        helpPanel.add(
                createHelpButton(
                        "https://www.jdeploy.com/docs/help/#_the_details_tab",
                        "",
                        "Learn about what these fields do."
                )
        );
        detailWrapper.add(helpPanel, BorderLayout.NORTH);
        detailWrapper.add(detailsPanelRoot, BorderLayout.CENTER);



        tabs.addTab("Details", detailWrapper);

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
        imagesHelpPanel.add(
                createHelpButton(
                        "https://www.jdeploy.com/docs/help/#splashscreens",
                        "",
                        "Learn more about this panel, and how splash screen images are used in jDeploy."
                )
        );

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
        urlSchemesHelp.setText(
                "Create one or more custom URL schemes that will trigger your app to launch when users try to open a " +
                        "link in their web browser with one of them.\nEnter one or more URL schemes separated by commas " +
                        "in the field below." +
                "\n\nFor example, if you want links like mynews:foobarfoo and mymusic:fuzzbazz to launch your app, then " +
                "add 'mynews, mymusic' to the field below."
        );
        urlSchemesHelp.setMaximumSize(new Dimension(600, 150));
        urlsPanel.setLayout(new BoxLayout(urlsPanel, BoxLayout.Y_AXIS));

        JPanel urlsHelpPanelWrapper = new JPanel();
        urlsHelpPanelWrapper.setLayout(new FlowLayout(FlowLayout.RIGHT));
        urlsHelpPanelWrapper.setOpaque(false);
        urlsHelpPanelWrapper.add(
                createHelpButton(
                        "https://www.jdeploy.com/docs/help/#_the_urls_tab",
                        "",
                        "Learn more about custom URL schemes in jDeploy"
                )
        );
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
        cliHelpPanel.add(createHelpButton(
                "https://www.jdeploy.com/docs/help/#cli",
                "",
                "Learn more about this tab in the help guide."
        ));
        cliHelpPanel.setMaximumSize(new Dimension(10000, cliHelpPanel.getPreferredSize().height));
        cliPanel.add(cliHelpPanel);
        cliPanel.add(new JLabel(
                "<html>" +
                        "<p style='width:400px'>" +
                        "Your app will also be installable and runnable as a command-line app using npm/npx.  " +
                        "See the CLI tutorialfor more details" +
                        "</p>" +
                        "</html>"
        ));
        JButton viewCLITutorial = new JButton("Open CLI Tutorial");
        viewCLITutorial.addActionListener(evt->{
            try {
                context.browse(new URI("https://www.jdeploy.com/docs/getting-started-tutorial-cli/"));
            } catch (Exception ex) {
                System.err.println("Failed to open cli tutorial.");
                ex.printStackTrace(System.err);
                JOptionPane.showMessageDialog(frame,
                        new JLabel(
                                "<html>" +
                                        "<p style='width:400px'>" +
                                        "Failed to open the CLI tutorial.  " +
                                        "Try opening https://www.jdeploy.com/docs/getting-started-tutorial-cli/ " +
                                        "manually in your browser." +
                                        "</p>" +
                                        "</html>"
                        ),
                        "Failed to Open",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        viewCLITutorial.setMaximumSize(viewCLITutorial.getPreferredSize());
        cliPanel.add(Box.createVerticalStrut(10));
        cliPanel.add(viewCLITutorial);
        cliPanel.add(Box.createVerticalStrut(10));
        cliPanel.add(new JLabel("" +
                "<html>" +
                "<p style='width:400px'>" +
                "The following field allows you to specify the command name for your app.  " +
                "Users will launch your app by entering this name in the command-line. " +
                "</p>" +
                "</html>"));
        cliPanel.add(mainFields.command);
        mainFields.command.setMaximumSize(new Dimension(1000, mainFields.command.getPreferredSize().height));
        //cliPanel.add(Box.createVerticalGlue());
        cliPanel.add(new Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(10, 1000)));
        tabs.add("CLI", cliPanel);


        JPanel runargsPanel = new JPanel();
        runargsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        runargsPanel.setLayout(new BorderLayout());
        runargsPanel.setOpaque(false);
        JPanel runArgsTop = new JPanel();
        runArgsTop.setOpaque(false);
        runArgsTop.setLayout(new BorderLayout());

        JButton runargsHelp = createHelpButton(
                "https://www.jdeploy.com/docs/help/#runargs",
                "",
                "Open run arguments help in web browser"
        );
        runargsHelp.setMaximumSize(new Dimension(runargsHelp.getPreferredSize()));

        JLabel runArgsLabel = new JLabel("Runtime Arguments");
        JLabel runArgsDescription = new JLabel("<html>" +
                "<p style='font-size:x-small;width:400px'>One argument per line.<br/></p>" +
                "<p style='font-size:x-small; width:400px'>Prefix system properties with '-D'.  E.g. -Dfoo=bar</p>" +
                "<p style='font-size:x-small;width:400px'>Prefix JVM options with '-X'.  E.g. -Xmx2G</p><br/>" +
                "<p style='font-size:x-small;width:400px;padding-top:1em'><strong>Placeholder Variables</strong><br/>" +
                "<strong>{{ user.home }}</strong> : The user's home directory<br/>" +
                "<strong>{{ exe.path }}</strong> : The path to the program executable.<br/>" +
                "<strong>{{ app.path }}</strong> : " +
                "The path to the .app bundle on Mac.  Falls back to executable path on other platforms.<br/>" +
                "</p><br/>" +
                "<p style='font-size:x-small;width:400px;padding-top:1em'>" +
                "<strong>Platform-Specific Arguments:</strong><br/>" +
                "Platform-specific arguments are only added on specific platforms.<br/>" +
                "<strong>Property Args:</strong> " +
                "-D[PLATFORMS]foo=bar, where PLATFORMS is mac, win, or linux, or pipe-concatenated list. " +
                " E.g. '-D[mac]foo=bar', '-D[win]foo=bar', '-D[linux]foo=bar', '-D[mac|linux]foo=bar', etc...<br/>" +
                "<strong>JVM Options:</strong> " +
                "-X[PLATFORMS]foo, where PLATFORMS is mac, win, or linux, or pipe-concatenated list.  " +
                "E.g. '-X[mac]foo', '-X[win]foo', '-X[linux]foo', '-X[mac|linux]foo', etc...<br/>" +
                "<strong>Program Args:</strong> " +
                "-[PLATFORMS]foo, where PLATFORMS is mac, win, or linux, or pipe-concatenated list.  " +
                "E.g. '-[mac]foo', '-[win]foo', '-[linux]foo', '-[mac|linux]foo', etc...<br/>" +
                "</p>" +
                "</html>");
        runArgsDescription.setBorder(new EmptyBorder(10,10,10,10));

        runArgsTop.add(runArgsLabel, BorderLayout.CENTER);
        runArgsTop.add(runArgsDescription, BorderLayout.SOUTH);
        runArgsTop.add(runargsHelp, BorderLayout.EAST);

        JScrollPane runArgsScroller = new JScrollPane(mainFields.runArgs);
        runArgsScroller.setOpaque(false);
        runArgsScroller.getViewport().setOpaque(false);
        runargsPanel.add(runArgsScroller, BorderLayout.CENTER);
        runargsPanel.add(runArgsTop, BorderLayout.NORTH);

        tabs.add("Runtime Args", runargsPanel);

        if (context.shouldDisplayCheerpJPanel()) {
            tabs.add("CheerpJ", cheerpjSettingsRoot);
        }

        if (context.shouldDisplayPublishSettingsTab()) {
            tabs.add("Publish Settings", createPublishSettingsPanel());
        }

        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(tabs, BorderLayout.CENTER);

        JPanel bottomButtons = new JPanel();
        bottomButtons.setLayout(new FlowLayout(FlowLayout.RIGHT));


        JButton viewDownloadPage = new JButton("View Download Page");
        viewDownloadPage.addActionListener(evt->{
            try {
                context.browse(new URI(getDownloadPageUrl()));
            } catch (Exception ex) {
                showError("Failed to open download page.  "+ex.getMessage(), ex);
            }
        });

        JButton preview = new JButton("Web Preview");
        preview.addActionListener(evt->{
            context.showWebPreview(frame);
        });


        JButton publish = new JButton("Publish");
        publish.setDefaultCapable(true);

        publish.addActionListener(evt->{
            String publishTargetName = getPublishTargetNames();
            String downloadPageUrl = getDownloadPageUrl();
            int result = JOptionPane.showConfirmDialog(
                    frame,
                    new JLabel("<html><p style='width:400px'>Are you sure you want to publish your app to " + publishTargetName + "?  " +
                    "Once published, users will be able to download your app at " +
                            "<a href='" + downloadPageUrl + "'>" +
                            downloadPageUrl +
                            "</a>." +
                            "<br/>Do you wish to proceed?</p>" +
                            "</html>"
                    ),
                    "Publish to " + publishTargetName + "?",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.NO_OPTION) {
                return;
            }

            new Thread(()->{
                handlePublish();
            }).start();
        });

        JButton apply = new JButton("Apply");
        apply.addActionListener(evt -> handleSave());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(evt -> handleClosing());

        bottomButtons.add(viewDownloadPage);
        if (context.isWebPreviewSupported()) {
            bottomButtons.add(preview);
        }
        if (context.shouldShowPublishButton()) {
            bottomButtons.add(publish);
        }
        if (context.shouldDisplayApplyButton()) {
            bottomButtons.add(apply);
        }
        if (context.shouldDisplayCancelButton()) {
            bottomButtons.add(closeBtn);
        }
        cnt.add(bottomButtons, BorderLayout.SOUTH);


    }

    private Component createPublishSettingsPanel() {
        PublishSettingsPanel panel = new PublishSettingsPanel();
        PublishTargetFactory factory = DIContext.get(PublishTargetFactory.class);
        PublishTargetServiceInterface publishTargetService = DIContext.get(PublishTargetServiceInterface.class);
        try {
            List<PublishTargetInterface> targets = publishTargetService.getTargetsForPackageJson(packageJSON, true);
            PublishTargetInterface npmTarget = targets.stream().filter(t -> t.getType() == PublishTargetType.NPM).findFirst().orElse(null);
            PublishTargetInterface gitHubTarget = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
            panel.getNpmCheckbox().setSelected(npmTarget != null);
            panel.getGithubCheckbox().setSelected(gitHubTarget != null);
            if (gitHubTarget != null) {
                panel.getGithubRepositoryField().setText(gitHubTarget.getUrl());
            }

            panel.getNpmCheckbox().addActionListener(evt -> {
                if (panel.getNpmCheckbox().isSelected()) {
                    try {
                        PublishTargetInterface existingNpm = targets.stream().filter(t -> t.getType() == PublishTargetType.NPM).findFirst().orElse(null);
                        if (existingNpm == null) {
                            targets.add(factory.createWithUrlAndName(packageJSON.getString("name"), packageJSON.getString("name")));
                            publishTargetService.updatePublishTargetsForPackageJson(packageJSON, targets);
                            setModified();
                        }
                    } catch (Exception ex) {
                        showError("Failed to create NPM publish target", ex);
                    }
                } else {
                    try {
                        PublishTargetInterface existingNpm = targets.stream().filter(t -> t.getType() == PublishTargetType.NPM).findFirst().orElse(null);
                        if (existingNpm != null) {
                            targets.remove(existingNpm);
                            publishTargetService.updatePublishTargetsForPackageJson(packageJSON, targets);
                            setModified();
                        }
                    } catch (Exception ex) {
                        showError("Failed to delete NPM publish target", ex);
                    }
                }
            });

            panel.getGithubCheckbox().addActionListener(evt -> {
                if (panel.getGithubCheckbox().isSelected()) {
                    try {
                        PublishTargetInterface existingGithub = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
                        if (existingGithub == null) {
                            targets.add(factory.createWithUrlAndName(panel.getGithubRepositoryField().getText(), packageJSON.getString("name")));
                            publishTargetService.updatePublishTargetsForPackageJson(packageJSON, targets);
                            setModified();
                        }
                    } catch (Exception ex) {
                        showError("Failed to create NPM publish target", ex);
                    }
                } else {
                    try {
                        PublishTargetInterface existingGithub = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
                        if (existingGithub != null) {
                            targets.remove(existingGithub);
                            publishTargetService.updatePublishTargetsForPackageJson(packageJSON, targets);
                            setModified();
                        }
                    } catch (Exception ex) {
                        showError("Failed to delete NPM publish target", ex);
                    }
                }
            });

            panel.getGithubRepositoryField().getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateGithubUrl();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateGithubUrl();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateGithubUrl();
                }

                private void updateGithubUrl() {
                    try {
                        PublishTargetInterface existingGithub = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
                        if (existingGithub != null) {
                            targets.remove(existingGithub);
                        }
                        PublishTargetInterface newGithub = factory.createWithUrlAndName(panel.getGithubRepositoryField().getText(), packageJSON.getString("name"));
                        targets.add(newGithub);
                        publishTargetService.updatePublishTargetsForPackageJson(packageJSON, targets);
                        setModified();
                    } catch (Exception ex) {
                        showError("Failed to update Github URL", ex);
                    }
                }
            });
        } catch (Exception ex) {
            showError("Failed to load publish targets", ex);
        }

        return panel;

    }


    private File showFileChooser(String title, String... extensions) {
        return showFileChooser(title, new HashSet<String>(Arrays.asList(extensions)));
    }
    private File showFileChooser(String title, Set<String> extensions) {
        return context.getFileChooserInterop().showFileChooser(frame, title, extensions);
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

        JMenuItem openProjectDirectory = new JMenuItem("Open Project Directory");
        openProjectDirectory.setToolTipText("Open the project directory in your system file manager");
        openProjectDirectory.addActionListener(evt->{
            if (context.getDesktopInterop().isDesktopSupported()) {
                try {
                    context.getDesktopInterop().openDirectory(packageJSONFile.getParentFile());
                } catch (Exception ex) {
                    showError("Failed to open project directory in file manager", ex);
                }
            } else {
                showError("That feature isn't supported on this platform.", null);
            }
        });
        file.addSeparator();
        file.add(openInTextEditor);
        file.add(openProjectDirectory);

        generateGithubWorkflowMenuItem = new JMenuItem("Create Github Workflow");
        generateGithubWorkflowMenuItem.setToolTipText(
                "Generate a Github workflow to deploy your application automatically with Github Actions"
        );
        generateGithubWorkflowMenuItem.addActionListener(evt -> generateGithubWorkflow());

        editGithubWorkflowMenuItem = new JMenuItem("Edit Github Workflow");
        editGithubWorkflowMenuItem.setToolTipText("Edit the Github workflow file in a text editor");
        editGithubWorkflowMenuItem.addActionListener(evt -> editGithubWorkflow());
        file.addSeparator();
        file.add(generateGithubWorkflowMenuItem);
        file.add(editGithubWorkflowMenuItem);

        JMenuItem verifyHomepage = new JMenuItem("Verify Homepage");
        verifyHomepage.setToolTipText(
                "Verify your app's homepage so that users will know that you are the developer of your app"
        );
        verifyHomepage.addActionListener(evt->{
            handleVerifyHomepage();
        });
        file.addSeparator();
        file.add(verifyHomepage);

        if (context.shouldDisplayExitMenu()) {
            file.addSeparator();
            JMenuItem quit = new JMenuItem("Exit");
            quit.setAccelerator(
                    KeyStroke.getKeyStroke('Q', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
            );
            quit.addActionListener(evt -> handleClosing());
            file.add(quit);
        }

        JMenu help = new JMenu("Help");
        JMenuItem jdeployHelp = createLinkItem(
                "https://www.jdeploy.com/docs/help",
                "jDeploy Help", "Open jDeploy application help in your web browser"
        );
        help.add(jdeployHelp);

        help.addSeparator();
        help.add(createLinkItem(
                "https://www.jdeploy.com/",
                "jDeploy Website",
                "Open the jDeploy website in your web browser."
        ));
        help.add(createLinkItem(
                "https://www.jdeploy.com/docs/manual",
                "jDeploy Developers Guide",
                "Open the jDeploy developers guide in your web browser."
        ));
        help.addSeparator();
        help.add(createLinkItem(
                "https://groups.google.com/g/jdeploy-developers",
                "jDeploy Developers Mailing List",
                "A mailing list for developers who are developing apps with jDeploy"
        ));
        help.add(createLinkItem(
                "https://github.com/shannah/jdeploy/discussions",
                "Support Forum",
                "A place to ask questions and get help from the community"));
        help.add(createLinkItem(
                "https://github.com/shannah/jdeploy/issues",
                "Issue Tracker",
                "Find and report bugs"
        ));

        jmb.add(file);
        jmb.add(help);
        if (context.shouldDisplayMenuBar()) {
            frame.setJMenuBar(jmb);
        }
    }

    private void handleVerifyHomepage() {
        NPMApplication app = NPMApplicationHelper.createFromPackageJSON(packageJSON);
        VerifyWebsiteController verifyController = new VerifyWebsiteController(frame, app);
        EventQueue.invokeLater(verifyController);
    }

    private void generateGithubWorkflow() {
        final File projectDirectory = packageJSONFile.getAbsoluteFile().getParentFile();
        final JDeploy jdeploy = new JDeploy(projectDirectory, false);
        jdeploy.setNpmToken(context.getNpmToken());
        jdeploy.setUseManagedNode(context.useManagedNode());

        final PackagingContext packagingContext = PackagingContext.builder()
                .directory(projectDirectory)
                .exitOnFail(false)
                .build();

        EventQueue.invokeLater(
                new GenerateGithubWorkflowController(
                        frame,
                        packagingContext.getJavaVersion(17),
                        "master",
                        new GithubWorkflowGenerator(projectDirectory)
                )
        );
    }

    private void editGithubWorkflow() {
        final File projectDirectory = packageJSONFile.getAbsoluteFile().getParentFile();
        EventQueue.invokeLater(
                new EditGithubWorkflowController(
                        frame,
                        new GithubWorkflowGenerator(projectDirectory),
                        context.getDesktopInterop()
                )
        );
    }

    private JButton createHelpButton(String url, String label, String tooltipText) {
        JButton btn = new JButton(FontIcon.of(Material.HELP));
        btn.setText(label);
        btn.setToolTipText(tooltipText);
        btn.addActionListener(evt->{
            if (context.getDesktopInterop().isDesktopSupported()) {
                try {
                    context.browse(new URI(url));
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
            if (context.getDesktopInterop().isDesktopSupported()) {
                try {
                    context.browse(new URI(url));
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
        if (context.getDesktopInterop().isDesktopSupported()) {
            try {
                context.edit(packageJSONFile);
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
            context.onFileUpdated(packageJSONFile);
        } catch (Exception ex) {
            System.err.println("Failed to save "+packageJSONFile+": "+ex.getMessage());
            ex.printStackTrace(System.err);
            showError("Failed to save package.json file. "+ex.getMessage(), ex);
        }
    }

    private void showError(String message, Throwable exception) {
        JOptionPane.showMessageDialog(
                frame,
                new JLabel(
                        "<html><p style='width:400px'>"+message+"</p></html>"
                ),
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
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
            throw new ValidationException(
                    "Selected jar file is not an executable Jar file.  " +
                            "\nPlease see https://www.jdeploy.com/docs/manual/#_appendix_building_executable_jar_file"
            );
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

                try {
                    TerminalLoginLauncher.launchLoginTerminal();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }

                // Create a JOptionPane with the desired message
                JOptionPane optionPane = new JOptionPane(
                        "<html><p style='width:400px'>You must be logged into NPM in order to publish your app. " +
                                "We have opened a terminal window for you to login. " +
                                "Please login to NPM in the terminal window and then try to publish again.</p></html>",
                        JOptionPane.INFORMATION_MESSAGE,
                        JOptionPane.DEFAULT_OPTION
                );

                // Create a JDialog from the JOptionPane
                JDialog dialog = optionPane.createDialog(frame, "Login to NPM");
                dialog.setModal(false); // Set to non-modal

                // Display the dialog
                dialog.setVisible(true);

                new Thread(()->{
                    NPM npm = getNPM();
                    try {
                        while (!npm.isLoggedIn() || !dialog.isShowing()) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ex1) {
                                throw new RuntimeException(ex1);
                            }
                        }
                    } finally {
                        EventQueue.invokeLater(()->{
                            dialog.setVisible(false);
                            dialog.dispose();
                        });
                    }

                    handlePublish();

                }).start();

            } else {
                showError(ex.getMessage(), ex);
            }
        } finally {
            publishInProgress = false;
        }
    }

    private void handleExportIdentity() {
        try {
            handleExportIdentity0();
        } catch (Exception ex) {
            showError(ex.getMessage(), ex);
        }
    }

    private void handleExportIdentity0() throws IOException {
        ExportIdentityService exportIdentityService = new ExportIdentityService();
        JDeploy jdeploy = new JDeploy(packageJSONFile.getAbsoluteFile().getParentFile(), false);
        jdeploy.setNpmToken(context.getNpmToken());
        jdeploy.setUseManagedNode(context.useManagedNode());
        exportIdentityService.setDeveloperIdentityKeyStore(jdeploy.getKeyStore());
        FileDialog saveDialog = new FileDialog(frame, "Select Destination", FileDialog.SAVE);
        saveDialog.setVisible(true);
        File[] dest = saveDialog.getFiles();
        if (dest == null || dest.length == 0) {
            return;
        }
        exportIdentityService.exportIdentityToFile(dest[0]);
    }

    private boolean isNpmPublishingEnabled() {
        try {
            return DIContext.get(PublishTargetServiceInterface.class)
                    .getTargetsForProject(
                            packageJSONFile
                                    .getAbsoluteFile()
                                    .getParentFile()
                                    .getAbsolutePath(),
                            true
                    ).stream()
                    .anyMatch(t -> t.getType() == PublishTargetType.NPM);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isGitHubPublishingEnabled() {
        try {
            return DIContext.get(PublishTargetServiceInterface.class)
                    .getTargetsForProject(
                            packageJSONFile
                                    .getAbsoluteFile()
                                    .getParentFile()
                                    .getAbsolutePath(),
                            true
                    ).stream()
                    .anyMatch(t -> t.getType() == PublishTargetType.GITHUB);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getPublishTargetNames() {
        try {
            return DIContext.get(PublishTargetServiceInterface.class)
                    .getTargetsForProject(
                            packageJSONFile
                                    .getAbsoluteFile()
                                    .getParentFile()
                                    .getAbsolutePath(),
                            true
                    ).stream()
                    .map(t -> t.getType().name())
                    .collect(Collectors.joining(", "));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getDownloadPageUrl() {
        try {
            List<PublishTargetInterface> targets = DIContext.get(PublishTargetServiceInterface.class)
                    .getTargetsForProject(
                            packageJSONFile
                                    .getAbsoluteFile()
                                    .getParentFile()
                                    .getAbsolutePath(),
                            true
                    );
            PublishTargetInterface npmTarget = targets.stream().filter(t -> t.getType() == PublishTargetType.NPM).findFirst().orElse(null);
            if (npmTarget != null) {
                return "https://www.jdeploy.com/~"+packageJSON.getString("name");
            }

            PublishTargetInterface githubTarget = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
            if (githubTarget != null) {
                return githubTarget.getUrl() + "/releases/tag/" + packageJSON.getString("version");
            }

        } catch (IOException e) {
            return "https://www.jdeploy.com/~"+packageJSON.getString("name");
        }

        return "https://www.jdeploy.com/~"+packageJSON.getString("name");
    }

    private void handlePublish0() throws ValidationException {
        if (!EventQueue.isDispatchThread()) {
            // We don't prompt on the dispatch thread because promptForNpmToken blocks
            if (isNpmPublishingEnabled() && !context.promptForNpmToken(frame)) {
                return;
            }
            if (isGitHubPublishingEnabled() && !context.promptForGithubToken(frame)) {
                return;
            }
        }

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
            throw new ValidationException(
                    "The selected jar file is not a jar file.  Jar files must have the .jar extension"
            );
        }
        if (!jarFile.exists()) {
            throw new ValidationException(
                    "The selected jar file does not exist.  Please check the selected jar file and try again."
            );
        }
        // This validates that the jar file is an executable jar file.
        validateJar(jarFile);

        // Now let's make sure that this version isn't already published.
        String rawVersion = packageJSON.getString("version");
        String version = VersionCleaner.cleanVersion(packageJSON.getString("version"));
        String packageName = packageJSON.getString("name");
        String source = packageJSON.has("source") ? packageJSON.getString("source") : "";
        if (isNpmPublishingEnabled()) {
            if (getNPM().isVersionPublished(packageName, version, source)) {
                throw new ValidationException(
                        "The package " + packageName + " already has a published version " + version + ".  " +
                                "Please increment the version number and try to publish again."
                );
            }
            if (!getNPM().isLoggedIn()) {
                throw new ValidationException("You must be logged into NPM in order to publish", NOT_LOGGED_IN);
            }
        }

        if (isGitHubPublishingEnabled()) {
            GitHubPublishDriver gitHubPublishDriver = DIContext.get(GitHubPublishDriver.class);
            try {
                PublishTargetInterface githubTarget = DIContext.get(PublishTargetServiceInterface.class)
                        .getTargetsForProject(absDirectory.getAbsolutePath(), true)
                        .stream()
                        .filter(t -> t.getType() == PublishTargetType.GITHUB)
                        .findFirst()
                        .orElse(null);
                if (
                        gitHubPublishDriver.isVersionPublished(packageName, version, githubTarget)
                        || gitHubPublishDriver.isVersionPublished(packageName, rawVersion, githubTarget)
                ) {
                    throw new ValidationException(
                            "The package " + packageName + " already has a published version " + version + " on Github.  " +
                                    "Please increment the version number and try to publish again."
                    );
                }
            } catch (IOException ex) {
                throw new ValidationException("Failed to load github publish target", ex);
            }
        }

        // Let's check to see if we're logged into


        ProgressDialog progressDialog = new ProgressDialog(packageJSON.getString("name"), getDownloadPageUrl());

        PackagingContext packagingContext = PackagingContext.builder()
                .directory(packageJSONFile.getAbsoluteFile().getParentFile())
                .out(new PrintStream(progressDialog.createOutputStream()))
                .err(new PrintStream(progressDialog.createOutputStream()))
                .exitOnFail(false)
                .build();
        JDeploy jdeployObject = new JDeploy(packageJSONFile.getAbsoluteFile().getParentFile(), false);
        jdeployObject.setOut(packagingContext.out);
        jdeployObject.setErr(packagingContext.err);
        jdeployObject.setNpmToken(context.getNpmToken());
        jdeployObject.setUseManagedNode(context.useManagedNode());
        EventQueue.invokeLater(()->{
            progressDialog.show(frame, "Publishing in Progress...");
            progressDialog.setMessage1("Publishing "+packageJSON.get("name")+" to " + getPublishTargetNames()+".  Please wait...");
            progressDialog.setMessage2("");

        });
        try {
            handleSave();
            PublishingContext publishingContext = PublishingContext
                    .builder()
                    .setPackagingContext(packagingContext)
                    .setNPM(jdeployObject.getNPM())
                    .setGithubToken(context.getGithubToken())
                    .build();
            jdeployObject.publish(
                    publishingContext,
                    new SwingOneTimePasswordProvider(frame)
            );
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
