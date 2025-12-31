package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.factories.PublishTargetFactory;
import ca.weblite.jdeploy.gui.controllers.EditGithubWorkflowController;
import ca.weblite.jdeploy.gui.controllers.GenerateGithubWorkflowController;
import ca.weblite.jdeploy.gui.controllers.PublishActionHandler;
import ca.weblite.jdeploy.gui.controllers.VerifyWebsiteController;
import ca.weblite.jdeploy.gui.navigation.EditorPanelRegistry;
import ca.weblite.jdeploy.gui.navigation.NavigablePanelAdapter;
import ca.weblite.jdeploy.gui.navigation.NavigationHost;
import ca.weblite.jdeploy.gui.navigation.TabbedPaneNavigationHost;
import ca.weblite.jdeploy.gui.services.ProjectFileWatcher;
import ca.weblite.jdeploy.gui.services.PublishingCoordinator;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.gui.tabs.BundleFiltersPanel;
import ca.weblite.jdeploy.gui.tabs.CheerpJSettingsPanel;
import ca.weblite.jdeploy.gui.tabs.CliSettingsPanel;
import ca.weblite.jdeploy.gui.tabs.FiletypesPanel;
import ca.weblite.jdeploy.gui.tabs.JavaRuntimePanel;
import ca.weblite.jdeploy.gui.tabs.RepositorySettingsPanel;
import ca.weblite.jdeploy.gui.tabs.PermissionsPanel;
import ca.weblite.jdeploy.gui.tabs.ProjectMetadataPanel;
import ca.weblite.jdeploy.gui.tabs.PublishSettingsPanel;
import ca.weblite.jdeploy.gui.tabs.RuntimeArgsPanel;
import ca.weblite.jdeploy.gui.tabs.SplashScreensPanel;
import ca.weblite.jdeploy.gui.tabs.UrlSchemesPanel;
import javax.swing.JTabbedPane;
import ca.weblite.jdeploy.helpers.NPMApplicationHelper;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.packaging.PackagingPreferences;
import ca.weblite.jdeploy.packaging.PackagingPreferencesService;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.services.*;
import ca.weblite.jdeploy.claude.SetupClaudeService;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.downloadPage.swing.DownloadPageSettingsPanel;
import ca.weblite.tools.io.FileUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.util.List;

public class JDeployProjectEditor {
    private boolean modified;
    private JSONObject packageJSON;
    private final File packageJSONFile;
    private boolean processingJdpignoreChange = false;
    private boolean processingPackageJSONChange = false;
    private JFrame frame;
    private final ProjectFileWatcher fileWatcher;

    private JDeployProjectEditorContext context = new JDeployProjectEditorContext();

    private ProjectMetadataPanel projectMetadataPanel;
    private JavaRuntimePanel javaRuntimePanel;
    private RepositorySettingsPanel repositorySettingsPanel;
    private DownloadPageSettingsPanel downloadPageSettingsPanel;
    private PermissionsPanel permissionsPanel;
    private BundleFiltersPanel bundleFiltersPanel;
    private PublishSettingsPanel publishSettingsPanel;
    private FiletypesPanel filetypesPanel;
    private UrlSchemesPanel urlSchemesPanel;
    private CliSettingsPanel cliSettingsPanel;
    private RuntimeArgsPanel runtimeArgsPanel;
    private CheerpJSettingsPanel cheerpJSettingsPanel;
    private SplashScreensPanel splashScreensPanel;
    private EditorPanelRegistry registry;
    private PublishActionHandler publishActionHandler;
    private final PublishingCoordinator publishingCoordinator;

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
        this.publishingCoordinator = new PublishingCoordinator(packageJSONFile, packageJSON);
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
        // Initialize the publishActionHandler with the frame reference
        this.publishActionHandler = new PublishActionHandler(
                frame,
                packageJSONFile,
                packageJSON,
                context,
                publishingCoordinator,
                this::handleSave,
                this::getDownloadPageUrl
        );
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

        return JOptionPane.showOptionDialog(frame,
                "There's still something unsaved.\n" +
                        "Do you want to save before exiting?",
                "Warning",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
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
                    if (repositorySettingsPanel != null) {
                        repositorySettingsPanel.setVerifyButtonVisible(!verified);
                        frame.revalidate();
                    }
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
        cnt.setPreferredSize(new Dimension(1024, 768));
        File projectDir = packageJSONFile.getAbsoluteFile().getParentFile();
        
        // Ensure jdeploy object exists
        if (!packageJSON.has("jdeploy")) {
            packageJSON.put("jdeploy", new JSONObject());
            setModified();
        }

        // Initialize PublishActionHandler with dependencies
        this.publishActionHandler = new PublishActionHandler(
                frame,
                packageJSONFile,
                packageJSON,
                context,
                publishingCoordinator,
                this::handleSave,
                this::getDownloadPageUrl
        );

        // Setup Project Metadata Panel
        projectMetadataPanel = new ProjectMetadataPanel(
            frame,
            projectDir,
            context.getFileChooserInterop()
        );
        projectMetadataPanel.addChangeListener(evt -> setModified());

        // Setup Java Runtime Panel
        javaRuntimePanel = new JavaRuntimePanel(
            frame,
            projectDir,
            jarFile -> {
                PublishingCoordinator.ValidationResult result = publishingCoordinator.validateJar(jarFile);
                if (result.isValid()) {
                    return JavaRuntimePanel.ValidationResult.success();
                } else {
                    return JavaRuntimePanel.ValidationResult.failure(result.getErrorMessage());
                }
            }
        );
        javaRuntimePanel.addChangeListener(evt -> setModified());

        // Setup Repository Settings Panel
        repositorySettingsPanel = new RepositorySettingsPanel(
            frame,
            homepage -> handleVerifyHomepage()
        );
        repositorySettingsPanel.addChangeListener(evt -> {
            setModified();
            queueHomepageVerification();
        });

        queueHomepageVerification();

        // Create panel registry and populate with all panels
        registry = createPanelRegistry();
        
        // Attach change listeners
        registry.attachChangeListeners(this::setModified);
        
        // Load all panels
        registry.loadAll(packageJSON, packageJSON.optJSONObject("jdeploy"));
        
        // Create navigation host
        NavigationHost host = new TabbedPaneNavigationHost(context, frame);
        registry.populateHost(host);
        
        // Setup container
        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(host.getComponent(), BorderLayout.CENTER);
        
        // Add tab change listener to refresh Platform-Specific Bundles panel
        if (host.getComponent() instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) host.getComponent();
            tabs.addChangeListener(e -> {
                int selectedIndex = tabs.getSelectedIndex();
                if (selectedIndex >= 0) {
                    String tabTitle = tabs.getTitleAt(selectedIndex);
                    if ("Platform-Specific Bundles".equals(tabTitle) && bundleFiltersPanel != null) {
                        bundleFiltersPanel.refreshUI();
                    }
                }
            });
        }

        // Setup bottom button bar
        JPanel bottomButtons = new JPanel();
        bottomButtons.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JButton viewDownloadPage = new JButton("View Download Page");
        viewDownloadPage.addActionListener(evt->{
            try {
                context.browse(new URI(getDownloadPageUrl()));
            } catch (Exception ex) {
                showError("Failed to open download page.  " + ex.getMessage(), ex);
            }
        });

        JButton preview = new JButton("Web Preview");
        preview.addActionListener(evt-> context.showWebPreview(frame));

        JButton publish = getPublishButton();

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

    private JButton getPublishButton() {
        JButton publish = new JButton("Publish");
        publish.setDefaultCapable(true);

        publish.addActionListener(evt->{
            String publishTargetName = publishingCoordinator.getPublishTargetNames();
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
            if (result == JOptionPane.YES_OPTION) {
                publishActionHandler.handlePublish();
            }
        });
        return publish;
    }

    private EditorPanelRegistry createPanelRegistry() {
        EditorPanelRegistry registry = new EditorPanelRegistry();
        File projectDir = packageJSONFile.getAbsoluteFile().getParentFile();

        // Project Metadata Panel
        registry.register(NavigablePanelAdapter.forPackageJsonPanel(
            "Project",
            MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#_the_details_tab",
            projectMetadataPanel.getRoot(),
            json -> projectMetadataPanel.load(json),
            json -> projectMetadataPanel.save(json),
            listener -> projectMetadataPanel.addChangeListener(listener)
        ));

        // Java Runtime Panel
        registry.register(NavigablePanelAdapter.forJdeployPanel(
            "Build",
            MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#_the_details_tab",
            javaRuntimePanel.getRoot(),
            json -> javaRuntimePanel.load(json),
            json -> javaRuntimePanel.save(json),
            listener -> javaRuntimePanel.addChangeListener(listener)
        ));

        // Repository Settings Panel
        registry.register(NavigablePanelAdapter.forPackageJsonPanel(
            "Repository",
            MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#_the_details_tab",
            repositorySettingsPanel.getRoot(),
            json -> repositorySettingsPanel.load(json),
            json -> repositorySettingsPanel.save(json),
            listener -> repositorySettingsPanel.addChangeListener(listener)
        ));

        // Splash Screens Panel
        splashScreensPanel = new SplashScreensPanel(projectDir, frame);
        registry.register(NavigablePanelAdapter.forJdeployPanel(
            "Splash Screens",
            MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#splashscreens",
            splashScreensPanel.getRoot(),
            json -> splashScreensPanel.load(json),
            json -> splashScreensPanel.save(json),
            listener -> splashScreensPanel.addChangeListener(listener)
        ));

        // Filetypes Panel
        filetypesPanel = new FiletypesPanel(projectDir);
        registry.register(NavigablePanelAdapter.forJdeployPanel(
            "Filetypes",
            null,
            filetypesPanel.getRoot(),
            json -> filetypesPanel.load(json),
            json -> filetypesPanel.save(json),
            listener -> filetypesPanel.addChangeListener(listener)
        ));

        // URL Schemes Panel
        urlSchemesPanel = new UrlSchemesPanel();
        registry.register(NavigablePanelAdapter.forJdeployPanel(
            "URLs",
            MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#_the_urls_tab",
            urlSchemesPanel.getRoot(),
            json -> urlSchemesPanel.load(json),
            json -> urlSchemesPanel.save(json),
            listener -> urlSchemesPanel.addChangeListener(listener)
        ));

        // CLI Settings Panel
        cliSettingsPanel = new CliSettingsPanel();
        registry.register(NavigablePanelAdapter.forPackageJsonPanel(
            "CLI",
            null,
            cliSettingsPanel.getRoot(),
            json -> cliSettingsPanel.load(json),
            json -> cliSettingsPanel.save(json),
            listener -> {
                cliSettingsPanel.addChangeListener(listener);
                cliSettingsPanel.getTutorialButton().addActionListener(evt -> {
                    try {
                        context.browse(new URI(MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/getting-started-tutorial-cli/"));
                    } catch (Exception ex) {
                        System.err.println("Failed to open cli tutorial.");
                        ex.printStackTrace(System.err);
                        JOptionPane.showMessageDialog(frame,
                                new JLabel(
                                        "<html>" +
                                        "<p style='width:400px'>" +
                                        "Failed to open the CLI tutorial.  " +
                                        "Try opening " + MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/getting-started-tutorial-cli/ " +
                                        "manually in your browser." +
                                        "</p>" +
                                        "</html>"
                                ),
                                "Failed to Open",
                                JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        ));

        // Runtime Args Panel
        runtimeArgsPanel = new RuntimeArgsPanel();
        registry.register(NavigablePanelAdapter.forJdeployPanel(
            "Runtime Args",
            MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#runargs",
            runtimeArgsPanel.getRoot(),
            json -> runtimeArgsPanel.load(json),
            json -> runtimeArgsPanel.save(json),
            listener -> runtimeArgsPanel.addChangeListener(listener)
        ));

        // CheerpJ Settings Panel
        if (context.shouldDisplayCheerpJPanel()) {
            cheerpJSettingsPanel = new CheerpJSettingsPanel();
            registry.register(NavigablePanelAdapter.forJdeployPanel(
                "CheerpJ",
                MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#cheerpj",
                cheerpJSettingsPanel.getRoot(),
                json -> cheerpJSettingsPanel.load(json),
                json -> cheerpJSettingsPanel.save(json),
                listener -> cheerpJSettingsPanel.addChangeListener(listener),
                () -> context.shouldDisplayCheerpJPanel()
            ));
        }

        // Permissions Panel
        permissionsPanel = new PermissionsPanel();
        registry.register(NavigablePanelAdapter.forPackageJsonPanel(
            "Permissions",
            null,
            permissionsPanel,
            json -> permissionsPanel.loadPermissions(json),
            json -> permissionsPanel.savePermissions(json),
            listener -> permissionsPanel.addChangeListener(listener)
        ));

        // Bundle Filters Panel
        bundleFiltersPanel = new BundleFiltersPanel(projectDir);
        bundleFiltersPanel.setNpmEnabledChecker(() -> publishSettingsPanel != null && publishSettingsPanel.getNpmCheckbox().isSelected());
        registry.register(NavigablePanelAdapter.forJdeployPanel(
            "Platform-Specific Bundles",
            null,
            bundleFiltersPanel.getRoot(),
            json -> bundleFiltersPanel.loadConfiguration(packageJSON),
            json -> {
                bundleFiltersPanel.saveConfiguration(json);
                bundleFiltersPanel.saveAllFiles();
            },
            listener -> bundleFiltersPanel.setOnChangeCallback(() -> listener.actionPerformed(null))
        ));

        // Download Page Settings Panel
        downloadPageSettingsPanel = new DownloadPageSettingsPanel(loadDownloadPageSettings());
        registry.register(NavigablePanelAdapter.forJdeployPanel(
            "Download Page",
            null,
            downloadPageSettingsPanel,
            json -> {
                DownloadPageSettings settings = loadDownloadPageSettings();
                downloadPageSettingsPanel.setSettings(settings);
            },
            json -> saveDownloadPageSettings(downloadPageSettingsPanel.getSettings()),
            listener -> downloadPageSettingsPanel.addChangeListener(e -> listener.actionPerformed(null))
        ));

        // Publish Settings Panel (conditional)
        if (context.shouldDisplayPublishSettingsTab()) {
            publishSettingsPanel = createPublishSettingsPanel();
            registry.register(NavigablePanelAdapter.forPackageJsonPanel(
                "Publish Settings",
                null,
                publishSettingsPanel,
                json -> publishSettingsPanel.load(json),
                json -> {},  // PublishSettingsPanel doesn't have a save method, data is managed via UI
                listener -> publishSettingsPanel.addChangeListener(listener),
                () -> context.shouldDisplayPublishSettingsTab()
            ));
        }

        return registry;
    }

    private PublishSettingsPanel createPublishSettingsPanel() {
        PublishTargetFactory factory = DIContext.get(PublishTargetFactory.class);
        PublishTargetServiceInterface publishTargetService = DIContext.get(PublishTargetServiceInterface.class);
        
        publishSettingsPanel = new PublishSettingsPanel(
            factory,
            publishTargetService,
            this::showError
        );
        publishSettingsPanel.load(packageJSON);
        publishSettingsPanel.addChangeListener(evt -> setModified());
        
        return publishSettingsPanel;
    }

    private void initMenu() {
        MenuBarBuilder menuBarBuilder = new MenuBarBuilder(
                frame,
                packageJSONFile,
                context,
                new MenuBarBuilder.MenuBarCallbacks() {
                    @Override
                    public void onSave() {
                        handleSave();
                    }

                    @Override
                    public void onOpenInTextEditor() {
                        handleOpenInTextEditor();
                    }

                    @Override
                    public void onGenerateGithubWorkflow() {
                        generateGithubWorkflow();
                    }

                    @Override
                    public void onEditGithubWorkflow() {
                        editGithubWorkflow();
                    }

                    @Override
                    public void onVerifyHomepage() {
                        handleVerifyHomepage();
                    }

                    @Override
                    public void onSetupClaude() {
                        handleSetupClaude();
                    }

                    @Override
                    public void onClose() {
                        handleClosing();
                    }
                }
        );

        JMenuBar jmb = menuBarBuilder.build();
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


    private void handleOpenInTextEditor() {
        if (context.getDesktopInterop().isDesktopSupported()) {
            try {
                context.edit(packageJSONFile);
            } catch (Exception ex) {
                showError("Failed to open package.json in a text editor. " + ex.getMessage(), ex);
            }
        } else {
            showError("That feature isn't supported on this platform.", null);
        }
    }

    private void showError(String message, Exception exception) {
        JOptionPane.showMessageDialog(
                frame,
                new JLabel("<html><p style='width:400px'>" + message + "</p></html>"),
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
        if (exception != null) {
            exception.printStackTrace(System.err);
        }
    }


    private void handleSave() {
        try {
            // Validate filetypes panel if present
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
            }

            // Save metadata panel first to ensure jdeploy object is created
            projectMetadataPanel.save(packageJSON);

            // Save all panels through registry
            JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
            if (registry != null) {
                registry.saveAll(packageJSON, jdeploy);
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
                return MenuBarBuilder.JDEPLOY_WEBSITE_URL + "~" + packageJSON.getString("name");
            }

            PublishTargetInterface githubTarget = targets.stream().filter(t -> t.getType() == PublishTargetType.GITHUB).findFirst().orElse(null);
            if (githubTarget != null) {
                return githubTarget.getUrl() + "/releases/tag/" + packageJSON.getString("version");
            }

        } catch (IOException e) {
            return MenuBarBuilder.JDEPLOY_WEBSITE_URL + "~" + packageJSON.getString("name");
        }

        return MenuBarBuilder.JDEPLOY_WEBSITE_URL + "~" + packageJSON.getString("name");
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
