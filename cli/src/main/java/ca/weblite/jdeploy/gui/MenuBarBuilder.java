package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.ideInterop.IdeInteropInterface;
import ca.weblite.jdeploy.ideInterop.IdeInteropService;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;

public class MenuBarBuilder {
    public static final String JDEPLOY_WEBSITE_URL = JDeployConstants.JDEPLOY_WEBSITE_URL;

    private final JFrame frame;
    private final File packageJSONFile;
    private final JDeployProjectEditorContext context;
    private final MenuBarCallbacks callbacks;
    
    private JMenuItem generateGithubWorkflowMenuItem;
    private JMenuItem editGithubWorkflowMenuItem;

    public interface MenuBarCallbacks {
        void onSave();
        void onOpenInTextEditor();
        void onGenerateGithubWorkflow();
        void onEditGithubWorkflow();
        void onVerifyHomepage();
        void onSetupClaude();
        void onClose();
    }

    public MenuBarBuilder(
            JFrame frame,
            File packageJSONFile,
            JDeployProjectEditorContext context,
            MenuBarCallbacks callbacks
    ) {
        this.frame = frame;
        this.packageJSONFile = packageJSONFile;
        this.context = context;
        this.callbacks = callbacks;
    }

    public JMenuBar build() {
        JMenuBar jmb = new JMenuBar();
        jmb.add(createFileMenu());
        jmb.add(createHelpMenu());
        return jmb;
    }

    public JMenuItem getGenerateGithubWorkflowMenuItem() {
        return generateGithubWorkflowMenuItem;
    }

    public JMenuItem getEditGithubWorkflowMenuItem() {
        return editGithubWorkflowMenuItem;
    }

    public static JButton createHelpButton(String url, String label, String tooltipText, JDeployProjectEditorContext context, JFrame parentFrame) {
        JButton btn = new JButton(FontIcon.of(Material.HELP));
        btn.setText(label);
        btn.setToolTipText(tooltipText);
        btn.addActionListener(evt -> {
            if (context.getDesktopInterop().isDesktopSupported()) {
                try {
                    context.browse(new URI(url));
                } catch (Exception ex) {
                    showError("Failed to open web browser to " + url, ex, parentFrame);
                }
            } else {
                showError("Attempt to open web browser failed.  Not supported on this platform", null, parentFrame);
            }
        });
        btn.setMaximumSize(new Dimension(btn.getPreferredSize()));
        return btn;
    }

    public static JMenuItem createLinkItem(String url, String label, String tooltipText, JDeployProjectEditorContext context, JFrame parentFrame) {
        JMenuItem btn = new JMenuItem();
        btn.setText(label);
        btn.setToolTipText(tooltipText);
        btn.addActionListener(evt -> {
            if (context.getDesktopInterop().isDesktopSupported()) {
                try {
                    context.browse(new URI(url));
                } catch (Exception ex) {
                    showError("Failed to open web browser to " + url, ex, parentFrame);
                }
            } else {
                showError("Attempt to open web browser failed.  Not supported on this platform", null, parentFrame);
            }
        });
        return btn;
    }

    private JMenu createFileMenu() {
        JMenu file = new JMenu("File");

        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke('S', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        save.addActionListener(evt -> callbacks.onSave());
        file.add(save);

        JMenuItem openInTextEditor = new JMenuItem("Open with Text Editor");
        openInTextEditor.setToolTipText("Open the package.json file for editing in your system text editor");
        openInTextEditor.addActionListener(evt -> callbacks.onOpenInTextEditor());
        file.add(openInTextEditor);

        JMenuItem openProjectDirectory = new JMenuItem("Open Project Directory");
        openProjectDirectory.setToolTipText("Open the project directory in your system file manager");
        openProjectDirectory.addActionListener(evt -> {
            if (context.getDesktopInterop().isDesktopSupported()) {
                try {
                    context.getDesktopInterop().openDirectory(packageJSONFile.getAbsoluteFile().getParentFile());
                } catch (Exception ex) {
                    showError("Failed to open project directory in file manager", ex, frame);
                }
            } else {
                showError("That feature isn't supported on this platform.", null, frame);
            }
        });
        file.add(openProjectDirectory);

        JMenu openInIde = createOpenInIdeMenu();
        file.add(openInIde);

        generateGithubWorkflowMenuItem = new JMenuItem("Create Github Workflow");
        generateGithubWorkflowMenuItem.setToolTipText(
                "Generate a Github workflow to deploy your application automatically with Github Actions"
        );
        generateGithubWorkflowMenuItem.addActionListener(evt -> callbacks.onGenerateGithubWorkflow());

        editGithubWorkflowMenuItem = new JMenuItem("Edit Github Workflow");
        editGithubWorkflowMenuItem.setToolTipText("Edit the Github workflow file in a text editor");
        editGithubWorkflowMenuItem.addActionListener(evt -> callbacks.onEditGithubWorkflow());
        
        file.addSeparator();
        file.add(generateGithubWorkflowMenuItem);
        file.add(editGithubWorkflowMenuItem);

        JMenuItem verifyHomepage = new JMenuItem("Verify Homepage");
        verifyHomepage.setToolTipText(
                "Verify your app's homepage so that users will know that you are the developer of your app"
        );
        verifyHomepage.addActionListener(evt -> callbacks.onVerifyHomepage());

        JMenuItem setupClaude = new JMenuItem("Setup Claude AI Assistant");
        setupClaude.setToolTipText(
                "Setup Claude AI assistant for this project by adding jDeploy-specific instructions to CLAUDE.md"
        );
        setupClaude.addActionListener(evt -> callbacks.onSetupClaude());

        file.addSeparator();
        file.add(verifyHomepage);
        file.add(setupClaude);

        if (context.shouldDisplayExitMenu()) {
            file.addSeparator();
            JMenuItem quit = new JMenuItem("Exit");
            quit.setAccelerator(
                    KeyStroke.getKeyStroke('Q', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
            );
            quit.addActionListener(evt -> callbacks.onClose());
            file.add(quit);
        }

        return file;
    }

    private JMenu createOpenInIdeMenu() {
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
                                    showError("Failed to open project directory in " + ideInterop.getName(), ex, frame);
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
        return openInIde;
    }

    private JMenu createHelpMenu() {
        JMenu help = new JMenu("Help");
        JMenuItem jdeployHelp = createLinkItem(
                JDEPLOY_WEBSITE_URL + "docs/help",
                "jDeploy Help", "Open jDeploy application help in your web browser",
                context, frame
        );
        help.add(jdeployHelp);

        help.addSeparator();
        help.add(createLinkItem(
                JDEPLOY_WEBSITE_URL,
                "jDeploy Website",
                "Open the jDeploy website in your web browser.",
                context, frame
        ));
        help.add(createLinkItem(
                JDEPLOY_WEBSITE_URL + "docs/manual",
                "jDeploy Developers Guide",
                "Open the jDeploy developers guide in your web browser.",
                context, frame
        ));
        help.addSeparator();
        help.add(createLinkItem(
                "https://groups.google.com/g/jdeploy-developers",
                "jDeploy Developers Mailing List",
                "A mailing list for developers who are developing apps with jDeploy",
                context, frame
        ));
        help.add(createLinkItem(
                "https://github.com/shannah/jdeploy/discussions",
                "Support Forum",
                "A place to ask questions and get help from the community",
                context, frame
        ));
        help.add(createLinkItem(
                "https://github.com/shannah/jdeploy/issues",
                "Issue Tracker",
                "Find and report bugs",
                context, frame
        ));

        return help;
    }

    private static void showError(String message, Throwable exception, JFrame parentFrame) {
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
}
