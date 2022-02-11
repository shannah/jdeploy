package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.npm.NPM;
import io.codeworth.panelmatic.PanelMatic;
import io.codeworth.panelmatic.util.Groupings;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.io.IOException;
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
        JTextField username = new JTextField();
        username.setColumns(numCols);
        JTextField email = new JTextField();
        email.setColumns(numCols);
        ((JComponent)cnt).setBorder(new EmptyBorder(15, 15, 15, 15));
        //cnt.setPreferredSize(new Dimension(480, 300));

        JPasswordField password = new JPasswordField();
        password.setColumns(numCols);



        JButton login = new JButton("Login");
        JButton cancel = new JButton("Cancel");

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        JPanel statusPanelWrapper = new JPanel();
        statusPanelWrapper.setLayout(new BorderLayout());

        JPanel statusPanel = new JPanel();
        //statusPanel.setPreferredSize(new Dimension(480, 200));

        JLabel statusLabel = new JLabel("Some sample text");
        statusPanel.add(statusLabel);
        statusPanelWrapper.setPreferredSize(new Dimension(10, statusPanel.getPreferredSize().height));
        statusPanel.setVisible(false);
        statusPanelWrapper.add(statusPanel, BorderLayout.CENTER);


        JButton loginManuallyButton = new JButton("Login using Command-line");
        loginManuallyButton.addActionListener(evt->{
            try {
                Desktop.getDesktop().browse(new URI("https://docs.npmjs.com/cli/v6/commands/npm-adduser"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, new JLabel("<html><p style='width:400px'>There was a problem opening the opening npm login docs.  Please visit https://docs.npmjs.com/cli/v6/commands/npm-adduser in your web browser.</p></html>"), "A Problem Occurred", JOptionPane.ERROR_MESSAGE);

            }
        });
        loginManuallyButton.setMaximumSize(loginManuallyButton.getPreferredSize());
        statusPanel.add(loginManuallyButton);

        JButton createNpmAccount = new JButton("Signup");
        createNpmAccount.setToolTipText("If you don't have an npm account yet, press here to signup.");
        createNpmAccount.addActionListener(evt->{
            try {
                Desktop.getDesktop().browse(new URI("https://www.npmjs.com/signup"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, new JLabel("<html><p style='width:400px'>There was a problem opening the opening npm login docs.  Please visit https://www.npmjs.com/signup in your web browser.</p></html>"), "A Problem Occurred", JOptionPane.ERROR_MESSAGE);

            }
        });

        cancel.addActionListener(evt->{
            npm.cancelLogin();
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(login);
        login.addActionListener(evt->{

            if (username.getText().isEmpty()) {
                statusLabel.setText("Username is required");
                statusLabel.setIcon(FontIcon.of(Material.ERROR));
                loginManuallyButton.setVisible(false);
                statusPanel.setVisible(true);
                cnt.revalidate();
                return;
            }
            if (password.getPassword().length == 0) {
                statusLabel.setText("Password is required");
                statusLabel.setIcon(FontIcon.of(Material.ERROR));
                loginManuallyButton.setVisible(false);
                statusPanel.setVisible(true);
                cnt.revalidate();
                return;
            }
            if (email.getText().isEmpty()) {
                statusLabel.setText("Email is required");
                statusLabel.setIcon(FontIcon.of(Material.ERROR));
                loginManuallyButton.setVisible(false);
                statusPanel.setVisible(true);
                cnt.revalidate();
                return;
            }


            progressBar.setVisible(true);
            login.setEnabled(false);
            statusPanel.setVisible(false);
            dialog.revalidate();
            new Thread(()->{
                try {
                    boolean result = npm.login(username.getText(), new String(password.getPassword()), email.getText(),
                            new NPM.OTPCallback() {
                                @Override
                                public String getOTPPassword() {
                                    String[] out = new String[1];
                                    try {
                                        EventQueue.invokeAndWait(() -> {
                                            out[0] = JOptionPane.showInputDialog(dialog, new JLabel("<html><p style='width:400px'>A one-time password has been emailed to you.  Please check your email and enter the password here:</p></html>"));
                                        });
                                        return out[0];
                                    } catch (Exception ex) {
                                        throw new RuntimeException("Dialog was interrupted while waiting for you to enter the one-time password");
                                    }
                                }
                            }
                    );
                    if (result) {
                        System.out.println("Login complete... about to invoke onlogin callback");
                        EventQueue.invokeLater(()->{
                            dialog.dispose();
                            if (onLogin != null) {
                                onLogin.run();
                            }
                        });
                    } else {
                        EventQueue.invokeLater(()->{
                            JLabel errorLabel = new JLabel("<html><p style='width:400px'>Login failed.  Please check username and password and try again.</p></html>");
                            JOptionPane.showMessageDialog(dialog, errorLabel, "Login Failed", JOptionPane.ERROR_MESSAGE);


                        });

                    }
                } catch (NPM.LoginTimeoutException ex) {
                    EventQueue.invokeLater(()->{

                        JLabel errorLabel = new JLabel("<html><p style='width:400px'>A timeout occurred waiting for the 'npm login' command.  Please login manually in any terminal window with the 'npm login' command, then try to publish your package again.</p></html>");
                        JOptionPane.showMessageDialog(dialog, errorLabel, "Login Failed", JOptionPane.ERROR_MESSAGE);

                    });

                } catch (IOException ex) {
                    EventQueue.invokeLater(()->{


                        JLabel errorLabel = new JLabel("<html><p style='width:400px'>An exception occurred 'npm login' command.  "+ex.getMessage()+". Please login manually in any terminal window with the 'npm login' command, then try to publish your package again.</p></html>");
                        JOptionPane.showMessageDialog(dialog, errorLabel, "Login Failed", JOptionPane.ERROR_MESSAGE);

                    });


                } finally {
                    EventQueue.invokeLater(()->{
                        login.setEnabled(true);
                        progressBar.setVisible(false);
                    });

                }
            }).start();
        });

        JComponent loginPanel = PanelMatic.begin()
                .add("Username", username)
                .add("Email", email)
                .add("Password", password).get();
        loginPanel.setBorder(new EmptyBorder(10, 10, 0, 10));
        JPanel center = new JPanel();
        center.setLayout(new BorderLayout());
        center.add(loginPanel, BorderLayout.CENTER);

        FontIcon loginIcon = FontIcon.of(Material.SECURITY);
        loginIcon.setIconSize(75);
        loginIcon.setIconColor(Color.DARK_GRAY);
        center.add(new JLabel(loginIcon), BorderLayout.WEST);

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
