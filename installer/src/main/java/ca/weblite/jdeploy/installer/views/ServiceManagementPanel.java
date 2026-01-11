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
    private static final int COL_INFO_WIDTH = 60;

    // Highlight color for selected row
    private static final Color SELECTED_ROW_COLOR = new Color(220, 235, 252);

    private final InstallationSettings installationSettings;
    private final ServiceDescriptorService descriptorService;
    private final List<ServiceRowModel> serviceModels;
    private final Map<String, ServiceRowComponents> rowComponents;
    private final JPanel servicesPanel;
    private final Timer refreshTimer;

    private ServiceDetailsPanel detailsPanel;
    private JSplitPane splitPane;
    private JScrollPane servicesScrollPane;
    private ServiceStatusPoller poller;
    private String selectedCommandName; // Track which service is selected for info display

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

        // Create the details panel
        detailsPanel = new ServiceDetailsPanel();

        // Create split pane (will be configured after loading services)
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        // Load services and build UI
        loadServices();

        if (serviceModels.isEmpty()) {
            showNoServicesMessage();
        } else {
            buildServiceRows();

            // Create scroll pane for services
            servicesScrollPane = new JScrollPane(servicesPanel);
            servicesScrollPane.setBorder(BorderFactory.createEmptyBorder());
            servicesScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            // Configure split pane
            splitPane.setTopComponent(servicesScrollPane);
            splitPane.setBottomComponent(detailsPanel);

            // If only one service, show details panel by default and hide Info button
            if (serviceModels.size() == 1) {
                add(splitPane, BorderLayout.CENTER);
                // Select the only service and show its details
                ServiceRowModel onlyService = serviceModels.get(0);
                selectService(onlyService);
                // Hide the Info button since details are always visible
                ServiceRowComponents components = rowComponents.get(onlyService.getCommandName());
                if (components != null) {
                    components.infoButton.setVisible(false);
                }
            } else {
                // Multiple services: hide details panel initially
                // Just show the scroll pane, details will appear when Info is clicked
                add(servicesScrollPane, BorderLayout.CENTER);
            }

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

        // Info header (empty - just for spacing)
        JLabel infoHeader = new JLabel("");
        infoHeader.setPreferredSize(new Dimension(COL_INFO_WIDTH, 20));
        panel.add(infoHeader);

        return panel;
    }

    private ServiceRowComponents createServiceRow(ServiceRowModel model) {
        // Main row with service info - same structure as header
        JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rowPanel.setOpaque(true); // Required for background color to show

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

        // Info button
        JButton infoButton = new JButton("Info");
        infoButton.setPreferredSize(new Dimension(COL_INFO_WIDTH, 25));
        infoButton.addActionListener(e -> selectService(model));
        rowPanel.add(infoButton);

        // Error label - added separately to servicesPanel, spans full width
        JLabel errorLabel = new JLabel("");
        errorLabel.setForeground(ERROR_COLOR);
        errorLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 10));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setVisible(false); // Hidden by default

        // Store and return components
        ServiceRowComponents components = new ServiceRowComponents(rowPanel, nameLabel, statusLabel, toggleButton, infoButton, errorLabel);
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
     * Selects a service to display its details.
     * Highlights the selected row and shows the details panel.
     *
     * @param model The service model to select
     */
    private void selectService(ServiceRowModel model) {
        String commandName = model.getCommandName();

        // Update highlighting for all rows
        for (Map.Entry<String, ServiceRowComponents> entry : rowComponents.entrySet()) {
            ServiceRowComponents comp = entry.getValue();
            if (entry.getKey().equals(commandName)) {
                // Highlight selected row
                comp.rowPanel.setBackground(SELECTED_ROW_COLOR);
            } else {
                // Reset other rows to default
                comp.rowPanel.setBackground(comp.defaultBackground);
            }
        }

        // Update selected command name
        selectedCommandName = commandName;

        // Show details for this service
        detailsPanel.showServiceDetails(model);

        // If details panel is not visible (multiple services case), show it
        if (serviceModels.size() > 1 && splitPane.getParent() == null) {
            // Remove the scroll pane from this panel
            remove(servicesScrollPane);

            // Add the split pane instead
            add(splitPane, BorderLayout.CENTER);

            // Set divider location to 50%
            SwingUtilities.invokeLater(() -> {
                splitPane.setDividerLocation(0.5);
            });

            // Revalidate and repaint
            revalidate();
            repaint();
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
        final JButton infoButton;
        final JLabel errorLabel;
        final Color defaultBackground;

        ServiceRowComponents(JPanel rowPanel, JLabel nameLabel, JLabel statusLabel, JButton toggleButton, JButton infoButton, JLabel errorLabel) {
            this.rowPanel = rowPanel;
            this.nameLabel = nameLabel;
            this.statusLabel = statusLabel;
            this.toggleButton = toggleButton;
            this.infoButton = infoButton;
            this.errorLabel = errorLabel;
            this.defaultBackground = rowPanel.getBackground();
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
