package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;

import javax.swing.*;
import java.awt.*;

/**
 * Panel that displays detailed information about a service,
 * including command-line usage and lifecycle information.
 *
 * @author Steve Hannah
 */
public class ServiceDetailsPanel extends JPanel {

    private final JLabel titleLabel;
    private final JTextArea detailsTextArea;

    public ServiceDetailsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title label
        titleLabel = new JLabel("Service Details");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(titleLabel, BorderLayout.NORTH);

        // Details text area (read-only, with scrolling)
        detailsTextArea = new JTextArea();
        detailsTextArea.setEditable(false);
        detailsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsTextArea.setLineWrap(true);
        detailsTextArea.setWrapStyleWord(true);
        detailsTextArea.setBackground(getBackground());

        JScrollPane scrollPane = new JScrollPane(detailsTextArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // Show placeholder text initially
        showPlaceholder();
    }

    /**
     * Shows placeholder text when no service is selected.
     */
    public void showPlaceholder() {
        titleLabel.setText("Service Details");
        detailsTextArea.setText("Select a service and click 'Info' to view details.");
    }

    /**
     * Displays details for the given service.
     *
     * @param model The service model to display
     */
    public void showServiceDetails(ServiceRowModel model) {
        String commandName = model.getCommandName();
        String serviceName = model.getServiceName();

        titleLabel.setText("Service: " + serviceName);

        StringBuilder sb = new StringBuilder();

        // Command-line usage section
        sb.append("COMMAND-LINE USAGE\n");
        sb.append("==================\n\n");
        sb.append("Start the service:\n");
        sb.append("  ").append(commandName).append(" service start\n\n");
        sb.append("Stop the service:\n");
        sb.append("  ").append(commandName).append(" service stop\n\n");
        sb.append("Check service status:\n");
        sb.append("  ").append(commandName).append(" service status\n\n");
        sb.append("Install the service (register with system):\n");
        sb.append("  ").append(commandName).append(" service install\n\n");
        sb.append("Uninstall the service (unregister from system):\n");
        sb.append("  ").append(commandName).append(" service uninstall\n\n");

        // Lifecycle section - platform specific
        sb.append("\nSERVICE LIFECYCLE\n");
        sb.append("=================\n\n");

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            sb.append("Platform: macOS (launchctl)\n\n");
            sb.append("This service is managed by macOS launchctl.\n\n");
            sb.append("Auto-start: The service starts automatically when you log in.\n");
            sb.append("Auto-stop: The service stops when you log out.\n");
            sb.append("Crash recovery: launchctl will automatically restart the service\n");
            sb.append("               if it crashes (KeepAlive is enabled).\n\n");
            sb.append("You can also manage the service using launchctl directly:\n");
            sb.append("  launchctl list | grep <service-label>\n");
        } else if (os.contains("linux")) {
            sb.append("Platform: Linux (systemd)\n\n");
            sb.append("This service is managed by systemd as a user service.\n\n");
            sb.append("Auto-start: The service starts automatically when you log in.\n");
            sb.append("Auto-stop: The service stops when you log out.\n");
            sb.append("Crash recovery: systemd will automatically restart the service\n");
            sb.append("               if it crashes (Restart=on-failure is enabled).\n\n");
            sb.append("You can also manage the service using systemctl directly:\n");
            sb.append("  systemctl --user status <service-name>\n");
            sb.append("  systemctl --user start <service-name>\n");
            sb.append("  systemctl --user stop <service-name>\n");
        } else if (os.contains("windows")) {
            sb.append("Platform: Windows (Background Process)\n\n");
            sb.append("This service runs as a background process (not a Windows Service).\n\n");
            sb.append("Auto-start: The service starts automatically when you log in.\n");
            sb.append("            (Added to Windows startup via registry or startup folder)\n");
            sb.append("Auto-stop: The service stops when you log out or shut down.\n");
            sb.append("Crash recovery: The service does NOT automatically restart if it\n");
            sb.append("               crashes. You must manually restart it.\n\n");
            sb.append("Note: Unlike macOS and Linux, Windows services managed by jDeploy\n");
            sb.append("are not registered with the Windows Service Control Manager.\n");
        } else {
            sb.append("Platform: Unknown\n\n");
            sb.append("Service lifecycle details are not available for this platform.\n");
        }

        detailsTextArea.setText(sb.toString());
        detailsTextArea.setCaretPosition(0); // Scroll to top
    }
}
