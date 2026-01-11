package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.models.ServiceStatus;
import ca.weblite.jdeploy.installer.services.ServiceDescriptor;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorService;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorServiceFactory;
import ca.weblite.jdeploy.installer.services.ServiceStatusPoller;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel for managing services in a jDeploy application.
 *
 * Displays the status of each service and allows users to start/stop them.
 * Auto-refreshes service status periodically.
 *
 * @author Steve Hannah
 */
public class ServiceManagementPanel extends JPanel {

    private static final Logger logger = Logger.getLogger(ServiceManagementPanel.class.getName());

    private static final int REFRESH_INTERVAL_MS = 5000;
    private static final Color RUNNING_COLOR = new Color(0, 128, 0);
    private static final Color STOPPED_COLOR = new Color(128, 128, 128);
    private static final Color UNINSTALLED_COLOR = new Color(200, 128, 0);
    private static final Color UNKNOWN_COLOR = new Color(128, 128, 128);
    private static final Color ERROR_COLOR = new Color(200, 0, 0);

    private final InstallationSettings installationSettings;
    private final ServiceDescriptorService descriptorService;
    private final List<ServiceRowModel> serviceModels;
    private final Map<String, ServiceRowComponents> rowComponents;
    private final JPanel servicesPanel;
    private final Timer refreshTimer;

    private ServiceStatusPoller poller;

    /**
     * Creates a new service management panel.
     *
     * @param installationSettings The installation settings
     */
    public ServiceManagementPanel(InstallationSettings installationSettings) {
        this.installationSettings = installationSettings;
        this.descriptorService = ServiceDescriptorServiceFactory.createDefault();
        this.serviceModels = new ArrayList<>();
        this.rowComponents = new HashMap<>();

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create the services panel
        servicesPanel = new JPanel();
        servicesPanel.setLayout(new BoxLayout(servicesPanel, BoxLayout.Y_AXIS));

        // Load services and build UI
        loadServices();

        if (serviceModels.isEmpty()) {
            showNoServicesMessage();
        } else {
            buildServiceRows();
            JScrollPane scrollPane = new JScrollPane(servicesPanel);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            add(scrollPane, BorderLayout.CENTER);

            // Initial status poll
            pollStatusInBackground();
        }

        // Set up auto-refresh timer
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> pollStatusInBackground());
        refreshTimer.setRepeats(true);
        if (!serviceModels.isEmpty()) {
            refreshTimer.start();
        }
    }

    /**
     * Stops the auto-refresh timer.
     * Should be called when the panel is no longer visible.
     */
    public void stopRefresh() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            refreshTimer.stop();
        }
    }

    /**
     * Starts the auto-refresh timer.
     * Should be called when the panel becomes visible.
     */
    public void startRefresh() {
        if (refreshTimer != null && !refreshTimer.isRunning() && !serviceModels.isEmpty()) {
            refreshTimer.start();
        }
    }

    private void loadServices() {
        String packageName = installationSettings.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            logger.warning("Package name is not set in InstallationSettings");
            return;
        }

        String source = installationSettings.getSource();

        try {
            List<ServiceDescriptor> descriptors = descriptorService.listServices(packageName, source);
            for (ServiceDescriptor descriptor : descriptors) {
                serviceModels.add(new ServiceRowModel(descriptor));
            }

            // Create poller if we have services
            if (!serviceModels.isEmpty()) {
                poller = new ServiceStatusPoller(null, packageName, source);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load services", e);
        }
    }

    private void showNoServicesMessage() {
        JLabel noServicesLabel = new JLabel("This application does not include any services");
        noServicesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        noServicesLabel.setFont(noServicesLabel.getFont().deriveFont(Font.ITALIC));
        noServicesLabel.setForeground(Color.GRAY);
        add(noServicesLabel, BorderLayout.CENTER);
    }

    private void buildServiceRows() {
        // Add header row
        JPanel headerPanel = createHeaderRow();
        servicesPanel.add(headerPanel);
        servicesPanel.add(Box.createVerticalStrut(5));
        servicesPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
        servicesPanel.add(Box.createVerticalStrut(5));

        // Add service rows
        for (ServiceRowModel model : serviceModels) {
            JPanel rowPanel = createServiceRow(model);
            servicesPanel.add(rowPanel);
            servicesPanel.add(Box.createVerticalStrut(5));
        }

        // Add glue to push rows to top
        servicesPanel.add(Box.createVerticalGlue());
    }

    private JPanel createHeaderRow() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Service name header
        gbc.gridx = 0;
        gbc.weightx = 0.3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel nameHeader = new JLabel("Service");
        nameHeader.setFont(nameHeader.getFont().deriveFont(Font.BOLD));
        panel.add(nameHeader, gbc);

        // Status header
        gbc.gridx = 1;
        gbc.weightx = 0.15;
        JLabel statusHeader = new JLabel("Status");
        statusHeader.setFont(statusHeader.getFont().deriveFont(Font.BOLD));
        panel.add(statusHeader, gbc);

        // Toggle header
        gbc.gridx = 2;
        gbc.weightx = 0.15;
        JLabel toggleHeader = new JLabel("Action");
        toggleHeader.setFont(toggleHeader.getFont().deriveFont(Font.BOLD));
        panel.add(toggleHeader, gbc);

        // Error header
        gbc.gridx = 3;
        gbc.weightx = 0.4;
        JLabel errorHeader = new JLabel("Message");
        errorHeader.setFont(errorHeader.getFont().deriveFont(Font.BOLD));
        panel.add(errorHeader, gbc);

        return panel;
    }

    private JPanel createServiceRow(ServiceRowModel model) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Service name
        gbc.gridx = 0;
        gbc.weightx = 0.3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel nameLabel = new JLabel(model.getServiceName());
        String description = model.getDescription();
        if (description != null && !description.isEmpty()) {
            nameLabel.setToolTipText(description);
        }
        panel.add(nameLabel, gbc);

        // Status label
        gbc.gridx = 1;
        gbc.weightx = 0.15;
        JLabel statusLabel = new JLabel(model.getStatus().getDisplayName());
        updateStatusLabelStyle(statusLabel, model.getStatus());
        panel.add(statusLabel, gbc);

        // Toggle button
        gbc.gridx = 2;
        gbc.weightx = 0.15;
        JButton toggleButton = new JButton(getToggleButtonText(model.getStatus()));
        toggleButton.setEnabled(false); // Disabled until status is known
        toggleButton.addActionListener(e -> handleToggle(model));
        panel.add(toggleButton, gbc);

        // Error label
        gbc.gridx = 3;
        gbc.weightx = 0.4;
        JLabel errorLabel = new JLabel("");
        errorLabel.setForeground(ERROR_COLOR);
        panel.add(errorLabel, gbc);

        // Store components for later updates
        ServiceRowComponents components = new ServiceRowComponents(nameLabel, statusLabel, toggleButton, errorLabel);
        rowComponents.put(model.getCommandName(), components);

        return panel;
    }

    private void updateStatusLabelStyle(JLabel label, ServiceStatus status) {
        label.setText(status.getDisplayName());
        switch (status) {
            case RUNNING:
                label.setForeground(RUNNING_COLOR);
                break;
            case STOPPED:
                label.setForeground(STOPPED_COLOR);
                break;
            case UNINSTALLED:
                label.setForeground(UNINSTALLED_COLOR);
                break;
            default:
                label.setForeground(UNKNOWN_COLOR);
                break;
        }
    }

    private String getToggleButtonText(ServiceStatus status) {
        switch (status) {
            case RUNNING:
                return "Stop";
            case STOPPED:
            case UNINSTALLED:
                return "Start";
            default:
                return "...";
        }
    }

    private void handleToggle(ServiceRowModel model) {
        if (model.isOperationInProgress()) {
            return;
        }

        // Update UI to show operation in progress
        model.setOperationInProgress(true);
        updateRowUI(model);

        // Run operation in background
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                if (poller == null) {
                    return false;
                }
                boolean success = poller.toggleService(model);
                // Re-poll status after operation
                poller.pollStatus(model);
                return success;
            }

            @Override
            protected void done() {
                model.setOperationInProgress(false);
                updateRowUI(model);
            }
        };
        worker.execute();
    }

    private void pollStatusInBackground() {
        if (poller == null || serviceModels.isEmpty()) {
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                poller.pollAll(serviceModels);
                return null;
            }

            @Override
            protected void done() {
                updateAllRowsUI();
            }
        };
        worker.execute();
    }

    private void updateAllRowsUI() {
        for (ServiceRowModel model : serviceModels) {
            updateRowUI(model);
        }
    }

    private void updateRowUI(ServiceRowModel model) {
        ServiceRowComponents components = rowComponents.get(model.getCommandName());
        if (components == null) {
            return;
        }

        // Update status label
        updateStatusLabelStyle(components.statusLabel, model.getStatus());

        // Update toggle button
        if (model.isOperationInProgress()) {
            components.toggleButton.setEnabled(false);
            components.toggleButton.setText("...");
        } else {
            components.toggleButton.setText(getToggleButtonText(model.getStatus()));
            components.toggleButton.setEnabled(
                    model.getStatus() != ServiceStatus.UNKNOWN);
        }

        // Update error label
        String error = model.getErrorMessage();
        components.errorLabel.setText(error != null ? error : "");
        components.errorLabel.setToolTipText(error);
    }

    /**
     * Helper class to hold references to row UI components.
     */
    private static class ServiceRowComponents {
        final JLabel nameLabel;
        final JLabel statusLabel;
        final JButton toggleButton;
        final JLabel errorLabel;

        ServiceRowComponents(JLabel nameLabel, JLabel statusLabel, JButton toggleButton, JLabel errorLabel) {
            this.nameLabel = nameLabel;
            this.statusLabel = statusLabel;
            this.toggleButton = toggleButton;
            this.errorLabel = errorLabel;
        }
    }

    /**
     * Main method for standalone testing.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Create mock installation settings for testing
            InstallationSettings settings = new InstallationSettings();
            settings.setPackageName("test-package");
            settings.setSource(null);

            // Create and show the panel in a frame
            JFrame frame = new JFrame("Service Management");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);

            ServiceManagementPanel panel = new ServiceManagementPanel(settings);
            frame.add(panel);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Stop timer when frame is closed
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    panel.stopRefresh();
                }
            });
        });
    }
}
