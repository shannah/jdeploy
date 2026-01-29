package ca.weblite.jdeploy.installer.views;

import javax.swing.*;
import java.awt.*;

/**
 * A modal dialog that shows indeterminate progress during application update.
 *
 * Features:
 * - Delays display by 1 second (don't show if update is fast)
 * - Once shown, displays for minimum 2 seconds to avoid flashing
 * - Auto-closes when update completes (respecting minimum display time)
 */
public class UpdateProgressDialog extends JDialog {
    private static final int SHOW_DELAY_MS = 1000;
    private static final int MIN_DISPLAY_MS = 2000;

    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private long shownAtTime = 0;
    private volatile boolean updateComplete = false;
    private volatile boolean updateFailed = false;
    private volatile String failureMessage = null;

    public UpdateProgressDialog(Window owner) {
        super(owner, "Downloading Application", ModalityType.MODELESS);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        statusLabel = new JLabel("Downloading application files...");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(statusLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(300, 20));
        content.add(progressBar, BorderLayout.CENTER);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Runs the update task with smart dialog display.
     *
     * @param updateTask The task that performs the update (runs --jdeploy:update)
     * @throws Exception if the update fails
     */
    public void runWithProgress(Runnable updateTask) throws Exception {
        // Start the update in a background thread
        Thread updateThread = new Thread(() -> {
            try {
                updateTask.run();
                updateComplete = true;
            } catch (Exception e) {
                updateFailed = true;
                failureMessage = e.getMessage();
            }
        });
        updateThread.start();

        // Wait up to SHOW_DELAY_MS for update to complete
        try {
            updateThread.join(SHOW_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // If not done yet, show the dialog
        if (updateThread.isAlive()) {
            shownAtTime = System.currentTimeMillis();
            setVisible(true);

            // Wait for update to complete
            try {
                updateThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Ensure minimum display time
            long elapsed = System.currentTimeMillis() - shownAtTime;
            if (elapsed < MIN_DISPLAY_MS) {
                try {
                    Thread.sleep(MIN_DISPLAY_MS - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            setVisible(false);
            dispose();
        }

        // Check for failure
        if (updateFailed) {
            String logPath = System.getProperty("user.home") + "/.jdeploy/log/jdeploy-installer.log";
            throw new RuntimeException("Failed to download application files. See log for details: " + logPath);
        }
    }
}
