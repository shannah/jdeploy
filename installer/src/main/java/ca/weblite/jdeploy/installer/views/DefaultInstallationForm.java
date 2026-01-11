package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.Main;
import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.events.InstallationFormEventListener;
import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorService;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorServiceFactory;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.tools.platform.Platform;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;

public class DefaultInstallationForm extends JFrame implements InstallationForm {
    private InstallationSettings installationSettings;
    private InstallationFormEventDispatcher dispatcher;
    private JButton installButton;
    private JButton updateButton;
    private JButton uninstallButton;
    private JButton configureServicesButton;
    private JProgressBar progressBar;
    private boolean appAlreadyInstalled = false;
    private boolean hasServices = false;


    private void fireEvent(InstallationFormEvent event) {
        dispatcher.fireEvent(event);
    }



    public void showInstallationCompleteDialog() {
        String[] options = new String[]{
                "Open "+appInfo().getTitle(),
                "Reveal app in "+(Platform.getSystemPlatform().isMac()?"Finder":"Explorer"),
                "Close"
        };

        // Build message with command-line info for Linux
        Object message = "Installation was completed successfully";
        if (Platform.getSystemPlatform().isLinux() && installationSettings.isCommandLineSymlinkCreated()) {
            String commandPath = installationSettings.getCommandLinePath();
            if (commandPath != null) {
                String commandName = new java.io.File(commandPath).getName();

                // Create a custom panel with better layout
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                JLabel successLabel = new JLabel("Installation was completed successfully");
                successLabel.setFont(successLabel.getFont().deriveFont(Font.BOLD, 14f));
                successLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                panel.add(successLabel);

                panel.add(Box.createVerticalStrut(15));

                JLabel cliHeaderLabel = new JLabel("Command-Line Installation:");
                cliHeaderLabel.setFont(cliHeaderLabel.getFont().deriveFont(Font.BOLD));
                cliHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                panel.add(cliHeaderLabel);

                panel.add(Box.createVerticalStrut(5));

                JLabel pathLabel = new JLabel(commandPath);
                pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
                pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                panel.add(pathLabel);

                panel.add(Box.createVerticalStrut(10));

                if (installationSettings.isAddedToPath()) {
                    JLabel launchLabel = new JLabel("Launch from command line:");
                    launchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    panel.add(launchLabel);

                    panel.add(Box.createVerticalStrut(5));

                    JLabel commandLabel = new JLabel(commandName);
                    commandLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
                    commandLabel.setForeground(new Color(0, 100, 0));
                    commandLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    panel.add(commandLabel);

                    panel.add(Box.createVerticalStrut(10));

                    JLabel noteLabel = new JLabel("<html><i>Note: You may need to restart your terminal for PATH changes to take effect</i></html>");
                    noteLabel.setFont(noteLabel.getFont().deriveFont(Font.PLAIN, 11f));
                    noteLabel.setForeground(Color.GRAY);
                    noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    panel.add(noteLabel);
                } else {
                    JLabel noteLabel = new JLabel("<html><i>Note: ~/.local/bin is not in your PATH.<br>Add it to your shell configuration to run the command from anywhere.</i></html>");
                    noteLabel.setFont(noteLabel.getFont().deriveFont(Font.PLAIN, 11f));
                    noteLabel.setForeground(Color.GRAY);
                    noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    panel.add(noteLabel);
                }

                message = panel;
            }
        }

        int choice = JOptionPane.showOptionDialog(this,
                message,
                "Installation Complete",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        switch (choice) {
            case 0:
                fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.InstallCompleteOpenApp));
                break;
            case 1:
                fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.InstallCompleteRevealApp));
                break;
            default:
                fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.InstallCompleteCloseInstaller));
                break;
        }
    }

    public DefaultInstallationForm(InstallationSettings installationSettings) {
        this.installationSettings = installationSettings;
        final AppInfo appInfo = installationSettings.getAppInfo();
        setTitle("Install "+appInfo.getTitle()+" "+npmPackageVersion().getVersion());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set window icon
        File iconFile = installationSettings.getApplicationIcon();
        if (iconFile != null && iconFile.exists()) {
            try {
                ImageIcon icon = new ImageIcon(iconFile.toURI().toURL());
                setIconImage(icon.getImage());
            } catch (Exception ex) {
                // Log but don't fail - fall back to default icon
                System.err.println("Warning: Could not load application icon for installer window: " + ex.getMessage());
            }
        }

        installButton = new JButton("Install");
        installButton.addActionListener(evt->{
            fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.InstallClicked));
        });

        updateButton = new JButton("Update");
        updateButton.addActionListener(evt->{
            fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.UpdateClicked));
        });
        updateButton.setVisible(false);

        uninstallButton = new JButton("Uninstall");
        uninstallButton.addActionListener(evt->{
            fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.UninstallClicked));
        });
        uninstallButton.setVisible(false);

        // Configure services button (gear icon)
        configureServicesButton = new JButton("\u2699"); // Unicode gear symbol
        configureServicesButton.setToolTipText("Configure Services");
        configureServicesButton.setFont(configureServicesButton.getFont().deriveFont(16f));
        configureServicesButton.setMargin(new Insets(2, 6, 2, 6));
        configureServicesButton.addActionListener(evt -> showServiceConfigurationDialog());
        configureServicesButton.setVisible(false); // Hidden by default, shown if services exist

        // Check if app has services
        checkForServices();

        getContentPane().setLayout(new BorderLayout());

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(installButton);
        buttonsPanel.add(updateButton);
        buttonsPanel.add(uninstallButton);
        buttonsPanel.add(configureServicesButton);

        File splash = installationSettings.getInstallSplashImage();
        if (splash.exists()) {
            try {
                ImageIcon splashImage = new ImageIcon(splash.toURI().toURL());
                JLabel splashLabel = new JLabel(splashImage);
                splashLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                getContentPane().add(splashLabel, BorderLayout.CENTER);
            } catch (Exception ex) {

            }
        }
        String desktopLabel = "Add desktop shortcut";
        if (Platform.getSystemPlatform().isMac()) {
            desktopLabel = "Add desktop alias";
        }
        JCheckBox desktopCheckbox = new JCheckBox(desktopLabel);
        desktopCheckbox.setSelected(installationSettings.isAddToDesktop());
        desktopCheckbox.addActionListener(evt->{
            installationSettings.setAddToDesktop(desktopCheckbox.isSelected());
        });

        // On Linux, disable desktop checkbox if no desktop environment is detected
        if (Platform.getSystemPlatform().isLinux() && !installationSettings.hasDesktopEnvironment()) {
            desktopCheckbox.setSelected(false);
            desktopCheckbox.setEnabled(false);
            desktopCheckbox.setToolTipText("No desktop environment detected");
            installationSettings.setAddToDesktop(false);
        }

        JCheckBox addToDockCheckBox = new JCheckBox("Add to dock");

        if (installationSettings.isAlreadyAddedToDock()) {
            addToDockCheckBox.setSelected(false);
            addToDockCheckBox.setEnabled(false);
            addToDockCheckBox.setToolTipText("This app is already in the dock");
            installationSettings.setAddToDock(false);
        } else {
            addToDockCheckBox.setSelected(installationSettings.isAddToDock());
            addToDockCheckBox.addActionListener(evt->{
                installationSettings.setAddToDock(addToDockCheckBox.isSelected());
            });
        }

        JCheckBox addToStartMenuCheckBox = new JCheckBox("Add to Start menu");
        addToStartMenuCheckBox.setSelected(installationSettings.isAddToStartMenu());
        addToStartMenuCheckBox.addActionListener(evt->{
            installationSettings.setAddToStartMenu(addToStartMenuCheckBox.isSelected());
        });

        JPanel checkboxesPanel = new JPanel();
        if (Platform.getSystemPlatform().isWindows()) {
            checkboxesPanel.add(addToStartMenuCheckBox);
        } else if (Platform.getSystemPlatform().isMac()) {
            checkboxesPanel.add(addToDockCheckBox);
        }
        checkboxesPanel.add(desktopCheckbox);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));

        JComboBox<AutoUpdateSettings> autoUpdateSettingsJComboBox = new JComboBox<>(AutoUpdateSettings.values());
        autoUpdateSettingsJComboBox.setSelectedIndex(0);
        autoUpdateSettingsJComboBox.addItemListener(evt->{
            if (evt.getStateChange() == ItemEvent.SELECTED) {
                installationSettings.setAutoUpdate((AutoUpdateSettings) evt.getItem());
            }
        });

        JCheckBox prereleaseCheckBox = new JCheckBox("Prereleases");
        prereleaseCheckBox.setToolTipText("Check this box to automatically update to pre-releases.  Warning: This not recommended unless you are a developer or beta tester of the application as prereleases may be unstable.");
        prereleaseCheckBox.setSelected(installationSettings.isPrerelease());
        prereleaseCheckBox.addActionListener(evt->{
            installationSettings.setPrerelease(prereleaseCheckBox.isSelected());
        });


        southPanel.add(checkboxesPanel);

        JPanel updatesPanel = new JPanel();
        updatesPanel.add(new JLabel("Auto update settings:"));
        updatesPanel.add(autoUpdateSettingsJComboBox);
        updatesPanel.add(prereleaseCheckBox);
        southPanel.add(updatesPanel);
        southPanel.add(buttonsPanel);

        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(100, 8));
        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        progressPanel.add(progressBar);
        southPanel.add(progressPanel);
        progressPanel.setPreferredSize(new Dimension(640, 16));

        getContentPane().add(southPanel, BorderLayout.SOUTH);

    }

    private AppInfo appInfo() {
        return installationSettings.getAppInfo();
    }

    private NPMPackageVersion npmPackageVersion() {
        return installationSettings.getNpmPackageVersion();
    }



    public void showInstallationForm() {
        setMinimumSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    @Override
    public void showTrustConfirmationDialog() {

        JLabel message = new JLabel("<html><b>Warning: </b> You should only install software from trusted sources.<br><br>This software's verified homepage is <font color='blue'>" + installationSettings.getWebsiteURL()+".</font>" +
                "<br><br><b>Do you wish to proceed with the installation?</b></html>");

        message.setPreferredSize(new Dimension(300, 100));

        message.setVerticalAlignment(JLabel.TOP);
        //ImageIcon icon = new ImageIcon(getClass().getResource("/ca/weblite/jdeploy/installer/icon.png"));
        //icon.setImage(icon.getImage().getScaledInstance(75, 75, Image.SCALE_SMOOTH));

        int result = JOptionPane.showOptionDialog(this, message, "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new Object[]{


                "Proceed",
                "Cancel",
                "Visit Software Homepage"
        }, 2);

        switch (result) {
            case 2:
                this.getEventDispatcher().fireEvent(InstallationFormEvent.Type.VisitSoftwareHomepage);
                break;
            case 1:
                this.getEventDispatcher().fireEvent(InstallationFormEvent.Type.CancelInstallation);
                break;
            case 0:
                this.getEventDispatcher().fireEvent(InstallationFormEvent.Type.ProceedWithInstallation);
                break;
        }
    }

    @Override
    public void setEventDispatcher(InstallationFormEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public InstallationFormEventDispatcher getEventDispatcher() {
        return dispatcher;
    }

    @Override
    public void setInProgress(boolean inProgress, String message) {
        if (inProgress) {
            disableAllButtons();
            if (message != null) {
                progressBar.setToolTipText(message);
            }
            progressBar.setVisible(true);
        } else {
            enableAppropriateButtons();
            progressBar.setVisible(false);
        }
    }

    @Override
    public void setAppAlreadyInstalled(boolean installed) {
        this.appAlreadyInstalled = installed;
        updateButtonVisibility();
    }

    @Override
    public void showUninstallCompleteDialog() {
        JOptionPane.showMessageDialog(this,
                "The application has been successfully removed from your system.",
                "Uninstall Complete",
                JOptionPane.INFORMATION_MESSAGE);

        fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.UninstallCompleteQuit));
    }

    private void updateButtonVisibility() {
        installButton.setVisible(!appAlreadyInstalled);
        updateButton.setVisible(appAlreadyInstalled);
        uninstallButton.setVisible(appAlreadyInstalled);
    }

    private void disableAllButtons() {
        installButton.setEnabled(false);
        updateButton.setEnabled(false);
        uninstallButton.setEnabled(false);
        configureServicesButton.setEnabled(false);
    }

    private void enableAppropriateButtons() {
        if (appAlreadyInstalled) {
            updateButton.setEnabled(true);
            uninstallButton.setEnabled(true);
        } else {
            installButton.setEnabled(true);
        }
        if (hasServices) {
            configureServicesButton.setEnabled(true);
        }
    }

    private void checkForServices() {
        AppInfo appInfo = installationSettings.getAppInfo();
        if (appInfo == null) {
            return;
        }

        String packageName = appInfo.getNpmPackage();
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        String source = appInfo.getNpmSource();
        // Treat empty string as null for source
        if (source != null && source.isEmpty()) {
            source = null;
        }

        try {
            ServiceDescriptorService service = ServiceDescriptorServiceFactory.createDefault();
            hasServices = !service.listServices(packageName, source).isEmpty();
            configureServicesButton.setVisible(hasServices);
        } catch (Exception e) {
            // Log but don't fail - just hide the button
            System.err.println("Warning: Could not check for services: " + e.getMessage());
        }
    }

    private void showServiceConfigurationDialog() {
        // Ensure installationSettings has packageName and source from AppInfo
        AppInfo appInfo = installationSettings.getAppInfo();
        if (appInfo != null) {
            if (installationSettings.getPackageName() == null) {
                installationSettings.setPackageName(appInfo.getNpmPackage());
            }
            if (installationSettings.getSource() == null) {
                String source = appInfo.getNpmSource();
                installationSettings.setSource(source != null && !source.isEmpty() ? source : null);
            }
        }

        ServiceManagementPanel panel = new ServiceManagementPanel(installationSettings);

        JDialog dialog = new JDialog(this, "Configure Services", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(panel);
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(this);

        // Stop the panel's refresh timer when the dialog is closed
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                panel.stopRefresh();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                panel.stopRefresh();
            }
        });

        dialog.setVisible(true);
    }
}
