package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.helpers.NPMApplicationHelper;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.services.WebsiteVerifier;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class VerifyWebsiteController implements Runnable {

    private WebsiteVerifier verifier = new WebsiteVerifier();

    private JFrame parentFrame;
    private JDialog dialog;
    private NPMApplication app;
    private JLabel instructionsLabel;
    private JLabel icon;
    private JTextField shaTextField;
    private JButton verifyButton;
    private JLabel copiedToClipboardMessageLabel;
    private JProgressBar progressBar;

    public VerifyWebsiteController(JFrame parentFrame, NPMApplication app) {
        this.parentFrame = parentFrame;
        this.app = app;
        createUI();
    }

    @Override
    public void run() {

        JPanel waitPanel = new JPanel();
        waitPanel.setLayout(new BorderLayout());
        waitPanel.add(new JLabel("Checking homepage verification status..."), BorderLayout.CENTER);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(150, 15));
        JPanel progressWrapper = new JPanel();
        progressWrapper.setLayout(new FlowLayout(FlowLayout.CENTER));
        progressWrapper.add(progress);
        waitPanel.add(progressWrapper, BorderLayout.SOUTH);

        JDialog waitDialog = new JDialog(parentFrame);
        waitDialog.setContentPane(waitPanel);
        waitDialog.setModal(false);
        waitDialog.setAlwaysOnTop(true);
        waitDialog.pack();
        waitDialog.setLocationRelativeTo(parentFrame);
        waitDialog.setVisible(true);
        SwingWorker worker = new SwingWorker() {

            @Override
            protected Object doInBackground() throws Exception {
                app.resetHomepageVerificationStatus();
                verifier.verifyHomepage(app);
                return null;
            }

            @Override
            protected void done() {
                waitDialog.dispose();
                updateUI_();
                dialog.setModal(true);
                dialog.pack();
                dialog.setLocationRelativeTo(parentFrame);
                dialog.setVisible(true);
            }
        };
        worker.execute();


    }

    private enum EventType {
        Verify,
        CopyToClipboard
    }

    private void createUI() {
        dialog = new JDialog(parentFrame, "Verify Homepage");

        JPanel cnt = new JPanel();

        cnt.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        ImageIcon imageIcon = new ImageIcon(getClass().getResource("/ca/weblite/jdeploy/gui/verify-homepage.png"));
        imageIcon.setImage(imageIcon.getImage().getScaledInstance(75, 75, Image.SCALE_SMOOTH));

        icon = new JLabel(imageIcon);
        icon.setBorder(new EmptyBorder(10, 10, 10, 20));

        instructionsLabel = new JLabel();
        instructionsLabel.setPreferredSize(new Dimension(400, 100));
        shaTextField = new JTextField();
        shaTextField.setEditable(false);

        shaTextField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                respondUI(EventType.CopyToClipboard);
            }
        });
        copiedToClipboardMessageLabel = new JLabel();
        verifyButton = new JButton("Verify Ownership");
        verifyButton.addActionListener(evt->respondUI(EventType.Verify));

        cnt.setLayout(new BorderLayout());
        //cnt.add(instructionsLabel, BorderLayout.NORTH);
        cnt.add(icon, BorderLayout.WEST);

        Container center = new JPanel();
        center.setLayout(new BorderLayout());
        center.add(instructionsLabel,BorderLayout.NORTH);
        JPanel textFieldWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        textFieldWrapper.add(shaTextField);
        center.add(textFieldWrapper, BorderLayout.CENTER);
        center.add(copiedToClipboardMessageLabel, BorderLayout.SOUTH);
        cnt.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.add(verifyButton);


        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(150, 16));
        progressBar.setVisible(false);
        south.add(progressBar);

        cnt.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(cnt);
    }

    private void updateUI_() {
        if (app.getHomepage() == null || !app.getHomepage().startsWith("https://")) {
            updateUINoHomepage();
        } else if (app.isHomepageVerified()) {
            updateUIVerifiedHomepage();
        } else {
            updateUIUnverifiedHomepage();
        }
    }

    private void updateUINoHomepage() {
        dialog.getContentPane().setPreferredSize(new Dimension(640, 150));
        StringBuilder instructions = new StringBuilder();
        instructions.append("<html>")
                .append("You need to enter a homepage for your app before you can verify ownership of it.</html>");
        shaTextField.setVisible(false);
        verifyButton.setVisible(false);
        instructionsLabel.setVisible(true);
        copiedToClipboardMessageLabel.setVisible(false);
        instructionsLabel.setText(instructions.toString());
        instructionsLabel.setPreferredSize(new Dimension(instructionsLabel.getPreferredSize().width, 60));
        instructionsLabel.setVerticalAlignment(JLabel.CENTER);

    }

    private void updateUIUnverifiedHomepage() {
        dialog.getContentPane().setPreferredSize(new Dimension(640, 300));
        StringBuilder instructions = new StringBuilder();
        instructions.append("<html>")
                .append("When users install your app, they'll be warned to only install software from trusted sources, and they'll be ")
                .append("encouraged to visit your app's homepage.<br><br>")
                .append("By default your app's homepage will be shown as your npmjs.org project page.  If you want users to see ")
                .append("your actual homepage (").append(app.getHomepage()).append(") then you need to verify ownership.<br><br>")
                .append("You can verify ownership by pasting the following sha256 hash into your homepage's content:</html>");

        shaTextField.setVisible(true);
        verifyButton.setVisible(true);
        copiedToClipboardMessageLabel.setVisible(true);

        try {
            shaTextField.setText(NPMApplicationHelper.getApplicationSha256Hash(app));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate application hash.", ex);
        }
        instructionsLabel.setText(instructions.toString());
        instructionsLabel.setVerticalAlignment(JLabel.TOP);
        instructionsLabel.setPreferredSize(new Dimension(instructionsLabel.getPreferredSize().width, 200));

    }

    private void updateUIVerifiedHomepage() {
        dialog.getContentPane().setPreferredSize(new Dimension(640, 150));
        StringBuilder instructions = new StringBuilder();
        instructions.append("<html>")
                .append("Your homepage (").append(app.getHomepage()).append(") has been verified</html>");
        shaTextField.setVisible(false);
        verifyButton.setVisible(false);
        copiedToClipboardMessageLabel.setVisible(false);
        instructionsLabel.setText(instructions.toString());
        instructionsLabel.setPreferredSize(new Dimension(instructionsLabel.getPreferredSize().width, 100));
        instructionsLabel.setVerticalAlignment(JLabel.CENTER);


    }

    private void respondUI(EventType type) {
        switch (type) {
            case CopyToClipboard:
                handleCopyToClipboard();
                break;
            case Verify:
                handleVerify();
                break;
        }
    }

    private Timer currentTimer;

    private void handleCopyToClipboard() {
        StringSelection stringSelection = new StringSelection (shaTextField.getText());
        Clipboard clpbrd = Toolkit.getDefaultToolkit ().getSystemClipboard ();
        clpbrd.setContents (stringSelection, null);

        copiedToClipboardMessageLabel.setVisible(true);
        copiedToClipboardMessageLabel.setText("The hash has been copied to the clipboard");
        dialog.revalidate();
        if (currentTimer != null) {
            currentTimer.stop();
            currentTimer = null;
        }
        Timer timer = new Timer(5000, evt->{
             copiedToClipboardMessageLabel.setText("");
             currentTimer = null;
        });
        timer.setRepeats(false);
        currentTimer = timer;
    }

    private void handleVerify() {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        dialog.revalidate();
        SwingWorker worker = new SwingWorker() {

            @Override
            protected Object doInBackground() throws Exception {
                app.resetHomepageVerificationStatus();
                verifier.verifyHomepage(app);
                return null;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                if (app.isHomepageVerified()) {
                    JOptionPane.showMessageDialog(dialog, "Your homepage has been verified successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                    return;
                } else {
                    JLabel msg = new JLabel("<html>Verification failed.  Could not find the provided hash in your homepage.  Please paste the hash string into your homepage, and try again.</html>");
                    msg.setPreferredSize(new Dimension(400, 100));
                    JOptionPane.showMessageDialog(dialog, msg,  "Verification Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
                updateUI_();
                dialog.revalidate();
            }
        };
        worker.execute();
    }
}
