package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.npm.NpmAccountInterface;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class NpmAccountChooserDialog extends JDialog {

    // For demo, we keep references to some UI controls:
    private final JLabel subtitleLabel;
    private final JButton whyButton;
    private final JPanel accountListPanel;
    private final JButton continueButton;
    private final JButton addAccountButton;
    private final JButton closeButton;

    // The currently selected account
    private NpmAccountInterface selectedAccount;
    // The currently selected account button (to highlight)
    private JButton selectedButton;

    private final Font headerFont = new Font("SansSerif", Font.BOLD, 18);
    private final Font subHeaderFont = new Font("SansSerif", Font.PLAIN, 14);

    // Colors
    private final Color textColor = Color.BLACK;
    private final Color linkColor = new Color(0, 120, 220);
    private final Color selectedBackground = new Color(220, 240, 255);

    /**
     * Creates a modal, undecorated dialog.
     */
    public NpmAccountChooserDialog(Frame parent, List<NpmAccountInterface> accounts) {
        super(parent, true);
        setUndecorated(true);

        /*
         * 1) Main content with BorderLayout
         *    - We'll use the NORTH region for the close button
         *    - The CENTER region for all the main content
         */
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
        setContentPane(contentPanel);

        /*
         * 2) Top "close button" panel, aligned to the right
         */
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        northPanel.setOpaque(false);

        closeButton = createCloseButton(parent);
        northPanel.add(closeButton);

        contentPanel.add(northPanel, BorderLayout.NORTH);

        /*
         * 3) Main (center) content. We'll still use a BoxLayout to stack
         *    the "npm" title, subtitle, account list, etc.
         */
        JPanel centerPanel = new JPanel();
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // Title: "npm", centered
        JLabel titleLabel = new JLabel("npm");
        titleLabel.setFont(headerFont);
        titleLabel.setForeground(textColor);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(titleLabel);
        centerPanel.add(Box.createVerticalStrut(8));

        // Subtitle: "Select an account"
        subtitleLabel = new JLabel("Select an account");
        subtitleLabel.setFont(subHeaderFont);
        subtitleLabel.setForeground(textColor);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(subtitleLabel);
        centerPanel.add(Box.createVerticalStrut(15));

        // "Why" link
        whyButton = createLinkButton("Why am I being asked to select an account?",
                Material.HELP_OUTLINE, linkColor);
        whyButton.setVisible(false);
        whyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(whyButton);
        centerPanel.add(Box.createVerticalStrut(20));

        // Account list
        accountListPanel = new JPanel();
        accountListPanel.setLayout(new BoxLayout(accountListPanel, BoxLayout.Y_AXIS));
        accountListPanel.setOpaque(false);
        accountListPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        for (NpmAccountInterface account : accounts) {
            JButton accountBtn = createAccountButton(account);
            accountListPanel.add(accountBtn);
            accountListPanel.add(Box.createVerticalStrut(10));
        }
        centerPanel.add(accountListPanel);
        centerPanel.add(Box.createVerticalStrut(15));

        // "Continue" button
        continueButton = new JButton("Continue");
        continueButton.setEnabled(false);
        continueButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        continueButton.setPreferredSize(new Dimension(120, 35));
        continueButton.addActionListener(e -> dispose());
        centerPanel.add(continueButton);
        centerPanel.add(Box.createVerticalStrut(20));

        // "Add a new account"
        addAccountButton = createLinkButton("Add a new account", null, linkColor);
        addAccountButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(addAccountButton);

        contentPanel.add(centerPanel, BorderLayout.CENTER);

        // Final setup
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Shows the dialog (modal). Returns the selected account, or null if none.
     */
    public NpmAccountInterface showDialog() {
        setVisible(true);
        return selectedAccount;
    }

    /**
     * Create a button for each account
     */
    private JButton createAccountButton(NpmAccountInterface account) {
        JButton btn = new JButton(account.getNpmAccountName());
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);

        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));

        // Account icon
        FontIcon icon = FontIcon.of(Material.ACCOUNT_CIRCLE, 20, textColor);
        btn.setIcon(icon);

        // Let it expand horizontally
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        // On click => select
        btn.addActionListener((ActionEvent e) -> {
            selectedAccount = account;
            continueButton.setEnabled(true);
            setSelectedButton(btn);
        });

        return btn;
    }

    /**
     * Highlight the newly selected button, unhighlight old one.
     */
    private void setSelectedButton(JButton btn) {
        if (selectedButton != null) {
            selectedButton.setOpaque(false);
            selectedButton.setContentAreaFilled(false);
            selectedButton.repaint();
        }
        selectedButton = btn;
        selectedButton.setOpaque(true);
        selectedButton.setContentAreaFilled(true);
        selectedButton.setBackground(selectedBackground);
        selectedButton.repaint();
    }

    /**
     * Create link-styled JButtons.
     */
    private JButton createLinkButton(String text, Material iconType, Color linkColor) {
        JButton linkBtn = new JButton(text);
        linkBtn.setFocusPainted(false);
        linkBtn.setBorderPainted(false);
        linkBtn.setContentAreaFilled(false);
        linkBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        linkBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        linkBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        linkBtn.setForeground(linkColor);

        if (iconType != null) {
            FontIcon icon = FontIcon.of(iconType, 16, linkColor);
            linkBtn.setIcon(icon);
        }

        // Underline on hover
        linkBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                linkBtn.setText("<html><div style='text-align:center;'>" +
                        "<u>" + text + "</u></div></html>");
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                linkBtn.setText(text);
            }
        });

        return linkBtn;
    }

    /**
     * Creates a "close" (X) button for the top-right (NORTH) region.
     */
    private JButton createCloseButton(Frame parent) {
        JButton closeBtn = new JButton();
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        FontIcon closeIcon = FontIcon.of(Material.CLOSE, 18, Color.GRAY);
        closeBtn.setIcon(closeIcon);

        // On click => clear selected & dispose
        closeBtn.addActionListener(e -> {
            selectedAccount = null;
            dispose();
            if (parent != null) {
                parent.requestFocus();
            }
        });
        return closeBtn;
    }

    // External getters if needed
    public JButton getAddAccountButton() {
        return addAccountButton;
    }
    public JButton getWhyButton() {
        return whyButton;
    }
}
