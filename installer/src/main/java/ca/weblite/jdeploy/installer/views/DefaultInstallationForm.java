package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.Main;
import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.events.InstallationFormEventListener;
import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.tools.platform.Platform;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.TimerTask;

public class DefaultInstallationForm extends JFrame implements InstallationForm {
    private InstallationSettings installationSettings;
    private InstallationFormEventDispatcher dispatcher;
    private JButton installButton;
    private JProgressBar progressBar;


    private void fireEvent(InstallationFormEvent event) {
        dispatcher.fireEvent(event);
    }



    public void showInstallationCompleteDialog() {
        String[] options = new String[]{
                "Open "+appInfo().getTitle(),
                "Reveal app in "+(Platform.getSystemPlatform().isMac()?"Finder":"Explorer"),
                "Close"
        };

        int choice = JOptionPane.showOptionDialog(this,
                "Installation was completed successfully",
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

        installButton = new JButton("Install");
        installButton.addActionListener(evt->{
            fireEvent(new InstallationFormEvent(InstallationFormEvent.Type.InstallClicked));
        });
        getContentPane().setLayout(new BorderLayout());

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(installButton);

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
            installButton.setEnabled(false);
            if (message != null) {
                progressBar.setToolTipText(message);
            }
            progressBar.setVisible(true);
        } else {
            installButton.setEnabled(true);
            progressBar.setVisible(false);
        }
    }
}
