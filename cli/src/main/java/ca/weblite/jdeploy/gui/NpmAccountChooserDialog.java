package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.npm.NpmAccountInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class NpmAccountChooserDialog extends JDialog {

    private final JLabel titleLabel;
    private final JButton cancelButton;
    private final NpmAccountChooserPanel chooserPanel;

    private NpmAccountInterface selectedAccount;

    /**
     * Creates a modal, undecorated dialog that allows the user
     * to select an NpmAccount or cancel.
     *
     * @param parent   The parent frame (for modality). Can be null if you want a top-level dialog.
     * @param accounts The list of available NpmAccountInterface objects to choose from.
     */
    public NpmAccountChooserDialog(Frame parent, List<NpmAccountInterface> accounts) {
        super(parent, true); // modal dialog
        setUndecorated(true); // no standard window decorations

        // Create a panel for the content so we can add a border+padding
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.DARK_GRAY, 1),    // thin dark gray border
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)        // padding
                )
        );
        setContentPane(contentPanel);

        // Title label at top
        titleLabel = new JLabel("Please select npm account", SwingConstants.CENTER);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(titleLabel, BorderLayout.NORTH);

        // NpmAccountChooserPanel with account buttons
        chooserPanel = new NpmAccountChooserPanel(accounts);
        contentPanel.add(chooserPanel, BorderLayout.CENTER);

        // Bottom panel for the "Cancel" button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        cancelButton = new JButton("Cancel");
        bottomPanel.add(cancelButton);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Wire up actions
        chooserPanel.addActionListener(e -> {
            // If user clicks an account, remember it and dispose
            selectedAccount = chooserPanel.getSelectedAccount();
            dispose();
        });

        cancelButton.addActionListener((ActionEvent e) -> {
            selectedAccount = null;
            dispose();
        });

        // Final setup
        pack();
        setLocationRelativeTo(parent);
    }

    public JButton getNewAccountButton() {
        return chooserPanel.getNewAccountButton();
    }

    /**
     * Shows this dialog and returns the selected account,
     * or null if the user cancels.
     */
    public NpmAccountInterface showDialog() {
        setVisible(true);
        return selectedAccount;
    }
}
