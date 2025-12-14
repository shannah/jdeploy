package ca.weblite.jdeploy.installer;

import javax.swing.*;

/**
 * Visual test for error dialog messages.
 * Run this class to see how the error dialogs look with the new user-friendly HTML messages.
 */
public class ErrorDialogTest {
    public static void main(String[] args) {
        // Test Mac error message
        showMacError();

        // Test Windows error message
        showWindowsError();

        // Test Linux error message
        showLinuxError();
    }

    private static void showMacError() {
        String macError = "<html><body style='width: 400px;'>" +
            "<h3>Installation Failed</h3>" +
            "<p>Could not install the application to:<br/><b>/Users/username/Applications</b></p>" +
            "<p><b>Possible causes:</b></p>" +
            "<ul>" +
            "<li>You don't have write permission to the Applications directory</li>" +
            "<li>The application is currently running (please close it and try again)</li>" +
            "</ul>" +
            "<p style='margin-top: 12px;'><small>For technical details, check the log file:<br/>" +
            "/Users/username/.jdeploy/log/jdeploy-installer.log</small></p>" +
            "</body></html>";

        JOptionPane.showMessageDialog(null, macError, "Installation failed.", JOptionPane.ERROR_MESSAGE);
    }

    private static void showWindowsError() {
        String windowsError = "<html><body style='width: 400px;'>" +
            "<h3>Installation Failed</h3>" +
            "<p>Could not install the application to:<br/><b>C:\\Users\\username\\.jdeploy\\apps\\myapp</b></p>" +
            "<p><b>Possible causes:</b></p>" +
            "<ul>" +
            "<li>You don't have write permission to the directory</li>" +
            "<li>The application is currently running (please close it completely and try again)</li>" +
            "<li>Antivirus software may be blocking the installation</li>" +
            "</ul>" +
            "<p style='margin-top: 12px;'><small>For technical details, check the log file:<br/>" +
            "C:\\Users\\username\\.jdeploy\\log\\jdeploy-installer.log</small></p>" +
            "</body></html>";

        JOptionPane.showMessageDialog(null, windowsError, "Installation failed.", JOptionPane.ERROR_MESSAGE);
    }

    private static void showLinuxError() {
        String linuxError = "<html><body style='width: 400px;'>" +
            "<h3>Installation Failed</h3>" +
            "<p>Could not install the application to:<br/><b>/home/username/.jdeploy/apps/myapp</b></p>" +
            "<p><b>Possible causes:</b></p>" +
            "<ul>" +
            "<li>You don't have write permission to the directory</li>" +
            "<li>The application is currently running (please close it and try again)</li>" +
            "</ul>" +
            "<p style='margin-top: 12px;'><small>For technical details, check the log file:<br/>" +
            "/home/username/.jdeploy/log/jdeploy-installer.log</small></p>" +
            "</body></html>";

        JOptionPane.showMessageDialog(null, linuxError, "Installation failed.", JOptionPane.ERROR_MESSAGE);
    }
}