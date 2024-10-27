package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.npm.NPM;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.net.URI;

public class LoginDialog {

    private JDialog dialog;
    private NPM npm = new NPM(System.out, System.err);
    private Runnable onLogin;

    public void onLogin(Runnable onLogin) {
        this.onLogin = onLogin;
    }

    public void show(JFrame parent) {
        dialog = new JDialog(parent, "Login to npm");
        dialog.setModal(true);
        initUI(dialog.getContentPane());
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void initUI(Container cnt) {
        int numCols = 20;

        JLabel loginInstructions = new JLabel(
                "<html><p style='width:400px'>You are not currently logged into npm.</p>" +
                "<p>Launching terminal window.  Please login in the terminal window, then press 'Try again'</p>" +
                        "<p>If you don't have an npm account yet, press 'Signup' to create one.</p>" +
                        "<p>If a terminal window does not open, please open a terminal window and run the command: " +
                        "<code>jdeploy login</code></p>" +
                "</html>");

        ((JComponent)cnt).setBorder(new EmptyBorder(15, 15, 15, 15));

        JButton login = new JButton("Try again");
        JButton cancel = new JButton("Cancel");

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        JPanel statusPanelWrapper = new JPanel();
        statusPanelWrapper.setLayout(new BorderLayout());

        JPanel statusPanel = new JPanel();
        JLabel statusLabel = new JLabel("Some sample text");
        statusPanel.add(statusLabel);
        statusPanelWrapper.setPreferredSize(new Dimension(10, statusPanel.getPreferredSize().height));
        statusPanel.setVisible(false);
        statusPanelWrapper.add(statusPanel, BorderLayout.CENTER);


        JButton createNpmAccount = new JButton("Signup");
        createNpmAccount.setToolTipText("If you don't have an npm account yet, press here to signup.");
        createNpmAccount.addActionListener(evt->{
            try {
                Desktop.getDesktop().browse(new URI("https://www.npmjs.com/signup"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        dialog,
                        new JLabel(
                                "<html>" +
                                    "<p style='width:400px'>There was a problem opening the opening npm login docs.  " +
                                    "Please visit https://www.npmjs.com/signup in your web browser.</p>" +
                                    "</html>"
                        ),
                        "A Problem Occurred",
                        JOptionPane.ERROR_MESSAGE
                );

            }
        });

        cancel.addActionListener(evt->{
            npm.cancelLogin();
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(login);
        login.addActionListener(evt->{
            if (onLogin != null) {
                onLogin.run();
            }
        });

        JPanel center = new JPanel();
        center.setLayout(new BorderLayout());

        FontIcon loginIcon = FontIcon.of(Material.SECURITY);
        loginIcon.setIconSize(75);
        loginIcon.setIconColor(Color.DARK_GRAY);
        center.add(new JLabel(loginIcon), BorderLayout.WEST);
        center.add(loginInstructions, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(login);

        center.add(buttons, BorderLayout.SOUTH);
        center.setBorder(new EtchedBorder());

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        JPanel noAccount = new JPanel();
        noAccount.add(new JLabel("Don't have an npm account yet?"));
        noAccount.add(createNpmAccount);
        south.add(noAccount);
        south.add(progressBar);

        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(statusPanelWrapper, BorderLayout.NORTH);
        cnt.add(center, BorderLayout.CENTER);
        cnt.add(south, BorderLayout.SOUTH);
    }
}
