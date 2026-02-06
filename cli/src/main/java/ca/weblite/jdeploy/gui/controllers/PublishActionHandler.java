package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.gui.JDeployProjectEditorContext;
import ca.weblite.jdeploy.gui.ProgressDialog;
import ca.weblite.jdeploy.gui.services.PublishingCoordinator;
import ca.weblite.jdeploy.gui.services.SwingOneTimePasswordProvider;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.npm.TerminalLoginLauncher;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.packaging.PackagingPreferences;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.function.Supplier;

/**
 * Encapsulates the publishing workflow for a jDeploy project.
 * Handles validation, token prompting, progress tracking, and error reporting.
 * Follows the controller pattern with constructor injection and UI handling.
 */
public class PublishActionHandler {

    private static final int NOT_LOGGED_IN = 1;

    /**
     * Exception thrown during validation with optional type and log file reference.
     */
    public static class ValidationException extends Exception {
        private final int type;
        private final File logFile;

        public ValidationException(String msg, int type, File logFile) {
            super(msg);
            this.type = type;
            this.logFile = logFile;
        }

        public int getType() {
            return type;
        }

        public File getLogFile() {
            return logFile;
        }
    }

    private final JFrame frame;
    private final File packageJSONFile;
    private final JSONObject packageJSON;
    private final JDeployProjectEditorContext context;
    private final PublishingCoordinator publishingCoordinator;
    private final Runnable onSave;
    private final Supplier<String> downloadPageUrlSupplier;
    private boolean publishInProgress = false;

    /**
     * Constructs a PublishActionHandler with all required dependencies.
     *
     * @param frame the parent frame for dialogs
     * @param packageJSONFile the package.json file
     * @param packageJSON the parsed package.json object
     * @param context the editor context for UI interactions
     * @param publishingCoordinator the coordinator for publishing operations
     * @param onSave callback to save changes before publishing
     * @param downloadPageUrlSupplier supplier for the download page URL
     */
    public PublishActionHandler(
            JFrame frame,
            File packageJSONFile,
            JSONObject packageJSON,
            JDeployProjectEditorContext context,
            PublishingCoordinator publishingCoordinator,
            Runnable onSave,
            Supplier<String> downloadPageUrlSupplier
    ) {
        this.frame = frame;
        this.packageJSONFile = packageJSONFile;
        this.packageJSON = packageJSON;
        this.context = context;
        this.publishingCoordinator = publishingCoordinator;
        this.onSave = onSave;
        this.downloadPageUrlSupplier = downloadPageUrlSupplier;
    }

    /**
     * Public entry point for publishing workflow.
     * Guards against concurrent publish operations and spawns a new thread.
     */
    public void handlePublish() {
        if (publishInProgress) {
            return;
        }
        publishInProgress = true;
        new Thread(() -> {
            try {
                handlePublish0();
            } catch (ValidationException ex) {
                if (ex.getType() == NOT_LOGGED_IN) {
                    handleNotLoggedIn();
                } else {
                    showError(ex.getMessage(), ex);
                }
            } finally {
                publishInProgress = false;
            }
        }).start();
    }

    /**
     * Handles the NOT_LOGGED_IN validation error by launching login and showing a retry dialog.
     */
    private void handleNotLoggedIn() {
        try {
            TerminalLoginLauncher.launchLoginTerminal();
        } catch (IOException | URISyntaxException e) {
            showError("Failed to launch login terminal: " + e.getMessage(), e);
            return;
        }

        // Skip GUI operations if frame is not displayable (e.g., in tests)
        if (frame == null || !frame.isDisplayable()) {
            System.err.println("Not logged in to NPM. Please login and try again.");
            return;
        }

        // Create a non-modal dialog
        JOptionPane optionPane = new JOptionPane(
                "<html><p style='width:400px'>You must be logged into NPM in order to publish your app. " +
                        "We have opened a terminal window for you to login. " +
                        "Please login to NPM in the terminal window and then try to publish again.</p></html>",
                JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION
        );

        JDialog dialog = optionPane.createDialog(frame, "Login to NPM");
        dialog.setModal(false);
        dialog.setVisible(true);

        // Spawn a thread to poll for login status
        new Thread(() -> {
            NPM npm = new NPM(System.out, System.err, context.useManagedNode());
            npm.setNpmToken(context.getNpmToken());
            try {
                while (dialog.isShowing() && !npm.isLoggedIn()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex1) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                EventQueue.invokeLater(() -> {
                    dialog.setVisible(false);
                    dialog.dispose();
                });
            }

            // If still logged in after dialog closes, retry publishing
            if (npm.isLoggedIn() && !dialog.isShowing()) {
                handlePublish();
            }
        }).start();
    }

    /**
     * Private implementation of the publishing workflow.
     * Prompts for tokens, validates, creates packaging context, and executes publish.
     *
     * @throws ValidationException if validation fails
     */
    private void handlePublish0() throws ValidationException {
        if (!EventQueue.isDispatchThread()) {
            // Confirm publish (allows context to intercept, e.g. to suggest GitHub release)
            if (!context.confirmPublish(frame)) {
                return;
            }

            // Prompt for tokens if needed (not on dispatch thread to allow blocking)
            if (publishingCoordinator.isNpmPublishingEnabled() && !context.promptForNpmToken(frame)) {
                return;
            }
            if (publishingCoordinator.isGitHubPublishingEnabled() && !context.promptForGithubToken(frame)) {
                return;
            }
        }

        // Validate all preconditions for publishing
        PublishingCoordinator.ValidationResult validationResult = publishingCoordinator.validateForPublishing(context.getNpmToken());
        if (!validationResult.isValid()) {
            throw new ValidationException(
                    validationResult.getErrorMessage(),
                    validationResult.getErrorType(),
                    validationResult.getLogFile()
            );
        }

        File absDirectory = packageJSONFile.getAbsoluteFile().getParentFile();
        PackagingPreferences packagingPreferences = publishingCoordinator.getBuildPreferences();
        boolean buildRequired = packagingPreferences.isBuildProjectBeforePackaging();

        // Create progress dialog
        String packageName = packageJSON.getString("name");
        String downloadPageUrl = downloadPageUrlSupplier.get();
        ProgressDialog progressDialog = new ProgressDialog(packageName, downloadPageUrl);

        // Build packaging context with output streams to progress dialog
        PackagingContext packagingContext = PackagingContext.builder()
                .directory(absDirectory)
                .out(new PrintStream(progressDialog.createOutputStream()))
                .err(new PrintStream(progressDialog.createOutputStream()))
                .exitOnFail(false)
                .isBuildRequired(buildRequired)
                .build();

        // Create and configure JDeploy instance
        JDeploy jdeployObject = new JDeploy(absDirectory, false);
        jdeployObject.setOut(packagingContext.out);
        jdeployObject.setErr(packagingContext.err);
        jdeployObject.setNpmToken(context.getNpmToken());
        jdeployObject.setUseManagedNode(context.useManagedNode());

        // Show progress dialog on dispatch thread (skip if frame is not displayable, e.g., in tests)
        if (frame != null && frame.isDisplayable()) {
            EventQueue.invokeLater(() -> {
                progressDialog.show(frame, "Publishing in Progress...");
                progressDialog.setMessage1("Publishing " + packageName + " to " +
                        publishingCoordinator.getPublishTargetNames() + ". Please wait...");
                progressDialog.setMessage2("");
            });
        }

        try {
            // Save changes before publishing
            onSave.run();

            // Execute publishing with progress callback
            publishingCoordinator.publish(
                    packagingContext,
                    jdeployObject,
                    new SwingOneTimePasswordProvider(frame),
                    progress -> {
                        // Skip GUI updates if frame is not displayable (e.g., in tests)
                        if (frame != null && frame.isDisplayable()) {
                            EventQueue.invokeLater(() -> {
                                if (progress.isComplete()) {
                                    progressDialog.setComplete();
                                } else if (progress.isFailed()) {
                                    progressDialog.setFailed();
                                }
                            });
                        }
                    },
                    context.getGithubToken()
            );
        } catch (Exception ex) {
            packagingContext.err.println("An error occurred during publishing");
            ex.printStackTrace(packagingContext.err);
            // Skip GUI updates if frame is not displayable (e.g., in tests)
            if (frame != null && frame.isDisplayable()) {
                EventQueue.invokeLater(progressDialog::setFailed);
            }
            throw new RuntimeException("Publishing failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Displays an error dialog with optional log file path copying.
     *
     * @param message the error message to display
     * @param exception the exception (may be null)
     */
    private void showError(String message, Throwable exception) {
        // Skip GUI operations if frame is not displayable (e.g., in tests)
        if (frame == null || !frame.isDisplayable()) {
            if (exception != null) {
                exception.printStackTrace(System.err);
            } else {
                System.err.println("Error: " + message);
            }
            return;
        }

        File logFile = (exception instanceof ValidationException)
                ? ((ValidationException) exception).getLogFile()
                : null;

        JPanel dialogComponent = new JPanel();
        dialogComponent.setLayout(new BoxLayout(dialogComponent, BoxLayout.Y_AXIS));
        dialogComponent.setOpaque(false);
        dialogComponent.setBorder(new EmptyBorder(10, 10, 10, 10));
        dialogComponent.add(new JLabel(
                "<html><p style='width:400px'>" + message + "</p></html>"
        ));

        if (logFile != null) {
            String[] options = {"Copy Path", "OK"};
            int choice = JOptionPane.showOptionDialog(
                    frame,
                    dialogComponent,
                    "Error",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.ERROR_MESSAGE,
                    null,
                    options,
                    options[1]
            );

            if (choice == 0) { // Copy Path selected
                try {
                    StringSelection stringSelection = new StringSelection(logFile.getAbsolutePath());
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(stringSelection, null);
                } catch (Exception ex) {
                    showError("Failed to copy path to clipboard. " + ex.getMessage(), ex);
                }
            }
        } else {
            JOptionPane.showMessageDialog(
                    frame,
                    dialogComponent,
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        if (exception != null) {
            exception.printStackTrace(System.err);
        }
    }
}
