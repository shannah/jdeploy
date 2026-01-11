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
        String packageName = model.getDescriptor().getPackageName();
        String source = model.getDescriptor().getSource();

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

        // Log location section
        sb.append("\nLOG FILES\n");
        sb.append("=========\n\n");
        sb.append("Location: ").append(getLogPath(packageName, commandName, source)).append("\n");
        sb.append("Rotation: 10MB max, keeps 5 old files (.log.1 to .log.5), rotates on start\n");

        // Lifecycle section - platform specific
        sb.append("\n\nSERVICE LIFECYCLE\n");
        sb.append("=================\n\n");

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            sb.append("Platform: macOS (launchctl)\n\n");
            sb.append("Auto-start: Starts when you log in\n");
            sb.append("Auto-stop: Stops when you log out\n");
            sb.append("Crash recovery: Auto-restarts on crash (KeepAlive enabled)\n");
        } else if (os.contains("linux")) {
            sb.append("Platform: Linux (systemd user service)\n\n");
            sb.append("Auto-start: Starts when you log in\n");
            sb.append("Auto-stop: Stops when you log out\n");
            sb.append("Crash recovery: Auto-restarts on crash (Restart=on-failure)\n");
        } else if (os.contains("windows")) {
            sb.append("Platform: Windows (background process)\n\n");
            sb.append("Auto-start: Starts when you log in\n");
            sb.append("Auto-stop: Stops when you log out or shut down\n");
            sb.append("Crash recovery: Does NOT auto-restart on crash\n");
        } else {
            sb.append("Platform: Unknown\n");
        }

        detailsTextArea.setText(sb.toString());
        detailsTextArea.setCaretPosition(0); // Scroll to top
    }

    /**
     * Computes the log file path based on platform and source.
     */
    private String getLogPath(String packageName, String commandName, String source) {
        String logFileName = packageName + "." + commandName + ".service.log";
        String os = System.getProperty("os.name", "").toLowerCase();

        if (source != null && !source.isEmpty() && source.contains("github.com")) {
            // GitHub-sourced package
            // Extract owner/repo from source URL
            String githubPath = extractGitHubPath(source);
            if (os.contains("windows")) {
                return "%USERPROFILE%\\.jdeploy\\log\\github.com\\" + githubPath + "\\" + logFileName;
            } else {
                return "~/.jdeploy/log/github.com/" + githubPath + "/" + logFileName;
            }
        } else {
            // NPM package
            if (os.contains("windows")) {
                return "%USERPROFILE%\\.jdeploy\\log\\" + logFileName;
            } else {
                return "~/.jdeploy/log/" + logFileName;
            }
        }
    }

    /**
     * Extracts owner/repo from a GitHub URL.
     */
    private String extractGitHubPath(String source) {
        // Handle URLs like https://github.com/owner/repo or github.com/owner/repo
        String path = source;
        if (path.contains("github.com/")) {
            path = path.substring(path.indexOf("github.com/") + "github.com/".length());
        }
        // Remove trailing slashes or .git
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        // Should now be "owner/repo"
        return path;
    }
}
