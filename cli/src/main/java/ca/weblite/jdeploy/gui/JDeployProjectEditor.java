package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.factories.PublishTargetFactory;
import ca.weblite.jdeploy.gui.controllers.EditGithubWorkflowController;
import ca.weblite.jdeploy.gui.controllers.GenerateGithubWorkflowController;
import ca.weblite.jdeploy.gui.controllers.VerifyWebsiteController;
import ca.weblite.jdeploy.gui.services.ProjectFileWatcher;
import ca.weblite.jdeploy.gui.services.SwingOneTimePasswordProvider;
import ca.weblite.jdeploy.gui.tabs.BundleFiltersPanel;
import ca.weblite.jdeploy.gui.tabs.CheerpJSettings;
import ca.weblite.jdeploy.gui.tabs.CliSettingsPanel;
import ca.weblite.jdeploy.gui.tabs.DetailsPanel;
import ca.weblite.jdeploy.gui.tabs.FiletypesPanel;
import ca.weblite.jdeploy.gui.tabs.PermissionsPanel;
import ca.weblite.jdeploy.gui.tabs.PublishSettingsPanel;
import ca.weblite.jdeploy.gui.tabs.RuntimeArgsPanel;
import ca.weblite.jdeploy.gui.tabs.SplashScreensPanel;
import ca.weblite.jdeploy.gui.tabs.UrlSchemesPanel;
import ca.weblite.jdeploy.helpers.NPMApplicationHelper;
import ca.weblite.jdeploy.ideInterop.IdeInteropInterface;
import ca.weblite.jdeploy.ideInterop.IdeInteropService;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.npm.TerminalLoginLauncher;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.packaging.PackagingPreferences;
import ca.weblite.jdeploy.packaging.PackagingPreferencesService;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTarget;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.services.*;
import ca.weblite.jdeploy.claude.SetupClaudeService;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.downloadPage.swing.DownloadPageSettingsPanel;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.MD5;
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
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.weblite.jdeploy.PathUtil.fromNativePath;
import static ca.weblite.jdeploy.PathUtil.toNativePath;

public class JDeployProjectEditor {
    public static final String JDEPLOY_WEBSITE_URL = System.getProperty("jdeploy.website.url", "https://www.jdeploy.com/");
    private boolean modified;
    private JSONObject packageJSON;
    private File packageJSONFile;
    private boolean processingJdpignoreChange = false;
    private boolean processingPackageJSONChange = false;
    private MainFields mainFields;
    private ArrayList<LinkFields> linkFields;
    private JFrame frame;
    private ProjectFileWatcher fileWatcher;

    private JDeployProjectEditorContext context = new JDeployProjectEditorContext();

    private JMenuItem generateGithubWorkflowMenuItem;
    private JMenuItem editGithubWorkflowMenuItem;
    
    private DownloadPageSettingsPanel downloadPageSettingsPanel;
    private PermissionsPanel permissionsPanel;
    private BundleFiltersPanel bundleFiltersPanel;
    private PublishSettingsPanel publishSettingsPanel;
    private SplashScreensPanel splashScreensPanel;
    private FiletypesPanel filetypesPanel;
    private UrlSchemesPanel urlSchemesPanel;
    private CliSettingsPanel cliSettingsPanel;
    private RuntimeArgsPanel runtimeArgsPanel;

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
        private JTextField name, title, version, iconUrl, jar, author,
                repository, repositoryDirectory, command, license, homepage;
        private JTextArea description;

        private JCheckBox javafx, jdk;
        private JComboBox javaVersion, jdkProvider, jbrVariant;
        private JButton icon, selectJar;
        private JButton verifyHomepageButton;
        private JLabel homepageVerifiedLabel;
    }

    private void handleFileChanged(ProjectFileWatcher.FileChangeEvent event) {
        if ("package.json".equals(event.filename())) {
            handlePackageJsonChanged(event);
        } else if (event.filename().startsWith(".jdpignore")) {
            handleJdpignoreChanged(event);
        }
    }

    private void handlePackageJsonChanged(ProjectFileWatcher.FileChangeEvent event) {
        if (processingPackageJSONChange) {
            return;
        }
        processingPackageJSONChange = true;
        try {
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

    private void handleJdpignoreChanged(ProjectFileWatcher.FileChangeEvent event) {
        if (processingJdpignoreChange || bundleFiltersPanel == null) {
            return;
        }
        processingJdpignoreChange = true;
        try {
            if (!modified && !bundleFiltersPanel.hasUnsavedChanges()) {
                // No unsaved changes, just reload
                bundleFiltersPanel.reloadAllFiles();
                try {
                    fileWatcher.refreshChecksums();
                } catch (java.io.IOException e) {
                    System.err.println("Failed to refresh checksums: " + e.getMessage());
                }
                return;
            }
            
            int result = JOptionPane.showConfirmDialog(frame,
                    "The " + event.filename() + " file has been modified externally. " +
                            "Would you like to reload it? Unsaved changes will be lost.",
                    "Reload " + event.filename() + "?",
                    JOptionPane.YES_NO_OPTION);

            if (result == JOptionPane.YES_OPTION) {
                bundleFiltersPanel.reloadAllFiles();
                try {
                    fileWatcher.refreshChecksums();
                } catch (java.io.IOException e) {
                    System.err.println("Failed to refresh checksums: " + e.getMessage());
                }
            }
        } catch (Exception ex) {
            System.err.println("A problem occurred while handling a file system change to " + event.filename());
            ex.printStackTrace(System.err);
        } finally {
            processingJdpignoreChange = false;
        }
    }

    private void reloadPackageJSON() throws IOException {
        packageJSON = new JSONObject(FileUtils.readFileToString(packageJSONFile, "UTF-8"));
        try {
            fileWatcher.refreshChecksums();
        } catch (java.io.IOException e) {
            System.err.println("Failed to refresh checksums after reload: " + e.getMessage());
        }
        clearModified();
        frame.getContentPane().removeAll();
        initMainFields(frame.getContentPane());
        frame.revalidate();
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
            this.fileWatcher = new ProjectFileWatcher(
                    packageJSONFile.getAbsoluteFile().getParentFile(),
                    packageJSONFile,
                    event -> EventQueue.invokeLater(() -> handleFileChanged(event))
            );
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Failed to initialize file watcher for the project directory.",
                    ex
            );
        }
    }

    private void initFrame() {
        frame = new JFrame("jDeploy");
        try {
            fileWatcher.startWatching();
        } catch (Exception ex) {
            System.err.println(
                    "A problem occurred while setting up file watcher on the project directory.  Disabling file watcher."
            );
            ex.printStackTrace(System.err);
        }
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClosing();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                fileWatcher.stopWatching();
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
        if (frame != null && frame.getRootPane() != null) {
            frame.getRootPane().putClientProperty("Window.documentModified", true);
        }
    }

    private void clearModified() {
        modified = false;
        if (frame != null && frame.getRootPane() != null) {
            frame.getRootPane().putClientProperty("Window.documentModified", false);
        }
    }

    private void updateJbrVariantVisibility() {
        if (mainFields == null || mainFields.jdkProvider == null || mainFields.jbrVariant == null) {
            return;
        }

        String selected = (String) mainFields.jdkProvider.getSelectedItem();
        boolean isJbr = "JetBrains Runtime (JBR)".equals(selected);

        // Find the parent panel containing the JBR variant field
        Container parent = mainFields.jbrVariant.getParent();
        if (parent != null) {
            // Make the field visible/invisible
            mainFields.jbrVariant.setVisible(isJbr);

            // Find and toggle the label visibility
            Component[] components = parent.getComponents();
            for (int i = 0; i < components.length; i++) {
                if (components[i] == mainFields.jbrVariant) {
                    // Look backward for the label
                    for (int j = i - 1; j >= 0; j--) {
                        if (components[j] instanceof JLabel) {
                            JLabel label = (JLabel) components[j];
                            if ("JBR Variant".equals(label.getText())) {
                                label.setVisible(isJbr);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
            parent.revalidate();
            parent.repaint();
        }

        // If not JBR, remove jbrVariant from package.json
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        if (!isJbr && jdeploy.has("jbrVariant")) {
            jdeploy.remove("jbrVariant");
        }
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
        cnt.setPreferredSize(new Dimension(1024, 768));
        DetailsPanel detailsPanel = new DetailsPanel();
        detailsPanel.getProjectPath().setText(packageJSONFile.getAbsoluteFile().getParentFile().getAbsolutePath());
        // Set a small font in the project path
        detailsPanel.getProjectPath().setFont(detailsPanel.getProjectPath().getFont().deriveFont(10f));

        detailsPanel.getCopyPath().setText("");
        detailsPanel.getCopyPath().setIcon(FontIcon.of(Material.CONTENT_COPY));
        detailsPanel.getCopyPath().addActionListener(evt -> {
            StringSelection stringSelection = new StringSelection(detailsPanel.getProjectPath().getText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        });
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


        // Splash screens are now handled by SplashScreensPanel
        splashScreensPanel = new SplashScreensPanel(packageJSONFile.getAbsoluteFile().getParentFile(), frame);
        splashScreensPanel.addChangeListener(evt -> setModified());
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
            mainFields.javaVersion.setSelectedItem("21");
        }
        mainFields.javaVersion.addItemListener(evt -> {
            jdeploy.put("javaVersion", mainFields.javaVersion.getSelectedItem());
            setModified();
        });

        // JDK Provider field
        mainFields.jdkProvider = detailsPanel.getJdkProvider();
        mainFields.jdkProvider.setToolTipText("Auto: Automatically selects the best JDK provider for your platform (Zulu, Adoptium, or Liberica). JBR: Use JetBrains Runtime for applications requiring JCEF or enhanced rendering.");

        // Load existing value from package.json
        if (jdeploy.has("jdkProvider")) {
            String provider = jdeploy.getString("jdkProvider");
            if ("jbr".equals(provider)) {
                mainFields.jdkProvider.setSelectedItem("JetBrains Runtime (JBR)");
            } else {
                // For any other explicit provider (zulu, adoptium, liberica),
                // show as Auto in the GUI but preserve the value in package.json
                // This allows advanced users to set providers manually
                mainFields.jdkProvider.setSelectedItem("Auto (Recommended)");
            }
        } else {
            mainFields.jdkProvider.setSelectedItem("Auto (Recommended)");
        }

        mainFields.jdkProvider.addItemListener(evt -> {
            String selected = (String) mainFields.jdkProvider.getSelectedItem();

            // Update package.json
            if ("Auto (Recommended)".equals(selected)) {
                // Remove jdkProvider to use automatic selection
                jdeploy.remove("jdkProvider");
            } else if ("JetBrains Runtime (JBR)".equals(selected)) {
                jdeploy.put("jdkProvider", "jbr");
            }

            // Show/hide JBR variant field
            updateJbrVariantVisibility();
            setModified();
        });

        // JBR Variant field
        mainFields.jbrVariant = detailsPanel.getJbrVariant();
        mainFields.jbrVariant.setToolTipText("JBR variant to use. Default uses standard or standard+SDK based on whether JDK is required. JCEF includes Chromium Embedded Framework for embedded browsers.");

        // Load existing value from package.json
        if (jdeploy.has("jbrVariant")) {
            String variant = jdeploy.getString("jbrVariant");
            if ("jcef".equals(variant)) {
                mainFields.jbrVariant.setSelectedItem("JCEF");
            } else {
                // For any other variant (standard, sdk, sdk_jcef), show as Default
                // and remove from package.json to use automatic selection
                mainFields.jbrVariant.setSelectedItem("Default");
                jdeploy.remove("jbrVariant");
            }
        } else {
            mainFields.jbrVariant.setSelectedItem("Default");
        }

        mainFields.jbrVariant.addItemListener(evt -> {
            String selected = (String) mainFields.jbrVariant.getSelectedItem();

            if ("Default".equals(selected)) {
                // Remove jbrVariant to use automatic selection based on jdk requirement
                jdeploy.remove("jbrVariant");
            } else if ("JCEF".equals(selected)) {
                jdeploy.put("jbrVariant", "jcef");
            }
            setModified();
        });

        // Set initial visibility
        updateJbrVariantVisibility();

        // URL schemes are now handled by UrlSchemesPanel
        urlSchemesPanel = new UrlSchemesPanel();
        urlSchemesPanel.load(jdeploy);
        urlSchemesPanel.addChangeListener(evt -> {
            urlSchemesPanel.save(jdeploy);
            setModified();
        });

        // Create FiletypesPanel for file type and directory associations
        filetypesPanel = new FiletypesPanel(packageJSONFile.getAbsoluteFile().getParentFile());
        filetypesPanel.load(jdeploy);
        filetypesPanel.addChangeListener(evt -> setModified());

        JPanel filetypesPanelWrapper = new JPanel();
        filetypesPanelWrapper.setOpaque(false);
        filetypesPanelWrapper.setLayout(new BorderLayout());
        filetypesPanelWrapper.add(filetypesPanel.getRoot(), BorderLayout.CENTER);
        JPanel cheerpjSettingsRoot = null;
        if (context.shouldDisplayCheerpJPanel()) {
            CheerpJSettings cheerpJSettings = new CheerpJSettings();
            cheerpJSettings.getButtons().add(
                    createHelpButton(
                            JDEPLOY_WEBSITE_URL + "docs/help/#cheerpj",
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
                        JDEPLOY_WEBSITE_URL + "docs/help/#_the_details_tab",
                        "",
                        "Learn about what these fields do."
                )
        );
        detailWrapper.add(helpPanel, BorderLayout.NORTH);
        detailWrapper.add(detailsPanelRoot, BorderLayout.CENTER);



        tabs.addTab("Details", detailWrapper);

        JPanel splashScreensWrapper = new JPanel();
        splashScreensWrapper.setLayout(new BorderLayout());
        splashScreensWrapper.setOpaque(false);
        splashScreensWrapper.add(splashScreensPanel.getRoot(), BorderLayout.CENTER);
        
        JPanel splashScreensHelpPanel = new JPanel();
        splashScreensHelpPanel.setOpaque(false);
        splashScreensHelpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        splashScreensHelpPanel.add(
                createHelpButton(
                        JDEPLOY_WEBSITE_URL + "docs/help/#splashscreens",
                        "",
                        "Learn more about this panel, and how splash screen images are used in jDeploy."
                )
        );
        
        splashScreensWrapper.add(splashScreensHelpPanel, BorderLayout.NORTH);
        tabs.add("Splash Screens", splashScreensWrapper);

        tabs.add("Filetypes", filetypesPanelWrapper);

        // URLs tab is now handled by UrlSchemesPanel
        JPanel urlsPanelWrapper = new JPanel();
        urlsPanelWrapper.setLayout(new BorderLayout());
        urlsPanelWrapper.setOpaque(false);
        
        JPanel urlsHelpPanel = new JPanel();
        urlsHelpPanel.setOpaque(false);
        urlsHelpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        urlsHelpPanel.add(
                createHelpButton(
                        JDEPLOY_WEBSITE_URL + "docs/help/#_the_urls_tab",
                        "",
                        "Learn more about custom URL schemes in jDeploy"
                )
        );
        
        urlsPanelWrapper.add(urlsHelpPanel, BorderLayout.NORTH);
        urlsPanelWrapper.add(urlSchemesPanel.getRoot(), BorderLayout.CENTER);
        
        tabs.add("URLs", urlsPanelWrapper);

        // CLI settings panel
        cliSettingsPanel = new CliSettingsPanel();
        cliSettingsPanel.load(jdeploy);
        cliSettingsPanel.addChangeListener(evt -> setModified());
        cliSettingsPanel.getTutorialButton().addActionListener(evt -> {
            try {
                context.browse(new URI(JDEPLOY_WEBSITE_URL + "docs/getting-started-tutorial-cli/"));
            } catch (Exception ex) {
                System.err.println("Failed to open cli tutorial.");
                ex.printStackTrace(System.err);
                JOptionPane.showMessageDialog(frame,
                        new JLabel(
                                "<html>" +
                                        "<p style='width:400px'>" +
                                        "Failed to open the CLI tutorial.  " +
                                        "Try opening " + JDEPLOY_WEBSITE_URL + "docs/getting-started-tutorial-cli/ " +
                                        "manually in your browser." +
                                        "</p>" +
                                        "</html>"
                        ),
                        "Failed to Open",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        tabs.add("CLI", cliSettingsPanel.getRoot());

        // Runtime Args panel
        runtimeArgsPanel = new RuntimeArgsPanel();
        runtimeArgsPanel.load(jdeploy);
        runtimeArgsPanel.addChangeListener(evt -> setModified());

        JPanel runtimeArgsPanelWrapper = new JPanel();
        runtimeArgsPanelWrapper.setLayout(new BorderLayout());
        runtimeArgsPanelWrapper.setOpaque(false);

        JPanel runtimeArgsHelpPanel = new JPanel();
        runtimeArgsHelpPanel.setOpaque(false);
        runtimeArgsHelpPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        runtimeArgsHelpPanel.add(
                createHelpButton(
                        JDEPLOY_WEBSITE_URL + "docs/help/#runargs",
                        "",
                        "Open run arguments help in web browser"
                )
        );

        runtimeArgsPanelWrapper.add(runtimeArgsHelpPanel, BorderLayout.NORTH);
        runtimeArgsPanelWrapper.add(runtimeArgsPanel.getRoot(), BorderLayout.CENTER);

        tabs.add("Runtime Args", runtimeArgsPanelWrapper);

        if (context.shouldDisplayCheerpJPanel()) {
            tabs.add("CheerpJ", cheerpjSettingsRoot);
        }
        
        // Permissions panel
        permissionsPanel = new PermissionsPanel();
        permissionsPanel.loadPermissions(packageJSON);
        permissionsPanel.addChangeListener(evt -> {
            permissionsPanel.savePermissions(packageJSON);
            setModified();
        });
        tabs.add("Permissions", permissionsPanel);

        // Add Bundle Filters tab
        bundleFiltersPanel = new BundleFiltersPanel(packageJSONFile.getParentFile());
        bundleFiltersPanel.setOnChangeCallback(() -> setModified());
        bundleFiltersPanel.loadConfiguration(packageJSON);
        
        // Set up NPM enabled checker to check current UI state
        bundleFiltersPanel.setNpmEnabledChecker(() -> {
            return publishSettingsPanel != null && publishSettingsPanel.getNpmCheckbox().isSelected();
        });
        
        tabs.add("Platform-Specific Bundles", bundleFiltersPanel);
        
        downloadPageSettingsPanel = new DownloadPageSettingsPanel(loadDownloadPageSettings());
        downloadPageSettingsPanel.addChangeListener(evt -> {
            this.saveDownloadPageSettings(downloadPageSettingsPanel.getSettings());
            setModified();

        });
        tabs.add("Download Page", downloadPageSettingsPanel);

        if (context.shouldDisplayPublishSettingsTab()) {
            tabs.add("Publish Settings", createPublishSettingsPanel());
        }

        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(tabs, BorderLayout.CENTER);
        
        // Add tab change listener to refresh Platform-Specific Bundles panel
        // This handles cases where publish settings change but package.json hasn't been saved yet
        tabs.addChangeListener(e -> {
            int selectedIndex = tabs.getSelectedIndex();
            if (selectedIndex >= 0) {
                String tabTitle = tabs.getTitleAt(selectedIndex);
                if ("Platform-Specific Bundles".equals(tabTitle) && bundleFiltersPanel != null) {
                    bundleFiltersPanel.refreshUI();
                }
            }
        });

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

        JCheckBox buildOnPublish = new JCheckBox("Build Project");
        buildOnPublish.setToolTipText("Build the project before publishing.  " +
                "This will ensure that the latest changes are included in the published app.");
        PackagingPreferencesService packagingPreferencesService = DIContext.get(PackagingPreferencesService.class);
        PackagingPreferences packagingPreferences = packagingPreferencesService.getPackagingPreferences(packageJSONFile.getAbsolutePath());
        buildOnPublish.setSelected(packagingPreferences.isBuildProjectBeforePackaging());
        buildOnPublish.addActionListener(evt->{
            packagingPreferences.setBuildProjectBeforePackaging(buildOnPublish.isSelected());
            packagingPreferencesService.setPackagingPreferences(packagingPreferences);
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
            bottomButtons.add(buildOnPublish);
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
        publishSettingsPanel = new PublishSettingsPanel();
        PublishSettingsPanel panel = publishSettingsPanel;
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
                        if (existingNpm == null || existingNpm.isDefault()) {
                            if (existingNpm != null && existingNpm.isDefault()) {
                                targets.remove(existingNpm);
                            }
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
                            String githubUrl = panel.getGithubRepositoryField().getText();
                            String name = "github: " + (githubUrl.isEmpty() ? packageJSON.getString("name") : githubUrl);
                            targets.add(new PublishTarget(name, PublishTargetType.GITHUB, githubUrl));
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
                    if (!panel.getGithubCheckbox().isSelected()) {
                        return; // Only update URL if GitHub is actually selected
                    }
                    try {
                        PublishTargetInterface existingGithub = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
                        if (existingGithub != null) {
                            targets.remove(existingGithub);
                        }
                        // Explicitly create GitHub target instead of relying on URL-based factory logic
                        String githubUrl = panel.getGithubRepositoryField().getText();
                        String name = "github: " + (githubUrl.isEmpty() ? packageJSON.getString("name") : githubUrl);
                        PublishTargetInterface newGithub = new PublishTarget(name, PublishTargetType.GITHUB, githubUrl);
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

        JMenu openInIde = new JMenu("Open in IDE");
        IdeInteropService ideInteropService = DIContext.get(IdeInteropService.class);
        openInIde.setToolTipText("Open the project directory in your IDE");
        try {
            for (IdeInteropInterface ideInterop : ideInteropService.findAll()) {
                try {
                    JMenuItem ideMenuItem = new JMenuItem(ideInterop.getName());
                    ideMenuItem.setToolTipText("Open the project directory in " + ideInterop.getPath());
                    ideMenuItem.addActionListener(evt -> {
                        SwingWorker worker = new SwingWorker() {
                            @Override
                            protected Object doInBackground() throws Exception {
                                try {
                                    ideInterop.openProject(packageJSONFile.getParentFile().getAbsolutePath());
                                } catch (Exception ex) {
                                    showError("Failed to open project directory in " + ideInterop.getName(), ex);
                                }
                                return null;
                            }
                        };

                        worker.execute();
                    });
                    openInIde.add(ideMenuItem);
                } catch (Exception ex) {
                    System.err.println("Failed to create menu item for IDE: " + ideInterop.getName());
                    ex.printStackTrace(System.err);
                }
            }
        } catch (Exception ideInteropException) {
            ideInteropException.printStackTrace();
        }

        file.addSeparator();
        file.add(openInTextEditor);
        file.add(openProjectDirectory);
        file.add(openInIde);

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
        
        JMenuItem setupClaude = new JMenuItem("Setup Claude AI Assistant");
        setupClaude.setToolTipText(
                "Setup Claude AI assistant for this project by adding jDeploy-specific instructions to CLAUDE.md"
        );
        setupClaude.addActionListener(evt->{
            handleSetupClaude();
        });
        
        file.addSeparator();
        file.add(verifyHomepage);
        file.add(setupClaude);

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
                JDEPLOY_WEBSITE_URL + "docs/help",
                "jDeploy Help", "Open jDeploy application help in your web browser"
        );
        help.add(jdeployHelp);

        help.addSeparator();
        help.add(createLinkItem(
                JDEPLOY_WEBSITE_URL,
                "jDeploy Website",
                "Open the jDeploy website in your web browser."
        ));
        help.add(createLinkItem(
                JDEPLOY_WEBSITE_URL + "docs/manual",
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
    
    private void handleSetupClaude() {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                SetupClaudeService service = new SetupClaudeService();
                File projectDirectory = packageJSONFile.getAbsoluteFile().getParentFile();
                service.setup(projectDirectory);
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(
                        frame,
                        "Claude AI assistant has been successfully set up for this project.\nCLAUDE.md file has been created/updated with jDeploy-specific instructions.",
                        "Claude Setup Complete",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception ex) {
                    showError("Failed to setup Claude AI assistant: " + ex.getMessage(), ex);
                }
            }
        };
        worker.execute();
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
            if (downloadPageSettingsPanel != null) {
                saveDownloadPageSettings(downloadPageSettingsPanel.getSettings());
            }
            if (bundleFiltersPanel != null) {
                bundleFiltersPanel.saveConfiguration(packageJSON.getJSONObject("jdeploy"));
                bundleFiltersPanel.saveAllFiles();
            }

            // Validate and save filetypes panel
            JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
            if (filetypesPanel != null) {
                String validationError = filetypesPanel.validateDirectoryAssociation();
                if (validationError != null) {
                    int result = JOptionPane.showConfirmDialog(
                        frame,
                        validationError + "\n\nContinue saving anyway?",
                        "Validation Warning",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    );
                    if (result != JOptionPane.YES_OPTION) {
                        return; // Don't save
                    }
                }
                filetypesPanel.save(jdeploy);
            }
            
            // Save URL schemes panel
            if (urlSchemesPanel != null) {
                urlSchemesPanel.save(jdeploy);
            }

            // Save CLI settings panel
            if (cliSettingsPanel != null) {
                cliSettingsPanel.save(jdeploy);
            }

            // Save Runtime Args panel
            if (runtimeArgsPanel != null) {
                runtimeArgsPanel.save(jdeploy);
            }

            FileUtil.writeStringToFile(packageJSON.toString(4), packageJSONFile);
            try {
                fileWatcher.refreshChecksums();
            } catch (java.io.IOException e) {
                System.err.println("Failed to refresh file checksums after save: " + e.getMessage());
            }
            clearModified();
            context.onFileUpdated(packageJSONFile);
        } catch (Exception ex) {
            System.err.println("Failed to save "+packageJSONFile+": "+ex.getMessage());
            ex.printStackTrace(System.err);
            showError("Failed to save package.json file. "+ex.getMessage(), ex);
        }
    }

    private void showError(String message, Throwable exception) {
        File logFile = exception instanceof ValidationException
                ? ((ValidationException) exception).getLogFile()
                : null;

        JPanel dialogComponent = new JPanel();
        dialogComponent.setLayout(new BoxLayout(dialogComponent, BoxLayout.Y_AXIS));
        dialogComponent.setOpaque(false);
        dialogComponent.setBorder(new EmptyBorder(10, 10, 10, 10));
        dialogComponent.add(new JLabel(
                "<html><p style='width:400px'>" + message + "</p></html>"
        ));

        if (logFile != null) {
            String[] options = {"Copy Path", "OK"};
            int choice = JOptionPane.showOptionDialog(
                    frame,
                    dialogComponent,
                    "Error",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.ERROR_MESSAGE,
                    null,
                    options,
                    options[1]
            );

            if (choice == 0) { // Copy Path selected
                try {
                    StringSelection stringSelection = new StringSelection(logFile.getAbsolutePath());
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(stringSelection, null);
                } catch (Exception ex) {
                    showError("Failed to copy path to clipboard. " + ex.getMessage(), ex);
                }
            }
        } else {
            JOptionPane.showMessageDialog(
                    frame,
                    dialogComponent,
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        exception.printStackTrace(System.err);
    }


    private static final int NOT_LOGGED_IN = 1;

    private class ValidationException extends Exception {
        private int type;

        private File logFile;

        ValidationException(String msg){
            this(msg, (File)null);
        }

        ValidationException(String msg, File logFile) {
            super(msg);
            this.logFile = logFile;
        }

        ValidationException(String msg, int type) {
            super(msg);
            this.type = type;
        }

        ValidationException(String msg, int type, File logFile) {
            super(msg);
            this.type = type;
            this.logFile = logFile;
        }

        ValidationException(String msg, Throwable cause, File logFile) {
            super(msg, cause);
            this.logFile = logFile;
        }

        ValidationException(String msg, Throwable cause) {
            super(msg, cause);
        }

        ValidationException(String msg, Throwable cause, int type, File logFile) {
            super(msg, cause);
            this.type = type;
            this.logFile = logFile;
        }

        ValidationException(String msg, Throwable cause, int type) {
            super(msg, cause);
            this.type = type;
        }

        int getType() {
            return type;
        }

        File getLogFile() {
            return logFile;
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
                            "\nPlease see " + JDEPLOY_WEBSITE_URL + "docs/manual/#_appendix_building_executable_jar_file"
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
                return JDEPLOY_WEBSITE_URL + "~"+packageJSON.getString("name");
            }

            PublishTargetInterface githubTarget = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
            if (githubTarget != null) {
                return githubTarget.getUrl() + "/releases/tag/" + packageJSON.getString("version");
            }

        } catch (IOException e) {
            return JDEPLOY_WEBSITE_URL + "~"+packageJSON.getString("name");
        }

        return JDEPLOY_WEBSITE_URL + "~"+packageJSON.getString("name");
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

        ProjectBuilderService projectBuilderService = DIContext.get(ProjectBuilderService.class);
        PackagingPreferencesService packagingPreferencesService = DIContext.get(PackagingPreferencesService.class);
        PackagingPreferences packagingPreferences = packagingPreferencesService.getPackagingPreferences(packageJSONFile.getAbsolutePath());
        boolean buildRequired = packagingPreferences.isBuildProjectBeforePackaging();
        PackagingContext buildContext = PackagingContext.builder()
                .directory(absDirectory)
                .exitOnFail(false)
                .isBuildRequired(buildRequired)
                .build();

        if (!jarFile.exists()) {
            // If the jar file doesn't exist, then we need to build the project.
            if (projectBuilderService.isBuildSupported(buildContext)) {
                try {
                    File buildLogFile = File.createTempFile("jdeploy-build-log", ".txt");
                    final JDialog[] buildProgressDialog = new JDialog[1];
                    try {

                        EventQueue.invokeLater(() -> {
                            buildProgressDialog[0] = createProgressDialog(
                                    "Building Project",
                                    "Building project.  Please wait..."
                            );
                            buildProgressDialog[0].setVisible(true);
                        });
                        projectBuilderService.buildProject(buildContext, buildLogFile);
                        buildRequired = false;
                    } catch (Exception ex) {
                        ex.printStackTrace(System.err);
                        throw new ValidationException(
                                "Failed to build project before publishing.  See build log at "
                                        + buildLogFile.getAbsolutePath(),
                                ex,
                                buildLogFile
                        );
                    } finally {
                        EventQueue.invokeLater(()-> {
                            if (buildProgressDialog[0] != null) {
                                buildProgressDialog[0].setVisible(false);
                                buildProgressDialog[0].dispose();
                            }
                        });
                    }
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                    throw new ValidationException("Failed to create build log file", ex);
                }

            }
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
                .isBuildRequired(buildRequired)
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
            packagingContext.err.println("An error occurred during publishing");
            ex.printStackTrace(packagingContext.err);
            EventQueue.invokeLater(() -> {
                progressDialog.setFailed();
            });
        }
    }

    private JDialog createProgressDialog(String title, String message) {
        JDialog dialog = new JDialog(frame, title, true);
        dialog.setLayout(new FlowLayout());
        dialog.add(new JLabel(message));
        JProgressBar progressBar = progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        dialog.add(progressBar);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setModal(false);
        return dialog;
    }
    
    private DownloadPageSettings loadDownloadPageSettings() {
        DownloadPageSettingsService service = DIContext.get(DownloadPageSettingsService.class);
        return service.read(packageJSON);
    }
    
    private void saveDownloadPageSettings(DownloadPageSettings settings) {
        DownloadPageSettingsService service = DIContext.get(DownloadPageSettingsService.class);
        service.write(settings, packageJSON);
    }
}
