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

    // Fixed column widths for consistent alignment
    private static final int COL_SERVICE_WIDTH = 150;
    private static final int COL_STATUS_WIDTH = 80;
    private static final int COL_ACTION_WIDTH = 80;

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
        servicesPanel.add(Box.createVerticalStrut(10));

        // Add service rows with error labels
        for (ServiceRowModel model : serviceModels) {
            ServiceRowComponents components = createServiceRow(model);
            servicesPanel.add(components.rowPanel);
            servicesPanel.add(components.errorLabel);
        }

        // Add glue to push rows to top
        servicesPanel.add(Box.createVerticalGlue());
    }

    private JPanel createHeaderRow() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Service name header
        JLabel nameHeader = new JLabel("Service");
        nameHeader.setFont(nameHeader.getFont().deriveFont(Font.BOLD));
        nameHeader.setPreferredSize(new Dimension(COL_SERVICE_WIDTH, 20));
        panel.add(nameHeader);

        // Status header
        JLabel statusHeader = new JLabel("Status");
        statusHeader.setFont(statusHeader.getFont().deriveFont(Font.BOLD));
        statusHeader.setPreferredSize(new Dimension(COL_STATUS_WIDTH, 20));
        panel.add(statusHeader);

        // Toggle header
        JLabel toggleHeader = new JLabel("Action");
        toggleHeader.setFont(toggleHeader.getFont().deriveFont(Font.BOLD));
        toggleHeader.setPreferredSize(new Dimension(COL_ACTION_WIDTH, 20));
        panel.add(toggleHeader);

        return panel;
    }

    private ServiceRowComponents createServiceRow(ServiceRowModel model) {
        // Main row with service info - same structure as header
        JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Service name
        JLabel nameLabel = new JLabel(model.getServiceName());
        nameLabel.setPreferredSize(new Dimension(COL_SERVICE_WIDTH, 20));
        String description = model.getDescription();
        if (description != null && !description.isEmpty()) {
            nameLabel.setToolTipText(description);
        }
        rowPanel.add(nameLabel);

        // Status label
        JLabel statusLabel = new JLabel(model.getStatus().getDisplayName());
        statusLabel.setPreferredSize(new Dimension(COL_STATUS_WIDTH, 20));
        updateStatusLabelStyle(statusLabel, model.getStatus());
        rowPanel.add(statusLabel);

        // Toggle button
        JButton toggleButton = new JButton(getToggleButtonText(model.getStatus()));
        toggleButton.setPreferredSize(new Dimension(COL_ACTION_WIDTH, 25));
        toggleButton.setEnabled(false); // Disabled until status is known
        toggleButton.addActionListener(e -> handleToggle(model));
        rowPanel.add(toggleButton);

        // Error label - added separately to servicesPanel, spans full width
        JLabel errorLabel = new JLabel("");
        errorLabel.setForeground(ERROR_COLOR);
        errorLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 10));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setVisible(false); // Hidden by default

        // Store and return components
        ServiceRowComponents components = new ServiceRowComponents(rowPanel, nameLabel, statusLabel, toggleButton, errorLabel);
        rowComponents.put(model.getCommandName(), components);

        return components;
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

        // Update error label - show/hide based on whether there's an error
        String error = model.getErrorMessage();
        if (error != null && !error.isEmpty()) {
            components.errorLabel.setText(error);
            components.errorLabel.setToolTipText(error);
            components.errorLabel.setVisible(true);
        } else {
            components.errorLabel.setText("");
            components.errorLabel.setToolTipText(null);
            components.errorLabel.setVisible(false);
        }
    }

    /**
     * Helper class to hold references to row UI components.
     */
    private static class ServiceRowComponents {
        final JPanel rowPanel;
        final JLabel nameLabel;
        final JLabel statusLabel;
        final JButton toggleButton;
        final JLabel errorLabel;

        ServiceRowComponents(JPanel rowPanel, JLabel nameLabel, JLabel statusLabel, JButton toggleButton, JLabel errorLabel) {
            this.rowPanel = rowPanel;
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
